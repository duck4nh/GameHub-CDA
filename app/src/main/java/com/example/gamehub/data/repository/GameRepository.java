package com.example.gamehub.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.QuizAssetImporter;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.dao.MemoryDao;
import com.example.gamehub.data.local.dao.QuizDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.models.ChatMessage;
import com.example.gamehub.models.GameRecord;
import com.example.gamehub.models.LeaderboardEntry;
import com.example.gamehub.models.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GameRepository {
    private static final String DEFAULT_UID = "uid_duong";
    private static final String DEFAULT_NICKNAME = "Pham Hong Duong";

    private static GameRepository instance;

    private final Context appContext;
    private final PreferenceManager preferenceManager;
    private final HistoryDao historyDao;
    private final QuizDao quizDao;
    private final MemoryDao memoryDao;
    private final List<User> users = new ArrayList<>();
    private final List<GameRecord> gameRecords = new ArrayList<>();
    private final List<ChatMessage> chatMessages = new ArrayList<>();

    private boolean localDataReady;

    private GameRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(appContext);
        preferenceManager = new PreferenceManager(appContext);
        historyDao = database.historyDao();
        quizDao = database.quizDao();
        memoryDao = database.memoryDao();
        ensurePreferences();
        seedUsers();
        seedGameRecords();
        seedChatMessages();
        localDataReady = quizDao.getCount() > 0 && memoryDao.getCount() > 0;
    }

    public static synchronized GameRepository getInstance(Context context) {
        if (instance == null) {
            instance = new GameRepository(context);
        }
        return instance;
    }

    public synchronized void ensureLocalDataReady() throws IOException {
        if (memoryDao.getCount() == 0) {
            localDataReady = false;
        }
        if (quizDao.getCount() == 0) {
            List<QuizQuestion> questions = QuizAssetImporter.readQuestions(appContext);
            if (!questions.isEmpty()) {
                quizDao.insertAll(questions);
            }
        }
        localDataReady = quizDao.getCount() > 0 && memoryDao.getCount() > 0;
        preferenceManager.putLong(PreferenceManager.KEY_LAST_SYNC_TIME, System.currentTimeMillis());
    }

    public boolean isLocalDataReady() {
        return localDataReady;
    }

    public List<String> getQuizCategories() {
        return new ArrayList<>(quizDao.getDistinctCategories());
    }

    public List<QuizQuestion> getRandomQuizQuestions(List<String> categories, @Nullable String difficulty, int limit) {
        List<String> normalizedCategories = categories == null ? new ArrayList<>() : new ArrayList<>(categories);
        if (normalizedCategories.isEmpty()) {
            normalizedCategories.addAll(quizDao.getDistinctCategories());
        }

        boolean filterAllCategories = normalizedCategories.size() >= quizDao.getDistinctCategories().size();
        boolean hasDifficulty = difficulty != null && !difficulty.trim().isEmpty() && !"all".equalsIgnoreCase(difficulty);

        if (filterAllCategories) {
            return hasDifficulty
                    ? quizDao.getRandomQuestionsByDifficulty(difficulty, limit)
                    : quizDao.getRandomQuestions(limit);
        }
        return hasDifficulty
                ? quizDao.getRandomQuestionsByCategoriesAndDifficulty(normalizedCategories, difficulty, limit)
                : quizDao.getRandomQuestionsByCategories(normalizedCategories, limit);
    }

    public List<MemoryLevel> getMemoryLevels() {
        return new ArrayList<>(memoryDao.getAllLevels());
    }

    @Nullable
    public MemoryLevel getMemoryLevel(int levelId) {
        return memoryDao.getLevel(levelId);
    }

    public void completeMemoryLevel(int levelId, long elapsedMs, boolean won) {
        if (!won) {
            return;
        }
        MemoryLevel currentLevel = memoryDao.getLevel(levelId);
        if (currentLevel == null) {
            return;
        }
        if (currentLevel.bestTimeMs == 0L || elapsedMs < currentLevel.bestTimeMs) {
            memoryDao.updateBestTime(levelId, elapsedMs);
        }
        MemoryLevel nextLevel = memoryDao.getLevel(levelId + 1);
        if (nextLevel != null && !nextLevel.isUnlocked) {
            memoryDao.unlockLevel(nextLevel.levelId);
        }
    }

    public long saveHistory(LocalHistory historyItem) {
        return historyDao.insert(historyItem);
    }

    @Nullable
    public LocalHistory getBestHistoryForGame(String gameName) {
        return historyDao.getBestHistoryForGame(gameName);
    }

    public List<LeaderboardEntry> getLeaderboardEntries(boolean weekly) {
        return weekly ? getWeeklyLeaderboard() : getAllTimeLeaderboard();
    }

    @Nullable
    public LeaderboardEntry getCurrentUserEntry(boolean weekly) {
        String currentUid = getCurrentUid();
        for (LeaderboardEntry entry : getLeaderboardEntries(weekly)) {
            if (entry.getUid().equals(currentUid)) {
                return entry;
            }
        }
        return null;
    }

    public String getCurrentUserTrendLabel(boolean weekly) {
        if (!weekly) {
            return "Tong diem tich luy";
        }
        int delta = getCurrentUserWeeklyRankDelta();
        if (delta > 0) {
            return String.format(Locale.getDefault(), "+%d bac tuan nay", delta);
        }
        if (delta < 0) {
            return String.format(Locale.getDefault(), "%d bac tuan nay", delta);
        }
        return "Giu nguyen tuan nay";
    }

    public void setLeaderboardFilter(boolean weekly) {
        preferenceManager.putString(
                PreferenceManager.KEY_LEADERBOARD_FILTER,
                weekly ? "weekly" : "all_time"
        );
    }

    public boolean isWeeklyLeaderboardSelected() {
        return "weekly".equals(preferenceManager.getString(PreferenceManager.KEY_LEADERBOARD_FILTER, "weekly"));
    }

    public int getTotalMatches() {
        return historyDao.getCount();
    }

    public long getAverageCompletionTime() {
        Double average = historyDao.getAverageTimeSpent();
        return average == null ? 0L : Math.round(average);
    }

    public int getWinRate() {
        int total = historyDao.getCount();
        if (total == 0) {
            return 0;
        }
        return Math.round(historyDao.getSuccessfulCount() * 100f / total);
    }

    public int getWeeklyScore() {
        return getScoreForWindow(getCurrentUid(), getStartOfCurrentWeek(), Long.MAX_VALUE);
    }

    public String getWeeklyScoreChangeLabel() {
        int current = getWeeklyScore();
        Calendar previousWeekStart = Calendar.getInstance();
        previousWeekStart.setTimeInMillis(getStartOfCurrentWeek());
        previousWeekStart.add(Calendar.DAY_OF_YEAR, -7);
        int previous = getScoreForWindow(getCurrentUid(), previousWeekStart.getTimeInMillis(), getStartOfCurrentWeek() - 1);
        if (previous <= 0) {
            return current > 0 ? "Tuan dau tien co diem" : "Chua co diem tuan nay";
        }
        int percent = Math.round((current - previous) * 100f / previous);
        if (percent > 0) {
            return String.format(Locale.getDefault(), "+%d%% so voi tuan truoc", percent);
        }
        if (percent < 0) {
            return String.format(Locale.getDefault(), "%d%% so voi tuan truoc", percent);
        }
        return "Bang tuan truoc";
    }

    public int getCurrentStreakDays() {
        List<LocalHistory> historyItems = historyDao.getAllNewestFirst();
        if (historyItems.isEmpty()) {
            return 0;
        }
        List<Long> days = new ArrayList<>();
        Set<Long> seenDays = new HashSet<>();
        for (LocalHistory item : historyItems) {
            long day = getStartOfDay(item.playDate);
            if (seenDays.add(day)) {
                days.add(day);
            }
        }
        Collections.sort(days, Collections.reverseOrder());
        int streak = 1;
        for (int i = 1; i < days.size(); i++) {
            long diffDays = (days.get(i - 1) - days.get(i)) / (24L * 60L * 60L * 1000L);
            if (diffDays == 1) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    public int[] getPlayTimeMinutesByDay() {
        int[] values = new int[7];
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(getStartOfCurrentWeek());

        List<LocalHistory> historyItems = historyDao.getAllNewestFirst();
        for (LocalHistory item : historyItems) {
            if (item.playDate < start.getTimeInMillis()) {
                continue;
            }
            int index = getDayBucketIndex(start.getTimeInMillis(), item.playDate);
            if (index >= 0 && index < values.length) {
                values[index] += Math.max(1, Math.round(item.timeSpent / 60000f));
            }
        }
        return values;
    }

    public long getBestSudokuTime() {
        Long best = historyDao.getBestTimeForGame("sudoku");
        return best == null ? 0L : best;
    }

    public int getUnsyncedCount() {
        return historyDao.getUnsyncedCount();
    }

    public List<LocalHistory> getHistory(@Nullable String filter) {
        List<LocalHistory> items = new ArrayList<>(historyDao.getAllNewestFirst());
        if (filter == null || "all".equalsIgnoreCase(filter)) {
            return items;
        }
        List<LocalHistory> filtered = new ArrayList<>();
        for (LocalHistory item : items) {
            if (item.gameName.equalsIgnoreCase(filter)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public List<ChatMessage> getChatMessages() {
        List<ChatMessage> items = new ArrayList<>(chatMessages);
        items.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
        return items;
    }

    @Nullable
    public ChatMessage sendChatMessage(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ChatMessage message = new ChatMessage(
                "msg_" + System.currentTimeMillis(),
                getCurrentUid(),
                getCurrentNickname(),
                trimmed,
                System.currentTimeMillis()
        );
        chatMessages.add(message);
        chatMessages.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
        return message;
    }

    public String getCurrentUid() {
        return preferenceManager.getString(PreferenceManager.KEY_CURRENT_UID, DEFAULT_UID);
    }

    public String getCurrentNickname() {
        return preferenceManager.getString(PreferenceManager.KEY_CACHE_NICKNAME, DEFAULT_NICKNAME);
    }

    private void ensurePreferences() {
        if (preferenceManager.getString(PreferenceManager.KEY_CURRENT_UID, null) == null) {
            preferenceManager.putString(PreferenceManager.KEY_CURRENT_UID, DEFAULT_UID);
        }
        if (preferenceManager.getString(PreferenceManager.KEY_CACHE_NICKNAME, null) == null) {
            preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, DEFAULT_NICKNAME);
        }
        if (preferenceManager.getString(PreferenceManager.KEY_LEADERBOARD_FILTER, null) == null) {
            preferenceManager.putString(PreferenceManager.KEY_LEADERBOARD_FILTER, "weekly");
        }
        if (!preferenceManager.contains(PreferenceManager.KEY_IS_SOUND_ON)) {
            preferenceManager.putBoolean(PreferenceManager.KEY_IS_SOUND_ON, true);
        }
        if (!preferenceManager.contains(PreferenceManager.KEY_IS_ANIMATION_ON)) {
            preferenceManager.putBoolean(PreferenceManager.KEY_IS_ANIMATION_ON, true);
        }
        if (!preferenceManager.contains(PreferenceManager.KEY_LAST_SYNC_TIME)) {
            preferenceManager.putLong(PreferenceManager.KEY_LAST_SYNC_TIME, 0L);
        }
    }

    private void seedUsers() {
        if (!users.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        users.add(new User("uid_linh", "linh@ptit.edu.vn", "Linh", "", 1860, now));
        users.add(new User("uid_minh", "minh@ptit.edu.vn", "Minh", "", 1730, now));
        users.add(new User("uid_trang", "trang@ptit.edu.vn", "Trang", "", 1680, now));
        users.add(new User("uid_quoc", "anh@ptit.edu.vn", "Quoc Anh", "", 1410, now));
        users.add(new User(getCurrentUid(), "duong@ptit.edu.vn", getCurrentNickname(), "", 1042, now));
        users.add(new User("uid_an", "an@ptit.edu.vn", "An Nguyen", "", 992, now));
        users.add(new User("uid_tuan", "tuan@ptit.edu.vn", "Tuan Le", "", 964, now));
        users.add(new User("uid_mai", "mai@ptit.edu.vn", "Mai Ho", "", 932, now));
    }

    private void seedGameRecords() {
        if (!gameRecords.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long oneDay = 24L * 60L * 60L * 1000L;
        gameRecords.add(new GameRecord("r1", "uid_linh", "quiz", 620, 380000, "won", now - oneDay));
        gameRecords.add(new GameRecord("r2", "uid_linh", "sudoku", 520, 420000, "won", now - 2 * oneDay));
        gameRecords.add(new GameRecord("r3", "uid_minh", "memory", 740, 510000, "won", now - oneDay));
        gameRecords.add(new GameRecord("r4", "uid_minh", "quiz", 480, 350000, "won", now - 3 * oneDay));
        gameRecords.add(new GameRecord("r5", "uid_trang", "quiz", 540, 370000, "won", now - 2 * oneDay));
        gameRecords.add(new GameRecord("r6", "uid_trang", "memory", 410, 290000, "won", now - 4 * oneDay));
        gameRecords.add(new GameRecord("r7", getCurrentUid(), "quiz", 380, 381000, "won", now - oneDay));
        gameRecords.add(new GameRecord("r8", getCurrentUid(), "sudoku", 332, 554000, "won", now - 2 * oneDay));
        gameRecords.add(new GameRecord("r9", getCurrentUid(), "memory", 330, 414000, "won", now - 3 * oneDay));
        gameRecords.add(new GameRecord("r10", "uid_an", "quiz", 300, 410000, "won", now - oneDay));
        gameRecords.add(new GameRecord("r11", "uid_tuan", "memory", 272, 450000, "won", now - 2 * oneDay));
        gameRecords.add(new GameRecord("r12", "uid_mai", "sudoku", 240, 620000, "won", now - oneDay));
        gameRecords.add(new GameRecord("r13", getCurrentUid(), "quiz", 210, 420000, "won", now - 8 * oneDay));
        gameRecords.add(new GameRecord("r14", "uid_an", "quiz", 420, 400000, "won", now - 8 * oneDay));
        gameRecords.add(new GameRecord("r15", "uid_tuan", "memory", 510, 430000, "won", now - 9 * oneDay));
    }

    private void seedChatMessages() {
        if (!chatMessages.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        chatMessages.add(new ChatMessage("c1", "uid_trang", "Trang", "Ai ranh dau Ghi nho sau gio hoc?", now - 180000));
        chatMessages.add(new ChatMessage("c2", getCurrentUid(), "Ban", "Minh vao sau 10 phut nua.", now - 120000));
        chatMessages.add(new ChatMessage("c3", "uid_linh", "Linh", "Minh mo phong o muc Cong dong roi.", now - 60000));
    }

    private List<LeaderboardEntry> getAllTimeLeaderboard() {
        List<User> sortedUsers = new ArrayList<>(users);
        sortedUsers.sort((first, second) -> Integer.compare(second.getTotalScore(), first.getTotalScore()));
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < sortedUsers.size(); i++) {
            User user = sortedUsers.get(i);
            entries.add(new LeaderboardEntry(
                    user.getUid(),
                    user.getNickname(),
                    user.getTotalScore(),
                    i + 1,
                    user.getUid().equals(getCurrentUid())
            ));
        }
        return entries;
    }

    private List<LeaderboardEntry> getWeeklyLeaderboard() {
        long startOfWeek = getStartOfCurrentWeek();
        Map<String, Integer> scoreByUid = new HashMap<>();
        for (GameRecord record : gameRecords) {
            if (record.getDate() >= startOfWeek) {
                scoreByUid.put(record.getUid(), scoreByUid.getOrDefault(record.getUid(), 0) + record.getScore());
            }
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (User user : users) {
            Integer score = scoreByUid.get(user.getUid());
            if (score != null) {
                entries.add(new LeaderboardEntry(
                        user.getUid(),
                        user.getNickname(),
                        score,
                        0,
                        user.getUid().equals(getCurrentUid())
                ));
            }
        }
        entries.sort((first, second) -> Integer.compare(second.getScore(), first.getScore()));
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry original = entries.get(i);
            entries.set(i, new LeaderboardEntry(
                    original.getUid(),
                    original.getNickname(),
                    original.getScore(),
                    i + 1,
                    original.isCurrentUser()
            ));
        }
        return entries;
    }

    private int getCurrentUserWeeklyRankDelta() {
        int currentRank = getRankForWindow(getStartOfCurrentWeek(), Long.MAX_VALUE, getCurrentUid());
        if (currentRank <= 0) {
            return 0;
        }
        Calendar previousWeekStart = Calendar.getInstance();
        previousWeekStart.setTimeInMillis(getStartOfCurrentWeek());
        previousWeekStart.add(Calendar.DAY_OF_YEAR, -7);
        int previousRank = getRankForWindow(previousWeekStart.getTimeInMillis(), getStartOfCurrentWeek() - 1, getCurrentUid());
        if (previousRank <= 0) {
            return 0;
        }
        return previousRank - currentRank;
    }

    private int getRankForWindow(long start, long end, String uid) {
        Map<String, Integer> scoreByUid = new HashMap<>();
        for (GameRecord record : gameRecords) {
            if (record.getDate() >= start && record.getDate() <= end) {
                scoreByUid.put(record.getUid(), scoreByUid.getOrDefault(record.getUid(), 0) + record.getScore());
            }
        }
        if (!scoreByUid.containsKey(uid)) {
            return -1;
        }
        List<Map.Entry<String, Integer>> ranks = new ArrayList<>(scoreByUid.entrySet());
        ranks.sort((first, second) -> Integer.compare(second.getValue(), first.getValue()));
        for (int i = 0; i < ranks.size(); i++) {
            if (uid.equals(ranks.get(i).getKey())) {
                return i + 1;
            }
        }
        return -1;
    }

    private long getStartOfCurrentWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        resetToStartOfDay(calendar);
        return calendar.getTimeInMillis();
    }

    private int getDayBucketIndex(long startMillis, long targetMillis) {
        long diff = targetMillis - startMillis;
        return (int) (diff / (24L * 60L * 60L * 1000L));
    }

    private int getScoreForWindow(String uid, long start, long end) {
        int score = 0;
        for (GameRecord record : gameRecords) {
            if (record.getUid().equals(uid) && record.getDate() >= start && record.getDate() <= end) {
                score += record.getScore();
            }
        }
        return score;
    }

    private long getStartOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        resetToStartOfDay(calendar);
        return calendar.getTimeInMillis();
    }

    private void resetToStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
