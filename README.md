# geotrack

[![CI](https://github.com/vic-998/geotrack/actions/workflows/ci.yml/badge.svg)](https://github.com/vic-998/geotrack/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.boyicloud/geotrack.svg)](https://central.sonatype.com/artifact/com.boyicloud/geotrack)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)

**English** | [简体中文](README.zh-CN.md)

A lightweight Java 21 toolkit for GPS track processing: parse, transform,
denoise, simplify and analyze — everything between raw location points and the
map or business system.

![GeoTrack overview](design/geotrack-overview.svg)

## Features

| Capability | What it does |
| --- | --- |
| KML / GPX / Polyline parsing | Turn different sources into a uniform `GeoPoint` track |
| WGS84 · GCJ02 · BD09 transform | Unify coordinate systems, with batch conversion |
| Kalman filter + RTS smoothing | Reduce GPS noise so the route stays stable |
| Douglas-Peucker / Visvalingam-Whyatt / LTTB | Simplify and downsample while keeping the shape |
| Fréchet / Hausdorff / DTW | Measure how similar two tracks are |
| Geohash / spherical centroid | Spatial encoding and center-of-mass |
| Stay point detection | Find stops with arrival, departure and dwell time |
| Encoded polyline / Tencent polyline | Exchange compact tracks with map services |

No Spring, no database — the core is plain JDK plus a couple of third-party
libraries. Every algorithm is exposed as static, thread-safe methods with no
hidden state, so it drops into services, batch jobs, desktop tools or your own
data pipeline.

## Requirements

- Java 21+

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

## Quick start

Process a raw track from GPX to a ready-to-render, noise-free route capped at
500 points:

```java
import com.boyicloud.geotrack.filter.KalmanTrackSmoother;
import com.boyicloud.geotrack.parse.GpxTrackParser;
import com.boyicloud.geotrack.simplify.TrackSimplifier;
import com.boyicloud.geotrack.transform.CoordinateTransform;

var raw       = GpxTrackParser.parse("route.gpx");
var converted = CoordinateTransform.transformBatch(raw); // WGS84 -> GCJ02
var smooth    = KalmanTrackSmoother.smooth(converted);   // remove GPS noise
var ready     = TrackSimplifier.simplify(smooth, 500, 5D);
```

The steps are independent — use only what you need. A few more examples:

```java
// Track simplification (Douglas-Peucker)
List<GeoPoint> dp = TrackSimplifier.simplifyByTolerance(raw, 5D);

// Visvalingam-Whyatt area simplification
List<GeoPoint> vw = VisvalingamWhyatt.simplifyToCount(raw, 200);

// LTTB downsampling that preserves visual shape
List<GeoPoint> lttb = LttbSimplifier.downsample(raw, 500);

// Coordinate systems
double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
double[] bd  = CoordinateTransform.transformWGS84ToBD09(116.397428, 39.90923);

// Track similarity in meters
double frechet = TrackSimilarity.discreteFrechet(trackA, trackB);

// Stay point detection
var stays = StayPointDetector.detectBySequence(points, 50D, 300D);

// Compact exchange with map services
String encoded = EncodedPolyline.encode(points, 6);
```

## Design & algorithms

geotrack is a thin, stateless layer between raw points and your map / business
system. It works on a single immutable `GeoPoint` record and exposes pure static
methods, so the stages below compose freely and stay thread-safe:

```
parse  ──►  transform  ──►  denoise  ──►  simplify  ──►  analyze
```

The interesting part is *how* each stage is built, and why:

| Module | Algorithm | Design notes |
| --- | --- | --- |
| `TrackSimplifier` | Douglas-Peucker | iterative (never overflows the stack); tolerance is found by **binary search** to fit a point budget in meters; GPS-noise baseline tolerance; even-sampling fallback |
| `VisvalingamWhyatt` | effective-area removal | monotone removal order yields a true simplification **hierarchy** (a coarse result is always a subset of a finer one); linked list + lazy-invalidated heap, O(n log n) |
| `LttbSimplifier` | largest-triangle-three-buckets | O(n) and tuning-free; extended to **3-D** `(index, east, north)` so it preserves the time distribution, not just the shape |
| `KalmanTrackSmoother` | constant-velocity Kalman + **RTS** | forward pass for streaming, backward RTS pass for the offline minimum-variance estimate; local ENU projection; zero-dependency matrix core |
| `CoordinateTransform` | GCJ02 offset + fixed-point inverse | GCJ02 has no analytic inverse, so it is solved iteratively to centimeter accuracy; BD09 composed from the GCJ02 hop |
| `TrackSimilarity` | discrete Fréchet / Hausdorff / DTW | O(n·m) time but O(min(n, m)) memory via rolling rows; all distances in meters |
| `StayPointDetector` | Li et al. 2008 | also emits the **trailing** stay that the textbook pseudo-code drops |
| `geometry` | haversine + local plane | sphere for distance, equirectangular projection for geometry; spherical centroid via a 3-D unit-vector mean |

The full write-up — process/measurement models, recurrences, complexity tables
and references — is in **[`docs/DESIGN.md`](docs/DESIGN.md)**.

## API at a glance

```
com.boyicloud.geotrack
├── model.GeoPoint               track point (lat / lng / altitude / sequence)
├── simplify
│   ├── TrackSimplifier          Douglas-Peucker with budget fitting
│   ├── VisvalingamWhyatt        effective-area simplification
│   └── LttbSimplifier           LTTB downsampling
├── filter.KalmanTrackSmoother   Kalman filter + RTS smoother
├── transform.CoordinateTransform  WGS84 / GCJ02 / BD09 conversion
├── parse
│   ├── KmlTrackParser           KML tracks
│   ├── GpxTrackParser           GPX tracks (XXE-safe)
│   └── PolylineParser           comma-separated coordinates
├── analysis.StayPointDetector   stay point detection
└── geometry
    ├── TencentPolyline          Tencent Map compressed polyline
    ├── EncodedPolyline          Google Encoded Polyline
    ├── SphericalCentroid        weighted spherical centroid
    ├── TrackSimilarity          Fréchet / Hausdorff / DTW
    ├── Geohash                  geohash codec
    └── GeoMath                  haversine / bearing / projection / area
```

## License

Released under the [Apache License 2.0](LICENSE).
