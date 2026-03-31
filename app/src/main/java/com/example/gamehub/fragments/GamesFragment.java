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
    private LinearLayout namesQuiz, namesMemory, namesSudoku;
    private TextView labelQuiz, labelMemory, labelSudoku;
    private View launchQuiz, launchMemory, launchSudoku;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedStatus) {
        View view = inflater.inflate(R.layout.fragment_games, container, false);
        repository = GameRepository.getInstance(requireContext());

        namesQuiz = view.findViewById(R.id.quiz_friends_avatars);
        namesMemory = view.findViewById(R.id.memory_friends_avatars);
        namesSudoku = view.findViewById(R.id.sudoku_friends_avatars);

        labelQuiz = view.findViewById(R.id.quiz_friends_status);
        labelMemory = view.findViewById(R.id.memory_friends_status);
        labelSudoku = view.findViewById(R.id.sudoku_friends_status);

        launchQuiz = view.findViewById(R.id.launch_quiz);
        launchMemory = view.findViewById(R.id.launch_memory);
        launchSudoku = view.findViewById(R.id.launch_sudoku);

        setupClickListeners();
        loadFriendsPlayed();

        return view;
    }

    private void setupClickListeners() {
        launchQuiz.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), QuizActivity.class);
            startActivity(intent);
        });

        launchMemory.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MemoryGameActivity.class);
            startActivity(intent);
        });

        launchSudoku.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SudokuActivity.class);
            startActivity(intent);
        });
    }

    private void loadFriendsPlayed() {
        loadForGame("quiz", namesQuiz, labelQuiz);
        loadForGame("memory", namesMemory, labelMemory);
        loadForGame("sudoku", namesSudoku, labelSudoku);
    }

    private void loadForGame(String type, LinearLayout layout, TextView label) {
        repository.fetchFriendsWhoPlayed(type, new GameRepository.FriendsPlayedCallback() {
            @Override
            public void onLoaded(List<String> names) {
                if (!isAdded()) return;
                layout.removeAllViews();
                if (names.isEmpty()) {
                    label.setText("Chưa có bạn bè nào chơi");
                } else {
                    label.setText("Bạn bè đã chơi:");
                    // Hiển thị tối đa tên của 3 người bạn
                    int count = Math.min(names.size(), 3);
                    for (int i = 0; i < count; i++) {
                        String displayText = names.get(i);
                        if (i < count - 1) displayText += ", ";
                        addNameToLayout(layout, displayText);
                    }
                    if (names.size() > 3) {
                        addNameToLayout(layout, " và " + (names.size() - 3) + " người khác");
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                label.setText("Chưa có bạn bè nào chơi");
            }
        });
    }

    private void addNameToLayout(LinearLayout layout, String text) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextSize(12);
        textView.setTextColor(getResources().getColor(R.color.gh_text_secondary));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textView.setLayoutParams(params);

        layout.addView(textView);
    }
}
