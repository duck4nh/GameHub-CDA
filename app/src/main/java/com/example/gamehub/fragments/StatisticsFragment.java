package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.models.LeaderboardEntry;

import java.text.NumberFormat;
import java.util.Locale;

public class StatisticsFragment extends Fragment {
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));

    private GameRepository repository;
    private TextView metricWeeklyScore;
    private TextView metricWeeklyDelta;
    private TextView metricStreak;
    private TextView metricStreakCaption;
    private TextView metricAverageTime;
    private TextView offlineBannerTitle;
    private TextView offlineBannerSubtitle;
    private TextView leaderboardSummaryCaption;
    private TextView bestSudokuTime;
    private TextView historySummaryCaption;
    private View[] chartBars;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = GameRepository.getInstance(requireContext());

        metricWeeklyScore = view.findViewById(R.id.metric_weekly_score);
        metricWeeklyDelta = view.findViewById(R.id.metric_weekly_delta);
        metricStreak = view.findViewById(R.id.metric_streak);
        metricStreakCaption = view.findViewById(R.id.metric_streak_caption);
        metricAverageTime = view.findViewById(R.id.metric_average_time);
        offlineBannerTitle = view.findViewById(R.id.offline_banner_title);
        offlineBannerSubtitle = view.findViewById(R.id.offline_banner_subtitle);
        leaderboardSummaryCaption = view.findViewById(R.id.leaderboard_summary_caption);
        bestSudokuTime = view.findViewById(R.id.best_sudoku_time);
        historySummaryCaption = view.findViewById(R.id.history_summary_caption);
        chartBars = new View[]{
                view.findViewById(R.id.bar_day_1),
                view.findViewById(R.id.bar_day_2),
                view.findViewById(R.id.bar_day_3),
                view.findViewById(R.id.bar_day_4),
                view.findViewById(R.id.bar_day_5),
                view.findViewById(R.id.bar_day_6),
                view.findViewById(R.id.bar_day_7)
        };

        view.findViewById(R.id.leaderboard_summary_row).setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showLeaderboard();
            }
        });
        view.findViewById(R.id.history_summary_row).setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showHistory();
            }
        });

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(true);
        }

        renderStatistics();
    }

    private void renderStatistics() {
        metricWeeklyScore.setText(numberFormat.format(repository.getWeeklyScore()));
        metricWeeklyDelta.setText(repository.getWeeklyScoreChangeLabel());
        metricStreak.setText(String.format(Locale.getDefault(), "%d ngày", repository.getCurrentStreakDays()));
        metricStreakCaption.setText("Duy trì từ Local_History");
        metricAverageTime.setText(formatDuration(repository.getAverageCompletionTime()));

        LeaderboardEntry weeklyEntry = repository.getCurrentUserEntry(true);
        if (weeklyEntry != null) {
            leaderboardSummaryCaption.setText(
                    String.format(
                            Locale.getDefault(),
                            "#%d tuần này · %s",
                            weeklyEntry.getRank(),
                            repository.getCurrentUserTrendLabel(true)
                    )
            );
        } else {
            leaderboardSummaryCaption.setText(repository.getCurrentUserTrendLabel(true));
        }

        bestSudokuTime.setText(formatDuration(repository.getBestSudokuTime()));
        historySummaryCaption.setText(
                String.format(
                        Locale.getDefault(),
                        "%d trận · %d%% thắng · TB %s",
                        repository.getTotalMatches(),
                        repository.getWinRate(),
                        formatDuration(repository.getAverageCompletionTime())
                )
        );

        int unsyncedCount = repository.getUnsyncedCount();
        if (unsyncedCount > 0) {
            offlineBannerTitle.setText(String.format(Locale.getDefault(), "%d trận đã lưu ngoại tuyến", unsyncedCount));
            offlineBannerSubtitle.setText("Sẽ đồng bộ khi kết nối ổn định.");
        } else {
            offlineBannerTitle.setText("Lịch sử đã đồng bộ");
            offlineBannerSubtitle.setText("Không còn trận nào chờ tải lên.");
        }

        updateChart(repository.getPlayTimeMinutesByDay());
    }

    private void updateChart(int[] values) {
        int max = 1;
        for (int value : values) {
            max = Math.max(max, value);
        }
        for (int i = 0; i < chartBars.length; i++) {
            View bar = chartBars[i];
            LayoutParams layoutParams = bar.getLayoutParams();
            int minHeight = dpToPx(74);
            int maxHeight = dpToPx(126);
            int scaled = minHeight + Math.round((maxHeight - minHeight) * (values[i] / (float) max));
            layoutParams.height = scaled;
            bar.setLayoutParams(layoutParams);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = durationMillis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
