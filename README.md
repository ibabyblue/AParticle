# AParticle

面向 Jetpack Compose 与 Android View 的原生粒子动画 AAR，参考 iOS
`IParticle` 的行为契约，并使用 Android 自己的 Canvas、生命周期和动画缩放机制实现。

当前包含三类效果：

- `riseAndBurst`：一个形状从画布底部沿轻微曲线上升，在上半部散开并淡出。
- `atmosphere`：透明的持续氛围层，包含漂移星光、边缘气泡和呼吸式玫红光晕。
- `celebration`：流星沿曲线入场、主爆炸与局部二次爆炸、持续运动的闪烁星星，4.2 秒后结束。

Compose 与 View 共用确定性粒子规划和物理模拟，但各自拥有原生状态入口。
画布按当前布局区域自适应，不要求业务计算屏幕坐标。

## 工程要求

- minSdk 23
- compileSdk 36
- Java 17
- Jetpack Compose（使用 Compose 接口时）

## 安装

通过 JitPack 使用已发布的 Git tag。在 `settings.gradle.kts` 的依赖仓库中添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

然后添加依赖（Mira 使用相同坐标）：

```kotlin
implementation("com.github.ibabyblue:AParticle:0.2.0")
```

本地验证或发布到 Maven Local：

```bash
./gradlew :aparticle:publishToMavenLocal
```

Maven Local 使用 `io.github.ibabyblue:aparticle:0.2.0`，消费时需要 `mavenLocal()`。
该坐标不代表已发布到 Maven Central。

### 从 0.1.x 升级

`0.2.0` 新增 `ParticleEffect.celebration()`，原有上升爆炸和 Kiss 氛围 API 保持兼容。
Mira 将版本目录中的 `aparticle` 升为 `0.2.0` 后即可使用庆祝接口；现有 Kiss 调用无需修改。
庆祝动画在 Android 14+ 硬件加速画布上使用 GPU Mesh；旧系统保留 Canvas 绘制。
GPU 路径允许少量抗锯齿边缘像素差异，粒子数量、轨迹、颜色和时序不变。
完整变更见 [CHANGELOG.md](CHANGELOG.md)，真机测量和适用范围见
[庆祝性能验证](docs/celebration-performance.md)。

## Jetpack Compose

`AParticle` 是不改变兄弟布局、不安装 pointer input 的透明 Canvas。把它放在业务内容上层即可；
按钮和手势仍由下层业务内容处理。

### 上升并散开

初始 trigger 只登记、不播放；之后每个不同的值都会创建一段独立动画，快速连续触发可并发播放。

```kotlin
var trigger by remember { mutableIntStateOf(0) }

Box {
    BusinessContent(onCelebrate = { trigger += 1 })

    AParticle(
        effect = ParticleEffect.riseAndBurst(
            RiseAndBurstConfiguration(
                launchShape = ParticleShape.Heart,
                burstShape = ParticleShape.Star,
            ),
        ),
        trigger = trigger,
        modifier = Modifier.matchParentSize(),
    )
}
```

### 持续氛围

氛围效果要求 Boolean trigger：初始 `true` 会在有效布局后启动，`false` 停止，
再次从 `false` 变为 `true` 会创建新场景。其他类型的 trigger 会安全无效。

```kotlin
var active by remember { mutableStateOf(true) }

Box {
    BusinessContent()
    AParticle(
        effect = ParticleEffect.atmosphere(
            AtmosphereConfiguration(
                glowColor = ParticleColor(
                    red = 220f / 255f,
                    green = 70f / 255f,
                    blue = 105f / 255f,
                    alpha = 0.2f,
                ),
            ),
        ),
        trigger = active,
        modifier = Modifier.matchParentSize(),
    )
}
```

### 庆祝效果（0.2.0 起）

对应 IParticle 0.4.0 的 SwiftUI `celebration`：4–8 颗流星从顶部进入，抵达终点后
散开，每次主爆炸周围再出现 5–7 次局部爆炸，最终留下粉、蓝、黄三色的四角／五角星。
初始 trigger 只登记，后续不同值会重新开始同一段庆祝，不叠加多段庆祝。

```kotlin
var celebrationTrigger by remember { mutableIntStateOf(0) }
Box {
    BusinessContent(onCelebrate = { celebrationTrigger += 1 })
    AParticle(
        effect = ParticleEffect.celebration(
            CelebrationConfiguration(backdropOpacity = 0.25f),
        ),
        trigger = celebrationTrigger,
        modifier = Modifier.matchParentSize(),
    )
}
```

