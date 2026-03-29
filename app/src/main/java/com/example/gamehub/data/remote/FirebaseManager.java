package com.example.gamehub.data.remote;

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

public class FirebaseManager {
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

    public boolean syncHistoryRecord(LocalHistory history, String currentUid, String cachedNickname) {
        if (history == null || currentUid == null || currentUid.trim().isEmpty()) {
            return false;
        }

        String recordId = buildRecordId(currentUid, history.id);
        DocumentReference recordRef = firestore.collection(COL_RECORDS).document(recordId);
        DocumentReference userRef = firestore.collection(COL_USERS).document(currentUid);

        try {
            Tasks.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot existingRecord = transaction.get(recordRef);
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

                DocumentSnapshot userSnapshot = transaction.get(userRef);
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
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private String buildRecordId(String currentUid, int localHistoryId) {
        return String.format(Locale.US, "local_%s_%d", currentUid, localHistoryId);
    }

    private String mapGameType(String gameName) {
        String normalized = gameName == null ? "" : gameName.toLowerCase(Locale.getDefault());
        if (normalized.contains("đố vui")) {
            return "Quiz";
        }
        if (normalized.contains("ghi nhớ")) {
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
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return 0L;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
