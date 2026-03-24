package com.example.gamehub.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Sudoku_Boards")
public class SudokuBoard {
    @PrimaryKey(autoGenerate = true)
    public int id;
}
