# AParticle

面向 Jetpack Compose 与 Android View 的原生粒子动画 AAR，参考 iOS
`IParticle` 的行为契约，并使用 Android 自己的 Canvas、生命周期和动画缩放机制实现。

当前包含两类效果：

- `riseAndBurst`：一个形状从画布底部沿轻微曲线上升，在上半部散开并淡出。
- `atmosphere`：透明的持续氛围层，包含漂移星光、边缘气泡和呼吸式玫红光晕。

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
implementation("com.github.ibabyblue:AParticle:0.1.1")
```

本地验证或发布到 Maven Local：

```bash
./gradlew :aparticle:publishToMavenLocal
```

Maven Local 使用 `io.github.ibabyblue:aparticle:0.1.1`，消费时需要 `mavenLocal()`。
该坐标不代表已发布到 Maven Central。

### 从 0.1.0 升级

`0.1.1` 修复 Kiss 使用的 `atmosphere` 渲染并对齐 iOS UIKit 动画节奏，公开 API 不变。
Mira 只需将版本目录中的 `aparticle` 升为 `0.1.1`，保留现有颜色、密度、5 秒呼吸周期
和命中后播放 3 秒的业务逻辑。完整变更见 [CHANGELOG.md](CHANGELOG.md)。

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
- 系统 animator duration scale 为 0 时新播放安全无效，并立即清理已有动画。
- 生命周期恢复或重新打开动画缩放不会静默重播；氛围需新的 `false → true`。
- 两个适配器都不创建无障碍内容，也不显示静态替代闪烁。

## Example 与验证

`example` 模块演示形状选择、快速并发触发和持续氛围开关。

```bash
./gradlew test lint :aparticle:assembleRelease :example:assembleDebug
```

## License

MIT
