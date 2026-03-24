package com.example.gamehub.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Local_History")
public class LocalHistory {
    @PrimaryKey(autoGenerate = true)
    public int id;
}
