package com.example.gamehub.games.memory;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.gamehub.R;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.utils.SoundManager;

import java.util.List;
import java.util.Locale;

public class MemoryGameActivity extends AppCompatActivity {
    private static final int LEVEL_GRID_COLUMNS = 2;

    private MemoryViewModel viewModel;
    private PreferenceManager preferenceManager;
    private SoundManager soundManager;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean timerScheduled;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timerScheduled = false;
            if (viewModel == null || viewModel.getCurrentScreen() != MemoryViewModel.Screen.GAMEPLAY || viewModel.isPauseVisible()) {
                return;
            }
            if (viewModel.tick()) {
                render();
                soundManager.playLose();
                animateResultScreen();
                return;
            }
            render();
            scheduleTimerTick();
        }
    };
    private final Runnable mismatchRunnable = () -> {
        if (viewModel == null) {
            return;
        }
        viewModel.resolveMismatch();
        render();
        scheduleTimerTick();
    };
    private final MemoryViewModel.Observer stateObserver = this::render;

    private ScrollView setupScrollView;
    private View setupScreen;
    private View gameplayScreen;
    private View resultScreen;
    private View pauseOverlay;
    private GridLayout levelGrid;
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
    private View resultBackButton;
    private TextView nextLevelButton;
    private View resultCloseButton;

    private MemoryBoardAdapter adapter;
    private int lastRenderedBoardVersion = -1;

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
        boardView.setItemAnimator(null);

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
        findViewById(R.id.memory_back).setOnClickListener(v -> showPauseDialog());
        findViewById(R.id.memory_pause).setOnClickListener(v -> showPauseDialog());
        resultBackButton.setOnClickListener(v -> viewModel.showLevelSelection());
        resultCloseButton.setVisibility(View.GONE);
        nextLevelButton.setOnClickListener(v -> {
            if (viewModel.canPlayNextLevel()) {
                startLevel(viewModel.getNextLevelIndex());
            }
        });
        findViewById(R.id.memory_pause_resume).setOnClickListener(v -> {
            viewModel.hidePause();
            render();
            scheduleTimerTick();
        });
        findViewById(R.id.memory_pause_exit).setOnClickListener(v -> finish());
    }

    private void installBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel.isPauseVisible()) {
                    viewModel.hidePause();
                    render();
                    scheduleTimerTick();
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
        if (!isSetup) {
            return;
        }
        buildLevelGrid(viewModel.getLevels(), viewModel.getSelectedLevelIndex());
        scrollLevelListToFocus(viewModel.getSelectedLevelIndex());
    }

    private void renderGameplay() {
        boolean isGameplay = viewModel.getCurrentScreen() == MemoryViewModel.Screen.GAMEPLAY;
        gameplayScreen.setVisibility(isGameplay ? View.VISIBLE : View.GONE);
        if (!isGameplay) {
            stopTimer();
            handler.removeCallbacks(mismatchRunnable);
            lastRenderedBoardVersion = -1;
            return;
        }

        MemoryLevel currentLevel = viewModel.getCurrentLevel();
        if (currentLevel == null) {
            return;
        }
        levelLabelView.setText(String.format(Locale.getDefault(), "Ghi nhớ · Level %d", currentLevel.levelId));
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
        boolean spanCountChanged = layoutManager == null || layoutManager.getSpanCount() != currentLevel.columnCount;
        if (spanCountChanged) {
            boardView.setLayoutManager(new GridLayoutManager(this, currentLevel.columnCount));
        }
        adapter.setAnimationsEnabled(isAnimationOn());
        int boardStateVersion = viewModel.getBoardStateVersion();
        if (spanCountChanged || lastRenderedBoardVersion != boardStateVersion) {
            adapter.submitList(viewModel.getCards());
            lastRenderedBoardVersion = boardStateVersion;
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
                : String.format(Locale.getDefault(), "Level %d (%s)", currentLevel.levelId, currentLevel.getDisplayLabel());

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

        if (!viewModel.didLastGameWin()) {
            resultNoteView.setText("Bạn chưa hoàn thành level này. Hãy thử lại hoặc quay lại danh sách level.");
        } else if (viewModel.didUnlockNextLevelThisRound()) {
            resultNoteView.setText("Đã mở level tiếp theo. Bạn có thể quay lại danh sách level hoặc sang thẳng level mới.");
        } else if (viewModel.canPlayNextLevel()) {
            resultNoteView.setText("Level tiếp theo đã sẵn sàng. Bạn có thể tiếp tục hoặc quay lại danh sách level.");
        } else {
            resultNoteView.setText("Bạn đã hoàn thành level cao nhất hiện có. Có thể quay lại danh sách để chơi lại các level trước.");
        }

        nextLevelButton.setEnabled(viewModel.canPlayNextLevel());
        nextLevelButton.setAlpha(nextLevelButton.isEnabled() ? 1f : 0.45f);
        String syncToastMessage = viewModel.consumePendingSyncToastMessage();
        if (syncToastMessage != null && !syncToastMessage.trim().isEmpty()) {
            Toast.makeText(this, syncToastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void renderPause() {
        pauseOverlay.setVisibility(viewModel.isPauseVisible() ? View.VISIBLE : View.GONE);
        if (viewModel.isPauseVisible()) {
            pauseMessageView.setText("Phiên ghi nhớ hiện tại đang tạm dừng. Bạn có thể tiếp tục hoặc thoát khỏi phiên.");
        }
    }

    private void buildLevelGrid(List<MemoryLevel> levels, int selectedIndex) {
        levelGrid.removeAllViews();
        levelGrid.setColumnCount(LEVEL_GRID_COLUMNS);
        levelGrid.setRowCount((int) Math.ceil(levels.size() / (float) LEVEL_GRID_COLUMNS));
        for (int index = 0; index < levels.size(); index++) {
            MemoryLevel level = levels.get(index);
            LinearLayout tile = createLevelTile(level, index == selectedIndex);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(96);
            params.columnSpec = GridLayout.spec(index % LEVEL_GRID_COLUMNS, 1f);
            params.rowSpec = GridLayout.spec(index / LEVEL_GRID_COLUMNS);
            params.setMargins(index % LEVEL_GRID_COLUMNS == 0 ? 0 : dpToPx(8), index < LEVEL_GRID_COLUMNS ? 0 : dpToPx(12), index % LEVEL_GRID_COLUMNS == 0 ? dpToPx(8) : 0, 0);
            tile.setLayoutParams(params);

            final int finalIndex = index;
            tile.setOnClickListener(v -> {
                if (!level.isUnlocked) {
                    return;
                }
                viewModel.selectLevel(finalIndex);
                render();
                startLevel(finalIndex);
            });
            levelGrid.addView(tile);
        }
    }

    private LinearLayout createLevelTile(MemoryLevel level, boolean isFocused) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        tile.setBackgroundResource(!level.isUnlocked
                ? R.drawable.bg_tile_memory_locked
                : isFocused ? R.drawable.bg_tile_selected_memory : R.drawable.bg_tile_memory_unlocked);
        tile.setClickable(level.isUnlocked);
        tile.setFocusable(level.isUnlocked);
        tile.setAlpha(level.isUnlocked ? 1f : 0.62f);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconStack = new FrameLayout(this);
        LinearLayout.LayoutParams iconStackParams = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(38));
        iconStack.setLayoutParams(iconStackParams);

        TextView backCard = new TextView(this);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dpToPx(24), dpToPx(28));
        backParams.gravity = Gravity.TOP | Gravity.START;
        backCard.setLayoutParams(backParams);
        backCard.setBackgroundResource(R.drawable.bg_stats_value_chip);
        backCard.setGravity(Gravity.CENTER);
        backCard.setText("?");
        backCard.setTextSize(13f);
        backCard.setTypeface(backCard.getTypeface(), android.graphics.Typeface.BOLD);
        backCard.setTextColor(getColor(R.color.gh_text_secondary));
        iconStack.addView(backCard);

        TextView frontCard = new TextView(this);
        FrameLayout.LayoutParams frontParams = new FrameLayout.LayoutParams(dpToPx(26), dpToPx(30));
        frontParams.gravity = Gravity.BOTTOM | Gravity.END;
        frontCard.setLayoutParams(frontParams);
        frontCard.setBackgroundResource(R.drawable.bg_stats_value_chip_active);
        frontCard.setGravity(Gravity.CENTER);
        frontCard.setText("A");
        frontCard.setTextSize(16f);
        frontCard.setTypeface(frontCard.getTypeface(), android.graphics.Typeface.BOLD);
        frontCard.setTextColor(getColor(R.color.gh_button_primary_border));
        iconStack.addView(frontCard);

        header.addView(iconStack);

        TextView title = new TextView(this);
        title.setText(String.format(Locale.getDefault(), "Level %d", level.levelId));
        title.setTextSize(18f);
        title.setTextColor(getColor(R.color.gh_text_primary));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dpToPx(10));
        title.setLayoutParams(titleParams);
        header.addView(title);

        tile.addView(header);

        LinearLayout recordRow = new LinearLayout(this);
        recordRow.setOrientation(LinearLayout.HORIZONTAL);
        recordRow.setGravity(Gravity.CENTER_VERTICAL);
        recordRow.setPadding(0, dpToPx(10), 0, 0);

        if (level.isUnlocked && viewModel.hasLevelRecord(level)) {
            TextView bestLabel = new TextView(this);
            bestLabel.setText("Best:");
            bestLabel.setTextSize(12f);
            bestLabel.setTypeface(bestLabel.getTypeface(), android.graphics.Typeface.BOLD);
            bestLabel.setTextColor(getColor(R.color.gh_text_secondary));
            recordRow.addView(bestLabel);

            ImageView avatarView = new ImageView(this);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
            avatarParams.setMarginStart(dpToPx(6));
            avatarView.setLayoutParams(avatarParams);
            String avatarUrl = viewModel.getCurrentPlayerAvatarUrl();
            Glide.with(this)
                    .load(avatarUrl == null || avatarUrl.trim().isEmpty() ? R.drawable.img_avatar_cat : avatarUrl.trim())
                    .placeholder(R.drawable.img_avatar_cat)
                    .error(R.drawable.img_avatar_cat)
                    .circleCrop()
                    .into(avatarView);
            recordRow.addView(avatarView);

            TextView bestValue = new TextView(this);
            bestValue.setText(String.format(
                    Locale.getDefault(),
                    "%s - %s",
                    viewModel.getCurrentPlayerName(),
                    viewModel.formatSeconds(level.bestTimeMs)
            ));
            bestValue.setTextSize(12f);
            bestValue.setTextColor(getColor(R.color.gh_text_primary));
            bestValue.setMaxLines(1);
            bestValue.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bestValueParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bestValueParams.setMarginStart(dpToPx(6));
            bestValue.setLayoutParams(bestValueParams);
            recordRow.addView(bestValue);
        } else if (level.isUnlocked) {
            TextView bestLabel = new TextView(this);
            bestLabel.setText("Best:");
            bestLabel.setTextSize(12f);
            bestLabel.setTypeface(bestLabel.getTypeface(), android.graphics.Typeface.BOLD);
            bestLabel.setTextColor(getColor(R.color.gh_text_secondary));
            recordRow.addView(bestLabel);

            TextView bestValue = new TextView(this);
            bestValue.setText("chưa có");
            bestValue.setTextSize(12f);
            bestValue.setTextColor(getColor(R.color.gh_text_secondary));
            LinearLayout.LayoutParams bestValueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bestValueParams.setMarginStart(dpToPx(6));
            bestValue.setLayoutParams(bestValueParams);
            recordRow.addView(bestValue);
        } else {
            TextView bestValue = new TextView(this);
            bestValue.setText(String.format(Locale.getDefault(), "Mở sau Level %d", Math.max(1, level.levelId - 1)));
            bestValue.setTextSize(12f);
            bestValue.setTextColor(getColor(R.color.gh_text_secondary));
            bestValue.setMaxLines(1);
            bestValue.setEllipsize(TextUtils.TruncateAt.END);
            recordRow.addView(bestValue);
        }

        tile.addView(recordRow);

        return tile;
    }

    private void scrollLevelListToFocus(int selectedIndex) {
        if (setupScrollView == null || selectedIndex < 0 || levelGrid.getChildCount() == 0) {
            return;
        }
        setupScrollView.post(() -> {
            if (levelGrid.getChildCount() == 0) {
                return;
            }
            int selectedRow = selectedIndex / LEVEL_GRID_COLUMNS;
            int anchorRow = Math.max(0, selectedRow - 1);
            int anchorChildIndex = Math.min(levelGrid.getChildCount() - 1, anchorRow * LEVEL_GRID_COLUMNS);
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
                scheduleTimerTick();
                break;
            case MATCH:
                soundManager.playMatch();
                scheduleTimerTick();
                break;
            case WIN:
                stopTimer();
                soundManager.playWin();
                animateResultScreen();
                break;
            case MISMATCH:
                stopTimer();
                soundManager.playWrong();
                handler.removeCallbacks(mismatchRunnable);
                handler.postDelayed(mismatchRunnable, MemoryViewModel.MISMATCH_DELAY_MS);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void startLevel(int index) {
        stopTimer();
        handler.removeCallbacks(mismatchRunnable);
        viewModel.startLevel(index);
        render();
        scheduleTimerTick();
    }

    private void showPauseDialog() {
        if (viewModel.getCurrentScreen() != MemoryViewModel.Screen.GAMEPLAY) {
            finish();
            return;
        }
        stopTimer();
        viewModel.showPause();
        render();
    }

    private void scheduleTimerTick() {
        if (timerScheduled || viewModel == null || viewModel.getCurrentScreen() != MemoryViewModel.Screen.GAMEPLAY || viewModel.isPauseVisible() || viewModel.isBoardLocked()) {
            return;
        }
        timerScheduled = true;
        handler.postDelayed(timerRunnable, 1000L);
    }

    private void stopTimer() {
        timerScheduled = false;
        handler.removeCallbacks(timerRunnable);
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
        stopTimer();
        handler.removeCallbacks(mismatchRunnable);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null
                && viewModel.getCurrentScreen() == MemoryViewModel.Screen.GAMEPLAY
                && !viewModel.isPauseVisible()
                && !viewModel.isBoardLocked()) {
            scheduleTimerTick();
        }
    }

    @Override
    protected void onDestroy() {
        viewModel.removeObserver(stateObserver);
        soundManager.release();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
