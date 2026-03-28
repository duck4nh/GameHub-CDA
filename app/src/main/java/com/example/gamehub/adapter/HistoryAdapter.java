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
        holder.titleView.setText(buildTitle(item));
        holder.subtitleView.setText(buildSubtitle(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildTitle(LocalHistory history) {
        String baseTitle = mapGameTitle(history.gameName);
        if ("memory".equalsIgnoreCase(history.gameName) && history.detail != null && !history.detail.trim().isEmpty()) {
            return baseTitle + " · " + history.detail.trim();
        }
        return baseTitle;
    }

    private String buildSubtitle(LocalHistory history) {
        String syncStatus = history.isSynced ? "đã đồng bộ" : "chỉ cục bộ";
        if ("memory".equalsIgnoreCase(history.gameName)) {
            return String.format(
                    Locale.getDefault(),
                    "%s · %d lượt đoán · %d điểm · %s · %s",
                    mapStatus(history.status),
                    history.attemptCount,
                    history.score,
                    formatDuration(history.timeSpent),
                    syncStatus
            );
        }
        return String.format(
                Locale.getDefault(),
                "%s · %d điểm · %s · %s",
                mapStatus(history.status),
                history.score,
                formatDuration(history.timeSpent),
                syncStatus
        );
    }

    private String mapGameTitle(String gameName) {
        if ("quiz".equalsIgnoreCase(gameName)) {
            return "Đố vui";
        }
        if ("memory".equalsIgnoreCase(gameName)) {
            return "Ghi nhớ";
        }
        if ("sudoku".equalsIgnoreCase(gameName)) {
            return "Sudoku";
        }
        return gameName;
    }

    private String mapStatus(String status) {
        if ("won".equalsIgnoreCase(status)) {
            return "Thắng";
        }
        if ("lost".equalsIgnoreCase(status)) {
            return "Thua";
        }
        if ("completed".equalsIgnoreCase(status)) {
            return "Hoàn thành";
        }
        return status;
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
