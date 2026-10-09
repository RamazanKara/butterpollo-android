package com.limelight;

import com.limelight.binding.video.UpscalingPolicy;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class ResourceConfigurationTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private static Document read(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new File("src/main", path));
    }

    @Test
    public void upscalingLabelsAndValuesMatchGlobalHostAndOverlayModeOrder() throws Exception {
        UpscalingPolicy.Mode[] modes = UpscalingPolicy.Mode.values();
        NodeList arrays = read("res/values/arrays.xml").getElementsByTagName("string-array");
        int checked = 0;
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            String name = array.getAttribute("name");
            if (!name.equals("upscaling_names") && !name.equals("upscaling_values")) continue;
            NodeList items = array.getElementsByTagName("item");
            assertEquals(modes.length, items.getLength());
            for (int j = 0; j < items.getLength(); j++) {
                assertEquals((name.equals("upscaling_names") ? "@string/upscaling_" : "") + modes[j].value,
                        items.item(j).getTextContent());
            }
            checked++;
        }
        assertEquals(2, checked);
    }

    @Test
    public void onlyShippedLanguagesAreOfferedByBothLanguagePickers() throws Exception {
        Set<String> platformLanguages = new HashSet<>();
        NodeList locales = read("res/xml/locales_config.xml").getElementsByTagName("locale");
        for (int i = 0; i < locales.getLength(); i++) {
            platformLanguages.add(((Element) locales.item(i)).getAttributeNS(ANDROID, "name"));
        }
        Set<String> legacyLanguages = new HashSet<>();
        NodeList arrays = read("res/values/arrays.xml").getElementsByTagName("string-array");
        int labelCount = 0;
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            NodeList items = array.getElementsByTagName("item");
            if (array.getAttribute("name").equals("language_names")) {
                labelCount = items.getLength();
            } else if (array.getAttribute("name").equals("language_values")) {
                for (int j = 0; j < items.getLength(); j++) {
                    legacyLanguages.add(items.item(j).getTextContent());
                }
            }
        }
        assertEquals(labelCount, legacyLanguages.size());
        assertTrue(legacyLanguages.remove("default"));
        assertEquals(platformLanguages, legacyLanguages);

        assertEquals(Set.of("en", "de"), platformLanguages);
    }

    @Test
    public void installLocationIsDeclaredOnTheManifest() throws Exception {
        Document manifest = read("AndroidManifest.xml");
        assertEquals("auto", manifest.getDocumentElement().getAttributeNS(ANDROID, "installLocation"));
        assertFalse(((Element) manifest.getElementsByTagName("application").item(0))
                .hasAttributeNS(ANDROID, "installLocation"));
    }
}
