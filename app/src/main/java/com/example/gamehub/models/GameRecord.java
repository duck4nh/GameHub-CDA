package com.example.gamehub.models;

public class GameRecord {
    private final String recordId;
    private final String uid;
    private final String gameType;
    private final int score;
    private final long timePlayed;
    private final String status;
    private final long date;

    public GameRecord(String recordId, String uid, String gameType, int score, long timePlayed, String status, long date) {
        this.recordId = recordId;
        this.uid = uid;
        this.gameType = gameType;
        this.score = score;
        this.timePlayed = timePlayed;
        this.status = status;
        this.date = date;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getUid() {
        return uid;
    }

    public String getGameType() {
        return gameType;
    }

    public int getScore() {
        return score;
    }

    public long getTimePlayed() {
        return timePlayed;
    }

    public String getStatus() {
        return status;
    }

    public long getDate() {
        return date;
    }
}
