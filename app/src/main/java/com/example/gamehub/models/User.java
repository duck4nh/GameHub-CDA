package com.example.gamehub.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties // Giúp app không bị crash nếu Firestore có thêm trường lạ
public class User {
    public String uid;          // ID duy nhất từ Firebase Auth
    public String email;        // Email dùng đăng nhập
    public String nickname;     // Biệt danh hiển thị
    public String avatar_url;   // Link ảnh sinh từ DiceBear API [cite: 41, 68]
    public long total_score;    // Tổng điểm tích lũy
    public Timestamp created_at; // Mốc thời gian tạo tài khoản

    // BẮT BUỘC: Constructor rỗng cho Firebase Firestore
    public User() {
    }

    // Constructor dùng khi tạo mới người dùng lúc Đăng ký [cite: 52]
    public User(String uid, String email, String nickname, String avatar_url) {
        this.uid = uid;
        this.email = email;
        this.nickname = nickname;
        this.avatar_url = avatar_url;
        this.total_score = 0; // Mặc định mới tạo là 0 điểm
        this.created_at = Timestamp.now(); // Lấy thời gian hiện tại [cite: 68, 71]
    }
}