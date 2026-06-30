package islamic.duas.cloud

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object CloudAuth {

    private const val TAG = "CloudAuth"

    private var cachedAccessToken: String? = null
    private var tokenExpiry: Long = 0L

    @Synchronized
    fun getFirebaseToken(): String {
        if (System.currentTimeMillis() < tokenExpiry && cachedAccessToken != null) {
            return cachedAccessToken!!
        }
        Log.d(TAG, "Requesting new Firebase access token...")
        val token = requestAccessToken(
            "${CloudConfig.SCOPE_RTDB} ${CloudConfig.SCOPE_DATASTORE}"
        )
        cachedAccessToken = token
        tokenExpiry = System.currentTimeMillis() + 3500 * 1000L
        return token
    }

    private fun requestAccessToken(scope: String): String {
        val json = JSONObject(CloudAuthData.SERVICE_ACCOUNT_JSON)
        val clientEmail = json.getString("client_email")
        val privateKeyPem = json.getString("private_key")

        val now = System.currentTimeMillis() / 1000L
        val header = JSONObject().apply {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        val claimSet = JSONObject().apply {
            put("iss", clientEmail)
            put("scope", scope)
            put("aud", "https://oauth2.googleapis.com/token")
            put("exp", now + 3600)
            put("iat", now)
        }

        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val claimB64 = base64UrlEncode(claimSet.toString().toByteArray())
        val signatureInput = "$headerB64.$claimB64"

        val privateKey = parsePrivateKey(privateKeyPem)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(signatureInput.toByteArray())
        val signature = sig.sign()
        val sigB64 = base64UrlEncode(signature)

        val jwt = "$signatureInput.$sigB64"

        val requestBody = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"

        val url = java.net.URL("https://oauth2.googleapis.com/token")
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.outputStream.write(requestBody.toByteArray())

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "no error body"
                Log.e(TAG, "OAuth token request failed: HTTP $responseCode — $errorBody")
                throw java.io.IOException("OAuth failed: HTTP $responseCode — $errorBody")
            }

            val responseJson = JSONObject(responseBody)
            return responseJson.getString("access_token")
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(cleaned, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
