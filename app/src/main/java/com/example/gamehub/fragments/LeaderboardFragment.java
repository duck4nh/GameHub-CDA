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

import com.example.gamehub.R;
import com.example.gamehub.adapter.LeaderboardAdapter;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.models.LeaderboardEntry;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardFragment extends Fragment {
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));

    private GameRepository repository;
    private LeaderboardAdapter adapter;
    private TextView weeklyTab;
    private TextView allTimeTab;
    private TextView podiumRankFirst;
    private TextView podiumRankSecond;
    private TextView podiumRankThird;
    private TextView podiumNameFirst;
    private TextView podiumNameSecond;
    private TextView podiumNameThird;
    private TextView currentUserRank;
    private TextView currentUserTrend;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = GameRepository.getInstance(requireContext());
        adapter = new LeaderboardAdapter();

        weeklyTab = view.findViewById(R.id.weekly_tab);
        allTimeTab = view.findViewById(R.id.all_time_tab);
        podiumRankFirst = view.findViewById(R.id.podium_rank_first);
        podiumRankSecond = view.findViewById(R.id.podium_rank_second);
        podiumRankThird = view.findViewById(R.id.podium_rank_third);
        podiumNameFirst = view.findViewById(R.id.podium_name_first);
        podiumNameSecond = view.findViewById(R.id.podium_name_second);
        podiumNameThird = view.findViewById(R.id.podium_name_third);
        currentUserRank = view.findViewById(R.id.current_user_rank);
        currentUserTrend = view.findViewById(R.id.current_user_trend);

        RecyclerView leaderboardList = view.findViewById(R.id.leaderboard_list);
        leaderboardList.setLayoutManager(new LinearLayoutManager(requireContext()));
        leaderboardList.setAdapter(adapter);

        weeklyTab.setOnClickListener(v -> renderLeaderboard(true));
        allTimeTab.setOnClickListener(v -> renderLeaderboard(false));

        renderLeaderboard(repository.isWeeklyLeaderboardSelected());
    }

    private void renderLeaderboard(boolean weekly) {
        repository.setLeaderboardFilter(weekly);
        setToggleState(weekly);

        List<LeaderboardEntry> entries = repository.getLeaderboardEntries(weekly);
        bindPodium(entries);

        LeaderboardEntry currentUser = repository.getCurrentUserEntry(weekly);
        if (currentUser != null) {
            currentUserRank.setText(
                    String.format(
                            Locale.getDefault(),
                            "#%d · %s điểm",
                            currentUser.getRank(),
                            numberFormat.format(currentUser.getScore())
                    )
            );
        } else {
            currentUserRank.setText("Chưa có thứ hạng");
        }
        currentUserTrend.setText(repository.getCurrentUserTrendLabel(weekly));

        List<LeaderboardEntry> remaining = new ArrayList<>();
        for (LeaderboardEntry entry : entries) {
            if (entry.getRank() > 3 && !entry.isCurrentUser()) {
                remaining.add(entry);
            }
        }
        adapter.submitList(remaining);
    }

    private void bindPodium(List<LeaderboardEntry> entries) {
        bindPodiumEntry(entries, 0, podiumRankFirst, podiumNameFirst, "#1", "Linh");
        bindPodiumEntry(entries, 1, podiumRankSecond, podiumNameSecond, "#2", "Minh");
        bindPodiumEntry(entries, 2, podiumRankThird, podiumNameThird, "#3", "Trang");
    }

    private void bindPodiumEntry(List<LeaderboardEntry> entries, int index, TextView rankView, TextView nameView, String fallbackRank, String fallbackName) {
        if (entries.size() > index) {
            LeaderboardEntry entry = entries.get(index);
            rankView.setText("#" + entry.getRank());
            nameView.setText(entry.getNickname());
        } else {
            rankView.setText(fallbackRank);
            nameView.setText(fallbackName);
        }
    }

    private void setToggleState(boolean weekly) {
        weeklyTab.setBackgroundResource(weekly ? R.drawable.bg_segmented_selected : android.R.color.transparent);
        allTimeTab.setBackgroundResource(weekly ? android.R.color.transparent : R.drawable.bg_segmented_selected);
    }
}
