package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.util.List;

/**
 * Các truy vấn Room dùng để lấy câu hỏi Quiz offline theo chủ đề và độ khó.
 */
@Dao
public interface QuizDao {
    /** Insert câu hỏi import từ seed DB; trùng id thì thay thế. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuizQuestion> items);

    /** Kiểm tra số câu hỏi hiện có để quyết định có cần import asset DB không. */
    @Query("SELECT COUNT(*) FROM Quiz_Questions")
    int getCount();

    /** Lấy danh sách chủ đề để hiển thị ở màn thiết lập Quiz. */
    @Query("SELECT DISTINCT category FROM Quiz_Questions ORDER BY category ASC")
    List<String> getDistinctCategories();

    /** Lấy ngẫu nhiên câu hỏi khi người chơi không lọc chủ đề/độ khó. */
    @Query("SELECT * FROM Quiz_Questions ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestions(int limit);

    /** Lấy ngẫu nhiên câu hỏi chỉ lọc theo độ khó. */
    @Query("SELECT * FROM Quiz_Questions WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestionsByDifficulty(String difficulty, int limit);

    /** Lấy ngẫu nhiên câu hỏi chỉ lọc theo một hoặc nhiều chủ đề đã chọn. */
    @Query("SELECT * FROM Quiz_Questions WHERE category IN (:categories) ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestionsByCategories(List<String> categories, int limit);

    /** Lấy ngẫu nhiên câu hỏi lọc đồng thời theo chủ đề và độ khó. */
    @Query("SELECT * FROM Quiz_Questions WHERE category IN (:categories) AND difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestionsByCategoriesAndDifficulty(List<String> categories, String difficulty, int limit);
}
