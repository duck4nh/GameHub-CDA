package com.example.gamehub.games.memory;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.R;

import java.util.ArrayList;
import java.util.List;

public class MemoryBoardAdapter extends RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder> {
    public interface OnCardClickListener {
        void onCardClicked(int position);
    }

    private static final String[] PALETTE = {
            "#DDEBFF",
            "#F7EEDC",
            "#ECF8EE",
            "#FFE5D6",
            "#F0E4FF",
            "#E2F7F7",
            "#FFE9F2",
            "#FFF4CC"
    };

    private final List<MemoryCard> items = new ArrayList<>();
    private final OnCardClickListener onCardClickListener;
    private boolean animationsEnabled = true;

    public MemoryBoardAdapter(OnCardClickListener onCardClickListener) {
        this.onCardClickListener = onCardClickListener;
        setHasStableIds(true);
    }

    public void setAnimationsEnabled(boolean animationsEnabled) {
        this.animationsEnabled = animationsEnabled;
    }

    public void submitList(List<MemoryCard> cards) {
        items.clear();
        if (cards != null) {
            items.addAll(cards);
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).cardId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory_card, parent, false);
        int spanCount = resolveSpanCount(parent);
        view.post(() -> {
            int parentWidth = parent.getWidth();
            if (parentWidth <= 0) {
                return;
            }
            int horizontalPadding = parent.getPaddingStart() + parent.getPaddingEnd();
            int spacingAllowance = dpToPx(view, spanCount >= 5 ? 4 : 6);
            int size = Math.max(dpToPx(view, spanCount >= 5 ? 58 : 64), ((parentWidth - horizontalPadding) / spanCount) - spacingAllowance);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
            if (params == null) {
                params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size);
            } else {
                params.height = size;
            }
            view.setLayoutParams(params);
        });
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MemoryCard card = items.get(position);
        boolean showFront = card.revealed || card.matched;
        boolean compactCard = resolveSpanCount(holder.itemView.getParent()) >= 5;

        holder.itemView.animate().cancel();
        holder.labelView.setText(card.label);
        holder.labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactCard ? 24f : 30f);
        holder.metaView.setVisibility(View.GONE);
        applyFrontTint(holder.frontFace, card);

        if (!holder.bound || holder.boundCardId != card.cardId) {
            applyStateImmediately(holder, showFront);
            holder.bound = true;
        } else if (holder.isFrontVisible != showFront && animationsEnabled) {
            animateFlip(holder, showFront);
        } else {
            applyStateImmediately(holder, showFront);
        }
        holder.boundCardId = card.cardId;
        holder.isFrontVisible = showFront;

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onCardClickListener.onCardClicked(adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void applyFrontTint(LinearLayout frontFace, MemoryCard card) {
        int color = card.matched
                ? Color.parseColor("#ECF8EE")
                : Color.parseColor(PALETTE[card.toneIndex % PALETTE.length]);
        ViewCompat.setBackgroundTintList(frontFace, ColorStateList.valueOf(color));
    }

    private void applyStateImmediately(ViewHolder holder, boolean showFront) {
        holder.frontFace.setVisibility(showFront ? View.VISIBLE : View.GONE);
        holder.backFace.setVisibility(showFront ? View.GONE : View.VISIBLE);
        holder.itemView.setRotationY(0f);
        holder.itemView.setAlpha(1f);
    }

    private void animateFlip(ViewHolder holder, boolean showFront) {
        holder.itemView.animate()
                .rotationY(90f)
                .setDuration(110L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        applyStateImmediately(holder, showFront);
                        holder.itemView.setRotationY(-90f);
                        holder.itemView.animate()
                                .rotationY(0f)
                                .setDuration(110L)
                                .setListener(null)
                                .start();
                    }
                })
                .start();
    }

    private int resolveSpanCount(Object parent) {
        if (parent instanceof RecyclerView) {
            RecyclerView.LayoutManager layoutManager = ((RecyclerView) parent).getLayoutManager();
            if (layoutManager instanceof GridLayoutManager) {
                return ((GridLayoutManager) layoutManager).getSpanCount();
            }
        }
        return 4;
    }

    private int dpToPx(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View backFace;
        final LinearLayout frontFace;
        final TextView labelView;
        final TextView metaView;
        boolean bound;
        boolean isFrontVisible;
        long boundCardId = RecyclerView.NO_ID;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            backFace = itemView.findViewById(R.id.memory_card_back_face);
            frontFace = itemView.findViewById(R.id.memory_card_front_face);
            labelView = itemView.findViewById(R.id.memory_card_label);
            metaView = itemView.findViewById(R.id.memory_card_meta);
            itemView.setCameraDistance(itemView.getResources().getDisplayMetrics().density * 1400f);
        }
    }
}
