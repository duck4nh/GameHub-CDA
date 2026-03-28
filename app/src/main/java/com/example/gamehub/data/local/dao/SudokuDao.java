package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.SudokuBoard;

import java.util.List;

@Dao
public interface SudokuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SudokuBoard> items);

    @Query("SELECT COUNT(*) FROM Sudoku_Boards")
    int getCount();

    @Query("SELECT * FROM Sudoku_Boards WHERE id = :boardId LIMIT 1")
    SudokuBoard getBoard(int boardId);

    @Query("SELECT * FROM Sudoku_Boards WHERE level = :level ORDER BY id ASC LIMIT 1")
    SudokuBoard getBoardByLevel(String level);

    @Query("SELECT * FROM Sudoku_Boards ORDER BY id ASC")
    List<SudokuBoard> getAllBoards();
}
