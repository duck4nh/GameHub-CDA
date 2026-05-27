package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.MemoryLevel;

import java.util.List;

/**
 * Các truy vấn Room cho level Memory và tiến độ local của người chơi.
 */
@Dao
public interface MemoryDao {
    /** Insert danh sách level đã sinh; trùng level_id thì thay thế. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MemoryLevel> items);

    /** Kiểm tra dữ liệu level Memory đã được seed hay chưa. */
    @Query("SELECT COUNT(*) FROM Memory_Levels")
    int getCount();

    /** Lấy danh sách level theo thứ tự tăng dần để hiển thị ở màn chọn level. */
    @Query("SELECT * FROM Memory_Levels ORDER BY level_id ASC")
    List<MemoryLevel> getAllLevels();

    /** Đọc cấu hình một level trước khi cập nhật tiến độ. */
    @Query("SELECT * FROM Memory_Levels WHERE level_id = :levelId LIMIT 1")
    MemoryLevel getLevel(int levelId);

    /** Lấy level mở khóa đầu tiên khi tiến độ chưa có dữ liệu rõ ràng. */
    @Query("SELECT * FROM Memory_Levels WHERE is_unlocked = 1 ORDER BY level_id ASC LIMIT 1")
    MemoryLevel getFirstUnlockedLevel();

    /** Xóa toàn bộ level trước khi seed lại danh sách cố định. */
    @Query("DELETE FROM Memory_Levels")
    void clearAll();

    /** Lưu thời gian hoàn thành tốt nhất sau khi người chơi thắng level. */
    @Query("UPDATE Memory_Levels SET best_time_ms = :bestTimeMs WHERE level_id = :levelId")
    void updateBestTime(int levelId, long bestTimeMs);

    /** Mở khóa level tiếp theo sau một ván Memory thành công. */
    @Query("UPDATE Memory_Levels SET is_unlocked = 1 WHERE level_id = :levelId")
    void unlockLevel(int levelId);
}
