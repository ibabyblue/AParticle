# Changelog

## 0.3.0 - 2026-11-27

- Add continuous twinkling and white perspective snowfall to the Compose and View APIs, matching IParticle 0.5.1.
- Batch perspective flakes and morphing crystals in one texture mesh on Android 10 and later.

## 0.2.0 - 2026-09-21

- Batch celebration artwork and explosion trails with GPU Mesh on hardware-accelerated Android 14+, preserving choreography and artwork with minor antialiasing edge differences; retain Canvas on older systems.
- Add non-debuggable, verified-AOT performance benchmarks and on-device GPU/Canvas pixel comparisons.
- Stream celebration frames directly into the renderer, reuse trail geometry/color buffers, and skip fully off-canvas sprites/trails and transparent trails without changing choreography or pixels.
- Add frozen-reference pixel comparisons and an on-device fixed-seed frame-budget regression gate.
- Request celebration bitmap uploads before playback; include complete frame timings and missed deadlines in the performance gate.
- Add `ParticleEffect.celebration` and `CelebrationConfiguration` to Compose and Android View.
- Match IParticle 0.4.0 meteor flights, primary/secondary fireworks, twinkling star remnants, and optional backdrop timing.
- Restart celebration on a new trigger/play request; provide a stationary 0.8-second fade when system animations are disabled.
- Cache bounded star/glow artwork and add choreography, Canvas rendering, and View lifecycle regressions.
- Add a dedicated light/dark celebration preview with backdrop and adapter switches.

## 0.1.1 - 2026-09-15

- Match atmosphere glow, twinkle, bubble breathing, and drift timing to the UIKit IParticleView used by Mira.
- Prevent sparkle paint opacity from dimming edge glows and subsequent bubble frames.
- Render cached soft sparkle shadows instead of flat translucent discs, with density-aware sizing.
- Add deterministic timing and native Canvas pixel regressions for the Kiss atmosphere.

## 0.1.0 - 2026-08-30

- Add native Jetpack Compose and Android View particle renderers.
- Add rise-and-burst and continuous atmosphere effects.
- Add lifecycle, animation-scale, layout, and touch-passthrough handling.
