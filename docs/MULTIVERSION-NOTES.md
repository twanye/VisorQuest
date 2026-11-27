# Multiversion notes (branch `hark/multiversion`)

> **Status: UNTESTED ON DEVICE.** Nothing on this branch has been run on a Quest. The only
> verification is that the Pojlib AAR and the Quest APK compile in CI, and that AtumVR compiles in a
> sandbox. Written by Hark for Tucker (the User), 2026-11-27. Do not merge without Tucker.

Base: `deepseek/visor-0.6.1-quest` @ `302ca1f` (launches 1.20.1 on a Quest 3S).
Plan it follows: `docs/VERSION_MATRIX.md`, `docs/ARCHITECTURE.md`, `docs/1.21.11-ASSESSMENT.md`.

## Commits

| Commit | What |
|---|---|
| `0220295` | PR #5 `ae2d355` (ChatGPT) brought over: `versions.json`, `VisorVersionTable`, pinned-loader `FabricMeta`, `fixDataPermissions`, `UPSTREAM_QUESTCRAFT_MANIFEST_BASE_URL` (TODO(User)). One conflict in `Installer.java` (the asset-download constants from `deepseek/…`) resolved by keeping both sides. |
| `949e873` | PR #5 `eeeaceb` (ChatGPT) cherry-picked cleanly: Visor/VE keep `type: mod`, sturdier `fixDataPermissions`. |
| `e80058b` | PR #5 `58c75cd` (ChatGPT) cherry-picked cleanly: `unzipSavesFromFolder`, `mirrorNativesInFolder`, `extraNatives` (the Unity JNI gap). |
| `58659b8` | Hark: P0 table-driven gate and per-version perf mods; 1.21.1 entry. |
| `0047ba7` | Hark: P1 AtumVR port of coah80/AtumVR `d04c1669` + `deps/lwjgl-openxr-quest-3.3.3.jar`. |
| `9dad863` | Hark: P1 Visor port of coah80/Visor `a0275017` behind `-Pvisor_quest=true` (**not compiled**). |
| (this) | Hark: this file. |

## P0: what changed in Pojlib

- **`API.getQCSupportedVersions`** reads `Pojlib/src/main/assets/versions.json` and lists only
  entries with `"questReady": true`. Today that is `1.20.1` only, so the launcher dropdown is unchanged.
- **`Installer.installVisor`** looks the instance's version up in the table. No entry → same
  "does not support" error as before. Entry with `questReady: false` → a clear "no Quest Visor
  build for X yet" error instead of installing a desktop jar.
- **Perf mods are per version.** `bundledMods` (APK assets) and `downloadedMods` (URL + SHA-1) are
  per entry. A mod listed without a usable SHA-1/asset/URL is skipped with a log line instead of
  failing the install. A mod with no entry for a version is not installed **and is no longer
  deleted**: the cleanup now only removes Vivecraft, Visor, VisorEssentials, that version's
  bundled mods and its `removeMods`. For 1.20.1 this is exactly the old hard-coded list
  (MoreCulling, Noisium, BadOptimizations, Chunky, C2ME + `removeMods: ["Voxy"]`), so 1.20.1
  behaviour should be identical. This matters for 1.21.1, where upstream `mods.json`
  `defaultMods` already brings Noisium, MoreCulling, BadOptimizations and ImmediatelyFast.
- **Visor/VisorEssentials** can now be either a bundled asset (`assetPath`) or a download (`url`),
  both SHA-1 checked.
