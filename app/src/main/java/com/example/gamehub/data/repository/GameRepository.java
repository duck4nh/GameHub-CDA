package com.example.gamehub.data.repository;

import android.content.Context;

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
import com.example.gamehub.models.ChatMessage;
import com.example.gamehub.models.GameRecord;
import com.example.gamehub.models.LeaderboardEntry;
import com.example.gamehub.models.User;
import com.example.gamehub.workers.SyncWorker;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

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

public class GameRepository {
    public interface LeaderboardCallback {
        void onLoaded(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel);

        void onError(String message);
    }

    public interface LeaderboardSummaryCallback {
        void onLoaded(@Nullable LeaderboardEntry currentUserEntry, String trendLabel);

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

    private static final String DEFAULT_NICKNAME = "Player";
    private static final String HISTORY_SYNC_WORK_NAME = "history_sync";
    private static final String GENERAL_CHAT_ROOM_ID = "general";

    private static GameRepository instance;

    private final Context appContext;
    private final PreferenceManager preferenceManager;
    private final HistoryDao historyDao;
    private final QuizDao quizDao;
    private final MemoryDao memoryDao;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    private ListenerRegistration chatListenerRegistration;
    private boolean localDataReady;

    private GameRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(appContext);
        preferenceManager = new PreferenceManager(appContext);
        historyDao = database.historyDao();
        quizDao = database.quizDao();
        memoryDao = database.memoryDao();
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        ensurePreferences();
        removeLegacyMockHistory();
        syncSessionFromFirebase();
        triggerHistorySyncIfNeeded();
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
        long insertedId = historyDao.insert(historyItem);
        triggerHistorySyncIfNeeded();
        return insertedId;
    }

    public void fetchLeaderboard(boolean weekly, LeaderboardCallback callback) {
        syncSessionFromFirebase();
        triggerHistorySyncIfNeeded();
        if (weekly) {
            fetchWeeklyLeaderboard(callback);
            return;
        }
        fetchAllTimeLeaderboard(callback);
    }

    public void fetchWeeklyLeaderboardSummary(LeaderboardSummaryCallback callback) {
        fetchWeeklyLeaderboard(new LeaderboardCallback() {
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

    private void fetchAllTimeLeaderboard(LeaderboardCallback callback) {
        firestore.collection("Users")
                .orderBy("total_score", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<LeaderboardEntry> entries = new ArrayList<>();
                    String currentUid = getCurrentUid();
                    int rank = 1;
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        User user = toUser(document);
                        entries.add(new LeaderboardEntry(
                                user.getUid(),
                                user.getNickname(),
                                user.getTotalScore(),
                                rank++,
                                user.getUid().equals(currentUid)
                        ));
                    }
                    callback.onLoaded(entries, findCurrentUserEntry(entries), "Tổng điểm tích lũy");
                })
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Không thể tải bảng xếp hạng." : error.getMessage()));
    }

    private void fetchWeeklyLeaderboard(LeaderboardCallback callback) {
        long currentWeekStart = getStartOfCurrentWeek();
        long previousWeekStart = currentWeekStart - 7L * 24L * 60L * 60L * 1000L;

        firestore.collection("Users")
                .get()
                .addOnSuccessListener(userSnapshot -> {
                    Map<String, User> usersByUid = new HashMap<>();
                    for (DocumentSnapshot document : userSnapshot.getDocuments()) {
                        User user = toUser(document);
                        usersByUid.put(user.getUid(), user);
                    }

                    firestore.collection("Game_Records")
                            .whereGreaterThanOrEqualTo("date", previousWeekStart)
                            .get()
                            .addOnSuccessListener(recordSnapshot -> {
                                Map<String, Integer> currentWeekScores = new HashMap<>();
                                Map<String, Integer> previousWeekScores = new HashMap<>();
                                for (DocumentSnapshot document : recordSnapshot.getDocuments()) {
                                    GameRecord record = toGameRecord(document);
                                    long playDate = record.getDate();
                                    if (playDate >= currentWeekStart) {
                                        currentWeekScores.put(record.getUid(), currentWeekScores.getOrDefault(record.getUid(), 0) + record.getScore());
                                    } else if (playDate >= previousWeekStart) {
                                        previousWeekScores.put(record.getUid(), previousWeekScores.getOrDefault(record.getUid(), 0) + record.getScore());
                                    }
                                }

                                List<LeaderboardEntry> entries = buildWeeklyEntries(currentWeekScores, usersByUid);
                                LeaderboardEntry currentUserEntry = findCurrentUserEntry(entries);
                                callback.onLoaded(entries, currentUserEntry, buildWeeklyTrendLabel(currentWeekScores, previousWeekScores));
                            })
                            .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Không thể tải bảng xếp hạng tuần." : error.getMessage()));
                })
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Không thể tải dữ liệu người dùng." : error.getMessage()));
    }

    private List<LeaderboardEntry> buildWeeklyEntries(Map<String, Integer> scoresByUid, Map<String, User> usersByUid) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String currentUid = getCurrentUid();
        for (Map.Entry<String, Integer> entry : scoresByUid.entrySet()) {
            User user = usersByUid.get(entry.getKey());
            String nickname = user != null && user.getNickname() != null && !user.getNickname().trim().isEmpty()
                    ? user.getNickname()
                    : entry.getKey();
            entries.add(new LeaderboardEntry(
                    entry.getKey(),
                    nickname,
                    entry.getValue(),
                    0,
                    entry.getKey().equals(currentUid)
            ));
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
        if (cachedNickname != null && !cachedNickname.trim().isEmpty()) {
            return;
        }

        firestore.collection("Users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    String nickname = document.getString("nickname");
                    if (nickname != null && !nickname.trim().isEmpty()) {
                        preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, nickname);
                    }
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
        return new User(
                uid,
                readString(document, "email"),
                readString(document, "nickname", uid),
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
        Long value = document.getLong(field);
        return value == null ? 0 : value.intValue();
    }

    private long readLong(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        return 0L;
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

    public static String formatDuration(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
