package com.example.gamehub.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.activities.LoginActivity;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.repository.AuthRepository;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProfileFragment extends Fragment {

    private PreferenceManager preferenceManager;
    private AuthRepository authRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferenceManager = new PreferenceManager(requireContext());
        authRepository = new AuthRepository(AppDatabase.getInstance(requireContext()), preferenceManager);

        setupUserInfo(view);
        setupClickListeners(view);
    }

    private void setupUserInfo(View view) {
        TextView tvUsername = view.findViewById(R.id.tvUsername);
        tvUsername.setText(preferenceManager.getCacheNickname());

        // Setup switch sound state
        SwitchMaterial swSound = view.findViewById(R.id.swSound);
        swSound.setChecked(preferenceManager.getBoolean(PreferenceManager.KEY_IS_SOUND_ON, true));
        swSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.putBoolean(PreferenceManager.KEY_IS_SOUND_ON, isChecked);
            Toast.makeText(getContext(), isChecked ? "Đã bật âm thanh" : "Đã tắt âm thanh", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupClickListeners(View view) {
        MainActivity activity = (MainActivity) getActivity();

        view.findViewById(R.id.llLeaderboard).setOnClickListener(v -> {
            if (activity != null) activity.showLeaderboard();
        });

        view.findViewById(R.id.llHistory).setOnClickListener(v -> {
            if (activity != null) activity.showHistory();
        });

        view.findViewById(R.id.llFriends).setOnClickListener(v -> {
            // Navigate to friends/chat
            if (activity != null) activity.showStatistics(); // Or appropriate method
        });

        view.findViewById(R.id.llLogout).setOnClickListener(v -> {
            authRepository.logout();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        view.findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Chức năng đang phát triển", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.llResetPassword).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Chức năng đang phát triển", Toast.LENGTH_SHORT).show();
        });
    }
}
