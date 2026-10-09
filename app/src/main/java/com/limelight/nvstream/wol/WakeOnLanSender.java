package com.limelight.nvstream.wol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import com.limelight.nvstream.http.ComputerDetails;

public class WakeOnLanSender {
    private static final int[] STATIC_PORTS_TO_TRY = {9, 47009};
    private static final int[] DYNAMIC_PORTS_TO_TRY = {47998, 47999, 48000, 48002, 48010};

    static Set<InetSocketAddress> destinations(InetAddress address, int httpPort) {
        Set<InetSocketAddress> destinations = new LinkedHashSet<>();
        for (int port : STATIC_PORTS_TO_TRY) {
            destinations.add(new InetSocketAddress(address, port));
        }
        for (int port : DYNAMIC_PORTS_TO_TRY) {
            int mappedPort = port - 47989 + httpPort;
            if (mappedPort > 0 && mappedPort <= 65535) {
                destinations.add(new InetSocketAddress(address, mappedPort));
            }
        }
        return destinations;
    }

    static void sendPackets(Set<InetSocketAddress> destinations, DatagramSocket sock, byte[] payload) throws IOException {
        IOException lastException = null;
        boolean sentWolPacket = false;
        // A magic packet has no acknowledgement; a short burst tolerates an isolated UDP loss.
        for (int attempt = 0; attempt < 3; attempt++) {
            for (InetSocketAddress destination : destinations) {
                try {
                    sock.send(new DatagramPacket(payload, payload.length, destination));
                    sentWolPacket = true;
                } catch (IOException e) {
                    lastException = e;
                }
            }
        }
        if (!sentWolPacket) {
            throw lastException != null ? lastException : new IOException("No Wake-on-LAN destinations");
        }
    }

    public static void sendWolPacket(ComputerDetails computer) throws IOException {
        byte[] payload = createWolPayload(computer.macAddress);
        Set<InetAddress> broadcasts = new LinkedHashSet<>();
        broadcasts.add(InetAddress.getByName("255.255.255.255"));
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nic : interfaces == null ? Collections.<NetworkInterface>emptyList() : Collections.list(interfaces)) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (InterfaceAddress address : nic.getInterfaceAddresses()) {
                    if (address.getBroadcast() instanceof Inet4Address) {
                        broadcasts.add(address.getBroadcast());
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            // Restricted interface enumeration must not prevent ordinary broadcast/unicast wake.
            e.printStackTrace();
        }
        Set<InetSocketAddress> targets = new LinkedHashSet<>();
        Set<Integer> ports = new LinkedHashSet<>();
        for (ComputerDetails.AddressTuple address : new ComputerDetails.AddressTuple[] {
                computer.localAddress, computer.remoteAddress, computer.manualAddress, computer.ipv6Address,
        }) {
            if (address == null) continue;
            ports.add(address.port);
            try {
                for (InetAddress resolvedAddress : InetAddress.getAllByName(address.address)) {
                    targets.addAll(destinations(resolvedAddress, address.port));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (ports.isEmpty()) ports.add(47989);
        // Directed broadcasts reach each local subnet and avoid stale ARP entries for sleeping PCs.
        for (InetAddress broadcast : broadcasts) {
            for (int port : ports) targets.addAll(destinations(broadcast, port));
        }
        try (DatagramSocket sock = new DatagramSocket(0)) {
            sock.setBroadcast(true);
            sendPackets(targets, sock, payload);
        }
    }

    static byte[] createWolPayload(String macAddress) throws IOException {
        if (macAddress == null || !macAddress.matches("(?i)[0-9a-f]{2}([:-])[0-9a-f]{2}(\\1[0-9a-f]{2}){4}")) {
            throw new IOException("Invalid Wake-on-LAN MAC address");
        }
        byte[] macBytes = new byte[6];
        String[] octets = macAddress.split("[:-]");
        for (int i = 0; i < macBytes.length; i++) {
            macBytes[i] = (byte) Integer.parseInt(octets[i], 16);
        }
        byte[] payload = new byte[102];
        for (int i = 0; i < 6; i++) payload[i] = (byte) 0xFF;
        for (int i = 6; i < payload.length; i += 6) {
            System.arraycopy(macBytes, 0, payload, i, macBytes.length);
        }
        return payload;
    }
}
