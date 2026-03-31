package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.SudokuGameState;

@Dao
public interface SudokuGameStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SudokuGameState state);

    @Query("SELECT * FROM Sudoku_Game_State ORDER BY last_played DESC LIMIT 1")
    SudokuGameState getLatestState();

    @Query("SELECT * FROM Sudoku_Game_State WHERE board_id = :boardId ORDER BY last_played DESC LIMIT 1")
    SudokuGameState getLatestStateForBoard(int boardId);

    @Query("DELETE FROM Sudoku_Game_State WHERE board_id = :boardId")
    void clearStateForBoard(int boardId);

    @Query("DELETE FROM Sudoku_Game_State")
    void deleteAll();
}
