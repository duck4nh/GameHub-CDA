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
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.games.memory.MemoryGameActivity;
import com.example.gamehub.games.quiz.QuizActivity;
import com.example.gamehub.games.sudoku.SudokuActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class HomeFragment extends Fragment {

    private PreferenceManager preferenceManager;
    private FirebaseFirestore firestore;
    private String currentUid;
    
    private View cardSudoku, cardMemory, cardQuiz;
    private ImageView ivSudokuIcon, ivMemoryIcon, ivQuizIcon;
    private View tvRecentLabel, hsvRecent;
    private TextView tvGamesLabel;
    private EditText etSearchGame;
    private TextView tvRank;
    private ImageView ivProfile;

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

        initViews(view);
        setupUserInfo(view);
        setupClickListeners(view);
        setupSearchLogic();
        fetchUserInfo();
        fetchRank();
        
        disableClipping(view);
    }

    private void initViews(View view) {
        cardSudoku = view.findViewById(R.id.cardSudoku);
        cardMemory = view.findViewById(R.id.cardMemory);
        cardQuiz = view.findViewById(R.id.cardQuiz);
        
        ivSudokuIcon = view.findViewById(R.id.ivSudokuIcon);
        ivMemoryIcon = view.findViewById(R.id.ivMemoryIcon);
        ivQuizIcon = view.findViewById(R.id.ivQuizIcon);
        
        tvRecentLabel = view.findViewById(R.id.tvRecentLabel);
        hsvRecent = view.findViewById(R.id.hsvRecent);
        tvGamesLabel = view.findViewById(R.id.tvGamesLabel);
        
        etSearchGame = view.findViewById(R.id.etSearchGame);
        tvRank = view.findViewById(R.id.tvHomeRank);
        ivProfile = view.findViewById(R.id.ivHomeProfile);
    }

    private void disableClipping(View view) {
        GridLayout glGames = view.findViewById(R.id.glGames);
        if (glGames != null) {
            glGames.setClipChildren(false);
            glGames.setClipToPadding(false);
        }
        if (cardSudoku instanceof ViewGroup) ((ViewGroup) cardSudoku).setClipChildren(false);
        if (cardMemory instanceof ViewGroup) ((ViewGroup) cardMemory).setClipChildren(false);
        if (cardQuiz instanceof ViewGroup) ((ViewGroup) cardQuiz).setClipChildren(false);
    }

    private void setupUserInfo(View view) {
        TextView tvUsername = view.findViewById(R.id.tvHomeUsername);
        if (tvUsername != null) {
            tvUsername.setText(preferenceManager.getCacheNickname());
        }

        // Load avatar from cache immediately
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
                    // Kiểm tra Fragment còn gắn vào Activity không trước khi xử lý UI/Glide
                    if (!isAdded() || getContext() == null) return;

                    if (document.exists()) {
                        String nickname = document.getString("nickname");
                        String avatarUrl = document.getString("avatar_url");
                        
                        Log.d("HomeFragment", "Fetched Avatar URL: " + avatarUrl);
                        
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
                })
                .addOnFailureListener(e -> Log.e("HomeFragment", "Error fetching user info", e));
    }

    private void loadAvatar(String url) {
        // Kiểm tra Fragment còn tồn tại không
        if (ivProfile == null || url == null || url.isEmpty() || !isAdded() || getContext() == null) {
            Log.d("HomeFragment", "Cannot load avatar: View or Fragment not ready");
            return;
        }
        
        // Cố gắng chuyển đổi sang định dạng PNG để Glide hiển thị tốt nhất
        String optimizedUrl = url;
        if (url.contains("/svg")) {
            optimizedUrl = url.replace("/svg", "/png");
        } else if (url.endsWith(".svg")) {
            optimizedUrl = url.replace(".svg", ".png");
        }

        Log.d("HomeFragment", "Final Optimized URL: " + optimizedUrl);
        
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

    private void setupClickListeners(View view) {
        View.OnClickListener goToProfile = v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                BottomNavigationView bottomNav = mainActivity.findViewById(R.id.bottom_nav);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_profile);
                }
            }
        };
        
        if (ivProfile != null) ivProfile.setOnClickListener(goToProfile);
        TextView tvUsername = view.findViewById(R.id.tvHomeUsername);
        if (tvUsername != null) tvUsername.setOnClickListener(goToProfile);
        if (tvRank != null) tvRank.setOnClickListener(goToProfile);

        if (cardSudoku != null) cardSudoku.setOnClickListener(v -> startActivity(new Intent(getActivity(), SudokuActivity.class)));
        if (cardMemory != null) cardMemory.setOnClickListener(v -> startActivity(new Intent(getActivity(), MemoryGameActivity.class)));
        if (cardQuiz != null) cardQuiz.setOnClickListener(v -> startActivity(new Intent(getActivity(), QuizActivity.class)));

        View llRecentSudoku = view.findViewById(R.id.llRecentSudoku);
        if (llRecentSudoku != null) llRecentSudoku.setOnClickListener(v -> startActivity(new Intent(getActivity(), SudokuActivity.class)));
        
        View llRecentMemory = view.findViewById(R.id.llRecentMemory);
        if (llRecentMemory != null) llRecentMemory.setOnClickListener(v -> startActivity(new Intent(getActivity(), MemoryGameActivity.class)));
        
        View llRecentQuiz = view.findViewById(R.id.llRecentQuiz);
        if (llRecentQuiz != null) llRecentQuiz.setOnClickListener(v -> startActivity(new Intent(getActivity(), QuizActivity.class)));
    }

    private void setupSearchLogic() {
        etSearchGame.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterGames(s.toString().toLowerCase().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterGames(String query) {
        if (query.isEmpty()) {
            tvRecentLabel.setVisibility(View.VISIBLE);
            hsvRecent.setVisibility(View.VISIBLE);
            tvGamesLabel.setVisibility(View.VISIBLE);
            tvGamesLabel.setText("Trò chơi");
            
            updateCard(cardSudoku, ivSudokuIcon, true);
            updateCard(cardMemory, ivMemoryIcon, true);
            updateCard(cardQuiz, ivQuizIcon, true);
        } else {
            tvRecentLabel.setVisibility(View.GONE);
            hsvRecent.setVisibility(View.GONE);
            tvGamesLabel.setText("Kết quả tìm kiếm");

            boolean matchSudoku = "sudoku".contains(query);
            boolean matchMemory = "lật hình".contains(query) || "memory".contains(query) || "lat hinh".contains(query);
            boolean matchQuiz = "quiz game".contains(query) || "đố vui".contains(query) || "do vui".contains(query) || "quiz".contains(query);

            updateCard(cardSudoku, ivSudokuIcon, matchSudoku);
            updateCard(cardMemory, ivMemoryIcon, matchMemory);
            updateCard(cardQuiz, ivQuizIcon, matchQuiz);
        }
    }

    private void updateCard(View card, ImageView icon, boolean isVisible) {
        if (card == null) return;
        card.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        if (isVisible && icon != null) {
            icon.setVisibility(View.VISIBLE);
            icon.bringToFront();
            icon.requestLayout();
        }
    }
}
