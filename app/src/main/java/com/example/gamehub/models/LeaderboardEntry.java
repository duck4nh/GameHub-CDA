package com.example.gamehub.models;

public class LeaderboardEntry {
    private final String uid;
    private final String nickname;
    private final int score;
    private final int rank;
    private final boolean currentUser;

    public LeaderboardEntry(String uid, String nickname, int score, int rank, boolean currentUser) {
        this.uid = uid;
        this.nickname = nickname;
        this.score = score;
        this.rank = rank;
        this.currentUser = currentUser;
    }

    public String getUid() {
        return uid;
    }

    public String getNickname() {
        return nickname;
    }

    public int getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }
}
