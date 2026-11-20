/*
 * Application context holder for media extraction and playback services.
 */
package com.widgetly.app

import android.app.Application

class RushApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        lateinit var instance: RushApplication
            private set
    }
}
