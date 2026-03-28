package com.example.gamehub.games.memory;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.gamehub.R;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.utils.SoundManager;

import java.util.List;
import java.util.Locale;

public class MemoryGameActivity extends AppCompatActivity {
    private MemoryViewModel viewModel;
    private PreferenceManager preferenceManager;
    private SoundManager soundManager;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewModel == null || viewModel.getCurrentScreen() != MemoryViewModel.Screen.GAMEPLAY || viewModel.isPauseVisible()) {
                return;
            }
            if (!viewModel.tick()) {
                render();
                handler.postDelayed(this, 1000L);
            } else {
                render();
                soundManager.playLose();
                animateResultScreen();
            }
        }
    };
    private final Runnable mismatchRunnable = () -> {
        if (viewModel == null) {
            return;
        }
        viewModel.resolveMismatch();
        render();
        startTimer();
    };
    private final MemoryViewModel.Observer stateObserver = this::render;

    private ScrollView setupScrollView;
    private View setupScreen;
    private View gameplayScreen;
    private View resultScreen;
    private View pauseOverlay;
    private GridLayout levelGrid;
    private Button startButton;
    private TextView levelLabelView;
    private TextView metaSmallView;
    private TextView bestStreakView;
    private TextView timerView;
    private TextView progressView;
    private androidx.recyclerview.widget.RecyclerView boardView;
    private TextView resultTitleView;
    private TextView resultSubtitleView;
    private TextView resultTimeValueView;
    private TextView resultAccuracyValueView;
    private TextView resultStreakValueView;
    private TextView resultNoteView;
    private TextView pauseMessageView;
    private Button resultBackButton;
    private Button nextLevelButton;
    private View resultCloseButton;

    private MemoryBoardAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_memory);

        preferenceManager = new PreferenceManager(this);
        soundManager = new SoundManager(this);
        viewModel = new ViewModelProvider(this).get(MemoryViewModel.class);

        bindViews();
        bindActions();
        installBackHandler();

        adapter = new MemoryBoardAdapter(this::onCardClicked);
        adapter.setAnimationsEnabled(isAnimationOn());
        boardView.setAdapter(adapter);

        viewModel.observe(stateObserver);
        viewModel.initialize();
    }

    private void bindViews() {
        setupScreen = findViewById(R.id.memory_setup_screen);
        setupScrollView = setupScreen instanceof ScrollView ? (ScrollView) setupScreen : null;
        gameplayScreen = findViewById(R.id.memory_gameplay_screen);
        resultScreen = findViewById(R.id.memory_result_screen);
        pauseOverlay = findViewById(R.id.memory_pause_screen);
        levelGrid = findViewById(R.id.memory_level_grid);
        startButton = findViewById(R.id.memory_start_button);
        levelLabelView = findViewById(R.id.memory_level_label);
        metaSmallView = findViewById(R.id.memory_meta_small);
        bestStreakView = findViewById(R.id.memory_best_streak);
        timerView = findViewById(R.id.memory_timer);
        progressView = findViewById(R.id.memory_progress);
        boardView = findViewById(R.id.memory_board);
        resultTitleView = findViewById(R.id.memory_result_title);
        resultSubtitleView = findViewById(R.id.memory_result_subtitle);
        resultTimeValueView = findViewById(R.id.memory_result_time_value);
        resultAccuracyValueView = findViewById(R.id.memory_result_accuracy_value);
        resultStreakValueView = findViewById(R.id.memory_result_streak_value);
        resultNoteView = findViewById(R.id.memory_result_note);
        pauseMessageView = findViewById(R.id.memory_pause_message);
        resultBackButton = findViewById(R.id.memory_result_retry);
        nextLevelButton = findViewById(R.id.memory_result_next);
        resultCloseButton = findViewById(R.id.memory_result_close);
    }

    private void bindActions() {
        findViewById(R.id.memory_setup_back).setOnClickListener(v -> finish());
        startButton.setOnClickListener(v -> viewModel.startSelectedLevel());
        findViewById(R.id.memory_back).setOnClickListener(v -> showPauseDialog());
        findViewById(R.id.memory_pause).setOnClickListener(v -> showPauseDialog());
        resultBackButton.setText("Quay lại");
        resultBackButton.setOnClickListener(v -> viewModel.showLevelSelection());
        resultCloseButton.setVisibility(View.GONE);
        nextLevelButton.setText("Màn tiếp");
        findViewById(R.id.memory_pause_resume).setOnClickListener(v -> {
            viewModel.hidePause();
            render();
            startTimer();
        });
        findViewById(R.id.memory_pause_exit).setOnClickListener(v -> finish());
        nextLevelButton.setOnClickListener(v -> {
            if (viewModel.canPlayNextLevel()) {
                viewModel.startLevel(viewModel.getNextLevelIndex());
            }
        });
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
                if (viewModel.getCurrentScreen() == MemoryViewModel.Screen.GAMEPLAY) {
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
        boolean isSetup = viewModel.getCurrentScreen() == MemoryViewModel.Screen.SETUP;
        setupScreen.setVisibility(isSetup ? View.VISIBLE : View.GONE);
        List<MemoryLevel> levels = viewModel.getLevels();
        int selectedLevelIndex = viewModel.getSelectedLevelIndex();
        buildLevelGrid(levels, selectedLevelIndex);
        startButton.setEnabled(!viewModel.isLoading() && !levels.isEmpty());
        startButton.setAlpha(startButton.isEnabled() ? 1f : 0.45f);
        if (viewModel.isLoading()) {
            startButton.setText("Đang chuẩn bị...");
        } else if (!levels.isEmpty()) {
            MemoryLevel selectedLevel = levels.get(Math.max(0, Math.min(selectedLevelIndex, levels.size() - 1)));
            startButton.setText(String.format(Locale.getDefault(), "Chơi Màn %d", selectedLevel.levelId));
        } else {
            startButton.setText("Bắt đầu chơi");
        }

        if (isSetup) {
            scrollLevelListToFocus(selectedLevelIndex);
        }
        if (isSetup && viewModel.consumeAutoLaunchSelectedLevel()) {
            handler.post(viewModel::startSelectedLevel);
        }
    }

    private void renderGameplay() {
        boolean isGameplay = viewModel.getCurrentScreen() == MemoryViewModel.Screen.GAMEPLAY;
        gameplayScreen.setVisibility(isGameplay ? View.VISIBLE : View.GONE);
        if (!isGameplay) {
            handler.removeCallbacks(timerRunnable);
            handler.removeCallbacks(mismatchRunnable);
            return;
        }

        MemoryLevel currentLevel = viewModel.getCurrentLevel();
        if (currentLevel == null) {
            return;
        }
        levelLabelView.setText(String.format(Locale.getDefault(), "Ghi nhớ · Màn %d", currentLevel.levelId));
        metaSmallView.setText(String.format(
                Locale.getDefault(),
                "%s · %d cặp · %d hàng · %d cột",
                currentLevel.getDisplayLabel(),
                currentLevel.getPairCount(),
                currentLevel.rowCount,
                currentLevel.columnCount
        ));
        bestStreakView.setText(String.format(Locale.getDefault(), "%d cặp liên tiếp", viewModel.getBestStreak()));
        timerView.setText(viewModel.formatDuration(viewModel.getRemainingTimeMs()));
        progressView.setText(String.format(Locale.getDefault(), "%d lượt", viewModel.getPairAttempts()));

        GridLayoutManager layoutManager = (GridLayoutManager) boardView.getLayoutManager();
        if (layoutManager == null || layoutManager.getSpanCount() != currentLevel.columnCount) {
            boardView.setLayoutManager(new GridLayoutManager(this, currentLevel.columnCount));
        }
        adapter.setAnimationsEnabled(isAnimationOn());
        adapter.submitList(viewModel.getCards());

        if (!viewModel.isPauseVisible() && !viewModel.isBoardLocked()) {
            startTimer();
        }
    }

    private void renderResult() {
        boolean isResult = viewModel.getCurrentScreen() == MemoryViewModel.Screen.RESULT;
        resultScreen.setVisibility(isResult ? View.VISIBLE : View.GONE);
        if (!isResult) {
            return;
        }
        MemoryLevel currentLevel = viewModel.getCurrentLevel();
        String levelLabel = currentLevel == null
                ? "--"
                : String.format(Locale.getDefault(), "Màn %d · %s", currentLevel.levelId, currentLevel.getDisplayLabel());
        resultTitleView.setText(String.format(Locale.getDefault(), "%d điểm", viewModel.getScore()));
        resultSubtitleView.setText(String.format(
                Locale.getDefault(),
                "%s · %d cặp khớp · %d lượt đoán",
                levelLabel,
                viewModel.getMatchedPairs(),
                viewModel.getPairAttempts()
        ));
        resultTimeValueView.setText(viewModel.formatDuration(viewModel.getElapsedTimeMs()));
        resultAccuracyValueView.setText(String.format(Locale.getDefault(), "%d%%", viewModel.getAccuracyPercent()));
        resultStreakValueView.setText(String.valueOf(viewModel.getBestStreak()));
        resultNoteView.setText(viewModel.canPlayNextLevel()
                ? "Đã mở màn tiếp theo. Bạn có thể quay lại danh sách level hoặc sang thẳng màn mới."
                : "Đây là màn cao nhất đã mở. Bạn có thể quay lại danh sách để chơi lại các màn trước.");
        nextLevelButton.setEnabled(viewModel.canPlayNextLevel());
        nextLevelButton.setAlpha(nextLevelButton.isEnabled() ? 1f : 0.45f);
    }

    private void renderPause() {
        pauseOverlay.setVisibility(viewModel.isPauseVisible() ? View.VISIBLE : View.GONE);
        if (viewModel.isPauseVisible()) {
            pauseMessageView.setText("Phiên ghi nhớ hiện tại đang tạm dừng. Bạn có thể tiếp tục hoặc thoát khỏi phiên.");
        }
    }

    private void buildLevelGrid(List<MemoryLevel> levels, int selectedIndex) {
        levelGrid.removeAllViews();
        levelGrid.setColumnCount(2);
        levelGrid.setRowCount((int) Math.ceil(levels.size() / 2f));
        for (int index = 0; index < levels.size(); index++) {
            MemoryLevel level = levels.get(index);
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(14));
            tile.setBackgroundResource(index == selectedIndex ? R.drawable.bg_tile_selected_memory : R.drawable.bg_card_surface_22);
            tile.setClickable(level.isUnlocked);
            tile.setFocusable(level.isUnlocked);
            tile.setAlpha(level.isUnlocked ? 1f : 0.55f);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(88);
            params.columnSpec = GridLayout.spec(index % 2, 1f);
            params.rowSpec = GridLayout.spec(index / 2);
            params.setMargins(0, index < 2 ? 0 : dpToPx(14), index % 2 == 0 ? dpToPx(10) : 0, 0);
            tile.setLayoutParams(params);

            TextView icon = new TextView(this);
            icon.setText("◌");
            icon.setTextSize(18f);
            icon.setTextColor(getColor(R.color.gh_bg_brand));
            tile.addView(icon);

            TextView title = new TextView(this);
            title.setText(String.format(Locale.getDefault(), "Màn %d", level.levelId));
            title.setTextSize(18f);
            title.setTextColor(getColor(R.color.gh_text_primary));
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setPadding(0, dpToPx(8), 0, 0);
            tile.addView(title);

            TextView subtitle = new TextView(this);
            subtitle.setText(level.isUnlocked
                    ? String.format(
                    Locale.getDefault(),
                    "%s · %d cặp · %ds",
                    level.getDisplayLabel(),
                    level.getPairCount(),
                    level.timeLimitSec
            )
                    : String.format(Locale.getDefault(), "Mở sau khi thắng màn %d", Math.max(1, level.levelId - 1)));
            subtitle.setTextSize(12f);
            subtitle.setTextColor(getColor(R.color.gh_text_secondary));
            tile.addView(subtitle);

            final int finalIndex = index;
            tile.setOnClickListener(v -> viewModel.selectLevel(finalIndex));
            levelGrid.addView(tile);
        }
    }

    private void scrollLevelListToFocus(int selectedIndex) {
        if (setupScrollView == null || selectedIndex < 0 || levelGrid.getChildCount() == 0) {
            return;
        }
        setupScrollView.post(() -> {
            if (levelGrid.getChildCount() == 0) {
                return;
            }
            int selectedRow = selectedIndex / 2;
            int anchorRow = Math.max(0, selectedRow - 1);
            int anchorChildIndex = Math.min(levelGrid.getChildCount() - 1, anchorRow * 2);
            View anchorChild = levelGrid.getChildAt(anchorChildIndex);
            if (anchorChild == null) {
                return;
            }
            int targetY = Math.max(0, anchorChild.getTop() - dpToPx(20));
            setupScrollView.scrollTo(0, targetY);
        });
    }

    private void onCardClicked(int position) {
        MemoryViewModel.TurnOutcome outcome = viewModel.onCardSelected(position);
        render();
        switch (outcome.type) {
            case FIRST_REVEAL:
                soundManager.playCardFlip();
                startTimer();
                break;
            case MATCH:
                soundManager.playMatch();
                startTimer();
                break;
            case WIN:
                soundManager.playWin();
                animateResultScreen();
                break;
            case MISMATCH:
                soundManager.playWrong();
                handler.removeCallbacks(timerRunnable);
                handler.removeCallbacks(mismatchRunnable);
                handler.postDelayed(mismatchRunnable, MemoryViewModel.MISMATCH_DELAY_MS);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void showPauseDialog() {
        if (viewModel.getCurrentScreen() != MemoryViewModel.Screen.GAMEPLAY) {
            finish();
            return;
        }
        handler.removeCallbacks(timerRunnable);
        viewModel.showPause();
        render();
    }

    private void startTimer() {
        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable, 1000L);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private boolean isAnimationOn() {
        return preferenceManager.getBoolean(PreferenceManager.KEY_IS_ANIMATION_ON, true);
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
        handler.removeCallbacks(mismatchRunnable);
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
