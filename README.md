# Speedforce - implementation notes

## Current project

This is a NeoForge 21.1 / Minecraft 1.21.1 Java 21 mod. The existing project already contained the ModDevGradle wrapper, client visual effects, movement, stamina, networking, and five-tier velocity tracking. The current commit adds the first integrated research-to-awakening loop rather than replacing that existing code.

## Convergence Engine loop

Craft the Convergence Engine, Temporal Shards, Resonance Core, and Unstable Speedforce Essence. Place the engine and interact with it while carrying three shards, one core, and one essence. It consumes these items, unlocks the player's Speedforce connection, and activates level one. The unlock is stored in player persistent data and restored on login and respawn. The existing Speedforce controls and server-authoritative gameplay are used after awakening.

The three research ingredients currently have crafting recipes so the loop is playable in a standard survival world. This is a functional early progression loop, not yet the natural Temporal Anomaly discovery, trial, or custom dimension requested in the design brief.

## Build and verification

A compile or client/server test must be run in an environment able to download Gradle and NeoForge dependencies. No claim of compilation or runtime verification is made by this commit. In particular, validate the NeoForge event/API signatures with `./gradlew clean build`, then run both `runClient` and `runServer` before release.
