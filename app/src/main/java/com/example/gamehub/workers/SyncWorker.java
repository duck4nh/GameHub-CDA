package com.example.gamehub.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.dao.HistoryDao;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.remote.FirebaseManager;
import com.example.gamehub.utils.NetworkUtils;

import java.util.List;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!NetworkUtils.isOnline(getApplicationContext())) {
            return Result.retry();
        }

        FirebaseManager firebaseManager = new FirebaseManager();
        if (!firebaseManager.canSyncSudokuResults()) {
            return Result.success();
        }

        HistoryDao historyDao = AppDatabase.getInstance(getApplicationContext()).historyDao();
        List<LocalHistory> pendingItems = historyDao.getUnsyncedHistoryForGame("sudoku");
        for (LocalHistory history : pendingItems) {
            if (firebaseManager.syncSudokuResult(history)) {
                historyDao.markSynced(history.id);
            }
        }

        new PreferenceManager(getApplicationContext()).putLong(PreferenceManager.KEY_LAST_SYNC_TIME, System.currentTimeMillis());
        return Result.success();
    }
}
