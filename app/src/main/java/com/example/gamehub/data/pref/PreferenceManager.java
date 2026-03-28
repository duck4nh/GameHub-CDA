package com.example.gamehub.data.pref;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private final SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "GameHub_Pref";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_CURRENT_UID = "current_uid";
    private static final String KEY_CACHE_NICKNAME = "cache_nickname";

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLoginSession(String uid, String nickname) {
        sharedPreferences.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_CURRENT_UID, uid)
                .putString(KEY_CACHE_NICKNAME, nickname)
                .apply();
    }

    // ĐÂY LÀ HÀM BẠN ĐANG THIẾU
    public void clear() {
        sharedPreferences.edit().clear().apply();
    }

    public boolean isLoggedIn() { return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false); }
    public String getCurrentUid() { return sharedPreferences.getString(KEY_CURRENT_UID, ""); }
    public String getCacheNickname() { return sharedPreferences.getString(KEY_CACHE_NICKNAME, "Player"); }
}