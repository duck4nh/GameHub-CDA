package com.example.gamehub.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.activities.ChangePasswordActivity;
import com.example.gamehub.activities.LoginActivity;
import com.example.gamehub.activities.ProfileActivity;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.repository.AuthRepository;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class ProfileFragment extends Fragment {

    private PreferenceManager preferenceManager;
    private AuthRepository authRepository;
    private FirebaseFirestore firestore;
    private String currentUid;
    private ImageView ivProfilePic;
    private TextView tvRank;

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
        firestore = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        initViews(view);
        setupUserInfo();
        setupClickListeners(view);
        fetchUserInfo();
        fetchRank();
    }

    private void initViews(View view) {
        ivProfilePic = view.findViewById(R.id.ivProfilePic);
        tvRank = view.findViewById(R.id.tvRank);
    }

    private void setupUserInfo() {
        TextView tvUsername = getView() != null ? getView().findViewById(R.id.tvUsername) : null;
        if (tvUsername != null) {
            tvUsername.setText(preferenceManager.getCacheNickname());
        }

        String avatarUrl = preferenceManager.getCacheAvatar();
        loadAvatar(avatarUrl);

        if (tvRank != null) {
            tvRank.setText("Đang tải...");
        }

        // Setup switch sound state
        SwitchMaterial swSound = getView() != null ? getView().findViewById(R.id.swSound) : null;
        if (swSound != null) {
            swSound.setChecked(preferenceManager.getBoolean(PreferenceManager.KEY_IS_SOUND_ON, true));
            swSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
                preferenceManager.putBoolean(PreferenceManager.KEY_IS_SOUND_ON, isChecked);
                Toast.makeText(getContext(), isChecked ? "Đã bật âm thanh" : "Đã tắt âm thanh", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void fetchUserInfo() {
        if (currentUid == null) return;
        firestore.collection("Users").document(currentUid).get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || getContext() == null) return;

                    if (document.exists()) {
                        String nickname = document.getString("nickname");
                        String avatarUrl = document.getString("avatar_url");

                        if (nickname != null) {
                            preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, nickname);
                            TextView tvUsername = getView() != null ? getView().findViewById(R.id.tvUsername) : null;
                            if (tvUsername != null) tvUsername.setText(nickname);
                        }

                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            preferenceManager.putString(PreferenceManager.KEY_CACHE_AVATAR, avatarUrl);
                            loadAvatar(avatarUrl);
                        }
                    }
                });
    }

    private void fetchRank() {
        if (currentUid == null || tvRank == null) {
            if (tvRank != null) tvRank.setText("No Rank");
            return;
        }

        firestore.collection("Users")
                .orderBy("total_score", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        tvRank.setText("No Rank");
                        return;
                    }

                    int rank = 1;
                    boolean found = false;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        if (doc.getId().equals(currentUid)) {
                            found = true;
                            break;
                        }
                        rank++;
                    }

                    if (found) {
                        tvRank.setText("Hạng: " + rank);
                    } else {
                        tvRank.setText("No Rank");
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) tvRank.setText("No Rank");
                });
    }

    private void loadAvatar(String url) {
        if (ivProfilePic == null || url == null || url.isEmpty() || !isAdded() || getContext() == null) return;

        String optimizedUrl = url;
        if (url.contains("/svg")) {
            optimizedUrl = url.replace("/svg", "/png");
        } else if (url.endsWith(".svg")) {
            optimizedUrl = url.replace(".svg", ".png");
        }

        Glide.with(this)
                .load(optimizedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(ivProfilePic);
    }

    private void setupClickListeners(View view) {
        MainActivity activity = (MainActivity) getActivity();

        View llLeaderboard = view.findViewById(R.id.llLeaderboard);
        if (llLeaderboard != null) {
            llLeaderboard.setOnClickListener(v -> {
                if (activity != null) activity.showLeaderboard();
            });
        }

        View llHistory = view.findViewById(R.id.llHistory);
        if (llHistory != null) {
            llHistory.setOnClickListener(v -> {
                if (activity != null) activity.showHistory();
            });
        }

        View llFriends = view.findViewById(R.id.llFriends);
        if (llFriends != null) {
            llFriends.setOnClickListener(v -> {
                if (activity != null) activity.showStatistics();
            });
        }

        View llLogout = view.findViewById(R.id.llLogout);
        if (llLogout != null) {
            llLogout.setOnClickListener(v -> {
                authRepository.logout();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }

        View btnEditProfile = view.findViewById(R.id.btnEditProfile);
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), ProfileActivity.class);
                startActivity(intent);
            });
        }

        View llResetPassword = view.findViewById(R.id.llResetPassword);
        if (llResetPassword != null) {
            llResetPassword.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), ChangePasswordActivity.class);
                startActivity(intent);
            });
        }
    }
}
