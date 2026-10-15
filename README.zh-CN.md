# geotrack

[![CI](https://github.com/vic-998/geotrack/actions/workflows/ci.yml/badge.svg)](https://github.com/vic-998/geotrack/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.boyicloud/geotrack.svg)](https://central.sonatype.com/artifact/com.boyicloud/geotrack)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)

[English](README.md) | **简体中文**

轻量级 Java 21 GPS 轨迹处理工具库：解析、纠偏、去噪、抽稀与空间分析——覆盖从原始定位点到地图／业务系统之间的那一层。

![GeoTrack 能力与架构总览](design/geotrack-overview.svg)

## 能力

| 能力 | 解决的问题 |
| --- | --- |
| KML / GPX / 坐标串解析 | 把不同来源的数据统一成 `GeoPoint` 轨迹点 |
| WGS84 · GCJ02 · BD09 转换 | 统一坐标系，支持批量转换 |
| Kalman 滤波 + RTS 平滑 | 降低 GPS 噪声，让路线更稳定 |
| Douglas-Peucker / Visvalingam-Whyatt / LTTB | 在保留形状的前提下抽稀与降采样 |
| Fréchet / Hausdorff / DTW | 比较两条轨迹的相似程度 |
| Geohash / 球面质心 | 空间编码与中心位置计算 |
| 停留点检测 | 找出停留位置、到离时间与持续时长 |
| Encoded Polyline / 腾讯 polyline | 与地图服务交换更紧凑的轨迹数据 |

无 Spring、无数据库，核心基于 JDK 与少量第三方库。所有算法都是静态、线程安全的方法，没有隐藏状态，可以直接放进服务端、批处理程序、桌面工具或你自己的数据管道。

## 环境要求

- Java 21+

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

## 快速开始

把一条 GPX 原始轨迹，处理成可直接渲染、去噪并压到最多 500 个点：

```java
import com.boyicloud.geotrack.filter.KalmanTrackSmoother;
import com.boyicloud.geotrack.parse.GpxTrackParser;
import com.boyicloud.geotrack.simplify.TrackSimplifier;
import com.boyicloud.geotrack.transform.CoordinateTransform;

var raw       = GpxTrackParser.parse("route.gpx");
var converted = CoordinateTransform.transformBatch(raw); // WGS84 -> GCJ02
var smooth    = KalmanTrackSmoother.smooth(converted);   // 去除 GPS 噪声
var ready     = TrackSimplifier.simplify(smooth, 500, 5D);
```

各步骤相互独立，按需取用。更多示例：

```java
// 轨迹抽稀（道格拉斯-普克）
List<GeoPoint> dp = TrackSimplifier.simplifyByTolerance(raw, 5D);

// Visvalingam-Whyatt 面积抽稀
List<GeoPoint> vw = VisvalingamWhyatt.simplifyToCount(raw, 200);

// LTTB 降采样，保留视觉形状
List<GeoPoint> lttb = LttbSimplifier.downsample(raw, 500);

// 坐标系转换
double[] gcj = CoordinateTransform.transformWGS84ToGCJ02(116.397428, 39.90923);
double[] bd  = CoordinateTransform.transformWGS84ToBD09(116.397428, 39.90923);

// 轨迹相似度（单位：米）
double frechet = TrackSimilarity.discreteFrechet(trackA, trackB);

// 停留点检测
var stays = StayPointDetector.detectBySequence(points, 50D, 300D);

// 与地图服务交换紧凑轨迹
String encoded = EncodedPolyline.encode(points, 6);
```

## 模块概览

```
com.boyicloud.geotrack
├── model.GeoPoint              轨迹点（纬度 / 经度 / 高度 / 序号）
├── simplify
│   ├── TrackSimplifier         道格拉斯-普克抽稀（点数预算拟合）
│   ├── VisvalingamWhyatt       有效面积抽稀
│   └── LttbSimplifier          LTTB 降采样
├── filter.KalmanTrackSmoother  卡尔曼滤波 + RTS 平滑
├── transform.CoordinateTransform  WGS84 / GCJ02 / BD09 坐标互转
├── parse
│   ├── KmlTrackParser          KML 轨迹
│   ├── GpxTrackParser          GPX 轨迹（防 XXE）
│   └── PolylineParser          逗号分隔坐标串
├── analysis.StayPointDetector  停留点检测
└── geometry
    ├── TencentPolyline         腾讯地图压缩 polyline
    ├── EncodedPolyline         Google Encoded Polyline
    ├── SphericalCentroid       加权球面质心
    ├── TrackSimilarity         Fréchet / Hausdorff / DTW
    ├── Geohash                 Geohash 编解码
    └── GeoMath                 测地数学（haversine / 方位角 / 投影 / 面积）
```

## License

基于 [Apache License 2.0](LICENSE) 开源。
