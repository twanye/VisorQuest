# VisorQuest Version Matrix

> **Status: UNTESTED. Planning document only. Every row is untested on hardware.** No version other than 1.20.1 has been built, and even 1.20.1 has not been built or run by this team (coah80's v1.1.1 release is reported working in the handoff; not independently verified).
> Merged 2026-11-24 from Hark's research pass (2026-11-23: Mojang piston-meta, meta.fabricmc.net, Modrinth API, jars' own `fabric.mod.json`) and Claude's static review (2026-11-23). Architecture background: `docs/ARCHITECTURE.md`.
>
> Evidence tags: **[read]** seen in repo/jar metadata; **[web]** from a public page or API (URL in *Sources*); **[memory]** background knowledge, verify before relying on it; **[assumed]** extrapolated, no evidence; **[inferred]** deduced; **[untested]** no hardware test. Where Hark's pass supplied a sourced value, it replaces Claude's earlier [memory]/[assumed]/unknown cell.
> Risk = judgement of "likelihood this version needs substantial new work or hits a blocker": **low / med / high**.

## 1. Summary

Visor/VisorEssentials = latest **Fabric** build on Modrinth listing the target in `game_versions`. Every one is **0.6.1**, published 2026-10-31, release channel, requires Fabric API; VisorEssentials requires `visor >= 0.6.1` [web]. "Fabric loader min" is `depends.fabricloader` in those jars [read: jar]. Java/LWJGL columns are from each Mojang version JSON [web]. Change codes (P*, L*) are defined in §2.

