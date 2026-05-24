package com.example.gamehub.games.quiz;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure quiz rules engine.
 *
 * It owns the question list, answer validation, score calculation, combo
 * tracking, and win condition. The Activity/ViewModel layer delegates all game
 * logic here to keep UI code thin.
 */
public class QuizManager {
    public static final int WIN_THRESHOLD_PERCENT = 60;

    public static class AnswerOutcome {
        public final QuizQuestion question;
        public final String selectedAnswerKey;
        public final String correctAnswerKey;
        public final boolean correct;
        public final boolean timedOut;
        public final int awardedScore;
        public final int totalScore;
        public final int combo;
        public final int correctCount;
        public final int answeredCount;
        public final boolean hasNextQuestion;

        public AnswerOutcome(
                QuizQuestion question,
                String selectedAnswerKey,
                String correctAnswerKey,
                boolean correct,
                boolean timedOut,
                int awardedScore,
                int totalScore,
                int combo,
                int correctCount,
                int answeredCount,
                boolean hasNextQuestion
        ) {
            this.question = question;
            this.selectedAnswerKey = selectedAnswerKey;
            this.correctAnswerKey = correctAnswerKey;
            this.correct = correct;
            this.timedOut = timedOut;
            this.awardedScore = awardedScore;
            this.totalScore = totalScore;
            this.combo = combo;
            this.correctCount = correctCount;
            this.answeredCount = answeredCount;
            this.hasNextQuestion = hasNextQuestion;
        }

        /**
         * Generates a short in-game message used directly on the gameplay
         * screen after the player submits an answer or times out.
         */
        public String buildFeedbackMessage() {
            if (correct) {
                if (combo > 1) {
                    return String.format(Locale.getDefault(), "Chính xác! +%d điểm, combo %d.", awardedScore, combo);
                }
                return String.format(Locale.getDefault(), "Chính xác! +%d điểm.", awardedScore);
            }
            if (timedOut) {
                return "Hết giờ. Đáp án đúng đã được hiện.";
            }
            return "Sai rồi. Đáp án đúng đã được hiện.";
        }
    }

    private final List<QuizQuestion> questions;
    private int currentIndex;
    private int answeredCount;
    private int correctCount;
    private int score;
    private int combo;
    private int bestCombo;

    public QuizManager(List<QuizQuestion> questions) {
        this.questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }

    public QuizQuestion getCurrentQuestion() {
        if (questions.isEmpty() || currentIndex < 0 || currentIndex >= questions.size()) {
            return null;
        }
        return questions.get(currentIndex);
    }

    /**
     * Applies the round rules to the current question and produces a complete
     * outcome object for the UI layer.
     */
    public AnswerOutcome answerCurrentQuestion(String optionKey, long remainingQuestionMs, boolean timedOut) {
        QuizQuestion question = getCurrentQuestion();
        if (question == null) {
            return null;
        }

        String normalizedSelectedKey = optionKey == null ? "" : optionKey.trim().toUpperCase(Locale.getDefault());
        String correctAnswerKey = question.correctAnswer == null ? "" : question.correctAnswer.trim().toUpperCase(Locale.getDefault());
        boolean isCorrect = !timedOut && correctAnswerKey.equals(normalizedSelectedKey);

        int awardedScore = 0;
        if (isCorrect) {
            correctCount++;
            combo++;
            bestCombo = Math.max(bestCombo, combo);
            awardedScore = calculateScore(question.difficulty, remainingQuestionMs, combo);
            score += awardedScore;
        } else {
            combo = 0;
        }
        answeredCount++;

        return new AnswerOutcome(
                question,
                normalizedSelectedKey,
                correctAnswerKey,
                isCorrect,
                timedOut,
                awardedScore,
                score,
                combo,
                correctCount,
                answeredCount,
                currentIndex < questions.size() - 1
        );
    }

    public boolean moveToNextQuestion() {
        if (currentIndex < questions.size() - 1) {
            currentIndex++;
            return true;
        }
        return false;
    }

    public int getCurrentQuestionNumber() {
        return Math.min(currentIndex + 1, questions.size());
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getScore() {
        return score;
    }

    public int getCombo() {
        return combo;
    }

    public int getBestCombo() {
        return bestCombo;
    }

    public int getAccuracyPercent() {
        if (questions.isEmpty()) {
            return 0;
        }
        return Math.round(correctCount * 100f / questions.size());
    }

    public boolean isWin() {
        return getAccuracyPercent() >= WIN_THRESHOLD_PERCENT;
    }

    /**
     * Score formula: base points + difficulty bonus + remaining-time bonus +
     * combo bonus. This keeps late answers from scoring as highly as fast ones.
     */
    private int calculateScore(String difficulty, long remainingQuestionMs, int combo) {
        int baseScore = 100;
        int difficultyBonus = 0;
        if ("medium".equalsIgnoreCase(difficulty)) {
            difficultyBonus = 35;
        } else if ("hard".equalsIgnoreCase(difficulty)) {
            difficultyBonus = 60;
        }
        int timeBonus = (int) Math.max(0L, remainingQuestionMs / 1000L) * 8;
        int comboBonus = Math.max(0, combo - 1) * 20;
        return baseScore + difficultyBonus + timeBonus + comboBonus;
    }
}
