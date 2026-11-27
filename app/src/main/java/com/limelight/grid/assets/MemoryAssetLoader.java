package com.limelight.grid.assets;

import android.util.LruCache;

import java.lang.ref.SoftReference;

public class MemoryAssetLoader {
    private static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    private static final LruCache<String, ScaledBitmap> memoryCache = new LruCache<String, ScaledBitmap>(maxMemory / 16) {
        @Override
        protected int sizeOf(String key, ScaledBitmap bitmap) {
            // Sizeof returns kilobytes
            return (int) ((bitmap.bitmap.getByteCount() + 1023L) / 1024);
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, ScaledBitmap oldValue, ScaledBitmap newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);

            if (evicted) {
                // Keep a soft reference around to the bitmap as long as we can
                evictionCache.put(key, new SoftReference<>(oldValue));
            }
        }
    };
    // Cleared soft references still retain keys; bound the secondary index as well.
    private static final LruCache<String, SoftReference<ScaledBitmap>> evictionCache = new LruCache<>(64);

    private static String constructKey(CachedAppAssetLoader.LoaderTuple tuple) {
        return tuple.computer.uuid+"-"+tuple.app.getAppId();
    }

    public ScaledBitmap loadBitmapFromCache(CachedAppAssetLoader.LoaderTuple tuple) {
        String key = constructKey(tuple);
        synchronized (memoryCache) {
            ScaledBitmap bmp = memoryCache.get(key);
            if (bmp != null) {
                return bmp;
            }
            SoftReference<ScaledBitmap> ref = evictionCache.remove(key);
            bmp = ref != null ? ref.get() : null;
            if (bmp != null) {
                memoryCache.put(key, bmp);
            }
            return bmp;
        }
    }

    public void populateCache(CachedAppAssetLoader.LoaderTuple tuple, ScaledBitmap bitmap) {
        String key = constructKey(tuple);
        synchronized (memoryCache) {
            evictionCache.remove(key);
            memoryCache.put(key, bitmap);
        }
    }

    public void clearCache() {
        clearStaticCache();
    }

    /** Clears shared thumbnail/cover bitmaps from Java heap (e.g. before entering 2 GB stream). */
    public static void clearStaticCache() {
        // We must evict first because that will push all items into the eviction cache
        synchronized (memoryCache) {
            memoryCache.evictAll();
            evictionCache.evictAll();
        }
    }
}
