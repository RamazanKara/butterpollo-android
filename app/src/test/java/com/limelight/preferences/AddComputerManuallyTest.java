package com.limelight.preferences;

import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.*;

public class AddComputerManuallyTest {
    @Test
    public void nonByteAlignedSubnetsCompareMostSignificantBitsFirst() throws Exception {
        byte[] local = InetAddress.getByName("172.16.8.10").getAddress();
        assertTrue(AddComputerManually.isInSubnet(InetAddress.getByName("172.31.2.20").getAddress(), local, 12));
        assertFalse(AddComputerManually.isInSubnet(InetAddress.getByName("172.32.8.10").getAddress(), local, 12));
        local = InetAddress.getByName("192.168.1.130").getAddress();
        assertTrue(AddComputerManually.isInSubnet(InetAddress.getByName("192.168.1.200").getAddress(), local, 25));
        assertFalse(AddComputerManually.isInSubnet(InetAddress.getByName("192.168.1.2").getAddress(), local, 25));
    }

    @Test
    public void byteAlignedAndHostRoutesStillMatch() throws Exception {
        byte[] local = InetAddress.getByName("192.168.1.10").getAddress();
        assertTrue(AddComputerManually.isInSubnet(InetAddress.getByName("192.168.1.200").getAddress(), local, 24));
        assertFalse(AddComputerManually.isInSubnet(InetAddress.getByName("192.168.2.10").getAddress(), local, 24));
        assertTrue(AddComputerManually.isInSubnet(local, local, 32));
        assertFalse(AddComputerManually.isInSubnet(InetAddress.getByName("192.168.1.11").getAddress(), local, 32));
    }
}
