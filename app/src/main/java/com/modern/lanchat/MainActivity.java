package com.modern.lanchat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.modern.lanchat.databinding.ActivityMainBinding;
import com.modern.lanchat.model.DiscoveredRoom;
import com.modern.lanchat.ui.DiscoveredRoomsAdapter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements DiscoveredRoomsAdapter.OnRoomClickListener {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    public static final String PREFS_NAME = "LanChatPrefs";
    public static final String KEY_USER_NAME = "userName";
    public static final String EXTRA_ROOM_NUMBER = "roomNumber";
    public static final String EXTRA_IS_HOST = "isHost";
    public static final String EXTRA_USER_NAME = "userName";
    public static final String EXTRA_HOST_IP = "hostIp";
    public static final String EXTRA_HOST_PORT = "hostPort";


    private String userName;
    // Simplified state management for hosting status - better with ActivityResultLauncher for real app
    public static String hostingRoomId = null;
    private String currentHostingRoomNumber = null; // Local copy for actual room number being hosted

    // Network Service Discovery (NSD)
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.RegistrationListener registrationListener;
    private String serviceName = null; // NSD service name
    public static final String SERVICE_TYPE = "_lanchat._tcp."; // Your unique service type
    private ServerSocket serverSocket; // For the host to listen
    private int hostPort = 0; // Port the host is listening on

    private List<DiscoveredRoom> discoveredRoomsList;
    private DiscoveredRoomsAdapter discoveredRoomsAdapter;

    // ActivityResultLauncher for getting result from ChatActivity (e.g., if room was terminated)
    private ActivityResultLauncher<Intent> chatActivityLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadUserName();
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
        }

        displayIpAddress();
        setupRecyclerView();

        binding.buttonCreateRoom.setOnClickListener(v -> handleCreateRoom());
        binding.buttonJoinRoom.setOnClickListener(v -> handleJoinRoom());

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        // Initialize ActivityResultLauncher
        chatActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // This callback is invoked when ChatActivity finishes
                // We can check if the room was terminated by the host
                // For simplicity, we are still using the static 'hostingRoomId'
                // but this is where you'd handle a more robust result.
                Log.d(TAG, "ChatActivity finished with result code: " + result.getResultCode());
                // The onResume will handle UI update based on hostingRoomId
            }
        );
    }

    private void loadUserName() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userName = prefs.getString(KEY_USER_NAME, null);
    }

    private void saveUserName(String name) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
        this.userName = name;
    }

    private void promptForUserName() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_enter_name, null);
        final EditText editTextName = dialogView.findViewById(R.id.editTextDialogName);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_enter_name)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    String name = editTextName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(MainActivity.this, R.string.error_name_required, Toast.LENGTH_SHORT).show();
                        promptForUserName();
                    } else {
                        saveUserName(name);
                    }
                })
                .show();
    }

    private void displayIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress == 0) {
                    binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, "N/A (Wi-Fi not connected?)"));
                } else {
                    String ip = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                            (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
                    binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, ip));
                }
            } else {
                 binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, "N/A"));
            }
        } else {
            binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, "N/A"));
        }
    }

    private void setupRecyclerView() {
        discoveredRoomsList = new ArrayList<>();
        discoveredRoomsAdapter = new DiscoveredRoomsAdapter(discoveredRoomsList, this);
        binding.recyclerViewDiscoveredRooms.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewDiscoveredRooms.setAdapter(discoveredRoomsAdapter);
        updateDiscoveredRoomsUI();
    }

    private void updateDiscoveredRoomsUI() {
        if (discoveredRoomsList.isEmpty()) {
            binding.textViewNoRooms.setVisibility(View.VISIBLE);
            binding.recyclerViewDiscoveredRooms.setVisibility(View.GONE);
            binding.textViewDiscoveredRoomsLabel.setText(R.string.label_no_rooms_available);
        } else {
            binding.textViewNoRooms.setVisibility(View.GONE);
            binding.recyclerViewDiscoveredRooms.setVisibility(View.VISIBLE);
            binding.textViewDiscoveredRoomsLabel.setText(R.string.label_available_rooms);
        }
    }

    private void handleCreateRoom() {
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
            Toast.makeText(this, "Please enter your name first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hostingRoomId != null) {
            Toast.makeText(this, "You are already hosting room: " + hostingRoomId + ". Stop it first.", Toast.LENGTH_LONG).show();
            return;
        }

        currentHostingRoomNumber = String.valueOf(10000 + new Random().nextInt(90000));
        hostingRoomId = currentHostingRoomNumber; // Set global static flag

        // Start server socket to get a port
        try {
            serverSocket = new ServerSocket(0); // 0 for dynamically allocated port
            hostPort = serverSocket.getLocalPort();
            Log.i(TAG, "Server socket started on port: " + hostPort);
            // Note: We are NOT yet accepting connections here. ChatActivity will do that.
            // For simplicity, we pass the serverSocket or its port to ChatActivity.
            // A more robust design would have a dedicated network service.

            registerNsdService(hostPort, currentHostingRoomNumber, userName);
            updateHostingUI();
            navigateToChatActivity(currentHostingRoomNumber, true, null, 0);
        } catch (IOException e) {
            Log.e(TAG, "Error starting server socket", e);
            Toast.makeText(this, "Failed to start hosting: " + e.getMessage(), Toast.LENGTH_LONG).show();
            hostingRoomId = null; // Reset if failed
            currentHostingRoomNumber = null;
            updateHostingUI();
        }
    }

    private void updateHostingUI() {
         if (hostingRoomId != null) {
            binding.textViewHostingStatus.setText(getString(R.string.label_hosting_room, hostingRoomId));
            binding.textViewHostingStatus.setVisibility(View.VISIBLE);
            binding.buttonCreateRoom.setEnabled(false); // Disable create if already hosting
        } else {
            binding.textViewHostingStatus.setVisibility(View.GONE);
            binding.buttonCreateRoom.setEnabled(true);
        }
    }


    private void handleJoinRoom() {
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
            Toast.makeText(this, "Please enter your name first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String roomNumToJoin = binding.editTextRoomNumber.getText().toString().trim();
        if (TextUtils.isEmpty(roomNumToJoin)) {
            binding.textInputLayoutRoomNumber.setError(getString(R.string.error_invalid_room_number));
            return;
        }
        binding.textInputLayoutRoomNumber.setError(null);

        // Find the room in the discovered list
        DiscoveredRoom targetRoom = null;
        for (DiscoveredRoom room : discoveredRoomsList) {
            if (room.getRoomNumber().equals(roomNumToJoin)) {
                targetRoom = room;
                break;
            }
        }

        if (targetRoom != null) {
            Log.i(TAG, "Joining discovered room: " + targetRoom.getRoomNumber() + " at " + targetRoom.getHostAddress() + ":" + targetRoom.getHostPort());
            navigateToChatActivity(targetRoom.getRoomNumber(), false, targetRoom.getHostAddress(), targetRoom.getHostPort());
        } else {
            Toast.makeText(this, "Room " + roomNumToJoin + " not found. Discovering or enter valid ID.", Toast.LENGTH_LONG).show();
            // Optionally, you could try a direct connection if IP/Port were manually entered
            // For now, we rely on discovery or exact match if user types.
        }
    }

    @Override
    public void onRoomClick(DiscoveredRoom room) {
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
            Toast.makeText(this, "Please enter your name first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "Clicked to join room: " + room.getRoomNumber() + " hosted by " + room.getHostName());
        Toast.makeText(this, getString(R.string.connecting_to_room, room.getRoomName()), Toast.LENGTH_SHORT).show();
        navigateToChatActivity(room.getRoomNumber(), false, room.getHostAddress(), room.getHostPort());
    }

    private void navigateToChatActivity(String roomNumber, boolean isHost, String hostIp, int port) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(EXTRA_ROOM_NUMBER, roomNumber);
        intent.putExtra(EXTRA_IS_HOST, isHost);
        intent.putExtra(EXTRA_USER_NAME, userName);
        if (!isHost && hostIp != null) {
            intent.putExtra(EXTRA_HOST_IP, hostIp);
            intent.putExtra(EXTRA_HOST_PORT, port);
        }
        // Pass the serverSocket's port if hosting. ChatActivity will manage the socket.
        if (isHost && serverSocket != null) {
            intent.putExtra(EXTRA_HOST_PORT, serverSocket.getLocalPort());
            // ChatActivity will need to take over the serverSocket or create its own.
            // For this example, let's assume ChatActivity creates its own listener on this port.
            // Closing the one here as ChatActivity will re-open it.
            // This is a simplification; a shared service or passing the socket itself would be better.
            try {
                if(serverSocket != null && !serverSocket.isClosed()){
                    // serverSocket.close(); // ChatActivity will take over this port.
                    // Actually, let ChatActivity handle the socket. HostPort is enough.
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing temporary server socket", e);
            }
        }
        chatActivityLauncher.launch(intent);
    }

    // --- NSD Implementation ---
    private void initializeNsdDiscovery() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "NSD Service discovery started: " + regType);
                runOnUiThread(() -> {
                    binding.textViewDiscoveredRoomsLabel.setText("Discovering rooms...");
                    binding.progressBar.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "NSD Service found: " + service.getServiceName() + " type: " + service.getServiceType());
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(MainActivity.this.serviceName)) {
                    Log.d(TAG, "Same machine: " + MainActivity.this.serviceName); // Own service
                } else {
                    nsdManager.resolveService(service, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "NSD Resolve failed for " + serviceInfo.getServiceName() + " Error: " + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            Log.i(TAG, "NSD Service resolved: " + serviceInfo);
                            String roomNum = serviceInfo.getAttributes().get("roomNumber") != null ? new String(serviceInfo.getAttributes().get("roomNumber")) : "N/A";
                            String hostUsername = serviceInfo.getAttributes().get("userName") != null ? new String(serviceInfo.getAttributes().get("userName")) : "Unknown Host";
                            String roomFriendlyName = serviceInfo.getAttributes().get("roomName") != null ? new String(serviceInfo.getAttributes().get("roomName")) : "Room " + roomNum;

                            DiscoveredRoom room = new DiscoveredRoom(
                                    roomNum,
                                    roomFriendlyName, // Can be enhanced
                                    hostUsername,
                                    serviceInfo.getHost().getHostAddress(),
                                    serviceInfo.getPort()
                            );
                            runOnUiThread(() -> {
                                discoveredRoomsAdapter.addRoom(room);
                                updateDiscoveredRoomsUI();
                            });
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "NSD Service lost: " + service.getServiceName());
                // Find and remove the room from the list
                 DiscoveredRoom roomToRemove = null;
                for (DiscoveredRoom r : discoveredRoomsList) {
                    // Matching needs to be careful: service name from NSD might be different from room ID
                    // Best to match on host IP and port if possible, or unique service ID from NSD record.
                    // For now, a simpler match if attributes were stored.
                    // This part needs robust unique identification of services.
                    if (r.getHostAddress().equals(service.getHost().getHostAddress()) && r.getHostPort() == service.getPort()) {
                        roomToRemove = r;
                        break;
                    }
                }
                if (roomToRemove != null) {
                    final DiscoveredRoom finalRoomToRemove = roomToRemove;
                    runOnUiThread(() -> {
                        discoveredRoomsAdapter.removeRoom(finalRoomToRemove);
                        updateDiscoveredRoomsUI();
                    });
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "NSD Discovery stopped: " + serviceType);
                 runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (discoveredRoomsList.isEmpty()) {
                         binding.textViewDiscoveredRoomsLabel.setText(R.string.label_no_rooms_available);
                    }
                });
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "NSD Discovery failed to start: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
                runOnUiThread(() -> {
                     binding.progressBar.setVisibility(View.GONE);
                     Toast.makeText(MainActivity.this, "Failed to start room discovery.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "NSD Discovery failed to stop: Error code:" + errorCode);
                // nsdManager.stopServiceDiscovery(this); // Careful with recursion
            }
        };
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void stopNsdDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error stopping NSD discovery, listener not registered or already unregistered.", e);
            }
            discoveryListener = null;
        }
    }

    private void registerNsdService(int port, String roomNum, String hostUserName) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("LanChatRoom_" + roomNum); // Unique service name
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);
        // Add attributes
        serviceInfo.setAttribute("roomNumber", roomNum);
        serviceInfo.setAttribute("userName", hostUserName);
        // serviceInfo.setAttribute("roomName", "My Awesome Chat Room"); // Optional friendly name

        this.serviceName = serviceInfo.getServiceName(); // Store our own service name

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                MainActivity.this.serviceName = NsdServiceInfo.getServiceName();
                Log.i(TAG, "NSD Service registered: " + MainActivity.this.serviceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "NSD Service registration failed: " + errorCode);
                MainActivity.this.serviceName = null;
                // Handle failure (e.g., inform user, retry)
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.i(TAG, "NSD Service unregistered: " + arg0.getServiceName());
                MainActivity.this.serviceName = null;
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "NSD Service unregistration failed: " + errorCode);
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    private void unregisterNsdService() {
        if (registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering NSD service, listener not registered or already unregistered.", e);
            }
            registrationListener = null;
            this.serviceName = null;
            // Close the server socket that was opened for hosting info
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    serverSocket = null;
                    Log.i(TAG, "Hosting server socket closed during unregistration.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket during NSD unregistration", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayIpAddress(); // Refresh IP
        // Update hosting UI based on static var (simplification)
        if (hostingRoomId == null && currentHostingRoomNumber != null) {
            // This means ChatActivity likely terminated the room
            unregisterNsdService(); // Stop advertising if it was running
            currentHostingRoomNumber = null; // Clear local room number
        }
        updateHostingUI();

        if (hostingRoomId == null) { // Only discover if not currently hosting
            discoveredRoomsAdapter.clearRooms(); // Clear previous results before new discovery
            updateDiscoveredRoomsUI();
            initializeNsdDiscovery();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopNsdDiscovery(); // Stop discovery when activity is not in foreground
        // Note: If hosting, we might want to keep advertising.
        // However, if ChatActivity handles hosting, then MainActivity unregistering here is okay
        // if ChatActivity also unregisters when it's done.
        // If hostingRoomId is not null here, it implies ChatActivity is likely active or will take over.
        // For now, MainActivity will stop discovery. Host advertising is tied to hostingRoomId.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNsdDiscovery(); // Ensure discovery is stopped
        unregisterNsdService(); // Ensure service is unregistered if it was active

        // Close the server socket if it's still open and managed by MainActivity
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.i(TAG, "Server socket closed in onDestroy.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket in onDestroy", e);
        }
    }
}