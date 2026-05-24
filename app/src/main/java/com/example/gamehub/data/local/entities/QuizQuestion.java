package com.example.gamehub.data.local.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Offline quiz question row imported from the packaged seed database.
 */
@Entity(tableName = "Quiz_Questions")
public class QuizQuestion {
    @PrimaryKey
    public int id;

    @NonNull
    public String category = "";

    @NonNull
    public String question = "";

    @ColumnInfo(name = "link_image")
    public String linkImage = "";

    @ColumnInfo(name = "opt_a")
    public String optionA = "";

    @ColumnInfo(name = "opt_b")
    public String optionB = "";

    @ColumnInfo(name = "opt_c")
    public String optionC = "";

    @ColumnInfo(name = "opt_d")
    public String optionD = "";

    @ColumnInfo(name = "correct_ans")
    public String correctAnswer = "";

    @NonNull
    public String difficulty = "easy";

    public QuizQuestion() {
    }

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
