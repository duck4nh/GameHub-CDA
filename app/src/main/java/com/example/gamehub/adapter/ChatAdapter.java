package com.example.gamehub.adapter;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.R;
import com.example.gamehub.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    public interface MessageActionListener {
        void onMessageLongPressed(ChatMessage message);

        void onReplyPreviewClicked(ChatMessage message);
    }

    private static final String[] REACTION_ORDER = {"👍", "❤️", "😂", "😮"};

    private final List<ChatMessage> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private String currentUid = "";
    @Nullable
    private String highlightedMessageId;
    @Nullable
    private MessageActionListener messageActionListener;

    public void submitList(List<ChatMessage> messages, String currentUid) {
        items.clear();
        items.addAll(messages);
        this.currentUid = currentUid;
        notifyDataSetChanged();
    }

    public void setMessageActionListener(@Nullable MessageActionListener messageActionListener) {
        this.messageActionListener = messageActionListener;
    }

    public int findPositionByMessageId(@Nullable String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return RecyclerView.NO_POSITION;
        }
        for (int i = 0; i < items.size(); i++) {
            if (messageId.equals(items.get(i).getMessageId())) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public void highlightMessage(@Nullable String messageId) {
        String previousHighlightedId = highlightedMessageId;
        highlightedMessageId = messageId;
        int previousPosition = findPositionByMessageId(previousHighlightedId);
        int newPosition = findPositionByMessageId(highlightedMessageId);
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition);
        }
        if (newPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(newPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = items.get(position);
        boolean isSelf = message.getSenderUid().equals(currentUid);
        String senderLabel = isSelf ? "Bạn" : message.getSenderNickname();
        holder.metaView.setText(senderLabel + " · " + timeFormat.format(new Date(message.getTimestamp())));
        holder.contentView.setText(message.getContent());

        FrameLayout.LayoutParams columnParams = (FrameLayout.LayoutParams) holder.messageColumn.getLayoutParams();
        columnParams.gravity = isSelf ? Gravity.END : Gravity.START;
        holder.messageColumn.setLayoutParams(columnParams);
        holder.messageColumn.setGravity(isSelf ? Gravity.END : Gravity.START);
        FrameLayout.LayoutParams bubbleParams = (FrameLayout.LayoutParams) holder.bubbleLayout.getLayoutParams();
        bubbleParams.gravity = isSelf ? Gravity.END : Gravity.START;
        holder.bubbleLayout.setLayoutParams(bubbleParams);
        FrameLayout.LayoutParams replyParams = (FrameLayout.LayoutParams) holder.replyContainer.getLayoutParams();
        replyParams.gravity = isSelf ? Gravity.END : Gravity.START;
        holder.replyContainer.setLayoutParams(replyParams);
        LinearLayout.LayoutParams reactionParams = (LinearLayout.LayoutParams) holder.reactionsContainer.getLayoutParams();
        reactionParams.gravity = isSelf ? Gravity.END : Gravity.START;
        holder.reactionsContainer.setLayoutParams(reactionParams);
        boolean isHighlighted = message.getMessageId().equals(highlightedMessageId);
        if (isSelf) {
            holder.bubbleLayout.setBackgroundResource(isHighlighted ? R.drawable.bg_chat_bubble_self_highlight : R.drawable.bg_chat_bubble_self);
            holder.metaView.setTextColor(holder.itemView.getResources().getColor(R.color.white, null));
            holder.metaView.setAlpha(0.72f);
            holder.contentView.setTextColor(holder.itemView.getResources().getColor(R.color.white, null));
        } else {
            holder.bubbleLayout.setBackgroundResource(isHighlighted ? R.drawable.bg_chat_bubble_other_highlight : R.drawable.bg_chat_bubble_other);
            holder.metaView.setTextColor(holder.itemView.getResources().getColor(R.color.gh_text_secondary, null));
            holder.metaView.setAlpha(1f);
            holder.contentView.setTextColor(holder.itemView.getResources().getColor(R.color.gh_text_primary, null));
        }

        if (message.hasReply()) {
            holder.replyLabelView.setVisibility(View.VISIBLE);
            holder.replyContainer.setVisibility(View.VISIBLE);
            holder.replyContainer.setBackgroundResource(isSelf ? R.drawable.bg_chat_reply_preview_self : R.drawable.bg_chat_reply_preview_other);
            holder.replyContentView.setText(message.getReplyToContent());
            holder.replyContentView.setTextColor(holder.itemView.getResources().getColor(isSelf ? R.color.white : R.color.gh_text_primary, null));
            holder.replyContentView.setAlpha(isSelf ? 0.84f : 0.72f);
            holder.replyLabelView.setText(buildReplyLabel(message, senderLabel, isSelf));
            View.OnClickListener clickListener = v -> {
                if (messageActionListener != null) {
                    messageActionListener.onReplyPreviewClicked(message);
                }
            };
            holder.replyContainer.setOnClickListener(clickListener);
            holder.replyLabelView.setOnClickListener(clickListener);
        } else {
            holder.replyLabelView.setVisibility(View.GONE);
            holder.replyContainer.setVisibility(View.GONE);
            holder.replyContainer.setOnClickListener(null);
            holder.replyLabelView.setOnClickListener(null);
        }

        bindReactionChips(holder, message);

        View.OnLongClickListener longClickListener = v -> {
            if (messageActionListener != null) {
                messageActionListener.onMessageLongPressed(message);
                return true;
            }
            return false;
        };
        holder.itemView.setOnLongClickListener(longClickListener);
        holder.bubbleLayout.setOnLongClickListener(longClickListener);
    }

    private void bindReactionChips(@NonNull ViewHolder holder, ChatMessage message) {
        holder.reactionsContainer.removeAllViews();
        Map<String, Integer> reactionCounts = message.getReactionCounts();
        if (reactionCounts.isEmpty()) {
            holder.reactionsContainer.setVisibility(View.GONE);
            return;
        }

        holder.reactionsContainer.setVisibility(View.VISIBLE);
        String currentReaction = message.getUserReaction(currentUid);
        Set<String> rendered = new HashSet<>();
        for (String emoji : REACTION_ORDER) {
            Integer count = reactionCounts.get(emoji);
            if (count == null || count <= 0) {
                continue;
            }
            rendered.add(emoji);
            holder.reactionsContainer.addView(createReactionChip(holder, emoji, count, emoji.equals(currentReaction)));
        }
        for (Map.Entry<String, Integer> entry : reactionCounts.entrySet()) {
            if (rendered.contains(entry.getKey()) || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            holder.reactionsContainer.addView(createReactionChip(holder, entry.getKey(), entry.getValue(), entry.getKey().equals(currentReaction)));
        }
    }

    private View createReactionChip(@NonNull ViewHolder holder, String emoji, int count, boolean selected) {
        TextView chip = new TextView(holder.itemView.getContext());
        chip.setText(count <= 1 ? emoji : (emoji + " " + count));
        chip.setTextColor(holder.itemView.getResources().getColor(R.color.gh_text_primary, null));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        chip.setPadding(dp(holder, 8), dp(holder, 3), dp(holder, 8), dp(holder, 3));
        chip.setBackgroundResource(selected ? R.drawable.bg_chat_reaction_chip_selected : R.drawable.bg_chat_reaction_chip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(dp(holder, 4));
        chip.setLayoutParams(params);
        return chip;
    }

    private int dp(@NonNull ViewHolder holder, int value) {
        return Math.round(holder.itemView.getResources().getDisplayMetrics().density * value);
    }

    private String buildReplyLabel(ChatMessage message, String senderLabel, boolean isSelf) {
        String replyTarget = message.getReplyToSenderNickname().trim().isEmpty()
                ? "một tin nhắn"
                : message.getReplyToSenderNickname().trim();
        if (isSelf) {
            return "\u21A9 Bạn đã trả lời " + replyTarget;
        }
        return "\u21A9 " + senderLabel + " đã trả lời " + replyTarget;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout messageColumn;
        final TextView replyLabelView;
        final FrameLayout chatStack;
        final LinearLayout bubbleLayout;
        final TextView metaView;
        final LinearLayout replyContainer;
        final TextView replyContentView;
        final TextView contentView;
        final LinearLayout reactionsContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            messageColumn = itemView.findViewById(R.id.chat_message_column);
            replyLabelView = itemView.findViewById(R.id.chat_reply_label);
            chatStack = itemView.findViewById(R.id.chat_stack);
            bubbleLayout = itemView.findViewById(R.id.chat_bubble);
            metaView = itemView.findViewById(R.id.chat_meta);
            replyContainer = itemView.findViewById(R.id.chat_reply_preview);
            replyContentView = itemView.findViewById(R.id.chat_reply_content);
            contentView = itemView.findViewById(R.id.chat_content);
            reactionsContainer = itemView.findViewById(R.id.chat_reactions_container);
        }
    }
}
