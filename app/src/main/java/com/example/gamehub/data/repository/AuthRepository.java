package com.example.gamehub.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.DatabaseSeeder;
import com.example.gamehub.data.local.entities.LocalFriend;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.models.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AppDatabase database;
    private final PreferenceManager prefManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AuthRepository(AppDatabase database, PreferenceManager prefManager) {
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.database = database;
        this.prefManager = prefManager;
    }

    // --- 1. ĐĂNG KÝ ---
    public void signUp(String email, String password, String nickname, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    String avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=" + nickname;
                    User newUser = new User(uid, email, nickname, avatarUrl);

                    firestore.collection("Users").document(uid).set(newUser)
                            .addOnSuccessListener(aVoid -> {
                                prefManager.saveLoginSession(uid, nickname, avatarUrl);
                                mainHandler.post(() -> callback.onSuccess("Đăng ký thành công!"));
                            })
                            .addOnFailureListener(e -> mainHandler.post(() -> callback.onError("Lỗi lưu hồ sơ: " + e.getMessage())));
                })
                .addOnFailureListener(e -> mainHandler.post(() -> callback.onError("Lỗi xác thực: " + e.getMessage())));
    }

    // --- 2. ĐĂNG NHẬP ---
    public void signIn(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    firestore.collection("Users").document(uid).get()
                            .addOnSuccessListener(document -> {
                                if (document.exists()) {
                                    String nickname = document.getString("nickname");
                                    String avatarUrl = document.getString("avatar_url");
                                    prefManager.saveLoginSession(uid, nickname, avatarUrl);
                                    
                                    new Thread(() -> {
                                        DatabaseSeeder.seedIfNeeded(database);
                                        
                                        syncFriends(uid, new SyncCallback() {
                                            @Override
                                            public void onSuccess() {
                                                syncHistory(uid, new SyncCallback() {
                                                    @Override
                                                    public void onSuccess() {
                                                        mainHandler.post(() -> callback.onSuccess("Đăng nhập và đồng bộ thành công!"));
                                                    }
                                                    @Override
                                                    public void onError(String error) {
                                                        mainHandler.post(() -> callback.onSuccess("Đăng nhập thành công (Lịch sử chưa được tải)"));
                                                    }
                                                });
                                            }
                                            @Override
                                            public void onError(String error) {
                                                mainHandler.post(() -> callback.onSuccess("Đăng nhập thành công (Bạn bè chưa được tải)"));
                                            }
                                        });
                                    }).start();
                                } else {
                                    mainHandler.post(() -> callback.onError("Không tìm thấy thông tin trên Cloud"));
                                }
                            })
                            .addOnFailureListener(e -> mainHandler.post(() -> callback.onError("Lỗi lấy dữ liệu: " + e.getMessage())));
                })
                .addOnFailureListener(e -> mainHandler.post(() -> callback.onError("Sai tài khoản hoặc mật khẩu")));
    }

    // --- 3. ĐỒNG BỘ BẠN BÈ ---
    public void syncFriends(String uid, SyncCallback callback) {
        // Thực hiện 2 truy vấn để lấy bạn bè (người gửi hoặc người nhận là mình)
        Task<QuerySnapshot> q1 = firestore.collection("Friendships")
                .whereEqualTo("from_uid", uid).whereEqualTo("status", "accepted").get();
        Task<QuerySnapshot> q2 = firestore.collection("Friendships")
                .whereEqualTo("to_uid", uid).whereEqualTo("status", "accepted").get();

        Tasks.whenAllComplete(q1, q2).addOnCompleteListener(task -> {
            new Thread(() -> {
                try {
                    Map<String, String> friendUids = new HashMap<>();
                    if (q1.isSuccessful()) {
                        for (DocumentSnapshot doc : q1.getResult()) friendUids.put(doc.getString("to_uid"), "accepted");
                    }
                    if (q2.isSuccessful()) {
                        for (DocumentSnapshot doc : q2.getResult()) friendUids.put(doc.getString("from_uid"), "accepted");
                    }

                    if (friendUids.isEmpty()) {
                        database.friendDao().deleteAllFriends();
                        mainHandler.post(callback::onSuccess);
                        return;
                    }

                    // Lấy thông tin nickname/avatar của các friend UID
                    List<LocalFriend> listToSync = new ArrayList<>();
                    for (String fUid : friendUids.keySet()) {
                        DocumentSnapshot userDoc = Tasks.await(firestore.collection("Users").document(fUid).get());
                        if (userDoc.exists()) {
                            listToSync.add(new LocalFriend(
                                    fUid,
                                    userDoc.getString("nickname") != null ? userDoc.getString("nickname") : "Người chơi",
                                    userDoc.getString("avatar_url"),
                                    "accepted"
                            ));
                        }
                    }

                    database.friendDao().deleteAllFriends();
                    database.friendDao().insertAll(listToSync);
                    mainHandler.post(callback::onSuccess);
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }).start();
        });
    }

    // --- 4. ĐỒNG BỘ LỊCH SỬ ---
    public void syncHistory(String uid, SyncCallback callback) {
        firestore.collection("Game_Records")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<LocalHistory> historyList = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String gameType = doc.getString("game_type");
                        String status = doc.getString("status");
                        long score = readLong(doc, "score");
                        long timePlayed = readLong(doc, "time_played");
                        long date = readLong(doc, "date");

                        if (gameType != null && status != null) {
                            historyList.add(new LocalHistory(
                                    gameType,
                                    status,
                                    (int) score,
                                    timePlayed,
                                    date > 0 ? date : System.currentTimeMillis(),
                                    true
                            ));
                        }
                    }
                    new Thread(() -> {
                        try {
                            database.historyDao().deleteAll();
                            database.historyDao().insertAll(historyList);
                            mainHandler.post(callback::onSuccess);
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError(e.getMessage()));
                        }
                    }).start();
                })
                .addOnFailureListener(e -> mainHandler.post(() -> callback.onError(e.getMessage())));
    }

    // --- 5. ĐĂNG XUẤT ---
    public void logout() {
        auth.signOut();
        prefManager.clear();
        new Thread(() -> {
            try {
                database.friendDao().deleteAllFriends();
                database.historyDao().deleteAll();
                database.sudokuGameStateDao().deleteAll();
                database.sudokuStatsDao().deleteAll();
                database.memoryDao().clearAll(); 
            } catch (Exception e) {
                Log.e("AuthRepo", "Logout Error", e);
            }
        }).start();
    }

    private long readLong(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date) return ((Date) value).getTime();
        return 0L;
    }

    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String error);
    }
}
