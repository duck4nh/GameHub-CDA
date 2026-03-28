package com.example.gamehub.games.memory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.R;

import java.util.ArrayList;
import java.util.List;

public class MemoryBoardAdapter extends RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder> {
    public interface OnCardClickListener {
        void onCardClicked(int position);
    }

    private final List<MemoryCard> items = new ArrayList<>();
    private final OnCardClickListener onCardClickListener;

    public MemoryBoardAdapter(OnCardClickListener onCardClickListener) {
        this.onCardClickListener = onCardClickListener;
    }

    public void submitList(List<MemoryCard> cards) {
        items.clear();
        if (cards != null) {
            items.addAll(cards);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory_card, parent, false);
        int spanCount = 4;
        RecyclerView.LayoutManager layoutManager = parent instanceof RecyclerView ? ((RecyclerView) parent).getLayoutManager() : null;
        if (layoutManager instanceof androidx.recyclerview.widget.GridLayoutManager) {
            spanCount = ((androidx.recyclerview.widget.GridLayoutManager) layoutManager).getSpanCount();
        }
        int parentWidth = parent.getMeasuredWidth();
        if (parentWidth > 0) {
            int size = Math.max(40, (parentWidth / spanCount) - 10);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
            params.height = size;
            view.setLayoutParams(params);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MemoryCard card = items.get(position);
        boolean visible = card.revealed || card.matched;
        holder.labelView.setText(visible ? "\u25CF" : "");
        holder.labelView.setTextSize(visible ? 28f : 18f);
        holder.labelView.setBackgroundResource(card.matched ? R.drawable.bg_card_success_22 : (visible ? R.drawable.bg_card_brand_22 : R.drawable.bg_card_surface_22));
        holder.itemView.setOnClickListener(v -> onCardClickListener.onCardClicked(holder.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public MemoryCard getItem(int position) {
        return items.get(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView labelView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            labelView = itemView.findViewById(R.id.memory_card_label);
        }
    }
}
