package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "Sudoku_Boards")
public class SudokuBoard {
    @PrimaryKey
    public int id;

    @NonNull
    public String level = "easy";

    // Repo implementation uses the conventional meaning:
    // initialMatrix = puzzle with blanks, solutionMatrix = completed answer.
    @ColumnInfo(name = "initial_matrix")
    public String initialMatrix = "";

    @ColumnInfo(name = "solution_matrix")
    public String solutionMatrix = "";

    public SudokuBoard() {
    }

    @Ignore
    public SudokuBoard(int id, @NonNull String level, String initialMatrix, String solutionMatrix) {
        this.id = id;
        this.level = level;
        this.initialMatrix = initialMatrix == null ? "" : initialMatrix;
        this.solutionMatrix = solutionMatrix == null ? "" : solutionMatrix;
    }
}
