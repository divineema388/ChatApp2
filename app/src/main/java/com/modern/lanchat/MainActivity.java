package com.modern.lanchat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider; // We'll add ViewModel later for better state management

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.modern.lanchat.databinding.ActivityMainBinding;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding; // Using ViewBinding

    public static final String PREFS_NAME = "LanChatPrefs";
    public static final String KEY_USER_NAME = "userName";
    public static final String EXTRA_ROOM_NUMBER = "roomNumber";
    public static final String EXTRA_IS_HOST = "isHost";
    public static final String EXTRA_USER_NAME = "userName"; // To pass to ChatActivity

    private String userName;
    private String currentHostingRoom = null;

    // We'll introduce a ViewModel later for handling network discovery and state.
    // For now, basic logic will be in the activity.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadUserName();
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
        } else {
            // User name already exists, good to go.
            // Potentially display a welcome message or user's name somewhere.
        }

        displayIpAddress();

        binding.buttonCreateRoom.setOnClickListener(v -> handleCreateRoom());
        binding.buttonJoinRoom.setOnClickListener(v -> handleJoinRoom());

        // Initially hide elements related to room discovery
        binding.textViewDiscoveredRoomsLabel.setVisibility(View.GONE);
        binding.recyclerViewDiscoveredRooms.setVisibility(View.GONE);
        binding.textViewNoRooms.setVisibility(View.GONE); // Or set to visible if discovery is on by default

        // TODO: Initialize RecyclerView for discovered rooms later
        // TODO: Start network service discovery (NSD) for rooms
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
        final EditText editTextName = dialogView.findViewById(R.id.editTextDialogName); // We need to create this layout

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_enter_name)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    String name = editTextName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(MainActivity.this, R.string.error_name_required, Toast.LENGTH_SHORT).show();
                        promptForUserName(); // Re-prompt
                    } else {
                        saveUserName(name);
                        // You might want to update UI here if needed, e.g., display user's name
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
                    Log.w(TAG, "IP Address is 0. Wi-Fi might not be connected or IP not assigned.");
                } else {
                    String ip = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                            (ipAddress & 0xff),
                            (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff),
                            (ipAddress >> 24 & 0xff));
                    binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, ip));
                }
            } else {
                binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, "N/A (WifiInfo null)"));
                Log.w(TAG, "WifiInfo is null.");
            }
        } else {
            binding.textViewIpAddress.setText(getString(R.string.label_your_ip_address, "N/A (WifiManager null)"));
            Log.e(TAG, "WifiManager is null.");
        }
    }

    private void handleCreateRoom() {
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
            Toast.makeText(this, "Please enter your name first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentHostingRoom != null) {
            Toast.makeText(this, "You are already hosting room: " + currentHostingRoom + ". Stop it first to create a new one.", Toast.LENGTH_LONG).show();
            // TODO: Add a button/logic to stop hosting
            return;
        }

        // Generate a simple room number (e.g., 5 digits)
        // For a real app, ensure uniqueness on the network or use a more robust system.
        String roomNumber = String.valueOf(10000 + new Random().nextInt(90000));
        currentHostingRoom = roomNumber;

        // TODO: Start server socket to listen for incoming connections for this room.
        // TODO: Start advertising this room via Network Service Discovery (NSD).

        binding.textViewHostingStatus.setText(getString(R.string.label_hosting_room, roomNumber));
        binding.textViewHostingStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Room " + roomNumber + " created. Share this number.", Toast.LENGTH_LONG).show();
        Log.i(TAG, "Created room: " + roomNumber);

        // For now, let's directly navigate to ChatActivity as host
        navigateToChatActivity(roomNumber, true);
    }

    private void handleJoinRoom() {
        if (TextUtils.isEmpty(userName)) {
            promptForUserName();
            Toast.makeText(this, "Please enter your name first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String roomNumber = binding.editTextRoomNumber.getText().toString().trim();
        if (TextUtils.isEmpty(roomNumber)) {
            binding.textInputLayoutRoomNumber.setError(getString(R.string.error_invalid_room_number));
            return;
        } else {
            binding.textInputLayoutRoomNumber.setError(null); // Clear error
        }

        // TODO: Attempt to connect to the host of the given roomNumber.
        // This will involve resolving the host's IP (e.g., via NSD or manual IP entry)
        // and then establishing a client socket connection.

        Log.i(TAG, "Attempting to join room: " + roomNumber);
        Toast.makeText(this, getString(R.string.connecting_to_room, roomNumber), Toast.LENGTH_SHORT).show();

        // For now, simulate joining and navigate to ChatActivity as client
        // In a real app, this navigation would happen after a successful connection.
        navigateToChatActivity(roomNumber, false);
    }

    private void navigateToChatActivity(String roomNumber, boolean isHost) {
        Intent intent = new Intent(this, ChatActivity.class); // We'll create ChatActivity.java next
        intent.putExtra(EXTRA_ROOM_NUMBER, roomNumber);
        intent.putExtra(EXTRA_IS_HOST, isHost);
        intent.putExtra(EXTRA_USER_NAME, userName);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: Stop network services (NSD, server socket if hosting)
        Log.d(TAG, "onDestroy: Cleaning up network resources (TODO)");
        if (currentHostingRoom != null) {
            // This is a placeholder. Actual cleanup is more involved.
            Log.i(TAG, "Stopping hosting for room: " + currentHostingRoom);
            currentHostingRoom = null;
        }
    }

    // TODO: Add methods for starting/stopping room discovery (NSD client)
    // TODO: Add methods for starting/stopping room hosting (NSD service registration, server socket)
}