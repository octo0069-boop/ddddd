# Speedforce 1.1.0 - Phase 1: networking + server-side movement

Status: **NOT compiled.** Source parses cleanly (22 files, 0 syntax errors) but `./gradlew clean build` could not run
(no access to services.gradle.org / maven.neoforged.net in the sandbox). Nothing was run in a client or server.

## Files
Replaced: `Config`, `net/Network`, `net/SyncPayload`, `net/ActionPayload`, `client/ClientState`, `client/ClientPayloads`,
`client/ClientEvents`, `client/SpeedforceClient`, `client/HudLayer` (stamina bar only), `server/ServerLogic`,
`server/ServerEvents`, `server/WaterRun`, `lang/en_us.json`, `gradle.properties` (1.0.0 -> 1.1.0).
New: `net/VisualPayload`, `client/ClientRemoteVisuals`.
Untouched: `BoltPayload`, `ClientBolts`, `BoltRenderer`, `BoltSparkParticle`, `SpeedLinesLayer`, `ModParticles`, `Speedforce`, `ClientConfig`.

## Three audit findings in v1 that drove the design
1. `ServerLogic` read `p.getDeltaMovement()` on a ServerPlayer. Player movement is client-reported, so that value is
   not a trustworthy speed. v2 samples **position deltas** per tick.
2. `WaterRun` called `setPos` on both sides (server and client positions diverge -> corrections) and the client call ran in
   `ClientTickEvent.Post`, which is AFTER `LocalPlayer.sendPosition()`, so the `onGround` it set was never the one sent.
   Also it read `Config.WATER_RUN` on the client, which is the client's own file, not the server's.
3. Flash Dash was `connection.teleport`, a hard teleport. Now a replicated velocity impulse (legacy blink kept as an option).

## Packet flow
| Packet | Dir | Sent to | When | Size |
|---|---|---|---|---|
| ActionPayload(action) | C->S | server | key press | 1 byte |
| SyncPayload | S->C | owner only | level/tier/flags change (immediate); speed >=0.15 b/t or stamina >=1% drift (min 5 ticks apart); 100-tick heartbeat while active; after every ability | ~17 B |
| VisualPayload | S->C | players **tracking** the owner (not owner) | level/tier/flags change; speed >=0.2 b/t drift (min 4 ticks); heartbeat; plus once on `StartTracking` | ~9 B |
| BoltPayload | S->C | near (128) | unchanged | unchanged |

Protocol version is now `"2"` (SyncPayload shape changed, VisualPayload added) - v1 and v2 peers are rejected at connect.

SyncPayload: seq, serverTick, level (selected), tier (from real speed), flags, speedMilli, staminaPerM, strikeCd, dashCd.
Flags: EXHAUSTED, WATER_ENABLED, WATER_ACTIVE, NEGATIVE_THEME, DASHING. `seq` is shared with VisualPayload and compared
wrap-safe `(a-b) > 0`.

## Server authority
- Request validation: id range, alive/not spectator, token bucket (burst 8, 1 per 4 ticks sustained), `requireActive`,
  cooldown (game-time deadline, server-owned), stamina cost (server-owned), level clamped to `Config.MAX_LEVEL`,
  dash rejected while riding / elytra / sleeping. Client sends no state, only which key was pressed.
- Tier: from smoothed (EMA 0.35) real speed, entered at `tierNSpeed`, left at `(1-hysteresis)*threshold`,
  one tier per step, with dwell (2 ticks up, 8 down). Thresholds are forced strictly increasing.
  Teleports (>10 blocks in one tick), dimension changes, dash blinks and riding are excluded from sampling; the smoothed
  speed is held, not zeroed, so no abrupt reset.
- Stamina (independent of hunger): drains per tier (0.02/0.06/0.12/0.25 per tick x scale), regens walking/idle.
  At 0 -> exhausted: effective level capped to `exhaustedMaxLevel` (selected level kept) until `exhaustedRecover`.
  Strike and Dash cost stamina. Hunger exhaustion and contact damage are preserved and now use the effective level.
- Attribute modifiers are cached and only touched when effective level / speedScale change.
- State is removed on logout, cleared on `ServerStoppedEvent`, reset on login/respawn, resynced on dimension change.

