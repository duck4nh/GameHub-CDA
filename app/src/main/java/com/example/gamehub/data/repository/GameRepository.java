package com.example.gamehub.data.repository;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.QuizAssetImporter;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.dao.MemoryDao;
import com.example.gamehub.data.local.dao.QuizDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.remote.FirebaseManager;
import com.example.gamehub.models.ChatMessage;
import com.example.gamehub.models.GameRecord;
import com.example.gamehub.models.LeaderboardEntry;
import com.example.gamehub.models.User;
import com.example.gamehub.utils.NetworkUtils;
import com.example.gamehub.workers.SyncWorker;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameRepository {
    public interface LeaderboardCallback {
        void onLoaded(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel);

        void onError(String message);
    }

    public interface LeaderboardSummaryCallback {
        void onLoaded(@Nullable LeaderboardEntry currentUserEntry, String trendLabel);

        void onError(String message);
    }

    public interface UserProfileCallback {
        void onLoaded(User user);

        void onError(String message);
    }

    public interface ChatMessagesListener {
        void onMessagesChanged(List<ChatMessage> messages);

        void onError(String message);
    }

    public interface ChatRoomCountsCallback {
        void onLoaded(Map<String, Integer> participantCounts);

        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();

        void onError(String message);
    }

    public interface HistorySyncCallback {
        void onCompleted(HistorySyncResult result);
    }

    private static final String DEFAULT_NICKNAME = "Player";
    private static final String HISTORY_SYNC_WORK_NAME = "history_sync";
    private static final String GENERAL_CHAT_ROOM_ID = "general";
    private static final long LEADERBOARD_CACHE_TTL_MS = 45_000L;
    private static final long ENSURE_USER_DOC_TTL_MS = 120_000L;
    private static final String TAG = "GameRepository";

    public static final class HistorySyncResult {
        public final boolean success;
        public final int syncedCount;
        public final int remainingCount;
        public final String message;

        public HistorySyncResult(boolean success, int syncedCount, int remainingCount, String message) {
            this.success = success;
            this.syncedCount = syncedCount;
            this.remainingCount = remainingCount;
            this.message = message == null ? "" : message;
        }
    }

    private static final class LeaderboardCacheEntry {
        final List<LeaderboardEntry> entries;
        @Nullable final LeaderboardEntry currentUserEntry;
        final String trendLabel;
        final long cachedAt;
        final String signature;

        LeaderboardCacheEntry(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel, long cachedAt, String signature) {
            this.entries = entries;
            this.currentUserEntry = currentUserEntry;
            this.trendLabel = trendLabel;
            this.cachedAt = cachedAt;
            this.signature = signature;
        }
    }

    private static final class PendingLeaderboardCallback {
        final LeaderboardCallback callback;
        @Nullable final String deliveredSignature;

        PendingLeaderboardCallback(LeaderboardCallback callback, @Nullable String deliveredSignature) {
            this.callback = callback;
            this.deliveredSignature = deliveredSignature;
        }
    }

    private static GameRepository instance;

    private final Context appContext;
    private final PreferenceManager preferenceManager;
    private final HistoryDao historyDao;
    private final QuizDao quizDao;
    private final MemoryDao memoryDao;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseManager firebaseManager;
    private final ExecutorService ioExecutor;
    private final Handler mainHandler;
    private final Object leaderboardCacheLock = new Object();
    private final List<PendingLeaderboardCallback> weeklyLeaderboardPendingCallbacks = new ArrayList<>();
    private final List<PendingLeaderboardCallback> allTimeLeaderboardPendingCallbacks = new ArrayList<>();

    private ListenerRegistration chatListenerRegistration;
    private boolean localDataReady;
    private volatile String lastHistorySyncStatusMessage = "";
    @Nullable private LeaderboardCacheEntry weeklyLeaderboardCache;
    @Nullable private LeaderboardCacheEntry allTimeLeaderboardCache;
    private boolean weeklyLeaderboardRefreshInFlight;
    private boolean allTimeLeaderboardRefreshInFlight;
    private long lastEnsureCurrentUserDocumentAt;

    private GameRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(appContext);
        preferenceManager = new PreferenceManager(appContext);
        historyDao = database.historyDao();
        quizDao = database.quizDao();
        memoryDao = database.memoryDao();
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        firebaseManager = new FirebaseManager();
        ioExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        ensurePreferences();
        removeLegacyMockHistory();
        syncSessionFromFirebase();
        triggerHistorySyncIfNeeded();
        localDataReady = quizDao.getCount() > 0 && memoryDao.getCount() > 0;
        mainHandler.post(() -> warmLeaderboardCache(isWeeklyLeaderboardSelected()));
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
        return saveHistory(historyItem, null);
    }

    public long saveHistory(LocalHistory historyItem, @Nullable HistorySyncCallback callback) {
        long insertedId = historyDao.insert(historyItem);
        syncPendingHistoryNow(callback);
        triggerHistorySyncIfNeeded();
        return insertedId;
    }

    public void fetchLeaderboard(boolean weekly, LeaderboardCallback callback) {
        syncSessionFromFirebase();
        ensureCurrentUserDocument();
        LeaderboardCacheEntry cachedEntry = getLeaderboardCache(weekly);
        String deliveredSignature = null;
        if (cachedEntry != null) {
            deliveredSignature = cachedEntry.signature;
            callback.onLoaded(copyLeaderboardEntries(cachedEntry.entries), cachedEntry.currentUserEntry, cachedEntry.trendLabel);
        }

        boolean needsRefresh = cachedEntry == null
                || isLeaderboardCacheStale(cachedEntry)
                || historyDao.getUnsyncedCount() > 0;

        if (!needsRefresh) {
            warmLeaderboardCache(!weekly);
            return;
        }

        queueLeaderboardRefresh(weekly, callback, deliveredSignature);
    }

    public void fetchWeeklyLeaderboardSummary(LeaderboardSummaryCallback callback) {
        fetchLeaderboard(true, new LeaderboardCallback() {
            @Override
            public void onLoaded(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel) {
                callback.onLoaded(currentUserEntry, trendLabel);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void startChatMessagesListener(String roomId, ChatMessagesListener listener) {
        syncSessionFromFirebase();
        stopChatMessagesListener();
        chatListenerRegistration = firestore.collection("Chat_Messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        listener.onError(error.getMessage() == null ? "Không thể tải chat cộng đồng." : error.getMessage());
                        return;
                    }
                    List<ChatMessage> messages = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot document : value.getDocuments()) {
                            ChatMessage message = toChatMessage(document);
                            if (belongsToRoom(message, roomId)) {
                                messages.add(message);
                            }
                        }
                    }
                    messages.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
                    listener.onMessagesChanged(messages);
                });
    }

    public void fetchChatRoomParticipantCounts(ChatRoomCountsCallback callback) {
        firestore.collection("Chat_Messages")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, Set<String>> participantsByRoom = new HashMap<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        ChatMessage message = toChatMessage(document);
                        String roomId = normalizeRoomId(message.getRoomId());
                        String participantKey = buildParticipantKey(message);
                        if (participantKey.isEmpty()) {
                            continue;
                        }
                        if (!participantsByRoom.containsKey(roomId)) {
                            participantsByRoom.put(roomId, new HashSet<>());
                        }
                        participantsByRoom.get(roomId).add(participantKey);
                    }

                    Map<String, Integer> counts = new HashMap<>();
                    for (Map.Entry<String, Set<String>> entry : participantsByRoom.entrySet()) {
                        counts.put(entry.getKey(), entry.getValue().size());
                    }
                    callback.onLoaded(counts);
                })
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Không thể tải số người tham gia." : error.getMessage()));
    }

    public void stopChatMessagesListener() {
        if (chatListenerRegistration != null) {
            chatListenerRegistration.remove();
            chatListenerRegistration = null;
        }
    }

    public void sendChatMessage(String roomId, String content, ActionCallback callback) {
        syncSessionFromFirebase();
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            callback.onError("Tin nhắn đang trống.");
            return;
        }

        String currentUid = getCurrentUid();
        if (currentUid.isEmpty()) {
            callback.onError("Chưa xác định được tài khoản hiện tại.");
            return;
        }

        String nickname = getCurrentNickname();
        if (nickname.trim().isEmpty()) {
            nickname = DEFAULT_NICKNAME;
        }

        String messageId = firestore.collection("Chat_Messages").document().getId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("message_id", messageId);
        payload.put("room_id", normalizeRoomId(roomId));
        payload.put("sender_uid", currentUid);
        payload.put("sender_nickname", nickname);
        payload.put("content", trimmed);
        payload.put("timestamp", System.currentTimeMillis());

        firestore.collection("Chat_Messages")
                .document(messageId)
                .set(payload)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Không gửi được tin nhắn." : error.getMessage()));
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

    public boolean hasLeaderboardCache(boolean weekly) {
        return getLeaderboardCache(weekly) != null;
    }

    public void fetchCurrentUserProfile(UserProfileCallback callback) {
        syncSessionFromFirebase();
        String currentUid = getCurrentUid();
        if (isBlank(currentUid)) {
            callback.onLoaded(buildFallbackCurrentUser(""));
            return;
        }

        firestore.collection("Users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(document -> {
                    User user = document.exists() ? toUser(document) : buildFallbackCurrentUser(currentUid);
                    cacheUserProfile(user);
                    callback.onLoaded(user);
                })
                .addOnFailureListener(error -> {
                    User fallbackUser = buildFallbackCurrentUser(currentUid);
                    cacheUserProfile(fallbackUser);
                    callback.onLoaded(fallbackUser);
                });
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
        return getLocalScoreForWindow(getStartOfCurrentWeek(), Long.MAX_VALUE);
    }

    public String getWeeklyScoreChangeLabel() {
        int current = getWeeklyScore();
        Calendar previousWeekStart = Calendar.getInstance();
        previousWeekStart.setTimeInMillis(getStartOfCurrentWeek());
        previousWeekStart.add(Calendar.DAY_OF_YEAR, -7);
        int previous = getLocalScoreForWindow(previousWeekStart.getTimeInMillis(), getStartOfCurrentWeek() - 1);
        if (previous <= 0) {
            return current > 0 ? "Tuần đầu tiên có điểm" : "Chưa có điểm tuần này";
        }
        int percent = Math.round((current - previous) * 100f / previous);
        if (percent > 0) {
            return String.format(Locale.getDefault(), "+%d%% so với tuần trước", percent);
        }
        if (percent < 0) {
            return String.format(Locale.getDefault(), "%d%% so với tuần trước", percent);
        }
        return "Bằng tuần trước";
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

    public long getBestSudokuTime() {
        LocalHistory best = getBestHistoryForGame("sudoku");
        return best == null ? 0L : best.timeSpent;
    }

    public long getBestQuizTime() {
        LocalHistory best = getBestHistoryForGame("quiz");
        return best == null ? 0L : best.timeSpent;
    }

    public long getBestMemoryTime() {
        LocalHistory best = getBestHistoryForGame("memory");
        return best == null ? 0L : best.timeSpent;
    }

    @Nullable
    public LocalHistory getBestHistoryForGame(String gameName) {
        String normalizedGameKey = normalizeGameKey(gameName);
        LocalHistory best = null;
        for (LocalHistory item : historyDao.getAllNewestFirst()) {
            if (!matchesGameKey(item.gameName, normalizedGameKey)) {
                continue;
            }
            if (!isSuccessfulStatus(item.status) || item.timeSpent <= 0L) {
                continue;
            }
            if (best == null
                    || item.timeSpent < best.timeSpent
                    || (item.timeSpent == best.timeSpent && item.playDate > best.playDate)) {
                best = item;
            }
        }
        return best;
    }

    @Nullable
    public LocalHistory getHistoryById(int historyId) {
        return historyDao.getById(historyId);
    }

    public int[] getWeeklyMatchCountByDay() {
        int[] values = new int[7];
        long startOfWeek = getStartOfCurrentWeek();
        List<LocalHistory> historyItems = historyDao.getAllNewestFirst();
        for (LocalHistory item : historyItems) {
            if (item.playDate < startOfWeek) {
                continue;
            }
            int index = getDayBucketIndex(startOfWeek, item.playDate);
            if (index >= 0 && index < values.length) {
                values[index]++;
            }
        }
        return values;
    }

    public int getUnsyncedCount() {
        return historyDao.getUnsyncedCount();
    }

    public long getLastSyncTime() {
        return preferenceManager.getLong(PreferenceManager.KEY_LAST_SYNC_TIME, 0L);
    }

    public String getLastHistorySyncStatusMessage() {
        return lastHistorySyncStatusMessage == null ? "" : lastHistorySyncStatusMessage;
    }

    public void syncPendingHistoryNow(@Nullable HistorySyncCallback callback) {
        ioExecutor.execute(() -> {
            HistorySyncResult result = syncPendingHistoryBlocking();
            if (callback != null) {
                mainHandler.post(() -> callback.onCompleted(result));
            }
        });
    }

    public List<LocalHistory> getHistory(@Nullable String filter) {
        triggerHistorySyncIfNeeded();
        List<LocalHistory> items = new ArrayList<>(historyDao.getAllNewestFirst());
        String normalizedFilter = normalizeGameKey(filter);
        if (filter == null || "all".equalsIgnoreCase(normalizedFilter)) {
            return items;
        }
        List<LocalHistory> filtered = new ArrayList<>();
        for (LocalHistory item : items) {
            if (matchesGameKey(item.gameName, normalizedFilter)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public String getCurrentUid() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getUid() != null) {
            String uid = currentUser.getUid();
            preferenceManager.putString(PreferenceManager.KEY_CURRENT_UID, uid);
            return uid;
        }
        return preferenceManager.getString(PreferenceManager.KEY_CURRENT_UID, "");
    }

    public String getCurrentNickname() {
        String nickname = preferenceManager.getString(PreferenceManager.KEY_CACHE_NICKNAME, "");
        if (!nickname.trim().isEmpty()) {
            return nickname;
        }
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()) {
            return currentUser.getDisplayName();
        }
        return DEFAULT_NICKNAME;
    }

    public String getCachedAvatarUrl() {
        return preferenceManager.getCacheAvatar();
    }

    private void fetchAllTimeLeaderboard(LeaderboardCallback callback) {
        firestore.collection("Users")
                .get()
                .addOnSuccessListener(userSnapshot -> {
                    Map<String, User> usersByUid = buildUsersByUid(userSnapshot.getDocuments());
                    firestore.collection("Game_Records")
                            .get()
                            .addOnSuccessListener(recordSnapshot -> {
                                Map<String, Integer> recordTotals = buildAllTimeRecordTotals(recordSnapshot.getDocuments());
                                deliverAllTimeLeaderboard(callback, usersByUid, recordTotals, "Tổng điểm tích lũy");
                            })
                            .addOnFailureListener(error -> {
                                if (!usersByUid.isEmpty()) {
                                    deliverAllTimeLeaderboard(callback, usersByUid, Collections.emptyMap(), "Tổng điểm tích lũy");
                                } else {
                                    deliverLocalLeaderboardFallback(callback, false, "Điểm trên thiết bị này");
                                }
                            });
                })
                .addOnFailureListener(error -> {
                    firestore.collection("Game_Records")
                            .get()
                            .addOnSuccessListener(recordSnapshot -> {
                                Map<String, Integer> recordTotals = buildAllTimeRecordTotals(recordSnapshot.getDocuments());
                                deliverAllTimeLeaderboard(callback, Collections.emptyMap(), recordTotals, "Tổng điểm tích lũy");
                            })
                            .addOnFailureListener(recordError -> deliverLocalLeaderboardFallback(callback, false, "Điểm trên thiết bị này"));
                });
    }

    private void fetchWeeklyLeaderboard(LeaderboardCallback callback) {
        long currentWeekStart = getStartOfCurrentWeek();
        long previousWeekStart = currentWeekStart - 7L * 24L * 60L * 60L * 1000L;

        firestore.collection("Users")
                .get()
                .addOnSuccessListener(userSnapshot -> {
                    loadWeeklyLeaderboard(callback, buildUsersByUid(userSnapshot.getDocuments()), currentWeekStart, previousWeekStart);
                })
                .addOnFailureListener(error -> loadWeeklyLeaderboard(callback, Collections.emptyMap(), currentWeekStart, previousWeekStart));
    }

    private List<LeaderboardEntry> buildWeeklyEntries(Map<String, Integer> scoresByUid, Map<String, User> usersByUid) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String currentUid = getCurrentUid();
        for (Map.Entry<String, Integer> entry : scoresByUid.entrySet()) {
            User user = usersByUid.get(entry.getKey());
            String nickname = user != null ? user.getNickname() : "";
            entries.add(new LeaderboardEntry(
                    entry.getKey(),
                    getDisplayName(nickname, entry.getKey()),
                    user != null ? user.getAvatarUrl() : "",
                    entry.getValue(),
                    0,
                    entry.getKey().equals(currentUid)
            ));
        }
        assignRanks(entries);
        return entries;
    }

    private String buildWeeklyTrendLabel(Map<String, Integer> currentWeekScores, Map<String, Integer> previousWeekScores) {
        String currentUid = getCurrentUid();
        int currentRank = getRankFromScoreMap(currentWeekScores, currentUid);
        if (currentRank <= 0) {
            return "Chưa có điểm tuần này";
        }
        int previousRank = getRankFromScoreMap(previousWeekScores, currentUid);
        if (previousRank <= 0) {
            return "Tuần đầu tiên có điểm";
        }
        int delta = previousRank - currentRank;
        if (delta > 0) {
            return String.format(Locale.getDefault(), "+%d bậc tuần này", delta);
        }
        if (delta < 0) {
            return String.format(Locale.getDefault(), "%d bậc tuần này", delta);
        }
        return "Giữ nguyên tuần này";
    }

    @Nullable
    private LeaderboardEntry findCurrentUserEntry(List<LeaderboardEntry> entries) {
        String currentUid = getCurrentUid();
        for (LeaderboardEntry entry : entries) {
            if (entry.getUid().equals(currentUid)) {
                return entry;
            }
        }
        return null;
    }

    private int getRankFromScoreMap(Map<String, Integer> scoreByUid, String uid) {
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

    private void ensurePreferences() {
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

    private void removeLegacyMockHistory() {
        historyDao.deleteByExactGameNames(Arrays.asList(
                "Đố vui · Thủ đô châu Á",
                "Ghi nhớ · Lưới trung bình",
                "Sudoku · Khó",
                "Đố vui · Chủ đề thể thao",
                "Ghi nhớ · 4x4"
        ));
    }

    private void syncSessionFromFirebase() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        preferenceManager.putString(PreferenceManager.KEY_CURRENT_UID, currentUser.getUid());

        String cachedNickname = preferenceManager.getString(PreferenceManager.KEY_CACHE_NICKNAME, "");
        String cachedAvatar = preferenceManager.getCacheAvatar();
        boolean hasCachedNickname = cachedNickname != null && !cachedNickname.trim().isEmpty();
        boolean hasCachedAvatar = cachedAvatar != null && !cachedAvatar.trim().isEmpty();
        if (hasCachedNickname && hasCachedAvatar) {
            return;
        }

        firestore.collection("Users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    String nickname = document.getString("nickname");
                    if (nickname != null && !nickname.trim().isEmpty()) {
                        preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, nickname);
                    } else if (!isBlank(currentUser.getDisplayName())) {
                        preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, currentUser.getDisplayName());
                    }

                    String avatarUrl = document.getString("avatar_url");
                    if (!isBlank(avatarUrl)) {
                        preferenceManager.putString(PreferenceManager.KEY_CACHE_AVATAR, avatarUrl);
                    }
                });
    }

    private void ensureCurrentUserDocument() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || currentUser.getUid() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastEnsureCurrentUserDocumentAt < ENSURE_USER_DOC_TTL_MS) {
            return;
        }
        lastEnsureCurrentUserDocumentAt = now;

        String uid = currentUser.getUid();
        firestore.collection("Users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("uid", uid);
                    payload.put("email", currentUser.getEmail() == null ? "" : currentUser.getEmail());
                    if (!isBlank(getCurrentNickname())) {
                        payload.put("nickname", getCurrentNickname());
                    }
                    if (!isBlank(preferenceManager.getCacheAvatar())) {
                        payload.put("avatar_url", preferenceManager.getCacheAvatar());
                    }
                    if (!document.exists() || document.get("total_score") == null) {
                        payload.put("total_score", 0);
                    }
                    if (!document.exists() || document.get("created_at") == null) {
                        payload.put("created_at", System.currentTimeMillis());
                    }
                    firestore.collection("Users")
                            .document(uid)
                            .set(payload, SetOptions.merge());
                });
    }

    private void triggerHistorySyncIfNeeded() {
        if (historyDao.getUnsyncedCount() <= 0) {
            return;
        }
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                HISTORY_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(SyncWorker.class).build()
        );
    }

    private void syncPendingHistoryThenFetchLeaderboard(boolean weekly, LeaderboardCallback callback) {
        if (historyDao.getUnsyncedCount() <= 0 || !NetworkUtils.isOnline(appContext)) {
            fetchLeaderboardFromCloud(weekly, callback);
            return;
        }

        ioExecutor.execute(() -> {
            syncPendingHistoryBlocking();
            mainHandler.post(() -> fetchLeaderboardFromCloud(weekly, callback));
        });
    }

    private HistorySyncResult syncPendingHistoryBlocking() {
        int pendingCount = historyDao.getUnsyncedCount();
        if (pendingCount <= 0) {
            lastHistorySyncStatusMessage = "Không còn bản ghi nào chờ đồng bộ.";
            return new HistorySyncResult(true, 0, 0, lastHistorySyncStatusMessage);
        }

        if (!NetworkUtils.isOnline(appContext)) {
            lastHistorySyncStatusMessage = "Không có mạng. Kết quả đã lưu trên máy và sẽ tự đồng bộ khi có mạng.";
            Log.w(TAG, "History sync skipped because device is offline. pending=" + pendingCount);
            triggerHistorySyncIfNeeded();
            return new HistorySyncResult(false, 0, pendingCount, lastHistorySyncStatusMessage);
        }

        syncSessionFromFirebase();
        String currentUid = getCurrentUid();
        if (!firebaseManager.canSyncHistoryResults(currentUid)) {
            lastHistorySyncStatusMessage = "Chưa có tài khoản đăng nhập để đồng bộ Game_Records.";
            Log.w(TAG, "History sync skipped because current uid is missing.");
            triggerHistorySyncIfNeeded();
            return new HistorySyncResult(false, 0, pendingCount, lastHistorySyncStatusMessage);
        }

        String cachedNickname = getCurrentNickname();
        List<LocalHistory> pendingItems = historyDao.getUnsyncedHistory();
        int syncedCount = 0;
        String firstError = "";
        for (LocalHistory history : pendingItems) {
            FirebaseManager.SyncHistoryResult syncResult = firebaseManager.syncHistoryRecordDetailed(history, currentUid, cachedNickname);
            if (syncResult.success) {
                historyDao.markSynced(history.id);
                syncedCount++;
            } else if (firstError.isEmpty()) {
                firstError = syncResult.message;
            }
        }

        int remainingCount = historyDao.getUnsyncedCount();
        if (syncedCount > 0) {
            preferenceManager.putLong(PreferenceManager.KEY_LAST_SYNC_TIME, System.currentTimeMillis());
        }

        String message;
        boolean success;
        if (remainingCount <= 0) {
            success = true;
            message = syncedCount > 0
                    ? String.format(Locale.getDefault(), "Đã đồng bộ %d trận lên Firebase.", syncedCount)
                    : "Không còn bản ghi nào chờ đồng bộ.";
        } else if (syncedCount > 0) {
            success = false;
            message = String.format(
                    Locale.getDefault(),
                    "Đã đồng bộ %d trận, còn %d trận chưa lên Firebase. %s",
                    syncedCount,
                    remainingCount,
                    firstError.isEmpty() ? "" : firstError
            ).trim();
        } else {
            success = false;
            message = firstError.isEmpty()
                    ? "Chưa đồng bộ được trận nào lên Firebase."
                    : "Lỗi đồng bộ Firebase: " + firstError;
        }

        lastHistorySyncStatusMessage = message;
        if (!success) {
            Log.w(TAG, "History sync incomplete. pending=" + pendingCount + ", synced=" + syncedCount + ", remaining=" + remainingCount + ", message=" + message);
            triggerHistorySyncIfNeeded();
        } else {
            Log.i(TAG, "History sync completed. synced=" + syncedCount);
        }
        return new HistorySyncResult(success, syncedCount, remainingCount, message);
    }

    private void fetchLeaderboardFromCloud(boolean weekly, LeaderboardCallback callback) {
        if (weekly) {
            fetchWeeklyLeaderboard(callback);
            return;
        }
        fetchAllTimeLeaderboard(callback);
    }

    private void queueLeaderboardRefresh(boolean weekly, LeaderboardCallback callback, @Nullable String deliveredSignature) {
        synchronized (leaderboardCacheLock) {
            getPendingLeaderboardCallbacks(weekly).add(new PendingLeaderboardCallback(callback, deliveredSignature));
            if (isLeaderboardRefreshInFlight(weekly)) {
                return;
            }
            setLeaderboardRefreshInFlight(weekly, true);
        }

        syncPendingHistoryThenFetchLeaderboard(weekly, new LeaderboardCallback() {
            @Override
            public void onLoaded(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel) {
                LeaderboardCacheEntry cacheEntry = new LeaderboardCacheEntry(
                        copyLeaderboardEntries(entries),
                        currentUserEntry,
                        trendLabel,
                        System.currentTimeMillis(),
                        buildLeaderboardSignature(entries, trendLabel)
                );
                LeaderboardCacheEntry previousEntry = getLeaderboardCache(weekly);
                setLeaderboardCache(weekly, cacheEntry);
                List<PendingLeaderboardCallback> callbacks = consumePendingLeaderboardCallbacks(weekly);
                setLeaderboardRefreshInFlight(weekly, false);

                for (PendingLeaderboardCallback pending : callbacks) {
                    boolean shouldNotify = pending.deliveredSignature == null
                            || previousEntry == null
                            || !cacheEntry.signature.equals(pending.deliveredSignature);
                    if (shouldNotify) {
                        pending.callback.onLoaded(copyLeaderboardEntries(cacheEntry.entries), cacheEntry.currentUserEntry, cacheEntry.trendLabel);
                    }
                }

                warmLeaderboardCache(!weekly);
            }

            @Override
            public void onError(String message) {
                List<PendingLeaderboardCallback> callbacks = consumePendingLeaderboardCallbacks(weekly);
                setLeaderboardRefreshInFlight(weekly, false);
                if (getLeaderboardCache(weekly) != null) {
                    return;
                }
                for (PendingLeaderboardCallback pending : callbacks) {
                    pending.callback.onError(message);
                }
            }
        });
    }

    private void warmLeaderboardCache(boolean weekly) {
        if (!NetworkUtils.isOnline(appContext)) {
            return;
        }
        LeaderboardCacheEntry cachedEntry = getLeaderboardCache(weekly);
        if (cachedEntry != null && !isLeaderboardCacheStale(cachedEntry) && historyDao.getUnsyncedCount() <= 0) {
            return;
        }

        synchronized (leaderboardCacheLock) {
            if (isLeaderboardRefreshInFlight(weekly)) {
                return;
            }
            setLeaderboardRefreshInFlight(weekly, true);
        }

        syncPendingHistoryThenFetchLeaderboard(weekly, new LeaderboardCallback() {
            @Override
            public void onLoaded(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel) {
                setLeaderboardCache(weekly, new LeaderboardCacheEntry(
                        copyLeaderboardEntries(entries),
                        currentUserEntry,
                        trendLabel,
                        System.currentTimeMillis(),
                        buildLeaderboardSignature(entries, trendLabel)
                ));
                setLeaderboardRefreshInFlight(weekly, false);
            }

            @Override
            public void onError(String message) {
                setLeaderboardRefreshInFlight(weekly, false);
            }
        });
    }

    private void loadWeeklyLeaderboard(LeaderboardCallback callback, Map<String, User> usersByUid, long currentWeekStart, long previousWeekStart) {
        firestore.collection("Game_Records")
                .get()
                .addOnSuccessListener(recordSnapshot -> {
                    Map<String, Integer> currentWeekScores = new HashMap<>();
                    Map<String, Integer> previousWeekScores = new HashMap<>();
                    for (DocumentSnapshot document : recordSnapshot.getDocuments()) {
                        GameRecord record = toGameRecord(document);
                        if (record.getUid().trim().isEmpty()) {
                            continue;
                        }
                        long playDate = record.getDate();
                        if (playDate >= currentWeekStart) {
                            currentWeekScores.put(record.getUid(), currentWeekScores.getOrDefault(record.getUid(), 0) + record.getScore());
                        } else if (playDate >= previousWeekStart) {
                            previousWeekScores.put(record.getUid(), previousWeekScores.getOrDefault(record.getUid(), 0) + record.getScore());
                        }
                    }

                    overlayCurrentUserLocalWeeklyScores(currentWeekScores, previousWeekScores, currentWeekStart, previousWeekStart);
                    List<LeaderboardEntry> entries = buildWeeklyEntries(currentWeekScores, usersByUid);
                    if (entries.isEmpty()) {
                        entries = buildLocalFallbackLeaderboard(true);
                    }
                    callback.onLoaded(entries, findCurrentUserEntry(entries), buildWeeklyTrendLabel(currentWeekScores, previousWeekScores));
                })
                .addOnFailureListener(error -> {
                    Map<String, Integer> currentWeekScores = new HashMap<>();
                    Map<String, Integer> previousWeekScores = new HashMap<>();
                    overlayCurrentUserLocalWeeklyScores(currentWeekScores, previousWeekScores, currentWeekStart, previousWeekStart);
                    List<LeaderboardEntry> entries = buildWeeklyEntries(currentWeekScores, usersByUid);
                    if (entries.isEmpty()) {
                        entries = buildLocalFallbackLeaderboard(true);
                    }
                    callback.onLoaded(entries, findCurrentUserEntry(entries), entries.isEmpty() ? "Chưa có điểm tuần này" : "Điểm trên thiết bị này");
                });
    }

    private void deliverAllTimeLeaderboard(LeaderboardCallback callback, Map<String, User> usersByUid, Map<String, Integer> recordTotals, String trendLabel) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        Set<String> knownUserIds = new HashSet<>();
        String currentUid = getCurrentUid();
        int localAllTimeScore = getLocalScoreForWindow(Long.MIN_VALUE, Long.MAX_VALUE);

        for (User user : usersByUid.values()) {
            if (isBlank(user.getUid())) {
                continue;
            }
            int repairedScore = reconcileAllTimeScore(user.getTotalScore(), recordTotals.get(user.getUid()));
            if (!recordTotals.isEmpty() && repairedScore != user.getTotalScore()) {
                repairUserTotalScore(user.getUid(), repairedScore);
            }
            int displayScore = user.getUid().equals(currentUid)
                    ? Math.max(repairedScore, localAllTimeScore)
                    : repairedScore;
            entries.add(new LeaderboardEntry(
                    user.getUid(),
                    getDisplayName(user.getNickname(), user.getUid()),
                    user.getAvatarUrl(),
                    displayScore,
                    0,
                    user.getUid().equals(currentUid)
            ));
            knownUserIds.add(user.getUid());
        }

        for (Map.Entry<String, Integer> entry : recordTotals.entrySet()) {
            if (knownUserIds.contains(entry.getKey())) {
                continue;
            }
            repairUserTotalScore(entry.getKey(), entry.getValue());
            entries.add(new LeaderboardEntry(
                    entry.getKey(),
                    getDisplayName("", entry.getKey()),
                    "",
                    entry.getValue(),
                    0,
                    entry.getKey().equals(currentUid)
            ));
        }

        maybeAppendCurrentUserFallback(entries, currentUid, localAllTimeScore);
        if (entries.isEmpty()) {
            entries = buildLocalFallbackLeaderboard(false);
        } else {
            assignRanks(entries);
        }
        callback.onLoaded(entries, findCurrentUserEntry(entries), trendLabel);
    }

    private void deliverLocalLeaderboardFallback(LeaderboardCallback callback, boolean weekly, String trendLabel) {
        List<LeaderboardEntry> entries = buildLocalFallbackLeaderboard(weekly);
        callback.onLoaded(entries, findCurrentUserEntry(entries), trendLabel);
    }

    private int getLocalScoreForWindow(long start, long end) {
        int score = 0;
        for (LocalHistory history : historyDao.getAllNewestFirst()) {
            if (history.playDate >= start && history.playDate <= end) {
                score += history.score;
            }
        }
        return score;
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

    private User toUser(DocumentSnapshot document) {
        String uid = readString(document, "uid");
        if (uid.isEmpty()) {
            uid = document.getId();
        }
        String nickname = readString(document, "nickname");
        if (nickname.trim().isEmpty() && uid.equals(getCurrentUid())) {
            nickname = getCurrentNickname();
        }
        return new User(
                uid,
                readString(document, "email"),
                nickname,
                readString(document, "avatar_url"),
                readInt(document, "total_score"),
                readLong(document, "created_at")
        );
    }

    private GameRecord toGameRecord(DocumentSnapshot document) {
        String recordId = readString(document, "record_id");
        if (recordId.isEmpty()) {
            recordId = document.getId();
        }
        return new GameRecord(
                recordId,
                readString(document, "uid"),
                readString(document, "game_type"),
                readInt(document, "score"),
                readLong(document, "time_played"),
                readString(document, "status"),
                readLong(document, "date")
        );
    }

    private ChatMessage toChatMessage(DocumentSnapshot document) {
        String messageId = readString(document, "message_id");
        if (messageId.isEmpty()) {
            messageId = document.getId();
        }
        return new ChatMessage(
                messageId,
                readString(document, "room_id"),
                readString(document, "sender_uid"),
                readString(document, "sender_nickname", "Người chơi"),
                readString(document, "content"),
                readLong(document, "timestamp")
        );
    }

    private boolean belongsToRoom(ChatMessage message, String roomId) {
        return normalizeRoomId(message.getRoomId()).equals(normalizeRoomId(roomId));
    }

    private String normalizeRoomId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            return GENERAL_CHAT_ROOM_ID;
        }
        return roomId.trim();
    }

    private String buildParticipantKey(ChatMessage message) {
        if (!message.getSenderUid().isEmpty()) {
            return message.getSenderUid();
        }
        return message.getSenderNickname().trim().toLowerCase(Locale.getDefault());
    }

    private String readString(DocumentSnapshot document, String field) {
        return readString(document, field, "");
    }

    private String readString(DocumentSnapshot document, String field, String fallback) {
        String value = document.getString(field);
        return value == null ? fallback : value;
    }

    private int readInt(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return (int) Math.round(Double.parseDouble((String) value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private long readLong(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private Map<String, User> buildUsersByUid(List<DocumentSnapshot> documents) {
        Map<String, User> usersByUid = new HashMap<>();
        for (DocumentSnapshot document : documents) {
            User user = toUser(document);
            if (!isBlank(user.getUid())) {
                usersByUid.put(user.getUid(), user);
            }
        }
        return usersByUid;
    }

    private Map<String, Integer> buildAllTimeRecordTotals(List<DocumentSnapshot> documents) {
        Map<String, Integer> recordTotals = new HashMap<>();
        for (DocumentSnapshot document : documents) {
            GameRecord record = toGameRecord(document);
            if (isBlank(record.getUid())) {
                continue;
            }
            recordTotals.put(
                    record.getUid(),
                    recordTotals.getOrDefault(record.getUid(), 0) + Math.max(0, record.getScore())
            );
        }
        return recordTotals;
    }

    private void overlayCurrentUserLocalWeeklyScores(Map<String, Integer> currentWeekScores, Map<String, Integer> previousWeekScores, long currentWeekStart, long previousWeekStart) {
        String currentUid = getCurrentUid();
        if (isBlank(currentUid)) {
            return;
        }
        int localCurrentWeekScore = getLocalScoreForWindow(currentWeekStart, Long.MAX_VALUE);
        int localPreviousWeekScore = getLocalScoreForWindow(previousWeekStart, currentWeekStart - 1L);
        currentWeekScores.put(
                currentUid,
                Math.max(currentWeekScores.getOrDefault(currentUid, 0), localCurrentWeekScore)
        );
        previousWeekScores.put(
                currentUid,
                Math.max(previousWeekScores.getOrDefault(currentUid, 0), localPreviousWeekScore)
        );
    }

    private void maybeAppendCurrentUserFallback(List<LeaderboardEntry> entries, String currentUid, int fallbackScore) {
        if (currentUid == null || currentUid.trim().isEmpty()) {
            return;
        }
        for (LeaderboardEntry entry : entries) {
            if (currentUid.equals(entry.getUid())) {
                return;
            }
        }
        entries.add(new LeaderboardEntry(
                currentUid,
                getDisplayName(getCurrentNickname(), currentUid),
                preferenceManager.getCacheAvatar(),
                Math.max(0, fallbackScore),
                0,
                true
        ));
        assignRanks(entries);
    }

    private List<LeaderboardEntry> buildLocalFallbackLeaderboard(boolean weekly) {
        String currentUid = getCurrentUid();
        if (isBlank(currentUid)) {
            return new ArrayList<>();
        }
        int fallbackScore = weekly
                ? getLocalScoreForWindow(getStartOfCurrentWeek(), Long.MAX_VALUE)
                : getLocalScoreForWindow(Long.MIN_VALUE, Long.MAX_VALUE);
        if (fallbackScore <= 0 && historyDao.getCount() <= 0) {
            return new ArrayList<>();
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        entries.add(new LeaderboardEntry(
                currentUid,
                getDisplayName(getCurrentNickname(), currentUid),
                preferenceManager.getCacheAvatar(),
                Math.max(0, fallbackScore),
                1,
                true
        ));
        return entries;
    }

    @Nullable
    private LeaderboardCacheEntry getLeaderboardCache(boolean weekly) {
        synchronized (leaderboardCacheLock) {
            return weekly ? weeklyLeaderboardCache : allTimeLeaderboardCache;
        }
    }

    private void setLeaderboardCache(boolean weekly, LeaderboardCacheEntry cacheEntry) {
        synchronized (leaderboardCacheLock) {
            if (weekly) {
                weeklyLeaderboardCache = cacheEntry;
            } else {
                allTimeLeaderboardCache = cacheEntry;
            }
        }
    }

    private boolean isLeaderboardCacheStale(LeaderboardCacheEntry cacheEntry) {
        return cacheEntry == null || System.currentTimeMillis() - cacheEntry.cachedAt > LEADERBOARD_CACHE_TTL_MS;
    }

    private List<PendingLeaderboardCallback> getPendingLeaderboardCallbacks(boolean weekly) {
        return weekly ? weeklyLeaderboardPendingCallbacks : allTimeLeaderboardPendingCallbacks;
    }

    private List<PendingLeaderboardCallback> consumePendingLeaderboardCallbacks(boolean weekly) {
        synchronized (leaderboardCacheLock) {
            List<PendingLeaderboardCallback> source = weekly ? weeklyLeaderboardPendingCallbacks : allTimeLeaderboardPendingCallbacks;
            List<PendingLeaderboardCallback> copy = new ArrayList<>(source);
            source.clear();
            return copy;
        }
    }

    private boolean isLeaderboardRefreshInFlight(boolean weekly) {
        synchronized (leaderboardCacheLock) {
            return weekly ? weeklyLeaderboardRefreshInFlight : allTimeLeaderboardRefreshInFlight;
        }
    }

    private void setLeaderboardRefreshInFlight(boolean weekly, boolean inFlight) {
        synchronized (leaderboardCacheLock) {
            if (weekly) {
                weeklyLeaderboardRefreshInFlight = inFlight;
            } else {
                allTimeLeaderboardRefreshInFlight = inFlight;
            }
        }
    }

    private List<LeaderboardEntry> copyLeaderboardEntries(List<LeaderboardEntry> entries) {
        return new ArrayList<>(entries == null ? Collections.emptyList() : entries);
    }

    private String buildLeaderboardSignature(List<LeaderboardEntry> entries, String trendLabel) {
        StringBuilder builder = new StringBuilder(trendLabel == null ? "" : trendLabel);
        if (entries != null) {
            for (LeaderboardEntry entry : entries) {
                builder.append('|')
                        .append(entry.getUid())
                        .append(':')
                        .append(entry.getScore())
                        .append(':')
                        .append(entry.getRank());
            }
        }
        return builder.toString();
    }

    private int reconcileAllTimeScore(int storedScore, @Nullable Integer recordScore) {
        int safeStoredScore = Math.max(0, storedScore);
        if (recordScore == null) {
            return safeStoredScore;
        }
        return Math.max(safeStoredScore, Math.max(0, recordScore));
    }

    private void repairUserTotalScore(String uid, int totalScore) {
        if (isBlank(uid) || totalScore < 0) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);
        payload.put("total_score", totalScore);
        if (uid.equals(getCurrentUid())) {
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null && !isBlank(currentUser.getEmail())) {
                payload.put("email", currentUser.getEmail());
            }
            if (!isBlank(getCurrentNickname())) {
                payload.put("nickname", getCurrentNickname());
            }
            if (!isBlank(preferenceManager.getCacheAvatar())) {
                payload.put("avatar_url", preferenceManager.getCacheAvatar());
            }
        }
        firestore.collection("Users")
                .document(uid)
                .set(payload, SetOptions.merge());
    }

    private void assignRanks(List<LeaderboardEntry> entries) {
        entries.sort((first, second) -> {
            int byScore = Integer.compare(second.getScore(), first.getScore());
            if (byScore != 0) {
                return byScore;
            }
            int byName = first.getNickname().compareToIgnoreCase(second.getNickname());
            if (byName != 0) {
                return byName;
            }
            return first.getUid().compareToIgnoreCase(second.getUid());
        });

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry original = entries.get(i);
            entries.set(i, new LeaderboardEntry(
                    original.getUid(),
                    original.getNickname(),
                    original.getAvatarUrl(),
                    original.getScore(),
                    i + 1,
                    original.isCurrentUser()
            ));
        }
    }

    private String getDisplayName(@Nullable String nickname, String fallbackUid) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        String cachedNickname = getCurrentNickname();
        if (!cachedNickname.trim().isEmpty() && fallbackUid.equals(getCurrentUid())) {
            return cachedNickname.trim();
        }
        return fallbackUid == null || fallbackUid.trim().isEmpty() ? DEFAULT_NICKNAME : fallbackUid.trim();
    }

    private User buildFallbackCurrentUser(String currentUid) {
        FirebaseUser currentUser = auth.getCurrentUser();
        String resolvedUid = isBlank(currentUid) && currentUser != null ? currentUser.getUid() : currentUid;
        String email = currentUser != null && currentUser.getEmail() != null ? currentUser.getEmail() : "";
        return new User(
                resolvedUid == null ? "" : resolvedUid,
                email,
                getCurrentNickname(),
                preferenceManager.getCacheAvatar(),
                0,
                0L
        );
    }

    private void cacheUserProfile(User user) {
        if (user == null) {
            return;
        }
        if (!isBlank(user.getNickname())) {
            preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, user.getNickname().trim());
        }
        if (!isBlank(user.getAvatarUrl())) {
            preferenceManager.putString(PreferenceManager.KEY_CACHE_AVATAR, user.getAvatarUrl().trim());
        }
    }

    private boolean matchesGameKey(String gameName, String expectedKey) {
        return normalizeGameKey(gameName).equals(normalizeGameKey(expectedKey));
    }

    private String normalizeGameKey(@Nullable String gameName) {
        if (gameName == null || gameName.trim().isEmpty()) {
            return "";
        }
        String normalized = gameName.trim().toLowerCase(Locale.getDefault());
        if (normalized.contains("quiz") || normalized.contains("đố vui")) {
            return "quiz";
        }
        if (normalized.contains("memory") || normalized.contains("ghi nhớ")) {
            return "memory";
        }
        if (normalized.contains("sudoku")) {
            return "sudoku";
        }
        if ("all".equals(normalized)) {
            return "all";
        }
        return normalized;
    }

    private boolean isSuccessfulStatus(String status) {
        return "won".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status);
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String formatDuration(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
