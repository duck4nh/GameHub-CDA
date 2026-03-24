package com.example.gamehub.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Memory_Levels")
public class MemoryLevel {
    @PrimaryKey(autoGenerate = true)
    public int level_id;
}
