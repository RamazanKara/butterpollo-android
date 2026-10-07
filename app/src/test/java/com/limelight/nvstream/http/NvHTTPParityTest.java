package com.limelight.nvstream.http;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.StreamConfiguration;

import org.junit.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.Locale;

import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;

public class NvHTTPParityTest {
    private NvHTTP http() throws java.io.IOException {
        return new NvHTTP(new ComputerDetails.AddressTuple("192.0.2.1", 47989), 47984,
                "1234567890abcdef", null, new LimelightCryptoProvider() {
            public X509Certificate getClientCertificate() { return null; }
            public PrivateKey getClientPrivateKey() { return null; }
            public byte[] getPemEncodedClientCertificate() { return new byte[0]; }
            public String encodeBase64String(byte[] data) { return ""; }
        });
    }

    private ComputerDetails capabilities(String xml) throws Exception {
        ComputerDetails details = new ComputerDetails();
        NvHTTP.readClientCapabilities(details, xml);
        return details;
    }

    @Test
    public void butterpolloAdvertisesUnsignedPermissionsAndFractionalLimiter() throws Exception {
        ComputerDetails details = http().getComputerDetails(fixture("butterpollo-serverinfo.xml"));
        assertEquals(0xFFFFFFFFL, details.permission);
        assertTrue(details.canReadClipboard());
        assertTrue(details.canWriteClipboard());
        assertTrue(details.frameLimiterSupported);
        assertTrue(details.frameLimiterEnabled);
        assertTrue(details.virtualDisplayFrameLimiterEnabled);
        assertEquals(59940, details.frameLimiterFpsLimitMilliHz);
        assertArrayEquals(new byte[] {0}, details.serverCommandPayload(0));
    }

    @Test
    public void apolloCommandsPreserveEmptyNamesIndicesAndReservedBytes() throws Exception {
        ComputerDetails details = capabilities(fixture("apollo-serverinfo.xml"));
        assertEquals("Display & audio", details.serverCommands.get(0));
        assertEquals("", details.serverCommands.get(1));
        assertEquals("Sleep", details.serverCommands.get(2));
        assertArrayEquals(new byte[] {2, 0, 0, 0}, details.serverCommandPayload(2));
        assertFalse(details.canRunServerCommand(-1));
        assertFalse(details.canRunServerCommand(3));
        assertThrows(IllegalArgumentException.class, () -> details.serverCommandPayload(3));
    }

    @Test
    public void commandIndexCannotWrapBeyondOneByte() {
        ComputerDetails details = new ComputerDetails();
        for (int i = 0; i < 257; i++) {
            details.serverCommands.add("Command " + i);
        }
        assertArrayEquals(new byte[] {(byte)255, 0, 0, 0}, details.serverCommandPayload(255));
        assertFalse(details.canRunServerCommand(256));
    }

    @Test
    public void viewerCanResumeButCannotLaunchQuitInputOrUseHostActions() throws Exception {
        ComputerDetails details = http().getComputerDetails(fixture("butterpollo-viewer-serverinfo.xml"));
        assertTrue(details.hasPermission(ComputerDetails.PERMISSION_LIST));
        assertTrue(details.hasPermission(ComputerDetails.PERMISSION_VIEW | ComputerDetails.PERMISSION_LAUNCH));
        assertFalse(details.hasPermission(ComputerDetails.PERMISSION_LAUNCH));
        assertFalse(details.hasPermission(ComputerDetails.PERMISSION_INPUT));
        assertFalse(details.canReadClipboard());
        assertFalse(details.canWriteClipboard());
        assertFalse(details.canRunServerCommand(0));
        assertFalse(details.frameLimiterEnabled);
        assertTrue(details.virtualDisplayFrameLimiterEnabled);
        assertEquals(0, details.frameLimiterFpsLimitMilliHz);
    }

    @Test
    public void clipboardDirectionsAreIndependentAndRequireViewing() throws Exception {
        ComputerDetails details = capabilities("<root status_code=\"200\"><Permission>" +
                (ComputerDetails.PERMISSION_LAUNCH | ComputerDetails.PERMISSION_CLIPBOARD_SET) + "</Permission></root>");
        assertTrue(details.canWriteClipboard());
        assertFalse(details.canReadClipboard());
        details.permission = ComputerDetails.PERMISSION_VIEW | ComputerDetails.PERMISSION_CLIPBOARD_READ;
        assertTrue(details.canReadClipboard());
        assertFalse(details.canWriteClipboard());
        details.permission = ComputerDetails.PERMISSION_CLIPBOARD_READ | ComputerDetails.PERMISSION_CLIPBOARD_SET;
        assertFalse(details.canReadClipboard());
        assertFalse(details.canWriteClipboard());
    }

