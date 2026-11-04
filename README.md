# Speedforce

Minecraft 1.21.1 / NeoForge 21.1 / Java 21.

## Current playable loop

1. Explore newly generated Overworld chunks and look for the rare, glowing Temporal Anomaly Anchor clusters. They are added to Overworld biome generation by a NeoForge biome modifier. The feature is deliberately a feature-based landmark, not a registered structure; `/locate structure` does not apply.
2. Mine anchors. Without Silk Touch, they drop Temporal Shards and have a chance to yield Chronal Fragments. Silk Touch preserves the anchor block.
3. Craft a Convergence Engine, collect three Temporal Shards, a Resonance Core and Unstable Speedforce Essence, then interact with the engine. It consumes those materials and awakens the player at level 1. The awakening marker is stored in player persistent data and copied on death/respawn.
4. Use the existing keybinds to change speed level, dash, strike, and switch energy theme. Server logic validates abilities and manages kinetic stamina.

The shard and essence recipes let players fill gaps in exploration finds; the anchor's loot is the intended early source of temporal research material.

## Graphics resources

The project includes original GLSL shader source assets and custom client effects from earlier commits. The shader files added under `assets/speedforce/shaders` are source materials, not yet wired to Minecraft's post-chain registration. They should not be described as active framebuffer passes.

## Verification status and known gaps

No `./gradlew clean build`, client launch, dedicated-server test, or multiplayer test has been run in this working context. The full requested registered structure with `/locate`, custom Speedforce dimension, ring trial, and shader/post-processing runtime pipeline are still not implemented. Run `./gradlew clean build` with Java 21 and dependency network access, then fix any resource or API validation issues before distributing.
