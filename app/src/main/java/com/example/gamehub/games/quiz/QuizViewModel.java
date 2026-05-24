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
    public static final long FEEDBACK_DELAY_MS = 2500L;

    private final GameRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private final List<String> availableCategories = new ArrayList<>();
    private final List<String> sessionLog = new ArrayList<>();
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
    private String pendingSyncToastMessage = "";
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
                sessionLog.clear();
                appendLog(String.format(
                        Locale.getDefault(),
                        "Bắt đầu ván mới với chủ đề %s, độ khó %s, %d câu.",
                        getSelectedCategoriesLabel(),
                        getSelectedDifficultyLabel(),
                        selectedQuestionCount
                ));
                if (emptyState) {
                    appendLog("Không lấy được bộ câu hỏi phù hợp với bộ lọc hiện tại.");
                } else {
                    appendQuestionShownLog();
                }
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
        appendLog(String.format(
                Locale.getDefault(),
                "Câu %d: chọn đáp án %s.",
                getCurrentQuestionNumber(),
                selectedAnswerKey
        ));
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
            appendOutcomeLog(latestOutcome);
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
            appendOutcomeLog(latestOutcome);
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
            appendQuestionShownLog();
            notifyObservers();
            return;
        }
        finishGame();
    }

    public void showPause() {
        if (currentScreen == Screen.GAMEPLAY && !emptyState) {
            pauseVisible = true;
            appendLog(String.format(Locale.getDefault(), "Tạm dừng ở câu %d.", getCurrentQuestionNumber()));
            notifyObservers();
        }
    }

    public void hidePause() {
        if (pauseVisible) {
            pauseVisible = false;
            appendLog(String.format(Locale.getDefault(), "Tiếp tục ván ở câu %d.", getCurrentQuestionNumber()));
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

    public int getBestCombo() {
        return quizManager == null ? 0 : quizManager.getBestCombo();
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

    public String getAiReviewRequestKey() {
        return String.format(
                Locale.US,
                "%d:%d:%d:%d:%d",
                getScore(),
                getCorrectCount(),
                getTotalQuestions(),
                elapsedSessionMs,
                getBestCombo()
        );
    }

    public String buildAiReviewPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("Bạn là huấn luyện viên cho game đố vui. ")
                .append("Hãy phân tích đúng theo luật chơi của Quiz trong GameHub để viết nhận xét cuối ván. ")
                .append("Mỗi câu hỏi có 15 giây; trả lời đúng càng sớm thì điểm thưởng càng cao; trả lời đúng liên tiếp sẽ tăng combo; hết giờ hoặc trả lời sai sẽ làm mất nhịp. ")
                .append("Kết quả tốt phải dựa vào tỉ lệ đúng trên tổng số câu, điểm số, combo tốt nhất, số lần hết giờ và tốc độ giữ nhịp qua nhiều câu. ")
                .append("Nếu kết quả thấp thì dùng giọng nhẹ nhàng, kiểu tạm ổn hoặc còn khoảng để cải thiện, không chê gay gắt. ")
                .append("Nhật ký thao tác bên dưới là log theo trình tự thời gian. ")
                .append("Các dòng như 'Hiển thị câu...' nghĩa là bắt đầu một câu mới; 'trả lời đúng' nghĩa là chọn đúng đáp án; 'trả lời sai' nghĩa là chọn sai; 'hết giờ' nghĩa là không kịp trả lời; thời gian còn lại thể hiện tốc độ xử lý câu; combo phản ánh độ ổn định qua nhiều câu liên tiếp. ")
                .append("Hãy dùng chính các log này để suy ra người chơi hiểu bài, đoán nhanh, hay bị cuống thời gian. ")
                .append("Nhận xét phải có đủ khen và chê: nêu rõ một điểm làm tốt, một điểm cần cải thiện, và nếu cần thì thêm một câu chốt ngắn.\n\n")
                .append("Tóm tắt ván chơi:\n")
                .append("- Chủ đề: ").append(getSelectedCategoriesLabel()).append('\n')
                .append("- Độ khó: ").append(getSelectedDifficultyLabel()).append('\n')
                .append("- Số câu: ").append(getTotalQuestions()).append('\n')
                .append("- Đúng: ").append(getCorrectCount()).append('\n')
                .append("- Chính xác: ").append(getAccuracyPercent()).append("%\n")
                .append("- Điểm: ").append(getScore()).append('\n')
                .append("- Combo tốt nhất: ").append(getBestCombo()).append('\n')
                .append("- Thời gian: ").append(formatDuration(elapsedSessionMs)).append('\n')
                .append("- Kết quả: ").append(isWin() ? "Đạt" : "Chưa đạt").append("\n\n")
                .append("Nhật ký thao tác:\n");
        if (sessionLog.isEmpty()) {
            builder.append("- Không có nhật ký chi tiết.");
        } else {
            for (String entry : sessionLog) {
                builder.append("- ").append(entry).append('\n');
            }
        }
        appendAiMetricsBlock(builder);
        return builder.toString();
    }

    private void appendAiMetricsBlock(StringBuilder builder) {
        builder.append('\n')
                .append("AI_METRICS\n")
                .append("total_questions=").append(getTotalQuestions()).append('\n')
                .append("correct_count=").append(getCorrectCount()).append('\n')
                .append("accuracy_percent=").append(getAccuracyPercent()).append('\n')
                .append("score=").append(getScore()).append('\n')
                .append("best_combo=").append(getBestCombo()).append('\n')
                .append("elapsed_ms=").append(elapsedSessionMs).append('\n');
    }

    @Nullable
    public String consumePendingSyncToastMessage() {
        if (pendingSyncToastMessage == null || pendingSyncToastMessage.trim().isEmpty()) {
            return null;
        }
        String value = pendingSyncToastMessage;
        pendingSyncToastMessage = "";
        return value;
    }

    private void finishGame() {
        currentScreen = Screen.RESULT;
        pauseVisible = false;
        answerLocked = true;
        bestHistoryText = "Đang cập nhật lịch sử...";
        appendLog(String.format(
                Locale.getDefault(),
                "Kết thúc ván: đúng %d/%d, điểm %d, chính xác %d%%, combo tốt nhất %d, thời gian %s.",
                getCorrectCount(),
                getTotalQuestions(),
                getScore(),
                getAccuracyPercent(),
                getBestCombo(),
                formatDuration(elapsedSessionMs)
        ));
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
            repository.saveHistory(currentHistory, result -> {
                if (!result.success && result.message != null && !result.message.trim().isEmpty()) {
                    pendingSyncToastMessage = result.message;
                    notifyObservers();
                }
            });
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

    private void appendQuestionShownLog() {
        QuizQuestion question = getCurrentQuestion();
        if (question == null) {
            return;
        }
        appendLog(String.format(
                Locale.getDefault(),
                "Hiển thị câu %d thuộc chủ đề %s.",
                getCurrentQuestionNumber(),
                safeValue(question.category, "không rõ")
        ));
    }

    private void appendOutcomeLog(@Nullable QuizManager.AnswerOutcome outcome) {
        if (outcome == null) {
            return;
        }
        String selected = outcome.selectedAnswerKey == null || outcome.selectedAnswerKey.isEmpty()
                ? "không chọn"
                : outcome.selectedAnswerKey;
        appendLog(String.format(
                Locale.getDefault(),
                "Câu %d: %s, đáp án đã chọn %s, đáp án đúng %s, +%d điểm, còn %s, combo %d.",
                outcome.answeredCount,
                outcome.timedOut ? "hết giờ" : (outcome.correct ? "trả lời đúng" : "trả lời sai"),
                selected,
                outcome.correctAnswerKey,
                outcome.awardedScore,
                formatDuration(remainingQuestionMs),
                outcome.combo
        ));
    }

    private void appendLog(@Nullable String entry) {
        if (entry == null) {
            return;
        }
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
            sessionLog.add(trimmed);
        }
    }

    private String safeValue(@Nullable String value, @NonNull String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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
