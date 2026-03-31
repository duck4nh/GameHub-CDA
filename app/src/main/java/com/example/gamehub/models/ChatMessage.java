package com.example.gamehub.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ChatMessage {
    public String message_id;
    public String room_id;
    public String sender_uid;
    public String sender_nickname;
    public String content;
    public long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(String messageId, String senderUid, String senderNickname, String content, long timestamp) {
        this(messageId, "", senderUid, senderNickname, content, timestamp);
    }

    public ChatMessage(String messageId, String roomId, String senderUid, String senderNickname, String content, long timestamp) {
        this.message_id = messageId;
        this.room_id = roomId;
        this.sender_uid = senderUid;
        this.sender_nickname = senderNickname;
        this.content = content;
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

    public long getTimestamp() {
        return timestamp;
    }
}
