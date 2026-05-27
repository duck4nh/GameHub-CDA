package com.example.gamehub.data.local.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity cấu hình level Memory và tiến độ local.
 *
 * Bảng này lưu kích thước lưới, thời gian giới hạn, best time trên máy và trạng
 * thái level đã mở khóa hay chưa.
 */
@Entity(tableName = "Memory_Levels")
public class MemoryLevel {
    /** Số level hiển thị đồng thời là khóa chính. */
    @PrimaryKey
    @ColumnInfo(name = "level_id")
    public int levelId;

    /** Số hàng của board ở level này. */
    @ColumnInfo(name = "row_count")
    public int rowCount;

    /** Số cột của board ở level này. */
    @ColumnInfo(name = "column_count")
    public int columnCount;

    /** Thời gian giới hạn của level, tính bằng giây. */
    @ColumnInfo(name = "time_limit_sec")
    public long timeLimitSec;

    /** Thời gian hoàn thành tốt nhất local, 0 nghĩa là chưa có record. */
    @ColumnInfo(name = "best_time_ms")
    public long bestTimeMs;

    /** Level bị khóa vẫn hiển thị nhưng không thể bắt đầu. */
    @ColumnInfo(name = "is_unlocked")
    public boolean isUnlocked;

    /** Constructor rỗng bắt buộc cho Room. */
    public MemoryLevel() {
    }

    /** Constructor tiện ích dùng khi DatabaseSeeder sinh level. */
    @Ignore
    public MemoryLevel(int levelId, int rowCount, int columnCount, long timeLimitSec, long bestTimeMs, boolean isUnlocked) {
        this.levelId = levelId;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.timeLimitSec = timeLimitSec;
        this.bestTimeMs = bestTimeMs;
        this.isUnlocked = isUnlocked;
    }

    /** Tổng số cặp thẻ trong board của level. */
    @Ignore
    public int getPairCount() {
        return (rowCount * columnCount) / 2;
    }

    /** Nhãn ngắn hiển thị ở setup/result, ví dụ 4x5. */
    @Ignore
    public String getDisplayLabel() {
        return rowCount + "x" + columnCount;
    }
}
