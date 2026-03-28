package com.example.gamehub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        holder.avatarBadge.setText(entry.getNickname().substring(0, 1).toUpperCase(Locale.getDefault()));
        holder.nameView.setText(entry.getNickname());
        holder.scoreView.setText(numberFormat.format(entry.getScore()) + " điểm");
        holder.rankView.setText("#" + entry.getRank());
        holder.itemView.setBackgroundResource(entry.isCurrentUser() ? R.drawable.bg_card_brand_20 : R.drawable.bg_card_surface_20);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatarBadge;
        final TextView nameView;
        final TextView scoreView;
        final TextView rankView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarBadge = itemView.findViewById(R.id.avatar_badge);
            nameView = itemView.findViewById(R.id.leaderboard_name);
            scoreView = itemView.findViewById(R.id.leaderboard_score);
            rankView = itemView.findViewById(R.id.leaderboard_rank);
        }
    }
}
