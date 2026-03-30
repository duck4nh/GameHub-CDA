package com.example.gamehub.games.quiz;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.CompoundButtonCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.gamehub.R;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.utils.ImageLoader;
import com.example.gamehub.utils.SoundManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuizActivity extends AppCompatActivity {
    private interface ChoiceListener {
        void onSelected(String value);
    }

    private QuizViewModel viewModel;
    private PreferenceManager preferenceManager;
    private SoundManager soundManager;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewModel == null || viewModel.getCurrentScreen() != QuizViewModel.Screen.GAMEPLAY || viewModel.isPauseVisible()) {
                return;
            }
            if (viewModel.tickQuestion()) {
                handleOutcome(viewModel.timeoutCurrentQuestion());
                return;
            }
            render();
            handler.postDelayed(this, 1000L);
        }
    };
    private final Runnable advanceRunnable = () -> {
        if (viewModel == null) {
            return;
        }
        viewModel.advanceAfterFeedback();
        render();
        if (viewModel.getCurrentScreen() == QuizViewModel.Screen.GAMEPLAY && !viewModel.isPauseVisible()) {
            startTimer();
            animateQuestionCard();
        } else if (viewModel.getCurrentScreen() == QuizViewModel.Screen.RESULT) {
            animateResultScreen();
        }
    };
    private final QuizViewModel.Observer stateObserver = this::render;

    private View setupScreen;
    private View gameplayScreen;
    private View resultScreen;
    private View pauseOverlay;
    private View questionCard;
    private View imageContainer;
    private TextView selectedTopicHeroView;
    private TextView selectedSummaryView;
    private TextView selectedTopicView;
    private TextView selectedDifficultyView;
    private TextView selectedDifficultyHelperView;
    private TextView selectedCountView;
    private Button startButton;

    private TextView playTitleView;
    private TextView playProgressView;
    private TextView timerView;
    private View progressTrackView;
    private View progressFillView;
    private TextView liveScoreView;
    private TextView liveComboView;
    private TextView feedbackView;
    private TextView questionView;
    private TextView illustrationCaptionView;
    private ImageView illustrationView;
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
    private String renderedIllustrationUrl = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_quiz);

        preferenceManager = new PreferenceManager(this);
        soundManager = new SoundManager(this);
        viewModel = new ViewModelProvider(this).get(QuizViewModel.class);

        bindViews();
        bindActions();
        installBackHandler();

        viewModel.observe(stateObserver);
        viewModel.initialize();
    }

    private void bindViews() {
        setupScreen = findViewById(R.id.quiz_setup_screen);
        gameplayScreen = findViewById(R.id.quiz_gameplay_screen);
        resultScreen = findViewById(R.id.quiz_result_screen);
        pauseOverlay = findViewById(R.id.quiz_pause_screen);
        questionCard = findViewById(R.id.quiz_question_card);
        imageContainer = findViewById(R.id.quiz_image_container);

        selectedTopicHeroView = findViewById(R.id.quiz_selected_topic_hero);
        selectedSummaryView = findViewById(R.id.quiz_selected_summary);
        selectedTopicView = findViewById(R.id.quiz_selected_topic);
        selectedDifficultyView = findViewById(R.id.quiz_selected_difficulty);
        selectedDifficultyHelperView = findViewById(R.id.quiz_selected_difficulty_helper);
        selectedCountView = findViewById(R.id.quiz_selected_count);
        startButton = findViewById(R.id.quiz_start_button);

        playTitleView = findViewById(R.id.quiz_play_title);
        playProgressView = findViewById(R.id.quiz_play_progress);
        timerView = findViewById(R.id.quiz_timer);
        progressTrackView = findViewById(R.id.quiz_progress_track);
        progressFillView = findViewById(R.id.quiz_progress_fill);
        liveScoreView = findViewById(R.id.quiz_live_score);
        liveComboView = findViewById(R.id.quiz_live_combo);
        feedbackView = findViewById(R.id.quiz_feedback);
        questionView = findViewById(R.id.quiz_question);
        illustrationCaptionView = findViewById(R.id.quiz_illustration_caption);
        illustrationView = findViewById(R.id.quiz_image);
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
        findViewById(R.id.quiz_topic_row).setOnClickListener(v -> showCategorySheet());
        findViewById(R.id.quiz_difficulty_row).setOnClickListener(v ->
                showChoiceSheet("Chọn độ khó", buildDifficultyOptions(), viewModel.getSelectedDifficultyLabel(), value -> {
                    if ("Dễ".equals(value)) {
                        viewModel.setSelectedDifficulty("easy");
                    } else if ("Trung bình".equals(value)) {
                        viewModel.setSelectedDifficulty("medium");
                    } else if ("Khó".equals(value)) {
                        viewModel.setSelectedDifficulty("hard");
                    } else {
                        viewModel.setSelectedDifficulty("all");
                    }
                }));
        findViewById(R.id.quiz_count_row).setOnClickListener(v ->
                showChoiceSheet("Số câu hỏi", buildQuestionCountOptions(), formatQuestionCount(viewModel.getSelectedQuestionCount()), value -> {
                    int count = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                    viewModel.setSelectedQuestionCount(count);
                }));
        startButton.setOnClickListener(v -> viewModel.startGame());

        findViewById(R.id.quiz_play_back).setOnClickListener(v -> showPauseDialog());
        findViewById(R.id.quiz_pause).setOnClickListener(v -> showPauseDialog());
        optionAButton.setOnClickListener(v -> viewModel.selectAnswer("A"));
        optionBButton.setOnClickListener(v -> viewModel.selectAnswer("B"));
        optionCButton.setOnClickListener(v -> viewModel.selectAnswer("C"));
        optionDButton.setOnClickListener(v -> viewModel.selectAnswer("D"));
        submitAnswerButton.setOnClickListener(v -> handleOutcome(viewModel.submitAnswer()));

        findViewById(R.id.quiz_result_retry).setOnClickListener(v -> viewModel.startGame());
        findViewById(R.id.quiz_result_close).setOnClickListener(v -> finish());
        findViewById(R.id.quiz_pause_resume).setOnClickListener(v -> {
            viewModel.hidePause();
            render();
            startTimer();
        });
        findViewById(R.id.quiz_pause_exit).setOnClickListener(v -> finish());
    }

    private void installBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel.isPauseVisible()) {
                    viewModel.hidePause();
                    render();
                    startTimer();
                    return;
                }
                if (viewModel.getCurrentScreen() == QuizViewModel.Screen.GAMEPLAY) {
                    showPauseDialog();
                    return;
                }
                finish();
            }
        });
    }

    private void render() {
        renderSetup();
        renderGameplay();
        renderResult();
        renderPause();
    }

    private void renderSetup() {
        selectedTopicHeroView.setText(viewModel.getSelectedCategoriesHeroLabel());
        selectedSummaryView.setText(viewModel.isLoading() ? "Đang tải câu hỏi..." : viewModel.getSetupSummary());
        selectedTopicView.setText(viewModel.getSelectedCategoriesLabel());
        selectedDifficultyView.setText(viewModel.getSelectedDifficultyLabel());
        selectedDifficultyHelperView.setText(viewModel.getDifficultyHelperLabel());
        selectedCountView.setText(formatQuestionCount(viewModel.getSelectedQuestionCount()));
        startButton.setEnabled(!viewModel.isLoading() && !viewModel.getAvailableCategories().isEmpty());
        startButton.setAlpha(startButton.isEnabled() ? 1f : 0.45f);
        startButton.setText(viewModel.isLoading() ? "Đang chuẩn bị..." : "Bắt đầu chơi");
        setupScreen.setVisibility(viewModel.getCurrentScreen() == QuizViewModel.Screen.SETUP ? View.VISIBLE : View.GONE);
    }

    private void renderGameplay() {
        boolean isGameplay = viewModel.getCurrentScreen() == QuizViewModel.Screen.GAMEPLAY;
        gameplayScreen.setVisibility(isGameplay ? View.VISIBLE : View.GONE);
        if (!isGameplay) {
            handler.removeCallbacks(timerRunnable);
            handler.removeCallbacks(advanceRunnable);
            renderedIllustrationUrl = "";
            illustrationView.setImageDrawable(null);
            return;
        }

        QuizQuestion question = viewModel.getCurrentQuestion();
        liveScoreView.setText(String.valueOf(viewModel.getScore()));
        liveComboView.setText(String.valueOf(viewModel.getCombo()));
        timerView.setText(formatDuration(viewModel.getRemainingQuestionMs()));
        playProgressView.setText(String.format(Locale.getDefault(), "Câu %d / %d", viewModel.getCurrentQuestionNumber(), Math.max(1, viewModel.getTotalQuestions())));
        playTitleView.setText(question == null ? "Đố vui" : question.category);
        updateProgressFill(viewModel.getProgressRatio());

        if (viewModel.isEmptyState() || question == null) {
            setQuestionUiVisible(false);
            emptyView.setVisibility(View.VISIBLE);
            feedbackView.setVisibility(View.GONE);
            imageContainer.setVisibility(View.GONE);
            questionView.setText("");
            illustrationView.setImageDrawable(null);
            renderedIllustrationUrl = "";
            return;
        }

        setQuestionUiVisible(true);
        emptyView.setVisibility(View.GONE);
        questionView.setText(question.question);
        feedbackView.setText(viewModel.getMessage());
        feedbackView.setVisibility(viewModel.isAnswerLocked() && !viewModel.getMessage().isEmpty() ? View.VISIBLE : View.GONE);
        renderIllustration(question);
        renderAnswerButtons(question, viewModel.getLatestOutcome());

        if (viewModel.isPauseVisible()) {
            handler.removeCallbacks(timerRunnable);
            handler.removeCallbacks(advanceRunnable);
        } else if (!viewModel.isAnswerLocked()) {
            startTimer();
        }
    }

    private void renderResult() {
        boolean isResult = viewModel.getCurrentScreen() == QuizViewModel.Screen.RESULT;
        resultScreen.setVisibility(isResult ? View.VISIBLE : View.GONE);
        if (!isResult) {
            return;
        }
        int totalQuestions = Math.max(1, viewModel.getTotalQuestions());
        resultScoreView.setText(String.format(Locale.getDefault(), "%d / %d câu đúng", viewModel.getCorrectCount(), totalQuestions));
        resultSummaryView.setText(String.format(
                Locale.getDefault(),
                "%s · %s · Chính xác %d%%",
                viewModel.isWin() ? "Chiến thắng" : "Chưa đạt",
                formatDuration(viewModel.getElapsedSessionMs()),
                viewModel.getAccuracyPercent()
        ));
        resultTimeValueView.setText(formatDuration(viewModel.getElapsedSessionMs()));
        resultAccuracyValueView.setText(String.format(Locale.getDefault(), "%d%%", viewModel.getAccuracyPercent()));
        resultRewardValueView.setText(String.valueOf(viewModel.getScore()));
        resultHistoryView.setText(viewModel.getBestHistoryText());
        resultNoteView.setText(viewModel.isWin()
                ? "Kết quả của bạn đã được lưu. Bạn đạt ngưỡng thắng từ 60% số câu đúng."
                : "Kết quả của bạn đã được lưu. Hãy thử lại để cải thiện độ chính xác và điểm số.");
    }

    private void renderPause() {
        pauseOverlay.setVisibility(viewModel.isPauseVisible() ? View.VISIBLE : View.GONE);
        if (viewModel.isPauseVisible()) {
            pauseMessageView.setText("Phiên đố vui hiện tại đang tạm dừng. Bạn có thể tiếp tục hoặc thoát khỏi phiên.");
        }
    }

    private void renderIllustration(QuizQuestion question) {
        String imageUrl = question.linkImage == null ? "" : question.linkImage.trim();
        if (imageUrl.isEmpty()) {
            imageContainer.setVisibility(View.GONE);
            illustrationView.setImageDrawable(null);
            renderedIllustrationUrl = "";
            return;
        }
        imageContainer.setVisibility(View.VISIBLE);
        illustrationCaptionView.setText("Minh họa cho chủ đề " + question.category);
        if (imageUrl.equals(renderedIllustrationUrl)) {
            return;
        }
        renderedIllustrationUrl = imageUrl;
        illustrationView.setImageDrawable(null);
        ImageLoader.load(imageUrl, illustrationView, success -> {
            if (!success && imageUrl.equals(renderedIllustrationUrl)) {
                illustrationCaptionView.setText("Không tải được minh họa, bạn vẫn có thể trả lời bình thường.");
            }
        });
    }

    private void renderAnswerButtons(QuizQuestion question, @Nullable QuizManager.AnswerOutcome outcome) {
        optionAButton.setText(question.optionA);
        optionBButton.setText(question.optionB);
        optionCButton.setText(question.optionC);
        optionDButton.setText(question.optionD);

        resetOptionState();

        if (outcome == null) {
            setAnswerButtonsEnabled(true);
            highlightSelectedAnswer(viewModel.getSelectedAnswerKey());
            submitAnswerButton.setEnabled(!viewModel.getSelectedAnswerKey().isEmpty());
            submitAnswerButton.setAlpha(submitAnswerButton.isEnabled() ? 1f : 0.45f);
            return;
        }

        setAnswerButtonsEnabled(false);
        submitAnswerButton.setEnabled(false);
        submitAnswerButton.setAlpha(0.45f);
        Button correctButton = getButtonForOption(outcome.correctAnswerKey);
        if (correctButton != null) {
            correctButton.setBackgroundResource(R.drawable.bg_quiz_option_correct);
        }
        if (!outcome.correct && outcome.selectedAnswerKey != null && !outcome.selectedAnswerKey.isEmpty()) {
            Button selectedWrongButton = getButtonForOption(outcome.selectedAnswerKey);
            if (selectedWrongButton != null && selectedWrongButton != correctButton) {
                selectedWrongButton.setBackgroundResource(R.drawable.bg_quiz_option_wrong);
            }
        }
    }

    private void showPauseDialog() {
        if (viewModel.getCurrentScreen() != QuizViewModel.Screen.GAMEPLAY || viewModel.isEmptyState()) {
            finish();
            return;
        }
        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(advanceRunnable);
        viewModel.showPause();
        render();
    }

    private void handleOutcome(@Nullable QuizManager.AnswerOutcome outcome) {
        if (outcome == null) {
            Toast.makeText(this, "Hãy chọn một đáp án trước khi gửi.", Toast.LENGTH_SHORT).show();
            return;
        }
        handler.removeCallbacks(timerRunnable);
        render();
        if (outcome.correct) {
            soundManager.playCorrect();
        } else {
            soundManager.playWrong();
        }
        handler.removeCallbacks(advanceRunnable);
        handler.postDelayed(advanceRunnable, QuizViewModel.FEEDBACK_DELAY_MS);
    }

    private void startTimer() {
        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable, 1000L);
    }

    private void setQuestionUiVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        questionCard.setVisibility(visibility);
        optionAButton.setVisibility(visibility);
        optionBButton.setVisibility(visibility);
        optionCButton.setVisibility(visibility);
        optionDButton.setVisibility(visibility);
        submitAnswerButton.setVisibility(visibility);
        progressTrackView.setVisibility(visibility);
    }

    private void updateProgressFill(float ratio) {
        progressTrackView.post(() -> {
            int width = progressTrackView.getWidth();
            int progressWidth = Math.max(dpToPx(28), Math.round(width * ratio));
            ViewGroup.LayoutParams params = progressFillView.getLayoutParams();
            params.width = progressWidth;
            progressFillView.setLayoutParams(params);
        });
    }

    private void resetOptionState() {
        optionAButton.setBackgroundResource(R.drawable.bg_quiz_option_default);
        optionBButton.setBackgroundResource(R.drawable.bg_quiz_option_default);
        optionCButton.setBackgroundResource(R.drawable.bg_quiz_option_default);
        optionDButton.setBackgroundResource(R.drawable.bg_quiz_option_default);
    }

    private void highlightSelectedAnswer(String selectedAnswerKey) {
        Button selectedButton = getButtonForOption(selectedAnswerKey);
        if (selectedButton != null) {
            selectedButton.setBackgroundResource(R.drawable.bg_quiz_option_selected);
        }
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        optionAButton.setEnabled(enabled);
        optionBButton.setEnabled(enabled);
        optionCButton.setEnabled(enabled);
        optionDButton.setEnabled(enabled);
    }

    @Nullable
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

    private void showCategorySheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(24);
        container.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText("Chọn chủ đề");
        titleView.setTextSize(20f);
        titleView.setTextColor(getColor(R.color.gh_text_primary));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(titleView);

        List<String> selections = new ArrayList<>(viewModel.getSelectedCategories());
        for (String category : viewModel.getAvailableCategories()) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(category);
            checkBox.setTextColor(getColor(R.color.gh_text_primary));
            CompoundButtonCompat.setButtonTintList(checkBox, null);
            checkBox.setChecked(selections.contains(category));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dpToPx(10);
            checkBox.setLayoutParams(params);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selections.contains(category)) {
                        selections.add(category);
                    }
                } else {
                    selections.remove(category);
                }
            });
            container.addView(checkBox);
        }

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = dpToPx(18);
        actionRow.setLayoutParams(actionParams);

        Button resetButton = new Button(this);
        resetButton.setText("Chọn tất cả");
        resetButton.setAllCaps(false);
        resetButton.setBackgroundResource(R.drawable.bg_filter_unselected);
        resetButton.setBackgroundTintList(null);
        resetButton.setTextColor(getColor(R.color.gh_button_secondary_text));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dpToPx(44), 1f);
        resetButton.setLayoutParams(resetParams);
        resetButton.setOnClickListener(v -> {
            viewModel.setSelectedCategories(viewModel.getAvailableCategories());
            dialog.dismiss();
        });

        Button applyButton = new Button(this);
        applyButton.setText("Áp dụng");
        applyButton.setAllCaps(false);
        applyButton.setBackgroundResource(R.drawable.bg_primary_button_round);
        applyButton.setBackgroundTintList(null);
        applyButton.setTextColor(getColor(R.color.white));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, dpToPx(44), 1f);
        applyParams.leftMargin = dpToPx(12);
        applyButton.setLayoutParams(applyParams);
        applyButton.setOnClickListener(v -> {
            viewModel.setSelectedCategories(selections);
            dialog.dismiss();
        });

        actionRow.addView(resetButton);
        actionRow.addView(applyButton);
        container.addView(actionRow);
        scrollView.addView(container);
        dialog.setContentView(scrollView);
        dialog.show();
    }

    private void showChoiceSheet(String title, List<String> options, String selectedValue, ChoiceListener listener) {
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

    private boolean isAnimationOn() {
        return preferenceManager.getBoolean(PreferenceManager.KEY_IS_ANIMATION_ON, true);
    }

    private void animateQuestionCard() {
        if (!isAnimationOn()) {
            return;
        }
        questionCard.setAlpha(0f);
        questionCard.animate().alpha(1f).setDuration(180L).start();
        imageContainer.setAlpha(imageContainer.getVisibility() == View.VISIBLE ? 0f : 1f);
        if (imageContainer.getVisibility() == View.VISIBLE) {
            imageContainer.animate().alpha(1f).setDuration(180L).start();
        }
    }

    private void animateResultScreen() {
        if (!isAnimationOn()) {
            return;
        }
        resultScreen.setAlpha(0f);
        resultScreen.setScaleX(0.97f);
        resultScreen.setScaleY(0.97f);
        resultScreen.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(advanceRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        viewModel.removeObserver(stateObserver);
        soundManager.release();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
