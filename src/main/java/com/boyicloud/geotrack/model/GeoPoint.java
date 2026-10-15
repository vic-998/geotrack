package com.boyicloud.geotrack.model;

/**
 * An immutable geographic point used across the geotrack toolkit.
 *
 * <p>{@code lat} / {@code lng} are decimal degrees, {@code altitude} is meters
 * ({@code 0} when unknown) and {@code sequence} is the order of the point inside
 * a track (the smaller the value, the earlier the point).</p>
 *
 * @param lat      latitude in decimal degrees
 * @param lng      longitude in decimal degrees
 * @param altitude altitude in meters, {@code 0} when unknown
 * @param sequence order inside the track
 */
public record GeoPoint(double lat, double lng, double altitude, int sequence) {

    /**
     * Creates a point with {@code altitude = 0} and {@code sequence = 0}.
     */
    public GeoPoint(double lat, double lng) {
        this(lat, lng, 0D, 0);
    }

    /**
     * Creates a point with {@code altitude = 0}.
     */
    public GeoPoint(double lat, double lng, int sequence) {
        this(lat, lng, 0D, sequence);
    }
}
