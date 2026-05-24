package com.example.gamehub.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.gamehub.data.local.dao.FriendDao;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.dao.MemoryDao;
import com.example.gamehub.data.local.dao.QuizDao;
import com.example.gamehub.data.local.dao.SudokuDao;
import com.example.gamehub.data.local.dao.SudokuGameStateDao;
import com.example.gamehub.data.local.dao.SudokuStatsDao;
import com.example.gamehub.data.local.entities.LocalFriend;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.local.entities.SudokuBoard;
import com.example.gamehub.data.local.entities.SudokuGameState;
import com.example.gamehub.data.local.entities.SudokuStats;

/**
 * Central Room database for GameHub.
 *
 * The app stores quiz questions, memory levels, sudoku data, local history,
 * friends, and saved sudoku state in one shared SQLite file: gamehub.db.
 */
@Database(
        entities = {
                QuizQuestion.class,
                SudokuBoard.class,
                MemoryLevel.class,
                LocalHistory.class,
                LocalFriend.class,
                SudokuGameState.class,
                SudokuStats.class
        },
        version = 6,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract QuizDao quizDao();
    public abstract MemoryDao memoryDao();
    public abstract SudokuDao sudokuDao();
    public abstract SudokuGameStateDao sudokuGameStateDao();
    public abstract SudokuStatsDao sudokuStatsDao();
    public abstract HistoryDao historyDao();
    public abstract FriendDao friendDao();

    /**
     * Returns the singleton Room instance and seeds bundled data the first time
     * the database is created.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "gamehub.db")
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
                    DatabaseSeeder.seedIfNeeded(instance);
                }
            }
        }
        return instance;
    }
}
