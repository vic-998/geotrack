package com.boyicloud.geotrack.parse;

import com.boyicloud.geotrack.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpxTrackParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesTrackPointsWithElevation() throws IOException {
        Path file = tempDir.resolve("track.gpx");
        Files.writeString(file, gpx(3));

        List<GeoPoint> points = GpxTrackParser.parse(file.toFile());
        assertEquals(3, points.size());
        assertEquals(39.9, points.get(0).lat(), 1e-6);
        assertEquals(116.4, points.get(0).lng(), 1e-6);
        assertEquals(50, points.get(0).altitude(), 1e-6);
        assertEquals(0, points.get(0).sequence());
    }

    private static String gpx(int count) {
        StringBuilder trkpt = new StringBuilder();
        for (int i = 0; i < count; i++) {
            trkpt.append("<trkpt lat=\"").append(39.9 + i * 0.0001)
                    .append("\" lon=\"").append(116.4 + i * 0.0001)
                    .append("\"><ele>").append(50 + i).append("</ele></trkpt>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="geotrack" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk><trkseg>%s</trkseg></trk>
                </gpx>
                """.formatted(trkpt);
    }
}
