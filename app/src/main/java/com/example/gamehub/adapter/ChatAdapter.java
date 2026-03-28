package com.example.gamehub.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.R;
import com.example.gamehub.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<ChatMessage> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private String currentUid = "";

    public void submitList(List<ChatMessage> messages, String currentUid) {
        items.clear();
        items.addAll(messages);
        this.currentUid = currentUid;
        notifyDataSetChanged();
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

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.bubbleLayout.getLayoutParams();
        params.gravity = isSelf ? Gravity.END : Gravity.START;
        holder.bubbleLayout.setLayoutParams(params);
        holder.bubbleLayout.setBackgroundResource(isSelf ? R.drawable.bg_card_brand_22 : R.drawable.bg_card_surface_22);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout bubbleLayout;
        final TextView metaView;
        final TextView contentView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleLayout = itemView.findViewById(R.id.chat_bubble);
            metaView = itemView.findViewById(R.id.chat_meta);
            contentView = itemView.findViewById(R.id.chat_content);
        }
    }
}
