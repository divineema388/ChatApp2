package com.modern.lanchat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.modern.lanchat.databinding.ActivityChatBinding;
// We will create these model and adapter classes next
import com.modern.lanchat.model.ChatMessage;
import com.modern.lanchat.ui.MessageAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// TODO: For actual networking, import socket, IO, and threading classes.
// Example:
// import java.io.BufferedReader;
// import java.io.IOException;
// import java.io.InputStreamReader;
// import java.io.PrintWriter;
// import java.net.ServerSocket;
// import java.net.Socket;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;


public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private MessageAdapter messageAdapter;
    private List<ChatMessage> chatMessages;

    private String roomNumber;
    private boolean isHost;
    private String currentUserName;

    // Handler for posting messages to the UI thread from background threads
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // TODO: Networking components
    // private ExecutorService executorService; // For running network operations in background
    // private ServerSocket serverSocket; // If host
    // private Socket clientSocket; // If client, or for each client connection if host
    // private PrintWriter outputWriter;
    // private List<PrintWriter> clientOutputWriters; // If host, to broadcast to multiple clients

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = getIntent();
        roomNumber = intent.getStringExtra(MainActivity.EXTRA_ROOM_NUMBER);
        isHost = intent.getBooleanExtra(MainActivity.EXTRA_IS_HOST, false);
        currentUserName = intent.getStringExtra(MainActivity.EXTRA_USER_NAME);

        if (TextUtils.isEmpty(roomNumber) || TextUtils.isEmpty(currentUserName)) {
            Toast.makeText(this, "Error: Room or User info missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Room number or user name is missing.");
            finish(); // Close activity if essential data is missing
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupSendButton();

        // executorService = Executors.newCachedThreadPool(); // Initialize thread pool for network tasks

        if (isHost) {
            setTitle(getString(R.string.title_activity_chat_room, roomNumber) + " (Host)");
            // TODO: Start server and listen for client connections
            // startServer();
            // For now, add a system message indicating hosting
            addSystemMessage("You created and are hosting room: " + roomNumber);
        } else {
            setTitle(getString(R.string.title_activity_chat_room, roomNumber) + " (Client)");
            // TODO: Connect to the host
            // connectToHost();
            // For now, add a system message indicating joining
            addSystemMessage("You joined room: " + roomNumber);
        }

        // Placeholder to simulate receiving a message after a delay
        // Remove this in actual implementation
        // simulateReceivingMessage();
    }

    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupRecyclerView() {
        chatMessages = new ArrayList<>();
        messageAdapter = new MessageAdapter(chatMessages, currentUserName); // Pass current user's name
        binding.recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewMessages.setAdapter(messageAdapter);
    }

    private void setupSendButton() {
        binding.buttonSend.setOnClickListener(v -> {
            String messageText = binding.editTextMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(messageText)) {
                sendMessage(messageText);
                binding.editTextMessage.setText(""); // Clear input field
            }
        });
    }

    private void sendMessage(String messageText) {
        String timestamp = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        ChatMessage message = new ChatMessage(messageText, currentUserName, timestamp, true); // true for isSentByMe

        // Add to local UI immediately
        addMessageToUI(message);

        // TODO: Send the message over the network
        // if (isHost) {
        //     broadcastMessageToClients(currentUserName + ": " + messageText);
        // } else { // Client
        //     if (outputWriter != null) {
        //         executorService.execute(() -> outputWriter.println(currentUserName + ": " + messageText));
        //     }
        // }
        Log.d(TAG, "Sending message: " + messageText);
    }

    // This method will be called when a message is received from the network
    private void onMessageReceived(String senderName, String messageText) {
        // Ensure this runs on the UI thread
        uiHandler.post(() -> {
            String timestamp = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            ChatMessage message = new ChatMessage(messageText, senderName, timestamp, false); // false for isSentByMe
            addMessageToUI(message);
        });
    }

    // Adds a message (sent or received or system) to the RecyclerView
    private void addMessageToUI(ChatMessage message) {
        chatMessages.add(message);
        messageAdapter.notifyItemInserted(chatMessages.size() - 1);
        binding.recyclerViewMessages.scrollToPosition(chatMessages.size() - 1); // Scroll to the bottom
    }

    // For system messages like "User X joined"
    private void addSystemMessage(String text) {
        ChatMessage systemMessage = new ChatMessage(text, ChatMessage.SENDER_SYSTEM, "", false, true);
        addMessageToUI(systemMessage);
    }


    // --- TODO: Placeholder Network Methods ---
    /*
    private void startServer() {
        // Show progress bar
        binding.progressBarChat.setVisibility(View.VISIBLE);
        // clientOutputWriters = new ArrayList<>();
        // executorService.execute(() -> {
        //     try {
        //         serverSocket = new ServerSocket(YOUR_CHOSEN_PORT); // Use a specific port, maybe derived from roomNumber or a fixed one
        //         Log.i(TAG, "Server started on port: " + serverSocket.getLocalPort());
        //         uiHandler.post(() -> {
        //             binding.progressBarChat.setVisibility(View.GONE);
        //             Toast.makeText(ChatActivity.this, "Hosting on port: " + serverSocket.getLocalPort(), Toast.LENGTH_SHORT).show();
        //         });
        //
        //         while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
        //             try {
        //                 Socket client = serverSocket.accept();
        //                 Log.i(TAG, "Client connected: " + client.getInetAddress());
        //                 // Handle new client connection in a new thread
        //                 handleClient(client);
        //             } catch (IOException e) {
        //                 if (serverSocket.isClosed()) {
        //                     Log.i(TAG, "Server socket closed, exiting accept loop.");
        //                     break;
        //                 }
        //                 Log.e(TAG, "Error accepting client connection", e);
        //             }
        //         }
        //     } catch (IOException e) {
        //         Log.e(TAG, "Could not start server", e);
        //         uiHandler.post(() -> {
        //             binding.progressBarChat.setVisibility(View.GONE);
        //             Toast.makeText(ChatActivity.this, R.string.error_failed_to_create_room, Toast.LENGTH_SHORT).show();
        //             finish();
        //         });
        //     } finally {
        //         Log.d(TAG, "Server thread finishing.");
        //     }
        // });
    }

    private void handleClient(final Socket clientSocket) {
        // executorService.execute(() -> {
        //     try {
        //         BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        //         PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
        //         synchronized (clientOutputWriters) {
        //             clientOutputWriters.add(writer);
        //         }
        //
        //         // TODO: Announce new user to others. First message from client could be their name.
        //         // For now, let's assume first line is name
        //         String clientName = reader.readLine(); // Blocking call
        //         if (clientName != null) {
        //             broadcastSystemMessage(clientName + " joined the chat.");
        //             onMessageReceived(ChatMessage.SENDER_SYSTEM, clientName + " joined the chat."); // Also show on host
        //         } else {
        //              // Handle client disconnecting before sending name
        //         }
        //
        //         String line;
        //         while ((line = reader.readLine()) != null) {
        //             Log.d(TAG, "Received from client " + (clientName != null ? clientName : clientSocket.getInetAddress()) + ": " + line);
        //             // Message format could be "SenderName: Actual Message"
        //             // For simplicity now, assume line is "SenderName: Message"
        //             // In a better protocol, sender name might be part of a JSON object
        //             // or sent once upon connection.
        //             int separatorIndex = line.indexOf(":");
        //             String sender = (separatorIndex > 0) ? line.substring(0, separatorIndex) : "Unknown";
        //             String msg = (separatorIndex > 0) ? line.substring(separatorIndex + 1).trim() : line;
        //
        //             onMessageReceived(sender, msg);
        //             broadcastMessageToClients(line, writer); // Broadcast to other clients (not back to sender)
        //         }
        //     } catch (IOException e) {
        //         Log.e(TAG, "Error handling client: " + clientSocket.getInetAddress(), e);
        //     } finally {
        //         // Clean up: remove writer, close socket
        //         // synchronized (clientOutputWriters) { clientOutputWriters.remove(writer); } // Need the actual writer object
        //         try {
        //             clientSocket.close();
        //         } catch (IOException e) {
        //             Log.e(TAG, "Error closing client socket", e);
        //         }
        //         // TODO: Announce user left
        //         // broadcastSystemMessage( (clientName != null ? clientName : "A user") + " left the chat.");
        //         // onMessageReceived(ChatMessage.SENDER_SYSTEM, (clientName != null ? clientName : "A user") + " left the chat.");
        //     }
        // });
    }

    private void broadcastMessageToClients(String message, PrintWriter excludeWriter) {
        // synchronized (clientOutputWriters) {
        //     for (PrintWriter writer : clientOutputWriters) {
        //         if (writer != excludeWriter) { // Don't send message back to original sender via this broadcast
        //            writer.println(message);
        //         }
        //     }
        // }
    }

    private void broadcastSystemMessage(String message) {
        // Add to host's UI
        // onMessageReceived(ChatMessage.SENDER_SYSTEM, message);
        // Send to all clients
        // synchronized (clientOutputWriters) {
        //     for (PrintWriter writer : clientOutputWriters) {
        //         writer.println(ChatMessage.SENDER_SYSTEM + ": " + message);
        //     }
        // }
    }


    private void connectToHost() {
        // Show progress bar
        binding.progressBarChat.setVisibility(View.VISIBLE);
        // executorService.execute(() -> {
        //     try {
        //         // TODO: Get host IP address. This is a major missing piece.
        //         // For LAN, Network Service Discovery (NSD) is needed to find the host
        //         // based on roomNumber. For now, you might hardcode an IP for testing.
        //         String hostIp = "192.168.1.YOUR_HOST_IP_HERE"; // Placeholder
        //         int port = YOUR_CHOSEN_PORT; // Same port as server
        //
        //         clientSocket = new Socket(hostIp, port);
        //         outputWriter = new PrintWriter(clientSocket.getOutputStream(), true);
        //         BufferedReader inputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        //
        //         Log.i(TAG, "Connected to host: " + hostIp);
        //         uiHandler.post(() -> {
        //             binding.progressBarChat.setVisibility(View.GONE);
        //             Toast.makeText(ChatActivity.this, R.string.connected_to_room, Toast.LENGTH_SHORT).show();
        //             // Send own name to server upon connection
        //             outputWriter.println(currentUserName); // Simple protocol: first line is name
        //         });
        //
        //         // Start listening for messages from server
        //         String line;
        //         while ((line = inputReader.readLine()) != null) {
        //             Log.d(TAG, "Received from server: " + line);
        //             // Parse message: "SenderName: Actual Message" or "SYSTEM: System Message"
        //             int separatorIndex = line.indexOf(":");
        //             if (separatorIndex > 0) {
        //                 String sender = line.substring(0, separatorIndex);
        //                 String msg = line.substring(separatorIndex + 1).trim();
        //                 onMessageReceived(sender, msg);
        //             } else {
        //                 onMessageReceived("Server", line); // Fallback
        //             }
        //         }
        //     } catch (IOException e) {
        //         Log.e(TAG, "Error connecting to host or during communication", e);
        //         uiHandler.post(() -> {
        //             binding.progressBarChat.setVisibility(View.GONE);
        //             Toast.makeText(ChatActivity.this, R.string.error_failed_to_join_room, Toast.LENGTH_LONG).show();
        //             finish(); // Close activity on connection failure
        //         });
        //     } finally {
        //         // Clean up clientSocket, outputWriter, inputReader
        //         try {
        //             if (outputWriter != null) outputWriter.close();
        //             if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        //         } catch (IOException e) {
        //             Log.e(TAG, "Error closing client resources", e);
        //         }
        //         Log.d(TAG, "Client connection thread finishing.");
        //         uiHandler.post(() -> {
        //              if (!isFinishing()) { // Check if activity is already finishing
        //                  addSystemMessage(getString(R.string.message_disconnected));
        //              }
        //         });
        //     }
        // });
    }
    */
    // --- End of Placeholder Network Methods ---

    private void simulateReceivingMessage() {
        // Simulate receiving a message from "OtherUser" after a few seconds
        // REMOVE THIS in actual implementation
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) { // Check if activity is still active
                onMessageReceived("OtherUser", "Hello from the other side!");
            }
        }, 5000);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) {
                addSystemMessage("TestUser joined the chat.");
            }
        }, 7000);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // TODO: Properly disconnect and inform others before finishing
            // For now, just finish
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called. Cleaning up chat resources.");
        addSystemMessage("You left the chat."); // Local message

        // TODO: Gracefully close network connections and threads
        // if (executorService != null) {
        //     executorService.shutdownNow(); // Attempt to stop all actively executing tasks
        // }
        // try {
        //     if (serverSocket != null && !serverSocket.isClosed()) {
        //         serverSocket.close();
        //         Log.i(TAG, "Server socket closed.");
        //     }
        //     if (clientSocket != null && !clientSocket.isClosed()) {
        //         clientSocket.close();
        //         Log.i(TAG, "Client socket closed.");
        //     }
        //     if (outputWriter != null) {
        //         outputWriter.close();
        //     }
        //     // if (clientOutputWriters != null) {
        //     //     for (PrintWriter writer : clientOutputWriters) {
        //     //         writer.close();
        //     //     }
        //     //     clientOutputWriters.clear();
        //     // }
        // } catch (IOException e) {
        //     Log.e(TAG, "Error closing network resources", e);
        // }
        // uiHandler.removeCallbacksAndMessages(null); // Clean up handler
    }
}