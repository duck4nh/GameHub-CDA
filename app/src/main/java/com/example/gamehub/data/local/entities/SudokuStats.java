package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "Sudoku_Stats")
public class SudokuStats {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String level = "easy";

    @ColumnInfo(name = "games_played")
    public int gamesPlayed;

    @ColumnInfo(name = "games_won")
    public int gamesWon;

    @ColumnInfo(name = "best_time")
    public long bestTime;

    public SudokuStats() {
    }

    @Ignore
    public SudokuStats(@NonNull String level, int gamesPlayed, int gamesWon, long bestTime) {
        this.level = level;
        this.gamesPlayed = gamesPlayed;
        this.gamesWon = gamesWon;
        this.bestTime = bestTime;
    }
}
