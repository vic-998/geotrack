# geotrack

[English](README.md) | **简体中文**

轻量级 Java (21) GPS 轨迹处理工具库：轨迹抽稀压缩（道格拉斯-普克 / Visvalingam-Whyatt / LTTB）、卡尔曼滤波与 RTS 平滑、WGS84/GCJ02/BD09 坐标互转、KML/GPX 解析、Encoded Polyline 编解码、轨迹相似度（Fréchet / Hausdorff / DTW）、Geohash、停留点检测等。

- 无 Spring / 无数据库依赖，纯 JDK + 少量第三方库
- 基于 Java 21（使用 record、模式匹配 switch 等）
- 所有算法为静态工具方法，线程安全、无隐藏状态
- 轨迹抽稀采用迭代实现（不爆栈）、保序、二分容差搜索
- 卡尔曼滤波实现前向因果滤波 + 后向 RTS 平滑，矩阵运算零依赖

## 环境要求

- JDK 21+
- Maven 3.9+（构建）

## 安装

Maven：

```xml
<dependency>
    <groupId>com.boyicloud</groupId>
    <artifactId>geotrack</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle：

```groovy
implementation 'com.boyicloud:geotrack:0.1.0'
```

也可以直接从 [`Releases`](https://github.com/vic-998/geotrack/releases) 下载
`geotrack-0.1.0.jar`，或者本地 `mvn install` 后引用。

## 快速开始

### 1. 轨迹抽稀 / 压缩

```java
import com.boyicloud.geotrack.model.GeoPoint;
import com.boyicloud.geotrack.simplify.TrackSimplifier;

List<GeoPoint> raw = List.of(
        new GeoPoint(39.9000, 116.4000, 0, 0),
        new GeoPoint(39.9001, 116.4001, 0, 1),
        // ...
        new GeoPoint(39.9100, 116.4100, 0, 999));

// 压到最多 500 个点，基准容差 5 米（先按 GPS 噪声去抖，超预算再二分压）
List<GeoPoint> simplified = TrackSimplifier.simplify(raw, 500, 5D);

// 只按固定容差去噪，不限制点数
List<GeoPoint> denoised = TrackSimplifier.simplifyByTolerance(raw, 5D);

// 直接用道格拉斯-普克
List<GeoPoint> dp = TrackSimplifier.douglasPeucker(raw, 5D);
```

### 2. 坐标纠偏（WGS84 -> GCJ02）

```java
import com.boyicloud.geotrack.transform.CoordinateTransform;

double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
// gcj[0] = 经度, gcj[1] = 纬度

// 批量转换整条轨迹（保留 altitude / sequence）
List<GeoPoint> converted = CoordinateTransform.transformBatch(raw);
```

### 3. 解析 KML / GPX

```java
import com.boyicloud.geotrack.parse.KmlTrackParser;
import com.boyicloud.geotrack.parse.GpxTrackParser;

List<GeoPoint> fromKml = KmlTrackParser.parse("route.kml");
List<GeoPoint> fromGpx = GpxTrackParser.parse("route.gpx");
```

> 解析器只负责读取原始坐标，不做坐标系转换。若源文件是 WGS84 而业务需要
> GCJ02，解析后调用 `CoordinateTransform.transformBatch(...)` 即可。

### 4. 解析逗号分隔坐标串

```java
import com.boyicloud.geotrack.parse.PolylineParser;

// "lat,lng,alt" 或 "lat,lng"
List<GeoPoint> points = PolylineParser.parse("39.9,116.4,50,39.91,116.41,52");
```

### 5. 腾讯地图压缩 polyline 解压

```java
import com.boyicloud.geotrack.geometry.TencentPolyline;

List<double[]> coords = TencentPolyline.decode(compressedPolyline); // [lat, lng]
List<GeoPoint> points = TencentPolyline.decodeToPoints(compressedPolyline);
```

### 6. 球面质心

```java
import com.boyicloud.geotrack.geometry.SphericalCentroid;

double[] center = SphericalCentroid.centroid(points);              // [lat, lng]
double[] weighted = SphericalCentroid.weightedCentroid(points, weights);
```

### 7. 卡尔曼滤波 / RTS 平滑（GPS 去噪）

```java
import com.boyicloud.geotrack.filter.KalmanTrackSmoother;

// 在线（因果）滤波：常数速度模型，4 维状态 [east, north, vEast, vNorth]
List<GeoPoint> online = KalmanTrackSmoother.filter(raw);

// 离线最小方差估计：前向卡尔曼 + 后向 RTS 平滑，轨迹更顺滑
List<GeoPoint> offline = KalmanTrackSmoother.smooth(raw);

// 自定义调参：加速度标准差(m/s^2)、GPS 测量噪声(m)、时间步长(s)
List<GeoPoint> tuned = KalmanTrackSmoother.smooth(raw, 0.5, 10.0, 1.0);
```

### 8. Visvalingam-Whyatt 面积抽稀

```java
import com.boyicloud.geotrack.simplify.VisvalingamWhyatt;

// 按点数抽稀：优先删除"有效三角面积"最小的顶点
List<GeoPoint> vw = VisvalingamWhyatt.simplifyToCount(raw, 200);

