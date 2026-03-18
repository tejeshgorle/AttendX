package com.tej.smartattendance

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class AttendXGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {

        // ── Memory cache: 20MB ─────────────────────────────────────
        // Holds recently loaded images in RAM for instant re-display
        val memoryCacheSizeBytes = 20 * 1024 * 1024 // 20MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes.toLong()))

        // ── Disk cache: 50MB ───────────────────────────────────────
        // Saves downloaded images to disk so they don't re-download
        val diskCacheSizeBytes = 50 * 1024 * 1024 // 50MB
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(context, diskCacheSizeBytes.toLong())
        )

        // ── Default request options ────────────────────────────────
        // Applied globally to every Glide call in the app
        builder.setDefaultRequestOptions(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)   // cache original + resized
                .format(DecodeFormat.PREFER_RGB_565)        // lower memory usage
                .placeholder(R.drawable.ic_default_avatar) // show while loading
                .error(R.drawable.ic_default_avatar)        // show on failure
        )
    }

    // ── Disable manifest parsing for faster init ──
    override fun isManifestParsingEnabled(): Boolean = false
}