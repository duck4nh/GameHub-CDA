package com.example.gamehub.games.sudoku;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

import com.example.gamehub.R;
import com.example.gamehub.ai.GeminiReviewService;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.dao.SudokuDao;
import com.example.gamehub.data.local.dao.SudokuGameStateDao;
import com.example.gamehub.data.local.dao.SudokuStatsDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.SudokuBoard;
import com.example.gamehub.data.local.entities.SudokuGameState;
import com.example.gamehub.data.local.entities.SudokuStats;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.repository.GameRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SudokuActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (gameplayScreen.getVisibility() != View.VISIBLE || currentBoardEntity == null || sessionFinished || pauseOverlay.getVisibility() == View.VISIBLE) {
                return;
            }
            elapsedTimeMs += 1000L;
            renderGameplayMeta();
            handler.postDelayed(this, 1000L);
        }
    };

    private SudokuDao sudokuDao;
    private SudokuGameStateDao sudokuGameStateDao;
    private SudokuStatsDao sudokuStatsDao;
    private PreferenceManager preferenceManager;
    private GameRepository repository;
    private GeminiReviewService reviewService;

    private View setupScreen;
    private View gameplayScreen;
    private View resultScreen;
    private View pauseOverlay;
    private View continueCard;

    private TextView continueTitleView;
    private TextView continueSummaryView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView toolTextView;
    private TextView pauseMessageView;

    private TextView resultTitleView;
    private TextView resultSubtitleView;
    private TextView resultLevelValueView;
    private TextView resultErrorsValueView;
    private TextView resultRewardValueView;
    private TextView resultNoteView;
    private TextView resultSyncBadgeView;

    private TextView heart1, heart2, heart3;
    private TextView hintButtonText;
    private TextView notesButtonText;
    private View[] numberButtons = new View[9];

    private SudokuGridView boardView;

    private String selectedLevel = "easy";
    private String gameplaySubtitleBeforePause;
    private SudokuBoard currentBoardEntity;
    private SudokuBoard continueBoard;
    private SudokuGameState continueState;
    private int[][] initialBoard = new int[9][9];
    private int[][] solutionBoard = new int[9][9];
    private int[][] currentBoard = new int[9][9];
    private long elapsedTimeMs;
    private boolean sessionFinished;
    private int currentErrorCount;
    private int remainingHints = 3;
    private boolean notesMode = false;
    private final List<String> sessionLog = new ArrayList<>();
    private String activeReviewKey = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_sudoku);

        AppDatabase database = AppDatabase.getInstance(this);
        sudokuDao = database.sudokuDao();
        sudokuGameStateDao = database.sudokuGameStateDao();
        sudokuStatsDao = database.sudokuStatsDao();
        preferenceManager = new PreferenceManager(this);
        repository = GameRepository.getInstance(this);
        reviewService = GeminiReviewService.getInstance(this);

        bindViews();
        bindActions();
        installBackHandler();

        selectedLevel = preferenceManager.getString(PreferenceManager.KEY_LAST_SUDOKU_LEVEL, "easy");
        showSetupScreen();
    }

    private void bindViews() {
        setupScreen = findViewById(R.id.sudoku_setup_screen);
        gameplayScreen = findViewById(R.id.sudoku_gameplay_screen);
        resultScreen = findViewById(R.id.sudoku_result_screen);
        pauseOverlay = findViewById(R.id.sudoku_pause_screen);
        if (pauseOverlay == null) {
            pauseOverlay = findViewById(R.id.sudoku_pause_overlay);
        }
        continueCard = findViewById(R.id.sudoku_continue_card);

        continueTitleView = findViewById(R.id.sudoku_continue_title);
        continueSummaryView = findViewById(R.id.sudoku_continue_summary);
        titleView = findViewById(R.id.sudoku_title);
        subtitleView = findViewById(R.id.sudoku_subtitle);
        toolTextView = findViewById(R.id.sudoku_tool_text);
        pauseMessageView = findViewById(R.id.sudoku_pause_message);

        resultTitleView = findViewById(R.id.sudoku_result_title);
        resultSubtitleView = findViewById(R.id.sudoku_result_subtitle);
        resultLevelValueView = findViewById(R.id.sudoku_result_level_value);
        resultErrorsValueView = findViewById(R.id.sudoku_result_errors_value);
        resultRewardValueView = findViewById(R.id.sudoku_result_reward_value);
        resultNoteView = findViewById(R.id.sudoku_result_note);
        resultSyncBadgeView = findViewById(R.id.sudoku_result_sync_badge);

        heart1 = findViewById(R.id.sudoku_heart_1);
        heart2 = findViewById(R.id.sudoku_heart_2);
        heart3 = findViewById(R.id.sudoku_heart_3);

        hintButtonText = findViewById(R.id.sudoku_hint_button_text);
        notesButtonText = findViewById(R.id.sudoku_notes_button_text);

        numberButtons[0] = findViewById(R.id.sudoku_number_1);
        numberButtons[1] = findViewById(R.id.sudoku_number_2);
        numberButtons[2] = findViewById(R.id.sudoku_number_3);
        numberButtons[3] = findViewById(R.id.sudoku_number_4);
        numberButtons[4] = findViewById(R.id.sudoku_number_5);
        numberButtons[5] = findViewById(R.id.sudoku_number_6);
        numberButtons[6] = findViewById(R.id.sudoku_number_7);
        numberButtons[7] = findViewById(R.id.sudoku_number_8);
        numberButtons[8] = findViewById(R.id.sudoku_number_9);

        boardView = findViewById(R.id.sudoku_board);
    }

    private void bindActions() {
        findViewById(R.id.sudoku_setup_back).setOnClickListener(v -> finish());
        continueCard.setOnClickListener(v -> startSavedBoard());
        findViewById(R.id.sudoku_tile_easy).setOnClickListener(v -> selectLevel("easy"));
        findViewById(R.id.sudoku_tile_medium).setOnClickListener(v -> selectLevel("medium"));
        findViewById(R.id.sudoku_tile_hard).setOnClickListener(v -> selectLevel("hard"));
        findViewById(R.id.sudoku_tile_expert).setOnClickListener(v -> selectLevel("expert"));
        findViewById(R.id.sudoku_start_button).setOnClickListener(v -> startSelectedLevel());

        findViewById(R.id.sudoku_back).setOnClickListener(v -> showPauseOverlay());
        findViewById(R.id.sudoku_tool_card).setOnClickListener(v -> showPauseOverlay());

        for (int index = 0; index < 9; index++) {
            final int value = index + 1;
            numberButtons[index].setOnClickListener(v -> boardView.setSelectedValue(value));
        }

        findViewById(R.id.sudoku_action_reset).setOnClickListener(v -> {
            if (currentBoardEntity != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Làm mới trò chơi?")
                        .setMessage("Tất cả tiến trình của bàn này sẽ bị xóa. Bạn có chắc chắn muốn bắt đầu lại không?")
                        .setPositiveButton("Bắt đầu lại", (dialog, which) -> startBoard(currentBoardEntity, null))
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });

        findViewById(R.id.sudoku_action_hint).setOnClickListener(v -> useHint());

        findViewById(R.id.sudoku_action_notes).setOnClickListener(v -> toggleNotesMode());

        findViewById(R.id.sudoku_action_delete).setOnClickListener(v -> {
            logDeleteAction();
            boardView.clearSelectedCell();
        });

        findViewById(R.id.sudoku_pause_resume).setOnClickListener(v -> {
            appendSessionLog(String.format(Locale.getDefault(), "%s Tiếp tục ván Sudoku.", getElapsedLabel()));
            hidePauseOverlay(true);
        });
        findViewById(R.id.sudoku_pause_exit).setOnClickListener(v -> finish());

        findViewById(R.id.sudoku_result_retry).setOnClickListener(v -> startSelectedLevel());
        findViewById(R.id.sudoku_result_change_level).setOnClickListener(v -> showSetupScreen());
        findViewById(R.id.sudoku_result_close).setOnClickListener(v -> finish());

        boardView.setOnBoardChangedListener(new SudokuGridView.OnBoardChangedListener() {
            @Override
            public void onCellSelected(int row, int col, boolean editable) {
                if (!sessionFinished && pauseOverlay.getVisibility() != View.VISIBLE) {
                    appendSessionLog(String.format(
                            Locale.getDefault(),
                            "%s Chọn ô R%dC%d (%s).",
                            getElapsedLabel(),
                            row + 1,
                            col + 1,
                            editable ? "có thể sửa" : "ô cố định"
                    ));
                    renderGameplayMeta();
                }
            }

            @Override
            public void onBoardChanged(int[][] board) {
                int[][] previousBoard = SudokuLogic.copyMatrix(currentBoard);
                currentBoard = SudokuLogic.copyMatrix(board);
                logBoardMutation(previousBoard, currentBoard);
                int oldErrors = currentErrorCount;
                currentErrorCount = SudokuLogic.countIncorrectFilledCells(currentBoard, solutionBoard);
                
                if (currentErrorCount > oldErrors) {
                    appendSessionLog(String.format(
                            Locale.getDefault(),
                            "%s Tăng lên %d lỗi.",
                            getElapsedLabel(),
                            currentErrorCount
                    ));
                    updateHearts();
                    if (currentErrorCount >= 3) {
                        finishSession(false);
                        return;
                    }
                }
                
                updateNumberButtonsVisibility();
                renderGameplayMeta();
                if (SudokuLogic.isSolved(currentBoard, solutionBoard)) {
                    finishSession(true);
                }
            }
        });
    }

    private void useHint() {
        if (remainingHints <= 0 || sessionFinished) return;

        List<int[]> emptyCells = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (currentBoard[r][c] == 0) {
                    emptyCells.add(new int[]{r, c});
                }
            }
        }

        if (emptyCells.isEmpty()) return;

        int[] randomCell = emptyCells.get(new Random().nextInt(emptyCells.size()));
        int row = randomCell[0];
        int col = randomCell[1];
        int correctValue = solutionBoard[row][col];

        appendSessionLog(String.format(
                Locale.getDefault(),
                "%s Dùng gợi ý cho ô R%dC%d -> %d.",
                getElapsedLabel(),
                row + 1,
                col + 1,
                correctValue
        ));
        boardView.setCellValue(row, col, correctValue);
        remainingHints--;
        updateHintButtonUI();
    }

    private void toggleNotesMode() {
        notesMode = !notesMode;
        boardView.setNotesMode(notesMode);
        appendSessionLog(String.format(
                Locale.getDefault(),
                "%s Chuyển chế độ nháp sang %s.",
                getElapsedLabel(),
                notesMode ? "bật" : "tắt"
        ));
        updateNotesButtonUI();
    }

    private void updateNotesButtonUI() {
        notesButtonText.setText(notesMode ? "Nháp: Bật" : "Nháp: Tắt");
        findViewById(R.id.sudoku_notes_button_icon).setAlpha(notesMode ? 1f : 0.6f);
    }

    private void updateHintButtonUI() {
        hintButtonText.setText(String.format(Locale.getDefault(), "Gợi ý (%d)", remainingHints));
        findViewById(R.id.sudoku_action_hint).setAlpha(remainingHints > 0 ? 1f : 0.5f);
        findViewById(R.id.sudoku_action_hint).setEnabled(remainingHints > 0);
    }

    private void updateNumberButtonsVisibility() {
        if (currentBoard == null || solutionBoard == null) return;
        for (int i = 1; i <= 9; i++) {
            int count = SudokuLogic.countCorrectOccurrences(currentBoard, solutionBoard, i);
            numberButtons[i - 1].setVisibility(count >= 9 ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void updateHearts() {
        if (currentErrorCount >= 1) heart3.setText("🖤");
        if (currentErrorCount >= 2) heart2.setText("🖤");
        if (currentErrorCount >= 3) heart1.setText("🖤");
    }

    private void resetHearts() {
        heart1.setText("❤️");
        heart2.setText("❤️");
        heart3.setText("❤️");
    }

    private void showGameOverDialog() {
        sessionFinished = true;
        appendSessionLog(String.format(Locale.getDefault(), "%s Tạm dừng ván Sudoku.", getElapsedLabel()));
        handler.removeCallbacks(timerRunnable);
        new AlertDialog.Builder(this)
                .setTitle("Bạn đã thua!")
                .setMessage("Bạn đã mắc 3 lỗi. Trò chơi kết thúc.")
                .setPositiveButton("Thoát", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void installBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pauseOverlay.getVisibility() == View.VISIBLE) {
                    appendSessionLog(String.format(Locale.getDefault(), "%s Tiếp tục ván Sudoku.", getElapsedLabel()));
                    hidePauseOverlay(true);
                    return;
                }
                if (gameplayScreen.getVisibility() == View.VISIBLE) {
                    showPauseOverlay();
                    return;
                }
                if (resultScreen.getVisibility() == View.VISIBLE) {
                    showSetupScreen();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private void showSetupScreen() {
        handler.removeCallbacks(timerRunnable);
        setupScreen.setVisibility(View.VISIBLE);
        gameplayScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.GONE);
        hidePauseOverlay(false);
        refreshSetupState();
    }

    private void showGameplayScreen() {
        setupScreen.setVisibility(View.GONE);
        gameplayScreen.setVisibility(View.VISIBLE);
        resultScreen.setVisibility(View.GONE);
        hidePauseOverlay(false);
    }

    private void showResultScreen() {
        handler.removeCallbacks(timerRunnable);
        setupScreen.setVisibility(View.GONE);
        gameplayScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.VISIBLE);
        hidePauseOverlay(false);
    }

    private void refreshSetupState() {
        ensureValidSelectedLevel();
        loadContinueState();
        updateLevelTile(R.id.sudoku_tile_easy, R.id.sudoku_status_easy, "easy");
        updateLevelTile(R.id.sudoku_tile_medium, R.id.sudoku_status_medium, "medium");
        updateLevelTile(R.id.sudoku_tile_hard, R.id.sudoku_status_hard, "hard");
        updateLevelTile(R.id.sudoku_tile_expert, R.id.sudoku_status_expert, "expert");

        View startButton = findViewById(R.id.sudoku_start_button);
        boolean hasBoard = sudokuDao.getBoardByLevel(selectedLevel) != null;
        startButton.setEnabled(hasBoard);
        startButton.setAlpha(hasBoard ? 1f : 0.45f);
    }

    private void ensureValidSelectedLevel() {
        if (sudokuDao.getBoardByLevel(selectedLevel) != null) {
            return;
        }
        String[] levels = {"easy", "medium", "hard", "expert"};
        for (String level : levels) {
            if (sudokuDao.getBoardByLevel(level) != null) {
                selectedLevel = level;
                preferenceManager.putString(PreferenceManager.KEY_LAST_SUDOKU_LEVEL, level);
                return;
            }
        }
    }

    private void loadContinueState() {
        continueState = sudokuGameStateDao.getLatestState();
        continueBoard = continueState == null ? null : sudokuDao.getBoard(continueState.boardId);

        boolean canContinue = continueState != null && continueBoard != null && continueState.currentMatrix != null && !continueState.currentMatrix.isEmpty();
        continueCard.setClickable(canContinue);
        continueCard.setFocusable(canContinue);
        continueCard.setAlpha(canContinue ? 1f : 0.75f);
        if (!canContinue) {
            continueTitleView.setText("Chưa có bàn đang chơi dở");
            continueSummaryView.setText("Ván gần nhất sẽ hiện ở đây để bạn quay lại nhanh hơn.");
            return;
        }

        int[][] savedBoard = SudokuLogic.parseMatrix(continueState.currentMatrix);
        int correctCells = SudokuLogic.countCorrectCells(savedBoard, SudokuLogic.parseMatrix(continueBoard.solutionMatrix));
        continueTitleView.setText("Tiếp tục · " + getLevelLabel(continueBoard.level));
        continueSummaryView.setText(String.format(
                Locale.getDefault(),
                "%s đã chơi · %d/81 ô đúng",
                formatDuration(continueState.elapsedTime),
                correctCells
        ));
    }

    private void updateLevelTile(int tileId, int statusId, String level) {
        View tile = findViewById(tileId);
        TextView statusView = findViewById(statusId);
        boolean isSelected = level.equals(selectedLevel);
        boolean hasBoard = sudokuDao.getBoardByLevel(level) != null;

        tile.setEnabled(hasBoard);
        tile.setAlpha(hasBoard ? 1f : 0.5f);
        tile.setBackgroundResource(isSelected ? R.drawable.bg_tile_selected_sudoku : R.drawable.bg_card_surface_22);

        if (!hasBoard) {
            statusView.setText("Chưa có dữ liệu");
        } else if (isSelected) {
            statusView.setText("Đang chọn");
        } else {
            statusView.setText("Đã mở");
        }
    }

    private void selectLevel(String level) {
        selectedLevel = level;
        preferenceManager.putString(PreferenceManager.KEY_LAST_SUDOKU_LEVEL, level);
        refreshSetupState();
    }

    private void startSelectedLevel() {
        SudokuBoard board = sudokuDao.getBoardByLevel(selectedLevel);
        if (board == null) {
            Toast.makeText(this, "Chưa có bàn Sudoku cho cấp độ này.", Toast.LENGTH_SHORT).show();
            return;
        }
        sudokuGameStateDao.clearStateForBoard(board.id);
        startBoard(board, null);
    }

    private void startSavedBoard() {
        if (continueState == null || continueBoard == null) {
            return;
        }
        startBoard(continueBoard, continueState);
    }

    private void startBoard(SudokuBoard board, @Nullable SudokuGameState savedState) {
        currentBoardEntity = board;
        selectedLevel = board.level;
        preferenceManager.putString(PreferenceManager.KEY_LAST_SUDOKU_LEVEL, selectedLevel);
        initialBoard = SudokuLogic.parseMatrix(board.initialMatrix);
        solutionBoard = SudokuLogic.parseMatrix(board.solutionMatrix);
        currentBoard = SudokuLogic.copyMatrix(initialBoard);
        elapsedTimeMs = 0L;
        sessionFinished = false;
        currentErrorCount = 0;
        remainingHints = 3;
        notesMode = false;
        activeReviewKey = "";
        sessionLog.clear();
        appendSessionLog(String.format(
                Locale.getDefault(),
                "Bắt đầu bàn Sudoku mức %s%s.",
                getLevelLabel(board.level),
                savedState == null ? "" : " từ bản lưu"
        ));

        if (savedState != null && savedState.currentMatrix != null && !savedState.currentMatrix.isEmpty()) {
            int[][] restored = SudokuLogic.parseMatrix(savedState.currentMatrix);
            if (SudokuLogic.isSolved(restored, solutionBoard)) {
                sudokuGameStateDao.clearStateForBoard(board.id);
            } else {
                currentBoard = restored;
                elapsedTimeMs = Math.max(0L, savedState.elapsedTime);
                currentErrorCount = SudokuLogic.countIncorrectFilledCells(currentBoard, solutionBoard);
            }
        }

        boardView.setBoard(initialBoard, currentBoard);
        showGameplayScreen();
        resetHearts();
        updateHearts();
        updateHintButtonUI();
        updateNotesButtonUI();
        updateNumberButtonsVisibility();
        renderGameplayMeta();
        startTimer();
    }

    private void renderGameplayMeta() {
        if (currentBoardEntity == null) {
            return;
        }
        titleView.setText(String.format(Locale.getDefault(), "Sudoku · %s", getLevelLabel(currentBoardEntity.level)));
        gameplaySubtitleBeforePause = String.format(Locale.getDefault(), "Thời gian %s · %d lỗi", formatDuration(elapsedTimeMs), currentErrorCount);
        if (pauseOverlay.getVisibility() != View.VISIBLE) {
            subtitleView.setText(gameplaySubtitleBeforePause);
        }
        toolTextView.setText(String.format(Locale.getDefault(), "Ghi chú %s · %d gợi ý · Tạm dừng", (notesMode ? "bật" : "tắt"), remainingHints));
    }

    private void showPauseOverlay() {
        if (pauseOverlay == null || gameplayScreen.getVisibility() != View.VISIBLE || currentBoardEntity == null || sessionFinished) {
            return;
        }
        appendSessionLog(String.format(Locale.getDefault(), "%s Tạm dừng ván Sudoku.", getElapsedLabel()));
        handler.removeCallbacks(timerRunnable);
        saveInProgressState();
        subtitleView.setText("Tạm dừng");
        pauseMessageView.setText("Bạn có thể tiếp tục ngay hoặc quay lại sau.");
        pauseOverlay.setVisibility(View.VISIBLE);
    }

    private void hidePauseOverlay(boolean resumeTimer) {
        if (pauseOverlay == null) {
            return;
        }
        pauseOverlay.setVisibility(View.GONE);
        if (gameplayScreen.getVisibility() == View.VISIBLE && currentBoardEntity != null) {
            subtitleView.setText(gameplaySubtitleBeforePause);
        }
        if (resumeTimer && gameplayScreen.getVisibility() == View.VISIBLE && currentBoardEntity != null && !sessionFinished) {
            startTimer();
        }
    }

    private void finishSession(boolean won) {
        if (sessionFinished || currentBoardEntity == null) {
            return;
        }

        sessionFinished = true;
        handler.removeCallbacks(timerRunnable);
        sudokuGameStateDao.clearStateForBoard(currentBoardEntity.id);

        int reward = won ? 100 : 0;
        LocalHistory history = new LocalHistory(
                "Sudoku",
                won ? "won" : "lost",
                reward,
                elapsedTimeMs,
                System.currentTimeMillis(),
                false
        );
        updateSudokuStats(won);

        appendSessionLog(String.format(Locale.getDefault(), "Kết thúc bàn: %s, mức %s, %d lỗi, dùng %d gợi ý, thời gian %s.", won ? "thắng" : "thua", getLevelLabel(currentBoardEntity.level), currentErrorCount, 3 - remainingHints, formatDuration(elapsedTimeMs)));
        resultTitleView.setText(won
                ? String.format(Locale.getDefault(), "Hoàn thành trong %s", formatDuration(elapsedTimeMs))
                : String.format(Locale.getDefault(), "Dừng lại sau %s", formatDuration(elapsedTimeMs)));
        resultSubtitleView.setText(String.format(
                Locale.getDefault(),
                "Chế độ %s · %d lỗi · %d gợi ý · +%d điểm",
                getLevelLabel(currentBoardEntity.level).toLowerCase(Locale.getDefault()),
                currentErrorCount,
                (3 - remainingHints),
                reward
        ));
        resultLevelValueView.setText(getLevelLabel(currentBoardEntity.level));
        resultErrorsValueView.setText(String.valueOf(currentErrorCount));
        resultRewardValueView.setText(String.format(Locale.getDefault(), "+%d", reward));
        resultSyncBadgeView.setText("Đang kiểm tra");
        resultSyncBadgeView.setBackgroundResource(R.drawable.bg_chip_brand);
        resultNoteView.setText("AI đang phân tích ván Sudoku của bạn...");
        showResultScreen();
        requestSudokuAiReview();

        repository.saveHistory(history, result -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            applySyncResultToResultUi(result);
            if (!result.success && result.message != null && !result.message.trim().isEmpty()) {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateSudokuStats(boolean won) {
        SudokuStats stats = sudokuStatsDao.getStatsForLevel(selectedLevel);
        if (stats == null) {
            sudokuStatsDao.insert(new SudokuStats(selectedLevel, 1, won ? 1 : 0, won ? elapsedTimeMs : 0L));
            return;
        }

        int played = stats.gamesPlayed + 1;
        int winCount = stats.gamesWon + (won ? 1 : 0);
        long bestTime = stats.bestTime;
        if (won && (bestTime == 0L || elapsedTimeMs < bestTime)) {
            bestTime = elapsedTimeMs;
        }
        sudokuStatsDao.updateStats(stats.id, played, winCount, bestTime);
    }

    private void applySyncResultToResultUi(GameRepository.HistorySyncResult result) {
        if (result == null) {
            resultSyncBadgeView.setText("Đã lưu");
            resultSyncBadgeView.setBackgroundResource(R.drawable.bg_chip_warning);

            return;
        }

        if (result.success && result.remainingCount <= 0) {
            resultSyncBadgeView.setText("Đã lên Firebase");
            resultSyncBadgeView.setBackgroundResource(R.drawable.bg_chip_success);

            return;
        }

        resultSyncBadgeView.setText("Đã lưu");
        resultSyncBadgeView.setBackgroundResource(R.drawable.bg_chip_warning);

    }

    private void saveInProgressState() {
        if (currentBoardEntity == null || sessionFinished) {
            return;
        }
        if (!isBoardChanged() && elapsedTimeMs == 0L) {
            sudokuGameStateDao.clearStateForBoard(currentBoardEntity.id);
            return;
        }
        sudokuGameStateDao.clearStateForBoard(currentBoardEntity.id);
        sudokuGameStateDao.insert(new SudokuGameState(
                currentBoardEntity.id,
                SudokuLogic.serializeMatrix(currentBoard),
                elapsedTimeMs,
                System.currentTimeMillis()
        ));
    }

    private boolean isBoardChanged() {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (initialBoard[row][col] != currentBoard[row][col]) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startTimer() {
        handler.removeCallbacks(timerRunnable);
        if (gameplayScreen.getVisibility() == View.VISIBLE && currentBoardEntity != null && !sessionFinished && pauseOverlay.getVisibility() != View.VISIBLE) {
            handler.postDelayed(timerRunnable, 1000L);
        }
    }

    private String getLevelLabel(String level) {
        if ("medium".equals(level)) {
            return "Trung bình";
        }
        if ("hard".equals(level)) {
            return "Khó";
        }
        if ("expert".equals(level)) {
            return "Chuyên gia";
        }
        return "Dễ";
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void logNumberTap(int value) {
        int row = boardView.getSelectedRow();
        int col = boardView.getSelectedCol();
        if (row < 0 || col < 0) {
            return;
        }
        appendSessionLog(String.format(
                Locale.getDefault(),
                "%s %s số %d tại ô R%dC%d.",
                getElapsedLabel(),
                notesMode ? "Đánh dấu nháp" : "Chọn",
                value,
                row + 1,
                col + 1
        ));
    }

    private void logDeleteAction() {
        int row = boardView.getSelectedRow();
        int col = boardView.getSelectedCol();
        if (row < 0 || col < 0) {
            return;
        }
        appendSessionLog(String.format(
                Locale.getDefault(),
                "%s Xóa nội dung ô R%dC%d.",
                getElapsedLabel(),
                row + 1,
                col + 1
        ));
    }

    private void logBoardMutation(int[][] previousBoard, int[][] nextBoard) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (previousBoard[row][col] != nextBoard[row][col]) {
                    if (nextBoard[row][col] == 0) {
                        appendSessionLog(String.format(Locale.getDefault(), "%s Ô R%dC%d trở về trống.", getElapsedLabel(), row + 1, col + 1));
                    } else {
                        appendSessionLog(String.format(Locale.getDefault(), "%s Điền %d vào ô R%dC%d.", getElapsedLabel(), nextBoard[row][col], row + 1, col + 1));
                    }
                    return;
                }
            }
        }
    }

    private String buildSudokuReviewPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("Bạn là huấn luyện viên Sudoku. ")
                .append("Hãy phân tích đúng theo luật chơi của Sudoku trong GameHub để viết nhận xét cuối ván. ")
                .append("Mục tiêu là hoàn thành toàn bộ bàn trước khi chạm ngưỡng 3 lỗi; càng ít lỗi, ít dùng gợi ý, quản lý thời gian tốt và hoàn thành được bàn thì càng chơi chắc tay. ")
                .append("Nếu lỗi tăng nhanh, dùng nhiều gợi ý, hoặc dừng ở mức chưa hoàn thành thì đó là dấu hiệu suy luận chưa ổn định hoặc thao tác còn vội. ")
                .append("Nhật ký thao tác bên dưới là log theo trình tự thời gian. ")
                .append("Các dòng chọn ô, nhập số, xóa số, bật/tắt nháp, dùng gợi ý và báo lỗi đều có ý nghĩa hành vi: dùng nháp hợp lý thường là chơi cẩn thận; nhiều lỗi liên tiếp thường là thử sai; nhiều gợi ý nghĩa là đang phụ thuộc trợ giúp. ")
                .append("Hãy dựa vào log để suy ra cách suy nghĩ của người chơi, không chỉ nhìn kết quả cuối cùng. ")
                .append("Nếu kết quả chưa tốt thì dùng giọng nhẹ nhàng, kiểu tạm ổn hoặc còn khoảng để cải thiện, không gay gắt. ")
                .append("Nhận xét phải có đủ khen và chê: nêu rõ một điểm làm tốt, một điểm cần cải thiện, và nếu cần thì thêm một câu chốt ngắn.\n\n")
                .append("Tóm tắt ván chơi:\n")
                .append("- Cấp độ: ").append(getLevelLabel(selectedLevel)).append('\n')
                .append("- Kết quả: ").append(currentErrorCount >= 3 ? "Thua" : "Hoàn thành").append('\n')
                .append("- Lỗi: ").append(currentErrorCount).append('\n')
                .append("- Gợi ý đã dùng: ").append(3 - remainingHints).append('\n')
                .append("- Chế độ nháp cuối: ").append(notesMode ? "Bật" : "Tắt").append('\n')
                .append("- Thời gian: ").append(formatDuration(elapsedTimeMs)).append("\n\n")
                .append("Nhật ký thao tác:\n");
        if (sessionLog.isEmpty()) {
            builder.append("- Không có nhật ký chi tiết.");
        } else {
            for (String entry : sessionLog) {
                builder.append("- ").append(entry).append('\n');
            }
        }
        return builder.toString();
    }

    private void requestSudokuAiReview() {
        activeReviewKey = String.format(
                Locale.US,
                "%s:%d:%d:%d:%d",
                selectedLevel,
                currentErrorCount,
                remainingHints,
                elapsedTimeMs,
                sessionLog.size()
        );
        String reviewKey = activeReviewKey;
        reviewService.requestReview(buildSudokuReviewPrompt(), new GeminiReviewService.Callback() {
            @Override
            public void onSuccess(String review) {
                if (!reviewKey.equals(activeReviewKey) || isFinishing() || isDestroyed()) {
                    return;
                }
                resultNoteView.setText(review == null || review.trim().isEmpty()
                        ? "Chưa thể tạo nhận xét AI cho ván này."
                        : review.trim());
            }

            @Override
            public void onError(String message) {
                if (!reviewKey.equals(activeReviewKey) || isFinishing() || isDestroyed()) {
                    return;
                }
                resultNoteView.setText(message == null || message.trim().isEmpty()
                        ? "Chưa thể tạo nhận xét AI cho ván này."
                        : message.trim());
            }
        });
    }

    private void appendSessionLog(String entry) {
        if (entry == null) {
            return;
        }
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
            sessionLog.add(trimmed);
        }
    }

    private String getElapsedLabel() {
        return formatDuration(elapsedTimeMs);
    }

    @Override
    protected void onPause() {
        saveInProgressState();
        handler.removeCallbacks(timerRunnable);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameplayScreen.getVisibility() == View.VISIBLE && pauseOverlay.getVisibility() != View.VISIBLE && currentBoardEntity != null && !sessionFinished) {
            startTimer();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
