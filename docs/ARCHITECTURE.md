# VisorQuest Architecture

> **Status: UNTESTED. Documentation only. Nothing here has been built or run on a headset by this team.**
> Merged 2026-11-24 from Hark's research pass (2026-11-23, upstream/metadata comparison) and Claude's static review (2026-11-23, read of `main`, draft PRs #1–#3 and the committed `Pojlib-release.aar`). Per team rules, nothing in this document claims a feature works.
>
> Evidence tags: **[read]** seen directly in this repo's source/metadata or a jar; **[web]** read from a public page or API (URL in *Sources*); **[inferred]** deduced from what was read; **[untested]** no hardware test exists.
>
> Branch/PR mapping: PR #1 = `import/visor` → `Visor/`, PR #2 = `import/pojlib` → `Pojlib/`, PR #3 = `import/atumvr` → `AtumVR/`. Commit SHAs below are coah80's originals (preserved by the subtree imports), so `git show <sha>` works after fetching the import branches.

## 1. The stack at a glance

```
Meta Quest (Android, arm64-v8a)
└─ QCXR launcher APK (Unity 6000.4.10f1, OpenXR)            main: Assets/Scripts/*.cs
   │  AndroidJavaClass("pojlib.API")  (JNI)
   └─ Pojlib-release.aar (prebuilt, committed binary)        main: Assets/Plugins/Android/
      │  source: PR #2  Pojlib/  (fork of QuestCraftPlusPlus/Pojlib QuestCraft-6.0.0)
      ├─ downloads: JRE.zip (OpenJDK 22.0.1), Mojang client/libs/assets,
      │             Fabric loader profile, mods (live upstream mods.json)
      ├─ bundles:   LWJGL 3.3.3 fat jar + GLFW stub, native .so set (libltw.so GL→GLES),
      │             Visor.jar, VisorEssentials.jar, perf mods
      ├─ libvloader.so: EGL display/context/config + Dalvik VM/activity → Java
      └─ JNI → libpojavexec / libjrelauncher → in-process JVM
         └─ Minecraft 1.20.1 (Fabric) on JRE
            ├─ Visor.jar 0.4.0+questcraft (VR core, OpenXR)      PR #1  Visor/
            │   └─ shades AtumVR ("-quest" builds) + lwjgl-openxr-quest-3.3.3.jar
            │      → libopenxr_loader.so → Meta OpenXR runtime
            ├─ VisorEssentials.jar 0.4.0 (stock addon, not forked)
            └─ perf mods (MoreCulling, Noisium, BadOptimizations, Chunky, C2ME, ImmediatelyFast)
```

**Headset hand-off [read]:** the Unity launcher owns the OpenXR session while its menu is shown. When the VR mod is ready it flips `API.gameReady`; the launcher calls `StopSubsystems` + `DeinitializeLoader` and sets `API.launcherXRStopped`; `vloader.cpp` waits (up to ~10 s) for that flag so Visor can create its own OpenXR session. (coah80: `InstanceButton.cs` in `e24b36b3`; `API.java` + `vloader.cpp` in `f241fb2c`.)

## 2. Components

| Component | Where | Role |
|---|---|---|
| **QCXR launcher** | `main` (`Assets/`, Unity 6000.4.10f1 [read]; upstream wrapper is on 2022.3.62f2 [web]) | UI: login, instance list, version/loader pickers, mod list. Holds OpenXR session for the launcher UI only. Calls Pojlib via JNI. Packages: `com.unity.xr.openxr` 1.16.1, `xr.hands` 1.7.3 [read]. |
| **Pojlib** | PR #2 → `Pojlib/` (Android library, Java + NDK) | Everything below the UI: instance model, installs, mod sync, JRE bootstrap, JVM launch, input bridges. Single entry class `pojlib.API`. Forked from upstream `QuestCraft-6.0.0` at `1cdc8231` (2026-02-07) [read]. |
| **Prebuilt AAR** | `main` → `Assets/Plugins/Android/Pojlib-release.aar` (13.7 MB, `33959a07`) | What the launcher *actually* consumes. Contains classes, `assets/mods/*`, `assets/lwjgl/*`, and the native `.so` set. Any Pojlib change means rebuilding and replacing this file. |
| **JRE** | downloaded at runtime | `QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE.zip` → `<filesDir>/runtimes/JRE`. Today resolves to release `jre22-6.0.0` = **OpenJDK 22.0.1** aarch64 [web]. Unpinned (`latest`); validated by file presence only [read]. |
| **LWJGL bundle** | `Pojlib/jre_lwjgl3glfw` → `lwjgl-glfw-classes.jar` | Pojav-style fat jar of LWJGL core, GLFW (Java stub: `GLFW.java`, `CallbackBridge.java`), OpenGL, OpenAL, STB, etc., built on a Java 8 toolchain. Manifest `Specification-Version: 3.3.3-snapshot` [read]. Mojang's own `lwjgl*` libraries are deliberately skipped by the installer. |
| **Natives** | `Pojlib/src/main/jniLibs/arm64-v8a/*.so`, `src/main/jni/libopenxr_loader.so` | `liblwjgl*.so`, `libltw.so` (LightThinWrapper GL→GLES), `libopenal.so`, … **Git LFS pointers whose objects are missing** (§5, item 11). |
| **Visor** | PR #1 → `Visor/` | Architectury multi-loader VR mod (`visor-api`, `visor-core`, `visor-fabric`, `visor-forge`), fork of VisorModStudio/Visor `dev` as of 2026-07-12. Bundled build is `0.4.0+questcraft` for MC 1.20.1 [read]. |
| **VisorEssentials** | bundled jar only (no source in the PRs) | Visor addon; stock `0.4.0`, no Android code [read]. |
| **AtumVR** | PR #3 → `AtumVR/` | OpenXR-over-LWJGL session/swapchain/input layer used by Visor. Source in PR #3 is `1.13.0beta` + coah80 Android patches [read]; Visor builds against prebuilt `*-quest` jars in `Visor/libs/` instead [read]. |

### 2.1 Key files: launcher (`main`)
Base: fork of [QuestCraftPlusPlus/QCXR-XR-Wrapper](https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper) `master`. coah80 commits: `e24b36b3` "make visor build work", `8226cad9` "update visor build", `33959a07` "release QuestCraft VISOR 1.1.0", `c9d110ba` (EULA fix).

| File | Role |
|---|---|
| `Assets/Scripts/JNIStorage.cs` | Boots the Java side; `API.getQCSupportedVersions` → `FillSupportedVersions` → `uiHandler.UpdateDropdowns`. coah80 removed adding existing instance names to the list (`e24b36b3`). `FixFilePermissions` calls `API.fixDataPermissions`, which does not exist (§5, item 5). |
| `Assets/Scripts/InstanceManager.cs` | "New instance" UI; mod loaders cut to **Fabric only** (`e24b36b3`). Calls `API.createNewInstance(...)`. |
| `Assets/Scripts/InstanceButton.cs` | Play: `createNewInstance` (default), `prelaunch` (now returns bool), `launchInstance`; XR hand-off. Natives/saves paths use the app's package name. |
| `Assets/Scripts/ModManager.cs` | Modrinth browser; filters by the instance's game version (already version-generic). |
| `UpdateChecker.cs`, `DevHandler.cs`, `LogUploader.cs` | Point at `coah80/questcraft-visor-releases`; "QuestCraft VISOR" branding (`33959a07`). |
| `Assets/Scripts/QuestHandTrackingProbe.cs` | New (coah80): logs XR Hands subsystem state. |
| `Assets/Editor/QuestCraftVisorBuild.cs` | New (coah80): headless build → `outputs/QuestCraft-VISOR-1.20.1.apk`, app id `com.qcxr.qcxr.visor`, PICO off, hand tracking + Meta/Oculus controller profiles. |
| `Assets/Plugins/Android/AndroidManifest.xml`, `launcherTemplate.gradle`, `mainTemplate.gradle` | Hand-tracking manifest entries; `pickFirst lib/arm64-v8a/libopenxr_loader.so`. |

### 2.2 Key files: Pojlib (PR #2)
coah80 commits: `f241fb2c` swap vivecraft for visor, `0dfa91d5` add visor essentials, `e4623648` fix jre setup, `2b211783` bundle optimized QuestCraft runtime.

| File | Role |
|---|---|
| `pojlib/API.java` | JNI API. `getQCSupportedVersions` hard-coded `{"1.20.1"}` (`f241fb2c`). `prelaunch` → `updateMods` → `Installer.installVisor` → `JREUtils.prelaunchCheck`. |
| `pojlib/InstanceHandler.java` | Creates instances (Fabric loader via `FabricMeta.getLatestVersion()`, Mojang JSON, client/libs/assets, classpath). `MODS`/`DEV_MODS` = upstream raw URLs. `isCoreMod` adds `Visor`/`VisorEssentials`. |
| `pojlib/install/Installer.java` | `installJVM` (single JRE), `installClient`, `installLibraries` (skips Mojang `lwjgl*`, appends `lwjgl3/lwjgl-glfw-classes.jar` + `hacks/ResConfHack.jar`), `installAssets`, `moveLocalAssets`, coah80's `installVisor` / `applyQuestDefaults` / `installImmediatelyFast`. |
| `pojlib/install/MinecraftMeta.java` | Reads piston-meta manifest + per-version JSON. Does **not** read `javaVersion` [read]. |
| `pojlib/install/FabricMeta.java` | `meta.fabricmc.net/v2/versions/loader`; `getLatestVersion()` (see §5, item 8). |
| `pojlib/util/json/MinecraftInstances.java` | `Instance.updateMods` (live `mods.json`), `generateLaunchArgs` built **by hand**; `CheckVivecraftConfig` (now uncalled). |
| `pojlib/util/JREUtils.java` | JVM env + args: ZGC, `-Xmx` (coah80: 1792 MB Quest 2 / 2560 MB otherwise, `2b211783`), `-Dorg.lwjgl.opengl.libname=libltw.so`, GLES/EGL paths, glfwstub window size. `prelaunchCheck`. |
| `pojlib/UnityPlayerActivity.java` | Ported to Unity 6 `UnityPlayerForActivityOrService` (`2b211783`). `installLWJGL`. |
| `src/main/jni/vloader.cpp` | VR JNI bridge. Upstream exports `Java_org_vivecraft_util_VLoader_*`; coah80 added `Java_org_vmstudio_visor_core_client_provider_openxr_QuestVLoader_*` wrappers (`f241fb2c`) and `getDeviceModel` (`2b211783`). |
| `src/main/jni/Android.mk` | Builds `pojavexec`, `vloader` (links `openxr_loader`), `jrelauncher`, AWT shims. |
| `src/main/assets/mods/*.jar` | Bundled 1.20.1 jars: Visor, VisorEssentials, MoreCulling, Noisium, BadOptimizations, Chunky, C2ME. |
| `mods.json`, `devmods.json`, `supportedVersions.json` | Per-version manifests; still list **Vivecraft 1.2.5** as core mod. Not what the app reads (§5, item 1). |
| `pojlib/util/Constants.java` | `GIT_BRANCH = "QuestCraft-6.0.0"`. |
| `.gitmodules` | References `wrapper` → `QuestCraftPlusPlus/Wrapper-IL2CPP` (not imported). |

### 2.3 Key files: Visor (PR #1)
Base: VisorModStudio/Visor `dev` at 0.4.0, single-target 1.20.1 (`libs.versions.toml`: `minecraftVersion = 1.20.1`, `fabric_api 0.92.3`, `fabric-loader 0.16.5`, `lwjgl 3.3.6`, parchment `2023.09.03`, `options.release = 17`, Architectury Loom) [read]. coah80 commits: `a0275017` quest android support, `25740234` make essentials work, `32c80265` optimize Quest rendering and world loading.

| File | Role |
|---|---|
| `visor-core/.../provider/openxr/QuestVLoader.java` | New: `native` methods bound to `libvloader.so` (EGL/Dalvik handles, `setupAndroid`, `getDeviceModel`, `isQuest2()/isQuest3Family()`). |
| `.../provider/openxr/QuestAndroidXRBridge.java` | New: implements AtumVR's `AndroidXRBridge` via `QuestVLoader`. |
| `.../provider/openxr/XrProvider.java` | Calls `XRPlatform.setAndroidBridge(new QuestAndroidXRBridge())` before session creation. |
| `.../render/XrRenderer.java`, `XrRenderTarget.java`, `VRPoseImpl.java`, overlays | Quest perf/render tweaks (`32c80265`). |
| `visor-fabric/.../WorldPreloader*.java`, `mixin/CreateWorldScreenMixin.java` | New world pre-generation screen (uses bundled Chunky; `32c80265`). |
| `visor-api/.../player/pose/PlayerPoseType.java` | Small API change so VisorEssentials works (`25740234`). |
| `libs/atumvr-api-quest.jar`, `libs/atumvr-core-quest.jar` | Prebuilt AtumVR with Android patches (replace `atumvr-*-1.14.0beta-snapshot#8`). |
| `libs/lwjgl-openxr-quest-3.3.3.jar` | Android-capable lwjgl-openxr (GLES + Android loader structs), shaded into the Fabric jar instead of upstream `lwjgl-openxr:3.3.6` + desktop natives (`visor-fabric/build.gradle`). |
| `version.properties` | `0.4.0` + stage `+questcraft`. Upstream cuts per-MC branches (`.github/branches.json`: `dev`, `mc/*`) [read]. |

Upstream has since moved on [web]: Visor `dev` is a **Stonecutter** multi-version build (`stonecutter.properties.toml` targets 1.20.1 … 26.3, Fabric/Forge/NeoForge); v0.6.1 released 2026-10-31.

### 2.4 Key files: AtumVR (PR #3)
Base: [Phoenix-Ra/AtumVR](https://github.com/Phoenix-Ra/AtumVR) `master` at 1.13.0beta. coah80 commits: `d04c1669` android openxr stuff, `1d7dd401` optimize Quest OpenXR runtime.

| File | Role |
|---|---|
| `atumvr-core/.../session/platform/XRPlatform.java` | New platform abstraction: `Desktop` (WGL/GLX) vs `Android` (`KHRLoaderInitAndroid`, `XrInstanceCreateInfoAndroidKHR`, `XrGraphicsBindingOpenGLESAndroidKHR`, GLES swapchain images). Android chosen when `os.version` contains "Android" (Pojlib sets `-Dos.version=Android-…`). |
| `.../session/platform/AndroidXRBridge.java` | New interface Visor implements. |
| `XRInstance`, `XRSystem`, `XRSwapChain`, `XRSession`, `XRState` | Route creation through `XRPlatform`; Quest perf work (`1d7dd401`). |
| `atumvr-api/build.gradle` | `lwjgl-openxr` replaced by `files("${rootDir}/../deps/lwjgl-openxr-quest-3.3.3.jar")`: **that `deps/` folder is not in any imported repo** (the jar exists only at `Visor/libs/`). |

Upstream AtumVR is now 1.14.0beta snapshots with separate `session/opengl` and `session/vulkan` packages, Linux arm64 + Steam Frame support, and **no Android platform** [web].

## 3. Launch flow (as written, 1.20.1 only)

1. **Unity `JNIStorage.Start`** loads `pojlib.API.loadAll()` and calls `API.getQCSupportedVersions` to fill the version dropdown. It returns the hardcoded `{"1.20.1"}` [read]. This is the only source for the launcher's version list.
2. **Create instance** (`InstanceManager.CreateCustomInstance` → `API.createNewInstance` → `InstanceHandler.create`):
   - Looks up the **latest** Fabric loader from `meta.fabricmc.net` (0.20.1 on 2026-11-23 [web]) and fetches its profile JSON for the chosen MC version (`FabricMeta`).
   - Fetches Mojang version info; installs client jar, libraries (skipping `lwjgl*`), assets with SHA-1 checks; appends `lwjgl-glfw-classes.jar` and `ResConfHack.jar` to the classpath.
   - Writes default configs from AAR assets (Sodium, MoreCulling, etc.).
3. **Prelaunch** (`API.prelaunch`): `instance.updateMods()` (live upstream `mods.json`) → `Installer.installVisor()` → `JREUtils.prelaunchCheck()` (LWJGL, JRE, client, libs, assets).
4. **Launch** (`InstanceHandler.launchInstance` → `JREUtils.launchJavaVM`): sets env (`POJLIB_RENDERER=LightThinWrapper`, `LD_LIBRARY_PATH`…), `dlopen`s the JRE, builds JVM args, calls `VMLauncher.launchJVM`. Game args are fixed (`--username … --versionType release`); the **version JSON's `arguments.jvm` is not consumed** and Mojang's `javaVersion` is ignored [read].
5. Inside the JVM, Fabric loads `Visor.jar`, which brings up OpenXR through AtumVR (`XRPlatform.Android`) using `libopenxr_loader.so` shipped in the AAR, after the headset hand-off in §1.

Consequence: the version picker, mod manifest, Visor install gate, Visor build, AtumVR build, JRE and LWJGL are each pinned in a different place. Multi-version support has to touch all of them (see `docs/VERSION_MATRIX.md`).

## 4. Where Vivecraft was swapped for Visor

Upstream QuestCraft uses a Quest fork of Vivecraft ([QuestCraftPlusPlus/VivecraftMod](https://github.com/QuestCraftPlusPlus/VivecraftMod), branches `OpenXR-<mc>`), downloaded per version as a `coreMods` entry in `mods.json` (e.g. `vivecraft-1.20.1-1.2.5-fabric.jar`). Its OpenXR provider calls `org.vivecraft.util.VLoader` natives in `vloader.cpp` [web].

| # | Layer / location | Upstream (Vivecraft) | coah80 (Visor) | Commit |
|---|---|---|---|---|
| 1 | Manifests: `Pojlib/mods.json`, `devmods.json` (and the **remote** copy, §5.1) | `coreMods: Vivecraft` for every version | Unchanged: still lists Vivecraft, so it is **downloaded**, then removed in step 3 | — |
| 2 | `MinecraftInstances.updateMods` | Downloads manifest each prelaunch | Unchanged | — |
| 3 | `Installer.installVisor` (from `API.prelaunch`) — **the swap** | — | Throws unless `versionName == "1.20.1"`. Deletes any jar matching `vivecraft`, old Visor jars, and perf mods; strips `Vivecraft/Visor/VisorEssentials/MoreCulling/Noisium/BadOptimizations/Chunky/C2ME/Voxy` from `extProjects`; writes `Visor.jar`, `VisorEssentials.jar`, MoreCulling, Noisium, BadOptimizations, Chunky, C2ME from AAR `assets/mods/`; downloads ImmediatelyFast by pinned URL + SHA-1; re-adds them with `bundled://…` pseudo-links | `f241fb2c`, `0dfa91d5`, `2b211783` |
| 4 | `API.prelaunch` | → `CheckVivecraftConfig(instance)` | Call removed; `installVisor` + save `instances.json`; returns `boolean` | `f241fb2c` |
| 5 | Supported versions | `getQCSupportedVersions` downloads upstream `supportedVersions.json` | `return new String[] {"1.20.1"}` | `f241fb2c` |
| 6 | `InstanceHandler.isCoreMod` | slugs from `mods.json` | plus hard-coded `Visor`, `VisorEssentials` (cannot be removed in the mod UI) | `f241fb2c`, `0dfa91d5` |
| 7 | `Installer.applyQuestDefaults` | — | Render/simulation distance 4, once per instance (`.qcx-render-distance-4` marker) | `2b211783` |
| 8 | Native bridge (`vloader.cpp`) | `Java_org_vivecraft_util_VLoader_*` | Re-exported as `…QuestVLoader_*` (+ `getDeviceModel`); `setupAndroid` waits for `launcherXRStopped` | `f241fb2c`, `2b211783` |
| 9 | OpenXR platform code | Inside Vivecraft (`DeviceCompat.Mobile`) | AtumVR `XRPlatform.Android` + Visor `QuestAndroidXRBridge` | AtumVR `d04c1669`; Visor `a0275017` |
| 10 | OpenXR Java bindings | QuestCraftPlusPlus lwjgl-openxr fork | `lwjgl-openxr-quest-3.3.3.jar` shaded into Visor | Visor `a0275017` |
| 11 | Launcher XR hand-off | `DeinitializeLoader` after 200 ms | `StopSubsystems` + `DeinitializeLoader` + `launcherXRStopped` | wrapper `e24b36b3` |

Vivecraft **remnants** [read]: `Installer.moveLocalAssets` still writes `config/vivecraft-client-config.json`; `MinecraftInstances.CheckVivecraftConfig` exists with no caller; this fork's `mods.json` still lists Vivecraft. Visor is configured via `config/Visor/settings.yml` (per upstream), which Pojlib does not seed.

## 5. Findings and discrepancies to resolve

1. **Remote mods manifest.** `InstanceHandler.MODS/DEV_MODS` point at `raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/refs/heads/QuestCraft-6.0.0/…` [read]: the *upstream* repo. Local edits to `mods.json` have no runtime effect until that URL (or `Constants.GIT_BRANCH`) changes. Upstream last edited it 2026-11-16 ("Update Fabric API for 1.21.1") [web], so upstream changes reach VisorQuest users with no release of ours.
2. **AAR vs source.** The AAR contains `installVisor` and the `1.20.1` guard string [read], consistent with PR #2, but byte-for-byte provenance is unknown. Rebuild the AAR from PR #2 before trusting any source change (spike S4).
3. **Visor/AtumVR provenance gap.** Visor bundles `atumvr-*-quest.jar` and `lwjgl-openxr-quest-3.3.3.jar` as binary blobs [read]. PR #3's AtumVR source is `1.13.0beta`, while Visor's committed blobs replace `1.14.0beta-snapshot#8`. The Quest patch set itself *is* now identified: AtumVR `d04c1669`, `1d7dd401` and Visor `a0275017`, `25740234`, `32c80265` [read]. Whether the `-quest` blobs were built from exactly PR #3's source is still unverified.
4. **LWJGL mismatch inside Visor.** Visor compiles against LWJGL `3.3.6`, bundles OpenXR `3.3.3`, and Pojlib's core LWJGL reports `3.3.3-snapshot` [read].
5. **Launcher calls a method Pojlib doesn't have.** `JNIStorage.FixFilePermissions` calls `API.fixDataPermissions`; absent from PR #2 source and the AAR [read].
6. **ImmediatelyFast** is pinned to the `1.5.5+1.20.4` build + SHA-1 and installed into 1.20.1 instances [read]; compatibility with 1.20.1 is [untested].
7. **JRE version — resolved.** Claude's review could not read the JRE metadata (rate limit) and inferred JDK 21+ from `-XX:+UseZGC -XX:+ZGenerational`. Hark's pass read the release: `jre22-6.0.0` `release` asset says `JAVA_VERSION="22.0.1"` [web]. So the runtime already meets Java 21 (1.20.5–1.21.11); it does **not** meet Java 25 (26.x). It is still fetched via `releases/latest` and must be pinned.
8. **Fabric loader selection** (`FabricMeta.getLatestVersion`) compares `minor` against `newVer.major` (apparent typo) and ignores the `stable` flag [read]. Works only because the meta API lists newest first [inferred]. Pin per version (minimums in VERSION_MATRIX.md).
9. **Upstream manifest typo:** `mods.json` 1.20.6 labels Fabric API `0.108.8` but its URL is `0.100.8` [read].
10. **No `rules` evaluation** for vanilla libraries and no use of version-JSON JVM args [read]; required for 26.x (VERSION_MATRIX.md P5/P6).
11. **Build blocker: Git LFS objects missing.** `Pojlib/src/main/jniLibs/arm64-v8a/*.so` and `Pojlib/src/main/jni/libopenxr_loader.so` are LFS pointers; the LFS batch API for `twanye/VisorQuest` returns 404 "Object does not exist on the server" (checked `liblwjgl.so`, oid `7cd38e15…`) [web]. Pojlib cannot be built from this repo until they are pushed (candidate sources coah80/Pojlib or QuestCraftPlusPlus/Pojlib; not verified accessible).
12. **Build blocker: AtumVR `../deps/lwjgl-openxr-quest-3.3.3.jar`** is referenced but absent; only `Visor/libs/` has a copy. Provenance undocumented (likely the QuestCraftPlusPlus lwjgl3 Android fork) [inferred].
13. **Visor consumes prebuilt AtumVR jars** (`Visor/libs/atumvr-*-quest.jar`), not the `AtumVR/` source; rebuilding AtumVR does not update Visor automatically [read].
14. **Upstream Modrinth Visor 0.6.1 jars lack the Quest/Android code** (no `QuestVLoader`, `XRPlatform`, `AndroidXRBridge`; they shade desktop `lwjgl-openxr` 3.3.6/3.4.x) [read: jar contents]. They cannot be dropped in as-is; a Quest build per version is needed. VisorEssentials has no Android code, so the stock jar *may* work unmodified [untested].

## 6. Upstream comparison notes [web]

- **Pojlib `QuestCraft-6.0.1`** (58 commits ahead / 12 behind `QuestCraft-6.0.0`): supports up to **1.21.11** with Vivecraft 1.3.x and replaces `libltw.so` with **MobileGlues** (`libmobileglues.so`), plus JREUtils/egl_bridge changes. coah80's fork is on 6.0.0 and has none of this. Upstream 6.0.1 targets Unity 2022.3, which conflicts with coah80's Unity 6 activity port.
- **Pojlib `QuestCraft-6.0.0`** (what the app downloads JSON from) lists 1.19.2–1.21.5.
- **Community 26.x ports**, both Vivecraft-based, neither touches Visor: [jringo12/questcraft-26.2-experiment](https://github.com/jringo12/questcraft-26.2-experiment) (MC 26.2; per-version JRE via `javaVersion.majorVersion`, bundled `JRE-25.zip` of unknown provenance, Vulkan-capable path, Fabric loader pinned 0.19.3) and [MisterEnglishDev/QuestCraftPlus](https://github.com/MisterEnglishDev/QuestCraftPlus) (MC 26.2; documents the LWJGL 3.4.1 `CallbackI` ABI break and the "13 Android-only OpenXR classes" workaround in `docs/PORTING_NOTES.md`).

## 7. Version-coupled hardcodes (all must become data)

- `API.getQCSupportedVersions` → `{"1.20.1"}`
- `Installer.installVisor` guard and the pinned bundled-mod versions (`MoreCulling 1.20.1-0.24.5`, `Noisium 2.3.0+mc1.20-1.20.1`, `BadOptimizations 2.4.1` (strictly `minecraft: 1.20.1`), `Chunky`, `C2ME`, ImmediatelyFast `1.5.5+1.20.4`)
- `Visor 0.4.0+questcraft` / `VisorEssentials 0.4.0` literals in `installVisor`
- Single `runtimes/JRE` directory fetched from `releases/latest` (no per-Java-major runtime, no pin)
- Single LWJGL bundle (3.3.3-snapshot) and native set
- Remote `mods.json` / `supportedVersions.json` URL via `GIT_BRANCH`
- Hand-built launch args (`generateLaunchArgs`)
- Visor `libs.versions.toml` (1.20.1, fabric_api 0.92.3, loader 0.16.5, parchment, release 17)
- Launcher build output name `QuestCraft-VISOR-1.20.1.apk`; update/log URLs pointing at `coah80/questcraft-visor-releases`

## 8. Licensing

Visor is MPL-2.0 (older releases LGPL-3.0); VisorEssentials is LGPL-3.0-only (Modrinth) [web]. `libltw.so` (LightThinWrapper) is PolyForm Shield per Pojlib's README [web]. Keep headers and licence files when bundling or redistributing jars. Confirm Pojlib/QCXR licences before publishing any per-version builds. Not legally reviewed.

## Sources
- This repo: `main`, PR #1/#2/#3 (`import/visor`, `import/pojlib`, `import/atumvr`), `Assets/Plugins/Android/Pojlib-release.aar` (files and SHAs above).
- https://github.com/QuestCraftPlusPlus/Pojlib (branches `QuestCraft-6.0.0`, `QuestCraft-6.0.1`; compare `QuestCraft-6.0.0...QuestCraft-6.0.1`)
- https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper, https://github.com/QuestCraftPlusPlus/QuestCraft, https://github.com/QuestCraftPlusPlus/VivecraftMod
- https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/tag/jre22-6.0.0 (`release` asset: `JAVA_VERSION="22.0.1"`)
- https://github.com/VisorModStudio/Visor (`stonecutter.properties.toml`), https://github.com/Phoenix-Ra/AtumVR
- https://github.com/jringo12/questcraft-26.2-experiment, https://github.com/MisterEnglishDev/QuestCraftPlus
