package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.util.List;

/**
 * Room queries for selecting offline quiz questions by category/difficulty.
 */
@Dao
public interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuizQuestion> items);

    @Query("SELECT COUNT(*) FROM Quiz_Questions")
    int getCount();

    @Query("SELECT DISTINCT category FROM Quiz_Questions ORDER BY category ASC")
    List<String> getDistinctCategories();

    @Query("SELECT * FROM Quiz_Questions ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestions(int limit);

    @Query("SELECT * FROM Quiz_Questions WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestionsByDifficulty(String difficulty, int limit);

    @Query("SELECT * FROM Quiz_Questions WHERE category IN (:categories) ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestionsByCategories(List<String> categories, int limit);

    @Query("SELECT * FROM Quiz_Questions WHERE category IN (:categories) AND difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit")
    List<QuizQuestion> getRandomQuestionsByCategoriesAndDifficulty(List<String> categories, String difficulty, int limit);
}
