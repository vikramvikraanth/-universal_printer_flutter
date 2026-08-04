package com.universalprinter.image

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads receipt images by URL via Glide and caches them (memory + disk). Glide's **disk cache is
 * the offline store**: an image fetched once while online is served from disk on later (offline) prints.
 */
internal object ImageCache {

    /**
     * Returns the (cached) bitmap for [url], or **null** if it can't be fetched — a bad URL, or offline
     * with nothing in the disk cache. `submit().get()` blocks, so it runs off the main thread.
     */
    suspend fun load(context: Context, url: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): Bitmap? =
        withContext(dispatcher) {
            runCatching {
                Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .submit()
                    .get()
            }.getOrNull()
        }

    /** Warms the disk cache for [urls] while online, so a later offline print has them. Failures are ignored. */
    suspend fun preload(context: Context, urls: List<String>) {
        for (url in urls) load(context, url)
    }
}
