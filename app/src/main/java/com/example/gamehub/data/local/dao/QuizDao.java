package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.util.List;

@Dao
public interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuizQuestion> items);

    @Query("SELECT COUNT(*) FROM Quiz_Questions")
    int getCount();

    @Query("SELECT DISTINCT category FROM Quiz_Questions ORDER BY category ASC")
    List<String> getDistinctCategories();

    @Query("SELECT * FROM Quiz_Questions WHERE difficulty = :difficulty ORDER BY id ASC LIMIT :limit")
    List<QuizQuestion> getQuestionsByDifficulty(String difficulty, int limit);

    @Query("SELECT * FROM Quiz_Questions WHERE category = :category ORDER BY id ASC LIMIT :limit")
    List<QuizQuestion> getQuestionsByCategory(String category, int limit);

    @Query("SELECT * FROM Quiz_Questions WHERE category = :category AND difficulty = :difficulty ORDER BY id ASC LIMIT :limit")
    List<QuizQuestion> getQuestionsByCategoryAndDifficulty(String category, String difficulty, int limit);

    @Query("SELECT * FROM Quiz_Questions ORDER BY id ASC LIMIT :limit")
    List<QuizQuestion> getQuestions(int limit);
}
