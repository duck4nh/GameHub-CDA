package com.example.gamehub.models;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
public class User {
    private String uid;
    private String email;
    private String nickname;
    private String avatar_url;
    private int total_score;
    private long created_at;

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

    @PropertyName("uid")
    public String getUid() {
        return uid;
    }

    @PropertyName("uid")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @PropertyName("email")
    public String getEmail() {
        return email;
    }

    @PropertyName("email")
    public void setEmail(String email) {
        this.email = email;
    }

    @PropertyName("nickname")
    public String getNickname() {
        return nickname;
    }

    @PropertyName("nickname")
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    @PropertyName("avatar_url")
    public String getAvatarUrl() {
        return avatar_url;
    }

    @PropertyName("avatar_url")
    public void setAvatarUrl(String avatarUrl) {
        this.avatar_url = avatarUrl;
    }

    @PropertyName("total_score")
    public int getTotalScore() {
        return total_score;
    }

    @PropertyName("total_score")
    public void setTotalScore(int totalScore) {
        this.total_score = totalScore;
    }

    @PropertyName("created_at")
    public long getCreatedAt() {
        return created_at;
    }

    @PropertyName("created_at")
    public void setCreatedAt(long createdAt) {
        this.created_at = createdAt;
    }
}
