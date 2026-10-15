# 设计与算法

[English](DESIGN.md) | **简体中文**

这份文档说明 geotrack **为什么**这么设计：数据模型、地理数学基础，以及每个模块背后的算法与权衡。用法见 [README](../README.zh-CN.md)。

## 1. 设计原则

**做薄的一层，不做平台。** geotrack 位于原始定位点和地图／业务系统之间，刻意不引入 Spring、数据库和 I/O 框架——只做解析、数学与算法。

**不可变、无业务的数据模型。** 一切都围绕同一个 `GeoPoint` record：

```java
public record GeoPoint(double lat, double lng, double altitude, int sequence) {}
```

`sequence` 表示点在轨迹内的顺序。核心库不塞租户 id、线路 id、WKT 字符串——否则业务模型会渗进底层库。调用方自行在自有实体与 `GeoPoint` 之间做映射。

**无状态静态方法。** 每个算法都是纯静态方法，没有可变状态、没有隐藏缓存。这让它们天然线程安全、易于在流水线里组合、也便于推理。涉及到顺序的地方，输出保持原始顺序，可直接入库。

**测距用球面，几何用局部平面。** 需要真实距离时用大圆（haversine）距离；纯粹局部的运算（点到线段距离、三角面积、抽稀）则先做等距圆柱投影到局部东北平面——成本低得多，且在一条轨迹段所跨越的几十公里尺度内误差远小于 0.5%。

**零外部数学依赖。** 卡尔曼平滑所需的线性代数由内部小工具 `Matrices` 实现（稠密乘、转置、带部分主元的高斯-约当求逆），不引入矩阵库。

整条处理链是一条由独立阶段组成的直线，每个阶段都可选：

```
解析 ──► 坐标转换 ──► 去噪 ──► 抽稀 ──► 分析 ──► 地图/业务
KML/GPX   WGS84/GCJ02  Kalman/RTS  RDP/VW/LTTB  相似度
polyline  BD09                                  停留点
                                                geohash/质心
```

## 2. 轨迹抽稀

抽稀是本库的核心，因此提供三种算法。它们**不可互相替代**，各自优化不同的目标。

### 2.1 Douglas-Peucker —— 点数预算下的形状保真

`TrackSimplifier` 用顶点到区间两端连线的垂距来衡量重要性，保留偏离最大的点，再对两半递归。

实现要点：

- **迭代而非递归。** 经典写法递归，在 10⁵ 点轨迹上会爆栈；这里用显式的 `ArrayDeque<int[]>` 存索引区间。
- **距离在局部平面计算。** 先做等距圆柱投影（`metersPerLat = 110540`、`metersPerLng = 111320·cos(lat)`），于是容差以**米**为单位，处处表现一致，而不是用原始角度。
- **二分搜索容差。** 保留点数随容差单调递减，因此要命中点数预算，就二分出"刚好不超过预算"的**最小**容差（≤18 轮，0.5 米停止，上限 2000 米）。固定容差梯度（1→2→5→100…）会断崖：轨迹可能从 2000 点直接塌成 2 点。
- **基准容差 = GPS 噪声。** 手持 GPS 抖动约 3–10 米，传 5 米基准先清掉无信息量的抖动；只有仍超预算时才启动二分。
- **兜底有保证。** 即使最大容差仍超预算，也退化为等间隔取样，必然不超预算且保留首尾点。

复杂度：最坏 O(n²)，典型 O(n log n)，空间 O(n)。首尾点必定保留，输出保持原始顺序。

### 2.2 Visvalingam-Whyatt —— 带层级的抽稀

Douglas-Peucker 不满足单调性：删掉一个顶点会改变后续该保留哪些顶点。Visvalingam-Whyatt 用顶点与两个邻居形成的**三角面积**打分，先删面积最小的。

关键细节是**有效面积**：某顶点被删除时，其邻居按 `max(新面积, 被删顶点的有效面积)` 重新计分。这保证了删除顺序单调，从而得到真正的**嵌套层级**——粗粒度的简化结果一定是最细粒度结果的子集，这是 DP 无法保证的。

实现：双向链表（并行的 `prev`／`next` 数组）加**惰性失效的最小堆**——过期堆项通过与顶点当前面积比较来跳过。复杂度 O(n log n)。提供两种入口：**按点数**、**按面积阈值**（m²）。

### 2.3 LTTB —— 保留视觉形状的降采样

最大三角桶（LTTB）把轨迹切成 `n − 2` 个桶，每个桶保留与"上一个选中点"和"下一桶平均值"构成三角面积最大的点。它是 **O(n)**、无需调参，是绘制超长轨迹的最佳选择。

经典 LTTB 是二维的。轨迹上直接套（x=经度、y=纬度）会把**时间分布**压没。geotrack 把它扩展到**三维**：每个点视为局部平面内的 `(索引, 东, 北)`，同时保留地理形状与时间采样分布。

## 3. GPS 去噪 —— 卡尔曼滤波 + RTS 平滑

`KalmanTrackSmoother` 在以第一个点为原点的局部东北平面里跑**匀速卡尔曼滤波**，状态为 4 维：

```
x = [ 东, 北, v东, v北 ]ᵀ
```

