package com.modern.lanchat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.modern.lanchat.databinding.ActivityChatBinding;
import com.modern.lanchat.db.AppDatabase;
import com.modern.lanchat.db.ChatMessageDao;
import com.modern.lanchat.model.ChatMessage;
import com.modern.lanchat.ui.MessageAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final String MSG_DELIMITER = ":"; // For "SENDER:MESSAGE"
    private static final String HIST_PREFIX = "HIST" + MSG_DELIMITER;
    private static final String HIST_END_MARKER = "HIST_END";
    private static final String CLIENT_NAME_PREFIX = "CLIENT_NAME" + MSG_DELIMITER;
    private static final String ROOM_CLOSED_MSG = "SYSTEM" + MSG_DELIMITER + "ROOM_CLOSED_BY_HOST";

    private ActivityChatBinding binding;
    private MessageAdapter messageAdapter;
    private List<ChatMessage> chatMessages;

    private String roomNumber;
    private boolean isHost;
    private String currentUserName;
    private String hostIpAddress; // For client
    private int hostPort;         // For both host and client

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private ExecutorService networkExecutorService;
    private ExecutorService dbExecutorService;

    // Host specific
    private ServerSocket serverSocket;
    private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>(); // Thread-safe list

    // Client specific
    private Socket clientSocket;
    private PrintWriter clientOutputWriter;
    private BufferedReader clientInputReader;

    // Database
    private ChatMessageDao chatMessageDao;
    private boolean roomHasBeenTerminated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        networkExecutorService = Executors.newCachedThreadPool();
        dbExecutorService = Executors.newSingleThreadExecutor();

        Intent intent = getIntent();
        roomNumber = intent.getStringExtra(MainActivity.EXTRA_ROOM_NUMBER);
        isHost = intent.getBooleanExtra(MainActivity.EXTRA_IS_HOST, false);
        currentUserName = intent.getStringExtra(MainActivity.EXTRA_USER_NAME);
        hostPort = intent.getIntExtra(MainActivity.EXTRA_HOST_PORT, 0); // Important for both

        if (TextUtils.isEmpty(roomNumber) || TextUtils.isEmpty(currentUserName) || hostPort == 0) {
            Toast.makeText(this, "Error: Room/User/Port info missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupSendButton();

        if (isHost) {
            setTitle(getString(R.string.title_activity_chat_room, roomNumber) + " (Host)");
            chatMessageDao = AppDatabase.getDatabase(getApplicationContext()).chatMessageDao();
            loadChatHistoryFromDb();
            startServer();
        } else {
            hostIpAddress = intent.getStringExtra(MainActivity.EXTRA_HOST_IP);
            if (TextUtils.isEmpty(hostIpAddress)) {
                Toast.makeText(this, "Error: Host IP missing for client.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            setTitle(getString(R.string.title_activity_chat_room, roomNumber) + " (Client)");
            connectToHost();
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbarChat); // Assuming you have a Toolbar with id 'toolbarChat' in activity_chat.xml
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupRecyclerView() {
        chatMessages = new ArrayList<>();
        messageAdapter = new MessageAdapter(chatMessages, currentUserName);
        binding.recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewMessages.setAdapter(messageAdapter);
    }

    private void setupSendButton() {
        binding.buttonSend.setOnClickListener(v -> {
            String messageText = binding.editTextMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(messageText)) {
                processAndSendMessage(messageText);
                binding.editTextMessage.setText("");
            }
        });
    }

    private void processAndSendMessage(String messageText) {
        String timestamp = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        ChatMessage message = new ChatMessage(roomNumber, messageText, currentUserName, timestamp, false);
        message.setSentByMe(true); // UI: This is from me
        addMessageToUI(true, message); // Add to local UI and save to DB if host

        // Network transmission
        String networkMessage = currentUserName + MSG_DELIMITER + messageText;
        if (isHost) {
            broadcastMessageToClients(networkMessage, null); // null: broadcast to all
        } else { // Client
            if (clientOutputWriter != null) {
                networkExecutorService.execute(() -> clientOutputWriter.println(networkMessage));
            }
        }
    }

    // UI update and DB saving (if host)
    private void addMessageToUI(boolean scrollToBottom, ChatMessage message) {
        // Ensure isSentByMe is correctly set for UI based on who is viewing
        message.setSentByMe(message.getSenderName().equals(currentUserName) ||
                (message.isSystemMessage() && message.getSenderName().equals(ChatMessage.SENDER_SYSTEM)));

        chatMessages.add(message);
        messageAdapter.notifyItemInserted(chatMessages.size() - 1);
        if (scrollToBottom && !chatMessages.isEmpty()) {
            binding.recyclerViewMessages.scrollToPosition(chatMessages.size() - 1);
        }

        if (isHost && chatMessageDao != null && !message.isSystemMessage()) { // Don't save system messages unless desired
            ChatMessage dbMessage = new ChatMessage(); // Create new obj for DB
            dbMessage.roomId = message.getRoomId();
            dbMessage.messageText = message.getMessageText();
            dbMessage.senderName = message.getSenderName();
            dbMessage.timestamp = message.getTimestamp();
            dbMessage.isSystemMessage = message.isSystemMessage();
            dbExecutorService.execute(() -> chatMessageDao.insertMessage(dbMessage));
        }
    }

    private void addSystemMessageToUI(String text) {
        String timestamp = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        ChatMessage systemMessage = new ChatMessage(roomNumber, text, ChatMessage.SENDER_SYSTEM, timestamp, true);
        addMessageToUI(true, systemMessage);
    }

    // Called when a message is received from network (by host or client from its input stream)
    private void onNetworkMessageReceived(String rawMessage) {
        Log.d(TAG, (isHost ? "Host" : "Client") + " received raw: " + rawMessage);
        if (rawMessage == null) return;

        uiHandler.post(() -> {
            if (rawMessage.startsWith(HIST_PREFIX)) {
                // Client receiving history
                String histContent = rawMessage.substring(HIST_PREFIX.length());
                String[] parts = histContent.split(MSG_DELIMITER, 3); // sender, timestamp, text
                if (parts.length == 3) {
                    ChatMessage histMsg = new ChatMessage(roomNumber, parts[2], parts[0], parts[1], false);
                    addMessageToUI(false, histMsg); // Don't scroll for each history item
                }
            } else if (rawMessage.equals(HIST_END_MARKER) && !isHost) {
                // Client: History sending finished
                if (!chatMessages.isEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(chatMessages.size() - 1);
                }
                Toast.makeText(this, "Chat history loaded.", Toast.LENGTH_SHORT).show();
            } else if (rawMessage.equals(ROOM_CLOSED_MSG) && !isHost) {
                Toast.makeText(ChatActivity.this, "Host has closed the room.", Toast.LENGTH_LONG).show();
                cleanupClientResources();
                finish();
            }
            else { // Regular chat message
                String[] parts = rawMessage.split(MSG_DELIMITER, 2);
                if (parts.length == 2) {
                    String senderName = parts[0];
                    String messageText = parts[1];
                    String timestamp = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                    boolean systemMsg = senderName.equals(ChatMessage.SENDER_SYSTEM);

                    ChatMessage message = new ChatMessage(roomNumber, messageText, senderName, timestamp, systemMsg);
                    addMessageToUI(true, message);

                    // If host received a message from a client, broadcast it to other clients
                    if (isHost && !senderName.equals(currentUserName) && !systemMsg) {
                        // The sender ClientHandler should be passed to avoid sending back to origin
                        // This needs ClientHandler to pass itself or its ID. For simplicity, not implemented here.
                        // broadcastMessageToClients(rawMessage, originatingClientHandler);
                    }
                } else {
                     Log.w(TAG, "Received malformed message: " + rawMessage);
                }
            }
        });
    }


    // --- Host Methods ---
    private void startServer() {
        binding.progressBarChat.setVisibility(View.VISIBLE);
        networkExecutorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(hostPort);
                uiHandler.post(() -> {
                    binding.progressBarChat.setVisibility(View.GONE);
                    Toast.makeText(ChatActivity.this, "Hosting on port: " + hostPort, Toast.LENGTH_SHORT).show();
                    addSystemMessageToUI("You are hosting room " + roomNumber);
                });
                Log.i(TAG, "Server started on port: " + serverSocket.getLocalPort());

                while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        Log.i(TAG, "Client connected: " + client.getInetAddress());
                        ClientHandler clientHandler = new ClientHandler(client);
                        clientHandlers.add(clientHandler);
                        networkExecutorService.execute(clientHandler);
                    } catch (IOException e) {
                        if (serverSocket.isClosed()) {
                            Log.i(TAG, "Server socket closed, exiting accept loop.");
                            break;
                        }
                        Log.e(TAG, "Error accepting client connection", e);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not start server on port " + hostPort, e);
                uiHandler.post(() -> {
                    binding.progressBarChat.setVisibility(View.GONE);
                    Toast.makeText(ChatActivity.this, R.string.error_failed_to_create_room, Toast.LENGTH_SHORT).show();
                    performRoomTermination(false); // Close down
                });
            }
        });
    }

    private void broadcastMessageToClients(String message, ClientHandler excludeHandler) {
        Log.d(TAG, "Host broadcasting: " + message);
        for (ClientHandler handler : clientHandlers) {
            if (handler != excludeHandler) { // Don't send back to the originator if it's a relay
                handler.sendMessage(message);
            }
        }
    }

    private void sendChatHistoryToClient(PrintWriter clientWriter) {
        if (chatMessageDao == null) return;
        dbExecutorService.execute(() -> {
            List<ChatMessage> history = chatMessageDao.getMessagesForRoom(roomNumber);
            if (history != null && !history.isEmpty()) {
                for (ChatMessage msg : history) {
                    if (!msg.isSystemMessage()) { // Typically don't send system messages as history
                        String histMsg = HIST_PREFIX + msg.getSenderName() + MSG_DELIMITER + msg.getTimestamp() + MSG_DELIMITER + msg.getMessageText();
                        clientWriter.println(histMsg);
                    }
                }
            }
            clientWriter.println(HIST_END_MARKER); // Signal end of history
        });
    }

    private void loadChatHistoryFromDb() {
        if (chatMessageDao == null) return;
        dbExecutorService.execute(() -> {
            final List<ChatMessage> history = chatMessageDao.getMessagesForRoom(roomNumber);
            uiHandler.post(() -> {
                if (history != null && !history.isEmpty()) {
                    for (ChatMessage msg : history) {
                        addMessageToUI(false, msg);
                    }
                    if (!chatMessages.isEmpty()) {
                         binding.recyclerViewMessages.scrollToPosition(chatMessages.size() - 1);
                    }
                    Toast.makeText(ChatActivity.this, "Chat history loaded", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String clientName = "UnknownUser";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // First message from client should be their name
                String nameMessage = reader.readLine();
                if (nameMessage != null && nameMessage.startsWith(CLIENT_NAME_PREFIX)) {
                    clientName = nameMessage.substring(CLIENT_NAME_PREFIX.length());
                } else {
                    Log.w(TAG, "Client did not send name correctly. Using IP.");
                    clientName = socket.getInetAddress().getHostAddress();
                }
                String joinMsg = clientName + " joined the chat.";
                addSystemMessageToUI(joinMsg); // Host UI
                broadcastMessageToClients(ChatMessage.SENDER_SYSTEM + MSG_DELIMITER + joinMsg, this); // Notify other clients

                sendChatHistoryToClient(writer); // Send history to this new client

                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, "Host received from " + clientName + ": " + line);
                    onNetworkMessageReceived(line); // Process for host UI
                    broadcastMessageToClients(line, this); // Relay to other clients
                }
            } catch (IOException e) {
                Log.e(TAG, "Error handling client " + clientName, e);
            } finally {
                cleanup();
                String leaveMsg = clientName + " left the chat.";
                addSystemMessageToUI(leaveMsg);
                broadcastMessageToClients(ChatMessage.SENDER_SYSTEM + MSG_DELIMITER + leaveMsg, null);
            }
        }

        void sendMessage(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        void cleanup() {
            clientHandlers.remove(this);
            try {
                if (writer != null) writer.close();
                if (reader != null) reader.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client handler resources for " + clientName, e);
            }
        }
    }

    // --- Client Methods ---
    private void connectToHost() {
        binding.progressBarChat.setVisibility(View.VISIBLE);
        networkExecutorService.execute(() -> {
            try {
                Log.i(TAG, "Client attempting to connect to " + hostIpAddress + ":" + hostPort);
                clientSocket = new Socket(hostIpAddress, hostPort);
                clientOutputWriter = new PrintWriter(clientSocket.getOutputStream(), true);
                clientInputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // Send client name to server
                clientOutputWriter.println(CLIENT_NAME_PREFIX + currentUserName);

                uiHandler.post(() -> {
                    binding.progressBarChat.setVisibility(View.GONE);
                    Toast.makeText(ChatActivity.this, "Connected to room " + roomNumber, Toast.LENGTH_SHORT).show();
                    addSystemMessageToUI("You joined room " + roomNumber);
                });

                String lineFromServer;
                while (!Thread.currentThread().isInterrupted() && (lineFromServer = clientInputReader.readLine()) != null) {
                    onNetworkMessageReceived(lineFromServer);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "Client: Unknown host " + hostIpAddress, e);
                handleConnectionError("Host not found or invalid IP.");
            } catch (IOException e) {
                Log.e(TAG, "Client: I/O error during connection or communication", e);
                handleConnectionError("Connection error: " + e.getMessage());
            } finally {
                if (clientSocket == null || clientSocket.isClosed()) { // If connection never established or closed by error
                    Log.d(TAG, "Client connection thread finishing due to error or closure.");
                } else { // Connection closed normally by server (e.g. ROOM_CLOSED_MSG) or client leaving
                    Log.d(TAG, "Client connection thread finishing.");
                }
                // Do not call cleanupClientResources here if ROOM_CLOSED_MSG was handled, as it already calls it.
            }
        });
    }

    private void handleConnectionError(String errorMessage) {
        uiHandler.post(() -> {
            binding.progressBarChat.setVisibility(View.GONE);
            Toast.makeText(ChatActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            if (!roomHasBeenTerminated) { // Avoid finishing if already terminating
                cleanupClientResources();
                finish();
            }
        });
    }


    // --- Menu and Room Management ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_room_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem terminateItem = menu.findItem(R.id.action_terminate_room);
        MenuItem shareItem = menu.findItem(R.id.action_share_room_id);
        if (isHost) {
            if (terminateItem != null) terminateItem.setVisible(true);
            if (shareItem != null) shareItem.setVisible(true);
        } else {
            if (terminateItem != null) terminateItem.setVisible(false);
            if (shareItem != null) shareItem.setVisible(false); // Or allow clients to share too
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            confirmAndLeaveRoom();
            return true;
        } else if (itemId == R.id.action_terminate_room) {
            if (isHost) confirmAndTerminateRoom();
            return true;
        } else if (itemId == R.id.action_share_room_id) {
            shareRoomId();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareRoomId() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_room_text, roomNumber));
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_room_title)));
    }

    private void confirmAndLeaveRoom() {
        String message = isHost ? "Leaving as host will close the room for everyone. Continue?" : "Are you sure you want to leave the room?";
        new MaterialAlertDialogBuilder(this)
            .setTitle("Leave Room?")
            .setMessage(message)
            .setPositiveButton("Leave", (dialog, which) -> {
                if (isHost) {
                    performRoomTermination(false); // false = don't delete history just by leaving
                } else {
                    cleanupClientResources(); // Client leaves
                    finish();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmAndTerminateRoom() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Terminate Room")
            .setMessage("This will close the room for all participants and delete its chat history. Are you sure?")
            .setPositiveButton("Terminate", (dialog, which) -> performRoomTermination(true))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void performRoomTermination(boolean deleteHistory) {
        if (roomHasBeenTerminated) return;
        roomHasBeenTerminated = true;

        addSystemMessageToUI("Host is closing the room...");
        broadcastMessageToClients(ROOM_CLOSED_MSG, null); // Notify clients

        // Stop server and clean up host resources
        cleanupServerResources();

        if (deleteHistory && isHost && chatMessageDao != null) {
            dbExecutorService.execute(() -> {
                chatMessageDao.deleteMessagesForRoom(roomNumber);
                uiHandler.post(() -> Toast.makeText(ChatActivity.this, "Room history deleted.", Toast.LENGTH_SHORT).show());
            });
        }
        Toast.makeText(this, "Room terminated.", Toast.LENGTH_LONG).show();
        MainActivity.hostingRoomId = null; // Signal MainActivity
        finish();
    }

    // --- Cleanup ---
    private void cleanupServerResources() {
        Log.d(TAG, "Cleaning up server resources.");
        for (ClientHandler handler : clientHandlers) {
            handler.cleanup(); // Closes individual client sockets and streams
        }
        clientHandlers.clear();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.i(TAG, "Server socket closed.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }

    private void cleanupClientResources() {
        Log.d(TAG, "Cleaning up client resources.");
        try {
            if (clientOutputWriter != null) clientOutputWriter.close();
            if (clientInputReader != null) clientInputReader.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                Log.i(TAG, "Client socket closed.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing client resources", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ChatActivity onDestroy. isHost: " + isHost + ", roomTerminated: " + roomHasBeenTerminated);

        if (isHost && !roomHasBeenTerminated) {
            // If host leaves without explicitly terminating (e.g. back button after confirmation)
            // ensure server resources are cleaned up. performRoomTermination handles this.
            // If it was a true termination, resources are already cleaned.
            // If it was just leaving (performRoomTermination(false)), server is also cleaned.
        } else if (!isHost && (clientSocket != null && !clientSocket.isClosed())) {
            // Client is being destroyed, ensure its resources are cleaned
            cleanupClientResources();
        }

        if (networkExecutorService != null && !networkExecutorService.isShutdown()) {
            networkExecutorService.shutdownNow(); // Attempt to stop all tasks
        }
        if (dbExecutorService != null && !dbExecutorService.isShutdown()) {
            dbExecutorService.shutdown();
        }
        uiHandler.removeCallbacksAndMessages(null); // Clean up handler
    }
}