package com.example.sensorsride

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SensorRideApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}