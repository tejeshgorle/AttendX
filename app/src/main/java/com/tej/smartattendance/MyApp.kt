package com.tej.smartattendance

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── Firestore Offline Cache ────────────────────────────────
        // Enables local persistence so data loads instantly on reopen
        // and app works offline with last fetched data.
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)        // ← enables local cache
            .setCacheSizeBytes(
                FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED  // no cache size limit
            )
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        // ─────────────────────────────────────────────────────────

        CloudinaryManager.init(this)
    }
}