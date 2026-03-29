package com.example.gamehub.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;

import com.example.gamehub.data.pref.PreferenceManager;

public class SoundManager {
    private static SoundManager instance;
    private final PreferenceManager preferenceManager;
    private final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 75);
    private MediaPlayer mediaPlayer;
    private final Context context;

    public SoundManager(Context context) {
        this.context = context.getApplicationContext();
        preferenceManager = new PreferenceManager(this.context);
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    public void playCorrect() {
        playTone(ToneGenerator.TONE_PROP_ACK, 120);
    }

    public void playWrong() {
        playTone(ToneGenerator.TONE_PROP_NACK, 160);
    }

    public void playCardFlip() {
        playTone(ToneGenerator.TONE_PROP_BEEP2, 70);
    }

    public void playMatch() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 90);
    }

    public void playWin() {
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 240);
    }

    public void playLose() {
        playTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 220);
    }

    public void startBGM(int resId) {
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_IS_SOUND_ON, true)) {
            return;
        }

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                return;
            }
            mediaPlayer.release();
        }

        mediaPlayer = MediaPlayer.create(context, resId);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
    }

    public void stopBGM() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void release() {
        toneGenerator.release();
        stopBGM();
    }

    private void playTone(int tone, int durationMs) {
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_IS_SOUND_ON, true)) {
            return;
        }
        toneGenerator.startTone(tone, durationMs);
    }
}