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

import static org.junit.Assert.*;

public class NvHTTPParityTest {
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
