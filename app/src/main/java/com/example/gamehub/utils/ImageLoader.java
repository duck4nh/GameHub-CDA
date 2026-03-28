package com.example.gamehub.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    public interface Callback {
        void onComplete(boolean success);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 16)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    public static void load(@Nullable String url, ImageView target, @Nullable Callback callback) {
        if (url == null || url.trim().isEmpty()) {
            target.setImageDrawable(null);
            if (callback != null) {
                callback.onComplete(false);
            }
            return;
        }

        String normalizedUrl = url.trim();
        target.setTag(normalizedUrl);
        Bitmap cachedBitmap = CACHE.get(normalizedUrl);
        if (cachedBitmap != null) {
            target.setImageBitmap(cachedBitmap);
            if (callback != null) {
                callback.onComplete(true);
            }
            return;
        }

        WeakReference<ImageView> targetRef = new WeakReference<>(target);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = downloadBitmap(normalizedUrl);
            if (bitmap != null) {
                CACHE.put(normalizedUrl, bitmap);
            }
            MAIN_HANDLER.post(() -> {
                ImageView imageView = targetRef.get();
                if (imageView == null || !normalizedUrl.equals(imageView.getTag())) {
                    return;
                }
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                } else {
                    imageView.setImageDrawable(null);
                }
                if (callback != null) {
                    callback.onComplete(bitmap != null);
                }
            });
        });
    }

    @Nullable
    private static Bitmap downloadBitmap(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            try (InputStream inputStream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(inputStream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
