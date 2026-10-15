package com.boyicloud.geotrack.parse;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KmlTrackParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesLineStringWithEnoughPoints() throws IOException {
        Path file = tempDir.resolve("track.kml");
        Files.writeString(file, kml(120));

        List<GeoPoint> points = KmlTrackParser.parse(file.toFile());
        assertEquals(120, points.size());
        assertEquals(116.4, points.get(0).lng(), 1e-6);
        assertEquals(39.9, points.get(0).lat(), 1e-6);
        assertEquals(0, points.get(0).sequence());
    }

    @Test
    void ignoresShortGeometriesBelowThreshold() throws IOException {
        Path file = tempDir.resolve("markers.kml");
        Files.writeString(file, kml(10));

        List<GeoPoint> points = KmlTrackParser.parse(file.toFile());
        assertTrue(points.isEmpty());
    }

    private static String kml(int count) {
        StringBuilder coordinates = new StringBuilder();
        for (int i = 0; i < count; i++) {
            coordinates.append(116.4 + i * 0.0001)
                    .append(',')
                    .append(39.9 + i * 0.0001)
                    .append(",0 ");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark>
                      <LineString>
                        <coordinates>%s</coordinates>
                      </LineString>
                    </Placemark>
                  </Document>
                </kml>
                """.formatted(coordinates);
    }
}
