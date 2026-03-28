package com.example.gamehub.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.gamehub.data.local.entities.*;
import com.example.gamehub.data.local.dao.*;

// Liệt kê đầy đủ 5 bảng theo thiết kế tổng thể của nhóm 3 [cite: 19, 26]
@Database(entities = {
//        QuizQuestion.class,
//        SudokuBoard.class,
//        MemoryLevel.class,
//        LocalHistory.class,
        LocalFriend.class
}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    // --- KHAI BÁO CÁC DAO (Phải có đủ để Room không báo lỗi) ---
    public abstract FriendDao friendDao(); // Phần của Quỳnh [cite: 55, 83]

    // Lưu ý: Nếu bạn chưa có các file DAO của đồng đội, hãy tạm thời comment
    // các Entity tương ứng ở trên dòng @Database để hết lỗi đỏ nhé.
    // public abstract QuizDao quizDao();
    // public abstract SudokuDao sudokuDao();
    // public abstract HistoryDao historyDao();

    // --- SINGLETON PATTERN ---
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "gamehub_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}