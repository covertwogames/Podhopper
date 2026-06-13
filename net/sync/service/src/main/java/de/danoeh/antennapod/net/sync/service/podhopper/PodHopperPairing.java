package de.danoeh.antennapod.net.sync.service.podhopper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PodHopperPairing {
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String FUNCTION_URL = PodHopperConfig.SUPABASE_URL + "/functions/v1/pairing";

    private final OkHttpClient httpClient;

    public PodHopperPairing(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String startPairing(String deviceName) throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "start");
            body.put("device_name", deviceName);
            JSONObject json = call(body, PodHopperConfig.SUPABASE_ANON_KEY);
            return json.getString("code");
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    public String pollPairing(String code) throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "poll");
            body.put("code", code);
            JSONObject json = call(body, PodHopperConfig.SUPABASE_ANON_KEY);
            String status = json.optString("status", "pending");
            if ("approved".equals(status)) {
                return json.getString("token_hash");
            }
            if ("expired".equals(status)) {
                throw new SyncServiceException("Pairing code expired");
            }
            return null;
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    public void approvePairing(String code, String userAccessToken) throws SyncServiceException {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "approve");
            body.put("code", code);
            call(body, userAccessToken);
        } catch (JSONException e) {
            throw new SyncServiceException(e);
        }
    }

    private JSONObject call(JSONObject body, String bearerToken) throws SyncServiceException, JSONException {
        Request request = new Request.Builder()
                .url(FUNCTION_URL)
                .addHeader("apikey", PodHopperConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + bearerToken)
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new SyncServiceException("Pairing request failed: HTTP "
                        + response.code() + " " + responseBody);
            }
            return new JSONObject(responseBody);
        } catch (IOException e) {
            throw new SyncServiceException(e);
        }
    }
}
