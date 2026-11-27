# Multiversion notes (branch `hark/multiversion`, continued on `hark/visor-builds`)

> **Status: UNTESTED ON DEVICE.** Nothing on this branch has been run on a Quest. The only
> verification is that the Pojlib AAR and the Quest APK compile in CI, that AtumVR compiles, and
> (on `hark/visor-builds`) that the Quest Visor 1.20.1 Fabric jar compiles in CI and is bundled into
> the APK for 1.20.1 in place of coah's 0.4.0 jar. Written by Hark for
> Tucker (the User), 2026-11-27. Do not merge without Tucker.

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
| `9dad863` | Hark: P1 Visor port of coah80/Visor `a0275017` behind `-Pvisor_quest=true` (compiled later in CI, see below). |
| `9232da6` | Hark: this file. |
| `0ad0e0d` | `hark/visor-builds` only: `.github/workflows/visor-quest-build.yml` (AtumVR → Visor CI build). |
| `e4fd696` | `hark/visor-builds` only: CI fix (keep every version in the CI `[targets]` table, `--configure-on-demand`). First green run. |
| `54aaf80` | `hark/visor-builds` only: CI build section + jar check. |
| `34af34a` | `hark/visor-builds` only: AtumVR port of coah80/AtumVR `1d7dd401` by hand (see "P1 for 1.20.1, round 2"). |
| `075a66a` | `hark/visor-builds` only: Visor port of coah80/Visor `32c80265` + `25740234`; Quest builds no longer nest `lwjgl-vulkan`. |
| `0a311c3` | `hark/visor-builds` only: Pojlib bundles the CI Visor `0.6.1+questcraft` + VisorEssentials `0.6.1` for 1.20.1. |
| (this) | `hark/visor-builds` only: this section update. |

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
- ~~Not ported: coah80/AtumVR `1d7dd401`~~ ported by hand on `hark/visor-builds` (`34af34a`), see
  "P1 for 1.20.1, round 2".
- **Risk:** AtumVR 1.14 is compiled against LWJGL 3.3.6 core but runs on Pojlib's LWJGL
  3.3.3-snapshot on the Quest. Any 3.3.4+ API it touches at runtime would fail. Static scan done in
  round 2: nothing missing outside `XRVulkanBridge`.
- Version note: the imported AtumVR source says `1.14.0beta-snapshot#14`, while upstream Visor 0.6.1
  pins `#27` jars in `Visor/libs`. The source in `AtumVR/` may be older than what Visor 0.6.1 expects.

### Visor (`9dad863`) — compiles in CI (see "Visor Quest CI build" below)
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
- ~~Not ported: coah80/Visor `25740234` and `32c80265`~~ ported on `hark/visor-builds` (`075a66a`),
  see "P1 for 1.20.1, round 2".

### Visor build result in the sandbox (superseded by the CI build below)
`./gradlew -Pvisor_quest=true :visor-fabric:1.20.1:build` (Gradle 9.7.1, JDK 21 + 17) never reached
compilation. Stonecutter configures **every** target (14 MC versions × fabric/forge/neoforge), and
Loom sets up a Minecraft jar for each one. On Hark's sandbox (2 CPUs, ~8 GB RAM shared with a
RAM-backed disk) the Gradle daemon was killed three times while configuring (last seen at
`:visor-forge:1.20.2`), with no compile errors logged. Per the "stop after two repeats" rule, P1 stopped there.

In the sandbox the Visor changes stayed unverified; the CI build below compiles them. Known consequence: `XrProvider` now references
`XRPlatform`/`AndroidXRBridge`, which only exist in the ported `AtumVR/`, so a **normal (non-quest)
Visor build will no longer compile** against the `#27` jars in `Visor/libs`. Build Visor only with
`-Pvisor_quest=true` and the Quest AtumVR jars, or revert `XrProvider` for desktop builds.