- **过程模型**：离散匀速 `F`。过程噪声采用连续**白噪声加速度**模型 `Q(q)`，`q = accelStd²`。
- **观测模型**：`H` 只观测位置；`R = σ²·I`，`σ` 为 GPS 精度（米）。
- **前向滤波**：标准预测/更新递推，因果、可用于流式，但看不见未来，急转弯会滞后。
- **RTS 平滑**：反向一遍，逐步算 `C_k = P_k^f Fᵀ (P_{k+1}^p)⁻¹` 与 `x_k^s = x_k^f + C_k (x_{k+1}^s − x_{k+1}^p)`。结果是整条轨迹的**最小方差估计**，离线处理明显更顺滑。

默认参数（`σ = 10 m`、`a = 0.5 m/s²`、`dt = 1 s`）针对手持／步行 GPS，均可配置。`filter` 是流式的因果变体，`smooth` 是离线变体。

## 4. 坐标转换

国内的 GCJ02（火星坐标）偏移**没有解析反函数**。正变换用标准的克拉索夫斯基椭球多项式（`A = 6378245`、`EE = 0.00669342…`）。反解用**不动点迭代**：

```
x_{k+1} = x_k + ( gcj - F(x_k) )
```

不到 10 次迭代即收敛到厘米级。BD09（百度）是在 GCJ02 之上再做一次极坐标式变换，因此库里用两跳组合出 `WGS84 ↔ BD09`。所有结果保留 6 位小数（≈0.11 米）。中国大陆以外的点原样返回（`outOfChina`）。

## 5. 轨迹相似度

三种互相独立的度量，单位都是米，时间 O(n·m)，但通过两行滚动数组把空间降到 **O(min(n, m))**：

- **离散 Fréchet** —— 保序的"遛狗"距离，比较真实路径最合适。递推式 `ca[i][j] = max(d(i,j), min(ca[i-1][j], ca[i-1][j-1], ca[i][j-1]))`。
- **Hausdorff** —— 两个点集之间的最坏不匹配（不保序）。
- **DTW** —— 保序但容忍不同速度。

## 6. 停留点检测

基于 Li et al.（2008）：从锚点起，只要点仍在距离阈值内就生长簇；若停留时间超过时间阈值，该簇即为一次停留，用质心与到离时间概括。

geotrack 相对教科书伪代码改了一处：轨迹末尾的停留簇（一直延伸到轨迹结尾）会被**输出**，而原始写法会提前一个点停下并丢掉它。`detectBySequence` 可在没有时间戳时，把 `sequence` 当作秒来运行。

## 7. 编码与空间工具

- **Encoded Polyline** —— Google 的差分 + 之字形 + 5 位变长 + 63 偏移方案，精度 5（≈1.1 米）或 6（≈11 厘米）。已对照 Google 公布的参考串校验。
- **Geohash** —— 交替 base-32 二分；解码同时返回中心点与包围盒。前缀匹配是廉价的空间近邻判断。
- **球面质心** —— 对**三维单位向量**求平均而非对经纬度求平均，因此跨越 180° 经线也正确、无纬度偏置；支持逐点权重。
- **GeoMath** —— 平均半径球面上的 haversine（相对椭球误差 < 0.5%）、局部投影、三角面积与点到线段距离。

## 8. 复杂度总览

| 模块 | 算法 | 时间 | 空间 |
| --- | --- | --- | --- |
| TrackSimplifier | Douglas-Peucker + 二分点数预算 | 典型 O(n log n)，最坏 O(n²) | O(n) |
| VisvalingamWhyatt | 有效面积堆 + 链表 | O(n log n) | O(n) |
| LttbSimplifier | 最大三角桶（三维） | O(n) | O(n) |
| KalmanTrackSmoother | 卡尔曼滤波 + RTS 平滑 | O(n) | O(n) |
| TrackSimilarity | Fréchet / Hausdorff / DTW | O(n·m) | O(min(n,m)) |
| StayPointDetector | Li et al. 簇生长 | 典型 O(n) | 额外 O(1) |

## 9. 测试与验证

45 个单元测试不只是冒烟测试，它们钉死了解析事实并对照已知参考：

- 共线轨迹恰好塌成两个点；
- 对称点集的质心为 `(0, 0)`；
- 卡尔曼平滑能可测量地降低相对真实线的偏差；
- **往返** `WGS84 → GCJ02 → WGS84` 与 `BD09 ↔ GCJ02` 能回到原值；
- `EncodedPolyline` 复现 Google 参考串 `_p~iF~ps|U_ulLnnqC_mqNvxq\`@`；
- `Geohash.encode(57.64911, 10.40744, 11)` 等于参考值 `u4pruydqqvj`；
- 合成的一条"停留—行进—停留"轨迹恰好得到两次停留。

CI 在每次 push 与 pull request 时跑全套测试（含打包）。

## 参考文献

1. D. Douglas, T. Peucker. *Algorithms for the reduction of the number of points required to represent a digitized line or its caricature*, 1973.
2. M. Visvalingam, J. Whyatt. *Line generalisation by repeated elimination of points*, 1993.
3. S. Steinarsson. *Downsampling time series for visual representation*（LTTB）, 2013.
4. R. E. Kalman. *A new approach to linear filtering and prediction problems*, 1960.
5. H. Rauch, F. Tung, C. Striebel. *Maximum likelihood estimates of linear dynamic systems*（RTS 平滑）, 1965.
6. Q. Li, Y. Zheng, et al. *Mining user similarity based on location history*（停留点）, 2008.
7. Google Maps. *Encoded Polyline Algorithm Format*.
8. G. Niemeyer. *Geohash*.
