package com.example.gamehub.data.remote;

import android.util.Log;

import com.example.gamehub.data.local.entities.LocalHistory;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small Firestore helper used by the repository and background worker.
 *
 * It converts LocalHistory rows into Game_Records documents and updates the
 * matching Users document with aggregate score data.
 */
public class FirebaseManager {
    public static final class SyncHistoryResult {
        public final boolean success;
        public final String message;

        public SyncHistoryResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }
    }

    private static final String TAG = "FirebaseManager";
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // Định nghĩa hằng số để tránh sai sót chính tả và viết hoa/thường
    public static final String COL_USERS = "Users";
    public static final String COL_RECORDS = "Game_Records";
    
    public static final String FIELD_UID = "uid";
    public static final String FIELD_NICKNAME = "nickname";
    public static final String FIELD_AVATAR_URL = "avatar_url";
    public static final String FIELD_TOTAL_SCORE = "total_score";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_CREATED_AT = "created_at";

    public boolean canSyncSudokuResults() {
        FirebaseUser currentUser = auth.getCurrentUser();
        return currentUser != null && canSyncHistoryResults(currentUser.getUid());
    }

    public boolean canSyncHistoryResults(String currentUid) {
        return currentUid != null && !currentUid.trim().isEmpty();
    }

    /**
     * Convenience wrapper for callers that only need success/failure.
     */
    public boolean syncHistoryRecord(LocalHistory history, String currentUid, String cachedNickname) {
        return syncHistoryRecordDetailed(history, currentUid, cachedNickname).success;
    }

    /**
     * Writes one finished game into Firestore inside a transaction so the
     * record insert and user total-score update stay consistent.
     */
    public SyncHistoryResult syncHistoryRecordDetailed(LocalHistory history, String currentUid, String cachedNickname) {
        if (history == null || currentUid == null || currentUid.trim().isEmpty()) {
            return new SyncHistoryResult(false, "Thiếu tài khoản hiện tại để đồng bộ.");
        }

        String recordId = buildRecordId(currentUid, history.id);
        DocumentReference recordRef = firestore.collection(COL_RECORDS).document(recordId);
        DocumentReference userRef = firestore.collection(COL_USERS).document(currentUid);

        try {
            Tasks.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot existingRecord = transaction.get(recordRef);
                DocumentSnapshot userSnapshot = transaction.get(userRef);
                if (existingRecord.exists()) {
                    return null;
                }

                Map<String, Object> recordPayload = new HashMap<>();
                recordPayload.put("record_id", recordId);
                recordPayload.put(FIELD_UID, currentUid);
                recordPayload.put("game_type", mapGameType(history.gameName));
                recordPayload.put("score", history.score);
                recordPayload.put("time_played", history.timeSpent);
                recordPayload.put("status", mapStatus(history.status));
                recordPayload.put("date", history.playDate);
                transaction.set(recordRef, recordPayload);

                if (userSnapshot.exists()) {
                    long currentScore = readLong(userSnapshot.get(FIELD_TOTAL_SCORE));
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(FIELD_TOTAL_SCORE, currentScore + history.score);
                    
                    // Kiểm tra nickname hiện tại trong DB, nếu trống mới cập nhật từ cache
                    String dbNickname = userSnapshot.getString(FIELD_NICKNAME);
                    if (isBlank(dbNickname) && !isBlank(cachedNickname)) {
                        updates.put(FIELD_NICKNAME, cachedNickname);
                    }
                    transaction.update(userRef, updates);
                } else {
                    FirebaseUser currentUser = auth.getCurrentUser();
                    Map<String, Object> newUser = new HashMap<>();
                    newUser.put(FIELD_UID, currentUid);
                    newUser.put(FIELD_EMAIL, currentUser != null && currentUser.getEmail() != null ? currentUser.getEmail() : "");
                    newUser.put(FIELD_NICKNAME, !isBlank(cachedNickname) ? cachedNickname : "Player");
                    newUser.put(FIELD_AVATAR_URL, "");
                    newUser.put(FIELD_TOTAL_SCORE, history.score);
                    newUser.put(FIELD_CREATED_AT, System.currentTimeMillis());
                    transaction.set(userRef, newUser);
                }
                return null;
            }));
            Log.i(TAG, "Synced history id=" + history.id + " to Game_Records/" + recordId);
            return new SyncHistoryResult(true, "Đã đồng bộ trận lên Firebase.");
        } catch (Exception error) {
            String rawMessage = error.getMessage();
            String message = rawMessage == null || rawMessage.trim().isEmpty()
                    ? "Không ghi được Game_Records lên Firebase."
                    : rawMessage.trim();
            Log.e(TAG, "Failed to sync history id=" + history.id + " to Firebase. uid=" + currentUid, error);
            return new SyncHistoryResult(false, message);
        }
    }

    /**
     * Builds a stable document id from the current user and local row id.
     */
    private String buildRecordId(String currentUid, int localHistoryId) {
        return String.format(Locale.US, "local_%s_%d", currentUid, localHistoryId);
    }

    private String mapGameType(String gameName) {
        String normalized = gameName == null ? "" : gameName.toLowerCase(Locale.getDefault());
        if (normalized.contains("quiz") || normalized.contains("đố vui")) {
            return "Quiz";
        }
        if (normalized.contains("memory") || normalized.contains("ghi nhớ")) {
            return "Memory";
        }
        return "Sudoku";
    }

    private String mapStatus(String status) {
        if (status == null) {
            return "Lose";
        }
        return "won".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status) ? "Win" : "Lose";
    }

    private long readLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