What would unblock it: a bigger machine (Tucker's PC is RAM-limited too) or a GitHub Actions job
that builds `AtumVR/`, copies the two jars into `Visor/libs/`, then runs the command above
(possibly after trimming `[targets]` in `stonecutter.properties.toml` to `"1.20.1" = ["fabric"]`
inside the CI job only, so Loom configures one target).

## Visor Quest CI build (branch `hark/visor-builds`)

Workflow `Build Visor Quest (1.20.1 Fabric)`, `.github/workflows/visor-quest-build.yml`. Runs on push
to `hark/visor-builds` and on `workflow_dispatch`. Steps:

1. JDK 17, 25 and 21 (default) via `setup-java`; Gradle cache via `gradle/actions/setup-gradle`
   (`cache-read-only: false`, so this non-default branch writes the cache too).
2. `AtumVR/`: `./gradlew :atumvr-api:jar :atumvr-core:jar` on JDK 17 (its Gradle 8.4 can't run on 21).
3. Copies `atumvr-{api,core}-1.14.0beta-snapshot#14.jar` to `Visor/libs/atumvr-{api,core}-quest.jar`.
4. Trims Stonecutter **in the CI checkout only** (nothing committed): every version stays in
   `[targets]` (visor-core's mcversion range-file headers must name real visor-core targets, so
   dropping versions fails with `PlayerMixin.java: 1.20.6 is not a visor-core target`), but only
   `"1.20.1" = ["fabric"]` keeps a loader; the `visor-forge`/`visor-neoforge` branch lines are removed
   from `settings.gradle`.
5. `./gradlew -Pvisor_quest=true :visor-fabric:1.20.1:buildAndCollect --configure-on-demand` with
   `-Xmx10G` and toolchains from `JAVA_HOME_{17,21,25}_X64`. With configure-on-demand only
   `visor-api`/`visor-core`/`visor-fabric` 1.20.1 set up a Minecraft jar. Build took 1m 45s.
6. Uploads artifact `visor-quest-1.20.1-<sha>`: `Visor/build/libs/*.jar` plus the two AtumVR jars.

First green run: https://github.com/twanye/VisorQuest/actions/runs/46066825520 (commit `e4fd696`),
artifact `visor-quest-1.20.1-e4fd69631d94748915a3b47b42eea1f0060d7c24`, 13,347,547 B zipped.
No compile errors: the `a0275017` port compiled as written. Two compiler warnings
(`RenderSystemAccessor`: no obfuscation mapping for `shaderTextures`/`shaderGameTime`) are in
upstream Visor code, not the port. Loom also notes `() is not valid semver for dependency
:lwjgl-openxr-quest-3.3.3:`; harmless, because that jar already carries its own `fabric.mod.json`
(id `questcraftplusplus_lwjgl3_lwjgl-openxr-3`, version `3.3.3`), same as in coah's jar.

### What the jar contains (checked from the artifact)

`Visor-0.6.1+mc1.20.1-fabric.jar`, 11,825,930 B, SHA-1 `6099e8040224a14abad07634f06799bcc1f52548`
(plus `Visor-0.6.1+mc1.20.1-fabric-api.jar`, 1,763,955 B). Class files are Java 17 (major 61).

- Quest classes present: `org/vmstudio/visor/core/client/provider/openxr/QuestVLoader`,
  `QuestAndroidXRBridge`, and the shaded AtumVR `me/phoenixra/atumvr/core/session/platform/XRPlatform`
  (+ `$Android`, `$Desktop`, `$Holder`) and `AndroidXRBridge`. Same class names as coah's
  `Pojlib/src/main/assets/mods/Visor.jar` (`0.4.0+questcraft`).
- `javap` against coah's jar: `QuestAndroidXRBridge` and `AndroidXRBridge` match exactly.
  `QuestVLoader` lacked coah's `getDeviceModel()` (native), `deviceModel()`, `isQuest2()`,
  `isQuest3Family()` at that point; added with `32c80265` in round 2. `XRPlatform$Android` additionally
  overrides `usesAndroidBridge()` (our 1.14 port's design).
- Nested jars: `lwjgl-openxr-quest-3.3.3.jar` (byte-identical to `deps/`, SHA-1 `e042aa98…`) and
  fabric-api-base / resource-loader-v0 / command-api-v2, same as coah's, **plus
  `lwjgl-vulkan-3.3.6.jar` (7.3 MB)**, which coah's 0.4.0 jar does not have (upstream 0.6.1's
  `bundlesVulkan`). That is most of the size difference (11.8 MB vs coah's 4.4 MB). Dropped for
  Quest builds in round 2.
- `fabric.mod.json`: `"version": "0.6.1"`, `"minecraft": ">=1.20 <1.20.2"`,
  `"fabricloader": ">=0.14.14"`, `"java": ">=17"`, `fabric-api: *`, license `MPL-2.0`.
  (coah's is `0.4.0+questcraft`, LGPLv3.) At that point `versions.json` still pointed 1.20.1 at
  coah's jar; round 2 bundles the CI jar instead.

### AtumVR #14 source vs the #27 jars Visor 0.6.1 pins

`javap -protected` over every class of our `atumvr-{api,core}` build vs `Visor/libs/*#27.jar`: the
API is identical; the only difference is the five added `session/platform` classes. So the `#14`
label in `AtumVR/version.properties` looks like a stale build number, not older code. Method
bodies were not compared.

## P1 for 1.20.1, round 2 (branch `hark/visor-builds`)

All three remaining coah80 Quest commits are now ported. Green CI = it compiles; nothing was run on a Quest.

### AtumVR `1d7dd401` ("optimize Quest OpenXR runtime"), commit `34af34a`
Ported by hand onto 1.14 (`git apply` took GLUtils, XRProvider and most of XRState/XRSession; the rest
was redone against 1.14's code):
- `XRProvider.getPreferredRefreshRate()` (default 120) and `getFoveationLevel()` (default `MEDIUM_FB`).
- `XRSession`: app space `LOCAL_FLOOR_EXT` when the runtime has it (else `STAGE`) + `getXrAppSpaceType()`;
  refresh rate: pick the supported rate nearest the preferred one, request it on READY, re-check by
  readback (1 s for 8 tries, then 30 s) and on `DISPLAY_REFRESH_RATE_CHANGED_FB`; CPU/GPU
  `SUSTAINED_HIGH` via `XR_EXT_performance_settings`; `XR_FB_color_space` REC709.
- `XRState`: handles `REFERENCE_SPACE_CHANGE_PENDING` (log) and `DISPLAY_REFRESH_RATE_CHANGED_FB`.
- `XRInstance`: asks for `XR_EXT_local_floor`, `XR_EXT_performance_settings`, `XR_FB_color_space`,
  `XR_FB_foveation(+_configuration)`, `XR_FB_swapchain_update_state` (only enabled if offered); no
  `XR_EXT_debug_utils`/messenger on Android.
- `XRSwapChain`: FB fixed foveated rendering on Android. **Difference from coah:** 1.14 creates one
  swapchain per eye (coah's base had one), so the foveation profile is applied to each eye swapchain.
- `GLUtils.checkGLError` is a no-op on Android unless `-Datumvr.glChecks=true`.
- Not needed: the `XREyeCamera` (reused matrices) and `XRRenderer` (reused projection-view buffer)
  hunks are already in upstream 1.14. The `case VISIBLE`/`FOCUSED` split was cosmetic.
- Behaviour change on desktop too: the refresh rate is now "nearest to 120" instead of "highest".

### Visor `32c80265` + `25740234`, commit `075a66a`
0.6.1 moved/renamed a lot, so each hunk went to where its code lives now:
- `QuestVLoader`: native `getDeviceModel()`, `deviceModel()`, `isQuest2()`, `isQuest3Family()` (same
  signatures as coah's jar, checked with `javap`). `XrProvider.getPreferredRefreshRate()` = 120 on
  Quest 3/3S, else 72 (calls `libvloader`, so this jar is Quest-only).
- Retained overlay textures: `VROverlay` dirty-flag defaults, `VROverlayScreen` marks dirty on
  tick/input/resize/cursor move (0.6.1's input forwarders are `mouseClicked`→`onMouseClicked` etc.),
  `VROverlayManagerImpl` skips clean overlays, cached profiler labels, one projection matrix.
  Opted in: chat, keyboard, settings, options menu, movement state, item-pose test.
  `VROverlayKeyButton` does not exist in 0.6.1 (skipped). HUD region copy only on render.
- Android: no mirror draw (moved to `VRRendererBase.renderMirror`, 0.6.1's mirror path), 1x1 mirror
  target (`RenderTargetMain`), only the two eye passes (`VRRenderState.getActivePasses`).
- sRGB: `GL_FRAMEBUFFER_SRGB` off in `XrRenderer.init()` (back on in `destroy()`), and off before the
  final eye pass in `VRShaderPostProcessEye.finishEye` (state logged once).
- Fewer allocations: `RenderPoseHelper` (shared camera/hand matrices; public `getViewRotation()` still
  returns a new matrix for outside callers), `RenderEffectsHelper` (0.6.1's `VREffectsHelper`:
  shared fullscreen/ortho matrices), `VRPoseImpl` (final mutable fields), `Vector3fHistory`,
  `QuaternionFloatHistory.addOwned` used by `XrRawPoseHandler`.
- Level teardown guards (level set, `gameMode`/`player` null): in the **1.20.1 parked mixins**
  (`visor-core/mcversion/1.20.1/.../MinecraftLoopMixin`, `GameRendererMixin`, `GuiMixin`; 0.6.1
  split coah's `MinecraftMixin`). The `src/main` copies (26.x) were not changed. VR GUI is visible
  whenever a screen or overlay is open (`VRRendererBase`).
- Fabric `WorldPreloader` (+ client screen, `CreateWorldScreenMixin`): 250-block Chunky preload of new
  worlds, only when Chunky is loaded (Pojlib bundles Chunky 1.3.146 for 1.20.1). Compiled against
  `Visor/libs/Chunky-1.3.146.jar` (compileOnly, byte-identical to Pojlib's `mods/Chunky.jar`). The code
  uses 1.20.1 APIs, so it is Stonecutter-gated `<1.20.2`; other targets get a no-op mixin
  (`require = 0`).
- `25740234`: `PlayerPoseType.RELATIVE` alias of `ROOM`. Instead of editing `version.properties`,
  `-Pvisor_quest=true` builds are versioned `0.6.1+questcraft` (build metadata, so `visor >=0.6.1`
  matches).
- Skipped as cosmetic (whitespace/comments only): `VRClientSettings`, `LevelRendererMixin`,
  `vr_post_process_eye.fsh`, `XrRenderTarget`, the `XrRawPoseHandler` gun-angle hunk (code gone in 0.6.1).

### No nested Vulkan in Quest builds
`visor-fabric/build.gradle`: `if (bundlesVulkan && !questBuild)`. Static check of the Quest path:
`org.lwjgl.vulkan` is referenced only by AtumVR `XRVulkanBridge` (+ `$Interop`); its public methods
use no Vulkan types; it is only constructed when `XRInstance.getGraphicsApi() == VULKAN`, and
`resolveGraphicsApi()` returns `OPENGL` whenever the Android bridge is in use. Visor's own classes
reference no Vulkan classes. Not proven at runtime.

### LWJGL newer than 3.3.3 (static scan)
Every `org/lwjgl` class/field/method referenced by the built `Visor-0.6.1+questcraft` jar (shaded
AtumVR included) was resolved against Pojlib's runtime LWJGL (`assets/lwjgl/lwjgl-glfw-classes.jar`,
`org.lwjgl.Version` 3.3.3) plus the nested `lwjgl-openxr-quest-3.3.3.jar`, walking superclasses.
Result: **no missing classes or members** except the 60 `org.lwjgl.vulkan` classes used by
`XRVulkanBridge` (not on the Quest path, see above). Limits: a member that exists in 3.3.3 with
different behaviour, or reflection/native lookups, would not show up.

### CI result and jar check
`Build Visor Quest (1.20.1 Fabric)`: run 46069926820 (`075a66a`) green on the first try; run
46070313239 (`0a311c3`) green and produced a byte-identical jar.
`Visor-0.6.1+questcraft+mc1.20.1-fabric.jar`: 5,066,441 B (was 11,825,930; coah's 0.4.0 jar is
4,441,782), SHA-1 `99bd73a4b936df7f7dbbc00a8d05ab3f22fbb794`. Nested jars: `lwjgl-openxr-quest-3.3.3.jar`
+ fabric-api-base / command-api-v2 / resource-loader-v0 (same set as coah's), **no `lwjgl-vulkan`**.
Quest classes present (`QuestVLoader` with the device-model methods, `QuestAndroidXRBridge`,
`XRPlatform*`, `AndroidXRBridge`, `WorldPreloader*`). `fabric.mod.json`: version `0.6.1+questcraft`,
`minecraft >=1.20 <1.20.2`, `fabric-api *`. The remaining ~0.6 MB over coah's jar is 0.6.1's larger code base.

### Wired into the launcher for 1.20.1 (method a: bundled asset), commit `0a311c3`
- `Pojlib/src/main/assets/mods/Visor.jar` ← the CI jar above; `mods/VisorEssentials.jar` ← Modrinth
  `1JGlKuFI` `VisorEssentials-0.6.1+mc1.20.1-fabric.jar` (SHA-1 `474040d7f5e361878b48e423f464f00ac040fffb`,
  323,812 B; `depends visor >=0.6.1`, `minecraft >=1.20 <1.20.2`). Jars are plain git blobs (LFS only
  covers `*.so` in `Pojlib/.gitattributes`).
- `versions.json` 1.20.1: `visor` version `0.6.1+questcraft`, sha1 `99bd73a4…`, size 5066441;
  `visorEssentials` version `0.6.1+mc1.20.1-fabric`, sha1 `474040d7…`, size 323812; both keep
  `artifactName` `Visor.jar` / `VisorEssentials.jar` and their `assetPath`, because
  `Installer.installVisor` removes old copies by the exact names `visor.jar`/`visoressentials.jar`.
  A `source` note records where each jar came from.
- Why (a): no network or CI artifact (which expires) at APK build time, SHA-1 checked at install, and
  the same path the 0.4.0 jar used. To update: rerun the Visor workflow, copy the jar over
  `mods/Visor.jar`, update sha1/size/version.
- Link check: every `org.vmstudio.visor` / `me.phoenixra` reference in VE 0.6.1 resolves in this jar
  (the same check against coah's 0.4.0 jar finds 9 missing).

### Test APK
`Build Quest APK` (`quest-apk.yml` on `hark/ci-quest-apk` @ `af86b4c`, `pojlib_ref=hark/visor-builds`
(Pojlib at `0a311c3`), `unity_ref` blank), run 46070318564:
https://github.com/twanye/VisorQuest/actions/runs/46070318564 — **success** (`pojlib` and `apk`),
first attempt. Artifact `visorquest-apk-af86b4c3eda1e6f79553d34d3149bfea7293843f` (`VisorQuest.apk`,
57,650,926 B). Checked inside the APK: `assets/mods/Visor.jar` SHA-1 `99bd73a4…`,
`assets/mods/VisorEssentials.jar` SHA-1 `474040d7…`, `assets/versions.json` 1.20.1 = `0.6.1+questcraft`.
This proves it builds and carries the new jars, nothing more.

## What is untested

Everything on device. In particular: the table-driven `installVisor` on 1.20.1, the saves/natives JNI
ports, every AtumVR/Visor Quest change (refresh rate, foveation, local floor, sRGB, retained overlays,
teardown guards, world preloader), Visor 0.6.1 itself on Quest, and VisorEssentials 0.6.1 on Quest.

## Next steps

1. Device-test this APK on 1.20.1 (should match `deepseek/…`): launch, Visor loads, saves/natives buttons.
2. P1 for 1.20.1 is ported and bundled (see "round 2"). Device-test the APK from `hark/visor-builds`:
   Visor loads, refresh rate / foveation / local-floor logs, world preload screen, VE 0.6.1. If it
   breaks, the old pair is coah's `0.4.0+questcraft` + VE `0.4.0` in git history (`54aaf80`).
3. 1.21.1: build the Quest Visor jar for the `1.21.1` Stonecutter target, fill `visor` in
   `versions.json`, decide its perf mods, set `questReady: true`.
4. Then, per `docs/VERSION_MATRIX.md` §5: **1.21.5 → 1.21.7** (Visor `1.21.8` build) **→ 1.21.11**
   (loader `>=0.17.3`, decide P2/MobileGlues first), **then 26.x** (Java 25 JRE, LWJGL 3.4.x and the
   split OpenXR jar, 26.2 Vulkan libs, 26.3 SDL3 last).
5. TODO(User) still open: where VisorQuest hosts its own `mods.json`/`supportedVersions.json`
   (`Constants.UPSTREAM_QUESTCRAFT_MANIFEST_BASE_URL`).

## WIP status / next steps (branch `hark/mc-1.21.1`, Hark, 2026-11-27)

> Stopped early (usage limit). **No code changed yet, no CI run started.** Nothing here is built or tested.

Branch `hark/mc-1.21.1` was created from `hark/visor-builds` @ `91331f8`. Findings so far (read only):

- Visor's per-version sources use `// #!MC-VERSION:: <from>-<to>` range headers in
  `visor-core/mcversion/<ver>/`. The Quest level-teardown guards are already in files whose ranges cover
  1.21.1: `mcversion/1.20.1/.../MinecraftLoopMixin.java` (`1.20.1-1.21.11`), `GuiMixin.java` and
  `GameRendererMixin.java` (`1.20.1-1.21.5`). So 1.21.1 picks them up with no port; only the 1.21.8+ and
  26.x copies of GuiMixin/GameRendererMixin lack them. Whether they compile for 1.21.1 is unverified.
- `WorldPreloader`/`CreateWorldScreenMixin` are already Stonecutter-gated `<1.20.2`, so 1.21.1 gets the no-op path.
- Stonecutter `["1.21.1"]`: Java 21, `fabric_api_version` `0.116.17`, MC range `>=1.21 <1.21.2`.

Next steps (the original task list):
1. `visor-quest-build.yml`: make it a matrix over `1.20.1` and `1.21.1` (the trim step keeps `["fabric"]`
   only for `${{ matrix.mc }}`; build `:visor-fabric:${{ matrix.mc }}:buildAndCollect`; artifact
   `visor-quest-${{ matrix.mc }}-<sha>`), add `hark/mc-1.21.1` to the push branches, dispatch, fix compile errors.
2. Check the 1.21.1 jar (Quest classes, no lwjgl-vulkan, MC range, LWJGL 3.3.3 static scan).
3. Pojlib: per-version Visor/VE assets (e.g. `mods/1.21.1/Visor.jar`), installed names stay
   `visor.jar`/`visoressentials.jar`; bundle VE `0.6.1+mc1.21.1-fabric` (Modrinth `xcYlq5Zb`, SHA-1
   `e5641e7a…`); verified perf mods; `questReady: true`. Keep the 1.20.1 entry and jars byte-identical.
4. Launch-arg/asset-index/`"1.20.1"` hard-code audit against the Mojang 1.21.1 JSON.
5. APK build (`quest-apk.yml` on `hark/ci-quest-apk`, `pojlib_ref=hark/mc-1.21.1`), then the draft PR
   into `hark/visor-builds` ("MC 1.21.1 Quest build (Hark, untested)").

## 1.21.1 and 26.2 (branch `perplexity/workspace`, Perplexity, 2026-11-27)

> Everything below **builds** (CI) and passed static checks. **Nothing has been run on a Quest.**
> questReady is true for both so the launcher offers them for testing.

Branch `perplexity/workspace` starts from `hark/mc-1.21.1` @ `69dfc2d` (Tucker's CI matrix).

### 1.21.1
- `mods/1.21.1/Visor.jar` = `Visor-0.6.1+questcraft+mc1.21.1-fabric.jar` from `visor-quest-build.yml`
  run 46077982516 (SHA-1 `db4b3db1…`). `mods/1.21.1/VisorEssentials.jar` = Modrinth `xcYlq5Zb`
  (SHA-1 `e5641e7a…`). Default runtime: runtimes/JRE (JRE 22 meets Java 21), LightThinWrapper,
  `lwjgl-glfw-classes.jar`.
- No VisorQuest perf mods: upstream mods.json `defaultMods` for 1.21.1 already covers them.
- JNA: 1.21.1 ships JNA 5.14 (native API 7.0.x); the existing `libjnidispatch.so` is 6.1.2 (kept for
  1.20.1's JNA 5.12.1). 1.21.1 now passes `-Djna.boot.library.name=jnidispatch704` to load the new
  `libjnidispatch704.so` (JNA native 7.0.4, same binary as QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1).
- Static scans: Visor/VE references resolve (only off-path `org.lwjgl.vulkan`).

### 26.2
- Visor fix `f4d353f`: `VRRendererBase` used `MC.screen`/`MC.getOverlay()`, which moved to `Gui` in
  26.2; it now uses the existing `McVersionClientUtils.screen()/overlay()` helpers (same field/method
  below 26.2).
- `mods/26.2/Visor.jar` = `Visor-0.6.1+questcraft+mc26.2-fabric.jar` from run 46081665517 (SHA-1
  `97f9945f…`): Quest XR classes, nests `lwjgl-openxr-quest-3.3.3`, no nested lwjgl-vulkan,
  `minecraft >=26.2 <26.3`, `java >=25`, needs `fabric-api`. VE `0.6.1+mc26.2-fabric` = Modrinth
  `AgBlduJ0` (SHA-1 `3dafec24…`).
- Runtime, all from the new optional versions.json fields (`RuntimeProfile`):
  - `runtime`: JRE 25 (OpenJDK 25.0.4.1.1, `JRE25.zip` from MisterEnglishDev/QuestCraftPlus-Pojlib
    release `jre25-android-arm64`, SHA-256 checked) installed in `runtimes/JRE-25` next to `runtimes/JRE`.
  - `renderer: MobileGlues`: `POJLIB_RENDERER=MobileGLUES`, `MG_DIR_PATH=<filesDir>/mg`,
    `egl_bridge.c` dlopens `libmobileglues.so` (from QuestCraft-6.0.1). The pbuffer EGL path is kept;
    QuestCraft 6.0.1 uses `EGL_NO_SURFACE` with MobileGlues, try that first if EGL setup fails.
  - `lwjglAssetPath`: `lwjgl-glfw-classes-26.jar` (3.3.3 shim + QuestCraftPlus+ GLFW 3.4 / IME /
    MemoryUtil / STB patches; rebuild with `jarMc26` or `tools/build_mc26_jar.py`).
    `lwjglVulkanClasses`: keeps `org.lwjgl:lwjgl-vulkan` Java classes (no natives), since the 26.2 client
    constructs its Vulkan backend class; OpenGL is tried first.
  - `extraJvmArgs`: `--sun-misc-unsafe-memory-access=allow`, `--enable-native-access=ALL-UNNAMED`
    (vanilla 26.2 flags), `-Djna.boot.library.name=jnidispatch704` (JNA 5.17). `-XX:+ZGenerational` is
    only passed on the default JRE (obsolete in JDK 24+).
  - `installFabricApi`: upstream mods.json has no 26.2 entry, so Fabric API 0.161.0+26.2 (Modrinth
    `ewUK83HI`) is installed from versions.json unless another fabric-api jar is already present.
- Static scans: all non-Vulkan LWJGL refs in the 26.2 Visor jar resolve against the 26 bundle + nested
  OpenXR jar; Vulkan refs resolve against lwjgl-vulkan 3.4.1. MC 26.2 client vs the 26 bundle: only
  `SOFTSystemEvents` (OpenAL, capability-guarded) and `tinyfd_messageBox(CharSequence)` (crash dialog).
- 1.20.1: entries without the new fields resolve to the old hard-coded values (runtimes/JRE,
  `lwjgl-glfw-classes.jar`, `libltw.so`, same JVM flags), so it launches as before.

### Not done / untested
- No device test of either version; no Quest APK build yet (`quest-apk.yml` on `hark/ci-quest-apk`,
  `pojlib_ref=perplexity/workspace`, needs the Unity secrets).
- JRE25.zip has no published build source yet: fine for testing, needs sorting before a public release.
- 26.x Quest options defaults (QuestCraftPlus+ `a078860`: simulationDistance 5, gamma 0) not applied.
- 26.3 (SDL3) not started.
