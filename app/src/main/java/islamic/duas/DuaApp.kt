package islamic.duas

import android.app.Application
import islamic.duas.cloud.CloudApi

class DuaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CloudApi.init(this)
    }
}
