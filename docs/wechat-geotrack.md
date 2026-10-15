# 【开源】GeoTrack：GPS 轨迹别只画在地图上，先把它处理干净

> 解析、纠偏、去噪、抽稀与空间分析，一套轻量级 Java 21 轨迹处理工具库。

作者：渤溢云拓  
系列：阿辅开源  

【配图：GeoTrack 产品能力与技术架构图】

## 阿辅开源：把轨迹数据这件事，也做成一个小而实的工具

做地图、出行、物联网，或者任何和位置有关的业务，GPS 轨迹通常都不是拿来就能用。

原始点里有噪声，同一条路可能抖成几条线；不同设备带来的坐标系不一样，叠到地图上会有偏移；一段轨迹可能有几万甚至几十万个点，传输、存储和渲染都要付出成本。

等业务继续往前走，还会遇到 KML、GPX、坐标串、地图压缩 polyline 等不同输入格式，以及轨迹相似度、停留点、中心位置这些分析需求。

最开始，大家可能只是想把线画出来。后来才发现，轨迹能画出来，不代表它已经能拿来做业务。

所以这次我们做了 **GeoTrack**：把 GPS 轨迹处理中经常要重复实现的基础能力，整理成一个纯 Java 21 工具库。

## 一、GPS 轨迹为什么总要先处理一下

最常见的情况，大概有这几类：

**轨迹抖动。** 手机、车机和穿戴设备的定位点都会有误差。直接把点连起来，路线边缘容易出现来回摆动，速度、距离等后续指标也会跟着不稳定。

**坐标不统一。** WGS84、GCJ02、BD09 经常同时出现在数据链路里。数据本身没有错，但如果没有先统一坐标系，显示出来的位置就可能对不上。

**点太多。** 一段轨迹记录几万个点并不稀奇。全部传给前端、全部存进数据库，成本会上升；但简单地每隔几个点取一个，又可能把拐弯和形状丢掉。

**格式太杂。** KML、GPX、逗号分隔坐标串、Google Encoded Polyline、腾讯地图压缩 polyline，各自都有自己的编码和结构。

> 轨迹处理的目标，不是把点删得越少越好，而是在保留形状和顺序的前提下，让数据更稳定、更轻量、更容易被业务使用。

## 二、GeoTrack 具体解决什么问题

GeoTrack 把这条链路拆成了几个可以单独使用的模块：

| 能力 | 解决的问题 |
| --- | --- |
| KML / GPX / 坐标串解析 | 把不同来源的数据统一成 `GeoPoint` 轨迹点 |
| WGS84 / GCJ02 / BD09 转换 | 统一坐标系，支持批量转换 |
| Kalman 滤波 + RTS 平滑 | 降低 GPS 噪声，让路线更稳定 |
| Douglas-Peucker / Visvalingam-Whyatt / LTTB | 在保留形状的情况下抽稀和降采样 |
| Fréchet / Hausdorff / DTW | 比较两条轨迹的相似程度 |
| Geohash / 球面质心 | 做空间编码和中心位置计算 |
| 停留点检测 | 找出轨迹中的停留位置、到离时间和持续时长 |
| Encoded Polyline / 腾讯 polyline | 和地图服务交换更紧凑的轨迹数据 |

它们可以组合成一条很清楚的数据流：

**导入 → 解析 → 坐标转换 → 去噪 → 抽稀 → 空间分析 → 地图或业务系统。**

这个顺序也不是固定的。只需要坐标转换，可以单独调用转换工具；只想把前端显示的点数压下来，可以直接使用 LTTB 或轨迹抽稀；需要做离线分析，则可以把滤波、相似度和停留点检测组合起来。

## 三、它和一个完整地图 SDK 有什么区别

GeoTrack 不是地图 SDK，也不是一个带页面的轨迹管理系统。

它负责的是地图和业务系统之前的那一层：把原始位置数据处理成更可靠、更适合计算和传输的轨迹数据。

这样做有几个好处。

第一，依赖很少。项目不需要 Spring，也不需要数据库，核心能力基于 JDK 和少量第三方库构建。

第二，调用方式简单。模块以静态工具方法为主，没有隐藏状态，适合放进服务端、批处理程序、桌面工具，或者你自己的数据管道里。

第三，算法边界清楚。抽稀、滤波、坐标转换、解析和空间分析各自独立，出了问题容易定位，也方便替换参数。

第四，轨迹点模型保持简单。`GeoPoint` 只关心纬度、经度、高度和序号，不把业务字段强行绑进底层库。

## 四、几段代码，把一条轨迹处理完

比如，先从 GPX 读取轨迹，再做坐标转换、RTS 平滑，最后压到最多 500 个点：

```java
import com.boyicloud.geotrack.filter.KalmanTrackSmoother;
import com.boyicloud.geotrack.parse.GpxTrackParser;
import com.boyicloud.geotrack.simplify.TrackSimplifier;
import com.boyicloud.geotrack.transform.CoordinateTransform;

var raw = GpxTrackParser.parse("route.gpx");
var converted = CoordinateTransform.transformBatch(raw);
var smooth = KalmanTrackSmoother.smooth(converted);
var ready = TrackSimplifier.simplify(smooth, 500, 5D);
```

如果手里没有 GPX，也可以直接解析坐标串：

```java
import com.boyicloud.geotrack.parse.PolylineParser;

var points = PolylineParser.parse(
        "39.9000,116.4000,50,39.9100,116.4100,52");
```

如果需要找出一条轨迹里的停留位置：

```java
import com.boyicloud.geotrack.analysis.StayPointDetector;

var stays = StayPointDetector.detectBySequence(points, 50D, 300D);
```

这些方法不会启动服务，也不会要求你先搭一套框架。对一个已经存在的 Java 项目来说，加入依赖、传入轨迹点，就可以开始处理。

## 五、为什么把它开源出来

轨迹处理看起来是基础能力，但它经常散落在不同项目里：一份坐标转换代码在服务 A，一份抽稀代码在服务 B，某个格式解析器又藏在另一个脚本里。

时间久了，大家都在重复解决同样的问题，却很难知道每一段代码的边界和假设。

GeoTrack 想做的是先把这一层整理清楚：接口小一点，依赖少一点，算法说明和测试跟着代码走。你可以直接使用，也可以读源码、换参数、按自己的业务继续扩展。

项目采用 **Apache License 2.0**，当前版本为 `0.1.0`，暂未发布到 Maven Central。欢迎通过 Issue 和 Pull Request 一起补充更多轨迹格式、算法和使用场景。

## 六、从源码开始

环境要求：

- JDK 21+
- Maven 3.9+

```bash
git clone https://github.com/vic-998/geotrack.git
cd geotrack
mvn clean package
```

项目地址：

**https://github.com/vic-998/geotrack**

## 写在最后

GeoTrack 做的事情不大：不替你画地图，也不替代完整 GIS 平台。

它只是把一条原始 GPS 轨迹，先处理成更稳定、更轻量、更适合分析的样子。

很多位置业务，真正需要的也许就是这一层基础能力。

如果你正在做轨迹回放、里程计算、路线比较、停留识别，或者只是想把一堆定位点整理干净，欢迎试试 GeoTrack。

觉得有用，可以点个 Star；遇到问题，欢迎提 Issue；如果你有自己的轨迹算法，也欢迎一起补进来。

**阿辅开源，继续把小工具做扎实。**

