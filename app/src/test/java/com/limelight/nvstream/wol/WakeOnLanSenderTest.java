package com.limelight.nvstream.wol;

import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class WakeOnLanSenderTest {
    @Test
    public void magicPacketContainsAllSixteenCopiesAndAcceptsBothSeparators() throws Exception {
        byte[] payload = WakeOnLanSender.createWolPayload("01:23:45:67:89:aB");
        assertEquals(102, payload.length);
        assertArrayEquals(new byte[] {-1, -1, -1, -1, -1, -1}, Arrays.copyOf(payload, 6));
        for (int i = 6; i < payload.length; i += 6) {
            assertArrayEquals(new byte[] {1, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab},
                    Arrays.copyOfRange(payload, i, i + 6));
        }
        assertArrayEquals(payload, WakeOnLanSender.createWolPayload("01-23-45-67-89-AB"));
    }

    @Test
    public void malformedMacNeverProducesAPartialPacket() {
        for (String mac : new String[] {null, "", "01:23:45", "01:23:45:67:89:zz", "1:23:45:67:89:ab",
                "01:23:45:67:89:abc", "01:23:45:67:89:ab:cd", "01-23:45:67:89:ab"}) {
            assertThrows(IOException.class, () -> WakeOnLanSender.createWolPayload(mac));
        }
    }

    @Test
    public void alternatePortsAreBoundedAndDuplicateSubnetsAreSentOnlyOnce() throws Exception {
        InetAddress wifi = InetAddress.getByName("192.168.1.255");
        InetAddress ethernet = InetAddress.getByName("10.2.255.255");
        Set<InetSocketAddress> targets = WakeOnLanSender.destinations(wifi, 48000);
        assertTrue(targets.contains(new InetSocketAddress(wifi, 48009)));
        assertTrue(targets.contains(new InetSocketAddress(wifi, 48021)));
        targets.addAll(WakeOnLanSender.destinations(wifi, 48000));
        targets.addAll(WakeOnLanSender.destinations(ethernet, 48000));
        assertEquals(14, targets.size());
        assertEquals(2, WakeOnLanSender.destinations(wifi, 65535).size());
        assertEquals(7, WakeOnLanSender.destinations(InetAddress.getByName("::1"), 47989).size());
    }

    @Test
    public void failedNicDoesNotPreventRemainingSubnetsAndPorts() throws Exception {
        InetAddress unavailable = InetAddress.getByName("192.168.1.255");
        Set<InetSocketAddress> targets = WakeOnLanSender.destinations(unavailable, 47989);
        targets.addAll(WakeOnLanSender.destinations(InetAddress.getByName("10.0.0.255"), 47989));
        Set<SocketAddress> attempted = new LinkedHashSet<>();
        try (DatagramSocket socket = new DatagramSocket((SocketAddress) null) {
            @Override public void send(DatagramPacket packet) throws IOException {
                attempted.add(packet.getSocketAddress());
                if (packet.getAddress().equals(unavailable)) throw new IOException("No route");
            }
        }) {
            WakeOnLanSender.sendPackets(targets, socket, WakeOnLanSender.createWolPayload("01:23:45:67:89:ab"));
        }
        assertEquals(targets, attempted);
    }

    @Test
    public void failureOfEveryDestinationIsReported() throws Exception {
        try (DatagramSocket socket = new DatagramSocket((SocketAddress) null) {
            @Override public void send(DatagramPacket packet) throws IOException {
                throw new IOException("No route");
            }
        }) {
            Set<InetSocketAddress> targets = WakeOnLanSender.destinations(InetAddress.getLoopbackAddress(), 47989);
            assertThrows(IOException.class, () -> WakeOnLanSender.sendPackets(targets, socket, new byte[102]));
        }
    }

    @Test
    public void transientSendFailureIsRetriedWithoutAddingDuplicateDestinations() throws Exception {
        int[] attempts = {0};
        try (DatagramSocket socket = new DatagramSocket((SocketAddress) null) {
            @Override public void send(DatagramPacket packet) throws IOException {
                if (++attempts[0] == 1) throw new IOException("Temporary route failure");
            }
        }) {
            Set<InetSocketAddress> targets = new LinkedHashSet<>();
            targets.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9));
            WakeOnLanSender.sendPackets(targets, socket, new byte[102]);
        }
        assertEquals(3, attempts[0]);
    }
}
