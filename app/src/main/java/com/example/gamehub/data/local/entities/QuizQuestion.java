package com.example.gamehub.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Quiz_Questions")
public class QuizQuestion {
    @PrimaryKey(autoGenerate = true)
    public int id;
}
