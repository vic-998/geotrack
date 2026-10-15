# Design & algorithms

[English](DESIGN.md) | [简体中文](DESIGN.zh-CN.md)

This document explains *why* geotrack is built the way it is: the data model,
the geographic math foundation, and the algorithm and trade-offs behind every
module. For usage, see the [README](../README.md).

## 1. Design principles

**A thin layer, not a platform.** geotrack sits between raw location points and
the map / business system. It deliberately has no Spring, no database, no I/O
framework — parsing, math and algorithms only.

**Immutable, business-free data model.** Everything operates on a single
`GeoPoint` record:

```java
public record GeoPoint(double lat, double lng, double altitude, int sequence) {}
```

`sequence` defines the order inside a track. No tenant id, no line id, no WKT
string is baked into the core — the domain model would otherwise leak into the
library. Callers map their own entities to/from `GeoPoint`.

**Stateless static utilities.** Every algorithm is a pure static method with no
mutable state and no hidden caches. This makes them thread-safe, trivially
composable in a pipeline, and easy to reason about. Where order matters, the
output preserves the original order so results can be persisted directly.

**Sphere for distance, local plane for geometry.** Great-circle (haversine)
distances are used where a true distance is needed. Where the operation is
purely local (point-to-segment distance, triangle area, simplification), an
equirectangular projection into a local east/north plane is used instead, which
is far cheaper and, over the tens of kilometers a track segment spans, is
accurate to well under 0.5%.

**Zero external math dependencies.** The linear algebra needed by the Kalman
smoother is implemented in a small internal `Matrices` helper (dense multiply,
transpose and Gauss-Jordan inversion with partial pivoting) rather than pulling
in a matrix library.

The processing pipeline is intentionally a straight line of independent stages;
each stage is optional:

```
parse ──► transform ──► denoise ──► simplify ──► analyze ──► map / business
KML/GPX    WGS84/GCJ02  Kalman/RTS   RDP/VW/LTTB   similarity
polyline   BD09                                    stay points
                                                   geohash / centroid
```

## 2. Track simplification

Simplification is the heart of the library, so three different algorithms are
offered. They are not interchangeable: they optimize for different things.

### 2.1 Douglas-Peucker — shape fidelity under a point budget

`TrackSimplifier` measures a vertex by its perpendicular distance to the chord
between the current range endpoints and keeps the worst-offending vertex,
recursing on both halves.

Implementation choices:

- **Iterative, not recursive.** The classic formulation recurses; on a 10⁵-point
  track that overflows the call stack. The implementation uses an explicit
  `ArrayDeque<int[]>` of index ranges instead.
- **Distance in a local plane.** Point-to-segment distance is computed after an
  equirectangular projection (`metersPerLat = 110540`,
  `metersPerLng = 111320·cos(lat)`) so the tolerance is expressed in **meters**
  and behaves the same everywhere, instead of in raw degrees.
- **Tolerance by binary search.** Kept-point count is monotonically decreasing in
  the tolerance, so to hit a point budget the code binary-searches the *smallest*
  tolerance that stays under the budget (≤18 rounds, 0.5 m stop, up to 2000 m).
  A fixed tolerance ladder (1 → 2 → 5 → 100 …) produces cliff artifacts where a
  track drops from 2000 points to 2.
- **Baseline tolerance = GPS noise.** Since handheld GPS jitter is 3–10 m,
  passing a 5 m baseline first removes jitter that carries no real information;
  the binary search only kicks in if the result is still over budget.
- **Guaranteed fallback.** If even the maximum tolerance exceeds the budget, the
  code degrades to even index sampling, which always fits and keeps the
  endpoints.

Complexity: worst case O(n²), typical O(n log n); O(n) memory. First and last
points are always kept and output stays in original order.

### 2.2 Visvalingam-Whyatt — a simplification *hierarchy*

Douglas-Peucker is not monotone: removing one vertex can change which vertices a
second pass would keep. Visvalingam-Whyatt scores each vertex by the **triangle
area** it forms with its two neighbours and removes the smallest area first.

The key detail is the **effective area**: when a vertex is removed, its two
neighbours are re-scored as `max(newArea, areaOfRemovedVertex)`. This keeps the
removal order monotone and gives a genuine nesting hierarchy — a coarser
simplification is always a subset of a finer one, which DP cannot promise.

Implementation: a doubly linked list (parallel `prev` / `next` arrays) plus a min
**heap with lazy invalidation** — stale heap entries are skipped by comparing the
stored area with the vertex's current area. Complexity O(n log n). Two entry
points: *by vertex count* and *by area threshold* (m²).

### 2.3 LTTB — downsampling that keeps the visual shape

Largest-Triangle-Three-Buckets divides the track into `n − 2` buckets and, per
bucket, keeps the point that maximizes the triangle area formed with the
previously selected point and the average of the next bucket. It is **O(n)**, has
no threshold to tune, and is the best choice for plotting huge tracks.

The classic LTTB is 2-D. A naïve trajectory adaptation (x = longitude, y =
latitude) collapses the *time* distribution. geotrack extends it to **3-D** by
treating each point as `(index, east, north)` in a local plane, so both the
geographic shape and the sampling-in-time distribution are preserved.

## 3. GPS denoising — Kalman filter + RTS smoother

`KalmanTrackSmoother` runs a **constant-velocity Kalman filter** in a local
east/north plane anchored at the first point, with a 4-state vector:

```
x = [ east, north, vEast, vNorth ]ᵀ
```

- **Process model.** Discrete constant velocity `F`. The process noise follows the
  continuous **white-noise-acceleration** model, `Q(q)` with `q = accelStd²`.
