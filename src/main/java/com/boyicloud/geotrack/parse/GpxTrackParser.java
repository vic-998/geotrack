package com.boyicloud.geotrack.parse;

import com.boyicloud.geotrack.model.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GPX track parser.
 *
 * <p>Resolution order: {@code trk/trkseg/trkpt} (standard track) &gt;
 * {@code rte/rtept} (route) &gt; {@code wpt} (waypoints). The output is identical to
 * {@link KmlTrackParser} so KML and GPX share the same downstream processing
 * pipeline.</p>
 *
 * <p>DTD and external entities are disabled to avoid XXE on user-uploaded files.</p>
 */
public final class GpxTrackParser {

    private static final Logger log = LoggerFactory.getLogger(GpxTrackParser.class);

    /**
     * Same threshold as KML: files with fewer points are treated as markers or
     * incomplete tracks.
     */
    public static final int MIN_TRACK_POINTS = 88;

    private GpxTrackParser() {
    }

    /**
     * Parses a GPX file.
     *
     * @param path path to the GPX file
     * @return parsed points, ordered by {@code sequence}
     */
    public static List<GeoPoint> parse(String path) {
        return parse(new File(path));
    }

    /**
     * Parses a GPX file.
     *
     * @param file the GPX file
     * @return parsed points, ordered by {@code sequence}
     */
    public static List<GeoPoint> parse(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("GPX file not found: " + file);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            document.getDocumentElement().normalize();

            AtomicInteger sequence = new AtomicInteger(0);
            List<GeoPoint> list = new ArrayList<>();
            collect(list, document.getElementsByTagName("trkpt"), sequence);
            if (list.isEmpty()) {
                collect(list, document.getElementsByTagName("rtept"), sequence);
            }
            if (list.isEmpty()) {
                collect(list, document.getElementsByTagName("wpt"), sequence);
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to parse GPX file: {}", file, e);
            throw new RuntimeException("GPX parsing failed: " + e.getMessage(), e);
        }
    }

    private static void collect(List<GeoPoint> list, NodeList nodes, AtomicInteger sequence) {
        if (nodes == null || nodes.getLength() == 0) {
            return;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String lat = element.getAttribute("lat");
            String lng = element.getAttribute("lon");
            if (lat == null || lng == null || lat.isEmpty() || lng.isEmpty()) {
                continue;
            }
            String altitude = textOf(element, "ele");
            list.add(new GeoPoint(Double.parseDouble(lat.trim()), Double.parseDouble(lng.trim()),
                    altitude == null || altitude.isEmpty() ? 0D : Double.parseDouble(altitude),
                    sequence.getAndIncrement()));
        }
    }

    /**
     * Returns the trimmed text of the first child element with the given tag name.
     */
    private static String textOf(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children == null || children.getLength() == 0) {
            return null;
        }
        String text = children.item(0).getTextContent();
        return text == null ? null : text.trim();
    }
}
