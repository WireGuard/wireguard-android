package com.wireguard.config;

import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Represents a wg-quick profile.
 */

public class Profile {
    private final Interface iface = new Interface();
    private final String name;
    private final ObservableList<Peer> peers = new ObservableArrayList<>();

    public Profile(String name) {
        this.name = name;
    }

    public Interface getInterface() {
        return iface;
    }

    public String getName() {
        return name;
    }

    public ObservableList<Peer> getPeers() {
        return peers;
    }

    public void parseFrom(InputStream stream)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Peer currentPeer = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("[Interface]")) {
                    currentPeer = null;
                } else if (line.equals("[Peer]")) {
                    currentPeer = new Peer();
                    peers.add(currentPeer);
                } else if (currentPeer == null) {
                    iface.parseFrom(line);
                } else {
                    currentPeer.parseFrom(line);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(iface.toString());
        for (Peer peer : peers)
            sb.append(peer.toString());
        return sb.toString();
    }
}
