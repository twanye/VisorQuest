# VisorQuest — Architecture

Status: **documentation only, nothing built or tested.** Written 2026-11-23 from a read of `main` and the three import branches (`import/visor`, `import/pojlib`, `import/atumvr`), compared against the upstream repos. Commit SHAs are coah80's originals (preserved by the subtree imports), so `git show <sha>` works in this repo after fetching the import branches.

---

## 1. The stack at a glance

```
Meta Quest (Android, arm64)
└─ QCXR launcher APK  (Unity 6000.4.10f1, OpenXR)            → main: Assets/
   │  C# UI  ──JNI──►  Pojlib-release.aar                    → main: Assets/Plugins/Android/Pojlib-release.aar
   │
   └─ Pojlib (Android library, Java + NDK)                   → import/pojlib: Pojlib/
      ├─ installs: JRE (Android OpenJDK 22), Minecraft client/libs/assets (Mojang),
      │            Fabric loader (meta.fabricmc.net), mods (mods.json + bundled jars)
      ├─ boots an in-process JVM (jre_launcher.c / JREUtils) with:
      │     • Pojlib's own LWJGL 3.3.3 build + GLFW stub (lwjgl-glfw-classes.jar)
      │     • GL→GLES translation: libltw.so ("LightThinWrapper")
      └─ libvloader.so: hands EGL display/context/config + Dalvik VM/activity to Java
         │
         └─ Minecraft 1.20.1 (Fabric) + mods
            └─ Visor 0.4.0+questcraft (VR core mod)               → import/visor: Visor/
               └─ AtumVR (OpenXR session/swapchain/input library) → import/atumvr: AtumVR/
                  └─ lwjgl-openxr-quest-3.3.3.jar → libopenxr_loader.so → Meta OpenXR runtime
               + VisorEssentials 0.4.0 (stock addon, not forked)
```

Handoff of the headset: the Unity launcher owns the OpenXR session while the menu is shown. When Minecraft's VR mod is ready, it flips `API.gameReady`; the launcher stops and deinitialises its own XR loader and sets `API.launcherXRStopped`; `vloader.cpp` waits (up to ~10 s) for that flag so Visor can create its own OpenXR session. (coah80 added this handshake: `InstanceButton.cs` in `e24b36b3`, `API.java` + `vloader.cpp` in `f241fb2c`.)

---

## 2. Components and key files

### 2.1 QCXR launcher (Unity) — `main`
Base: fork of [QuestCraftPlusPlus/QCXR-XR-Wrapper](https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper) `master`. coah80 commits on top: `e24b36b3` "make visor build work", `8226cad9` "update visor build", `33959a07` "release QuestCraft VISOR 1.1.0", `c9d110ba` (EULA fix).

| File | Role |
|---|---|
| `Assets/Scripts/JNIStorage.cs` | Boots the Java side; calls `API.getQCSupportedVersions` and fills the version dropdown (`FillSupportedVersions`, `uiHandler.UpdateDropdowns`). coah80 removed the code that also added existing instance names to the list (`e24b36b3`). |
| `Assets/Scripts/InstanceManager.cs` | "New instance" UI. coah80 cut mod loaders down to **Fabric only** (`e24b36b3`). Calls `API.createNewInstance(...)`. |
| `Assets/Scripts/InstanceButton.cs` | Play button: `API.createNewInstance` (default instance), `API.prelaunch` (now returns bool), `API.launchInstance`; XR hand-off (`StopSubsystems`, `DeinitializeLoader`, `launcherXRStopped`). Natives/saves paths now use the app's package name instead of hard-coded `com.qcxr.qcxr`. |
| `Assets/Scripts/ModManager.cs` | Modrinth browser; filters by the instance's game version (already version-generic). |
| `Assets/Scripts/UpdateChecker.cs`, `DevHandler.cs`, `LogUploader.cs` | Point at `coah80/questcraft-visor-releases`; "QuestCraft VISOR" branding (`33959a07`). |
| `Assets/Scripts/QuestHandTrackingProbe.cs` | New (coah80): logs XR Hands subsystem state. |
| `Assets/Editor/QuestCraftVisorBuild.cs` | New (coah80): headless build → `outputs/QuestCraft-VISOR-1.20.1.apk`, app id `com.qcxr.qcxr.visor`, disables PICO features, enables hand tracking + Meta/Oculus controller profiles. |
| `Assets/Plugins/Android/Pojlib-release.aar` | The prebuilt Pojlib (13.7 MB in `33959a07`). Any Pojlib change means rebuilding and replacing this file. |
| `Assets/Plugins/Android/AndroidManifest.xml`, `launcherTemplate.gradle`, `mainTemplate.gradle` | Hand-tracking manifest entries; `pickFirst lib/arm64-v8a/libopenxr_loader.so`. |
| `ProjectSettings/ProjectVersion.txt` | **Unity 6000.4.10f1** (upstream wrapper is on 2022.3.62f2, per its commit `99bf6da7`). Packages: `com.unity.xr.openxr` 1.16.1, `xr.hands` 1.7.3. |

