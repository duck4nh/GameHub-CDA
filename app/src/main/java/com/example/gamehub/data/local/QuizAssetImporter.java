package com.example.gamehub.data.local;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Import câu hỏi Quiz từ file SQLite đóng gói trong thư mục assets.
 *
 * File DB đi kèm giúp Quiz có thể chơi offline và không cần gọi mạng trước khi
 * bắt đầu ván.
 */
public final class QuizAssetImporter {
    private static final String ASSET_DB_PATH = "databases/quiz_questions_500_vi_entity_images.db";
    private static final String CACHE_DB_NAME = "quiz_questions_seed.db";

    private QuizAssetImporter() {
    }

    /**
     * Copy asset DB vào cache nếu cần, sau đó đọc bảng Quiz_Questions thành các
     * entity QuizQuestion để insert vào Room.
     */
    public static List<QuizQuestion> readQuestions(Context context) throws IOException {
        File cacheFile = ensureSeedDatabaseCopied(context);
        SQLiteDatabase database = SQLiteDatabase.openDatabase(cacheFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        List<QuizQuestion> questions = new ArrayList<>();
        Cursor cursor = database.query(
                "Quiz_Questions",
                new String[]{"id", "category", "question", "link_image", "opt_a", "opt_b", "opt_c", "opt_d", "correct_ans", "difficulty"},
                null,
                null,
                null,
                null,
                "id ASC"
        );
        try {
            while (cursor.moveToNext()) {
                questions.add(new QuizQuestion(
                        cursor.getInt(0),
                        value(cursor, 1),
                        value(cursor, 2),
                        value(cursor, 3),
                        value(cursor, 4),
                        value(cursor, 5),
                        value(cursor, 6),
                        value(cursor, 7),
                        value(cursor, 8),
                        value(cursor, 9)
                ));
            }
        } finally {
            cursor.close();
            database.close();
        }
        return questions;
    }

    /**
     * SQLiteDatabase cần đường dẫn file thật, nên asset phải được copy một lần
     * vào cache trước khi mở ở chế độ chỉ đọc.
     */
    private static File ensureSeedDatabaseCopied(Context context) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "quiz_seed");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Cannot create quiz seed cache directory.");
        }
        File cacheFile = new File(cacheDir, CACHE_DB_NAME);
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return cacheFile;
        }

        try (InputStream inputStream = context.getAssets().open(ASSET_DB_PATH);
             FileOutputStream outputStream = new FileOutputStream(cacheFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
        return cacheFile;
    }

    private static String value(Cursor cursor, int index) {
        String raw = cursor.isNull(index) ? "" : cursor.getString(index);
        return raw == null ? "" : raw.trim();
    }
}
