package com.fankes.coloros.notify.application

import android.app.Application
import com.fankes.coloros.notify.data.ConfigData
import com.fankes.coloros.notify.utils.tool.FrameworkServiceBridge

class CNNApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ConfigData.initialize(this)
        FrameworkServiceBridge.initialize()
    }
}
