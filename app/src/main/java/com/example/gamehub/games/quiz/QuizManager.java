package com.example.gamehub.games.quiz;

import com.example.gamehub.data.local.entities.QuizQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Engine luật chơi thuần Java cho Game Quiz.
 *
 * Lớp này giữ danh sách câu hỏi, kiểm tra đáp án, tính điểm, theo dõi combo và
 * xác định điều kiện thắng. Activity/ViewModel gọi lớp này để UI không chứa
 * công thức tính điểm.
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
         * Tạo thông báo ngắn hiển thị ngay trên màn chơi sau khi người chơi trả
         * lời hoặc hết giờ.
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
     * Áp dụng luật chơi cho câu hỏi hiện tại và trả về đầy đủ dữ liệu kết quả
     * để UI tô màu đáp án, hiển thị điểm và feedback.
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
     * Công thức điểm: điểm nền + thưởng độ khó + thưởng thời gian còn lại +
     * thưởng combo. Cách tính này khuyến khích trả lời nhanh và đúng liên tiếp.
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