    @Test
    public void stockSunshineKeepsNormalActionsWithoutApolloExtras() throws Exception {
        ComputerDetails details = capabilities(fixture("sunshine-serverinfo.xml"));
        assertEquals(-1, details.permission);
        assertTrue(details.hasPermission(ComputerDetails.PERMISSION_LAUNCH));
        assertTrue(details.hasPermission(ComputerDetails.PERMISSION_VIEW));
        assertFalse(details.canReadClipboard());
        assertFalse(details.canWriteClipboard());
        assertFalse(details.canRunServerCommand(0));
        assertFalse(details.frameLimiterSupported);
    }

    @Test
    public void malformedPermissionsFailClosedWithoutBreakingDiscovery() throws Exception {
        for (String value : new String[] {"", "-1", "4294967296", "not-a-mask"}) {
            ComputerDetails details = capabilities("<root status_code=\"200\"><Permission>" + value +
                    "</Permission><ServerCommand>Sleep</ServerCommand></root>");
            assertEquals(0, details.permission);
            assertFalse(details.canRunServerCommand(0));
            assertFalse(details.hasPermission(ComputerDetails.PERMISSION_LAUNCH));
        }
    }

    @Test
    public void capabilityRefreshClearsOldGrantsAndDoesNotAliasCommands() throws Exception {
        ComputerDetails original = capabilities(fixture("butterpollo-serverinfo.xml"));
        ComputerDetails copy = new ComputerDetails(original);
        original.serverCommands.clear();
        assertTrue(copy.canRunServerCommand(0));
        copy.update(http().getComputerDetails(fixture("butterpollo-viewer-serverinfo.xml")));
        assertFalse(copy.canRunServerCommand(0));
        assertFalse(copy.canWriteClipboard());
        NvHTTP.readClientCapabilities(copy, fixture("sunshine-serverinfo.xml"));
        assertEquals(-1, copy.permission);
        assertNull(copy.rustHostVersion);
        assertFalse(copy.frameLimiterSupported);
    }

