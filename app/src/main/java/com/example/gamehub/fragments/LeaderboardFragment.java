package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.R;
import com.example.gamehub.adapter.LeaderboardAdapter;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.models.LeaderboardEntry;
import com.example.gamehub.models.User;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
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
    private ImageView headerAvatar;
    private ImageView podiumAvatarFirst;
    private ImageView podiumAvatarSecond;
    private ImageView podiumAvatarThird;
    private TextView podiumNameFirst;
    private TextView podiumNameSecond;
    private TextView podiumNameThird;
    private TextView podiumScoreFirst;
    private TextView podiumScoreSecond;
    private TextView podiumScoreThird;
    private TextView currentUserRank;
    private TextView currentUserName;
    private ImageView currentUserAvatar;
    private TextView currentUserTrend;
    private TextView leaderboardStatusText;
    private TextView leaderboardEmptyText;
    private int leaderboardRequestVersion;
    private boolean hasRenderedLeaderboard;
    private boolean requestedCurrentUserProfile;

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
        headerAvatar = view.findViewById(R.id.leaderboard_header_avatar);
        podiumRankFirst = view.findViewById(R.id.podium_rank_first);
        podiumRankSecond = view.findViewById(R.id.podium_rank_second);
        podiumRankThird = view.findViewById(R.id.podium_rank_third);
        podiumAvatarFirst = view.findViewById(R.id.podium_avatar_first);
        podiumAvatarSecond = view.findViewById(R.id.podium_avatar_second);
        podiumAvatarThird = view.findViewById(R.id.podium_avatar_third);
        podiumNameFirst = view.findViewById(R.id.podium_name_first);
        podiumNameSecond = view.findViewById(R.id.podium_name_second);
        podiumNameThird = view.findViewById(R.id.podium_name_third);
        podiumScoreFirst = view.findViewById(R.id.podium_score_first);
        podiumScoreSecond = view.findViewById(R.id.podium_score_second);
        podiumScoreThird = view.findViewById(R.id.podium_score_third);
        currentUserRank = view.findViewById(R.id.current_user_rank);
        currentUserName = view.findViewById(R.id.current_user_name);
        currentUserAvatar = view.findViewById(R.id.current_user_avatar);
        currentUserTrend = view.findViewById(R.id.current_user_trend);
        leaderboardStatusText = view.findViewById(R.id.leaderboard_status_text);
        leaderboardEmptyText = view.findViewById(R.id.leaderboard_empty_text);

        RecyclerView leaderboardList = view.findViewById(R.id.leaderboard_list);
        leaderboardList.setLayoutManager(new LinearLayoutManager(requireContext()));
        leaderboardList.setAdapter(adapter);

        weeklyTab.setOnClickListener(v -> renderLeaderboard(true));
        allTimeTab.setOnClickListener(v -> renderLeaderboard(false));

        bindCurrentUserAvatars();
        renderLeaderboard(repository.isWeeklyLeaderboardSelected());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            bindCurrentUserAvatars();
            renderLeaderboard(repository.isWeeklyLeaderboardSelected());
        }
    }

    private void renderLeaderboard(boolean weekly) {
        int requestVersion = ++leaderboardRequestVersion;
        boolean hasInstantCache = repository.hasLeaderboardCache(weekly);
        repository.setLeaderboardFilter(weekly);
        setToggleState(weekly);
        if (!hasRenderedLeaderboard && !hasInstantCache) {
            leaderboardStatusText.setVisibility(View.VISIBLE);
            leaderboardStatusText.setText("Đang tải bảng xếp hạng...");
            leaderboardEmptyText.setVisibility(View.GONE);
            currentUserName.setText(repository.getCurrentNickname());
            currentUserRank.setText("Đang tải thứ hạng...");
            currentUserTrend.setText(weekly ? "Tuần này" : "Toàn thời gian");
            bindPodium(Collections.emptyList());
            adapter.submitList(Collections.emptyList());
        } else {
            leaderboardStatusText.setVisibility(View.GONE);
        }

        repository.fetchLeaderboard(weekly, new GameRepository.LeaderboardCallback() {
            @Override
            public void onLoaded(List<LeaderboardEntry> entries, @Nullable LeaderboardEntry currentUserEntry, String trendLabel) {
                if (!isAdded() || requestVersion != leaderboardRequestVersion) {
                    return;
                }
                hasRenderedLeaderboard = true;
                leaderboardStatusText.setVisibility(View.GONE);
                bindPodium(entries);
                leaderboardEmptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

                if (currentUserEntry != null) {
                    currentUserName.setText(currentUserEntry.getNickname());
                    loadAvatar(currentUserAvatar, currentUserEntry.getAvatarUrl());
                    currentUserRank.setText(
                            String.format(
                                    Locale.getDefault(),
                                    "#%d · %s điểm",
                                    currentUserEntry.getRank(),
                                    numberFormat.format(currentUserEntry.getScore())
                            )
                    );
                } else {
                    currentUserName.setText(repository.getCurrentNickname());
                    currentUserRank.setText("Chưa có thứ hạng");
                }
                currentUserTrend.setText(trendLabel);

                List<LeaderboardEntry> remaining = new ArrayList<>();
                for (LeaderboardEntry entry : entries) {
                    if (entry.getRank() > 3 && !entry.isCurrentUser()) {
                        remaining.add(entry);
                    }
                }
                adapter.submitList(remaining);
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || requestVersion != leaderboardRequestVersion) {
                    return;
                }
                bindPodium(Collections.emptyList());
                adapter.submitList(Collections.emptyList());
                leaderboardEmptyText.setVisibility(View.VISIBLE);
                leaderboardStatusText.setVisibility(View.VISIBLE);
                leaderboardStatusText.setText("Bảng xếp hạng chưa sẵn sàng");
                currentUserName.setText(repository.getCurrentNickname());
                currentUserRank.setText("Không tải được bảng xếp hạng");
                currentUserTrend.setText(message);
            }
        });
    }

    private void bindPodium(List<LeaderboardEntry> entries) {
        bindPodiumEntry(entries, 0, podiumRankFirst, podiumAvatarFirst, podiumNameFirst, podiumScoreFirst);
        bindPodiumEntry(entries, 1, podiumRankSecond, podiumAvatarSecond, podiumNameSecond, podiumScoreSecond);
        bindPodiumEntry(entries, 2, podiumRankThird, podiumAvatarThird, podiumNameThird, podiumScoreThird);
    }

    private void bindPodiumEntry(List<LeaderboardEntry> entries, int index, TextView rankView, ImageView avatarView, TextView nameView, TextView scoreView) {
        if (entries.size() > index) {
            LeaderboardEntry entry = entries.get(index);
            rankView.setText("#" + entry.getRank());
            nameView.setText(entry.getNickname());
            loadAvatar(avatarView, entry.getAvatarUrl());
            scoreView.setText(numberFormat.format(entry.getScore()) + " điểm");
        } else {
            rankView.setText("#" + (index + 1));
            loadAvatar(avatarView, "");
            nameView.setText("Đang chờ");
            scoreView.setText("Chưa có điểm");
        }
    }

    private void setToggleState(boolean weekly) {
        weeklyTab.setBackgroundResource(weekly ? R.drawable.bg_segmented_selected : android.R.color.transparent);
        allTimeTab.setBackgroundResource(weekly ? android.R.color.transparent : R.drawable.bg_segmented_selected);
        weeklyTab.setTextColor(requireContext().getColor(weekly ? R.color.gh_text_primary : R.color.gh_text_secondary));
        allTimeTab.setTextColor(requireContext().getColor(weekly ? R.color.gh_text_secondary : R.color.gh_text_primary));
    }

    private void bindCurrentUserAvatars() {
        String cachedAvatarUrl = repository.getCachedAvatarUrl();
        loadAvatar(headerAvatar, cachedAvatarUrl);
        loadAvatar(currentUserAvatar, cachedAvatarUrl);
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
                loadAvatar(headerAvatar, user.getAvatarUrl());
                loadAvatar(currentUserAvatar, user.getAvatarUrl());
                if (currentUserName != null && (currentUserName.getText() == null || currentUserName.getText().length() == 0 || "Bạn".contentEquals(currentUserName.getText()))) {
                    currentUserName.setText(user.getNickname());
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                loadAvatar(headerAvatar, "");
                loadAvatar(currentUserAvatar, "");
            }
        });
    }

    private void loadAvatar(ImageView imageView, @Nullable String url) {
        if (imageView == null || !isAdded()) {
            return;
        }
        String optimizedUrl = optimizeAvatarUrl(url);
        String cacheKey = optimizedUrl.isEmpty() ? "__fallback__" : optimizedUrl;
        Object currentTag = imageView.getTag();
        if (cacheKey.equals(currentTag)) {
            return;
        }
        imageView.setTag(cacheKey);
        Glide.with(this)
                .load(optimizedUrl.isEmpty() ? R.drawable.img_avatar_cat : optimizedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(imageView);
    }

    private String optimizeAvatarUrl(@Nullable String url) {
        String optimizedUrl = url == null ? "" : url.trim();
        if (optimizedUrl.contains("/svg")) {
            optimizedUrl = optimizedUrl.replace("/svg", "/png");
        } else if (optimizedUrl.endsWith(".svg")) {
            optimizedUrl = optimizedUrl.replace(".svg", ".png");
        }
        return optimizedUrl;
    }
}
