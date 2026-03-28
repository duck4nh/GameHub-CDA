package com.example.gamehub.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gamehub.R;
import com.example.gamehub.games.memory.MemoryGameActivity;
import com.example.gamehub.games.quiz.QuizActivity;
import com.example.gamehub.games.sudoku.SudokuActivity;

public class GamesFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_games, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.launch_quiz).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), QuizActivity.class)));
        view.findViewById(R.id.launch_memory).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MemoryGameActivity.class)));
        view.findViewById(R.id.launch_sudoku).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SudokuActivity.class)));
    }
}
