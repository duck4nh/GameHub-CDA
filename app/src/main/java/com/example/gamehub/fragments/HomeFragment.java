package com.example.gamehub.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.games.memory.MemoryGameActivity;
import com.example.gamehub.games.quiz.QuizActivity;
import com.example.gamehub.games.sudoku.SudokuActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private PreferenceManager preferenceManager;
    private FirebaseFirestore firestore;
    private String currentUid;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    private View tvRecentLabel, hsvRecent;
    private EditText etSearchGame;
    private TextView tvRank;
    private ImageView ivProfile;
    private LinearLayout llActivityList, llRecentGamesContainer;
    private TextView tvNoActivity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferenceManager = new PreferenceManager(requireContext());
        firestore = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();
        db = AppDatabase.getInstance(requireContext());

        initViews(view);
        setupUserInfo(view);
        setupClickListeners(view);
        setupSearchLogic();
        fetchUserInfo();
        fetchRank();
        fetchRecentData();
    }

    private void initViews(View view) {
        tvRecentLabel = view.findViewById(R.id.tvRecentLabel);
        hsvRecent = view.findViewById(R.id.hsvRecent);
        
        etSearchGame = view.findViewById(R.id.etSearchGame);
        tvRank = view.findViewById(R.id.tvHomeRank);
        ivProfile = view.findViewById(R.id.ivHomeProfile);
        llActivityList = view.findViewById(R.id.llActivityList);
        llRecentGamesContainer = view.findViewById(R.id.llRecentGamesContainer);
        tvNoActivity = view.findViewById(R.id.tvNoActivity);
    }

    private void setupUserInfo(View view) {
        TextView tvUsername = view.findViewById(R.id.tvHomeUsername);
        if (tvUsername != null) {
            tvUsername.setText(preferenceManager.getCacheNickname());
        }

        String avatarUrl = preferenceManager.getCacheAvatar();
        loadAvatar(avatarUrl);

        if (tvRank != null) {
            tvRank.setText("Đang tải...");
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
                            TextView tvUsername = getView() != null ? getView().findViewById(R.id.tvHomeUsername) : null;
                            if (tvUsername != null) tvUsername.setText(nickname);
                        }
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            preferenceManager.putString(PreferenceManager.KEY_CACHE_AVATAR, avatarUrl);
                            loadAvatar(avatarUrl);
                        }
                    }
                });
    }

    private void loadAvatar(String url) {
        if (ivProfile == null || url == null || url.isEmpty() || !isAdded() || getContext() == null) return;
        String optimizedUrl = url;
        if (url.contains("/svg")) optimizedUrl = url.replace("/svg", "/png");
        else if (url.endsWith(".svg")) optimizedUrl = url.replace(".svg", ".png");

        Glide.with(this)
                .load(optimizedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(ivProfile);
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
                    if (found) tvRank.setText("Hạng: " + rank);
                    else tvRank.setText("No Rank");
                });
    }

    private void fetchRecentData() {
        executorService.execute(() -> {
            List<LocalHistory> historyList = db.historyDao().getAllNewestFirst();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    renderRecentActivity(historyList);
                    renderRecentGames(historyList);
                });
            }
        });
    }

    private void renderRecentGames(List<LocalHistory> historyList) {
        if (!isAdded() || llRecentGamesContainer == null) return;
        llRecentGamesContainer.removeAllViews();

        if (historyList == null || historyList.isEmpty()) {
            tvRecentLabel.setVisibility(View.GONE);
            hsvRecent.setVisibility(View.GONE);
            return;
        }

        tvRecentLabel.setVisibility(View.VISIBLE);
        hsvRecent.setVisibility(View.VISIBLE);

        Set<String> addedGames = new HashSet<>();
        List<String> orderedGames = new ArrayList<>();

        for (LocalHistory h : historyList) {
            String name = h.gameName;
            if (!addedGames.contains(name)) {
                addedGames.add(name);
                orderedGames.add(name);
            }
        }

        for (String gameName : orderedGames) {
            View gameItem = getLayoutInflater().inflate(R.layout.item_recent_game_home, llRecentGamesContainer, false);
            TextView tvName = gameItem.findViewById(R.id.tvRecentGameName);
            ImageView ivIcon = gameItem.findViewById(R.id.ivRecentGameIcon);
            
            tvName.setText(gameName);
            int iconRes = R.drawable.img_home_sudoku;
            int bgRes = R.drawable.bg_profile_row_orange;
            Class<?> activityClass = SudokuActivity.class;

            if (gameName.equalsIgnoreCase("Sudoku")) {
                iconRes = R.drawable.img_home_sudoku;
                bgRes = R.drawable.bg_profile_row_orange;
                activityClass = SudokuActivity.class;
            } else if (gameName.contains("Lật hình") || gameName.equalsIgnoreCase("Memory")) {
                iconRes = R.drawable.img_home_memory;
                bgRes = R.drawable.bg_profile_row_green;
                activityClass = MemoryGameActivity.class;
                tvName.setText("Lật hình");
            } else if (gameName.contains("Quiz") || gameName.contains("Đố vui")) {
                iconRes = R.drawable.img_home_quiz;
                bgRes = R.drawable.bg_profile_row_blue;
                activityClass = QuizActivity.class;
                tvName.setText("Quiz Game");
            }

            gameItem.setBackgroundResource(bgRes);
            ivIcon.setImageResource(iconRes);
            final Class<?> finalActivity = activityClass;
            gameItem.setOnClickListener(v -> startActivity(new Intent(getActivity(), finalActivity)));
            llRecentGamesContainer.addView(gameItem);
        }
    }

    private void renderRecentActivity(List<LocalHistory> historyList) {
        if (!isAdded() || llActivityList == null) return;
        llActivityList.removeAllViews();

        if (historyList == null || historyList.isEmpty()) {
            tvNoActivity.setVisibility(View.VISIBLE);
            return;
        }

        tvNoActivity.setVisibility(View.GONE);
        int count = Math.min(historyList.size(), 3);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault());

        for (int i = 0; i < count; i++) {
            LocalHistory history = historyList.get(i);
            View itemView = getLayoutInflater().inflate(R.layout.item_recent_activity_home, llActivityList, false);
            TextView tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            TextView tvSubtitle = itemView.findViewById(R.id.tvActivityTime);

            String statusText = history.status.toLowerCase();
            String action = statusText.contains("won") || statusText.contains("completed") ? "Bạn đã hoàn thành " : "Bạn đã chơi ";
            tvTitle.setText(action + history.gameName);
            tvSubtitle.setText(sdf.format(new Date(history.playDate)) + " · " + (history.isSynced ? "synced" : "local"));

            llActivityList.addView(itemView);
        }
    }

    private void setupClickListeners(View view) {
        ivProfile.setOnClickListener(v -> ((MainActivity)getActivity()).findViewById(R.id.bottom_nav).performClick());
    }

    private void setupSearchLogic() {
        etSearchGame.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                tvRecentLabel.setVisibility(query.isEmpty() ? View.VISIBLE : View.GONE);
                hsvRecent.setVisibility(query.isEmpty() ? View.VISIBLE : View.GONE);
                getView().findViewById(R.id.llRecentActivityContainer).setVisibility(query.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
}
