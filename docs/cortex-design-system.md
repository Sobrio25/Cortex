# Cortex visual system

The app-wide look is derived from the translucent Cortex assistant interface. Material 3 remains the Compose implementation layer for accessibility and platform behavior, while its default visual identity is replaced by the Cortex palette, atmosphere, shapes, and surfaces.

## Foundation

- `CortexDesign.kt` owns the violet, blue, mint, and pink accents, dark/light glass palettes, atmospheric backdrop, Cortex mark, and `Modifier.cortexGlass`.
- `Theme.kt` maps those tokens into the app color scheme. Dynamic system colors are disabled by default so every screen keeps the same identity.
- The root activity is transparent over `CortexBackdrop`; screens and scaffolds therefore reveal the shared gradient instead of painting an opaque Material background.
- The assistant activity opts out of the app backdrop because its window intentionally reveals and blurs the app underneath it.

## Component guidance

- Use `MaterialTheme.colorScheme.surface` and `surfaceVariant` for standard translucent containers.
- Use `Modifier.cortexGlass(shape)` for prominent floating panels, inputs, drawers, or custom boxes that need the highlight and luminous outline.
- Use `CortexColors` instead of adding local purple/cyan/mint constants.
- Use the expressive theme shapes or `ShapeTokens` to preserve the rounded silhouette.

## Agent activity glow

The chat composer always lights up while work is running. Cortex uses the primary violet/blue halo; each active delegated agent contributes its stable color to the sweep gradient, so multi-agent work is visible without changing chat behavior.
