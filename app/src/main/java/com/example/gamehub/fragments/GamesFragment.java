package com.example.gamehub.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gamehub.R;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.games.memory.MemoryGameActivity;
import com.example.gamehub.games.quiz.QuizActivity;
import com.example.gamehub.games.sudoku.SudokuActivity;

import java.util.List;

public class GamesFragment extends Fragment {

    private GameRepository repository;
    private LinearLayout layoutQuizFriends, layoutMemoryFriends, layoutSudokuFriends;
    private TextView tvQuizStatus, tvMemoryStatus, tvSudokuStatus;
    private View launchQuiz, launchMemory, launchSudoku;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedStatus) {
        View view = inflater.inflate(R.layout.fragment_games, container, false);
        repository = GameRepository.getInstance(requireContext());

        layoutQuizFriends = view.findViewById(R.id.quiz_friends_avatars);
        layoutMemoryFriends = view.findViewById(R.id.memory_friends_avatars);
        layoutSudokuFriends = view.findViewById(R.id.sudoku_friends_avatars);

        tvQuizStatus = view.findViewById(R.id.quiz_friends_status);
        tvMemoryStatus = view.findViewById(R.id.memory_friends_status);
        tvSudokuStatus = view.findViewById(R.id.sudoku_friends_status);

        launchQuiz = view.findViewById(R.id.launch_quiz);
        launchMemory = view.findViewById(R.id.launch_memory);
        launchSudoku = view.findViewById(R.id.launch_sudoku);

        setupClickListeners();
        loadFriendsPlayed();

        return view;
    }

    private void setupClickListeners() {
        launchQuiz.setOnClickListener(v -> startActivity(new Intent(requireContext(), QuizActivity.class)));
        launchMemory.setOnClickListener(v -> startActivity(new Intent(requireContext(), MemoryGameActivity.class)));
        launchSudoku.setOnClickListener(v -> startActivity(new Intent(requireContext(), SudokuActivity.class)));
    }

    private void loadFriendsPlayed() {
        loadForGame("quiz", layoutQuizFriends, tvQuizStatus);
        loadForGame("memory", layoutMemoryFriends, tvMemoryStatus);
        loadForGame("sudoku", layoutSudokuFriends, tvSudokuStatus);
    }

    private void loadForGame(String type, LinearLayout layout, TextView statusLabel) {
        repository.fetchFriendsWhoPlayed(type, new GameRepository.FriendsPlayedCallback() {
            @Override
            public void onLoaded(List<String> names) {
                if (!isAdded()) return;
                layout.removeAllViews();
                
                if (names == null || names.isEmpty()) {
                    statusLabel.setText("Chưa có bạn bè nào chơi");
                } else {
                    statusLabel.setText("Bạn bè đã chơi: ");
                    
                    // Xây dựng chuỗi tên hiển thị trực tiếp vào label để tiết kiệm diện tích
                    StringBuilder sb = new StringBuilder("Bạn bè đã chơi: ");
                    int showLimit = 2;
                    for (int i = 0; i < Math.min(names.size(), showLimit); i++) {
                        sb.append(names.get(i));
                        if (i < Math.min(names.size(), showLimit) - 1) {
                            sb.append(", ");
                        }
                    }
                    
                    if (names.size() > showLimit) {
                        sb.append(" và ").append(names.size() - showLimit).append(" người khác");
                    }
                    
                    statusLabel.setText(sb.toString());
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) statusLabel.setText("Chưa có bạn bè nào chơi");
            }
        });
    }
}