// 按面积阈值抽稀（单位：平方米）
List<GeoPoint> byArea = VisvalingamWhyatt.simplifyByArea(raw, 25.0);
```

### 9. LTTB 降采样（保留视觉形状）

```java
import com.boyicloud.geotrack.simplify.LttbSimplifier;

// 三维（索引 + 局部东北平面）最大三角桶降采样，O(n)
List<GeoPoint> sampled = LttbSimplifier.downsample(raw, 500);
```

### 10. Google Encoded Polyline 编解码

```java
import com.boyicloud.geotrack.geometry.EncodedPolyline;

String encoded = EncodedPolyline.encode(points);        // 精度 5
List<GeoPoint> decoded = EncodedPolyline.decode(encoded);
String highRes = EncodedPolyline.encode(points, 6);     // 精度 6
```

### 11. 轨迹相似度（Fréchet / Hausdorff / DTW）

```java
import com.boyicloud.geotrack.geometry.TrackSimilarity;

double frechet = TrackSimilarity.discreteFrechet(trackA, trackB); // 米，保序
double hausdorff = TrackSimilarity.hausdorff(trackA, trackB);     // 米，不保序
double dtw = TrackSimilarity.dtw(trackA, trackB);                 // 米，允许时间伸缩
```

### 12. Geohash 编解码

```java
import com.boyicloud.geotrack.geometry.Geohash;

String hash = Geohash.encode(39.9, 116.4, 9);
double[] center = Geohash.decode(hash);           // [lat, lng]
double[][] bounds = Geohash.decodeBounds(hash);   // [[minLat,maxLat],[minLng,maxLng]]
```

### 13. 停留点检测（Li et al. 2008）

```java
import com.boyicloud.geotrack.analysis.StayPointDetector;

// 真实时间戳（毫秒）
List<StayPointDetector.StayPoint> stays =
        StayPointDetector.detect(points, 50D, 300D, timestampsMillis);

// 没有时间戳时，用 sequence 当作秒
List<StayPointDetector.StayPoint> bySeq =
        StayPointDetector.detectBySequence(points, 50D, 300D);

for (var stay : stays) {
    System.out.println(stay.lat() + "," + stay.lng() + " 停留 " + stay.durationSeconds() + "s");
}
```

### 14. 坐标系转换扩展

```java
import com.boyicloud.geotrack.transform.CoordinateTransform;

// WGS84 -> GCJ02，以及迭代反解
double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
double[] wgs = CoordinateTransform.transformGCJ02ToWGS84(gcj[0], gcj[1]);

// 百度 BD09 全家桶
double[] bd = CoordinateTransform.transformWGS84ToBD09(116.397428, 39.90923);
double[] back = CoordinateTransform.transformBD09ToWGS84(bd[0], bd[1]);
```

## 模块结构

```
com.boyicloud.geotrack
├── model.GeoPoint              轨迹点（lat/lng/altitude/sequence）
├── simplify
│   ├── TrackSimplifier         道格拉斯-普克抽稀、二分容差搜索、等间隔兜底
│   ├── VisvalingamWhyatt      面积抽稀（有效三角面积，O(n log n)）
│   └── LttbSimplifier         LTTB 降采样（O(n)，保形）
├── filter.KalmanTrackSmoother  卡尔曼滤波 + RTS 平滑（GPS 去噪）
├── transform.CoordinateTransform  WGS84 / GCJ02 / BD09 坐标互转
├── parse.*                     KML / GPX / 逗号坐标串解析（GPX 防 XXE）
├── analysis.StayPointDetector  停留点检测
└── geometry
    ├── TencentPolyline         腾讯地图压缩 polyline 解压
    ├── EncodedPolyline         Google Encoded Polyline 编解码
    ├── SphericalCentroid       加权球面质心
    ├── TrackSimilarity         Fréchet / Hausdorff / DTW
    ├── Geohash                 Geohash 编解码
    └── GeoMath                 测地数学（haversine/方位角/投影/三角面积…）
```

## 构建与打包

```bash
# 编译 + 跑测试 + 打包（产出 target/geotrack-0.1.0.jar）
mvn clean package

# 跳过测试
mvn clean package -DskipTests

# 安装到本地仓库，供其他项目引用
mvn clean install
```

`package` 阶段会同时产出 `geotrack-0.1.0.jar`、`-sources.jar`、`-javadoc.jar`。

## 发布到 Maven Central

`pom.xml` 已配置好 `maven-source-plugin`、`maven-javadoc-plugin`、`maven-gpg-plugin`
以及 `distributionManagement`（OSSRH）。发布前请：

1. 将 `pom.xml` 中的 `groupId`、`url`、`scm`、`developers` 改成你自己的信息
   （Maven Central 要求 groupId 对应可验证的域名所有权，`com.boyicloud` 需要你拥有该域）；
2. 在 `~/.m2/settings.xml` 中配置 `ossrh` 账户；
3. 准备 GPG 密钥并发布：

```bash
mvn clean deploy -Dgpg.passphrase=你的口令
```

只签名不发布时可用 `mvn verify`。本地构建时用 `-Dgpg.skip=true` 跳过签名。

## License

[Apache License 2.0](LICENSE)
