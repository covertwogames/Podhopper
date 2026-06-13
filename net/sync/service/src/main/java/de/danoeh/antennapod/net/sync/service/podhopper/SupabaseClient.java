package de.danoeh.antennapod.net.sync.service.podhopper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseClient {
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final long TOKEN_SAFETY_MARGIN_MS = 60 * 1000;

    private static String cachedAccessToken = null;
    private static String cachedUserId = null;
    private static long tokenExpiresAtMs = 0;

    private final OkHttpClient httpClient;
    private final String email;
    private final String password;

    public SupabaseClient(@NonNull OkHttpClient httpClient, String email, String password) {
        this.httpClient = httpClient;
        this.email = email;
        this.password = password;
    }

    public synchronized String ensureSession() throws SyncServiceException {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAtMs - TOKEN_SAFETY_MARGIN_MS) {
            return cachedAccessToken;
        }
        String refreshToken = SynchronizationCredentials.getRefreshToken();
        if (password == null && refreshToken != null) {
            return refreshSession(refreshToken);
        }
        if (email == null || password == null) {
            throw new SyncServiceException("PodHopper sync: not logged in");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            JSONObject json = authCall("/auth/v1/token?grant_type=password", body);
            applySession(json);
            return cachedAccessToken;
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    private synchronized String refreshSession(String refreshToken) throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            JSONObject json;
            try {
                json = authCall("/auth/v1/token?grant_type=refresh_token", body);
            } catch (AuthRejectedException e) {
                SynchronizationCredentials.setRefreshToken(null);
                throw new SyncServiceException("PodHopper pairing expired. Re-pair this device with your phone.");
            }
            applySession(json);
            String rotated = json.optString("refresh_token", null);
            if (rotated != null) {
                SynchronizationCredentials.setRefreshToken(rotated);
            }
            return cachedAccessToken;
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    public boolean signUp(String signupEmail, String signupPassword) throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("email", signupEmail);
            body.put("password", signupPassword);
            JSONObject json = authCall("/auth/v1/signup", body);
            if (json.has("access_token")) {
                applySession(json);
                return true;
            }
            return false;
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    public void recoverPassword(String recoveryEmail) throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("email", recoveryEmail);
            authCall("/auth/v1/recover", body);
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    public static synchronized String claimPairingSession(OkHttpClient client, String tokenHash)
            throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("type", "magiclink");
            body.put("token_hash", tokenHash);
            JSONObject json = staticAuthCall(client, "/auth/v1/verify", body);
            cachedAccessToken = json.getString("access_token");
            long expiresInSec = json.optLong("expires_in", 3600);
            tokenExpiresAtMs = System.currentTimeMillis() + expiresInSec * 1000;
            JSONObject user = json.getJSONObject("user");
            cachedUserId = user.getString("id");
            String rotated = json.optString("refresh_token", null);
            if (rotated != null) {
                SynchronizationCredentials.setRefreshToken(rotated);
            }
            return user.optString("email", "");
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    private synchronized void applySession(JSONObject json) throws JSONException {
        cachedAccessToken = json.getString("access_token");
        long expiresInSec = json.optLong("expires_in", 3600);
        tokenExpiresAtMs = System.currentTimeMillis() + expiresInSec * 1000;
        cachedUserId = json.getJSONObject("user").getString("id");
    }

    private JSONObject authCall(String path, JSONObject body) throws SyncServiceException {
        return staticAuthCall(httpClient, path, body);
    }

    private static JSONObject staticAuthCall(OkHttpClient client, String path, JSONObject body)
            throws SyncServiceException {
        Request request = new Request.Builder()
                .url(PodHopperConfig.SUPABASE_URL + path)
                .addHeader("apikey", PodHopperConfig.SUPABASE_ANON_KEY)
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                throw new AuthRejectedException("PodHopper auth failed: HTTP "
                        + response.code() + " " + responseBody);
            }
            if (!response.isSuccessful()) {
                throw new SyncServiceException("PodHopper auth failed: HTTP "
                        + response.code() + " " + responseBody);
            }
            if (responseBody.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(responseBody);
        } catch (IOException | JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    public static class AuthRejectedException extends SyncServiceException {
        private static final long serialVersionUID = 1L;

        public AuthRejectedException(String message) {
            super(message);
        }
    }

    public synchronized String getUserId() throws SyncServiceException {
        ensureSession();
        return cachedUserId;
    }

    public static synchronized void clearSession() {
        cachedAccessToken = null;
        cachedUserId = null;
        tokenExpiresAtMs = 0;
    }

    public void upsert(String table, String onConflictColumns, JSONArray rows) throws SyncServiceException {
        if (rows.length() == 0) {
            return;
        }
        String url = PodHopperConfig.SUPABASE_URL + "/rest/v1/" + table
                + "?on_conflict=" + onConflictColumns;
        Request request = restRequestBuilder(url)
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(rows.toString(), JSON_TYPE))
                .build();
        executeExpectingSuccess(request, true);
    }

    public JSONArray select(String table, String query) throws SyncServiceException {
        String url = PodHopperConfig.SUPABASE_URL + "/rest/v1/" + table + "?" + query;
        Request request = restRequestBuilder(url).get().build();
        String body = executeExpectingSuccess(request, true);
        try {
            return new JSONArray(body);
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    private Request.Builder restRequestBuilder(String url) throws SyncServiceException {
        String token = ensureSession();
        return new Request.Builder()
                .url(url)
                .addHeader("apikey", PodHopperConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json");
    }

    private String executeExpectingSuccess(Request request, boolean retryOnAuthError) throws SyncServiceException {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (response.code() == 401 && retryOnAuthError) {
                clearSession();
                ensureSession();
                Request retried = request.newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", "Bearer " + cachedAccessToken)
                        .build();
                return executeExpectingSuccess(retried, false);
            }
            if (!response.isSuccessful()) {
                throw new SyncServiceException("PodHopper sync request failed: HTTP "
                        + response.code() + " " + responseBody);
            }
            return responseBody;
        } catch (IOException e) {
            throw new SyncServiceException(e);
        }
    }
}
