package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.SudokuStats;

@Dao
public interface SudokuStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SudokuStats stats);

    @Query("SELECT * FROM Sudoku_Stats WHERE level = :level LIMIT 1")
    SudokuStats getStatsForLevel(String level);

    @Query("UPDATE Sudoku_Stats SET games_played = :gamesPlayed, games_won = :gamesWon, best_time = :bestTime WHERE id = :id")
    void updateStats(int id, int gamesPlayed, int gamesWon, long bestTime);

    @Query("DELETE FROM Sudoku_Stats")
    void deleteAll();
}