    @Test
    public void clipboardUsesHttpsPathPostBodyAndPerRequestIdentity() throws Exception {
        NvHTTP http = http();
        String text = "Grüße & <text>\nこんにちは 🧈";
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            assertEquals("https", chain.request().url().scheme());
            assertEquals("/actions/clipboard", chain.request().url().encodedPath());
            assertEquals("text", chain.request().url().queryParameter("type"));
            assertEquals("1234567890abcdef", chain.request().url().queryParameter("uniqueid"));
            assertNotNull(chain.request().url().queryParameter("uuid"));
            assertFalse(chain.request().url().toString().contains("Grüße"));
            if (chain.request().method().equals("POST")) {
                Buffer buffer = new Buffer();
                chain.request().body().writeTo(buffer);
                assertEquals(text, buffer.readUtf8());
                assertEquals("text/plain; charset=utf-8", chain.request().body().contentType().toString());
            } else {
                assertEquals("GET", chain.request().method());
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(
                            chain.request().method().equals("POST") ? "" : text,
                            MediaType.get("text/plain; charset=utf-8"))).build();
        }).build();
        java.lang.reflect.Field field = NvHTTP.class.getDeclaredField("httpClientLongConnectTimeout");
        field.setAccessible(true);
        field.set(http, client);
        http.sendClipboard(text);
        assertEquals(text, http.getClipboard());
    }

    @Test
    public void clipboardRejectsXmlErrorPagesAndOversizedTransfersButAcceptsEmptyText() throws Exception {
        try (ResponseBody empty = ResponseBody.create("", MediaType.get("text/plain"));
             ResponseBody error = ResponseBody.create("<root status_code=\"404\"/>", MediaType.get("application/xml"));
             ResponseBody large = ResponseBody.create("a".repeat(1024 * 1024 + 1), MediaType.get("text/plain"))) {
            assertEquals("", NvHTTP.readClipboardResponse(empty));
            assertThrows(java.io.IOException.class, () -> NvHTTP.readClipboardResponse(error));
            assertThrows(java.io.IOException.class, () -> NvHTTP.readClipboardResponse(large));
        }
        assertEquals(1024 * 1024, NvHTTP.clipboardRequestBody("a".repeat(1024 * 1024)).contentLength());
        assertThrows(java.io.IOException.class, () -> NvHTTP.clipboardRequestBody("ä".repeat(524289)));
    }

    @Test
    public void runtimeBitrateUsesKbpsAndReportsTheHostAppliedCap() throws Exception {
        NvHTTP http = http();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            assertEquals("https", chain.request().url().scheme());
            assertEquals("/bitrate", chain.request().url().encodedPath());
            assertEquals("30000", chain.request().url().queryParameter("bitrate"));
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(
                            "<root status_code=\"200\"><bitrate>20000</bitrate><updated>1</updated></root>",
                            MediaType.get("application/xml"))).build();
        }).build();
        java.lang.reflect.Field field = NvHTTP.class.getDeclaredField("httpClientLongConnectTimeout");
        field.setAccessible(true);
        field.set(http, client);
        assertEquals(20000, http.setBitrate(30000));
        assertThrows(IllegalArgumentException.class, () -> http.setBitrate(0));
        assertThrows(IllegalArgumentException.class, () -> http.setBitrate(500001));
    }

    @Test
    public void hostActionsPropagateRevocationUnsupportedEndpointsAndInvalidSuccessBodies() throws Exception {
        NvHTTP http = http();
        java.lang.reflect.Field field = NvHTTP.class.getDeclaredField("httpClientLongConnectTimeout");
        field.setAccessible(true);
        for (int code : new int[] {403, 404, 500, 200}) {
            field.set(http, new OkHttpClient.Builder().addInterceptor(chain ->
                    new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(code).message("Fixture response").body(ResponseBody.create(
                                    "<root status_code=\"200\"><bitrate>0</bitrate></root>",
                                    MediaType.get("application/xml"))).build()).build());
            assertThrows(java.io.IOException.class, http::getClipboard);
            assertThrows(java.io.IOException.class, () -> http.sendClipboard("text"));
            assertThrows(java.io.IOException.class, () -> http.setBitrate(10000));
        }
    }

    @Test
    public void requestNonceDoesNotReplaceThePersistentDeviceIdentity() throws Exception {
        LimelightCryptoProvider crypto = new LimelightCryptoProvider() {
            public X509Certificate getClientCertificate() { return null; }
            public PrivateKey getClientPrivateKey() { return null; }
            public byte[] getPemEncodedClientCertificate() { return new byte[0]; }
            public String encodeBase64String(byte[] data) { return ""; }
        };
        NvHTTP first = new NvHTTP(new ComputerDetails.AddressTuple("192.0.2.1", 47989),
                47984, "1234567890abcdef", null, crypto);
        NvHTTP second = new NvHTTP(new ComputerDetails.AddressTuple("192.0.2.1", 47989),
                47984, "fedcba0987654321", null, crypto);
        HttpUrl base = HttpUrl.get("https://192.0.2.1:47984");
        HttpUrl one = first.getCompleteUrl(base, "serverinfo", null);
        HttpUrl again = first.getCompleteUrl(base, "resume", "appid=42");
        assertEquals("1234567890abcdef", one.queryParameter("uniqueid"));
        assertEquals(one.queryParameter("uniqueid"), again.queryParameter("uniqueid"));
        assertNotEquals(one.queryParameter("uuid"), again.queryParameter("uuid"));
        assertNotEquals(one.queryParameter("uniqueid"), second.getCompleteUrl(base, "serverinfo", null).queryParameter("uniqueid"));
    }

    @Test
    public void pyrowaveServerCapabilitiesRemainSeparateFromLaunchHdr() throws Exception {
        ConnectionContext context = context("butterpollo-serverinfo.xml");
        String server = fixture("butterpollo-serverinfo.xml");
        assertEquals(0x07830301L, Long.parseLong(NvHTTP.getXmlString(server, "ServerCodecModeSupport", true)));
        assertNull(query(context, false).queryParameter("hdrMode"));
        assertEquals("1", query(context, true).queryParameter("hdrMode"));
        assertFalse(query(context, true).toString().toLowerCase(Locale.ROOT).contains("pyrowave"));
    }

    private String fixture(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/protocol/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ConnectionContext context(String server) throws Exception {
        ConnectionContext context = new ConnectionContext();
        context.streamConfig = new StreamConfiguration.Builder()
                .setResolution(1968, 2184).setRefreshRate(120).setLaunchRefreshRate(120)
                .setClientRefreshRateX100(11988).setVirtualDisplay(true, 100).build();
        context.negotiatedWidth = 1968;
        context.negotiatedHeight = 2184;
        context.riKey = new SecretKeySpec(new byte[16], "AES");
        context.riKeyId = -123;
        NvHTTP.readDisplayCapabilities(context, fixture(server));
        return context;
    }

    private HttpUrl query(ConnectionContext context, boolean hdr) {
        return HttpUrl.get("https://host/launch?" + NvHTTP.getLaunchQuery(context, 42, hdr));
    }

    @Test
    public void butterpolloLaunchPreservesNativePixelsAndFractionalHighRefresh() throws Exception {
        ConnectionContext context = context("butterpollo-serverinfo.xml");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            HttpUrl query = query(context, true);
            assertEquals("1968x2184x119.88", query.queryParameter("mode"));
            assertEquals("1", query.queryParameter("virtualDisplay"));
            assertEquals("100", query.queryParameter("scaleFactor"));
            assertEquals("1", query.queryParameter("hdrMode"));
            assertEquals("42", query.queryParameter("appid"));
            assertEquals("-123", query.queryParameter("rikeyid"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void renderScaleDoesNotChangeTheStreamViewport() throws Exception {
        ConnectionContext context = context("butterpollo-serverinfo.xml");
        context.streamConfig = new StreamConfiguration.Builder().setLaunchRefreshRate(144)
                .setClientRefreshRateX100(24000).setVirtualDisplay(true, 150).build();
        HttpUrl query = query(context, false);
        assertEquals("1968x2184x144", query.queryParameter("mode"));
        assertEquals("150", query.queryParameter("scaleFactor"));
        assertNull(query.queryParameter("hdrMode"));
    }

    @Test
    public void sunshineDoesNotReceiveApolloDisplayExtensions() throws Exception {
        HttpUrl query = query(context("sunshine-serverinfo.xml"), false);
        assertEquals("1968x2184x120", query.queryParameter("mode"));
        assertNull(query.queryParameter("virtualDisplay"));
        assertNull(query.queryParameter("scaleFactor"));
    }

    @Test
    public void apolloKeepsIntegerRateAndSupportsDisplayRequest() throws Exception {
        HttpUrl query = query(context("apollo-serverinfo.xml"), false);
        assertEquals("1968x2184x120", query.queryParameter("mode"));
        assertEquals("1", query.queryParameter("virtualDisplay"));
    }

    @Test
    public void unavailableDriverAndDisabledPreferencePreserveHostPolicy() throws Exception {
        ConnectionContext context = context("butterpollo-serverinfo.xml");
        NvHTTP.readDisplayCapabilities(context, fixture("butterpollo-serverinfo.xml")
                .replace("<VirtualDisplayDriverReady>true", "<VirtualDisplayDriverReady>false"));
        assertNull(query(context, false).queryParameter("virtualDisplay"));
        NvHTTP.readDisplayCapabilities(context, fixture("butterpollo-serverinfo.xml"));
        context.streamConfig = new StreamConfiguration.Builder().build();
        assertNull(query(context, false).queryParameter("virtualDisplay"));
        assertNull(query(context, false).queryParameter("scaleFactor"));
    }

    @Test
    public void legacyNvidiaRetainsSopsWorkarounds() throws Exception {
        ConnectionContext context = context("sunshine-serverinfo.xml");
        context.isNvidiaServerSoftware = true;
        HttpUrl query = query(context, false);
        assertEquals("1968x2184x0", query.queryParameter("mode"));
        assertEquals("0", query.queryParameter("sops"));
    }

    @Test
    public void appListAcceptsInheritedExtensionFields() throws Exception {
        LinkedList<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(fixture("butterpollo-applist.xml")));
        assertEquals(2, apps.size());
        assertEquals("Desktop", apps.get(0).getAppName());
        assertEquals(42, apps.get(0).getAppId());
        assertTrue(apps.get(0).isHdrSupported());
        assertEquals("Game & tools", apps.get(1).getAppName());
    }

    @Test
    public void emptyAppListAllowsRootWhitespaceAndExtensionText() throws Exception {
        assertTrue(NvHTTP.getAppListByReader(new StringReader(
                "<root status_code=\"200\">\n<Extension>host data</Extension>\n</root>\n")).isEmpty());
    }
}
