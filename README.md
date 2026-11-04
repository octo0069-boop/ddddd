# Speedforce progress and implementation status

This repository contains the existing NeoForge 21.1 / Minecraft 1.21.1 Java 21 mod, with server-authoritative velocity tiers, kinetic stamina, dash, strike, water-run support, client visual effects, and multiplayer payloads. The current content adds a Convergence Engine interaction, research materials, a persistent awakening marker, naturally placed Temporal Anomaly anchor clusters in Overworld biomes, and original GLSL effect sources.

## Natural anomaly discovery

The configured and placed feature in `data/speedforce/worldgen` is added to the Overworld through a NeoForge biome modifier. It scatters rare glowing temporal anchors at the surface in newly generated chunks; the default rarity filter uses one attempt per 110 eligible chunks. Explore newly generated Overworld terrain to find them. This is a rare feature-based anomaly landmark, not a registered jigsaw structure, so `/locate structure` is not provided.

## Engine materials and interaction

Craft the listed materials and engine using the recipes in `data/speedforce/recipe`. The engine consumes three Temporal Shards, one Resonance Core, and Unstable Essence, then starts the current level-one Speedforce ability. Player persistence is stored in vanilla persistent player data.

## Graphics resources

The `assets/speedforce/shaders` sources define procedural lightning/ribbon materials and speed distortion, chromatic separation, streaking and bloom stages. They are original GLSL source assets. Existing NeoForge/Minecraft rendering code remains the runtime renderer; the standalone post shader files are source assets and are not registered as a Minecraft post-effect chain by this commit. No claim is made that shader files alone are active runtime passes.

## Build and verification

Run `./gradlew clean build` with Java 21 and network access to Gradle and NeoForge Maven. Dedicated-server/client launch, dimension registration, full trial gameplay, custom framebuffer/post-processing integration and real-world generation have not been runtime-tested here. The feature-based anomaly is not equivalent to the full custom dimension and trial in the original design brief.
