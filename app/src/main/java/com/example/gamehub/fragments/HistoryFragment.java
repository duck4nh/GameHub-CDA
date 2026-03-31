package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.adapter.HistoryAdapter;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.repository.GameRepository;

public class HistoryFragment extends Fragment {
    private GameRepository repository;
    private HistoryAdapter historyAdapter;
    private View queueBanner;
    private TextView filterAll;
    private TextView filterQuiz;
    private TextView filterMemory;
    private TextView filterSudoku;
    private String selectedFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = GameRepository.getInstance(requireContext());
        historyAdapter = new HistoryAdapter(this::openHistoryDetail);

        queueBanner = view.findViewById(R.id.queue_banner);
        filterAll = view.findViewById(R.id.filter_all);
        filterQuiz = view.findViewById(R.id.filter_quiz);
        filterMemory = view.findViewById(R.id.filter_memory);
        filterSudoku = view.findViewById(R.id.filter_sudoku);

        RecyclerView historyList = view.findViewById(R.id.history_list);
        historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyList.setAdapter(historyAdapter);

        view.findViewById(R.id.history_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        filterAll.setOnClickListener(v -> applyFilter("all"));
        filterQuiz.setOnClickListener(v -> applyFilter("quiz"));
        filterMemory.setOnClickListener(v -> applyFilter("memory"));
        filterSudoku.setOnClickListener(v -> applyFilter("sudoku"));

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(true);
        }

        if (queueBanner != null) {
            queueBanner.setVisibility(View.GONE);
        }
        applyFilter(selectedFilter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            applyFilter(selectedFilter);
        }
    }

    private void applyFilter(String filter) {
        selectedFilter = filter;
        setFilterState(filter);
        historyAdapter.submitList(repository.getHistory(filter));
    }

    private void setFilterState(String filter) {
        filterAll.setBackgroundResource("all".equals(filter) ? R.drawable.bg_filter_selected : R.drawable.bg_filter_unselected);
        filterQuiz.setBackgroundResource("quiz".equals(filter) ? R.drawable.bg_filter_selected : R.drawable.bg_filter_unselected);
        filterMemory.setBackgroundResource("memory".equals(filter) ? R.drawable.bg_filter_selected : R.drawable.bg_filter_unselected);
        filterSudoku.setBackgroundResource("sudoku".equals(filter) ? R.drawable.bg_filter_selected : R.drawable.bg_filter_unselected);
    }

    private void openHistoryDetail(LocalHistory item) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showHistoryDetail(item.id);
        }
    }
}
