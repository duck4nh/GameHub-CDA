package com.example.gamehub.games.quiz;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.repository.GameRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizViewModel extends AndroidViewModel {
    public enum Screen {
        SETUP,
        GAMEPLAY,
        RESULT
    }

    public interface Observer {
        void onStateChanged();
    }

    public static final long QUESTION_TIME_MS = 15_000L;
    public static final long FEEDBACK_DELAY_MS = 900L;

    private final GameRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private final List<String> availableCategories = new ArrayList<>();
    private final Set<String> selectedCategories = new LinkedHashSet<>();

    private Screen currentScreen = Screen.SETUP;
    private boolean initialized;
    private boolean loading;
    private boolean pauseVisible;
    private boolean emptyState;
    private boolean answerLocked;
    private String selectedDifficulty = "all";
    private int selectedQuestionCount = 20;
    private long remainingQuestionMs = QUESTION_TIME_MS;
    private long elapsedSessionMs;
    private String selectedAnswerKey = "";
    private String message = "";
    private String bestHistoryText = "Đang cập nhật lịch sử...";
    private QuizManager quizManager;
    private QuizManager.AnswerOutcome latestOutcome;

    public QuizViewModel(@NonNull Application application) {
        super(application);
        repository = GameRepository.getInstance(application.getApplicationContext());
    }

    public void observe(Observer observer) {
        observers.add(observer);
        observer.onStateChanged();
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public void initialize() {
        if (initialized || loading) {
            notifyObservers();
            return;
        }
        loading = true;
        notifyObservers();
        executor.execute(() -> {
            try {
                repository.ensureLocalDataReady();
                List<String> categories = repository.getQuizCategories();
                mainHandler.post(() -> {
                    initialized = true;
                    loading = false;
                    availableCategories.clear();
                    availableCategories.addAll(categories);
                    if (selectedCategories.isEmpty()) {
                        selectedCategories.addAll(categories);
                    }
                    notifyObservers();
                });
            } catch (IOException exception) {
                mainHandler.post(() -> {
                    loading = false;
                    message = "Không thể tải bộ câu hỏi lúc này.";
                    notifyObservers();
                });
            }
        });
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isPauseVisible() {
        return pauseVisible;
    }

    public boolean isEmptyState() {
        return emptyState;
    }

    public boolean isAnswerLocked() {
        return answerLocked;
    }

    public List<String> getAvailableCategories() {
        return new ArrayList<>(availableCategories);
    }

    public List<String> getSelectedCategories() {
        return new ArrayList<>(selectedCategories);
    }

    public String getSelectedCategoriesLabel() {
        if (selectedCategories.isEmpty() || selectedCategories.size() == availableCategories.size()) {
            return "Tất cả chủ đề";
        }
        if (selectedCategories.size() == 1) {
            return selectedCategories.iterator().next();
        }
        if (selectedCategories.size() == 2) {
            List<String> labels = new ArrayList<>(selectedCategories);
            return labels.get(0) + ", " + labels.get(1);
        }
        return String.format(Locale.getDefault(), "%d chủ đề đã chọn", selectedCategories.size());
    }

    public String getSelectedCategoriesHeroLabel() {
        if (selectedCategories.isEmpty() || selectedCategories.size() == availableCategories.size()) {
            return "Tất cả chủ đề";
        }
        if (selectedCategories.size() <= 2) {
            return getSelectedCategoriesLabel();
        }
        return String.format(Locale.getDefault(), "%d chủ đề", selectedCategories.size());
    }

    public String getSelectedDifficulty() {
        return selectedDifficulty;
    }

    public String getSelectedDifficultyLabel() {
        if ("easy".equals(selectedDifficulty)) {
            return "Dễ";
        }
        if ("medium".equals(selectedDifficulty)) {
            return "Trung bình";
        }
        if ("hard".equals(selectedDifficulty)) {
            return "Khó";
        }
        return "Tất cả";
    }

    public String getDifficultyHelperLabel() {
        if ("easy".equals(selectedDifficulty)) {
            return "Ưu tiên bộ câu hỏi dễ để làm nóng.";
        }
        if ("medium".equals(selectedDifficulty)) {
            return "Cân bằng giữa tốc độ và độ chính xác.";
        }
        if ("hard".equals(selectedDifficulty)) {
            return "Tập trung vào những câu hỏi khó hơn.";
        }
        return "Mọi mức độ đều có thể xuất hiện trong ván chơi.";
    }

    public int getSelectedQuestionCount() {
        return selectedQuestionCount;
    }

    public String getSetupSummary() {
        return String.format(
                Locale.getDefault(),
                "%s · %d câu hỏi mỗi ván.",
                getSelectedDifficultyLabel(),
                selectedQuestionCount
        );
    }

    public void setSelectedCategories(List<String> categories) {
        selectedCategories.clear();
        if (categories != null) {
            selectedCategories.addAll(categories);
        }
        if (selectedCategories.isEmpty()) {
            selectedCategories.addAll(availableCategories);
        }
        notifyObservers();
    }

    public void setSelectedDifficulty(String difficulty) {
        selectedDifficulty = difficulty == null ? "all" : difficulty;
        notifyObservers();
    }

    public void setSelectedQuestionCount(int questionCount) {
        selectedQuestionCount = questionCount;
        notifyObservers();
    }

    public void startGame() {
        if (loading) {
            return;
        }
        loading = true;
        message = "";
        notifyObservers();
        executor.execute(() -> {
            List<QuizQuestion> questions = repository.getRandomQuizQuestions(
                    getSelectedCategories(),
                    "all".equals(selectedDifficulty) ? null : selectedDifficulty,
                    selectedQuestionCount
            );
            mainHandler.post(() -> {
                loading = false;
                quizManager = new QuizManager(questions);
                currentScreen = Screen.GAMEPLAY;
                pauseVisible = false;
                emptyState = questions.isEmpty();
                answerLocked = emptyState;
                latestOutcome = null;
                selectedAnswerKey = "";
                remainingQuestionMs = QUESTION_TIME_MS;
                elapsedSessionMs = 0L;
                message = emptyState ? "Chưa có câu hỏi phù hợp với bộ lọc hiện tại." : "";
                notifyObservers();
            });
        });
    }

    public boolean tickQuestion() {
        if (currentScreen != Screen.GAMEPLAY || pauseVisible || answerLocked || emptyState || quizManager == null) {
            return false;
        }
        remainingQuestionMs = Math.max(0L, remainingQuestionMs - 1000L);
        elapsedSessionMs += 1000L;
        notifyObservers();
        return remainingQuestionMs <= 0L;
    }

    public void selectAnswer(String answerKey) {
        if (currentScreen != Screen.GAMEPLAY || answerLocked) {
            return;
        }
        selectedAnswerKey = answerKey == null ? "" : answerKey.toUpperCase(Locale.getDefault());
        notifyObservers();
    }

    @Nullable
    public QuizManager.AnswerOutcome submitAnswer() {
        if (quizManager == null || answerLocked || selectedAnswerKey.isEmpty()) {
            return null;
        }
        latestOutcome = quizManager.answerCurrentQuestion(selectedAnswerKey, remainingQuestionMs, false);
        answerLocked = true;
        if (latestOutcome != null) {
            message = latestOutcome.buildFeedbackMessage();
        }
        notifyObservers();
        return latestOutcome;
    }

    @Nullable
    public QuizManager.AnswerOutcome timeoutCurrentQuestion() {
        if (quizManager == null || answerLocked) {
            return null;
        }
        latestOutcome = quizManager.answerCurrentQuestion("", 0L, true);
        answerLocked = true;
        selectedAnswerKey = "";
        if (latestOutcome != null) {
            message = latestOutcome.buildFeedbackMessage();
        }
        notifyObservers();
        return latestOutcome;
    }

    public void advanceAfterFeedback() {
        if (quizManager == null) {
            return;
        }
        if (latestOutcome != null && latestOutcome.hasNextQuestion && quizManager.moveToNextQuestion()) {
            remainingQuestionMs = QUESTION_TIME_MS;
            selectedAnswerKey = "";
            latestOutcome = null;
            answerLocked = false;
            message = "";
            notifyObservers();
            return;
        }
        finishGame();
    }

    public void showPause() {
        if (currentScreen == Screen.GAMEPLAY && !emptyState) {
            pauseVisible = true;
            notifyObservers();
        }
    }

    public void hidePause() {
        if (pauseVisible) {
            pauseVisible = false;
            notifyObservers();
        }
    }

    public QuizQuestion getCurrentQuestion() {
        return quizManager == null ? null : quizManager.getCurrentQuestion();
    }

    public int getCurrentQuestionNumber() {
        return quizManager == null ? 0 : quizManager.getCurrentQuestionNumber();
    }

    public int getTotalQuestions() {
        return quizManager == null ? 0 : quizManager.getTotalQuestions();
    }

    public float getProgressRatio() {
        if (quizManager == null || quizManager.getTotalQuestions() == 0) {
            return 0f;
        }
        return quizManager.getCurrentQuestionNumber() / (float) quizManager.getTotalQuestions();
    }

    public long getRemainingQuestionMs() {
        return remainingQuestionMs;
    }

    public long getElapsedSessionMs() {
        return elapsedSessionMs;
    }

    public String getSelectedAnswerKey() {
        return selectedAnswerKey;
    }

    @Nullable
    public QuizManager.AnswerOutcome getLatestOutcome() {
        return latestOutcome;
    }

    public String getMessage() {
        return message;
    }

    public int getScore() {
        return quizManager == null ? 0 : quizManager.getScore();
    }

    public int getCombo() {
        return quizManager == null ? 0 : quizManager.getCombo();
    }

    public int getCorrectCount() {
        return quizManager == null ? 0 : quizManager.getCorrectCount();
    }

    public int getAccuracyPercent() {
        return quizManager == null ? 0 : quizManager.getAccuracyPercent();
    }

    public boolean isWin() {
        return quizManager != null && quizManager.isWin();
    }

    public String getBestHistoryText() {
        return bestHistoryText;
    }

    private void finishGame() {
        currentScreen = Screen.RESULT;
        pauseVisible = false;
        answerLocked = true;
        bestHistoryText = "Đang cập nhật lịch sử...";
        notifyObservers();

        if (quizManager == null) {
            return;
        }
        LocalHistory currentHistory = new LocalHistory(
                "quiz",
                quizManager.isWin() ? "won" : "lost",
                quizManager.getScore(),
                elapsedSessionMs,
                System.currentTimeMillis(),
                false
        );

        executor.execute(() -> {
            repository.saveHistory(currentHistory);
            LocalHistory bestHistory = repository.getBestHistoryForGame("quiz");
            mainHandler.post(() -> {
                bestHistoryText = buildBestHistoryText(bestHistory);
                notifyObservers();
            });
        });
    }

    private String buildBestHistoryText(@Nullable LocalHistory history) {
        if (history == null) {
            return "Chưa có mốc lịch sử trước đó.";
        }
        return String.format(
                Locale.getDefault(),
                "Cao nhất: %d điểm trong %s.",
                history.score,
                formatDuration(history.timeSpent)
        );
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void notifyObservers() {
        for (Observer observer : observers) {
            observer.onStateChanged();
        }
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }
}