| MC | Java | LWJGL (Mojang) | Fabric loader min | Visor / VisorEssentials (Fabric) | Pojlib changes | Launcher changes | Risk |
|---|---|---|---|---|---|---|---|
| **1.20.1** (keep) | 17 [web] | 3.3.1 [web] (Pojlib ships 3.3.3-snapshot [read]) | ≥0.14.14 [read] | shipped: `0.4.0+questcraft` / `0.4.0` bundled [read]; upstream `0.6.1+mc1.20.1-fabric` ([cVPfjqQe](https://modrinth.com/mod/visor/version/cVPfjqQe) / [1JGlKuFI](https://modrinth.com/mod/visoressentials/version/1JGlKuFI)) | P0 (de-hardcode); P1 if moving to 0.6.1 (recommended for parity) | L0, L1 | **low** |
| **1.20.5** | 21 [web] | 3.3.3 [web] | ≥0.14.14 [read] | `0.6.1+mc1.20.6-fabric` ([ifg1X5xT](https://modrinth.com/mod/visor/version/ifg1X5xT) / [g6pTrqJl](https://modrinth.com/mod/visoressentials/version/g6pTrqJl)); one build covers 1.20.5–1.20.6 | P0, P1 (Stonecutter target 1.20.6), new perf-mod list; no upstream `mods.json` precedent | L0, L1 | **med** |
| **1.20.6** | 21 [web] | 3.3.3 [web] | ≥0.14.14 | same jar as 1.20.5 | P0, P1, perf mods; upstream entry exists (Fabric API 0.100.8) [read] | L0, L1 | **med** |
| **1.21.1** | 21 [web] | 3.3.3 [web] | ≥0.14.14 | `0.6.1+mc1.21.1-fabric` ([FJN2Te69](https://modrinth.com/mod/visor/version/FJN2Te69) / [xcYlq5Zb](https://modrinth.com/mod/visoressentials/version/xcYlq5Zb)) | P0, P1, perf mods; upstream entry exists (Fabric API 0.116.7) [read] | L0, L1 | **low–med** ¹ |
| **1.21.5** | 21 [web] | 3.3.3 [web] | ≥0.14.14 | `0.6.1+mc1.21.5-fabric` ([C4bBZJZj](https://modrinth.com/mod/visor/version/C4bBZJZj) / [7VekFC6l](https://modrinth.com/mod/visoressentials/version/7VekFC6l)) | P0, P1, perf mods; maybe P2; upstream entry exists (Fabric API 0.128.2) [read] | L0, L1 | **med** |
| **1.21.7** | 21 [web] | 3.3.3 [web] | ≥0.14.14 | `0.6.1+mc1.21.8-fabric` ([7fKWdcvJ](https://modrinth.com/mod/visor/version/7fKWdcvJ) / [gfB0Ga34](https://modrinth.com/mod/visoressentials/version/gfB0Ga34)); 1.21.8 build covers 1.21.6–1.21.8 | P0, P1 (target 1.21.8), P2 likely; **no** upstream entry | L0, L1 | **med** |
| **1.21.11** | 21 [web] | 3.3.3 [web] | **≥0.17.3** [read] | `0.6.1+mc1.21.11-fabric` ([CUHDRyuw](https://modrinth.com/mod/visor/version/CUHDRyuw) / [EMSERYhW](https://modrinth.com/mod/visoressentials/version/EMSERYhW)) | P0, P1, **P2 strongly recommended**; **no** upstream 6.0.0 entry | L0, L1 | **med–high** ¹ |
| **26.1** | **25** [web] | **3.4.1** [web] | **≥0.18.4** [read] | `0.6.1+mc26.1.2-fabric` ([X7IdndEP](https://modrinth.com/mod/visor/version/X7IdndEP) / [zpbHeDb4](https://modrinth.com/mod/visoressentials/version/zpbHeDb4)); covers 26.1–26.1.2 | P0, P1 (+ AtumVR on LWJGL 3.4.1), **P3, P4, P5**, P8 | L0, L1, L2 | **high** |
| **26.1.1** | 25 [web] | 3.4.1 [web] | ≥0.18.4 | same jar as 26.1 | as 26.1 | as 26.1 | **high** (cheap once 26.1 works) |
| **26.1.2** | 25 [web] | 3.4.1 [web] | ≥0.18.4 | same jar as 26.1 | as 26.1 | as 26.1 | **high** (as 26.1.1) |
| **26.2** | 25 [web] | 3.4.1 + `lwjgl:unsafe`, vulkan/shaderc/spvc/vma [web] | **≥0.19.5** [read] | `0.6.1+mc26.2-fabric` ([egOqPxop](https://modrinth.com/mod/visor/version/egOqPxop) / [AgBlduJ0](https://modrinth.com/mod/visoressentials/version/AgBlduJ0)) | 26.1 set + **P6** (force OpenGL) | + L3 | **high** |
| **26.3** | 25 [web] | **3.4.3** + **SDL3, no GLFW** [web] | ≥0.19.5 [read] | `0.6.1+mc26.3-fabric` ([UtojxWaX](https://modrinth.com/mod/visor/version/UtojxWaX) / [Q08QAQ7J](https://modrinth.com/mod/visoressentials/version/Q08QAQ7J)) | 26.2 set + **P7** (SDL3 stub) + LWJGL 3.4.3 build | + L3 | **high (highest)** |
| 1.20.7 / 1.20.8 | — | — | — | — | — | — | **on hold**: not in Mojang's manifest (1.20 ends at 1.20.6). Waiting on Tucker. |

¹ **Risk disagreement.** Claude rated 1.21.1 *med* and 1.21.11 *high*; Hark rated them *low–med* and *med*. Kept Hark's 1.21.1 (upstream QuestCraft ships it on this exact JRE/LWJGL) and split the difference on 1.21.11 (*med–high*): it needs no runtime change, but upstream only reached it on Pojlib 6.0.1/MobileGlues and there is no ready mod list.

**Correction to the handoff's constraints:** the Java 21 jump at 1.20.5 is **already covered** by the current runtime (OpenJDK 22.0.1) and LWJGL 3.3.3 matches Mojang's 1.20.5–1.21.11 exactly. The real runtime jump is **26.x: Java 25 + LWJGL 3.4.x**, plus Vulkan libraries (26.2) and SDL3 replacing GLFW (26.3).

**Superseded claims from Claude's review** (better-sourced values above): 1.20.5–1.21.x LWJGL "3.3.x [memory, verify]" → 3.3.3 from Mojang JSONs; 26.2/26.3 Java 25 and LWJGL "[assumed]" → read from Mojang JSONs (26.3 is 3.4.3, not 3.4.1); "Visor per-version file not confirmed" for 1.20.5/1.20.6/1.21.5/1.21.7 → confirmed 0.6.1 builds on Modrinth; Claude's alpha-release range notes (0.6.0-alpha.1 = 1.20.1–1.21.1, etc.) are dropped in favour of the 0.6.1 per-target builds; unknown loader minimums → filled from jars.

## 2. Per-column notes

### Java runtime
- Today: **one** runtime dir (`runtimes/JRE`) from `…/releases/latest/download/JRE.zip`, resolving to `jre22-6.0.0` = **OpenJDK 22.0.1** [web]; validated by file presence only [read]. Meets Java 17 and 21; **not** Java 25.
- **P3 – Java 25 runtime.** Add a second JRE (25), chosen per instance from the version JSON's `javaVersion.majorVersion` (`MinecraftMeta`/`VersionInfo` + `Installer.installJVM`); jringo12's experiment does this [web]. Pin each JRE by tag + SHA-1 instead of `latest`.
- **Blocker (eased 2026-11-25):** no *QuestCraft* Java 25 Android JRE exists: `QuestCraftPlusPlus/android-openjdk-build-multiarch` branches stop at `buildjre24` [web]. jringo12 ships a `JRE-25.zip` (~61 MB) of unknown provenance [web]. **Buildable source:** [FCL-Team/Android-OpenJDK-Build `Build_JRE_25`](https://github.com/FCL-Team/Android-OpenJDK-Build/tree/Build_JRE_25) builds JRE 25 for Android (aarch64/aarch32/x86_64) with NDK r28 and a JDK 24 boot JDK; it is FoldCraftLauncher's runtime [web]. Build our own from it so provenance is known [untested on Quest].
- JVM flags `-XX:+UseZGC -XX:+ZGenerational` [read]: on newer JDKs `ZGenerational` is deprecated/obsolete [memory]; whether a Java 25 build tolerates it is **unknown** [untested]. Fallback: `-XX:+IgnoreUnrecognizedVMOptions` or drop the flag per runtime.

### LWJGL
- Pojlib skips every Mojang `lwjgl*` library and supplies its own fat jar + `liblwjgl*.so` natives [read], so **LWJGL is ours to match per MC version**.
- 1.20.5 → 1.21.11: Mojang uses 3.3.3 [web]; Pojlib's 3.3.3-snapshot matches. 1.20.5+ adds `lwjgl-freetype`: Pojlib ships the jar but no `liblwjgl_freetype.so`; upstream runs 1.20.6–1.21.11 anyway, so probably loaded lazily or unneeded (unverified).
- **P4 – LWJGL 3.4.x.** Keep 3.3.3 for ≤1.21.11; build 3.4.1 (26.1–26.2) and 3.4.3 (26.3) Pojav-style jars, `lwjgl-glfw-classes.jar` equivalents and arm64 natives. In 3.4.1 `CallbackI` gained abstract methods, which **breaks `lwjgl-openxr-quest-3.3.3.jar`**; use official `lwjgl-openxr` 3.4.x plus a small jar with the 13 Android-only OpenXR classes (QuestCraftPlus approach) [web]. See **§2a** for the working reference. So one bundle cannot serve both families; ship per-version bundles keyed by the version table.
- **P6 – Vulkan libraries (26.2+).** The JSON adds `lwjgl-vulkan`, `-shaderc`, `-spvc`, `-vma`, and `lwjgl:unsafe` replaces core `lwjgl` [web]. Vulkan is experimental with OpenGL default in 26.2 [web]; force OpenGL (`preferredGraphicsBackend:"opengl"` in `options.txt`) and make sure class loading doesn't need missing natives.
- **P7 – SDL3 (26.3).** 26.3 drops `lwjgl-glfw` for `lwjgl-sdl` [web]. Pojlib's window/input layer is a **GLFW stub** (`jre_lwjgl3glfw/.../org/lwjgl/glfw/GLFW.java`, `CallbackBridge`, `input_bridge_v3.c`) [read], so it needs an SDL3 equivalent. No Quest port of 26.3 has been found.

### 2a. Reference: QuestCraftPlus+ (working 26.2 on Quest)
[MisterEnglishDev/QuestCraftPlus](https://github.com/MisterEnglishDev/QuestCraftPlus) is an independent QuestCraft rebuild that runs **Minecraft 26.2** on Quest (APK `com.questcraftplus.qcxr`, installs beside stock QuestCraft). Tucker uses it to play servers, so it is a known-good comparison on real hardware [user-reported]. What it does [web]:
- **Fixes the LWJGL 3.4.1 `CallbackI` ABI break** (`XrDebugUtilsMessengerCallbackEXTI` no longer satisfies `CallbackI`). The ~13 Android-only classes from QuestCraft's custom bindings (`KHR_android_create_instance`, `KHR_loader_init_android`, `KHR_opengl_es_enable`) go in a small standalone jar; everything else uses official `org.lwjgl:lwjgl-openxr`. The classes have no natives; entry points resolve at runtime from QuestCraft's `libopenxr_loader.so` (which VisorQuest's Pojlib now has). This answers open question 2 for 26.x.
- **Pojlib:** [MrEnglish1996/QuestCraftPlus-Pojlib](https://github.com/MrEnglish1996/QuestCraftPlus-Pojlib), branched from `QuestCraft-6.0.1` with the mod-config + OpenXR dependency fix. Diff it against our `import/pojlib` to lift P4 (and see how it handles P3/P5).
- **VR mod:** [MrEnglish1996/QuestCraftPlus-VivecraftMod](https://github.com/MrEnglish1996/QuestCraftPlus-VivecraftMod) merges QuestCraft's Android OpenXR provider onto upstream Vivecraft's `openxr` branch (26.2, Vulkan-ready). VisorQuest uses Visor, not Vivecraft, so only the Android provider pattern (`DeviceCompat.Mobile`, `VLoader`/EGL bridge) carries over to the AtumVR port (P1).
- **Wrapper:** unmodified `QCXR-XR-Wrapper` except a debug key and headless build script. Build needs Unity `2022.3.62f3`, NDK `26.1.10909125`, JDK 17+.
- **Licensing:** Pojlib/Vivecraft LGPL-3.0; extracted LWJGL classes BSD-3. Reuse with attribution.
- Not covered: 26.3 (SDL3), Visor/AtumVR.

### Fabric loader
- Today: newest from `meta.fabricmc.net` (0.20.1 on 2026-11-23, meets every minimum below) via a buggy selector that ignores `stable` [read]. meta.fabricmc.net offers every loader for every target, so it sets no minimum itself.
- **Pin per version** in the version table. Minimums from the Visor 0.6.1 jars [read]: **0.14.14** (1.20.1–1.21.7), **0.17.3** (1.21.11), **0.18.4** (26.1.x), **0.19.5** (26.2, 26.3). jringo12 pins 0.19.3 for 26.2, which is **below** Visor's minimum: don't copy that pin.

### Visor / VisorEssentials build
- Upstream publishes 0.6.1 Fabric builds for every target [web]. Shared builds: one **1.20.6** build covers 1.20.5–1.20.6; the **1.21.8** build covers 1.21.6–1.21.8 (so 1.21.7); the **26.1.2** build covers 26.1–26.1.2. VisorEssentials must match Visor's version [web].
- **Modrinth 0.6.1 jars lack Quest/Android code** (no `QuestVLoader`, `XRPlatform`, `AndroidXRBridge`; desktop `lwjgl-openxr` 3.3.6/3.4.x shaded) [read: jar]. They cannot be dropped in.
- **P1 – Quest Visor build per version.** Port coah80's Visor patches (`a0275017`, `25740234`, `32c80265`) onto upstream Visor `dev` (Stonecutter, 0.6.1) and AtumVR patches (`d04c1669`, `1d7dd401`) onto upstream AtumVR 1.14; swap the shaded desktop `lwjgl-openxr` for the Quest jar (3.3.3 family) or the 3.4.x split (26.x). Ideally upstream both to VisorModStudio and Phoenix-Ra. If accepted, P1 shrinks to "build upstream with a Quest flag"; if not, we maintain a Stonecutter fork.
- VisorEssentials has **no Android code** [read]; coah80 shipped the stock 0.4.0 jar, which suggests stock 0.6.1 may work unmodified [untested].
- Upstream keeps `dev` + `mc/*` branches [read: `Visor/.github/branches.json`].

### Pojlib changes (shared catalogue)
- **P0 – Generalise the Visor install (all versions).** Data-driven, VisorQuest-hosted version table + mods manifest (MC id, Java major, loader pin, Visor/VE artifact + SHA-1, Fabric API, perf mods + SHA-1s, LWJGL bundle id). Point `InstanceHandler.MODS/DEV_MODS` and `APIHandler.SUPPORTED_VERSIONS` at JSON in **this** repo, not `QuestCraftPlusPlus/Pojlib@QuestCraft-6.0.0`. `getQCSupportedVersions` and the `installVisor` guard read the table. Drop Vivecraft from `coreMods`; remove Vivecraft remnants; seed Visor config instead. Key bundled perf jars per version (BadOptimizations declares `minecraft: 1.20.1` [read]). Upstream `mods.json` has ready lists for 1.20.4/1.20.6/1.21.1/1.21.4/1.21.5 [read] but none for 1.20.5, 1.21.7, 1.21.11, 26.x.
- **P1** – Quest Visor/AtumVR build per version (above).
- **P2 – Renderer.** Consider rebasing on upstream Pojlib `QuestCraft-6.0.1` (MobileGlues instead of `libltw.so`; what upstream uses through 1.21.11) [web]. Conflicts with coah80's Unity 6 `UnityPlayerActivity` port.
- **P3** – Java 25 runtime (above). **P4** – LWJGL 3.4.x (above).
- **P5 – JVM args from the version JSON.** `generateLaunchArgs` is hand-built [read]. 26.1/26.2 add `--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED`; 26.3 adds `-XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED` [web]. Also evaluate library `rules`.
- **P6** – Vulkan libs (above). **P7** – SDL3 (above).
- **P8 – Unobfuscated 26.x.** Version JSONs have no `client_mappings` [web]. Fabric handles this; check any Pojlib/Visor code or mixins that assume intermediary names (low effort).
- **Storage:** Visor.jar is 4.4 MB [read]; ×16 versions plus mods in the AAR would bloat the APK. Recommend keeping 1.20.1 bundled for offline and downloading others from our release host with SHA-1 verification (the ImmediatelyFast path already does this).

### Launcher (QCXR) changes
- **L0 – Version picker.** Dropdown is fed entirely by `API.getQCSupportedVersions` [read], so no Unity change is needed to add versions once Pojlib reads the table. Rename the build output (`QuestCraft-VISOR-1.20.1.apk`); point `UpdateChecker.cs`/`DevHandler.cs` at VisorQuest releases. Fabric is the only loader offered [read]. Fix the `fixDataPermissions` mismatch (ARCHITECTURE §5.5). Consider a per-version "experimental/untested" badge.
- **L1 – Rebuild `Pojlib-release.aar`** after any Pojlib change (every row), or move to a CI-built artifact.
- **L2 – APK size / first-run download** for a Java 25 JRE and extra LWJGL sets. Prefer download-on-demand.
- **L3 – Graphics API.** Hide/force OpenGL for 26.2+.
- **L4 – XR hand-off re-check** (`gameReady`/`launcherXRStopped`) per version; no code change expected.

## 3. Risk reasoning

- **1.20.1 (low):** de-hardcoding only; risk is regressions from moving to Visor 0.6.1. Needs a from-source rebuild to prove parity with coah80's artifact.
- **1.20.5 / 1.20.6 (med):** runtime and LWJGL already match; risk is the multi-version plumbing and the Visor port. 1.20.6 has upstream Pojlib precedent (though upstream commit `0c6fd773` says "we gotta figure out … 1.20.6" [web]); 1.20.5 has none.
- **1.21.1 (low–med):** Visor targets it; upstream QuestCraft 6.0.0 and 6.0.1 support it on the same runtime; only the Visor Quest port is new.
- **1.21.5 / 1.21.7 (med):** rendering internals churn vs the LightThinWrapper GL layer [untested]; coah80's GL-level perf patches must be re-applied; 1.21.7 has no exact Visor target and no upstream mod list.
- **1.21.11 (med–high):** last Java 21/LWJGL 3.3.3 version; needs loader ≥0.17.3; upstream reached it only on Pojlib 6.0.1 (MobileGlues); no ready mod list.
- **26.x (high):** Java 25 runtime with no upstream Android build, LWJGL 3.4.x natives + OpenXR binding ABI break, JVM flag compatibility, no Pojlib precedent, unobfuscated jars. All 26.1.x share infrastructure, so 26.1 carries most cost. 26.2 adds Vulkan libs; **26.3 is highest risk** (SDL3 replaces GLFW, breaking Pojlib's whole window/input shim).

## 4. Spikes that gate the plan (do first, each is small)

| ID | Question | Status after merge |
|---|---|---|
| **S1** | What differs between `atumvr-*-quest` / `lwjgl-openxr-quest` and upstream? Does upstream Visor 0.6.1 start OpenXR on Quest with upstream jars? | **Partly answered:** upstream 0.6.1 jars have no Android code, so a fork/port is required (P1). Patch set identified (Visor `a0275017`, `32c80265`, `25740234`; AtumVR `d04c1669`, `1d7dd401`). Still open: exact diff vs the prebuilt `-quest` blobs, and provenance of `lwjgl-openxr-quest-3.3.3.jar`. |
| **S2** | What Java is in `JRE.zip`? Do Java 21 / 25 Android runtimes exist? Does `-XX:+ZGenerational` survive on 25? | **Java 21 part answered:** OpenJDK 22.0.1 covers 1.20.5–1.21.11. **Open:** no upstream Java 25 Android JRE (stops at Java 24); ZGenerational on 25 untested. |
| **S3** | Exact LWJGL per target. | **Answered** from Mojang JSONs (table above). |
| **S4** | Rebuild `Pojlib-release.aar` from PR #2 and diff against the committed AAR. | **Open, blocked** by missing Git LFS natives (ARCHITECTURE §5.11). |

## 5. Implementation order

### Current team status (Tucker, 2026-11-24)
- **OpenCode (DeepSeek)** is the active coder. First task: build audit + Visor 0.6.1 Quest port for **1.20.1**, on branch `deepseek/visor-0.6.1-quest` (not pushed yet). This is Phase 0's P1 step below.
- **Codex** rejoins coding on 2026-12-06.
- **Claude** is review/docs only for now (no build or code work).
- No change in direction: the phased order below stands.

### Recommended phases

**Phase 0: foundation (no new Minecraft version).** Push the missing Git LFS natives so Pojlib builds from this repo; fix AtumVR's `../deps/` path. Run S4 (and finish S1/S2). Implement P0 (own manifests, version table, remove the 1.20.1 gate and Vivecraft remnants), pinned loader, per-instance runtime selection, `fixDataPermissions`, L0/L1. Do P1 for **1.20.1 first**: a Quest build of upstream Visor 0.6.1 + AtumVR 1.14 compared against coah80's 0.4.0 build. **Exit criterion:** 1.20.1 behaves the same as today after a from-source AAR rebuild (hardware test). *Why:* every later row depends on the manifest and the Visor/AtumVR port, and 1.20.1 is the only version with a known-good reference.

**Phase 1: 1.20.6, then 1.20.5** (one Visor jar covers both). Smallest real step: JRE 22 and LWJGL 3.3.3 already match, so it tests the multi-version plumbing and the Visor port without runtime changes. (Corrects the handoff: 1.20.5 does not force a runtime change.)

**Phase 2: 1.21.1.** Visor and upstream QuestCraft both support it; establishes the "modern 1.21" mod set and tests download-and-verify for non-bundled Visor.

**Phase 3: 1.21.5 → 1.21.7 → 1.21.11**, in order so each failure is attributable. Decide P2 (rebase on Pojlib 6.0.1/MobileGlues) before 1.21.5; upstream needed 6.0.1 to reach 1.21.11.

**Phase 4: 26.x.** **26.1 / 26.1.1 / 26.1.2** together (one Visor jar; P3, P4, P5 land here), then **26.2** (P6, force OpenGL), then **26.3 last** (P7 SDL3 + LWJGL 3.4.3).

### Per-version hardware checklist
A version stays labelled *untested* until all pass on a Quest: game reaches main menu; world loads in VR; head and controller tracking; VisorEssentials inventory overlay opens and moves items; 10 minutes without crash; no Vivecraft jar present; logs captured.

## 6. Decisions needed from Tucker

1. **Bundle vs download** Visor/VE and mods for non-1.20.1 versions (APK size vs offline use).
2. **Maintained Quest fork vs upstreaming** the Android `XRPlatform`/bridge to VisorModStudio and Phoenix-Ra (S1 shows a port is needed either way).
3. **Where our version table and mods manifest are hosted**, and who owns release pinning.
4. Whether **1.20.7/1.20.8** are dropped, or meant something else (e.g. 1.21.7/1.21.8).
5. Whether a **Java 25 runtime** (and 26.x) lands in the first multi-version release, given no upstream Java 25 Android JRE exists.
6. **Renderer:** stay on `libltw.so` (6.0.0) or rebase on MobileGlues (6.0.1), accepting the Unity 2022.3 vs Unity 6 merge work.

## 7. Other open questions

1. **LFS natives:** can we pull them from coah80/Pojlib or QuestCraftPlusPlus/Pojlib (not verified; cross-repo check refused), and is there enough LFS quota?
2. **Provenance of `lwjgl-openxr-quest-3.3.3.jar`** (still open for ≤1.21.11). For 26.x, take the "13 Android classes + official lwjgl-openxr" split, which QuestCraftPlus+ ships working on 26.2 (§2a).
3. **Renderer capability:** do `libltw.so` / MobileGlues support what Visor's renderer needs (FBO/texture formats) on Quest 2 vs Quest 3?
4. **Vulkan on Quest (26.2+)** and Mojang's plan to make Vulkan default in 26.4: decides how long "force OpenGL" keeps working.
5. **Licensing:** Visor MPL-2.0, VisorEssentials LGPL-3.0-only, `libltw.so` PolyForm Shield; check before any public release.

## Sources
- Mojang version manifest: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json. Per-version JSONs (`javaVersion.majorVersion`, LWJGL libraries, `arguments.jvm`):
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
- Minecraft 26.1 requires Java 25: minecraft.wiki 26.1 Snapshot 1 ("Minimum Java version: Java SE 25"); Fabric docs (cited in Claude's review)
- 26.2 Vulkan (experimental, OpenGL default): https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2, https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-8
- 26.3 SDL3 replaces GLFW: https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-4, https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
- LWJGL 3.4.1 `CallbackI` break + Android OpenXR class split: https://github.com/MisterEnglishDev/QuestCraftPlus/blob/main/docs/PORTING_NOTES.md
- QuestCraftPlus+ overview and repos: https://github.com/MisterEnglishDev/QuestCraftPlus
- Android JRE 25 build scripts: https://github.com/FCL-Team/Android-OpenJDK-Build/tree/Build_JRE_25
- Per-version JRE selection / JRE-25 asset: https://github.com/jringo12/questcraft-26.2-experiment (`Pojlib/src/main/java/pojlib/install/Installer.java`)
