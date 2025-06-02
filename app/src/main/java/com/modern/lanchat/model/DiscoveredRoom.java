package com.modern.lanchat.model;

public class DiscoveredRoom {
    private String roomNumber; // Or a more general room identifier
    private String roomName;   // Optional: a user-friendly name for the room
    private String hostName;   // Name of the user hosting the room
    private String hostAddress; // IP address of the host
    private int hostPort;       // Port number the host is listening on

    public DiscoveredRoom(String roomNumber, String roomName, String hostName, String hostAddress, int hostPort) {
        this.roomNumber = roomNumber;
        this.roomName = roomName;
        this.hostName = hostName;
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
    }

    // Getters
    public String getRoomNumber() {
        return roomNumber;
    }

    public String getRoomName() {
        if (roomName != null && !roomName.isEmpty()) {
            return roomName;
        }
        return "Room " + roomNumber; // Fallback display name
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getHostPort() {
        return hostPort;
    }

    // You might want to override equals() and hashCode() if you store these in Sets or use them as keys in Maps.
    // For now, it's not strictly necessary for basic RecyclerView display.

    @Override
    public String toString() {
        return "DiscoveredRoom{" +
                "roomNumber='" + roomNumber + '\'' +
                ", roomName='" + roomName + '\'' +
                ", hostName='" + hostName + '\'' +
                ", hostAddress='" + hostAddress + '\'' +
                ", hostPort=" + hostPort +
                '}';
    }
}