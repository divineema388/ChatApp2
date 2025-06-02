package com.modern.lanchat.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.modern.lanchat.R;
import com.modern.lanchat.model.ChatMessage;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3; // For system messages

    private final List<ChatMessage> messageList;
    private final String currentUserName;

    public MessageAdapter(List<ChatMessage> messageList, String currentUserName) {
        this.messageList = messageList;
        this.currentUserName = currentUserName;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);

        if (message.isSystemMessage()) {
            return VIEW_TYPE_SYSTEM_MESSAGE;
        } else if (message.getSenderName().equals(currentUserName) || message.isSentByMe()) {
            // Check senderName equality OR the isSentByMe flag for robustness
            return VIEW_TYPE_MESSAGE_SENT;
        } else {
            return VIEW_TYPE_MESSAGE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_MESSAGE_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else if (viewType == VIEW_TYPE_MESSAGE_RECEIVED) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        } else { // VIEW_TYPE_SYSTEM_MESSAGE
            // For system messages, we can create a simple centered text view layout
            // or reuse one of the existing ones if styling is similar.
            // Let's create a new simple layout for system messages.
            // For now, let's make it distinct. We'll need to create item_system_message.xml
             view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_system_message, parent, false); // We'll create this
            return new SystemMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_MESSAGE_SENT:
                ((SentMessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_MESSAGE_RECEIVED:
                ((ReceivedMessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_SYSTEM_MESSAGE:
                ((SystemMessageViewHolder) holder).bind(message);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // ViewHolder for sent messages
    private static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timestampText;
        // TextView senderNameText; // Optional for sent messages

        SentMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textViewMessageText);
            timestampText = itemView.findViewById(R.id.textViewTimestamp);
            // senderNameText = itemView.findViewById(R.id.textViewSenderName); // If you have it in the layout
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getMessageText());
            if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
                timestampText.setText(message.getTimestamp());
                timestampText.setVisibility(View.VISIBLE);
            } else {
                timestampText.setVisibility(View.GONE);
            }
            // if (senderNameText != null) senderNameText.setText(message.getSenderName()); // Usually "You" or hidden
        }
    }

    // ViewHolder for received messages
    private static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timestampText, senderNameText;

        ReceivedMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textViewMessageText);
            timestampText = itemView.findViewById(R.id.textViewTimestamp);
            senderNameText = itemView.findViewById(R.id.textViewSenderName);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getMessageText());
            senderNameText.setText(message.getSenderName());
            if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
                timestampText.setText(message.getTimestamp());
                timestampText.setVisibility(View.VISIBLE);
            } else {
                timestampText.setVisibility(View.GONE);
            }
        }
    }

    // ViewHolder for system messages
    private static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView systemMessageText;

        SystemMessageViewHolder(View itemView) {
            super(itemView);
            systemMessageText = itemView.findViewById(R.id.textViewSystemMessage); // ID in item_system_message.xml
        }

        void bind(ChatMessage message) {
            systemMessageText.setText(message.getMessageText());
        }
    }
}