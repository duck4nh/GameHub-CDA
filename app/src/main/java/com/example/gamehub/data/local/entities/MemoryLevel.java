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

    @ColumnInfo(name = "row_count")
    public int rowCount;

    @ColumnInfo(name = "column_count")
    public int columnCount;

    @ColumnInfo(name = "time_limit_sec")
    public long timeLimitSec;

    @ColumnInfo(name = "best_time_ms")
    public long bestTimeMs;

    @ColumnInfo(name = "is_unlocked")
    public boolean isUnlocked;

    public MemoryLevel() {
    }

    @Ignore
    public MemoryLevel(int levelId, int rowCount, int columnCount, long timeLimitSec, long bestTimeMs, boolean isUnlocked) {
        this.levelId = levelId;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.timeLimitSec = timeLimitSec;
        this.bestTimeMs = bestTimeMs;
        this.isUnlocked = isUnlocked;
    }

    @Ignore
    public int getPairCount() {
        return (rowCount * columnCount) / 2;
    }

    @Ignore
    public String getDisplayLabel() {
        return rowCount + "x" + columnCount;
    }
}
