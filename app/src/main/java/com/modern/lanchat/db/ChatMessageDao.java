package com.modern.lanchat.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.modern.lanchat.model.ChatMessage;
import java.util.List;

@Dao
public interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(ChatMessage message);

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY id ASC")
    List<ChatMessage> getMessagesForRoom(String roomId);

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    void deleteMessagesForRoom(String roomId);

    // Optional: for deleting all messages if ever needed
    // @Query("DELETE FROM chat_messages")
    // void deleteAllMessages();
}