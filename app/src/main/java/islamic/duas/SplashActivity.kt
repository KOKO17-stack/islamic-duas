package islamic.duas

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_EmeraldDusk_Dark)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        // Hand off to the real activity almost immediately. MainActivity performs
        // its heavy startup work on background threads, so we don't need to block here.
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 350)
    }
}
