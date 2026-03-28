package com.example.gamehub.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    public String uid;
    public String email;
    public String nickname;
    public String avatar_url;
    public int total_score;
    public long created_at;

    public User() {
    }

    public User(String uid, String email, String nickname, String avatarUrl) {
        this(uid, email, nickname, avatarUrl, 0, System.currentTimeMillis());
    }

    public User(String uid, String email, String nickname, String avatarUrl, int totalScore, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.nickname = nickname;
        this.avatar_url = avatarUrl;
        this.total_score = totalScore;
        this.created_at = createdAt;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatar_url;
    }

    public int getTotalScore() {
        return total_score;
    }

    public long getCreatedAt() {
        return created_at;
    }
}