## Client prediction / reconciliation
- `ClientState` has authoritative fields (from the latest accepted snapshot) and presentation fields (`speed`, `tierBlend`,
  `staminaDisp`, with prev-values for partial-tick lerp: `speed(pt)`, `tierBlend(pt)`, `stamina(pt)`).
- Speed = local real velocity (prediction) + an offset easing toward the server value (rate 0.2/tick while snapshots are
  < 40 ticks old, then decays). Big disagreements (>1 b/t, level change) snap. `tierBlend` eases to the server tier
  (fast up, slow down) so effects never pop.
- Reset on login, logout, respawn and dimension change (`LoggingIn`, `LoggingOut`, `Clone`).
- Remote players: `ClientRemoteVisuals` (int -> state, hard cap 256, per-entity stale rejection, removed on
  `EntityLeaveLevelEvent` and when Speedforce turns off). Future renderers call `ClientRemoteVisuals.get(entity.getId())`.
- Nothing ever teleports the client player. Dash IMPULSE sets server velocity for 5 ticks (`hurtMarked` -> vanilla motion
  packet); BLINK (`dashMode = BLINK`) is the old teleport.
- Water running: server only evaluates (read-only; sets WATER_ACTIVE, cancels fall damage, resets the vanilla flying-kick
  timer). Client predicts only when the server sent WATER_ENABLED, by solving the vertical velocity that lands on the
  surface next tick (`vy = dy/0.98 + 0.08`) - no `setPos`, horizontal momentum untouched.

## Config additions (config/speedforce-common.toml)
tier1Speed..tier4Speed (0.35/0.80/1.40/2.40 b/t), tierHysteresis 0.10, staminaEnabled, staminaMax 100,
staminaDrainScale, staminaRegenWalk 0.08, staminaRegenIdle 0.20, exhaustedMaxLevel 1, exhaustedRecover 0.25,
strikeStamina 10, dashStamina 15, dashMode IMPULSE|BLINK, trailStepCap 16 (v1 was effectively 24; lower = fewer packets).
New keybind: V = toggle gold/red theme (ActionPayload.THEME). Existing keys unchanged.

## Compatibility
- Both sides must run 1.1.0 (protocol "2"). Existing config files keep working; new keys get defaults.
- Visible behaviour changes: dash is a ~5-tick burst by default (not instant); stamina now limits sustained tier 3-4 running
  (set `staminaEnabled=false` to restore v1 feel); HUD speedometer shows smoothed horizontal speed + tier.
- BoltPayload, aura bolts and particles are still server-generated in this phase; moving them client-side (driven by
  tier/flags) is the graphics phase.

## NOT verified - check these first when you compile
1. `PacketDistributor.sendToPlayersTrackingEntity(Entity, CustomPacketPayload, CustomPacketPayload...)` - from memory of the 21.1 API.
2. `p.connection.resetFlyingTicks()` (ServerGamePacketListenerImpl) - if absent in your mappings, delete that line in
   `ServerLogic.tick`; water running may then trigger vanilla's "flying not enabled" kick after ~4 s on servers with allow-flight=false.
3. `PlayerEvent.StartTracking#getTarget`, `PlayerEvent.PlayerChangedDimensionEvent`, `ServerStoppedEvent`,
   `ClientPlayerNetworkEvent.LoggingIn/Clone`, `EntityLeaveLevelEvent` - all from memory.
4. `LocalPlayer.tick()` calling `super.tick()` before `sendPosition()` (basis for the water-run hook choice).
5. Dash impulse speeds (up to 5 b/t at level 5) against vanilla's 10 b/t "moved too quickly" check - should pass, test with a real server.
6. Not done: dedicated server + two-client test, `./gradlew clean build`.

## GitHub build
`.github/workflows/build.yml`: Java 21 (Temurin) + Gradle cache, `./gradlew clean build` on push/PR, uploads
`build/libs/*.jar` as an artifact. Pushing a tag `vX.Y.Z` also creates a GitHub Release with the jar.
This is also the first real compile of Phase 1 - read the first run's log for any API mismatches listed above.
