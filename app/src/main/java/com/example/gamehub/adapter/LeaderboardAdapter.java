package com.example.gamehub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.R;
import com.example.gamehub.models.LeaderboardEntry;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {
    private final List<LeaderboardEntry> items = new ArrayList<>();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));

    public void submitList(List<LeaderboardEntry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaderboardEntry entry = items.get(position);
        String nickname = entry.getNickname() == null ? "" : entry.getNickname().trim();
        String safeName = nickname.isEmpty() ? "Người chơi" : nickname;
        holder.nameView.setText(safeName);
        holder.scoreView.setText(numberFormat.format(entry.getScore()) + " điểm");
        holder.rankView.setText("#" + entry.getRank());
        holder.itemView.setBackgroundResource(entry.isCurrentUser() ? R.drawable.bg_card_brand_20 : R.drawable.bg_card_surface_20);
        holder.rankView.setBackgroundResource(entry.isCurrentUser() ? R.drawable.bg_stats_value_chip_active : R.drawable.bg_leaderboard_rank_chip);
        holder.iconView.setColorFilter(holder.itemView.getContext().getColor(entry.isCurrentUser() ? R.color.gh_button_primary : R.color.gh_text_secondary));
        holder.iconView.setAlpha(entry.isCurrentUser() ? 1f : 0.68f);
        loadAvatar(holder.avatarView, entry.getAvatarUrl());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatarView;
        final TextView nameView;
        final TextView scoreView;
        final TextView rankView;
        final ImageView iconView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView = itemView.findViewById(R.id.avatar_view);
            nameView = itemView.findViewById(R.id.leaderboard_name);
            scoreView = itemView.findViewById(R.id.leaderboard_score);
            rankView = itemView.findViewById(R.id.leaderboard_rank);
            iconView = itemView.findViewById(R.id.leaderboard_icon);
        }
    }

    private void loadAvatar(ImageView imageView, @Nullable String url) {
        String optimizedUrl = optimizeAvatarUrl(url);
        Glide.with(imageView)
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
