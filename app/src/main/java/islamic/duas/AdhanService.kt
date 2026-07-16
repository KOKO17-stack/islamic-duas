package islamic.duas

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper

class AdhanService : Service() {

    private var player: AdhanPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = when (intent?.getStringExtra("adhan_mode")) {
            "first_two" -> AdhanMode.FIRST_TWO
            else -> AdhanMode.FULL
        }
        player = AdhanPlayer(this)
        player?.play(mode)
        val timeout = if (mode == AdhanMode.FIRST_TWO) 22000L else 45000L
        handler.postDelayed({ stopSelf() }, timeout)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.stop()
        super.onDestroy()
    }
}