### 2.2 Pojlib — `import/pojlib` → `Pojlib/`
Base: fork of [QuestCraftPlusPlus/Pojlib](https://github.com/QuestCraftPlusPlus/Pojlib) branch `QuestCraft-6.0.0`, forked at `1cdc8231` (2026-02-07, "Revert Vivecraft"). coah80 commits: `f241fb2c` swap vivecraft for visor, `0dfa91d5` add visor essentials, `e4623648` fix jre setup, `2b211783` bundle optimized QuestCraft runtime.

| File | Role |
|---|---|
| `src/main/java/pojlib/API.java` | Public API called from Unity over JNI. **`getQCSupportedVersions` is hard-coded to `{"1.20.1"}`** (`f241fb2c`). `prelaunch` → `instance.updateMods` → `Installer.installVisor` → `JREUtils.prelaunchCheck`. |
| `src/main/java/pojlib/InstanceHandler.java` | Creates instances: picks Fabric loader via `FabricMeta.getLatestVersion()`, fetches Mojang version JSON, installs client/libs/assets, classpath. `MODS`/`DEV_MODS` URLs = `raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/refs/heads/QuestCraft-6.0.0/{mods,devmods}.json` (**upstream, fetched live**). `isCoreMod` treats `Visor`/`VisorEssentials` as core mods. |
| `src/main/java/pojlib/install/Installer.java` | `installJVM` (single JRE at `files/runtimes/JRE`), `installClient`, `installLibraries` (**skips every Mojang `lwjgl` library**, then appends Pojlib's `lwjgl3/lwjgl-glfw-classes.jar` and `hacks/ResConfHack.jar`), `installAssets`, `moveLocalAssets` (configs), and coah80's `installVisor` / `applyQuestDefaults` / `installImmediatelyFast`. |
| `src/main/java/pojlib/install/MinecraftMeta.java` | Reads `piston-meta.mojang.com/mc/game/version_manifest_v2.json` and the per-version JSON. Does **not** read `javaVersion`. |
| `src/main/java/pojlib/install/FabricMeta.java` | `meta.fabricmc.net/v2/versions/loader`; `getLatestVersion()` (effectively the first = newest entry; its comparison logic has a bug — compares `minor` against `major`). |
| `src/main/java/pojlib/util/json/MinecraftInstances.java` | `Instance.updateMods` downloads the live `mods.json`, merges `coreMods` (+ `defaultMods`) for the instance's version, downloads jars. `generateLaunchArgs` builds game args **by hand** (ignores the version JSON's `arguments.jvm`). |
| `src/main/java/pojlib/util/JREUtils.java` | JVM env + args: ZGC, `-Xmx` (coah80: cap 1792 MB on Quest 2 / 2560 MB otherwise, `2b211783`), `-Dorg.lwjgl.opengl.libname=libltw.so`, GLES/EGL lib paths, glfwstub window size. `prelaunchCheck` installs LWJGL, JRE, launcher assets, client, libs, assets. |
| `src/main/java/pojlib/UnityPlayerActivity.java` | Android activity; coah80 ported it to Unity 6's `UnityPlayerForActivityOrService` (`2b211783`). `installLWJGL` copies `assets/lwjgl/lwjgl-glfw-classes.jar`. |
| `jre_lwjgl3glfw/` | Pojav-style LWJGL: `libs/lwjgl*.jar` (**LWJGL 3.3.3-snapshot**, manifest `Specification-Version: 3.3.3-snapshot`) and a Java GLFW stub (`org/lwjgl/glfw/GLFW.java`, `CallbackBridge.java`) that routes input/window calls to Android. Built into `src/main/assets/lwjgl/lwjgl-glfw-classes.jar`. |
| `src/main/jniLibs/arm64-v8a/*.so` | Prebuilt natives (`liblwjgl*.so`, `libltw.so`, `libopenal.so`, …). **Git LFS pointers** — see §6. |
| `src/main/jni/vloader.cpp` | JNI bridge for the VR mod: EGL display/context/config, Dalvik VM/activity, `setupAndroid`. Upstream exports only `Java_org_vivecraft_util_VLoader_*`; coah80 added `Java_org_vmstudio_visor_core_client_provider_openxr_QuestVLoader_*` wrappers (`f241fb2c`) and `getDeviceModel` (`2b211783`). |
| `src/main/jni/Android.mk` | Builds `pojavexec`, `vloader` (links `openxr_loader`), `jrelauncher`, AWT shims. |
| `src/main/assets/mods/*.jar` | **Bundled** (not downloaded) by coah80: `Visor.jar` (0.4.0+questcraft), `VisorEssentials.jar` (0.4.0), `MoreCulling`, `Noisium`, `BadOptimizations`, `Chunky`, `C2ME` — all 1.20.1 builds. |
| `mods.json`, `devmods.json`, `supportedVersions.json` | Per-version mod manifests (`versions[].name`, `coreMods[]`, `defaultMods[]` of `{slug, version, download_link}`). In this fork they still list **Vivecraft 1.2.5** as a core mod for every version — but the running app reads the *upstream* copies anyway (see §4). |
| `src/main/java/pojlib/util/Constants.java` | `GIT_BRANCH = "QuestCraft-6.0.0"` (used to build the raw JSON URLs). |

### 2.3 Visor — `import/visor` → `Visor/`
Base: fork of [VisorModStudio/Visor](https://github.com/VisorModStudio/Visor) `dev` as of 2026-07-12 (version 0.4.0, single-target **1.20.1**, `gradle/libs.versions.toml`: `minecraftVersion = "1.20.1"`, `fabric-loader 0.16.5`, `lwjgl 3.3.6`, Architectury Loom). coah80 commits: `a0275017` quest android support, `25740234` make essentials work, `32c80265` optimize Quest rendering and world loading.

| File | Role |
|---|---|
| `visor-core/.../provider/openxr/QuestVLoader.java` | New: `native` methods bound to Pojlib's `libvloader.so` (EGL/Dalvik handles, `setupAndroid`, `getDeviceModel`; `isQuest2()/isQuest3Family()`). |
| `visor-core/.../provider/openxr/QuestAndroidXRBridge.java` | New: implements AtumVR's `AndroidXRBridge` using `QuestVLoader`. |
| `visor-core/.../provider/openxr/XrProvider.java` | Calls `XRPlatform.setAndroidBridge(new QuestAndroidXRBridge())` before creating the session. |
| `visor-core/.../provider/openxr/render/XrRenderer.java`, `XrRenderTarget.java`, `render/*`, `VRPoseImpl.java`, overlays | Quest perf/render tweaks (`32c80265`). |
| `visor-fabric/.../WorldPreloader*.java`, `mixin/CreateWorldScreenMixin.java` | New world pre-generation screen (uses bundled Chunky; `32c80265`). |
| `visor-api/.../player/pose/PlayerPoseType.java` | Small API change so VisorEssentials works (`25740234`). |
| `libs/atumvr-api-quest.jar`, `libs/atumvr-core-quest.jar` | Prebuilt AtumVR with coah80's Android patches (replace `atumvr-*-1.14.0beta-snapshot#8`). |
| `libs/lwjgl-openxr-quest-3.3.3.jar` | Android-capable lwjgl-openxr (GLES + Android loader structs); shaded into the Fabric jar instead of upstream `org.lwjgl:lwjgl-openxr:3.3.6` + desktop natives (`visor-fabric/build.gradle`). |
| `version.properties` | `0.4.0` + stage `+questcraft`. |

Upstream has since moved on: Visor `dev` is now a **Stonecutter** multi-version build (`stonecutter.properties.toml` targets 1.20.1 … 26.3, Fabric/Forge/NeoForge), v0.6.1 released 2026-10-31.

### 2.4 AtumVR — `import/atumvr` → `AtumVR/`
Base: fork of [Phoenix-Ra/AtumVR](https://github.com/Phoenix-Ra/AtumVR) `master` at 1.13.0beta. coah80 commits: `d04c1669` android openxr stuff, `1d7dd401` optimize Quest OpenXR runtime.

| File | Role |
|---|---|
| `atumvr-core/.../session/platform/XRPlatform.java` | New: platform abstraction. `Desktop` (WGL/GLX bindings) vs `Android` (`KHRLoaderInitAndroid`, `XrInstanceCreateInfoAndroidKHR`, `XrGraphicsBindingOpenGLESAndroidKHR`, GLES swapchain images). Android is chosen when `os.version` contains "Android" (Pojlib sets `-Dos.version=Android-…`). |
| `atumvr-core/.../session/platform/AndroidXRBridge.java` | New: interface Visor implements (EGL/Dalvik handles). |
| `XRInstance.java`, `XRSystem.java`, `XRSwapChain.java`, `XRSession.java`, `XRState.java` | Route instance/graphics binding/swapchain creation through `XRPlatform`; Quest perf work (`1d7dd401`). |
| `atumvr-api/build.gradle` | `lwjgl-openxr` replaced by `files("${rootDir}/../deps/lwjgl-openxr-quest-3.3.3.jar")` — **that `deps/` folder is not in any imported repo** (the jar exists at `Visor/libs/`). |

Upstream AtumVR is now 1.14.0beta snapshots with separate `session/opengl` and `session/vulkan` packages, Linux arm64 + Steam Frame support, and no Android platform.

---

## 3. Where Vivecraft was replaced by Visor

Upstream QuestCraft ([QuestCraftPlusPlus/QuestCraft](https://github.com/QuestCraftPlusPlus/QuestCraft)) uses a Quest fork of Vivecraft ([QuestCraftPlusPlus/VivecraftMod](https://github.com/QuestCraftPlusPlus/VivecraftMod), branches `OpenXR-<mc>`), downloaded per version as a `coreMods` entry in Pojlib's `mods.json` (e.g. `vivecraft-1.20.1-1.2.5-fabric.jar` from that fork's releases). Its OpenXR provider calls `org.vivecraft.util.VLoader` natives in Pojlib's `vloader.cpp`.

coah80's swap, file by file:

| Layer | Upstream (Vivecraft) | coah80 (Visor) | Commit |
|---|---|---|---|
| Mod delivery | `mods.json` `coreMods: Vivecraft` downloaded at prelaunch | `Installer.installVisor()` deletes any `*vivecraft*` jar from `mods/`, writes bundled `assets/mods/Visor.jar` (+ `VisorEssentials.jar`), rewrites `instance.extProjects` with `bundled://visor` entries. **Throws unless `instance.versionName == "1.20.1"`.** | `f241fb2c`, `0dfa91d5`, `2b211783` |
| Prelaunch | `API.prelaunch` → `MinecraftInstances.CheckVivecraftConfig(instance)` | Call removed; `installVisor` + save `instances.json` instead; `prelaunch` returns `boolean` | `f241fb2c` |
| Supported versions | `APIHandler.getQCSupportedVersions` (downloads upstream `supportedVersions.json`) | `return new String[] {"1.20.1"}` | `f241fb2c` |
| Core-mod detection | slugs from `mods.json` | plus hard-coded `Visor`, `VisorEssentials` | `f241fb2c`, `0dfa91d5` |
| Native bridge | `Java_org_vivecraft_util_VLoader_*` | Same functions re-exported as `Java_org_vmstudio_visor_core_client_provider_openxr_QuestVLoader_*` (+ `getDeviceModel`), `setupAndroid` now waits for `launcherXRStopped` | `f241fb2c`, `2b211783` |
| OpenXR platform code | Inside Vivecraft (`DeviceCompat.Mobile`) | Inside AtumVR (`XRPlatform.Android`) + Visor (`QuestAndroidXRBridge`) | AtumVR `d04c1669`; Visor `a0275017` |
| OpenXR Java bindings | `QuestCraftPlusPlus` lwjgl-openxr fork | `lwjgl-openxr-quest-3.3.3.jar` shaded into Visor | Visor `a0275017` |
| Launcher XR hand-off | `DeinitializeLoader` after a 200 ms delay | `StopSubsystems` + `DeinitializeLoader` + set `launcherXRStopped` | wrapper `e24b36b3` |

Leftovers that still reference Vivecraft: `Installer.moveLocalAssets` still writes `config/vivecraft-client-config.json`; `MinecraftInstances.CheckVivecraftConfig` still exists (unused); this fork's `mods.json` still lists Vivecraft (harmless today because the app reads upstream's copy, and `installVisor` deletes the jar after it is downloaded).

---

## 4. How versions and mods are defined and downloaded

1. **Version list.** Upstream: Pojlib downloads `supportedVersions.json` from `QuestCraftPlusPlus/Pojlib@<GIT_BRANCH>`. coah80: hard-coded `["1.20.1"]`. The launcher shows whatever `getQCSupportedVersions` returns.
2. **Instance creation** (`InstanceHandler.create`): Fabric loader = newest from `meta.fabricmc.net/v2/versions/loader` (0.20.1 on 2026-11-23); profile JSON from `/v2/versions/loader/<mc>/<loader>/profile/json`; Minecraft JSON from piston-meta; client, libraries (minus LWJGL), assets downloaded with SHA-1 checks.
3. **Mods** (`Instance.updateMods`): downloads `mods.json` (or `devmods.json` when developer mods are on) **from the upstream QuestCraftPlusPlus repo at runtime**, selects the entry whose `name` equals the instance version, installs `coreMods` (+ `defaultMods` if enabled). The copy in `Pojlib/mods.json` here is not what the app reads.
4. **Visor layer** (`Installer.installVisor`, coah80): after step 3, deletes Vivecraft and the five perf mods, writes the bundled 1.20.1 jars, pins ImmediatelyFast 1.5.5 by URL + SHA-1, forces render/simulation distance 4 once (`.qcx-render-distance-4` marker).
5. **Runtime:** one JRE for every version — `https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE.zip`, which today resolves to release `jre22-6.0.0` = **OpenJDK 22.0.1** (aarch64). One LWJGL for every version — Pojlib's **3.3.3** build. Mojang's `javaVersion` and LWJGL libraries are ignored.
6. **Launch:** `JREUtils.launchJavaVM` with hand-built args (`generateLaunchArgs`), GL via `libltw.so`.

Consequence: the version picker, mod manifest, Visor install gate, Visor build, AtumVR build, JRE and LWJGL are each pinned in a different place. Multi-version support has to touch all of them (see `docs/VERSION_MATRIX.md`).

---

## 5. Upstream comparison notes

- **Pojlib `QuestCraft-6.0.1`** (upstream, 58 commits ahead / 12 behind `QuestCraft-6.0.0`): supports up to **1.21.11** with Vivecraft 1.3.x, and replaces `libltw.so` with **MobileGlues** (`libmobileglues.so`), plus JREUtils/egl_bridge changes. coah80's fork is on 6.0.0 and does not have these.
- **Pojlib `QuestCraft-6.0.0`** (upstream, what the app still downloads JSON from) lists 1.19.2–1.21.5; last updated 2026-11-16 ("Update Fabric API for 1.21.1"), i.e. upstream edits reach VisorQuest users with no release.
- **Community 26.x ports:** [jringo12/questcraft-26.2-experiment](https://github.com/jringo12/questcraft-26.2-experiment) (Vivecraft, MC 26.2; per-version JRE selection via `javaVersion.majorVersion`, bundled `JRE-25.zip` asset, Vulkan-capable path, pinned Fabric loader 0.19.3) and [MisterEnglishDev/QuestCraftPlus](https://github.com/MisterEnglishDev/QuestCraftPlus) (Vivecraft, MC 26.2; documents the LWJGL 3.4.1 `CallbackI` ABI break and the "13 Android-only OpenXR classes" workaround in `docs/PORTING_NOTES.md`). Both are Vivecraft-based; neither touches Visor.

---

## 6. Build-blocking gaps found while reading (untested)

1. **Git LFS objects are missing.** `Pojlib/src/main/jniLibs/arm64-v8a/*.so` and `Pojlib/src/main/jni/libopenxr_loader.so` are LFS pointers. The LFS batch API for `twanye/VisorQuest` returns 404 "Object does not exist on the server" (checked for `liblwjgl.so`, oid `7cd38e15…`). Pojlib cannot be built from this repo until the LFS objects are pushed (source: coah80/Pojlib or QuestCraftPlusPlus/Pojlib; not verified accessible).
2. **AtumVR's `../deps/lwjgl-openxr-quest-3.3.3.jar`** is referenced but absent; only `Visor/libs/` has a copy. Provenance of that jar is undocumented (likely the QuestCraftPlusPlus lwjgl3 Android fork).
3. **Visor consumes prebuilt AtumVR jars** (`Visor/libs/atumvr-*-quest.jar`), not the `AtumVR/` source; rebuilding AtumVR does not update Visor automatically.
4. `Pojlib/.gitmodules` references `wrapper` → `QuestCraftPlusPlus/Wrapper-IL2CPP` (not imported).

## Sources
- This repo: `main`, `import/pojlib`, `import/visor`, `import/atumvr` (files and SHAs above).
- https://github.com/QuestCraftPlusPlus/Pojlib (branches `QuestCraft-6.0.0`, `QuestCraft-6.0.1`; compare `QuestCraft-6.0.0...QuestCraft-6.0.1`)
- https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper, https://github.com/QuestCraftPlusPlus/QuestCraft, https://github.com/QuestCraftPlusPlus/VivecraftMod
- https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/tag/jre22-6.0.0 (`release` asset: `JAVA_VERSION="22.0.1"`)
- https://github.com/VisorModStudio/Visor (`stonecutter.properties.toml`), https://github.com/Phoenix-Ra/AtumVR
- https://github.com/jringo12/questcraft-26.2-experiment, https://github.com/MisterEnglishDev/QuestCraftPlus
