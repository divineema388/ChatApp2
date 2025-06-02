package com.modern.lanchat.model;

public class ChatMessage {

    public static final String SENDER_SYSTEM = "SYSTEM"; // Special sender for system messages

    private String messageText;
    private String senderName;
    private String timestamp;
    private boolean isSentByMe; // True if this message was sent by the current user
    private boolean isSystemMessage; // True if this is a system notification

    // Constructor for regular user messages
    public ChatMessage(String messageText, String senderName, String timestamp, boolean isSentByMe) {
        this.messageText = messageText;
        this.senderName = senderName;
        this.timestamp = timestamp;
        this.isSentByMe = isSentByMe;
        this.isSystemMessage = false;
    }

    // Constructor for system messages
    public ChatMessage(String messageText, String senderName, String timestamp, boolean isSentByMe, boolean isSystemMessage) {
        this.messageText = messageText;
        this.senderName = senderName; // Could be SENDER_SYSTEM
        this.timestamp = timestamp; // Often empty or not relevant for system messages
        this.isSentByMe = isSentByMe; // Usually false for system messages shown to user
        this.isSystemMessage = isSystemMessage;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSentByMe() {
        return isSentByMe;
    }

    public void setSentByMe(boolean sentByMe) {
        isSentByMe = sentByMe;
    }

    public boolean isSystemMessage() {
        return isSystemMessage;
    }

    public void setSystemMessage(boolean systemMessage) {
        isSystemMessage = systemMessage;
    }
}