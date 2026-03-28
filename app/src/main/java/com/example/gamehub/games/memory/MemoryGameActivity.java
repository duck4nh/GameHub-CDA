package com.example.gamehub.games.memory;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.R;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.dao.MemoryDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.pref.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MemoryGameActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (gameplayScreen.getVisibility() != View.VISIBLE || currentLevel == null) {
                return;
            }
            remainingTimeMs = Math.max(0L, remainingTimeMs - 1000L);
            updateGameplayHeader();
            if (remainingTimeMs <= 0L) {
                finishLevel(false);
            } else {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private MemoryDao memoryDao;
    private HistoryDao historyDao;
    private PreferenceManager preferenceManager;

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
    private RecyclerView boardView;
    private TextView resultTitleView;
    private TextView resultSubtitleView;
    private TextView resultTimeValueView;
    private TextView resultAccuracyValueView;
    private TextView resultStreakValueView;
    private TextView resultNoteView;
    private TextView pauseMessageView;
    private Button nextLevelButton;

    private final List<MemoryLevel> levels = new ArrayList<>();
    private final List<MemoryCard> cards = new ArrayList<>();
    private MemoryBoardAdapter adapter;

    private MemoryLevel currentLevel;
    private int selectedLevelIndex;
    private int currentLevelIndex;
    private int firstSelectedPosition = -1;
    private int secondSelectedPosition = -1;
    private boolean waitingForFlipBack;
    private int matchedPairs;
    private int flipTurns;
    private int currentStreak;
    private int bestStreak;
    private long remainingTimeMs;
    private String gameplaySubtitleBeforePause;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_memory);

        AppDatabase database = AppDatabase.getInstance(this);
        memoryDao = database.memoryDao();
        historyDao = database.historyDao();
        preferenceManager = new PreferenceManager(this);

        bindViews();
        bindActions();

        adapter = new MemoryBoardAdapter(this::handleCardClick);
        boardView.setAdapter(adapter);

        levels.addAll(memoryDao.getAllLevels());
        selectedLevelIndex = findFirstUnlockedLevelIndex();
        buildLevelGrid();
        showSetupScreen();
        installBackHandler();
    }

    private void bindViews() {
        setupScreen = findViewById(R.id.memory_setup_screen);
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
        nextLevelButton = findViewById(R.id.memory_result_next);
    }

    private void bindActions() {
        findViewById(R.id.memory_setup_back).setOnClickListener(v -> finish());
        startButton.setOnClickListener(v -> startLevel(selectedLevelIndex));
        findViewById(R.id.memory_back).setOnClickListener(v -> showPauseDialog());
        findViewById(R.id.memory_pause).setOnClickListener(v -> showPauseDialog());
        findViewById(R.id.memory_result_retry).setOnClickListener(v -> startLevel(currentLevelIndex));
        findViewById(R.id.memory_result_close).setOnClickListener(v -> finish());
        findViewById(R.id.memory_pause_resume).setOnClickListener(v -> hidePauseOverlay(true));
        findViewById(R.id.memory_pause_exit).setOnClickListener(v -> finish());
        nextLevelButton.setOnClickListener(v -> {
            if (currentLevelIndex + 1 < levels.size() && levels.get(currentLevelIndex + 1).isUnlocked) {
                selectedLevelIndex = currentLevelIndex + 1;
                startLevel(selectedLevelIndex);
            }
        });
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

    private void buildLevelGrid() {
        levelGrid.removeAllViews();
        for (int i = 0; i < levels.size(); i++) {
            MemoryLevel level = levels.get(i);
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(14));
            tile.setBackgroundResource(i == selectedLevelIndex ? R.drawable.bg_tile_selected_memory : R.drawable.bg_card_surface_22);
            tile.setClickable(level.isUnlocked);
            tile.setFocusable(level.isUnlocked);
            tile.setAlpha(level.isUnlocked ? 1f : 0.55f);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(82);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, i < 2 ? 0 : dpToPx(14), dpToPx(i % 2 == 0 ? 10 : 0), 0);
            tile.setLayoutParams(params);

            TextView icon = new TextView(this);
            icon.setText("\u25CE");
            icon.setTextSize(18f);
            icon.setTextColor(getColor(R.color.gh_bg_brand));
            tile.addView(icon);

            TextView title = new TextView(this);
            title.setText(String.format(Locale.getDefault(), "%dx%d", level.gridSize, level.gridSize));
            title.setTextSize(18f);
            title.setTextColor(getColor(R.color.gh_text_primary));
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setPadding(0, dpToPx(8), 0, 0);
            tile.addView(title);

            TextView subtitle = new TextView(this);
            subtitle.setText(String.format(Locale.getDefault(), "%d cặp", (level.gridSize * level.gridSize) / 2));
            subtitle.setTextSize(12f);
            subtitle.setTextColor(getColor(R.color.gh_text_secondary));
            tile.addView(subtitle);

            final int index = i;
            tile.setOnClickListener(v -> {
                selectedLevelIndex = index;
                buildLevelGrid();
            });
            levelGrid.addView(tile);
        }
    }

    private int findFirstUnlockedLevelIndex() {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).isUnlocked) {
                return i;
            }
        }
        return 0;
    }

    private void showSetupScreen() {
        handler.removeCallbacks(timerRunnable);
        setupScreen.setVisibility(View.VISIBLE);
        gameplayScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.GONE);
        hidePauseOverlay(false);
        buildLevelGrid();
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

    private void startLevel(int levelIndex) {
        if (levels.isEmpty() || levelIndex < 0 || levelIndex >= levels.size()) {
            return;
        }
        MemoryLevel level = levels.get(levelIndex);
        if (!level.isUnlocked) {
            return;
        }

        selectedLevelIndex = levelIndex;
        currentLevelIndex = levelIndex;
        currentLevel = level;
        showGameplayScreen();
        hidePauseOverlay(false);
        firstSelectedPosition = -1;
        secondSelectedPosition = -1;
        waitingForFlipBack = false;
        matchedPairs = 0;
        flipTurns = 0;
        currentStreak = 0;
        bestStreak = 0;
        remainingTimeMs = level.timeLimit * 1000L;

        buildDeck(level.gridSize);
        boardView.setLayoutManager(new GridLayoutManager(this, level.gridSize));
        adapter.submitList(cards);
        updateGameplayHeader();

        handler.removeCallbacks(timerRunnable);
        if (remainingTimeMs > 0L) {
            handler.postDelayed(timerRunnable, 1000L);
        }
    }

    private void buildDeck(int gridSize) {
        cards.clear();
        int totalCards = gridSize * gridSize;
        int pairCount = totalCards / 2;
        List<Integer> values = new ArrayList<>();
        for (int i = 1; i <= pairCount; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values);
        for (Integer value : values) {
            cards.add(new MemoryCard(value));
        }
    }

    private void handleCardClick(int position) {
        if (waitingForFlipBack || position < 0 || position >= cards.size()) {
            return;
        }
        MemoryCard card = cards.get(position);
        if (card.matched || card.revealed) {
            return;
        }

        card.revealed = true;
        adapter.notifyItemChanged(position);

        if (firstSelectedPosition < 0) {
            firstSelectedPosition = position;
            return;
        }

        secondSelectedPosition = position;
        flipTurns++;
        MemoryCard firstCard = cards.get(firstSelectedPosition);
        MemoryCard secondCard = cards.get(secondSelectedPosition);

        if (firstCard.identifier == secondCard.identifier) {
            firstCard.matched = true;
            secondCard.matched = true;
            matchedPairs++;
            currentStreak++;
            bestStreak = Math.max(bestStreak, currentStreak);
            adapter.notifyItemChanged(firstSelectedPosition);
            adapter.notifyItemChanged(secondSelectedPosition);
            resetTurn();
            updateGameplayHeader();
            if (matchedPairs == cards.size() / 2) {
                finishLevel(true);
            }
            return;
        }

        currentStreak = 0;
        updateGameplayHeader();
        waitingForFlipBack = true;
        handler.postDelayed(() -> {
            firstCard.revealed = false;
            secondCard.revealed = false;
            adapter.notifyItemChanged(firstSelectedPosition);
            adapter.notifyItemChanged(secondSelectedPosition);
            resetTurn();
            waitingForFlipBack = false;
        }, 650L);
    }

    private void updateGameplayHeader() {
        if (currentLevel == null) {
            return;
        }
        levelLabelView.setText(String.format(Locale.getDefault(), "Ghi nhớ · %dx%d", currentLevel.gridSize, currentLevel.gridSize));
        gameplaySubtitleBeforePause = "Đang thi đấu";
        metaSmallView.setText(gameplaySubtitleBeforePause);
        bestStreakView.setText(String.format(Locale.getDefault(), "%d cặp liên tiếp", bestStreak));
        timerView.setText(formatDuration(remainingTimeMs));
        progressView.setText(String.format(Locale.getDefault(), "%d lượt", flipTurns));
    }

    private void finishLevel(boolean won) {
        handler.removeCallbacks(timerRunnable);
        long elapsed = currentLevel == null ? 0L : (currentLevel.timeLimit * 1000L) - remainingTimeMs;
        elapsed = Math.max(0L, elapsed);
        if (currentLevel != null && won) {
            if (currentLevel.bestTime == 0L || elapsed < currentLevel.bestTime) {
                memoryDao.updateBestTime(currentLevel.levelId, elapsed);
                currentLevel.bestTime = elapsed;
            }
            if (currentLevelIndex + 1 < levels.size()) {
                memoryDao.unlockLevel(levels.get(currentLevelIndex + 1).levelId);
                levels.get(currentLevelIndex + 1).isUnlocked = true;
            }
        }

        historyDao.insert(new LocalHistory("Ghi nhớ", won ? "won" : "lost", matchedPairs, elapsed, System.currentTimeMillis(), false));

        int accuracy = won ? 100 : Math.round((matchedPairs * 100f) / Math.max(1, cards.size() / 2));
        resultTitleView.setText(String.format(Locale.getDefault(), "%d lượt", flipTurns));
        resultSubtitleView.setText(String.format(Locale.getDefault(), "Thời gian %s · Chính xác %d%% · Chuỗi tốt nhất %d", formatDuration(elapsed), accuracy, bestStreak));
        resultTimeValueView.setText(formatDuration(elapsed));
        resultAccuracyValueView.setText(String.format(Locale.getDefault(), "%d%%", accuracy));
        resultStreakValueView.setText(String.valueOf(bestStreak));
        resultNoteView.setText(won
                ? "Phiên ghi nhớ đã được lưu cục bộ và dùng cho lịch sử, thống kê."
                : "Bạn có thể chơi lại để cải thiện số lượt lật và chuỗi tốt nhất.");
        nextLevelButton.setEnabled(currentLevelIndex + 1 < levels.size() && levels.get(currentLevelIndex + 1).isUnlocked);
        nextLevelButton.setAlpha(nextLevelButton.isEnabled() ? 1f : 0.45f);
        showResultScreen();
    }

    private void resetTurn() {
        firstSelectedPosition = -1;
        secondSelectedPosition = -1;
    }

    private void showPauseDialog() {
        if (gameplayScreen.getVisibility() != View.VISIBLE) {
            finish();
            return;
        }
        handler.removeCallbacks(timerRunnable);
        gameplaySubtitleBeforePause = metaSmallView.getText().toString();
        metaSmallView.setText("Tạm dừng");
        pauseMessageView.setText("Phiên ghi nhớ hiện tại đang được tạm dừng. Bạn có thể tiếp tục ngay hoặc thoát khỏi phiên.");
        pauseOverlay.setVisibility(View.VISIBLE);
    }

    private void hidePauseOverlay(boolean resumeTimer) {
        if (pauseOverlay == null) {
            return;
        }
        pauseOverlay.setVisibility(View.GONE);
        if (gameplayScreen.getVisibility() == View.VISIBLE && gameplaySubtitleBeforePause != null) {
            metaSmallView.setText(gameplaySubtitleBeforePause);
        }
        if (resumeTimer && gameplayScreen.getVisibility() == View.VISIBLE && currentLevel != null) {
            handler.removeCallbacks(timerRunnable);
            handler.postDelayed(timerRunnable, 1000L);
        }
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
                && currentLevel != null
                && (pauseOverlay == null || pauseOverlay.getVisibility() != View.VISIBLE)) {
            handler.postDelayed(timerRunnable, 1000L);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
