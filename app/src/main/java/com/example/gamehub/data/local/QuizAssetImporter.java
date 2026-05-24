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
 * Imports bundled quiz questions from an asset SQLite database.
 *
 * The packaged DB keeps the app usable offline and avoids requiring a network
 * request before a quiz session can start.
 */
public final class QuizAssetImporter {
    private static final String ASSET_DB_PATH = "databases/quiz_questions_500_vi_entity_images.db";
    private static final String CACHE_DB_NAME = "quiz_questions_seed.db";

    private QuizAssetImporter() {
    }

    /**
     * Copies the asset DB into cache if needed and reads Quiz_Questions rows
     * into Room entity objects.
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
     * SQLiteDatabase needs a real filesystem path, so the asset is copied once
     * to the app cache before opening it read-only.
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
