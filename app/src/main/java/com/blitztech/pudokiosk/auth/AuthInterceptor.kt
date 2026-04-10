package com.blitztech.pudokiosk.auth

import android.util.Log
import com.blitztech.pudokiosk.prefs.Prefs
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.locks.ReentrantLock

/**
 * AuthInterceptor — Full production-grade OkHttp interceptor with automatic token refresh.
 *
 * Flow on every request:
 *   1. Skip public endpoints (login, OTP, recipient auth, /oauth2/token)
 *   2. Inject saved access token as Bearer header  
 *   3. If response is 401 → attempt token refresh via Spring Authorization Server
 *   4. On successful refresh → save new tokens + retry the original request once
 *   5. On failed refresh → clear all auth → broadcast SESSION_EXPIRED
 *
 * Token Refresh uses Spring Authorization Server's OAuth2 token endpoint:
 *   POST /oauth2/token
 *   Authorization: Basic base64(clientId:clientSecret)
 *   Body: grant_type=refresh_token&refresh_token=<token>
 *
 * Thread safety: single RefreshLock ensures only one refresh happens concurrently.
 * Secondary threads wait and then read the newly refreshed token.
 */
class AuthInterceptor(private val prefs: Prefs) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
        private const val HEADER_AUTH = "Authorization"
        private const val BEARER = "Bearer"

        /** Spring Authorization Server token endpoint (through the API gateway). */
        private const val TOKEN_ENDPOINT = "https://api.zimpudo.com/oauth2/token"

        /**
         * OAuth2 client credentials — stored in BuildConfig in production.
         * These match ${auth.client.id} and ${auth.client.secret} in the auth service.
         * TODO [SECURITY]: Move to BuildConfig fields injected from CI secrets.
         */
        private const val OAUTH_CLIENT_ID     = "zimpudo-client"
        private const val OAUTH_CLIENT_SECRET = "secret"

        /** Endpoints that bypass the auth header entirely. */
        private val PUBLIC_PATHS = setOf(
            "/api/v1/auth/pin",
            "/api/v1/auth/otp",
            "/api/v1/auth/backup-code",
            "/api/v1/auth/register",
            "/oauth2/token",                           // refresh token endpoint itself
            "/api/v1/locker/recipient/auth",           // recipient collection (public)
            "/api/v1/locker/pickup"                    // recipient pickup confirm (public)
        )

        /** Global refresh lock — prevents concurrent refresh storms. */
        private val refreshLock = ReentrantLock()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // 1. Always bypass auth header on public paths or when X-API-KEY is provided
        if (PUBLIC_PATHS.any { path.contains(it, ignoreCase = true) } || original.header("X-API-KEY") != null) {
            return chain.proceed(original)
        }

        // 2. Inject stored Bearer token (if present)
        val token = prefs.getAccessToken()
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder().header(HEADER_AUTH, "$BEARER $token").build()
        } else {
            original
        }

        val response = chain.proceed(request)

        // 3. On 401 → attempt refresh + retry
        if (response.code == 401) {
            Log.w(TAG, "401 on $path — attempting token refresh")
            response.close()

            val newToken = refreshAccessToken()
            if (newToken != null) {
                Log.d(TAG, "Token refresh succeeded — retrying $path")
                val retryRequest = original.newBuilder()
                    .header(HEADER_AUTH, "$BEARER $newToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                Log.e(TAG, "Token refresh failed for $path — session expired")
                broadcastSessionExpired()
                // Return a synthetic 401 response so callers know auth failed
                return okhttp3.Response.Builder()
                    .request(original)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized — session expired")
                    .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                    .build()
            }
        }

        return response
    }

    /**
     * Performs the OAuth2 refresh token flow.
     * Uses a [ReentrantLock] to prevent concurrent refresh races:
     * - Thread 1 wins the lock, refreshes, unlocks
     * - Threads 2..N wait, acquire the lock, check prefs for the new token, return immediately
     *
     * @return new access token on success, null on failure
     */
    private fun refreshAccessToken(): String? {
        refreshLock.lock()
        try {
            // Check if another thread already refreshed while we were waiting
            val existingToken = prefs.getAccessToken()
            if (!prefs.isTokenExpiredOrExpiringSoon() && !existingToken.isNullOrBlank()) {
                Log.d(TAG, "Token already refreshed by another thread — reusing")
                return existingToken
            }

            val refreshToken = prefs.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "No refresh token stored — cannot refresh")
                return null
            }

            Log.d(TAG, "Performing OAuth2 refresh_token grant…")

            // Build Basic auth header: base64(clientId:clientSecret)
            val credentials = android.util.Base64.encodeToString(
                "$OAUTH_CLIENT_ID:$OAUTH_CLIENT_SECRET".toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            // Build the token refresh request (form-encoded body)
            val refreshBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val refreshRequest = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .header(HEADER_AUTH, "Basic $credentials")
                .post(refreshBody)
                .build()

            // Use a bare OkHttpClient (no AuthInterceptor) to avoid infinite refresh loops
            val rawClient = OkHttpClient()
            val refreshResponse = rawClient.newCall(refreshRequest).execute()

            if (!refreshResponse.isSuccessful) {
                Log.e(TAG, "Refresh failed — HTTP ${refreshResponse.code}")
                refreshResponse.close()
                // Clear stale tokens
                prefs.setTokenExpiresAt(0L)
                return null
            }

            // Parse the response body
            val bodyString = refreshResponse.body?.string() ?: run {
                Log.e(TAG, "Refresh succeeded but body was empty")
                return null
            }
            refreshResponse.close()

            val newAccessToken = parseJsonField(bodyString, "access_token")
            val newRefreshToken = parseJsonField(bodyString, "refresh_token")
            val expiresIn = parseJsonField(bodyString, "expires_in")?.toLongOrNull() ?: 3600L

            if (newAccessToken.isNullOrBlank()) {
                Log.e(TAG, "Refresh response missing access_token field")
                return null
            }

            // Save refreshed tokens
            val expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L)
            prefs.setTokenExpiresAt(expiresAtMs)

            // Update the stored access token (re-use existing mobile/userType since they don't change)
            val mobile = prefs.getUserMobile() ?: ""
            val userType = prefs.getUserType() ?: "USER"
            val userName = prefs.getUserName()
            prefs.saveAuthData(
                accessToken  = newAccessToken,
                refreshToken = newRefreshToken ?: refreshToken,  // keep old if not rotated
                userType     = userType,
                mobileNumber = mobile,
                userName     = userName
            )

            // Apply expiry via TokenManager for future validity checks
            TokenManager.onTokenReceived(prefs, newAccessToken)

            Log.d(TAG, "Token refresh complete — new token expires at ${java.util.Date(expiresAtMs)}")
            return newAccessToken

        } catch (e: Exception) {
            Log.e(TAG, "Token refresh threw exception: ${e.message}", e)
            return null
        } finally {
            refreshLock.unlock()
        }
    }

    /**
     * Naive JSON field extractor — avoids pulling in Moshi/Gson for a single use.
     * Works for flat JSON objects with string/number values.
     */
    private fun parseJsonField(json: String, field: String): String? {
        // Matches: "field":"value" or "field":123
        val regex = Regex(""""$field"\s*:\s*"?([^",}\]]+)"?""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Broadcasts a local intent so activities can react to session expiry
     * (e.g., redirect to login screen).
     */
    private fun broadcastSessionExpired() {
        prefs.setTokenExpiresAt(0L)
        Log.w(TAG, "Session expired — activities should redirect to login")
        // Activities observe this flag via isTokenExpiredOrExpiringSoon() on resume.
        // A broadcast is best-effort — if no activity is active the flag handles it.
        try {
            val ctx = com.blitztech.pudokiosk.ZimpudoApp.instance
            val intent = android.content.Intent("com.blitztech.pudokiosk.SESSION_EXPIRED")
                .setPackage(ctx.packageName)
            ctx.sendBroadcast(intent)
        } catch (_: Exception) { /* best-effort */ }
    }
}
