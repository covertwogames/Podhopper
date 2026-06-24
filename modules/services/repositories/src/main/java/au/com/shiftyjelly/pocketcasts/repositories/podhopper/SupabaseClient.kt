package au.com.shiftyjelly.pocketcasts.repositories.podhopper

import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.servers.di.Raw
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to PodHopper's Supabase backend: GoTrue auth plus PostgREST upsert and select.
 *
 * This is a faithful Kotlin port of the proven AntennaPod implementation. The session
 * (access token, user id, expiry) is cached in memory. The refresh token and signed-in email
 * are persisted in app settings, so the user stays signed in across restarts and the access
 * token is silently refreshed when it expires.
 *
 * The @Raw OkHttp client is used deliberately: it carries no Pocket Casts auth interceptor,
 * since Supabase supplies its own apikey and bearer token.
 */
@Singleton
class SupabaseClient @Inject constructor(
    @Raw private val httpClient: OkHttpClient,
    private val settings: Settings,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private var cachedAccessToken: String? = null
    private var cachedUserId: String? = null
    private var tokenExpiresAtMs: Long = 0

    fun isLoggedIn(): Boolean = settings.podhopperRefreshToken.value.isNotEmpty()

    @Synchronized
    fun login(email: String, password: String) {
        val body = JSONObject()
        body.put("email", email)
        body.put("password", password)
        val json = authCall("/auth/v1/token?grant_type=password", body)
        applySession(json)
        settings.podhopperRefreshToken.set(json.optString("refresh_token", ""), updateModifiedAt = false)
        settings.podhopperEmail.set(email, updateModifiedAt = false)
    }

    /**
     * Creates a new account. When the project does not require email confirmation, Supabase returns
     * a session immediately: that session is applied and persisted exactly like [login], and this
     * returns true. When confirmation is required, no token comes back and this returns false, which
     * the caller surfaces as "check your email to confirm".
     */
    @Synchronized
    fun signUp(email: String, password: String): Boolean {
        val body = JSONObject()
        body.put("email", email)
        body.put("password", password)
        val json = authCall("/auth/v1/signup", body)
        if (json.has("access_token")) {
            applySession(json)
            settings.podhopperRefreshToken.set(json.optString("refresh_token", ""), updateModifiedAt = false)
            settings.podhopperEmail.set(email, updateModifiedAt = false)
            return true
        }
        return false
    }

    /**
     * Asks Supabase to send a password reset email. Succeeds quietly; failures throw.
     */
    fun recoverPassword(email: String) {
        val body = JSONObject()
        body.put("email", email)
        authCall("/auth/v1/recover", body)
    }

    @Synchronized
    fun ensureSession(): String {
        val cached = cachedAccessToken
        if (cached != null && System.currentTimeMillis() < tokenExpiresAtMs - TOKEN_SAFETY_MARGIN_MS) {
            return cached
        }
        val refreshToken = settings.podhopperRefreshToken.value
        if (refreshToken.isNotEmpty()) {
            return refreshSession(refreshToken)
        }
        throw SupabaseException("PodHopper sync: not signed in")
    }

    @Synchronized
    fun getUserId(): String? {
        ensureSession()
        return cachedUserId
    }

    @Synchronized
    fun logout() {
        clearSessionCache()
        settings.podhopperRefreshToken.set("", updateModifiedAt = false)
        settings.podhopperEmail.set("", updateModifiedAt = false)
    }

    fun upsert(table: String, onConflictColumns: String, rows: JSONArray) {
        if (rows.length() == 0) {
            return
        }
        val url = PodHopperConfig.SUPABASE_URL + "/rest/v1/" + table + "?on_conflict=" + onConflictColumns
        val request = restRequestBuilder(url)
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(rows.toString().toRequestBody(jsonType))
            .build()
        executeExpectingSuccess(request, retryOnAuthError = true)
    }

    fun select(table: String, query: String): JSONArray {
        val url = PodHopperConfig.SUPABASE_URL + "/rest/v1/" + table + "?" + query
        val request = restRequestBuilder(url).get().build()
        val body = executeExpectingSuccess(request, retryOnAuthError = true)
        return JSONArray(body)
    }

    @Synchronized
    private fun refreshSession(refreshToken: String): String {
        val body = JSONObject()
        body.put("refresh_token", refreshToken)
        val json = try {
            authCall("/auth/v1/token?grant_type=refresh_token", body)
        } catch (e: AuthRejectedException) {
            settings.podhopperRefreshToken.set("", updateModifiedAt = false)
            throw SupabaseException("PodHopper sign-in expired. Sign in again.", e)
        }
        applySession(json)
        val rotated = json.optString("refresh_token", "")
        if (rotated.isNotEmpty()) {
            settings.podhopperRefreshToken.set(rotated, updateModifiedAt = false)
        }
        return cachedAccessToken ?: throw SupabaseException("PodHopper sync: missing access token")
    }

    private fun applySession(json: JSONObject) {
        cachedAccessToken = json.getString("access_token")
        val expiresInSec = json.optLong("expires_in", 3600)
        tokenExpiresAtMs = System.currentTimeMillis() + expiresInSec * 1000
        val user = json.optJSONObject("user")
        val userId = user?.optString("id").orEmpty()
        cachedUserId = if (userId.isEmpty()) null else userId
    }

    private fun clearSessionCache() {
        cachedAccessToken = null
        cachedUserId = null
        tokenExpiresAtMs = 0
    }

    private fun authCall(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(PodHopperConfig.SUPABASE_URL + path)
            .addHeader("apikey", PodHopperConfig.SUPABASE_ANON_KEY)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            val code = response.code
            if (code == 400 || code == 401 || code == 403) {
                throw AuthRejectedException("PodHopper auth failed: HTTP $code $responseBody")
            }
            if (!response.isSuccessful) {
                throw SupabaseException("PodHopper auth failed: HTTP $code $responseBody")
            }
            return if (responseBody.isEmpty()) JSONObject() else JSONObject(responseBody)
        }
    }

    private fun restRequestBuilder(url: String): Request.Builder {
        val token = ensureSession()
        return Request.Builder()
            .url(url)
            .addHeader("apikey", PodHopperConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
    }

    private fun executeExpectingSuccess(request: Request, retryOnAuthError: Boolean): String {
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (response.code == 401 && retryOnAuthError) {
                clearSessionCache()
                val refreshed = ensureSession()
                val retried = request.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer $refreshed")
                    .build()
                return executeExpectingSuccess(retried, retryOnAuthError = false)
            }
            if (!response.isSuccessful) {
                throw SupabaseException("PodHopper sync request failed: HTTP ${response.code} $responseBody")
            }
            return responseBody
        }
    }

    private companion object {
        const val TOKEN_SAFETY_MARGIN_MS = 60 * 1000L
    }
}

open class SupabaseException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AuthRejectedException(message: String) : SupabaseException(message)