`backdropOpacity` 默认为 `0f`，有限值限定在 `0..1`，非有限值按 0 处理。
黑色遮罩只覆盖组件布局区域，前 0.25 秒淡入、最后 0.65 秒淡出，不拦截触摸。
配置在触发时捕获，只修改配置不会重播，也不会改变正在播放的遮罩。
流星／爆炸位置随布局自适应；径向扩散以宽度为单位，星星半径以 dp 为单位。
两端使用各自的随机源和原生渲染，不保证随机粒子位置逐一相同。

使用 `0.2.0` 或更新版本即可调用该 API。

## Android View

`AParticleView` 使用命令式播放句柄，适合 XML 或传统 View 页面：

```kotlin
val particleView = AParticleView(context)
container.addView(
    particleView,
    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
)

val playback = particleView.play(ParticleEffect.riseAndBurst())
particleView.stop(playback)
particleView.stopAll()
```

庆祝效果也支持 `particleView.play(ParticleEffect.celebration(CelebrationConfiguration(0.25f)))`。
同一 View 中的新庆祝替换旧庆祝，旧句柄不能停止新的播放；其他效果保留原有并发语义。
`play`、`stop`、`stopAll` 需在主线程调用。

View 本身不可点击、不可聚焦，`onTouchEvent` 始终返回 `false`。`stop` 对未知、已结束或
已停止的句柄安全无效。

## 配置与行为

`RiseAndBurstConfiguration` 默认行为：

- 水平出生范围：画布宽度的 `15%..85%`
- 爆开高度：从顶部算画布高度的 `15%..50%`
- 上升时长：`0.6..0.9` 秒，ease-out
- 上升曲线偏移：`10..18dp`
- 粒子数：`20..33`
- 粒子存活：`0.8..1.2` 秒
- 默认颜色：暖玫红；散开阶段保持上升形状的第一个配置颜色

内置 `Heart`、`Circle`、`Star`，也可使用会被粒子颜色着色的
`ParticleShape.DrawableResource`。资源无法解析时会绘制指定内置 fallback。

`AtmosphereConfiguration` 公开光晕颜色、星光颜色、两类密度、光晕强度和呼吸周期。
元素数量按当前画布的 dp 面积计算，星光最多 320 个、气泡最多 32 个；密度为 0 时省略
对应层。所有颜色都保留源 alpha，并与每帧透明度相乘。

氛围的动画节奏以 Mira 使用的 iOS `IParticleView`（UIKit）为准：光晕从低亮度开始，
在半个呼吸周期到达峰值，再返回低亮度；星光闪烁、气泡呼吸和漂移使用相同的关键帧规则。
星光包含模糊柔光，气泡内部前 85% 半径透明、外侧渐亮。随机布局不保证两端逐粒子相同。

## 布局、生命周期与无障碍

- 画布尺寸为 0 时保留播放请求，直到获得有效布局。
- 尺寸变化超过 1px 时清理旧的短时路径；氛围使用相同随机种子重建。
- View 脱离窗口或窗口不可见时清理；Compose 离开 STARTED 生命周期时清理。
- 系统 animator duration scale 变为 0 时清理已有动画；`riseAndBurst` / `atmosphere` 的新请求安全无效。
- `celebration` 在动画缩放为 0 时使用 12 颗静止星星，和可选遮罩一起在 0.8 秒内淡入淡出。
- 庆祝效果在尺寸归零、明显变更、动画偏好变化时清理；无有效初始布局时只保留最新庆祝请求。
- 生命周期恢复或重新打开动画缩放不会静默重播；氛围需新的 `false → true`。
- 两个适配器都不创建无障碍内容；庆祝的减少动态版本不移动、旋转或闪烁星星。

## Example 与验证

`example` 默认展示庆祝预览：可重播、切换深浅背景、切换 25% 遮罩及 Compose / View 适配器。
“Other effects” 页面保留形状选择、快速并发触发和持续氛围开关。

```bash
./gradlew test lint :aparticle:assembleRelease :example:assembleDebug
```

庆祝性能回归可在连接的设备上执行：

```bash
./scripts/check-celebration-performance.sh gpu
./scripts/check-celebration-visual.sh
```

性能测试使用独立 Release 测试包并核实 ART 编译状态，以固定密集种子播放三次，检查
完整帧耗时和系统截止时间。Android 14+ 硬件加速场景使用 GPU Mesh 批量绘制；旧系统和
软件 Canvas 保留原绘制路径。粒子数量、轨迹、颜色、层次和时序保持不变，GPU 路径允许
少量抗锯齿边缘像素差异。已测设备的三轮完整帧验收通过，画面对比与适用边界见
[庆祝性能验证](docs/celebration-performance.md)。测试期间请保持设备解锁、测试页面前台。

## License

MIT
