package com.example.gamehub.data.repository;

import android.util.Log;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.entities.LocalFriend;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AuthRepository {
    // --- PHẦN QUỲNH THIẾU: KHAI BÁO BIẾN ---
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AppDatabase database;
    private final PreferenceManager prefManager;

    // --- PHẦN QUỲNH THIẾU: HÀM KHỞI TẠO (CONSTRUCTOR) ---
    public AuthRepository(AppDatabase database, PreferenceManager prefManager) {
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.database = database;
        this.prefManager = prefManager;
    }

    // --- 1. ĐĂNG KÝ (SIGN UP) ---
    public void signUp(String email, String password, String nickname, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    String avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + nickname;
                    User newUser = new User(uid, email, nickname, avatarUrl);

                    firestore.collection("Users").document(uid).set(newUser)
                            .addOnSuccessListener(aVoid -> {
                                prefManager.saveLoginSession(uid, nickname);
                                callback.onSuccess("Đăng ký thành công!");
                            })
                            .addOnFailureListener(e -> callback.onError("Lỗi lưu hồ sơ: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("Lỗi xác thực: " + e.getMessage()));
    }

    // --- 2. ĐĂNG NHẬP (SIGN IN) ---
    public void signIn(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    firestore.collection("Users").document(uid).get()
                            .addOnSuccessListener(document -> {
                                if (document.exists()) {
                                    String nickname = document.getString("nickname");
                                    prefManager.saveLoginSession(uid, nickname);
                                    callback.onSuccess("Đăng nhập thành công!");
                                }
                            });
                })
                .addOnFailureListener(e -> callback.onError("Sai tài khoản hoặc mật khẩu"));
    }

    // --- 3. ĐỒNG BỘ BẠN BÈ (SYNC) ---
    public void syncFriends(String uid, SyncCallback callback) {
        firestore.collection("Friendships")
                .whereEqualTo("user_id", uid)
                .whereEqualTo("status", "accepted")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<LocalFriend> listToSync = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        listToSync.add(new LocalFriend(
                                doc.getString("friend_uid"),
                                doc.getString("friend_nickname"),
                                doc.getString("friend_avatar"),
                                "accepted"
                        ));
                    }

                    new Thread(() -> {
                        try {
                            database.friendDao().deleteAllFriends();
                            database.friendDao().insertAll(listToSync);
                            callback.onSuccess();
                        } catch (Exception e) {
                            Log.e("AuthRepo", "Sync Error", e);
                            callback.onError(e.getMessage());
                        }
                    }).start();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- 4. ĐĂNG XUẤT (LOGOUT) ---
    public void logout() {
        auth.signOut();
        prefManager.clear();
        new Thread(() -> database.friendDao().deleteAllFriends()).start();
    }

    // --- INTERFACES ---
    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String error);
    }
}