package com.example.gamehub.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class GameRecord {
    public String record_id;
    public String uid;
    public String game_type;
    public int score;
    public long time_played;
    public String status;
    public long date;

    public GameRecord() {
    }

    public GameRecord(String recordId, String uid, String gameType, int score, long timePlayed, String status, long date) {
        this.record_id = recordId;
        this.uid = uid;
        this.game_type = gameType;
        this.score = score;
        this.time_played = timePlayed;
        this.status = status;
        this.date = date;
    }

    public String getRecordId() {
        return record_id;
    }

    public String getUid() {
        return uid;
    }

    public String getGameType() {
        return game_type;
    }

    public int getScore() {
        return score;
    }

    public long getTimePlayed() {
        return time_played;
    }

    public String getStatus() {
        return status;
    }

    public long getDate() {
        return date;
    }
}
