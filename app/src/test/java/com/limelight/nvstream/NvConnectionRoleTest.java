package com.limelight.nvstream;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.LaunchConfirmation;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.NvHTTPRoleTest;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class NvConnectionRoleTest {
    private ConnectionContext context(int appId, Consumer<LaunchConfirmation> decide, AtomicInteger completed) {
        ConnectionContext context = new ConnectionContext();
        context.streamConfig = new StreamConfiguration.Builder().setApp(new NvApp("Tile", appId, false)).build();
        context.connListener = (NvConnectionListener) Proxy.newProxyInstance(
                NvConnectionListener.class.getClassLoader(), new Class<?>[] {NvConnectionListener.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("launchConfirmationRequired")) {
                        decide.accept((LaunchConfirmation) args[0]);
                    } else if (method.getName().equals("launchActionCompleted")) {
                        completed.incrementAndGet();
                    }
                    return null;
                });
        return context;
    }

    private NvHTTP http(ConnectionContext expected, String verb, AtomicInteger requests,
                        HostHttpResponseException... errors) throws IOException {
        return new NvHTTP(new ComputerDetails.AddressTuple("192.0.2.1", 47989), 47984, "device", null,
                new LimelightCryptoProvider() {
                    public java.security.cert.X509Certificate getClientCertificate() { return null; }
                    public java.security.PrivateKey getClientPrivateKey() { return null; }
                    public byte[] getPemEncodedClientCertificate() { return new byte[0]; }
                    public String encodeBase64String(byte[] data) { return ""; }
                }) {
            @Override
            public boolean launchApp(ConnectionContext context, String actualVerb, int appId, boolean hdr)
                    throws IOException {
                assertSame(expected, context);
                assertEquals(verb, actualVerb);
                assertEquals(expected.streamConfig.getApp().getAppId(), appId);
                int index = requests.getAndIncrement();
                if (index < errors.length) {
                    throw errors[index];
                }
                return true;
            }

            @Override
            public boolean quitApp() {
                throw new AssertionError("Role tile attempted to cancel the main session");
            }
        };
    }

    @Test
    public void launchAndResumeRepeatTheSameRequestOnlyAfterConfirmation() throws Exception {
        for (String verb : new String[] {"launch", "resume"}) {
            AtomicInteger requests = new AtomicInteger();
            ConnectionContext context = context(73, request -> {
                assertEquals(1, requests.get());
                request.confirm(LaunchConfirmation.nowMs());
            }, new AtomicInteger());
            assertTrue(NvConnection.launchApp(http(context, verb, requests,
                    NvHTTPRoleTest.error("replace-confirm")), context, verb));
            assertEquals(2, requests.get());
            assertFalse(context.launchActionCompleted);
        }
    }

    @Test
    public void cancelledAndExpiredPromptsNeverRetry() throws Exception {
        for (boolean timeout : new boolean[] {false, true}) {
            AtomicInteger requests = new AtomicInteger();
            ConnectionContext context = context(73, request -> {
                if (timeout) {
                    request.getState(LaunchConfirmation.nowMs() + 60_000);
                } else {
                    request.cancel();
                }
            }, new AtomicInteger());
            NvHTTP http = http(context, "launch", requests, NvHTTPRoleTest.error("replace-confirm"));
            IOException error = assertThrows(IOException.class, () -> NvConnection.launchApp(http, context, "launch"));
            assertTrue(error.getMessage().contains(timeout ? "timed out" : "cancelled"));
            assertEquals(1, requests.get());
        }
    }

    @Test
    public void denialAfterConfirmationIsReadableAndNeverRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        ConnectionContext context = context(73, request -> request.confirm(LaunchConfirmation.nowMs()), new AtomicInteger());
        HostHttpResponseException denied = new HostHttpResponseException(403, "Permission denied");
        NvHTTP http = http(context, "launch", requests, NvHTTPRoleTest.error("replace-confirm"), denied);
        assertSame(denied, assertThrows(HostHttpResponseException.class,
                () -> NvConnection.launchApp(http, context, "launch")));
        assertEquals(2, requests.get());
    }

    @Test
    public void changedHostGenerationRequiresStartingAgainInsteadOfAnotherAutomaticConfirmation() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger prompts = new AtomicInteger();
        ConnectionContext context = context(73, request -> {
            prompts.incrementAndGet();
            request.confirm(LaunchConfirmation.nowMs());
        }, new AtomicInteger());
        HostHttpResponseException confirm = NvHTTPRoleTest.error("replace-confirm");
        NvHTTP http = http(context, "launch", requests, confirm, confirm);
        IOException error = assertThrows(IOException.class, () -> NvConnection.launchApp(http, context, "launch"));
        assertTrue(error.getMessage().contains("host session changed"));
        assertEquals(2, requests.get());
        assertEquals(1, prompts.get());
    }

    @Test
    public void terminationConfirmationAndCompletionDoNotStartAStream() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        ConnectionContext context = context(2147483504, request -> {
            assertTrue(request.isTerminate());
            request.confirm(LaunchConfirmation.nowMs());
        }, completed);
        assertFalse(NvConnection.launchApp(http(context, "launch", requests,
                NvHTTPRoleTest.error("terminate-confirm"), NvHTTPRoleTest.error("terminate-done")), context, "launch"));
        assertEquals(2, requests.get());
        assertEquals(1, completed.get());
        assertTrue(context.launchActionCompleted);
    }

    @Test
    public void disconnect410CompletesWithoutPromptOrRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        ConnectionContext context = context(2147483502, request -> fail("Disconnect is not a confirmation"), completed);
        assertFalse(NvConnection.launchApp(http(context, "launch", requests,
                NvHTTPRoleTest.error("monitor-disconnected")), context, "launch"));
        assertEquals(1, requests.get());
        assertEquals(1, completed.get());
    }

    @Test
    public void interruptionWhileWaitingNeverRetriesAndClosesThePrompt() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        ConnectionContext context = context(73, request -> Thread.currentThread().interrupt(), new AtomicInteger());
        NvHTTP http = http(context, "launch", requests, NvHTTPRoleTest.error("replace-confirm"));
        try {
            assertThrows(java.io.InterruptedIOException.class, () -> NvConnection.launchApp(http, context, "launch"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, requests.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void allControlTilesBypassTheLegacyQuitBeforeLaunchPath() throws Exception {
        for (int id = 2147483501; id <= 2147483507; id++) {
            AtomicInteger requests = new AtomicInteger();
            ConnectionContext context = context(id, request -> fail("Unexpected prompt"), new AtomicInteger());
            NvConnection connection = new NvConnection(null, new ComputerDetails.AddressTuple("192.0.2.1", 47989),
                    47984, "device", context.streamConfig, null, null);
            assertTrue(connection.quitAndLaunch(http(context, "launch", requests), context));
            assertEquals(1, requests.get());
        }
    }

    @Test
    public void monitorSuppressesEveryInputFamilyBeforeTheNativeBridge() {
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setApp(new NvApp("Monitor", 2147483505, false)).build();
        NvConnection connection = new NvConnection(null, new ComputerDetails.AddressTuple("192.0.2.1", 47989),
                47984, "device", config, null, null);
        assertFalse(connection.canSendInput());
        connection.sendMouseMove((short) 1, (short) 1);
        connection.sendMouseButtonDown((byte) 1);
        connection.sendKeyboardInput((short) 1, (byte) 0, (byte) 0, (byte) 0);
        connection.sendUtf8Text("test");
        connection.sendControllerInput((short) 0, (short) 1, 0, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0);
        connection.sendControllerBatteryEvent((byte) 0, (byte) 0, (byte) 0);
        int unsupported = com.limelight.nvstream.jni.MoonBridge.LI_ERR_UNSUPPORTED;
        assertEquals(unsupported, connection.sendControllerArrivalEvent((byte) 0, (short) 1, (byte) 0, 0, (short) 0));
        assertEquals(unsupported, connection.sendTouchEvent((byte) 0, 0, 0, 0, 0, 0, 0, (short) 0));
        assertEquals(unsupported, connection.sendPenEvent((byte) 0, (byte) 0, (byte) 0, 0, 0, 0, 0, 0, (short) 0, (byte) 0));
        assertEquals(unsupported, connection.sendControllerTouchEvent((byte) 0, (byte) 0, 0, 0, 0, 0));
        assertEquals(unsupported, connection.sendControllerMotionEvent((byte) 0, (byte) 0, 0, 0, 0));
    }
}
