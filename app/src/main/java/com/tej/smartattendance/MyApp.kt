package com.tej.smartattendance

import android.app.Application

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CloudinaryManager.init(this)
    }
}