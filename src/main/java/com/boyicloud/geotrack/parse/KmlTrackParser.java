package com.boyicloud.geotrack.parse;

import com.boyicloud.geotrack.model.GeoPoint;
import de.micromata.opengis.kml.v_2_2_0.Boundary;
import de.micromata.opengis.kml.v_2_2_0.Coordinate;
import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Feature;
import de.micromata.opengis.kml.v_2_2_0.Folder;
import de.micromata.opengis.kml.v_2_2_0.Geometry;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.LineString;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.Point;
import de.micromata.opengis.kml.v_2_2_0.Polygon;
import de.micromata.opengis.kml.v_2_2_0.gx.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KML track parser.
 *
 * <p>Extracts coordinates from {@code LineString}, {@code gx:Track} and
 * {@code Polygon} geometries. A {@code Placemark} is treated as a track only when
 * it contains at least {@link #MIN_TRACK_POINTS} points; smaller geometries are
 * assumed to be markers and ignored.</p>
 *
 * <p>The parser returns raw coordinates (no coordinate system conversion). Apply
 * {@link com.boyicloud.geotrack.transform.CoordinateTransform} afterwards when the
 * source is WGS84 and GCJ02 is required.</p>
 */
public final class KmlTrackParser {

    private static final Logger log = LoggerFactory.getLogger(KmlTrackParser.class);

    /**
     * Minimum number of points for a geometry to be considered a track.
     */
    public static final int MIN_TRACK_POINTS = 88;

    private KmlTrackParser() {
    }

    /**
     * Parses a KML file.
     *
     * @param path path to the KML file
     * @return parsed points, ordered by {@code sequence}
     */
    public static List<GeoPoint> parse(String path) {
        return parse(new File(path));
    }

    /**
     * Parses a KML file.
     *
     * @param file the KML file
     * @return parsed points, ordered by {@code sequence}
     */
    public static List<GeoPoint> parse(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("KML file not found: " + file);
        }
        List<GeoPoint> points = new ArrayList<>();
        try {
            Kml kml = Kml.unmarshal(file);
            AtomicInteger sequence = new AtomicInteger(0);
            processFeature(kml.getFeature(), points, sequence);
            return points;
        } catch (Exception e) {
            log.error("Failed to parse KML file: {}", file, e);
            throw new RuntimeException("KML parsing failed: " + e.getMessage(), e);
        }
    }

    private static void processFeature(Feature feature, List<GeoPoint> list, AtomicInteger sequence) {
        switch (feature) {
            case null -> {
            }
            case Document doc -> {
                for (Feature f : doc.getFeature()) {
                    processFeature(f, list, sequence);
                }
            }
            case Folder folder -> {
                for (Feature f : folder.getFeature()) {
                    processFeature(f, list, sequence);
                }
            }
            case Placemark placemark -> {
                Geometry geometry = placemark.getGeometry();
                if (!isTrackFeature(geometry)) {
                    return;
                }
                if (geometry instanceof LineString lineString) {
                    processCoordinates(list, lineString.getCoordinates(), sequence);
                } else if (geometry instanceof Track track) {
                    processTrack(list, track.getCoord(), sequence);
                } else if (geometry instanceof Polygon polygon) {
                    if (polygon.getOuterBoundaryIs() != null
                            && polygon.getOuterBoundaryIs().getLinearRing() != null) {
                        processCoordinates(list,
                                polygon.getOuterBoundaryIs().getLinearRing().getCoordinates(), sequence);
                    }
                    if (polygon.getInnerBoundaryIs() != null) {
                        for (Boundary innerBoundary : polygon.getInnerBoundaryIs()) {
                            if (innerBoundary.getLinearRing() != null) {
                                processCoordinates(list,
                                        innerBoundary.getLinearRing().getCoordinates(), sequence);
                            }
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    /**
     * A geometry is a track when it holds at least {@link #MIN_TRACK_POINTS} points.
     */
    private static boolean isTrackFeature(Geometry geometry) {
        if (geometry == null) {
            return false;
        }
        int pointCount = 0;
        if (geometry instanceof LineString lineString) {
            if (lineString.getCoordinates() != null) {
                pointCount = lineString.getCoordinates().size();
            }
        } else if (geometry instanceof Track track) {
            if (track.getCoord() != null) {
                pointCount = track.getCoord().size();
            }
        } else if (geometry instanceof Point) {
            return false;
        } else if (geometry instanceof Polygon polygon) {
            if (polygon.getOuterBoundaryIs() != null
                    && polygon.getOuterBoundaryIs().getLinearRing() != null
                    && polygon.getOuterBoundaryIs().getLinearRing().getCoordinates() != null) {
                pointCount += polygon.getOuterBoundaryIs().getLinearRing().getCoordinates().size();
            }
            if (polygon.getInnerBoundaryIs() != null) {
                for (Boundary innerBoundary : polygon.getInnerBoundaryIs()) {
                    if (innerBoundary.getLinearRing() != null
                            && innerBoundary.getLinearRing().getCoordinates() != null) {
                        pointCount += innerBoundary.getLinearRing().getCoordinates().size();
                    }
                }
            }
        }
        return pointCount >= MIN_TRACK_POINTS;
    }

    private static void processCoordinates(List<GeoPoint> list, List<Coordinate> coords,
                                           AtomicInteger sequence) {
        if (coords == null || coords.isEmpty()) {
            return;
        }
        for (Coordinate coord : coords) {
            list.add(new GeoPoint(coord.getLatitude(), coord.getLongitude(),
                    coord.getAltitude(), sequence.getAndIncrement()));
        }
    }

    private static void processTrack(List<GeoPoint> list, List<String> trackCoords,
                                     AtomicInteger sequence) {
        if (trackCoords == null || trackCoords.isEmpty()) {
            return;
        }
        for (String coordStr : trackCoords) {
            String[] parts = coordStr.trim().split("\\s+");
            if (parts.length >= 2) {
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                double altitude = parts.length >= 3 ? Double.parseDouble(parts[2]) : 0D;
                list.add(new GeoPoint(lat, lng, altitude, sequence.getAndIncrement()));
            }
        }
    }
}
