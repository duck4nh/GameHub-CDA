package com.example.gamehub.models;

public class ChatMessage {
    private final String messageId;
    private final String senderUid;
    private final String senderNickname;
    private final String content;
    private final long timestamp;

    public ChatMessage(String messageId, String senderUid, String senderNickname, String content, long timestamp) {
        this.messageId = messageId;
        this.senderUid = senderUid;
        this.senderNickname = senderNickname;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
