package com.example.gamehub.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Friendship {
    public String doc_id;      // ID tự sinh cho mỗi lượt tương tác [cite: 71]
    public String sender_id;   // uid của người chủ động gửi lời mời [cite: 71]
    public String receiver_id; // uid của người nhận lời mời [cite: 71]
    public String status;      // Trạng thái (pending hoặc accepted) [cite: 71]
    public Timestamp updated_at; // Thời gian cập nhật để sắp xếp [cite: 71]

    public Friendship() {
    } // Bắt buộc cho Firestore

    public Friendship(String sender_id, String receiver_id, String status) {
        this.sender_id = sender_id;
        this.receiver_id = receiver_id;
        this.status = status;
        this.updated_at = Timestamp.now();
    }
}