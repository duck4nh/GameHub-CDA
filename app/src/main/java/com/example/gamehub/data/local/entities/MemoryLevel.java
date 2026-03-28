package com.example.gamehub.data.local.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "Memory_Levels")
public class MemoryLevel {
    @PrimaryKey
    @ColumnInfo(name = "level_id")
    public int levelId;

    @ColumnInfo(name = "grid_size")
    public int gridSize;

    @ColumnInfo(name = "time_limit")
    public long timeLimit;

    @ColumnInfo(name = "best_time")
    public long bestTime;

    @ColumnInfo(name = "is_unlocked")
    public boolean isUnlocked;

    public MemoryLevel() {
    }

    @Ignore
    public MemoryLevel(int levelId, int gridSize, long timeLimit, long bestTime, boolean isUnlocked) {
        this.levelId = levelId;
        this.gridSize = gridSize;
        this.timeLimit = timeLimit;
        this.bestTime = bestTime;
        this.isUnlocked = isUnlocked;
    }
}