- **Fabric loader** is pinned per entry (`FabricMeta.getVersionForMinecraftVersion`, newest stable
  as fallback); the old buggy comparison is gone (from PR #5).
- New informational field `fabricApi` per entry (the build the version is meant for). It is **not**
  installed by `installVisor`; Fabric API still comes from upstream `mods.json` `coreMods`.

### versions.json entries

**1.20.1** (unchanged behaviour): `questReady: true`, Java 17, loader `0.20.1`, bundled Visor
`0.4.0+questcraft` / VisorEssentials `0.4.0` (SHA-1s re-checked against the asset files), same
bundled perf mods, ImmediatelyFast download, `fabricApi` `0.92.12+1.20.1` (what upstream
`mods.json` installs, Modrinth `rvI2dfzR`, SHA-1 `3e9cdd3e2f827ca9a259df9eb8e31949437b6bd4`).

**1.21.1** (`questReady: false`, hidden from the launcher):

| Field | Value | Source |
|---|---|---|
| javaMajor | 21 | Visor jar `fabric.mod.json` `"java": ">=21"`; Mojang 1.21.1 JSON (VERSION_MATRIX) |
| fabricLoaderVersion | `0.20.1` | meta.fabricmc.net `/v2/versions/loader/1.21.1`: newest stable on 2026-11-27; Visor 0.6.1 needs `>=0.14.14` (read from the jar) |
| fabricApi | `0.116.18+1.21.1`, SHA-1 `584d303f646287ee6be059ecb2fc451255be9ee9`, 2453884 B | Modrinth version `5SwzqWUu` (newest 1.21.1 release); also what upstream QuestCraft `mods.json` uses |
| visor | `0.6.1+mc1.21.1-fabric`, assetPath/url/sha1 **null, TODO** | No Quest build exists. Upstream Modrinth `FJN2Te69` (`Visor-0.6.1+mc1.21.1-fabric.jar`, SHA-1 `5215f85800f3ce5b5dc181846d5111fb7748ee4c`) has no Android/Quest classes (checked) and is noted only for reference |
| visorEssentials | `0.6.1+mc1.21.1-fabric`, download, SHA-1 `e5641e7a1485032c74b4f837705e6abd438256d6`, 346346 B | Modrinth `xcYlq5Zb`; jar downloaded and hash/`fabric.mod.json` checked (`visor >=0.6.1`, `minecraft >=1.21 <1.21.2`). Untested on Quest |
| bundledMods / downloadedMods | empty (`perfModsTodo`) | See TODO in the file |
| lwjglBundleId | `3.3.3-snapshot` | Pojlib's bundle; Mojang 1.21.1 uses 3.3.3 (VERSION_MATRIX) |

To turn 1.21.1 on later: build the Quest Visor jar for 1.21.1, fill `visor` (asset or URL, SHA-1,
size), set `questReady: true`.

## CI

`Build Quest APK` (`quest-apk.yml` on `hark/ci-quest-apk`, `pojlib_ref=hark/multiversion`), run
46060534824: https://github.com/twanye/VisorQuest/actions/runs/46060534824 — **success** (both
`pojlib` and `apk` jobs), first attempt. Pojlib was built at `58659b8`. This proves compilation only.

## P1: how far it got

### AtumVR (`0047ba7`) — compiles in sandbox
- Ported coah80/AtumVR `d04c1669` ("android openxr stuff") onto upstream AtumVR 1.14:
  `session/platform/XRPlatform` + `AndroidXRBridge` (from coah), and hooks in `XRInstance`
  (loader init, `XrInstanceCreateInfoAndroidKHR` in `next`, `XR_KHR_opengl_es_enable` as the
  required graphics extension, OpenGL path forced on Android), `XRSession` (GLES Android graphics
  binding), `XRSwapChain` (GLES swapchain image type), `XREvent` (numeric ML event ids).
  Upstream's new Vulkan bridge / desktop `XROpenGLBinding` are kept for desktop.
- `atumvr-api` now compiles against `deps/lwjgl-openxr-quest-3.3.3.jar`. **That jar was the known
  blocker; it is now in the repo**, copied byte-for-byte from coah80/Visor commit `32c80265`
  (`libs/lwjgl-openxr-quest-3.3.3.jar`, SHA-1 `e042aa98f276b74e49ca96040589d503dd112ad5`,
  manifest "LWJGL OpenXR 3.3.3-snapshot"). Provenance is coah80's build of QuestCraft's LWJGL fork;
  we have not rebuilt it from source. LWJGL is BSD-3.
- Side effect: that 3.3.3 jar's `XrGraphicsBindingEGLMNDX` has no `getProcAddress`, so the
  **desktop Linux EGL** path in `XROpenGLBinding` now throws (TODO in the code). Quest never uses it.
- `./gradlew :atumvr-api:jar :atumvr-core:jar` (JDK 17) → BUILD SUCCESSFUL.
- **Not ported:** coah80/AtumVR `1d7dd401` ("optimize Quest OpenXR runtime": XRSession,
  XRSwapChain, XRState, XRRenderer, XREyeCamera). It conflicts with upstream 1.14's rewrite of the
  same files and needs a by-hand port.
- **Risk:** AtumVR 1.14 is compiled against LWJGL 3.3.6 core but runs on Pojlib's LWJGL
  3.3.3-snapshot on the Quest. Any 3.3.4+ API it touches at runtime would fail. Unchecked.
