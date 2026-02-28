package com.tej.smartattendance

import android.content.Context
import com.cloudinary.android.MediaManager

object CloudinaryManager {

    fun init(context: Context) {

        val config = mapOf(
            "cloud_name" to "djtjxtdqx",
            "api_key" to "561536234528575",
            "api_secret" to "AZaZLIhVbiNWMnLj6pc3kU1wai4"
        )

        MediaManager.init(context, config)
    }
}