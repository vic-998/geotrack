# geotrack

**English** | [简体中文](README.zh-CN.md)

Lightweight Java (21) toolkit for GPS track processing: Douglas-Peucker /
Visvalingam-Whyatt / LTTB simplification, Kalman & RTS smoothing,
WGS84/GCJ02/BD09 coordinate transform, KML/GPX parsing, encoded polyline codec,
trajectory similarity (Frechet / Hausdorff / DTW), geohash and stay point
detection.

- No Spring / no database dependency, plain JDK plus a couple of third-party libs
- Built for Java 21 (records, pattern-matching switch, ...)
- Every algorithm is exposed as static, thread-safe utility methods with no hidden state
- Track simplification is iterative (no stack overflow), order-preserving and uses a binary-search tolerance
- Kalman filter ships a forward causal pass plus a backward RTS smoother with a zero-dependency matrix core

## Requirements

- JDK 21+
- Maven 3.9+ (to build)

## Installation

Maven:

```xml
<dependency>
    <groupId>com.boyicloud</groupId>
    <artifactId>geotrack</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.boyicloud:geotrack:0.1.0'
```

You can also grab `geotrack-0.1.0.jar` straight from
[`Releases`](https://github.com/vic-998/geotrack/releases), or run
`mvn install` locally and reference it from your build.

## Quick start

### 1. Track simplification / compression

```java
import com.boyicloud.geotrack.model.GeoPoint;
import com.boyicloud.geotrack.simplify.TrackSimplifier;

List<GeoPoint> raw = List.of(
        new GeoPoint(39.9000, 116.4000, 0, 0),
        new GeoPoint(39.9001, 116.4001, 0, 1),
        // ...
        new GeoPoint(39.9100, 116.4100, 0, 999));

// Cap at 500 points with a 5 m baseline tolerance (de-noise first, then binary-search if still over budget)
List<GeoPoint> simplified = TrackSimplifier.simplify(raw, 500, 5D);

// Fixed-tolerance de-noising only, no point budget
List<GeoPoint> denoised = TrackSimplifier.simplifyByTolerance(raw, 5D);

// Raw Douglas-Peucker
List<GeoPoint> dp = TrackSimplifier.douglasPeucker(raw, 5D);
```

### 2. Coordinate transform (WGS84 -> GCJ02)

```java
import com.boyicloud.geotrack.transform.CoordinateTransform;

double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
// gcj[0] = longitude, gcj[1] = latitude

// Batch-convert a whole track (altitude / sequence preserved)
List<GeoPoint> converted = CoordinateTransform.transformBatch(raw);
```

### 3. Parse KML / GPX

```java
import com.boyicloud.geotrack.parse.KmlTrackParser;
import com.boyicloud.geotrack.parse.GpxTrackParser;

List<GeoPoint> fromKml = KmlTrackParser.parse("route.kml");
List<GeoPoint> fromGpx = GpxTrackParser.parse("route.gpx");
```

> The parsers only read raw coordinates; they never transform coordinate systems.
> If the source is WGS84 and you need GCJ02, just call
> `CoordinateTransform.transformBatch(...)` afterwards.

### 4. Parse a comma-separated coordinate string

```java
import com.boyicloud.geotrack.parse.PolylineParser;

// "lat,lng,alt" or "lat,lng"
List<GeoPoint> points = PolylineParser.parse("39.9,116.4,50,39.91,116.41,52");
```

### 5. Decode a Tencent Map compressed polyline

```java
import com.boyicloud.geotrack.geometry.TencentPolyline;

List<double[]> coords = TencentPolyline.decode(compressedPolyline); // [lat, lng]
List<GeoPoint> points = TencentPolyline.decodeToPoints(compressedPolyline);
```

### 6. Spherical centroid

```java
import com.boyicloud.geotrack.geometry.SphericalCentroid;

double[] center = SphericalCentroid.centroid(points);              // [lat, lng]
double[] weighted = SphericalCentroid.weightedCentroid(points, weights);
```

### 7. Kalman filter / RTS smoothing (GPS de-noising)

```java
import com.boyicloud.geotrack.filter.KalmanTrackSmoother;

// Online (causal) filter: constant-velocity model, state [east, north, vEast, vNorth]
List<GeoPoint> online = KalmanTrackSmoother.filter(raw);

// Offline minimum-variance estimate: forward Kalman + backward RTS smoother, much smoother
List<GeoPoint> offline = KalmanTrackSmoother.smooth(raw);

// Custom tuning: accel std (m/s^2), GPS measurement noise (m), time step (s)
List<GeoPoint> tuned = KalmanTrackSmoother.smooth(raw, 0.5, 10.0, 1.0);
```

### 8. Visvalingam-Whyatt area simplification

```java
import com.boyicloud.geotrack.simplify.VisvalingamWhyatt;

// By point count: remove the vertex with the smallest "effective triangle area" first
List<GeoPoint> vw = VisvalingamWhyatt.simplifyToCount(raw, 200);

// By area threshold (square meters)
List<GeoPoint> byArea = VisvalingamWhyatt.simplifyByArea(raw, 25.0);
```

### 9. LTTB downsampling (preserves visual shape)

```java
import com.boyicloud.geotrack.simplify.LttbSimplifier;

// 3D largest-triangle-three-buckets (index + local east/north plane), O(n)
List<GeoPoint> sampled = LttbSimplifier.downsample(raw, 500);
```

### 10. Google Encoded Polyline codec

```java
import com.boyicloud.geotrack.geometry.EncodedPolyline;

String encoded = EncodedPolyline.encode(points);        // precision 5
List<GeoPoint> decoded = EncodedPolyline.decode(encoded);
String highRes = EncodedPolyline.encode(points, 6);     // precision 6
```

### 11. Trajectory similarity (Frechet / Hausdorff / DTW)

```java
import com.boyicloud.geotrack.geometry.TrackSimilarity;

double frechet = TrackSimilarity.discreteFrechet(trackA, trackB); // meters, order-preserving
double hausdorff = TrackSimilarity.hausdorff(trackA, trackB);     // meters, order-insensitive
double dtw = TrackSimilarity.dtw(trackA, trackB);                 // meters, time warping allowed
```

### 12. Geohash codec

```java
import com.boyicloud.geotrack.geometry.Geohash;

String hash = Geohash.encode(39.9, 116.4, 9);
double[] center = Geohash.decode(hash);           // [lat, lng]
double[][] bounds = Geohash.decodeBounds(hash);   // [[minLat,maxLat],[minLng,maxLng]]
```

### 13. Stay point detection (Li et al. 2008)

```java
import com.boyicloud.geotrack.analysis.StayPointDetector;

// Real timestamps (milliseconds)
List<StayPointDetector.StayPoint> stays =
        StayPointDetector.detect(points, 50D, 300D, timestampsMillis);

// No timestamps? use the sequence as seconds
List<StayPointDetector.StayPoint> bySeq =
        StayPointDetector.detectBySequence(points, 50D, 300D);

for (var stay : stays) {
    System.out.println(stay.lat() + "," + stay.lng() + " stayed " + stay.durationSeconds() + "s");
}
```

### 14. Extended coordinate transforms

```java
import com.boyicloud.geotrack.transform.CoordinateTransform;

// WGS84 -> GCJ02 and the iterative inverse
double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
double[] wgs = CoordinateTransform.transformGCJ02ToWGS84(gcj[0], gcj[1]);

// Baidu BD09, full set
double[] bd = CoordinateTransform.transformWGS84ToBD09(116.397428, 39.90923);
double[] back = CoordinateTransform.transformBD09ToWGS84(bd[0], bd[1]);
```

## Module layout

```
com.boyicloud.geotrack
├── model.GeoPoint              track point (lat/lng/altitude/sequence)
├── simplify
│   ├── TrackSimplifier         Douglas-Peucker, binary-search tolerance, even-sampling fallback
│   ├── VisvalingamWhyatt       area simplification (effective triangle area, O(n log n))
│   └── LttbSimplifier          LTTB downsampling (O(n), shape-preserving)
├── filter.KalmanTrackSmoother  Kalman filter + RTS smoother (GPS de-noising)
├── transform.CoordinateTransform  WGS84 / GCJ02 / BD09 conversion
├── parse.*                     KML / GPX / comma-separated coordinate parsing (GPX is XXE-safe)
├── analysis.StayPointDetector  stay point detection
└── geometry
    ├── TencentPolyline         Tencent Map compressed polyline decoder
    ├── EncodedPolyline         Google Encoded Polyline codec
    ├── SphericalCentroid       weighted spherical centroid
    ├── TrackSimilarity         Frechet / Hausdorff / DTW
    ├── Geohash                 geohash codec
    └── GeoMath                 geodesic math (haversine/bearing/projection/triangle area/...)
```

## Build & package

```bash
# compile + test + package (produces target/geotrack-0.1.0.jar)
mvn clean package

# skip tests
mvn clean package -DskipTests

# install into the local repository for other projects
mvn clean install
```

The `package` phase also produces `geotrack-0.1.0.jar`, `-sources.jar` and
`-javadoc.jar`.

## Publishing to Maven Central

`pom.xml` already wires up `maven-source-plugin`, `maven-javadoc-plugin`,
`maven-gpg-plugin` and a `distributionManagement` section (OSSRH). Before
publishing:

1. Confirm the `url`, `scm` and `developers` in `pom.xml`. The `groupId`
   `com.boyicloud` maps to the `boyicloud.com` domain, so verify ownership with a
   DNS `TXT` record when registering the namespace on the Central Portal;
2. configure the `ossrh` account in `~/.m2/settings.xml`;
3. prepare a GPG key and deploy:

```bash
mvn clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE
```

Use `mvn verify` to sign without publishing, or `-Dgpg.skip=true` to skip
signing for a local build.

## License

[Apache License 2.0](LICENSE)
