package com.example.gamehub.models;

public class User {
    private final String uid;
    private final String email;
    private final String nickname;
    private final String avatarUrl;
    private final int totalScore;
    private final long createdAt;

    public User(String uid, String email, String nickname, String avatarUrl, int totalScore, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.totalScore = totalScore;
        this.createdAt = createdAt;
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
        return avatarUrl;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
