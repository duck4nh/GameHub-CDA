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
        DocumentReference recordRef = firestore.collection("Game_Records").document(recordId);
        DocumentReference userRef = firestore.collection("Users").document(currentUid);

        try {
            Tasks.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot existingRecord = transaction.get(recordRef);
                if (existingRecord.exists()) {
                    return null;
                }

                Map<String, Object> recordPayload = new HashMap<>();
                recordPayload.put("record_id", recordId);
                recordPayload.put("uid", currentUid);
                recordPayload.put("game_type", mapGameType(history.gameName));
                recordPayload.put("score", history.score);
                recordPayload.put("time_played", history.timeSpent);
                recordPayload.put("status", mapStatus(history.status));
                recordPayload.put("date", history.playDate);
                transaction.set(recordRef, recordPayload);

                DocumentSnapshot userSnapshot = transaction.get(userRef);
                if (userSnapshot.exists()) {
                    long currentScore = readLong(userSnapshot.get("total_score"));
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("total_score", currentScore + history.score);
                    if (isBlank(userSnapshot.getString("nickname")) && !isBlank(cachedNickname)) {
                        updates.put("nickname", cachedNickname);
                    }
                    transaction.update(userRef, updates);
                } else {
                    FirebaseUser currentUser = auth.getCurrentUser();
                    Map<String, Object> newUser = new HashMap<>();
                    newUser.put("uid", currentUid);
                    newUser.put("email", currentUser != null && currentUser.getEmail() != null ? currentUser.getEmail() : "");
                    newUser.put("nickname", !isBlank(cachedNickname) ? cachedNickname : "Player");
                    newUser.put("avatar_url", "");
                    newUser.put("total_score", history.score);
                    newUser.put("created_at", System.currentTimeMillis());
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
