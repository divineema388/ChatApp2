package com.modern.lanchat.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages",
        indices = {@Index(value = {"roomId", "id"})}) // Index for querying by room and sorting
public class ChatMessage {

    @PrimaryKey(autoGenerate = true)
    public long id; // Auto-generated primary key (long is better for timestamps if used as primary sort)

    public String roomId; // To associate messages with a specific room
    public String messageText;
    public String senderName;
    public String timestamp; // Keep as String for now, or convert to Long for better sorting
    @Ignore // isSentByMe is a UI-specific flag, determined at runtime
    public boolean isSentByMe;
    public boolean isSystemMessage;

    public static final String SENDER_SYSTEM = "SYSTEM";

    // Default constructor for Room
    public ChatMessage() {}

    // Main constructor for creating new messages before saving
    @Ignore
    public ChatMessage(String roomId, String messageText, String senderName, String timestamp, boolean isSystemMessage) {
        this.roomId = roomId;
        this.messageText = messageText;
        this.senderName = senderName;
        this.timestamp = timestamp; // Consider storing actual long timestamp and format on display
        this.isSystemMessage = isSystemMessage;
        // isSentByMe will be set in the adapter or ChatActivity based on currentUserName
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public boolean isSentByMe() { return isSentByMe; }
    public void setSentByMe(boolean sentByMe) { isSentByMe = sentByMe; }

    public boolean isSystemMessage() { return isSystemMessage; }
    public void setSystemMessage(boolean systemMessage) { isSystemMessage = systemMessage; }
}