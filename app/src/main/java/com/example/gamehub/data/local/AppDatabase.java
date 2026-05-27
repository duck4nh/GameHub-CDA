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
 * Room database trung tâm của GameHub.
 *
 * Ứng dụng lưu câu hỏi Quiz, level Memory, dữ liệu Sudoku, lịch sử local, bạn
 * bè và trạng thái Sudoku trong cùng file SQLite `gamehub.db`.
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

    /** Truy cập bảng câu hỏi Quiz để lọc chủ đề và lấy câu hỏi ngẫu nhiên. */
    public abstract QuizDao quizDao();
    /** Truy cập bảng level Memory để đọc cấu hình, best time và trạng thái mở khóa. */
    public abstract MemoryDao memoryDao();
    public abstract SudokuDao sudokuDao();
    public abstract SudokuGameStateDao sudokuGameStateDao();
    public abstract SudokuStatsDao sudokuStatsDao();
    /** Truy cập bảng lịch sử dùng chung cho kết quả Quiz/Memory và trạng thái sync. */
    public abstract HistoryDao historyDao();
    public abstract FriendDao friendDao();

    /**
     * Trả về singleton Room database và seed dữ liệu offline khi database được
     * khởi tạo lần đầu.
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
