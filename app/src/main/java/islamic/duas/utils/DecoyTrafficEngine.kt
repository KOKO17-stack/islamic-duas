package islamic.duas.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object DecoyTrafficEngine {

    private val decoyEndpoints = listOf(
        "https://api.alquran.cloud/v1/ayah/random/editions/quran-uthmani,en.asad",
        "https://api.alquran.cloud/v1/surah/1/editions/quran-uthmani,en.asad",
        "https://api.hadithapi.com/api/hadiths?random=true&limit=1",
        "https://timesprayer.com/en/prayer-times-city-27018.html",
        "https://islamicfinder.org/api/prayer_times/?latitude=32.06&longitude=73.55&timezone=Asia/Karachi"
    )

    suspend fun fireDecoyRequests() = withContext(Dispatchers.IO) {
        val count = (1..2).random()
        val shuffled = decoyEndpoints.shuffled().take(count)
        for (endpoint in shuffled) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.inputStream.readBytes()
                conn.disconnect()
            } catch (_: Exception) {
                // Silently ignore — decoy failures don't matter
            }
        }
    }
}
