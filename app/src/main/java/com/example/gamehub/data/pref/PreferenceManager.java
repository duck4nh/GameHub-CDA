package com.example.gamehub.data.pref;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_CURRENT_UID = "current_uid";
    public static final String KEY_CACHE_NICKNAME = "cache_nickname";
    public static final String KEY_IS_DARK_MODE = "is_dark_mode";
    public static final String KEY_LEADERBOARD_FILTER = "leaderboard_filter";
    public static final String KEY_LAST_SYNC_TIME = "LAST_SYNC_TIME";
    public static final String KEY_IS_SOUND_ON = "IS_SOUND_ON";
    public static final String KEY_IS_ANIMATION_ON = "IS_ANIMATION_ON";
    public static final String KEY_LAST_SUDOKU_LEVEL = "LAST_SUDOKU_LEVEL";
    public static final String KEY_IS_HINT_ENABLED = "IS_HINT_ENABLED";

    private static final String PREFS_NAME = "gamehub_prefs";

    private final SharedPreferences sharedPreferences;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    public void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public long getLong(String key, long defaultValue) {
        return sharedPreferences.getLong(key, defaultValue);
    }

    public void putLong(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
    }

    public void saveLoginSession(String uid, String nickname) {
        sharedPreferences.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_CURRENT_UID, uid)
                .putString(KEY_CACHE_NICKNAME, nickname)
                .apply();
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getCurrentUid() {
        return sharedPreferences.getString(KEY_CURRENT_UID, "");
    }

    public String getCacheNickname() {
        return sharedPreferences.getString(KEY_CACHE_NICKNAME, "Player");
    }
}
