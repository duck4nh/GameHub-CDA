package com.example.gamehub.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bộ tải ảnh đơn giản cho URL minh họa câu hỏi Quiz.
 *
 * Lớp này tải Bitmap ở background thread, cache trong bộ nhớ và trả kết quả về
 * main thread. Glide vẫn được dùng ở các nơi cần xử lý ảnh phức tạp hơn như
 * avatar hồ sơ.
 */
public final class ImageLoader {
    public interface Callback {
        void onComplete(boolean success);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; GameHub) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36";
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 16)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    /**
     * Tải ảnh từ URL vào ImageView đích.
     *
     * Tag của ImageView lưu URL đang yêu cầu để response mạng đến muộn không ghi
     * đè ảnh cũ lên view đã được bind cho câu hỏi khác.
     */
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

    /**
     * Thực hiện request HTTP thật sự và decode byte response thành Bitmap.
     */
    @Nullable
    private static Bitmap downloadBitmap(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setDoInput(true);
            connection.setUseCaches(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return null;
            }
            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                byte[] bytes = outputStream.toByteArray();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
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
