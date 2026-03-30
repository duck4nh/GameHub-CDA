package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.models.User;
import java.text.NumberFormat;
import java.util.Locale;

public class StatisticsFragment extends Fragment {
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));

    private GameRepository repository;
    private TextView metricWeeklyScore;
    private TextView metricWeeklyDelta;
    private TextView metricStreak;
    private TextView metricStreakCaption;
    private View offlineBanner;
    private TextView leaderboardSummaryCaption;
    private View bestQuizRow;
    private TextView bestQuizTime;
    private View bestMemoryRow;
    private TextView bestMemoryTime;
    private View bestSudokuRow;
    private TextView bestSudokuTime;
    private TextView historySummaryCaption;
    private ImageView headerAvatar;
    private TextView chartOverviewText;
    private TextView chartWeekTotalChip;
    private TextView[] chartValueLabels;
    private TextView[] chartDayLabels;
    private View[] chartBars;
    private boolean requestedCurrentUserProfile;

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
        offlineBanner = view.findViewById(R.id.offline_banner);
        leaderboardSummaryCaption = view.findViewById(R.id.leaderboard_summary_caption);
        bestQuizRow = view.findViewById(R.id.best_quiz_row);
        bestQuizTime = view.findViewById(R.id.best_quiz_time);
        bestMemoryRow = view.findViewById(R.id.best_memory_row);
        bestMemoryTime = view.findViewById(R.id.best_memory_time);
        bestSudokuRow = view.findViewById(R.id.best_sudoku_row);
        bestSudokuTime = view.findViewById(R.id.best_sudoku_time);
        historySummaryCaption = view.findViewById(R.id.history_summary_caption);
        headerAvatar = view.findViewById(R.id.stats_header_avatar);
        chartOverviewText = view.findViewById(R.id.chart_overview_text);
        chartWeekTotalChip = view.findViewById(R.id.chart_week_total_chip);
        chartValueLabels = new TextView[]{
                view.findViewById(R.id.bar_value_1),
                view.findViewById(R.id.bar_value_2),
                view.findViewById(R.id.bar_value_3),
                view.findViewById(R.id.bar_value_4),
                view.findViewById(R.id.bar_value_5),
                view.findViewById(R.id.bar_value_6),
                view.findViewById(R.id.bar_value_7)
        };
        chartDayLabels = new TextView[]{
                view.findViewById(R.id.bar_label_1),
                view.findViewById(R.id.bar_label_2),
                view.findViewById(R.id.bar_label_3),
                view.findViewById(R.id.bar_label_4),
                view.findViewById(R.id.bar_label_5),
                view.findViewById(R.id.bar_label_6),
                view.findViewById(R.id.bar_label_7)
        };
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

        if (offlineBanner != null) {
            offlineBanner.setVisibility(View.GONE);
        }

        bindCurrentUserAvatar();
        renderStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            bindCurrentUserAvatar();
            renderStatistics();
        }
    }

    private void renderStatistics() {
        metricWeeklyScore.setText(numberFormat.format(repository.getWeeklyScore()));
        metricWeeklyDelta.setText(repository.getWeeklyScoreChangeLabel());
        metricStreak.setText(String.format(Locale.getDefault(), "%d ngày", repository.getCurrentStreakDays()));
        metricStreakCaption.setText("Số ngày chơi liên tiếp");
        leaderboardSummaryCaption.setText("Đang tải từ leaderboard trực tuyến...");
        repository.fetchWeeklyLeaderboardSummary(new GameRepository.LeaderboardSummaryCallback() {
            @Override
            public void onLoaded(@Nullable com.example.gamehub.models.LeaderboardEntry currentUserEntry, String trendLabel) {
                if (!isAdded()) {
                    return;
                }
                if (currentUserEntry != null) {
                    leaderboardSummaryCaption.setText(
                            String.format(
                                    Locale.getDefault(),
                                    "#%d tuần này · %s",
                                    currentUserEntry.getRank(),
                                    trendLabel
                            )
                    );
                } else {
                    leaderboardSummaryCaption.setText(trendLabel);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                leaderboardSummaryCaption.setText("Chưa tải được leaderboard");
            }
        });

        bindBestHistoryRow(bestQuizRow, bestQuizTime, repository.getBestHistoryForGame("quiz"));
        bindBestHistoryRow(bestMemoryRow, bestMemoryTime, repository.getBestHistoryForGame("memory"));
        bindBestHistoryRow(bestSudokuRow, bestSudokuTime, repository.getBestHistoryForGame("sudoku"));
        historySummaryCaption.setText(
                String.format(
                        Locale.getDefault(),
                        "%d trận · %d%% thắng · TB %s",
                        repository.getTotalMatches(),
                        repository.getWinRate(),
                        formatDuration(repository.getAverageCompletionTime())
                )
        );

        updateChart(repository.getWeeklyMatchCountByDay());
    }

    private void bindCurrentUserAvatar() {
        if (headerAvatar == null) {
            return;
        }
        loadAvatar(repository.getCachedAvatarUrl());
        if (requestedCurrentUserProfile) {
            return;
        }
        requestedCurrentUserProfile = true;
        repository.fetchCurrentUserProfile(new GameRepository.UserProfileCallback() {
            @Override
            public void onLoaded(User user) {
                if (!isAdded()) {
                    return;
                }
                loadAvatar(user.getAvatarUrl());
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                loadAvatar("");
            }
        });
    }

    private void updateChart(int[] values) {
        int max = 1;
        int total = 0;
        int peakIndex = -1;
        for (int value : values) {
            max = Math.max(max, value);
            total += value;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0 && (peakIndex < 0 || values[i] > values[peakIndex])) {
                peakIndex = i;
            }
        }
        for (int i = 0; i < chartBars.length; i++) {
            View bar = chartBars[i];
            LayoutParams layoutParams = bar.getLayoutParams();
            int minHeight = dpToPx(10);
            int maxHeight = dpToPx(108);
            int scaled = values[i] <= 0
                    ? minHeight
                    : dpToPx(18) + Math.round((maxHeight - dpToPx(18)) * (values[i] / (float) max));
            layoutParams.height = scaled;
            bar.setLayoutParams(layoutParams);
            boolean isHighlight = values[i] > 0 && values[i] == max;
            bar.setBackgroundResource(isHighlight ? R.drawable.bg_stats_bar_fill_active : R.drawable.bg_stats_bar_fill);
            bar.setAlpha(values[i] > 0 ? 1f : 0.42f);

            chartValueLabels[i].setText(String.valueOf(values[i]));
            chartValueLabels[i].setBackgroundResource(isHighlight ? R.drawable.bg_stats_value_chip_active : R.drawable.bg_stats_value_chip);
            chartValueLabels[i].setAlpha(values[i] > 0 ? 1f : 0.68f);

            chartDayLabels[i].setTextColor(requireContext().getColor(isHighlight ? R.color.gh_text_primary : R.color.gh_text_secondary));
            chartDayLabels[i].setAlpha(isHighlight ? 1f : 0.86f);
            chartDayLabels[i].setTypeface(chartDayLabels[i].getTypeface(), isHighlight ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        chartWeekTotalChip.setText(String.format(Locale.getDefault(), "%d trận", total));
        if (total <= 0 || peakIndex < 0) {
            chartOverviewText.setText("Chưa có trận nào trong tuần này");
        } else {
            chartOverviewText.setText(String.format(
                    Locale.getDefault(),
                    "Cao nhất vào %s",
                    chartDayLabels[peakIndex].getText()
            ));
        }
    }

    private void bindBestHistoryRow(View row, TextView valueView, @Nullable com.example.gamehub.data.local.entities.LocalHistory history) {
        if (history == null) {
            valueView.setText("Chưa có dữ liệu");
            row.setAlpha(0.55f);
            row.setOnClickListener(null);
            return;
        }
        valueView.setText(formatDuration(history.timeSpent));
        row.setAlpha(1f);
        row.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showHistoryDetail(history.id);
            }
        });
    }

    private void loadAvatar(@Nullable String url) {
        if (headerAvatar == null || !isAdded()) {
            return;
        }

        String optimizedUrl = url == null ? "" : url.trim();
        if (optimizedUrl.contains("/svg")) {
            optimizedUrl = optimizedUrl.replace("/svg", "/png");
        } else if (optimizedUrl.endsWith(".svg")) {
            optimizedUrl = optimizedUrl.replace(".svg", ".png");
        }
        String cacheKey = optimizedUrl.isEmpty() ? "__fallback__" : optimizedUrl;
        Object currentTag = headerAvatar.getTag();
        if (cacheKey.equals(currentTag)) {
            return;
        }
        headerAvatar.setTag(cacheKey);

        Glide.with(this)
                .load(optimizedUrl.isEmpty() ? R.drawable.img_avatar_cat : optimizedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(headerAvatar);
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
