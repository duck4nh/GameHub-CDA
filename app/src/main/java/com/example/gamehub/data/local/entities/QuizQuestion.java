package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity câu hỏi Quiz offline được import từ seed database đóng gói.
 */
@Entity(tableName = "Quiz_Questions")
public class QuizQuestion {
    /** Id câu hỏi lấy từ SQLite seed database. */
    @PrimaryKey
    public int id;

    /** Chủ đề hiển thị ở bộ lọc setup và tiêu đề khi chơi. */
    @NonNull
    public String category = "";

    /** Nội dung câu hỏi hiển thị cho người chơi. */
    @NonNull
    public String question = "";

    /** URL ảnh minh họa, có thể rỗng nếu câu hỏi không có ảnh. */
    @ColumnInfo(name = "link_image")
    public String linkImage = "";

    /** Phương án trả lời A. */
    @ColumnInfo(name = "opt_a")
    public String optionA = "";

    /** Phương án trả lời B. */
    @ColumnInfo(name = "opt_b")
    public String optionB = "";

    /** Phương án trả lời C. */
    @ColumnInfo(name = "opt_c")
    public String optionC = "";

    /** Phương án trả lời D. */
    @ColumnInfo(name = "opt_d")
    public String optionD = "";

    /** Khóa đáp án đúng, kỳ vọng là A, B, C hoặc D. */
    @ColumnInfo(name = "correct_ans")
    public String correctAnswer = "";

    /** Độ khó dùng cho bộ lọc và tính điểm thưởng. */
    @NonNull
    public String difficulty = "easy";

    /** Constructor rỗng bắt buộc cho Room. */
    public QuizQuestion() {
    }

    /** Constructor tiện ích dùng khi QuizAssetImporter đọc từng dòng từ asset DB. */
    @Ignore
    public QuizQuestion(int id, @NonNull String category, @NonNull String question, String linkImage,
                        String optionA, String optionB, String optionC, String optionD,
                        String correctAnswer, @NonNull String difficulty) {
        this.id = id;
        this.category = category;
        this.question = question;
        this.linkImage = linkImage == null ? "" : linkImage;
        this.optionA = optionA == null ? "" : optionA;
        this.optionB = optionB == null ? "" : optionB;
        this.optionC = optionC == null ? "" : optionC;
        this.optionD = optionD == null ? "" : optionD;
        this.correctAnswer = correctAnswer == null ? "" : correctAnswer;
        this.difficulty = difficulty;
    }
}
