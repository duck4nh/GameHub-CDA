package com.example.gamehub.games.quiz;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

import com.example.gamehub.R;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.dao.QuizDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.pref.PreferenceManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuizActivity extends AppCompatActivity {
    private interface OnChoiceSelectedListener {
        void onSelected(String value);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (gameplayScreen.getVisibility() != View.VISIBLE || sessionFinished) {
                return;
            }
            remainingTimeMs = Math.max(0L, remainingTimeMs - 1000L);
            timerView.setText(formatDuration(remainingTimeMs));
            if (remainingTimeMs <= 0L) {
                finishQuiz();
            } else {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private QuizDao quizDao;
    private HistoryDao historyDao;
    private PreferenceManager preferenceManager;

    private View setupScreen;
    private View gameplayScreen;
    private View resultScreen;
    private View pauseOverlay;
    private TextView selectedTopicHeroView;
    private TextView selectedSummaryView;
    private TextView selectedTopicView;
    private TextView selectedDifficultyView;
    private TextView selectedDifficultyHelperView;
    private TextView selectedCountView;

    private TextView playTitleView;
    private TextView playProgressView;
    private TextView timerView;
    private View progressTrackView;
    private View progressFillView;
    private TextView questionView;
    private TextView illustrationCaptionView;
    private TextView emptyView;
    private Button optionAButton;
    private Button optionBButton;
    private Button optionCButton;
    private Button optionDButton;
    private Button submitAnswerButton;

    private TextView resultScoreView;
    private TextView resultSummaryView;
    private TextView resultTimeValueView;
    private TextView resultAccuracyValueView;
    private TextView resultRewardValueView;
    private TextView resultHistoryView;
    private TextView resultNoteView;
    private TextView pauseMessageView;

    private final List<String> categoryOptions = new ArrayList<>();
    private String selectedCategory = "Tất cả chủ đề";
    private String selectedDifficulty = "Tất cả";
    private int selectedQuestionCount = 20;
    private String selectedAnswerKey;
    private QuizManager quizManager;
    private long remainingTimeMs;
    private long totalTimeMs;
    private boolean sessionFinished;
    private String gameplaySubtitleBeforePause;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_quiz);

        AppDatabase database = AppDatabase.getInstance(this);
        quizDao = database.quizDao();
        historyDao = database.historyDao();
        preferenceManager = new PreferenceManager(this);

        bindViews();
        loadCategoryOptions();
        updateSetupSummary();
        showSetupScreen();
        bindActions();
        installBackHandler();
    }

    private void bindViews() {
        setupScreen = findViewById(R.id.quiz_setup_screen);
        gameplayScreen = findViewById(R.id.quiz_gameplay_screen);
        resultScreen = findViewById(R.id.quiz_result_screen);
        pauseOverlay = findViewById(R.id.quiz_pause_screen);

        selectedTopicHeroView = findViewById(R.id.quiz_selected_topic_hero);
        selectedSummaryView = findViewById(R.id.quiz_selected_summary);
        selectedTopicView = findViewById(R.id.quiz_selected_topic);
        selectedDifficultyView = findViewById(R.id.quiz_selected_difficulty);
        selectedDifficultyHelperView = findViewById(R.id.quiz_selected_difficulty_helper);
        selectedCountView = findViewById(R.id.quiz_selected_count);

        playTitleView = findViewById(R.id.quiz_play_title);
        playProgressView = findViewById(R.id.quiz_play_progress);
        timerView = findViewById(R.id.quiz_timer);
        progressTrackView = findViewById(R.id.quiz_progress_track);
        progressFillView = findViewById(R.id.quiz_progress_fill);
        questionView = findViewById(R.id.quiz_question);
        illustrationCaptionView = findViewById(R.id.quiz_illustration_caption);
        emptyView = findViewById(R.id.quiz_empty);
        optionAButton = findViewById(R.id.quiz_option_a);
        optionBButton = findViewById(R.id.quiz_option_b);
        optionCButton = findViewById(R.id.quiz_option_c);
        optionDButton = findViewById(R.id.quiz_option_d);
        submitAnswerButton = findViewById(R.id.quiz_submit_answer);

        resultScoreView = findViewById(R.id.quiz_result_score);
        resultSummaryView = findViewById(R.id.quiz_result_summary);
        resultTimeValueView = findViewById(R.id.quiz_result_time_value);
        resultAccuracyValueView = findViewById(R.id.quiz_result_accuracy_value);
        resultRewardValueView = findViewById(R.id.quiz_result_reward_value);
        resultHistoryView = findViewById(R.id.quiz_result_history);
        resultNoteView = findViewById(R.id.quiz_result_note);
        pauseMessageView = findViewById(R.id.quiz_pause_message);
    }

    private void bindActions() {
        findViewById(R.id.quiz_setup_back).setOnClickListener(v -> finish());
        findViewById(R.id.quiz_topic_row).setOnClickListener(v ->
                showChoiceSheet("Chọn chủ đề", categoryOptions, selectedCategory, value -> {
                    selectedCategory = value;
                    updateSetupSummary();
                }));
        findViewById(R.id.quiz_difficulty_row).setOnClickListener(v ->
                showChoiceSheet("Chọn độ khó", buildDifficultyOptions(), selectedDifficulty, value -> {
                    selectedDifficulty = value;
                    updateSetupSummary();
                }));
        findViewById(R.id.quiz_count_row).setOnClickListener(v ->
                showChoiceSheet("Số câu hỏi", buildQuestionCountOptions(), formatQuestionCount(selectedQuestionCount), value -> {
                    selectedQuestionCount = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                    updateSetupSummary();
                }));
        findViewById(R.id.quiz_start_button).setOnClickListener(v -> startQuiz());

        findViewById(R.id.quiz_play_back).setOnClickListener(v -> showPauseDialog());
        findViewById(R.id.quiz_pause).setOnClickListener(v -> showPauseDialog());
        optionAButton.setOnClickListener(v -> selectAnswer("A"));
        optionBButton.setOnClickListener(v -> selectAnswer("B"));
        optionCButton.setOnClickListener(v -> selectAnswer("C"));
        optionDButton.setOnClickListener(v -> selectAnswer("D"));
        submitAnswerButton.setOnClickListener(v -> submitAnswer());

        findViewById(R.id.quiz_result_retry).setOnClickListener(v -> showSetupScreen());
        findViewById(R.id.quiz_result_close).setOnClickListener(v -> finish());
        findViewById(R.id.quiz_pause_resume).setOnClickListener(v -> hidePauseOverlay(true));
        findViewById(R.id.quiz_pause_exit).setOnClickListener(v -> finish());
    }

    private void installBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) {
                    hidePauseOverlay(true);
                    return;
                }
                if (gameplayScreen != null && gameplayScreen.getVisibility() == View.VISIBLE) {
                    showPauseDialog();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private void loadCategoryOptions() {
        categoryOptions.clear();
        categoryOptions.add("Tất cả chủ đề");
        categoryOptions.addAll(quizDao.getDistinctCategories());
    }

    private void updateSetupSummary() {
        selectedTopicHeroView.setText(selectedCategory);
        selectedTopicView.setText(selectedCategory);
        selectedDifficultyView.setText(selectedDifficulty);
        selectedDifficultyHelperView.setText("Tất cả".equals(selectedDifficulty)
                ? "Mọi mức độ đều có thể xuất hiện"
                : "Ưu tiên câu hỏi mức " + selectedDifficulty.toLowerCase(Locale.getDefault()));
        selectedCountView.setText(formatQuestionCount(selectedQuestionCount));
        selectedSummaryView.setText(String.format(
                Locale.getDefault(),
                "%s · %d câu hỏi mỗi ván.",
                "Tất cả".equals(selectedDifficulty) ? "Ngẫu nhiên" : selectedDifficulty,
                selectedQuestionCount
        ));
    }

    private void startQuiz() {
        List<QuizQuestion> questions = loadQuestionsForSelection();
        if (questions.isEmpty()) {
            showGameplayScreen();
            emptyView.setVisibility(View.VISIBLE);
            setQuestionUiVisible(false);
            playTitleView.setText("Đố vui");
            playProgressView.setText("0 / 0");
            questionView.setText("");
            return;
        }

        quizManager = new QuizManager(questions);
        selectedAnswerKey = null;
        sessionFinished = false;
        totalTimeMs = questions.size() * 20000L;
        remainingTimeMs = totalTimeMs;
        showGameplayScreen();
        hidePauseOverlay(false);
        setQuestionUiVisible(true);
        renderCurrentQuestion();
        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable, 1000L);
    }

    private List<QuizQuestion> loadQuestionsForSelection() {
        String difficultyKey = mapDifficulty(selectedDifficulty);
        boolean allCategory = "Tất cả chủ đề".equals(selectedCategory);
        List<QuizQuestion> questions;
        if (!allCategory && difficultyKey != null) {
            questions = quizDao.getQuestionsByCategoryAndDifficulty(selectedCategory, difficultyKey, selectedQuestionCount);
            if (questions.isEmpty()) {
                questions = quizDao.getQuestionsByCategory(selectedCategory, selectedQuestionCount);
            }
        } else if (!allCategory) {
            questions = quizDao.getQuestionsByCategory(selectedCategory, selectedQuestionCount);
        } else if (difficultyKey != null) {
            questions = quizDao.getQuestionsByDifficulty(difficultyKey, selectedQuestionCount);
        } else {
            questions = quizDao.getQuestions(selectedQuestionCount);
        }
        return questions;
    }

    private void renderCurrentQuestion() {
        QuizQuestion question = quizManager == null ? null : quizManager.getCurrentQuestion();
        if (question == null) {
            finishQuiz();
            return;
        }

        selectedAnswerKey = null;
        resetOptionState();
        setAnswerButtonsEnabled(true);
        submitAnswerButton.setEnabled(true);
        submitAnswerButton.setAlpha(0.5f);

        playTitleView.setText("Tất cả chủ đề".equals(selectedCategory) ? "Kiến thức tổng hợp" : question.category);
        gameplaySubtitleBeforePause = String.format(Locale.getDefault(), "Câu %d / %d", quizManager.getAnsweredCount(), quizManager.getTotalQuestions());
        playProgressView.setText(gameplaySubtitleBeforePause);
        questionView.setText(question.question);
        illustrationCaptionView.setText("Chủ đề " + question.category.toLowerCase(Locale.getDefault()) + " và dữ liệu minh họa câu hỏi");
        optionAButton.setText(question.optionA);
        optionBButton.setText(question.optionB);
        optionCButton.setText(question.optionC);
        optionDButton.setText(question.optionD);
        timerView.setText(formatDuration(remainingTimeMs));
        updateProgressFill();
    }

    private void selectAnswer(String answerKey) {
        if (sessionFinished) {
            return;
        }
        selectedAnswerKey = answerKey;
        resetOptionState();
        Button selectedButton = getButtonForOption(answerKey);
        if (selectedButton != null) {
            selectedButton.setBackgroundResource(R.drawable.bg_quiz_option_selected);
        }
        submitAnswerButton.setEnabled(true);
        submitAnswerButton.setAlpha(1f);
    }

    private void submitAnswer() {
        if (quizManager == null || sessionFinished) {
            return;
        }
        if (selectedAnswerKey == null) {
            Toast.makeText(this, "Hãy chọn một đáp án trước khi gửi.", Toast.LENGTH_SHORT).show();
            return;
        }

        submitAnswerButton.setEnabled(false);
        setAnswerButtonsEnabled(false);
        boolean correct = quizManager.answerCurrentQuestion(selectedAnswerKey);
        Button selectedButton = getButtonForOption(selectedAnswerKey);
        if (selectedButton != null) {
            selectedButton.setBackgroundResource(correct ? R.drawable.bg_quiz_option_correct : R.drawable.bg_quiz_option_wrong);
        }
        if (!correct) {
            QuizQuestion question = quizManager.getCurrentQuestion();
            if (question != null) {
                Button correctButton = getButtonForOption(question.correctAnswer);
                if (correctButton != null) {
                    correctButton.setBackgroundResource(R.drawable.bg_quiz_option_correct);
                }
            }
        }

        handler.postDelayed(() -> {
            if (quizManager.moveToNextQuestion()) {
                renderCurrentQuestion();
            } else {
                finishQuiz();
            }
        }, 650L);
    }

    private void finishQuiz() {
        if (sessionFinished) {
            return;
        }
        sessionFinished = true;
        handler.removeCallbacks(timerRunnable);

        int total = quizManager == null ? 0 : quizManager.getTotalQuestions();
        int correct = quizManager == null ? 0 : quizManager.getCorrectCount();
        long elapsed = Math.max(0L, totalTimeMs - remainingTimeMs);
        int accuracy = total == 0 ? 0 : Math.round((correct * 100f) / total);
        int reward = correct;

        historyDao.insert(new LocalHistory("Đố vui", correct > 0 ? "won" : "lost", reward, elapsed, System.currentTimeMillis(), false));

        resultScoreView.setText(String.format(Locale.getDefault(), "%d / %d câu đúng", correct, total));
        resultSummaryView.setText(String.format(Locale.getDefault(), "Thời gian %s · Chính xác %d%% · +%d điểm", formatDuration(elapsed), accuracy, reward));
        resultTimeValueView.setText(formatDuration(elapsed));
        resultAccuracyValueView.setText(String.format(Locale.getDefault(), "%d%%", accuracy));
        resultRewardValueView.setText(String.format(Locale.getDefault(), "+%d", reward));
        resultHistoryView.setText(buildBestHistoryText());
        resultNoteView.setText("Đã lưu vào lịch sử và sẵn sàng đồng bộ bảng xếp hạng.");
        showResultScreen();
    }

    private String buildBestHistoryText() {
        int bestScore = 0;
        long bestTime = 0L;
        for (LocalHistory item : historyDao.getAllNewestFirst()) {
            if (!"đố vui".equalsIgnoreCase(item.gameName)) {
                continue;
            }
            if (item.score > bestScore) {
                bestScore = item.score;
                bestTime = item.timeSpent;
            }
        }
        if (bestScore == 0) {
            return "Chưa có mốc tốt nhất trước đó trong lịch sử cục bộ.";
        }
        return String.format(Locale.getDefault(), "Mốc tốt nhất gần đây: %d câu đúng trong %s.", bestScore, formatDuration(bestTime));
    }

    private void showChoiceSheet(String title, List<String> options, String selectedValue, OnChoiceSelectedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(24);
        container.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(20f);
        titleView.setTextColor(getColor(R.color.gh_text_primary));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(titleView);

        for (String option : options) {
            TextView row = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dpToPx(12);
            row.setLayoutParams(params);
            row.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(14));
            row.setBackgroundResource(option.equals(selectedValue) ? R.drawable.bg_filter_selected : R.drawable.bg_filter_unselected);
            row.setText(option);
            row.setTextColor(getColor(R.color.gh_text_primary));
            row.setOnClickListener(v -> {
                listener.onSelected(option);
                dialog.dismiss();
            });
            container.addView(row);
        }

        dialog.setContentView(container);
        dialog.show();
    }

    private List<String> buildDifficultyOptions() {
        List<String> options = new ArrayList<>();
        options.add("Tất cả");
        options.add("Dễ");
        options.add("Trung bình");
        options.add("Khó");
        return options;
    }

    private List<String> buildQuestionCountOptions() {
        List<String> options = new ArrayList<>();
        options.add("10 câu");
        options.add("15 câu");
        options.add("20 câu");
        options.add("25 câu");
        options.add("30 câu");
        return options;
    }

    private void showSetupScreen() {
        handler.removeCallbacks(timerRunnable);
        setupScreen.setVisibility(View.VISIBLE);
        gameplayScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.GONE);
        hidePauseOverlay(false);
        updateSetupSummary();
    }

    private void showGameplayScreen() {
        setupScreen.setVisibility(View.GONE);
        gameplayScreen.setVisibility(View.VISIBLE);
        resultScreen.setVisibility(View.GONE);
        hidePauseOverlay(false);
    }

    private void showResultScreen() {
        setupScreen.setVisibility(View.GONE);
        gameplayScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.VISIBLE);
        hidePauseOverlay(false);
    }

    private void setQuestionUiVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        findViewById(R.id.quiz_option_a).setVisibility(visibility);
        findViewById(R.id.quiz_option_b).setVisibility(visibility);
        findViewById(R.id.quiz_option_c).setVisibility(visibility);
        findViewById(R.id.quiz_option_d).setVisibility(visibility);
        findViewById(R.id.quiz_submit_answer).setVisibility(visibility);
        findViewById(R.id.quiz_progress_track).setVisibility(visibility);
        emptyView.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void updateProgressFill() {
        if (quizManager == null) {
            return;
        }
        progressTrackView.post(() -> {
            int width = progressTrackView.getWidth();
            int progressWidth = Math.max(dpToPx(28), Math.round(width * (quizManager.getAnsweredCount() / (float) quizManager.getTotalQuestions())));
            ViewGroup.LayoutParams params = progressFillView.getLayoutParams();
            params.width = progressWidth;
            progressFillView.setLayoutParams(params);
        });
    }

    private void resetOptionState() {
        optionAButton.setBackgroundResource(R.drawable.bg_filter_unselected);
        optionBButton.setBackgroundResource(R.drawable.bg_filter_unselected);
        optionCButton.setBackgroundResource(R.drawable.bg_filter_unselected);
        optionDButton.setBackgroundResource(R.drawable.bg_filter_unselected);
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        optionAButton.setEnabled(enabled);
        optionBButton.setEnabled(enabled);
        optionCButton.setEnabled(enabled);
        optionDButton.setEnabled(enabled);
    }

    private Button getButtonForOption(String optionKey) {
        if (optionKey == null) {
            return null;
        }
        switch (optionKey.toUpperCase(Locale.getDefault())) {
            case "A":
                return optionAButton;
            case "B":
                return optionBButton;
            case "C":
                return optionCButton;
            case "D":
                return optionDButton;
            default:
                return null;
        }
    }

    private void showPauseDialog() {
        if (gameplayScreen.getVisibility() != View.VISIBLE || sessionFinished) {
            finish();
            return;
        }
        handler.removeCallbacks(timerRunnable);
        gameplaySubtitleBeforePause = playProgressView.getText().toString();
        playProgressView.setText("Tạm dừng");
        pauseMessageView.setText("Phiên đố vui hiện tại đang được tạm dừng. Bạn có thể tiếp tục để trả lời tiếp hoặc thoát khỏi phiên.");
        pauseOverlay.setVisibility(View.VISIBLE);
    }

    private void hidePauseOverlay(boolean resumeTimer) {
        if (pauseOverlay == null) {
            return;
        }
        pauseOverlay.setVisibility(View.GONE);
        if (gameplayScreen.getVisibility() == View.VISIBLE && gameplaySubtitleBeforePause != null) {
            playProgressView.setText(gameplaySubtitleBeforePause);
        }
        if (resumeTimer && gameplayScreen.getVisibility() == View.VISIBLE && !sessionFinished) {
            handler.removeCallbacks(timerRunnable);
            handler.postDelayed(timerRunnable, 1000L);
        }
    }

    private String mapDifficulty(String value) {
        if ("Dễ".equals(value)) {
            return "easy";
        }
        if ("Trung bình".equals(value)) {
            return "medium";
        }
        if ("Khó".equals(value)) {
            return "hard";
        }
        return null;
    }

    private String formatQuestionCount(int count) {
        return String.format(Locale.getDefault(), "%d câu", count);
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    protected void onPause() {
        handler.removeCallbacks(timerRunnable);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameplayScreen != null
                && gameplayScreen.getVisibility() == View.VISIBLE
                && (pauseOverlay == null || pauseOverlay.getVisibility() != View.VISIBLE)
                && !sessionFinished) {
            handler.postDelayed(timerRunnable, 1000L);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
