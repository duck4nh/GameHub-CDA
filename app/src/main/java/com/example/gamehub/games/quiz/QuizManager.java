package com.example.gamehub.games.quiz;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.util.ArrayList;
import java.util.List;

public class QuizManager {
    private final List<QuizQuestion> questions;
    private int currentIndex;
    private int correctCount;

    public QuizManager(List<QuizQuestion> questions) {
        this.questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }

    public QuizQuestion getCurrentQuestion() {
        if (questions.isEmpty() || currentIndex < 0 || currentIndex >= questions.size()) {
            return null;
        }
        return questions.get(currentIndex);
    }

    public boolean answerCurrentQuestion(String optionKey) {
        QuizQuestion question = getCurrentQuestion();
        if (question == null) {
            return false;
        }
        boolean correct = question.correctAnswer.equalsIgnoreCase(optionKey);
        if (correct) {
            correctCount++;
        }
        return correct;
    }

    public boolean moveToNextQuestion() {
        currentIndex++;
        return currentIndex < questions.size();
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getAnsweredCount() {
        return Math.min(currentIndex + 1, questions.size());
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public void reset() {
        currentIndex = 0;
        correctCount = 0;
    }
}
