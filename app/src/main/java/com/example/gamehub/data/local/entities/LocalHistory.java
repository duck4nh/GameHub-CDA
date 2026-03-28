package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "Local_History")
public class LocalHistory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "game_name")
    public String gameName = "";

    @NonNull
    public String status = "";

    public int score;

    @ColumnInfo(name = "time_spent")
    public long timeSpent;

    @ColumnInfo(name = "play_date")
    public long playDate;

    @ColumnInfo(name = "is_synced")
    public boolean isSynced;

    public LocalHistory() {
    }

    @Ignore
    public LocalHistory(@NonNull String gameName, @NonNull String status, int score, long timeSpent, long playDate, boolean isSynced) {
        this.gameName = gameName;
        this.status = status;
        this.score = score;
        this.timeSpent = timeSpent;
        this.playDate = playDate;
        this.isSynced = isSynced;
    }
}