- Version note: the imported AtumVR source says `1.14.0beta-snapshot#14`, while upstream Visor 0.6.1
  pins `#27` jars in `Visor/libs`. The source in `AtumVR/` may be older than what Visor 0.6.1 expects.

### Visor (`9dad863`) — NOT compiled
- Ported coah80/Visor `a0275017` ("quest android support"): `QuestVLoader` (JNI into Pojlib's
  `libvloader`), `QuestAndroidXRBridge`, and `XrProvider` installs the bridge (only switches
  platform on Android).
- Build switch `-Pvisor_quest=true` (`buildSrc/.../visor.minecraft.gradle`): uses
  `Visor/libs/atumvr-{api,core}-quest.jar` and bundles `deps/lwjgl-openxr-quest-3.3.3.jar` instead
  of desktop `lwjgl-openxr` 3.3.6 + natives (`visor-fabric/build.gradle`). Without the flag the
  build is unchanged.
- The `atumvr-*-quest.jar` files are **not committed**. Make them with:
  `cd AtumVR && ./gradlew :atumvr-api:jar :atumvr-core:jar` and copy
  `atumvr-api/build/libs/atumvr-api-*.jar` → `Visor/libs/atumvr-api-quest.jar` (same for core).
- **Not ported:** coah80/Visor `25740234` ("make essentials work", `PlayerPoseType`, tiny) and
  `32c80265` ("optimize Quest rendering and world loading", ~40 files across overlays, renderer,
  mixins). Both need a by-hand port onto 0.6.1's Stonecutter layout.

### Visor build result: blocked by the sandbox, not by code
`./gradlew -Pvisor_quest=true :visor-fabric:1.20.1:build` (Gradle 9.7.1, JDK 21 + 17) never reached
compilation. Stonecutter configures **every** target (14 MC versions × fabric/forge/neoforge), and
Loom sets up a Minecraft jar for each one. On Hark's sandbox (2 CPUs, ~8 GB RAM shared with a
RAM-backed disk) the Gradle daemon was killed three times while configuring (last seen at
`:visor-forge:1.20.2`), with no compile errors logged. Per the "stop after two repeats" rule, P1 stopped there.

So the Visor changes are **unverified**. Known consequence: `XrProvider` now references
`XRPlatform`/`AndroidXRBridge`, which only exist in the ported `AtumVR/`, so a **normal (non-quest)
Visor build will no longer compile** against the `#27` jars in `Visor/libs`. Build Visor only with
`-Pvisor_quest=true` and the Quest AtumVR jars, or revert `XrProvider` for desktop builds.

What would unblock it: a bigger machine (Tucker's PC is RAM-limited too) or a GitHub Actions job
that builds `AtumVR/`, copies the two jars into `Visor/libs/`, then runs the command above
(possibly after trimming `[targets]` in `stonecutter.properties.toml` to `"1.20.1" = ["fabric"]`
inside the CI job only, so Loom configures one target).

## What is untested

Everything on device. In particular: the table-driven `installVisor` on 1.20.1 (should be identical),
the saves/natives JNI ports, every AtumVR/Visor Quest change, and VisorEssentials 0.6.1 on Quest.

## Next steps

1. Device-test this APK on 1.20.1 (should match `deepseek/…`): launch, Visor loads, saves/natives buttons.
2. Finish P1 for 1.20.1: port AtumVR `1d7dd401`, Visor `25740234` and `32c80265`; build the Quest
   Visor 1.20.1 jar; compare against coah's 0.4.0 artifact on device. Consider a CI job that builds
   AtumVR → Visor (the sandbox is too small for Visor's 14-target Stonecutter configure).
3. 1.21.1: build the Quest Visor jar for the `1.21.1` Stonecutter target, fill `visor` in
   `versions.json`, decide its perf mods, set `questReady: true`.
4. Then, per `docs/VERSION_MATRIX.md` §5: **1.21.5 → 1.21.7** (Visor `1.21.8` build) **→ 1.21.11**
   (loader `>=0.17.3`, decide P2/MobileGlues first), **then 26.x** (Java 25 JRE, LWJGL 3.4.x and the
   split OpenXR jar, 26.2 Vulkan libs, 26.3 SDL3 last).
5. TODO(User) still open: where VisorQuest hosts its own `mods.json`/`supportedVersions.json`
   (`Constants.UPSTREAM_QUESTCRAFT_MANIFEST_BASE_URL`).
