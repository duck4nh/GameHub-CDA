package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.MemoryLevel;

import java.util.List;

/**
 * Room queries for Memory levels and local progress tracking.
 */
@Dao
public interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MemoryLevel> items);

    @Query("SELECT COUNT(*) FROM Memory_Levels")
    int getCount();

    @Query("SELECT * FROM Memory_Levels ORDER BY level_id ASC")
    List<MemoryLevel> getAllLevels();

    @Query("SELECT * FROM Memory_Levels WHERE level_id = :levelId LIMIT 1")
    MemoryLevel getLevel(int levelId);

    @Query("SELECT * FROM Memory_Levels WHERE is_unlocked = 1 ORDER BY level_id ASC LIMIT 1")
    MemoryLevel getFirstUnlockedLevel();

    @Query("DELETE FROM Memory_Levels")
    void clearAll();

    @Query("UPDATE Memory_Levels SET best_time_ms = :bestTimeMs WHERE level_id = :levelId")
    void updateBestTime(int levelId, long bestTimeMs);

    @Query("UPDATE Memory_Levels SET is_unlocked = 1 WHERE level_id = :levelId")
    void unlockLevel(int levelId);
}
