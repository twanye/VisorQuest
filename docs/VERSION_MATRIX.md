# VisorQuest — Version Matrix

Status: **every row is untested.** Nothing here has been built or run on a headset. Data pulled 2026-11-23 from Mojang piston-meta, meta.fabricmc.net, the Modrinth API, and the jars' own `fabric.mod.json`. Architecture background: `docs/ARCHITECTURE.md`.

## Baseline today (coah80 build, 1.20.1 only)
- **JRE:** one runtime for all versions, OpenJDK **22.0.1** (`android-openjdk-build-multiarch` release `jre22-6.0.0`, fetched via `releases/latest`). It already meets Java 17 and Java 21; it does **not** meet Java 25.
- **LWJGL:** one Pojlib build for all versions, **3.3.3-snapshot** (`Pojlib/jre_lwjgl3glfw/libs/lwjgl.jar` manifest). Mojang's LWJGL libraries are skipped at install.
- **Fabric loader:** newest on meta.fabricmc.net at instance creation (0.20.1 on 2026-11-23). This meets every minimum below.
- **Visor:** bundled `Visor.jar` 0.4.0+questcraft and stock `VisorEssentials.jar` 0.4.0, both 1.20.1-only. `Installer.installVisor` refuses any other version.
- **Upstream Modrinth Visor jars lack the Quest/Android code** (no `QuestVLoader`, `XRPlatform`, `AndroidXRBridge`; they shade desktop `lwjgl-openxr` 3.3.6/3.4.x). **They cannot be dropped in as-is.** Every target needs a Quest build of Visor (coah80's patches ported to upstream 0.6.1). VisorEssentials has no Android code, so the stock jar *may* work unmodified (untested).

## Change catalogue (referenced in the table)

**Pojlib**
- **P0 – Generalise the Visor install (all versions).** Replace the 1.20.1 gate in `Installer.installVisor` with a per-version entry in a VisorQuest-owned manifest (Visor + VisorEssentials + Fabric API + perf mods per MC version). Point `InstanceHandler.MODS/DEV_MODS` and `APIHandler.SUPPORTED_VERSIONS` at JSON in **this** repo, not `QuestCraftPlusPlus/Pojlib@QuestCraft-6.0.0`. Drop Vivecraft from `coreMods`. Key the bundled perf jars (C2ME, Noisium, MoreCulling, Chunky, BadOptimizations, the ImmediatelyFast 1.5.5 pin) per version, or move them to the manifest.
- **P1 – Quest Visor build per version.** Port `QuestVLoader`, `QuestAndroidXRBridge`, `XrProvider` hook, perf changes (Visor `a0275017`, `25740234`, `32c80265`) onto upstream Visor `dev` (Stonecutter, 0.6.1). Port `XRPlatform`/`AndroidXRBridge` (AtumVR `d04c1669`, `1d7dd401`) onto upstream AtumVR 1.14. Swap the shaded desktop `lwjgl-openxr` for the Quest jar. Ideally upstream both patches to VisorModStudio and Phoenix-Ra.
- **P2 – Renderer.** Consider rebasing on upstream Pojlib `QuestCraft-6.0.1`, which replaced `libltw.so` with MobileGlues and is what upstream QuestCraft uses through 1.21.11. Conflicts with coah80's Unity 6 `UnityPlayerActivity` port.
- **P3 – Java 25 runtime.** Add a second JRE (25) and pick it per instance from the version JSON's `javaVersion.majorVersion` (`MinecraftMeta`/`VersionInfo` + `Installer.installJVM`). jringo12's experiment does exactly this. Also pin the JRE by tag; `releases/latest` can change under us.
- **P4 – LWJGL 3.4.x.** Per-version LWJGL bundle: keep 3.3.3 for ≤1.21.11, and build 3.4.1 (26.1–26.2) / 3.4.3 (26.3) Pojav-style jars, `lwjgl-glfw-classes.jar` and arm64 natives (`jniLibs`). In 3.4.1, `CallbackI` gained abstract methods. That breaks `lwjgl-openxr-quest-3.3.3.jar`, so use official `lwjgl-openxr` 3.4.x plus a small jar with the 13 Android-only OpenXR classes (the QuestCraftPlus approach).
- **P5 – JVM args from the version JSON.** `generateLaunchArgs` is hand-built. 26.1/26.2 add `--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED`. 26.3 adds `-XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED`.
- **P6 – Vulkan libraries (26.2+).** The JSON adds `lwjgl-vulkan`, `-shaderc`, `-spvc`, `-vma` (and `lwjgl:unsafe` replaces core `lwjgl` in 26.2). Force OpenGL (`preferredGraphicsBackend:"opengl"` in `options.txt`) and make sure class loading does not need missing natives.
- **P7 – SDL3 (26.3).** 26.3 drops `lwjgl-glfw` for `lwjgl-sdl`. Pojlib's window/input layer is a **GLFW stub** (`jre_lwjgl3glfw/.../org/lwjgl/glfw/GLFW.java`, `CallbackBridge`, `input_bridge_v3.c`), so it needs an SDL3 equivalent.
- **P8 – Unobfuscated 26.x.** The version JSONs have no `client_mappings`. Fabric handles this, but check any Pojlib/Visor code or mixins that assume intermediary names (low effort, flagged for completeness).

**Launcher (QCXR)**
- **L0 – Version picker.** Replace the hard-coded `{"1.20.1"}` (Pojlib `API.getQCSupportedVersions`) with the VisorQuest `supportedVersions.json`. `JNIStorage.FillSupportedVersions` → `UIHandler.UpdateDropdowns` then works unchanged. Rename the build output (`QuestCraftVisorBuild.cs` → not `QuestCraft-VISOR-1.20.1.apk`). Point `UpdateChecker.cs`/`DevHandler.cs` at VisorQuest releases.
- **L1 – Rebuild `Assets/Plugins/Android/Pojlib-release.aar`** after any Pojlib change (every row).
- **L2 – APK size / first-run download.** Bundling a Java 25 JRE (~61 MB zip in jringo12's repo) and extra LWJGL sets grows the APK. Prefer download-on-demand.
- **L3 – Graphics API.** Hide/force OpenGL for 26.2+ (Vulkan on the Quest is an open question).
- **L4 – XR hand-off re-check.** Re-verify the `gameReady`/`launcherXRStopped` handshake per version (no code change expected).

## Matrix

Visor/VisorEssentials = latest **Fabric** build on Modrinth that lists the target in `game_versions`. Every one is 0.6.1, published 2026-10-31, release channel, requires Fabric API. VisorEssentials requires `visor >= 0.6.1`. "Fabric loader min" is the `depends.fabricloader` in those jars. meta.fabricmc.net offers every loader for every target, so it sets no minimum.

| MC | Java (Mojang) | LWJGL (Mojang) | Fabric loader min | Visor (Fabric) | VisorEssentials (Fabric) | Pojlib changes | Launcher changes | Risk |
|---|---|---|---|---|---|---|---|---|
| **1.20.1** (keep) | 17 | 3.3.1 | ≥0.14.14 | `0.6.1+mc1.20.1-fabric` ([cVPfjqQe](https://modrinth.com/mod/visor/version/cVPfjqQe)); shipped today: 0.4.0+questcraft | `0.6.1+mc1.20.1-fabric` ([1JGlKuFI](https://modrinth.com/mod/visoressentials/version/1JGlKuFI)); shipped: 0.4.0 | P0; P1 only if moving to 0.6.1 (recommended for parity across versions) | L0, L1 | **Low.** Working baseline (per coah80's releases); the risk is only regressions from moving to 0.6.1. |
| **1.20.5** | 21 | 3.3.3 | ≥0.14.14 | `0.6.1+mc1.20.6-fabric` ([ifg1X5xT](https://modrinth.com/mod/visor/version/ifg1X5xT)); covers 1.20.5–1.20.6 | `0.6.1+mc1.20.6-fabric` ([g6pTrqJl](https://modrinth.com/mod/visoressentials/version/g6pTrqJl)) | P0, P1 (Stonecutter target 1.20.6), new perf-mod list | L0, L1 | **Med.** JRE 22 and LWJGL 3.3.3 already match, but upstream QuestCraft never listed 1.20.5, so it is unproven on a Quest. |
| **1.20.6** | 21 | 3.3.3 | ≥0.14.14 | same jar as 1.20.5 | same jar as 1.20.5 | P0, P1, perf mods | L0, L1 | **Med.** Upstream QuestCraft lists 1.20.6, but its own commit `0c6fd773` says "we gotta figure out … 1.20.6". |
| **1.21.1** | 21 | 3.3.3 | ≥0.14.14 | `0.6.1+mc1.21.1-fabric` ([FJN2Te69](https://modrinth.com/mod/visor/version/FJN2Te69)) | `0.6.1+mc1.21.1-fabric` ([xcYlq5Zb](https://modrinth.com/mod/visoressentials/version/xcYlq5Zb)) | P0, P1, perf mods | L0, L1 | **Low–Med.** Supported by upstream QuestCraft 6.0.0 and 6.0.1 on the same runtime. Only the Visor Quest port is new. |
| **1.21.5** | 21 | 3.3.3 | ≥0.14.14 | `0.6.1+mc1.21.5-fabric` ([C4bBZJZj](https://modrinth.com/mod/visor/version/C4bBZJZj)) | `0.6.1+mc1.21.5-fabric` ([7VekFC6l](https://modrinth.com/mod/visoressentials/version/7VekFC6l)) | P0, P1, perf mods; maybe P2 | L0, L1 | **Med.** Upstream QuestCraft supports it, but render internals changed (Visor keeps a separate 1.21.5 target, inferred). coah80's GL-level perf patches must be re-applied. |
| **1.21.7** | 21 | 3.3.3 | ≥0.14.14 | `0.6.1+mc1.21.8-fabric` ([7fKWdcvJ](https://modrinth.com/mod/visor/version/7fKWdcvJ)); covers 1.21.6–1.21.8 | `0.6.1+mc1.21.8-fabric` ([gfB0Ga34](https://modrinth.com/mod/visoressentials/version/gfB0Ga34)) | P0, P1 (Stonecutter target 1.21.8), P2 likely | L0, L1 | **Med.** There is no exact Visor target, and upstream QuestCraft lists 1.21.8 but not 1.21.7, so the perf-mod set must be checked per mod. |
| **1.21.11** | 21 | 3.3.3 | **≥0.17.3** | `0.6.1+mc1.21.11-fabric` ([CUHDRyuw](https://modrinth.com/mod/visor/version/CUHDRyuw)) | `0.6.1+mc1.21.11-fabric` ([EMSERYhW](https://modrinth.com/mod/visoressentials/version/EMSERYhW)) | P0, P1, **P2 strongly recommended** | L0, L1 | **Med.** This is the last version on Java 21/LWJGL 3.3.3. Upstream reached it only on the 6.0.1 branch (MobileGlues). |
| **26.1** | **25** | **3.4.1** | **≥0.18.4** | `0.6.1+mc26.1.2-fabric` ([X7IdndEP](https://modrinth.com/mod/visor/version/X7IdndEP)); covers 26.1–26.1.2 | `0.6.1+mc26.1.2-fabric` ([zpbHeDb4](https://modrinth.com/mod/visoressentials/version/zpbHeDb4)) | P0, P1 (+ AtumVR on LWJGL 3.4.1), **P3, P4, P5**, P8 | L0, L1, L2 | **High.** Needs a new JRE (25) and a new LWJGL (3.4.1, with a native rebuild and an OpenXR binding ABI break) at the same time. |
| **26.1.1** | 25 | 3.4.1 | ≥0.18.4 | same jar as 26.1 | same jar as 26.1 | as 26.1 | as 26.1 | **High** (as 26.1). A hotfix, so it is cheap once 26.1 works. |
| **26.1.2** | 25 | 3.4.1 | ≥0.18.4 | same jar as 26.1 | same jar as 26.1 | as 26.1 | as 26.1 | **High** (as 26.1). Same as 26.1.1. |
| **26.2** | 25 | 3.4.1 (+ `lwjgl:unsafe`, vulkan/shaderc/spvc/vma) | **≥0.19.5** | `0.6.1+mc26.2-fabric` ([egOqPxop](https://modrinth.com/mod/visor/version/egOqPxop)) | `0.6.1+mc26.2-fabric` ([AgBlduJ0](https://modrinth.com/mod/visoressentials/version/AgBlduJ0)) | 26.1 set + **P6** | + L3 | **High.** Adds the Vulkan libraries and the experimental Vulkan option. Two community Vivecraft ports exist to learn from (neither uses Visor). |
| **26.3** | 25 | **3.4.3** (+ **SDL3**, no GLFW) | ≥0.19.5 | `0.6.1+mc26.3-fabric` ([UtojxWaX](https://modrinth.com/mod/visor/version/UtojxWaX)) | `0.6.1+mc26.3-fabric` ([Q08QAQ7J](https://modrinth.com/mod/visoressentials/version/Q08QAQ7J)) | 26.2 set + **P7** (SDL3 stub) + LWJGL 3.4.3 build | + L3 | **High (highest).** SDL3 replaces GLFW, which breaks Pojlib's whole window/input shim. No Quest port of 26.3 has been found. |
| 1.20.7 / 1.20.8 | — | — | — | — | — | — | — | **On hold.** Not in Mojang's version manifest (the 1.20 line ends at 1.20.6). Waiting on Tucker. |

## Recommended implementation order

0. **Foundation (no new MC version yet).** Push the missing Git LFS natives so Pojlib builds from this repo. Do P0 (own manifests, remove the 1.20.1 gate) and L0/L1. Do P1 for **1.20.1 first**: a Quest build of upstream Visor 0.6.1 + AtumVR 1.14 that matches coah80's 0.4.0 build on a headset. *Why:* every later row depends on the per-version manifest and the Visor/AtumVR port, and 1.20.1 is the only version with a known-good reference to compare against.
1. **1.20.6, then 1.20.5** (one Visor jar covers both). *Why:* this is the smallest real step. The "Java 21 jump" is already covered by the JRE 22 runtime, and LWJGL 3.3.3 matches exactly. So the step tests the multi-version plumbing and the Visor port without runtime changes. (This corrects the handoff's assumption that 1.20.5 forces a runtime change: the real runtime jump is Java 25 at 26.x.)
2. **1.21.1 → 1.21.5 → 1.21.7 → 1.21.11.** *Why:* same JRE and LWJGL as step 1, and upstream QuestCraft has shipped each family on this runtime. Decide on P2 (rebase on Pojlib 6.0.1/MobileGlues) before 1.21.5. Upstream needed 6.0.1 to reach 1.21.11.
3. **26.1 / 26.1.1 / 26.1.2** together. *Why:* they share one Visor jar, Java 25 and LWJGL 3.4.1. This is where P3, P4 and P5 land, the largest runtime change in the plan.
4. **26.2.** *Why:* the 26.1 runtime plus Vulkan libraries (P6). Force OpenGL first; Vulkan on the Quest is a separate project.
5. **26.3 last.** *Why:* it needs everything above plus an SDL3 replacement for Pojlib's GLFW layer (P7) and LWJGL 3.4.3. It has the most unknowns, and no reference port exists.

## Open questions / unknowns

1. **LFS natives:** the `twanye/VisorQuest` LFS store returns 404 for Pojlib's `.so` objects. Can we pull them from coah80/Pojlib or QuestCraftPlusPlus/Pojlib (not verified; the API refused the cross-repo check), and is there enough LFS quota?
2. **Provenance of `lwjgl-openxr-quest-3.3.3.jar`** (used by Visor and AtumVR) and of AtumVR's missing `../deps/` path. Who builds it, and can we rebuild it for 3.4.x, or should we use the "13 Android classes + official lwjgl-openxr" split?
3. **Upstreaming:** will VisorModStudio/Phoenix-Ra accept an Android `XRPlatform`/bridge? If yes, P1 shrinks to "build upstream with a Quest flag". If no, we maintain a Stonecutter fork.
4. **Renderer choice:** stay on `libltw.so` (6.0.0) or move to MobileGlues (6.0.1)? Does either support what Visor's renderer needs (FBO/texture formats) on Quest 2 vs Quest 3?
5. **Unity versions:** coah80 moved the launcher to Unity 6000.4.10f1 and ported `UnityPlayerActivity` to `UnityPlayerForActivityOrService`. Upstream Pojlib 6.0.1 targets Unity 2022.3. Merging the two needs care.
6. **LWJGL 3.3.3 module coverage:** 1.20.5+ adds `lwjgl-freetype`. Pojlib ships `lwjgl-freetype.jar` but no `liblwjgl_freetype.so` in `jniLibs`. Upstream runs 1.20.6–1.21.11 anyway, so the native is probably loaded lazily or not needed (unverified).
7. **Java 25 on Android:** no Java 25 build exists in `QuestCraftPlusPlus/android-openjdk-build-multiarch` (branches stop at `buildjre24`). jringo12 ships a `JRE-25.zip`; its build provenance is unknown.
8. **Fabric loader pinning:** Pojlib always takes the newest loader (and `FabricMeta.getLatestVersion` has a comparison bug). Should we pin per version? (jringo12 pins 0.19.3 for 26.2, which is **below** Visor's ≥0.19.5, so don't copy that pin.)
9. **VisorEssentials unmodified on Quest:** coah80 shipped the stock 0.4.0 jar, which suggests 0.6.1 will also work unmodified. Untested.
10. **Vulkan on Quest (26.2+)** and Mojang's plan to make Vulkan the default in 26.4. Out of scope here, but it decides how long "force OpenGL" keeps working.
11. **Licensing:** Visor is MPL-2.0 and VisorEssentials is LGPL-3.0-only (Modrinth). `libltw.so` (LightThinWrapper) is PolyForm Shield per Pojlib's README; check before any public release.
12. **1.20.7 / 1.20.8:** Tucker to confirm whether he meant something else (e.g. 1.21.7/1.21.8) or drop them.

## Sources
- Mojang version manifest: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json. Per-version JSONs used for `javaVersion.majorVersion`, LWJGL libraries and `arguments.jvm`:
  1.20.1 https://piston-meta.mojang.com/v1/packages/fb730d2829f057fe26a41e5ecdc9e3307333bef5/1.20.1.json ·
  1.20.5 https://piston-meta.mojang.com/v1/packages/5aed5fd00ddc6b1f1c0ad4a8ed191eb6f1b77446/1.20.5.json ·
  1.20.6 https://piston-meta.mojang.com/v1/packages/e614a15052d9bf81d314ff0111aaa21f7144115d/1.20.6.json ·
  1.21.1 https://piston-meta.mojang.com/v1/packages/ff7ef54488da4a711f3c8a781a9f79105bbe801a/1.21.1.json ·
  1.21.5 https://piston-meta.mojang.com/v1/packages/d7eabdfb864d74c6d3942cdb864405778e948f28/1.21.5.json ·
  1.21.7 https://piston-meta.mojang.com/v1/packages/5149e9aa052c041683a5b2d80aecaaabde953b87/1.21.7.json ·
  1.21.11 https://piston-meta.mojang.com/v1/packages/a969d96978af7b52dba8df44e177f48ec4ebe0d8/1.21.11.json ·
  26.1 https://piston-meta.mojang.com/v1/packages/224ef4d5ccb0a181b750765f2fdc591e0072bd3a/26.1.json ·
  26.1.1 https://piston-meta.mojang.com/v1/packages/3b4b2a18cae8ce8e7dc9255035cb951165ff1c5a/26.1.1.json ·
  26.1.2 https://piston-meta.mojang.com/v1/packages/80bb32e704c07fb2c44066a2c1774cb460d7e22b/26.1.2.json ·
  26.2 https://piston-meta.mojang.com/v1/packages/cf103b8fab4900562221bf7f23b72725dd9a914a/26.2.json ·
  26.3 https://piston-meta.mojang.com/v1/packages/ead839cbccf0ddf013c3f935f2840080b5cb0d26/26.3.json
- Fabric: https://meta.fabricmc.net/v2/versions/loader and `/v2/versions/loader/<mc>` (latest stable 0.20.1 on 2026-11-23)
- Modrinth: https://api.modrinth.com/v2/project/visor/version, https://api.modrinth.com/v2/project/visoressentials/version (and each jar's `fabric.mod.json`)
- Visor build targets: https://github.com/VisorModStudio/Visor/blob/dev/stonecutter.properties.toml
- JRE: https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/tag/jre22-6.0.0
- Upstream Pojlib supported versions: https://github.com/QuestCraftPlusPlus/Pojlib/blob/QuestCraft-6.0.0/supportedVersions.json, https://github.com/QuestCraftPlusPlus/Pojlib/blob/QuestCraft-6.0.1/supportedVersions.json
- 26.2 Vulkan (experimental, OpenGL default): https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2, https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-8
- 26.3 SDL3 replaces GLFW: https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-4, https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
- LWJGL 3.4.1 `CallbackI` break + Android OpenXR class split: https://github.com/MisterEnglishDev/QuestCraftPlus/blob/main/docs/PORTING_NOTES.md
- Per-version JRE selection / JRE-25 asset: https://github.com/jringo12/questcraft-26.2-experiment (`Pojlib/src/main/java/pojlib/install/Installer.java`)
