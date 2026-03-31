package com.example.gamehub.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.remote.FirebaseManager;
import com.example.gamehub.utils.NetworkUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!NetworkUtils.isOnline(getApplicationContext())) {
            return Result.retry();
        }

        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = currentUser != null && currentUser.getUid() != null
                ? currentUser.getUid()
                : preferenceManager.getCurrentUid();
        if (currentUser != null && currentUser.getUid() != null) {
            preferenceManager.putString(PreferenceManager.KEY_CURRENT_UID, currentUser.getUid());
        }
        String cachedNickname = preferenceManager.getCacheNickname();
        FirebaseManager firebaseManager = new FirebaseManager();
        if (!firebaseManager.canSyncHistoryResults(currentUid)) {
            return Result.success();
        }

        HistoryDao historyDao = AppDatabase.getInstance(getApplicationContext()).historyDao();
        List<LocalHistory> pendingItems = historyDao.getUnsyncedHistory();
        int syncedCount = 0;
        boolean hasFailure = false;
        for (LocalHistory history : pendingItems) {
            FirebaseManager.SyncHistoryResult syncResult = firebaseManager.syncHistoryRecordDetailed(history, currentUid, cachedNickname);
            if (syncResult.success) {
                historyDao.markSynced(history.id);
                syncedCount++;
            } else {
                hasFailure = true;
                Log.w(TAG, "Background history sync failed for id=" + history.id + ": " + syncResult.message);
            }
        }

        if (syncedCount > 0) {
            preferenceManager.putLong(PreferenceManager.KEY_LAST_SYNC_TIME, System.currentTimeMillis());
        }
        return hasFailure ? Result.retry() : Result.success();
    }
}
