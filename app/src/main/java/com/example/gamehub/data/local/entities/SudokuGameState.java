package com.example.gamehub.data.local.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "Sudoku_Game_State")
public class SudokuGameState {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "board_id")
    public int boardId;

    @ColumnInfo(name = "current_matrix")
    public String currentMatrix = "";

    @ColumnInfo(name = "elapsed_time")
    public long elapsedTime;

    @ColumnInfo(name = "last_played")
    public long lastPlayed;

    public SudokuGameState() {
    }

    @Ignore
    public SudokuGameState(int boardId, String currentMatrix, long elapsedTime, long lastPlayed) {
        this.boardId = boardId;
        this.currentMatrix = currentMatrix == null ? "" : currentMatrix;
        this.elapsedTime = elapsedTime;
        this.lastPlayed = lastPlayed;
    }
}
