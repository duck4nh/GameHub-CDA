package com.example.gamehub.games.memory;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.repository.GameRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemoryViewModel extends AndroidViewModel {
    public enum Screen {
        SETUP,
        GAMEPLAY,
        RESULT
    }

    public interface Observer {
        void onStateChanged();
    }

    public enum TurnType {
        NONE,
        FIRST_REVEAL,
        MATCH,
        MISMATCH,
        WIN
    }

    public static class TurnOutcome {
        public final TurnType type;
        public final int firstPosition;
        public final int secondPosition;
        public final int awardedScore;

        public TurnOutcome(TurnType type, int firstPosition, int secondPosition, int awardedScore) {
            this.type = type;
            this.firstPosition = firstPosition;
            this.secondPosition = secondPosition;
            this.awardedScore = awardedScore;
        }
    }

    public static final long MISMATCH_DELAY_MS = 900L;

    private final GameRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private final List<MemoryLevel> levels = new ArrayList<>();
    private final List<MemoryCard> cards = new ArrayList<>();
    private final Random random = new Random();

    private Screen currentScreen = Screen.SETUP;
    private boolean initialized;
    private boolean loading;
    private boolean pauseVisible;
    private boolean boardLocked;
    private int selectedLevelIndex;
    private int currentLevelIndex;
    private int firstSelectedPosition = -1;
    private int secondSelectedPosition = -1;
    private int matchedPairs;
    private int pairAttempts;
    private int currentStreak;
    private int bestStreak;
    private int score;
    private int boardStateVersion;
    private long remainingTimeMs;
    private long elapsedTimeMs;

    public MemoryViewModel(@NonNull Application application) {
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
                List<MemoryLevel> items = repository.getMemoryLevels();
                mainHandler.post(() -> {
                    initialized = true;
                    loading = false;
                    levels.clear();
                    levels.addAll(items);
                    selectedLevelIndex = findHighestUnlockedLevelIndex();
                    currentLevelIndex = selectedLevelIndex;
                    notifyObservers();
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    loading = false;
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

    public boolean isBoardLocked() {
        return boardLocked;
    }

    public List<MemoryLevel> getLevels() {
        return new ArrayList<>(levels);
    }

    public List<MemoryCard> getCards() {
        return new ArrayList<>(cards);
    }

    public int getSelectedLevelIndex() {
        return selectedLevelIndex;
    }

    public void selectLevel(int index) {
        if (index < 0 || index >= levels.size()) {
            return;
        }
        if (!levels.get(index).isUnlocked) {
            return;
        }
        selectedLevelIndex = index;
        notifyObservers();
    }

    public void startSelectedLevel() {
        startLevel(selectedLevelIndex);
    }

    public void startLevel(int index) {
        if (index < 0 || index >= levels.size()) {
            return;
        }
        MemoryLevel level = levels.get(index);
        if (!level.isUnlocked) {
            return;
        }
        selectedLevelIndex = index;
        currentLevelIndex = index;
        currentScreen = Screen.GAMEPLAY;
        pauseVisible = false;
        boardLocked = false;
        firstSelectedPosition = -1;
        secondSelectedPosition = -1;
        matchedPairs = 0;
        pairAttempts = 0;
        currentStreak = 0;
        bestStreak = 0;
        score = 0;
        elapsedTimeMs = 0L;
        remainingTimeMs = level.timeLimitSec * 1000L;
        buildDeck(level);
        notifyObservers();
    }

    public boolean tick() {
        if (currentScreen != Screen.GAMEPLAY || pauseVisible || boardLocked || levels.isEmpty()) {
            return false;
        }
        remainingTimeMs = Math.max(0L, remainingTimeMs - 1000L);
        elapsedTimeMs += 1000L;
        notifyObservers();
        if (remainingTimeMs <= 0L) {
            finishGame(false);
            return true;
        }
        return false;
    }

    public TurnOutcome onCardSelected(int position) {
        if (currentScreen != Screen.GAMEPLAY || boardLocked || position < 0 || position >= cards.size()) {
            return new TurnOutcome(TurnType.NONE, -1, -1, 0);
        }

        MemoryCard tappedCard = cards.get(position);
        if (tappedCard.matched || tappedCard.revealed) {
            return new TurnOutcome(TurnType.NONE, -1, -1, 0);
        }

        tappedCard.revealed = true;
        markBoardChanged();
        if (firstSelectedPosition < 0) {
            firstSelectedPosition = position;
            notifyObservers();
            return new TurnOutcome(TurnType.FIRST_REVEAL, position, -1, 0);
        }

        secondSelectedPosition = position;
        pairAttempts++;
        MemoryCard firstCard = cards.get(firstSelectedPosition);
        MemoryCard secondCard = cards.get(secondSelectedPosition);

        if (firstCard.identifier == secondCard.identifier) {
            firstCard.matched = true;
            secondCard.matched = true;
            markBoardChanged();
            matchedPairs++;
            currentStreak++;
            bestStreak = Math.max(bestStreak, currentStreak);
            int awardedScore = 80 + (int) (remainingTimeMs / 1000L) * 3 + Math.max(0, currentStreak - 1) * 15;
            score += awardedScore;
            int resolvedFirst = firstSelectedPosition;
            int resolvedSecond = secondSelectedPosition;
            resetSelection();
            if (matchedPairs == cards.size() / 2) {
                finishGame(true);
                return new TurnOutcome(TurnType.WIN, resolvedFirst, resolvedSecond, awardedScore);
            }
            notifyObservers();
            return new TurnOutcome(TurnType.MATCH, resolvedFirst, resolvedSecond, awardedScore);
        }

        currentStreak = 0;
        boardLocked = true;
        notifyObservers();
        return new TurnOutcome(TurnType.MISMATCH, firstSelectedPosition, secondSelectedPosition, 0);
    }

    public void resolveMismatch() {
        if (firstSelectedPosition < 0 || secondSelectedPosition < 0) {
            boardLocked = false;
            notifyObservers();
            return;
        }
        cards.get(firstSelectedPosition).revealed = false;
        cards.get(secondSelectedPosition).revealed = false;
        markBoardChanged();
        resetSelection();
        boardLocked = false;
        notifyObservers();
    }

    public void showPause() {
        if (currentScreen == Screen.GAMEPLAY) {
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

    public void showLevelSelection() {
        currentScreen = Screen.SETUP;
        pauseVisible = false;
        boardLocked = false;
        resetSelection();
        selectedLevelIndex = findHighestUnlockedLevelIndex();
        currentLevelIndex = selectedLevelIndex;
        notifyObservers();
    }

    public MemoryLevel getCurrentLevel() {
        if (currentLevelIndex < 0 || currentLevelIndex >= levels.size()) {
            return null;
        }
        return levels.get(currentLevelIndex);
    }

    public int getMatchedPairs() {
        return matchedPairs;
    }

    public int getPairAttempts() {
        return pairAttempts;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public int getScore() {
        return score;
    }

    public int getBoardStateVersion() {
        return boardStateVersion;
    }

    public int getAccuracyPercent() {
        if (pairAttempts == 0) {
            return 0;
        }
        return Math.round(matchedPairs * 100f / pairAttempts);
    }

    public long getRemainingTimeMs() {
        return remainingTimeMs;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public boolean canPlayNextLevel() {
        return currentLevelIndex + 1 < levels.size() && levels.get(currentLevelIndex + 1).isUnlocked;
    }

    public int getNextLevelIndex() {
        return currentLevelIndex + 1;
    }

    private void finishGame(boolean won) {
        currentScreen = Screen.RESULT;
        pauseVisible = false;
        boardLocked = true;

        MemoryLevel currentLevel = getCurrentLevel();
        if (currentLevel != null && won) {
            if (currentLevel.bestTimeMs == 0L || elapsedTimeMs < currentLevel.bestTimeMs) {
                currentLevel.bestTimeMs = elapsedTimeMs;
            }
            if (currentLevelIndex + 1 < levels.size()) {
                levels.get(currentLevelIndex + 1).isUnlocked = true;
            }
        }
        notifyObservers();

        if (currentLevel == null) {
            return;
        }
        LocalHistory history = new LocalHistory(
                "memory",
                won ? "won" : "lost",
                score,
                elapsedTimeMs,
                System.currentTimeMillis(),
                false,
                String.format(Locale.getDefault(), "Màn %d · %s", currentLevel.levelId, currentLevel.getDisplayLabel()),
                pairAttempts
        );

        executor.execute(() -> {
            repository.saveHistory(history);
            repository.completeMemoryLevel(currentLevel.levelId, elapsedTimeMs, won);
            List<MemoryLevel> refreshedLevels = repository.getMemoryLevels();
            mainHandler.post(() -> {
                levels.clear();
                levels.addAll(refreshedLevels);
                selectedLevelIndex = findHighestUnlockedLevelIndex();
                notifyObservers();
            });
        });
    }

    private void buildDeck(MemoryLevel level) {
        cards.clear();
        int pairCount = level.getPairCount();
        List<Integer> bestArrangement = buildSmartArrangement(pairCount, level.rowCount, level.columnCount);
        long nextCardId = 1L;
        for (Integer identifier : bestArrangement) {
            cards.add(new MemoryCard(nextCardId++, identifier, buildLabel(identifier), identifier % 8));
        }
        markBoardChanged();
    }

    private List<Integer> buildSmartArrangement(int pairCount, int rowCount, int columnCount) {
        List<Integer> source = new ArrayList<>();
        for (int identifier = 1; identifier <= pairCount; identifier++) {
            source.add(identifier);
            source.add(identifier);
        }

        List<Integer> best = new ArrayList<>(source);
        int bestConflictScore = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 80; attempt++) {
            List<Integer> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, random);
            int conflictScore = calculateAdjacencyConflicts(shuffled, rowCount, columnCount);
            if (conflictScore < bestConflictScore) {
                bestConflictScore = conflictScore;
                best = shuffled;
                if (conflictScore == 0) {
                    break;
                }
            }
        }
        return best;
    }

    private int calculateAdjacencyConflicts(List<Integer> values, int rowCount, int columnCount) {
        int conflicts = 0;
        for (int row = 0; row < rowCount; row++) {
            for (int column = 0; column < columnCount; column++) {
                int index = row * columnCount + column;
                int value = values.get(index);
                if (column + 1 < columnCount && value == values.get(index + 1)) {
                    conflicts++;
                }
                if (row + 1 < rowCount && value == values.get(index + columnCount)) {
                    conflicts++;
                }
            }
        }
        return conflicts;
    }

    private String buildLabel(int identifier) {
        int zeroBased = Math.max(0, identifier - 1);
        char letter = (char) ('A' + (zeroBased % 26));
        int suffix = zeroBased / 26 + 1;
        return suffix == 1 ? String.valueOf(letter) : letter + String.valueOf(suffix);
    }

    private int findHighestUnlockedLevelIndex() {
        for (int index = levels.size() - 1; index >= 0; index--) {
            if (levels.get(index).isUnlocked) {
                return index;
            }
        }
        return 0;
    }

    private void resetSelection() {
        firstSelectedPosition = -1;
        secondSelectedPosition = -1;
    }

    private void markBoardChanged() {
        boardStateVersion++;
    }

    private void notifyObservers() {
        for (Observer observer : observers) {
            observer.onStateChanged();
        }
    }

    public String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }
}