- **Measurement model.** `H` observes position only; `R = σ²·I` where `σ` is the
  GPS accuracy in meters.
- **Forward filter.** The standard predict/update recursion. This is causal and
  usable for streaming, but lags behind sharp turns because it cannot see the
  future.
- **RTS smoother.** A backward pass computes, per step,
  `C_k = P_k^f Fᵀ (P_{k+1}^p)⁻¹` and
  `x_k^s = x_k^f + C_k (x_{k+1}^s − x_{k+1}^p)`. The result is the
  minimum-variance estimate over the whole track — noticeably smoother for
  offline processing.

Defaults (`σ = 10 m`, `a = 0.5 m/s²`, `dt = 1 s`) target handheld / pedestrian
GPS and are fully configurable. `filter` is the causal variant for streaming,
`smooth` the offline variant.

## 4. Coordinate transforms

The Chinese GCJ02 ("Mars") offset has **no closed-form inverse**. The forward
transform uses the standard Krasovsky-ellipsoid polynomial
(`A = 6378245`, `EE = 0.00669342…`). The inverse is solved by **fixed-point
iteration**:

```
x_{k+1} = x_k + ( gcj - F(x_k) )
```

which converges to centimeter accuracy in under 10 iterations. BD09 (Baidu) is a
further polar-style transform on top of GCJ02, so the library composes
`WGS84 ↔ BD09` from the two hops. All results are rounded to 6 decimals
(≈0.11 m). Points outside mainland China are returned unchanged (`outOfChina`).

## 5. Trajectory similarity

Three independent metrics, all in meters, all O(n·m) time but **O(min(n, m))
space** via two rolling rows:

- **Discrete Fréchet** — the order-preserving "dog-walking" distance; the best fit
  for comparing actual paths. Recurrence
  `ca[i][j] = max(d(i,j), min(ca[i-1][j], ca[i-1][j-1], ca[i][j-1]))`.
- **Hausdorff** — the worst mismatch between the two point sets (order-insensitive).
- **DTW** — order-preserving but tolerant to different speeds.

## 6. Stay point detection

Based on Li et al. (2008): starting from an anchor, a cluster grows while points
stay within a distance threshold; if the dwell time exceeds a time threshold the
cluster becomes a stay, summarized by its centroid and arrival/departure times.

geotrack improves on the textbook pseudo-code in one place: the trailing cluster
(when a stay reaches the end of the track) is **emitted**, whereas the original
stops one point early and drops it. `detectBySequence` lets you run without
timestamps by treating `sequence` as seconds.

## 7. Encoding & spatial helpers

- **Encoded polyline** — Google's delta + zig-zag + 5-bit varint + 63-offset
  scheme, precision 5 (≈1.1 m) or 6 (≈11 cm). Validated against Google's published
  reference vector.
- **Geohash** — interleaved base-32 bisection; decode returns both the center and
  the bounding box. Prefix matching is a cheap spatial-proximity test.
- **Spherical centroid** — averaging **3-D unit vectors** instead of lat/lng, so it
  is correct across the antimeridian and free of latitude bias; supports
  per-point weights.
- **GeoMath** — haversine on a mean-radius sphere (error < 0.5% vs. an
  ellipsoid), local projection, triangle area and point-to-segment distance.

## 8. Complexity summary

| Module | Algorithm | Time | Space |
| --- | --- | --- | --- |
| TrackSimplifier | Douglas-Peucker + binary-search budget | O(n log n) typical, O(n²) worst | O(n) |
| VisvalingamWhyatt | effective-area heap + linked list | O(n log n) | O(n) |
| LttbSimplifier | largest-triangle-three-buckets (3-D) | O(n) | O(n) |
| KalmanTrackSmoother | Kalman filter + RTS smoother | O(n) | O(n) |
| TrackSimilarity | Fréchet / Hausdorff / DTW | O(n·m) | O(min(n,m)) |
| StayPointDetector | Li et al. cluster growing | O(n) typical | O(1) extra |

## 9. Testing & validation

The 45 unit tests are not just smoke tests. They pin down analytic truths and
cross-check against known references:

- collinear tracks collapse to exactly two points;
- a symmetric point set has centroid `(0, 0)`;
- the Kalman smoother measurably reduces deviation from the true line;
- **round-trips** `WGS84 → GCJ02 → WGS84` and `BD09 ↔ GCJ02` return the original;
- `EncodedPolyline` reproduces Google's reference string `_p~iF~ps|U_ulLnnqC_mqNvxq\`@`;
- `Geohash.encode(57.64911, 10.40744, 11)` equals the reference `u4pruydqqvj`;
- a synthesized stay-and-transit track yields exactly two stays.

CI runs the whole suite (plus packaging) on every push and pull request.

## References

1. D. Douglas, T. Peucker. *Algorithms for the reduction of the number of points
   required to represent a digitized line or its caricature*, 1973.
2. M. Visvalingam, J. Whyatt. *Line generalisation by repeated elimination of
   points*, 1993.
3. S. Steinarsson. *Downsampling time series for visual representation* (LTTB), 2013.
4. R. E. Kalman. *A new approach to linear filtering and prediction problems*, 1960.
5. H. Rauch, F. Tung, C. Striebel. *Maximum likelihood estimates of linear dynamic
   systems* (RTS smoother), 1965.
6. Q. Li, Y. Zheng, et al. *Mining user similarity based on location history*
   (stay points), 2008.
7. Google Maps. *Encoded Polyline Algorithm Format*.
8. G. Niemeyer. *Geohash*.
