# Speedforce progress update

The Convergence Engine interaction now has two server-validated paths:

- **Awakening:** bring three Temporal Shards, one Resonance Core, and Unstable Speedforce Essence. Interact normally for the Positive/gold path; sneak-interact for the Negative/red path. It validates every ingredient before consuming any, stores awakening/level/path on player persistent data, and activates level one.
- **Mastery upgrades:** after awakening, bring two Chronal Fragments and one Resonance Core to the engine to advance the persistent level up to five. Upgrades are stored per player and restored after login/respawn, using the existing server-owned movement levels.

The path selection uses the existing theme flag and server-side theme toggle. The persistent initial path is restored after login; the normal theme keybind remains available in play.

This update is not a claim that the original full mod is complete. A registered locatable structure, custom dimension, challenge trial, actual runtime shader/post chain, full regression build, and client/server multiplayer verification remain outstanding. Run `./gradlew clean build` in Java 21 with dependency network access to validate the current code/resources.
