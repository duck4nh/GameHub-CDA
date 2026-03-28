package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Local_Friends") // Tên bảng chuẩn theo thiết kế [cite: 26, 84]
public class LocalFriend {
    @PrimaryKey
    @NonNull // CỰC KỲ QUAN TRỌNG: String Primary Key không được null
    public String friend_uid;   // ID lấy từ Firebase

    public String nickname;
    public String avatar_path;  // Đường dẫn ảnh cache
    public String status;       // "pending" hoặc "accepted"

    public LocalFriend(@NonNull String friend_uid, String nickname, String avatar_path, String status) {
        this.friend_uid = friend_uid;
        this.nickname = nickname;
        this.avatar_path = avatar_path;
        this.status = status;
    }
}