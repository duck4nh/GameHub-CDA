package com.example.gamehub.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.example.gamehub.data.local.entities.*;
import com.example.gamehub.data.local.dao.*;

@Database(entities = {QuizQuestion.class, SudokuBoard.class, MemoryLevel.class, LocalHistory.class, LocalFriend.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
}
