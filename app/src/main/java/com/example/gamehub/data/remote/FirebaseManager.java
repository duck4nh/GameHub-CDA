package com.example.gamehub.data.remote;

import com.example.gamehub.data.local.entities.LocalHistory;

public class FirebaseManager {
    public boolean canSyncSudokuResults() {
        return false;
    }

    public boolean syncSudokuResult(LocalHistory history) {
        return false;
    }
}
