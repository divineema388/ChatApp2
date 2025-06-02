package com.modern.lanchat.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.modern.lanchat.R;
import com.modern.lanchat.model.DiscoveredRoom;

import java.util.List;
import java.util.Locale;

public class DiscoveredRoomsAdapter extends RecyclerView.Adapter<DiscoveredRoomsAdapter.RoomViewHolder> {

    private final List<DiscoveredRoom> discoveredRoomsList;
    private OnRoomClickListener onRoomClickListener;

    public interface OnRoomClickListener {
        void onRoomClick(DiscoveredRoom room);
    }

    public DiscoveredRoomsAdapter(List<DiscoveredRoom> discoveredRoomsList, OnRoomClickListener listener) {
        this.discoveredRoomsList = discoveredRoomsList;
        this.onRoomClickListener = listener;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discovered_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        DiscoveredRoom room = discoveredRoomsList.get(position);
        holder.bind(room, onRoomClickListener);
    }

    @Override
    public int getItemCount() {
        return discoveredRoomsList.size();
    }

    // Method to update the list of rooms
    public void updateRooms(List<DiscoveredRoom> newRooms) {
        discoveredRoomsList.clear();
        if (newRooms != null) {
            discoveredRoomsList.addAll(newRooms);
        }
        notifyDataSetChanged(); // Or use DiffUtil for better performance with large lists
    }

    public void addRoom(DiscoveredRoom room) {
        // Prevent duplicates based on hostAddress and port (or a unique room ID from NSD)
        for (DiscoveredRoom existingRoom : discoveredRoomsList) {
            if (existingRoom.getHostAddress().equals(room.getHostAddress()) &&
                existingRoom.getHostPort() == room.getHostPort() &&
                existingRoom.getRoomNumber().equals(room.getRoomNumber())) {
                return; // Room already exists
            }
        }
        discoveredRoomsList.add(room);
        notifyItemInserted(discoveredRoomsList.size() - 1);
    }

    public void removeRoom(DiscoveredRoom roomToRemove) {
        int position = -1;
        for (int i = 0; i < discoveredRoomsList.size(); i++) {
            DiscoveredRoom room = discoveredRoomsList.get(i);
            // Matching criteria might need to be more robust depending on NSD service info
            if (room.getHostAddress().equals(roomToRemove.getHostAddress()) &&
                room.getHostPort() == roomToRemove.getHostPort() &&
                room.getRoomNumber().equals(roomToRemove.getRoomNumber())) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            discoveredRoomsList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void clearRooms() {
        discoveredRoomsList.clear();
        notifyDataSetChanged();
    }


    static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView textViewRoomIdentifier;
        TextView textViewHostInfo;

        RoomViewHolder(View itemView) {
            super(itemView);
            textViewRoomIdentifier = itemView.findViewById(R.id.textViewRoomIdentifier);
            textViewHostInfo = itemView.findViewById(R.id.textViewHostInfo);
        }

        void bind(final DiscoveredRoom room, final OnRoomClickListener listener) {
            textViewRoomIdentifier.setText(String.format(Locale.getDefault(),
                    "%s (ID: %s)", room.getRoomName(), room.getRoomNumber()));
            textViewHostInfo.setText(String.format(Locale.getDefault(),
                    "Host: %s (%s:%d)", room.getHostName(), room.getHostAddress(), room.getHostPort()));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRoomClick(room);
                }
            });
        }
    }
}