package com.example.gamehub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.R;
import com.example.gamehub.data.local.entities.LocalHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final List<LocalHistory> items = new ArrayList<>();

    public void submitList(List<LocalHistory> historyItems) {
        items.clear();
        items.addAll(historyItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalHistory item = items.get(position);
        holder.titleView.setText(item.gameName);
        holder.subtitleView.setText(buildSubtitle(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildSubtitle(LocalHistory history) {
        String syncStatus = history.isSynced ? "đã đồng bộ" : "chỉ cục bộ";
        String gameName = history.gameName.toLowerCase(Locale.getDefault());
        if (gameName.contains("đố vui")) {
            return String.format(Locale.getDefault(), "%d/20 · %s · %s", history.score, formatDuration(history.timeSpent), syncStatus);
        }
        if (gameName.contains("ghi nhớ")) {
            return String.format(Locale.getDefault(), "%d lượt · %s", history.score, syncStatus);
        }
        if (gameName.contains("sudoku")) {
            return String.format(Locale.getDefault(), "%s · %s", formatDuration(history.timeSpent), history.isSynced ? "synced" : "cục bộ");
        }
        return String.format(Locale.getDefault(), "%d điểm · %s · %s", history.score, formatDuration(history.timeSpent), syncStatus);
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = durationMillis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView subtitleView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.history_title);
            subtitleView = itemView.findViewById(R.id.history_subtitle);
        }
    }
}
