# 庆祝动画性能验证

## 结果与适用范围

2026-09-21，Samsung SM-A146P（Android 15，1080×2408，density 2.8125，约 90 Hz）实测：
在相同 ART `speed` 编译条件和固定密集种子下，GPU 版本三次播放均通过完整帧性能门。
两轮热播放的完整帧 P95 均值从 65.95 ms 降至 23.23 ms，降低约 65%。

| 绘制路径 | 首次完整帧 P95 | 热播放 1 P95 | 热播放 2 P95 | 三轮错过截止时间 | 性能门 |
| --- | ---: | ---: | ---: | --- | --- |
| 原 Canvas | 62.89 ms | 64.23 ms | 67.67 ms | 22/130、22/132、23/131 | FAIL |
| GPU Mesh | 20.94 ms | 23.42 ms | 23.04 ms | 0/165、0/165、0/164 | PASS |

GPU 版本三轮主线程绘制 P95 为 6.16 / 6.00 / 5.48 ms，指令提交 P95 为
3.29 / 4.86 / 4.02 ms；Canvas 指令提交 P95 约 20 ms。主要收益来自减少尾迹绘制提交开销。

GPU 路径用于 **Android 14 / API 34 及以上的硬件加速 Canvas**；更旧系统和软件 Canvas
继续使用原绘制路径，不能套用本次 GPU 提升数据。库的最低系统版本不变。

## 实现与视觉约束

Compose 与 Android View 共用渲染器。保留粒子数量、轨迹、爆炸层次、颜色、光晕、透明度、
绘制顺序与 4.2 秒时序，以及关闭系统动画后的 0.8 秒静止星星淡入淡出。
按已确认的验收范围，允许少量抗锯齿边缘像素差异。

- 流式传递模拟结果，复用尾迹坐标、法线和颜色数组，避免逐帧快照、元素和点列表分配。
- 将原始星星、阴影和光晕素材打包到共享纹理；保留原始纹理像素、透明边距和叠加顺序。
- 用有容量上限的 Mesh 批次绘制星星、光晕与爆炸尾迹；保留尾迹原采样点、宽度、渐变节点。
- 少量流星尾迹仍使用原 Canvas 路径，提交前先清空 Mesh 队列，保持层叠关系。
- 播放前请求纹理上传；缓存几何缓冲区，批次过大时按顺序分段提交。Mesh 等框架对象仍有分配。

## 回归验证

软件 Canvas：冻结优化前的模拟器与渲染器，63 个固定画面逐像素完全一致，包括两种种子、
不同尺寸/密度、横竖布局、流星/爆炸/星星/结束阶段、关闭系统动画与渲染器复用。
图集测试验证半透明素材像素、透明边距及换行排布。

硬件 Canvas：在同一设备、同一固定时刻，用 PixelCopy 分别捕获原 Canvas 与 GPU 结果。
40 组对照全部通过：覆盖深浅背景、两个种子、不同尺寸/密度、结束、重播、减少动态，以及强制小批次后复用。
验收要求 RGB 平均绝对误差 ≤ 0.1（0–255 标度）、任一通道差值 >16 的像素占比 ≤0.1%，
且参考图局部平坦区域不出现通道差值 >8 的像素。该检查属于采样回归，不能证明所有 GPU
和随机种子绝对一致。本次最大平均误差 0.0638/255，最大强差异像素占比 0.0284%，
平坦区域差异计数为 0。PNG 对照图由下述脚本保存在本地报告目录。

本次原始数值记录：[Canvas](performance-records/2026-09-21-canvas.txt)、
[GPU](performance-records/2026-09-21-gpu.txt)、[画面对比](performance-records/2026-09-21-visual.txt)。

Debug / Release 各 36 项单元测试、Lint、库和示例构建通过。

## 真机复现

```bash
./scripts/check-celebration-performance.sh canvas
./scripts/check-celebration-performance.sh gpu
./scripts/check-celebration-visual.sh
```

性能测试需要 Android 12+；GPU 路径和硬件画面对比需要 Android 14+。
测试期间保持设备解锁、测试页面前台。脚本构建并安装独立、不可调试的 benchmark APK，
直接编译库的生产源文件；执行 `cmd package compile -m speed -f` 后检查实际包状态为
`status=speed`，不满足则停止，避免 JIT 历史影响公平对照。只改变 benchmark 包的编译状态。

固定种子 `327942116865947580`，遮罩 0.25，连续播放三次；统计动画 0.65–2.5 秒的
主线程绘制、相邻绘制间隔，以及 `FrameMetrics` 完整帧、GPU、提交和同步耗时。
三轮（包含首轮）均要求：绘制 P95 ≤16.67 ms、间隔 P95 ≤25 ms、完整帧 P95 ≤33.34 ms、
错过系统截止时间比例 ≤1%，至少 30 个完整帧样本，且不能丢失测量回调。
脚本在不达标或运行中断时返回非零值。

报告位置：`benchmark/build/reports/celebration-performance-{canvas,gpu}.txt`、
`celebration-visual.txt`、`celebration-visual/`（对照 PNG）和 `compilation.txt`。

构建与回归命令：

```bash
./gradlew :aparticle:testDebugUnitTest :aparticle:testReleaseUnitTest lint \
  :aparticle:assembleRelease :example:assembleDebug :example:assembleRelease \
  :benchmark:assembleRelease --offline
```

## 示例实际入口复测

同日安装当前 Release 示例，进程重启后通过实际按钮分别播放 Compose、View 各两次；
没有对示例执行强制 `speed` 编译。此为覆盖安装，保留既有 ART/profile 历史，不冒充全新安装测试。

| 入口 | 爆炸阶段完整帧 P95（两次） | 系统 Janky frames（两次） |
| --- | --- | --- |
| Compose | 21.02 / 25.79 ms | 2/374（0.53%）、2/376（0.53%） |
| View | 20.80 / 16.00 ms | 1/373（0.27%）、1/373（0.27%） |

分段抓取的 `gfxinfo framestats` 样本均未出现 >33.34 ms 的完整帧；Compose 第二轮样本中
有 2 帧超过系统截止时间，其他三轮样本为 0。系统累计 Janky frames 与分段样本统计口径不同，
抓取缓冲区没有覆盖每一帧，因此保留两种数据，不宣称零掉帧。示例使用随机种子，不能替代上表
固定种子的公平前后对照。[原始摘要](performance-records/2026-09-21-example.json)。

## 测量边界与历史修正

早期只测 `onDraw` 的性能门曾报 PASS，但遗漏渲染线程长帧，不能用于证明卡顿修复。
旧的可调试 AndroidTest APK 执行预编译命令后实际仍为 `verify`，此前“相同预编译条件”及
约 47% CPU 提升的描述缺乏公平编译条件，不再作为结论。上表来自独立 Release 测试包，
已确认双方实际处于 `speed` 状态。

`FrameMetrics` 与系统截止时间用于评估应用帧，不是所有宿主和设备的屏幕呈现保证。
本性能门通过也不等于稳定 90 fps；单轮仍可能出现 CPU 长帧（本次最大 22.39 ms）。
AOT 测试中的“首次播放”不是首次安装、未预编译宿主的冷启动保证，需另测实际入口。
