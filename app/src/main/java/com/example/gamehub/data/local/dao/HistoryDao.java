package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.LocalHistory;

import java.util.List;

/**
 * Các truy vấn Room cho lịch sử ván chơi, thành tích tốt nhất và hàng đợi đồng bộ.
 */
@Dao
public interface HistoryDao {
    /** Danh sách lịch sử chính, ván mới nhất đứng trước. */
    @Query("SELECT * FROM Local_History ORDER BY play_date DESC")
    List<LocalHistory> getAllNewestFirst();

    /** Xóa dữ liệu mock/legacy mà không ảnh hưởng bản ghi hợp lệ hiện tại. */
    @Query("DELETE FROM Local_History WHERE game_name IN (:gameNames)")
    void deleteByExactGameNames(List<String> gameNames);

    /** Xóa toàn bộ lịch sử local, chỉ dùng cho luồng bảo trì rõ ràng. */
    @Query("DELETE FROM Local_History")
    void deleteAll();

    /** Insert hàng loạt khi cần restore hoặc migrate lịch sử. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalHistory> historyItems);

    /** Insert một ván đã hoàn thành và trả về id local tự sinh. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LocalHistory historyItem);

    /** Tổng số ván local của tất cả game. */
    @Query("SELECT COUNT(*) FROM Local_History")
    int getCount();

    /** Thời gian chơi trung bình dùng cho màn thống kê. */
    @Query("SELECT AVG(time_spent) FROM Local_History")
    Double getAverageTimeSpent();

    /** Đếm số ván thắng/hoàn thành để tính tỷ lệ thắng. */
    @Query("SELECT COUNT(*) FROM Local_History WHERE lower(status) IN ('won', 'completed')")
    int getSuccessfulCount();

    /** Số bản ghi local còn chờ đồng bộ Firebase. */
    @Query("SELECT COUNT(*) FROM Local_History WHERE is_synced = 0")
    int getUnsyncedCount();

    /** Hàng đợi đồng bộ theo thứ tự cũ trước để Firebase nhận dữ liệu ổn định. */
    @Query("SELECT * FROM Local_History WHERE is_synced = 0 ORDER BY play_date ASC")
    List<LocalHistory> getUnsyncedHistory();

    /** Truy vấn nhanh best time của một loại game. */
    @Query("SELECT MIN(time_spent) FROM Local_History WHERE lower(game_name) LIKE '%' || lower(:gameName) || '%' AND lower(status) IN ('won', 'completed') AND time_spent > 0")
    Long getBestTimeForGame(String gameName);

    /** Lấy đầy đủ record tốt nhất khi màn kết quả cần cả điểm và thời gian. */
    @Query("SELECT * FROM Local_History WHERE lower(game_name) LIKE '%' || lower(:gameName) || '%' AND lower(status) IN ('won', 'completed') AND time_spent > 0 ORDER BY time_spent ASC, play_date DESC LIMIT 1")
    LocalHistory getBestRecordForGame(String gameName);

    /** Lấy chi tiết một bản ghi lịch sử theo id. */
    @Query("SELECT * FROM Local_History WHERE id = :historyId LIMIT 1")
    LocalHistory getById(int historyId);

    /** Lấy bản ghi chưa sync của một game, hữu ích khi debug đồng bộ theo phạm vi. */
    @Query("SELECT * FROM Local_History WHERE is_synced = 0 AND lower(game_name) = lower(:gameName) ORDER BY play_date ASC")
    List<LocalHistory> getUnsyncedHistoryForGame(String gameName);

    /** Đánh dấu bản ghi local đã upload sau khi transaction Firebase thành công. */
    @Query("UPDATE Local_History SET is_synced = 1 WHERE id = :historyId")
    void markSynced(int historyId);
}
