package com.example.gamehub.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class ChatMessage {
    public String message_id;
    public String room_id;
    public String sender_uid;
    public String sender_nickname;
    public String content;
    public String reply_to_message_id;
    public String reply_to_sender_nickname;
    public String reply_to_content;
    public Map<String, String> reactions_by_uid;
    public long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(String messageId, String senderUid, String senderNickname, String content, long timestamp) {
        this(messageId, "", senderUid, senderNickname, content, timestamp, "", "", "", Collections.emptyMap());
    }

    public ChatMessage(String messageId, String roomId, String senderUid, String senderNickname, String content, long timestamp) {
        this(messageId, roomId, senderUid, senderNickname, content, timestamp, "", "", "", Collections.emptyMap());
    }

    public ChatMessage(
            String messageId,
            String roomId,
            String senderUid,
            String senderNickname,
            String content,
            long timestamp,
            String replyToMessageId,
            String replyToSenderNickname,
            String replyToContent,
            Map<String, String> reactionsByUid
    ) {
        this.message_id = messageId;
        this.room_id = roomId;
        this.sender_uid = senderUid;
        this.sender_nickname = senderNickname;
        this.content = content;
        this.reply_to_message_id = replyToMessageId;
        this.reply_to_sender_nickname = replyToSenderNickname;
        this.reply_to_content = replyToContent;
        this.reactions_by_uid = reactionsByUid;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return message_id;
    }

    public String getRoomId() {
        return room_id == null ? "" : room_id;
    }

    public String getSenderUid() {
        return sender_uid == null ? "" : sender_uid;
    }

    public String getSenderNickname() {
        return sender_nickname == null ? "" : sender_nickname;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public String getReplyToMessageId() {
        return reply_to_message_id == null ? "" : reply_to_message_id;
    }

    public String getReplyToSenderNickname() {
        return reply_to_sender_nickname == null ? "" : reply_to_sender_nickname;
    }

    public String getReplyToContent() {
        return reply_to_content == null ? "" : reply_to_content;
    }

    public boolean hasReply() {
        return !getReplyToMessageId().isEmpty() || !getReplyToContent().isEmpty();
    }

    public Map<String, String> getReactionsByUid() {
        if (reactions_by_uid == null || reactions_by_uid.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(reactions_by_uid);
    }

    public String getUserReaction(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return "";
        }
        String reaction = getReactionsByUid().get(uid);
        return reaction == null ? "" : reaction;
    }

    public Map<String, Integer> getReactionCounts() {
        if (reactions_by_uid == null || reactions_by_uid.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String reaction : reactions_by_uid.values()) {
            if (reaction == null || reaction.trim().isEmpty()) {
                continue;
            }
            counts.put(reaction, counts.getOrDefault(reaction, 0) + 1);
        }
        return counts;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
