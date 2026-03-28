package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.LocalHistory;

import java.util.List;

@Dao
public interface HistoryDao {
    @Query("SELECT * FROM Local_History ORDER BY play_date DESC")
    List<LocalHistory> getAllNewestFirst();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalHistory> historyItems);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LocalHistory historyItem);

    @Query("SELECT COUNT(*) FROM Local_History")
    int getCount();

    @Query("SELECT AVG(time_spent) FROM Local_History")
    Double getAverageTimeSpent();

    @Query("SELECT COUNT(*) FROM Local_History WHERE lower(status) IN ('won', 'completed')")
    int getSuccessfulCount();

    @Query("SELECT COUNT(*) FROM Local_History WHERE is_synced = 0")
    int getUnsyncedCount();

    @Query("SELECT MIN(time_spent) FROM Local_History WHERE lower(game_name) LIKE '%' || lower(:gameName) || '%'")
    Long getBestTimeForGame(String gameName);

    @Query("SELECT * FROM Local_History WHERE is_synced = 0 AND lower(game_name) = lower(:gameName) ORDER BY play_date ASC")
    List<LocalHistory> getUnsyncedHistoryForGame(String gameName);

    @Query("UPDATE Local_History SET is_synced = 1 WHERE id = :historyId")
    void markSynced(int historyId);
}
