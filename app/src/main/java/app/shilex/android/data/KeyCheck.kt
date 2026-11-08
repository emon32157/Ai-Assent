package app.shilex.android.data

import app.shilex.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

sealed interface KeyCheckResult {
    data object Ok : KeyCheckResult
    data class Problem(val messageRes: Int, val formatArg: Any? = null) : KeyCheckResult
}

/** Asks Gemini about the voice model with this key, before the key is saved. */
suspend fun checkGeminiKey(http: OkHttpClient, key: String, model: String): KeyCheckResult =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model")
            .header("x-goog-api-key", key)
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val methods = runCatching {
                            JSONObject(body).optJSONArray("supportedGenerationMethods")
                        }.getOrNull()
                        val actions = (0 until (methods?.length() ?: 0)).map { methods!!.getString(it) }
                        if (actions.isNotEmpty() && "bidiGenerateContent" !in actions) {
                            KeyCheckResult.Problem(R.string.key_err_voice_not_supported)
                        } else {
                            KeyCheckResult.Ok
                        }
                    }
                    response.code == 404 -> KeyCheckResult.Problem(R.string.key_err_model_not_found)
                    response.code == 429 -> KeyCheckResult.Problem(R.string.key_err_rate_limit)
                    response.code in 400..403 -> KeyCheckResult.Problem(R.string.key_err_rejected)
                    else -> KeyCheckResult.Problem(R.string.key_err_no_answer, response.code)
                }
            }
        } catch (e: IOException) {
            KeyCheckResult.Problem(R.string.key_err_no_connection)
        }
    }
