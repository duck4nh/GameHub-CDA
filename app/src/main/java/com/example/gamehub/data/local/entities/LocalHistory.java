package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity lưu một ván chơi đã kết thúc trên thiết bị.
 *
 * Các bản ghi chưa sync được giữ trong máy và sẽ được GameRepository/SyncWorker
 * upload lên Firebase khi có mạng và có tài khoản đăng nhập.
 */
@Entity(tableName = "Local_History")
public class LocalHistory {
    /** Id local tự sinh, đồng thời dùng để tạo id Firebase ổn định. */
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Khóa tên game, ví dụ quiz hoặc memory. */
    @NonNull
    @ColumnInfo(name = "game_name")
    public String gameName = "";

    /** Trạng thái kết quả, thường là won/lost/completed. */
    @NonNull
    public String status = "";

    /** Điểm cuối ván do ViewModel/manager của game tính. */
    public int score;

    /** Tổng thời gian chơi, tính bằng mili giây. */
    @ColumnInfo(name = "time_spent")
    public long timeSpent;

    /** Timestamp hệ thống tại thời điểm kết thúc ván. */
    @ColumnInfo(name = "play_date")
    public long playDate;

    /** False cho đến khi đồng bộ Firebase thành công. */
    @ColumnInfo(name = "is_synced")
    public boolean isSynced;

    /** Thông tin phụ hiển thị trong lịch sử, ví dụ nhãn level Memory. */
    @NonNull
    public String detail = "";

    /** Bộ đếm lượt tùy chọn; Memory dùng để lưu số lượt đoán cặp. */
    @ColumnInfo(name = "attempt_count")
    public int attemptCount;

    /** Constructor rỗng bắt buộc cho Room. */
    public LocalHistory() {
    }

    /** Constructor cho game chỉ cần các trường lịch sử cơ bản. */
    @Ignore
    public LocalHistory(@NonNull String gameName, @NonNull String status, int score, long timeSpent, long playDate, boolean isSynced) {
        this(gameName, status, score, timeSpent, playDate, isSynced, "", 0);
    }

    /** Constructor cho game cần thêm detail hoặc số lượt đoán. */
    @Ignore
    public LocalHistory(@NonNull String gameName, @NonNull String status, int score, long timeSpent, long playDate, boolean isSynced, @NonNull String detail, int attemptCount) {
        this.gameName = gameName;
        this.status = status;
        this.score = score;
        this.timeSpent = timeSpent;
        this.playDate = playDate;
        this.isSynced = isSynced;
        this.detail = detail;
        this.attemptCount = attemptCount;
    }
}
