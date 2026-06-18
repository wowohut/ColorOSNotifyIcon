package com.fankes.coloros.notify.framework

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

object LsposedServiceBridge {

    interface Listener {
        fun onServiceChanged(service: XposedService?)
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var currentService: XposedService? = null

    private val listeners = CopyOnWriteArraySet<Listener>()

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    currentService = service
                    notifyListeners(service)
                }

                override fun onServiceDied(service: XposedService) {
                    if (currentService == service) currentService = null
                    notifyListeners(currentService)
                }
            })
            initialized = true
        }
    }

    fun getCurrentService(): XposedService? = currentService

    fun addListener(listener: Listener, dispatchCurrent: Boolean = true) {
        listeners += listener
        if (dispatchCurrent) listener.onServiceChanged(currentService)
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    private fun notifyListeners(service: XposedService?) {
        listeners.forEach { it.onServiceChanged(service) }
    }
}
