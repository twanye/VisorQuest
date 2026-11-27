# VisorQuest 26.x Plan (26.1, 26.1.1, 26.1.2, 26.2, 26.3)

> **Status: PLAN ONLY. Nothing here has been built or run by this team. Every 26.x row is untested on hardware.**
> Written 2026-11-27 by Hark from a read of the two Quest 26.2 ports, upstream QuestCraft, and this branch (`deepseek/visor-0.6.1-quest` @ `302ca1fd71bd756d293140d55d969b208537266b`). Builds on `docs/VERSION_MATRIX.md` §2, §2a and §5 and corrects parts of them (see §4).
>
> Evidence tags: **[read]** seen in a repo, commit or file; **[hw-user]** the User reports running it on a headset; **[hw-commit]** a commit message describes an on-device run; **[inferred]** deduced, not seen; **[memory]** background knowledge, verify before relying on it; **[unverified]** could not be checked.

## 0. Summary

**Goal.** Add 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 to VisorQuest, using both working-on-Quest 26.2 ports as references so the result works on hardware as soon as possible:

- **QCP** = QuestCraftPlus+ by MisterEnglishDev (formerly MrEnglish1996): `MisterEnglishDev/QuestCraftPlus` (docs + APK), `MisterEnglishDev/QuestCraftPlus-Pojlib`, `MisterEnglishDev/QuestCraftPlus-VivecraftMod`. The User plays servers on it **[hw-user]**. It renders through **OpenGL (MobileGlues)**.
- **NEC** = `necat101/questcraft-26.2-experiment` (monorepo: `Pojlib/`, `QCXR-XR-Wrapper/`, `Vivecraft-26.2-Quest/`). Its README calls it experimental and says crashes should be expected **[read]**. Commit messages and `.debug-video-frames/` show device testing, but nothing states it runs well **[read]**. It launches 26.2 with **`--graphicsBackend vulkan`**.

**Both ports sit on upstream `QuestCraftPlusPlus/Pojlib` branch `QuestCraft-6.0.1` @ `0e22a02a43e6705cbefa56ca00951db774ff116a` ("Fix kilt"), which uses MobileGlues, not `libltw.so`.** Our Pojlib is the 6.0.0 lineage (coah80) with `libltw.so` and a Unity 6 launcher. So the renderer is the first structural decision (§6, step 2).

**Neither port runs LWJGL 3.4.1 at runtime.** QCP keeps the Pojav LWJGL 3.3.3 jars we already ship and byte-patches the few methods 26.2 calls. NEC swaps in FoldCraftLauncher's (FCL) LWJGL 3.3.6 build, which includes Vulkan. Minecraft's own 3.4.1 jars are skipped by Pojlib in both. This changes VERSION_MATRIX P4 (§4).

**Neither port touches 26.1.x or 26.3 (SDL3).** Both hard-code `"26.2"`.

### Decision table (what to take from where)

| Piece | Take from | Why |
|---|---|---|
| Base Pojlib lineage / renderer | **Upstream QuestCraft-6.0.1** MobileGlues path (both ports use it), selected **per instance** | Both 26.2 ports render through MobileGlues; QCP's is the proven one. Per-instance selection keeps 1.20.1 on `libltw.so` until MobileGlues is proven there. |
| 26.2 graphics backend | **QCP**: OpenGL (Minecraft default), with Vulkan Java classes present for class loading | Proven **[hw-user]**. NEC's Vulkan path is unproven and needs far more native work. |
| Java 25 runtime: selection | **NEC** (`Installer.installJVM(..., VersionInfo)`, `VersionInfo.javaVersion`), changed to side-by-side runtime dirs | Per-version selection keeps JRE 22 for ≤1.21.11. QCP replaces the one JRE with 25 for every version. NEC deletes and reinstalls on every switch. |
| Java 25 runtime: binary | Spike: **QCP `JRE25.zip`** (pinned SHA-256). Release: **build our own** | QCP's is the one tied to the proven setup (if the User's build includes it; see risk R1). Neither port publishes source for its JRE (GPL-2.0 + CE issue, §8). |
| Installer/download robustness | **QCP** `9361b33`, `bd3d402` | Our `StreamDL` has the same one-byte `read()` bug QCP fixed; likely related to our first-install asset failures **[inferred]**. |
| LWJGL runtime jars (26.1.x/26.2 on OpenGL) | **QCP**: our existing Pojav 3.3.3 jars + QCP's byte-patched `lwjgl.jar`, `lwjgl-stb.jar`, GLFW additions, IME interfaces | Our `jre_lwjgl3glfw/libs/*.jar` are byte-identical in size to the ones QCP patched; smallest change; proven. |
| LWJGL runtime jars (later Vulkan phase) | **NEC**: FCL `lwjgl-fcl-vulkan-3.3.6.jar` + FCL natives + `egl_bridge.c` exports | Only option that has Vulkan on Android; unproven; provenance check needed. |
| OpenXR Java binding for Visor/AtumVR on 26.x | **QCP** split: `lwjgl-openxr-3.4.1-patched.jar` + `questcraft-android-openxr-extras.jar`, no `natives-linux-arm64` | Proven with Vivecraft **[hw-user]**. Visor's 26.x build compiles against LWJGL 3.4.1, which hits the `CallbackI` break with the 3.3.3 Quest jar. |
| JVM args | **NEC** Java-25 block + LWJGL libname props, then our P5 (args from the version JSON), plus **QCP** heap cap `cc7c7ce` | NEC's set is the most complete; neither reads the JSON's `--sun-misc-unsafe-memory-access=allow`. |
| JVM launch thread / signal handler removal | **NEC**, only if the JRE 25 crashes at launch | QCP runs JRE 25 without it **[read]**; NEC's build logs (`...-jvmlaunchthread-nosig.log`) suggest NEC's JRE needed it. |
| Fabric loader selection | **NEC** `FabricMeta` fix, but pin per version from our table (≥0.19.5 for 26.2/26.3, ≥0.18.4 for 26.1.x) | NEC fixes the selector bug VERSION_MATRIX noted; NEC's 0.19.3 pin is below Visor 0.6.1's minimum. |
| 26.2 mod list | **QCP** list minus Vivecraft and Sodium, plus Visor/VisorEssentials `0.6.1+mc26.2` | QCP dropped Sodium for a confirmed on-device MobileGlues failure (`341ae41`). |
| Default config fixes | **QCP** `a078860` (options.txt), `b1faa22a` idea (legacy config values) | Real 26.2 option-range errors seen on device **[hw-commit]**. |
| Per-eye swapchains | **QCP** `1abde61d` pattern, if Visor shows a blank right eye | Hypothesised MobileGlues array-layer bug; QCP marks it unverified. |
| Unity launcher | **Ours** (already has the `launcherXRStopped` handoff); NEC's handoff only as reference | QCP used the stock wrapper. Our `vloader.cpp` already waits on `launcherXRStopped`. |
| OSHI shim, fake `/proc`, OpenAL config | **NEC**, only if those failures appear | QCP runs 26.2 without them. |
| Bundled manifests / `asset://` mods | **NEC** mechanism, optional | Useful for offline and dev builds; costs APK size (decision 1 in VERSION_MATRIX §6). |

## 1. Sources read

| Repo | Ref read | Notes |
|---|---|---|
| `MisterEnglishDev/QuestCraftPlus` | `main` @ `fea04dcec79ca30c32715514558d23d712fcb2d3` | README + `docs/PORTING_NOTES.md`; release `v26.2-1.0.0` APK (47.7 MB, 2026-08-29 19:27 UTC). No code, no license file. |
| `MisterEnglishDev/QuestCraftPlus-Pojlib` | `questcraftplus-main` @ `a07886068a1f76f1b92ef627cf8c5abd8889e12a` (code); `main` @ `122415dd07b080ddc3ec85adb88545372861a12a` (live `mods.json`) | Root commit `48ad8030c310168cd4d320147dc27ceee1cd7b4c` is an orphan squash of upstream 6.0.1 @ `0e22a02` (tree differs in 7 files only). `Constants.GIT_BRANCH = "main"`, so the app reads `mods.json` from `main` at runtime while code fixes live on `questcraftplus-main`. Has an LGPL-3.0 `LICENSE`. Release `jre25-android-arm64` hosts `JRE25.zip`. |
| `MisterEnglishDev/QuestCraftPlus-VivecraftMod` | `QuestCraft-26.2` @ `b1faa22a1da2bd587084d6db4ca6bee729734cf3` | Merge of QuestCraftPlusPlus `OpenXR-1.21.10` into upstream Vivecraft `openxr` @ `13eafae17a987521cc270e81cd774a4746ad9299`. Has `LICENSE.md` (LGPL-3.0 + components). |
| `necat101/questcraft-26.2-experiment` | `main` @ `e0c87d4020e68f5d8c9b367e5f160853a50107ba` | Sources imported in `6492b180218f5c7ef93f28097d498d8fb40b839e`; fixes in `370d406`, `7c596d2`, `9b057ef`, `4509723`, `e0c87d4`. History sits on top of the QuestCraft README repo. No top-level license; `Pojlib/LICENSE` and `QCXR-XR-Wrapper/LICENSE` are LGPL-3.0, `Vivecraft-26.2-Quest/LICENSE.md` LGPL-3.0. Earlier snapshots exist as `necat101/Pojlib`, `necat101/QCXR-XR-Wrapper`, `necat101/Vivecraft-26.2-Quest` (2026-07-20). |
| `QuestCraftPlusPlus/Pojlib` | `QuestCraft-6.0.1` @ `0e22a02a43e6705cbefa56ca00951db774ff116a` | Common base of both ports. |
| `QuestCraftPlusPlus/QCXR-XR-Wrapper` | `master` @ `5aeb178085f36e3f4db70a548330d05f34bdf897` | NEC's wrapper diff base. Unity 2022.3.62f3. |
| `twanye/VisorQuest` | `deepseek/visor-0.6.1-quest` @ `302ca1fd71bd756d293140d55d969b208537266b` | Pojlib 6.0.0 lineage, `libltw.so`, Unity 6000.4.10f1, `Visor/` 0.6.1 (Stonecutter `lwjgl_version` 3.4.1 for 26.1–26.2, 3.4.3 for 26.3), `AtumVR/` 1.14. |

**Authorship note (NEC).** Every NEC commit is authored as `Makhi Burroughs <nger10449@gmail.com>`, and the README carries a Patreon link for "MakhiBurroughs". Whether this is QuestCraft's lead developer or necat101 using that identity is **[unverified]**. Ask before treating it as permission from the QuestCraft team.

**Hardware evidence, stated exactly.**
- QCP: the User plays servers on it **[hw-user]**. Commit messages describe on-device runs ("showed up on-device as 'Free physical memory: 0.3 GiB'", Adreno 740 shader errors, "the pulled device log") **[hw-commit]**. `1abde61d` (per-eye swapchains) says "Unverified until tested on-device". The only published APK (`v26.2-1.0.0`, 2026-08-29 19:27 UTC) predates the JRE 25 commit `9361b33` (2026-08-30 01:13 UTC) and the `JRE25.zip` release (23:50 UTC). So the published APK points at QuestCraft's Java 22 JRE, and **which QCP build the User actually runs is unknown** (see R1).
- NEC: README says experimental. No statement that it runs. Crash frames and build logs in the repo show it was run on a device **[read]**.

## 2. What each port changed

Paths are relative to each repo (NEC paths under `Pojlib/`, `QCXR-XR-Wrapper/`, `Vivecraft-26.2-Quest/`). "vs 6.0.1" = against upstream Pojlib `QuestCraft-6.0.1` @ `0e22a02`; "vs ours" = against our `Pojlib/` @ `302ca1f`.

### 2.1 Java runtime

| | QCP | NEC | Ours |
|---|---|---|---|
| Version | OpenJDK **25.0.4.1.1** (`release`: `25.0.4.1.1-internal-adhoc.root.openjdk`, SOURCE `db58e534a804+`, date 2026-08-18) | OpenJDK **25.0.3** (`25.0.3-internal-adhoc.runner.openjdk`, SOURCE `d91e3ef51121+`, built 2026-03-17 by a CI "runner") | OpenJDK 22.0.1 (`QuestCraftPlusPlus/android-openjdk-build-multiarch` latest) |
| Origin | Self-built: "graalvm/labs-openjdk jdk25 branch + ported Bionic compat patches" (release text). **Build scripts and patches not published.** | **Undocumented.** Same size (≈61 MB) and Fabric pin as the "jringo12 experiment" cited in VERSION_MATRIX **[inferred: same artifact]**. | Upstream CI release |
| Delivery | Downloaded at runtime: `https://github.com/MrEnglish1996/QuestCraftPlus-Pojlib/releases/download/jre25-android-arm64/JRE25.zip` (48,955,276 B, SHA-256 `221acb8ef910d17435f9539db454a7a293f548430f289eb81446b58004f2a4f1`) | Bundled in the APK: `src/main/assets/runtimes/JRE-25.zip` (61,061,819 B, SHA-256 `43ce2b4e5b27c1d5879bca85805c5d8d5f9f0f5ee190bd36d99efd6650461be0`), stored as a plain Git blob | Downloaded at runtime |
| Selection | Global: one `runtimes/JRE`, replaced by 25 for **every** version. Marker `runtimes/JRE/.jre_version` = "25" forces reinstall (`src/main/java/pojlib/install/Installer.java`, commit `9361b337bfb0b3b31c21956e4a322dfbff5659c9`) | Per version: `VersionInfo.javaVersion.majorVersion >= 25` → Java 25, else 22; marker `.questcraft-java-major`; one dir, wiped and reinstalled when the major changes (`Installer.java`, `install/VersionInfo.java`, `util/JREUtils.java#prelaunchCheck`, in `6492b18`) | One JRE, presence check only |

NEC also changes `JREUtils.initJavaRuntime()` to dlopen `libjli.so` first, check all results, then dlopen every `.so` under the runtime, and adds `lib/jli` and `/system_ext/lib64` to `LD_LIBRARY_PATH`.

### 2.2 LWJGL jars and natives

**Background [read]:** Pojlib's `Installer.installLibraries` skips every Mojang library whose name contains `lwjgl` and puts its own `lwjgl-glfw-classes.jar` on the classpath. Neither port changes that skip. So Minecraft 26.2 (compiled against LWJGL 3.4.1) runs on whatever LWJGL Pojlib supplies.

**QCP: keep Pojav 3.3.3, patch what 26.2 calls.**
- `jre_lwjgl3glfw/libs/lwjgl.jar`: `MemoryUtil.class` byte-patched to add `memFree(ByteBuffer/IntBuffer/ShortBuffer/CharBuffer/LongBuffer/FloatBuffer/DoubleBuffer)` (`c98b70a4bcae0ceb8ff09d27dda43eb9ead29eea`, `b85338dc5b20b465a8e3f175620049c1a2d958dc`). The commit says swapping to 3.3.6/3.4.1 broke the old sibling jars and 3.4.1's Java 25 bytecode breaks Jetifier.
- `jre_lwjgl3glfw/libs/lwjgl-stb.jar`: `STBImageResize.nstbir_resize_uint8_linear` added as a Java method delegating to the old `nstbir_resize_uint8` native (`398d55940b87f2ff0d58ca14e5100ed0c93dc230`). Patch script not checked in.
- `jre_lwjgl3glfw/src/main/java/org/lwjgl/glfw/GLFW.java`: `glfwPlatformSupported()` and about 20 GLFW 3.4 methods (`9361b33`); `glfwGetInputMode` NPE fix (`c98b70a`).
- New `GLFWPreeditCallback(I)`, `GLFWIMEStatusCallback(I)`, `GLFWPreeditCandidateCallback(I)` placeholder interfaces (`c98b70a`).
- `jre_lwjgl3glfw/build.gradle`: exclude `META-INF/versions/**` when merging (`c98b70a`).
- Output `src/main/assets/lwjgl/lwjgl-glfw-classes.jar` rebuilt; natives unchanged from 6.0.1.
- Our `jre_lwjgl3glfw/libs/*.jar` have exactly the pre-patch sizes QCP started from (e.g. `lwjgl.jar` 777,773 B, `lwjgl-stb.jar` 119,671 B) **[read]**, so QCP's patched jars are drop-in candidates.

**NEC: FCL LWJGL 3.3.6 base (with Vulkan).**
- `jre_lwjgl3glfw/base/lwjgl-fcl-vulkan-3.3.6.jar` (10.8 MB; manifest `3.3.6`): LWJGL core + `glfw`, `opengl`, `vulkan` (3,288 classes), `stb`, `openal`, `nanovg`, plus FCL's GLFW. No `shaderc`/`spvc`/`vma` Java classes. A constant scan shows it already has the typed `memFree` overloads, `nstbir_resize_uint8_linear` and `glfwPlatformSupported` **[read]**, which QCP had to patch in.
- `jre_lwjgl3glfw/build.gradle` rewritten: compile QuestCraft's `GLFW.java` (+220 lines vs 6.0.1) against the FCL jar and zip only `GLFW*.class`/`GLFWWindowProperties.class` over it into `src/main/assets/lwjgl/lwjgl-glfw-classes.bin` (renamed `.jar` → `.bin`; `UnityPlayerActivity.installLWJGL` reads the `.bin`).
- Natives (Git LFS): `src/main/jniLibs/arm64-v8a/liblwjgl.so`, `liblwjgl_nanovg.so`, `liblwjgl_opengl.so`, `liblwjgl_stb.so`, `liblwjgl_tinyfd.so` replaced; `liblwjgl_vma.so`, `libshaderc.so`, `libspirv-cross-c-shared.so` added. Matching files sit in `.research/fcl-native-audit/jni/arm64-v8a/` (FCL origin **[inferred]**).
- Source trees referenced as gitlinks with **no `.gitmodules`** (unfetchable): `.tools/fcl-source`, `.tools/lwjgl3-android-3.4.1`, `.research/Vivecraft-QC-OpenXR`, `.research/Vivecraft-QC-Zink`, `.reference-tlauncher`. `.research/jni-3.4.1-signatures.txt` shows a 3.4.1 Android build was explored and not used.

### 2.3 OpenXR Java binding (the 3.4.1 `CallbackI` break)

- **QCP** (`QuestCraftPlus-VivecraftMod`): drop QuestCraftPlusPlus's full `lwjgl3` 3.3.3 OpenXR jar; use official `org.lwjgl:lwjgl-openxr:3.4.1` plus `libs/questcraft-android-openxr-extras.jar` with the 13 Android-only classes (`KHRAndroidCreateInstance`, `KHRLoaderInitAndroid`, `KHROpenGLESEnable`, `XrGraphicsBindingOpenGLESAndroidKHR`, `XrGraphicsRequirementsOpenGLESKHR`, `XrInstanceCreateInfoAndroidKHR`, `XrLoaderInitInfoAndroidKHR`, `XrSwapchainImageOpenGLESKHR`, + `.Buffer`s) (`5be2cf330510a87ca92957100de954fb6ecc2e9e`). Follow-ups that matter:
  - extras jar needs a minimal `fabric.mod.json` or Fabric refuses all mods (`5bde9a713c8769c48cb99a333a400836c5622e3e`);
  - official 3.4.1 `XRCapabilities` lacks the GLES field; QuestCraft's 3.3.3 `XRCapabilities.class` is byte-swapped into `libs/lwjgl-openxr-3.4.1-patched.jar` (`5ecbd35136d7cc5841d6c74875a33da34e79592a`), which also needs a `fabric.mod.json` (`09fb9e39b8d1f515001f6aa014315beed523a5b1`); shipping it as a duplicate class did not work (`307a045bdd852ad48ff1e2e72d192bf061982cbf`);
  - **remove `lwjgl-openxr:natives-linux-arm64`**: Android reports `os.name=Linux`, so LWJGL extracts the glibc loader and fails on `libstdc++.so.6` (`8935cccbd152c78c5ca2e8df0e066857393c7cb6`);
  - bundle `lwjgl-vulkan` 3.4.1 **Java classes only**, because 26.2's `PreferredGraphicsApi.getBackendsToTry()` instantiates `VulkanBackend` (`dd944debf9818503af3bdd9e54b86fcf1b114837`);
  - Fabric caches nested jars by the mod's `fabric.mod.json` version, so bump `mod_version` whenever a nested jar changes (QCP-Pojlib `a5dfd2f9bff45ff63e9c84ce22434059d421b277` message; VivecraftMod `84c5a53e381e22b742f33e3d4222355b360945ff` is the bump).
- **NEC**: keeps QuestCraft's `lwjgl-openxr-android-3.3.3.jar` (`Vivecraft-26.2-Quest/fabric/build.gradle` line 26; nested at `fabric/src/main/resources/META-INF/jars/`). This works only because NEC's runtime core is LWJGL 3.3.6, not 3.4.1 **[inferred]**.

### 2.4 JVM arguments

- **QCP** (`util/JREUtils.java`, `cc7c7ce64af292c87a42d44dc9fb2988b5a88d8d`): heap `-Xmx` = min(available, 45% of total RAM), clamped 1536–4096 MB. Otherwise upstream 6.0.1's flags, ZGC included. No Java 25-specific flags.
- **NEC** (`util/JREUtils.java`, `6492b18`):
  - Java ≥25: `-XX:+IgnoreUnrecognizedVMOptions -XX:+DisableExplicitGC -XX:-UsePerfData -XX:-UseContainerSupport -XX:-CreateCoredumpOnCrash -XX:ErrorFile=<gameDir>/hs_err_pid%p.log -Djdk.lang.Process.launchMechanism=FORK`, `--enable-native-access=ALL-UNNAMED`, a block of `--add-exports`/`--add-opens` (java.base, java.desktop, java.management, jdk.unsupported). ZGC flags only for <25.
  - LWJGL props: `org.lwjgl.system.allocator=system`, `glfw.checkThread0=false`, `glfw.libname=libpojavexec.so`, `opengl/opengles/egl/vulkan/shaderc/spvc/openxr/openal/stb/nanovg/tinyfd/lmdb/openvr.libname=…`, `SharedLibraryExtractPath=<cache>/lwjgl-natives`.
  - Locale/timezone/encoding, IPv4 preference, `user.dir`, `jna.nounpack`, `jna.tmpdir`, five `sodium.checks.*=false`, OSHI path props.
  - Launches the JVM on a new thread `QuestCraft-JVM` and returns 0 at once; removes the Pojav signal catcher from `src/main/jni/jre_launcher.c`.
- **Neither** reads `arguments.jvm` from the version JSON. 26.2's JSON adds `--sun-misc-unsafe-memory-access=allow` and `--enable-native-access=ALL-UNNAMED` **[read: `.research/jars/26.2.json` in NEC]**; NEC adds only the second.

### 2.5 Renderer

- Both: **MobileGlues** (`src/main/jniLibs/arm64-v8a/libmobileglues.so`, `libmobileglues_info_getter.so`; `POJLIB_RENDERER=MobileGLUES`, `MG_DIR_PATH`), inherited from upstream 6.0.1. Neither has `libltw.so`.
- NEC's README still describes "Light Thin Wrapper" as the fallback renderer; the code has no `libltw.so` **[read]**. Treat that README section as stale.
- QCP evidence on MobileGlues 1.3.4 (`341ae4185380e0552858a1d05a56cb6b0d6a4a1d`): it reports `GL_VERSION "4.0.0 MobileGlues 1.3.4"`, so Sodium 0.9.2-alpha.4 emits desktop GLSL that the Adreno 740 GLES driver rejects; terrain invisible. Fix: drop Sodium.
- Ours: `libltw.so` (`POJLIB_RENDERER=LightThinWrapper`, `JREUtils.loadGraphicsLibrary` returns `libltw.so`, `egl_bridge.c` dlopens it).

### 2.6 `egl_bridge.c` and other JNI

- **QCP: no JNI changes** vs 6.0.1 **[read]**.
- **NEC** (`src/main/jni/egl_bridge.c`, `6492b18` + `4509723`):
  - FCL GLFW ABI stubs: `pojavSetInjectorCallback`, `pojavSetHitResultType`.
  - Vulkan entry points FCL's GLFW resolves from `libpojavexec`: `pojavInitVulkanLoader`, `pojavVulkanSupported`, `pojavGetRequiredInstanceExtensions`, `pojavGetInstanceProcAddress`, `pojavGetPhysicalDevicePresentationSupport`, `pojavCreateWindowSurface` (creates a `VkAndroidSurface` on a native window).
  - `Java_org_lwjgl_vulkan_VK_getVulkanDriverHandle`, `Java_org_lwjgl_vulkan_VK_getFpsAddress` (**we already took the second**).
  - `pojavGetNativeWindow`/`pojavReleaseNativeWindow`: fetch an `ANativeWindow` from `UnityPlayerActivity.getMinecraftSurface()`.
  - `pojavInit()` returns 1; EGL init moved lazily into `pojavCreateContext`.
- NEC `src/main/jni/vloader.cpp`: `setupAndroid` uses `GetEnv` before attach, sets `gameReady`, then **waits up to 15 s for `API.openXRHandoffReleased`** before `xrInitializeLoaderKHR`. Our `vloader.cpp` already waits on `API.launcherXRStopped` (coah80's equivalent) **[read]**.
- NEC `src/main/jni/Android.mk`: `-DXR_USE_GRAPHICS_API_VULKAN`; `environ.h` includes `vulkan/vulkan.h`; `build.gradle` excludes `**/libopenxr_loader.so` from the AAR packaging.

### 2.7 Launcher (Unity) changes

- **QCP:** stock `QCXR-XR-Wrapper` except a debug keystore and a headless build script (README; neither is published).
- **NEC** (`QCXR-XR-Wrapper/`, vs upstream `master` @ `5aeb178`): `Assets/Scripts/InstanceButton.cs` (prelaunch on a worker thread; on `gameReady`, fade, `JNIStorage.CloseXR()`, wait two frames, call `API.releaseOpenXRHandoff`); `Assets/Scripts/JNIStorage.cs` (`CloseXR` uses `StopSubsystems` + `DeinitializeLoader`, null-safe); `Assets/XR/Settings/OpenXR Package Settings.asset` (Oculus Touch + Touch Pro profiles on, PICO off, Meta "Metro" feature added); `ProjectSettings.asset` (version code 54, local keystore); `Assets/Editor/QuestCraftCliBuild.cs` (CLI build); new `Pojlib-release.aar` (86 MB, includes the JRE). Pojlib `UnityPlayerActivity.java` adds a hidden `SurfaceView` and `getMinecraftSurface()` for the Vulkan surface.
- **Ours:** Unity 6000.4.10f1 (coah80), `InstanceButton.cs` already does `DeinitializeLoader` then sets `launcherXRStopped`.

### 2.8 Mod lists for 26.2

- **QCP** (`mods.json` on `main` @ `122415d`): core Vivecraft 1.3.19 (`.../releases/download/26.2-1.3.15-openxr/vivecraft-26.2-1.3.19-fabric.jar`), Fabric API `0.158.0+26.2`. Default: Lithium `0.25.3`, BadOptimizations `2.4.1`, zFastNoise `1.0.40+26.2`, ModernFix-mVUS `5.27.19-build.1`, Better Biome Blend `26.2-1.4.0`, EntityCulling `1.10.5`, Simple Voice Chat `fabric-2.6.22+26.2`, Cloth Config `26.2.155`, FerriteCore `9.0.0`, Krypton `0.3.1`, MoreCulling `1.8.1`. Sodium removed (`341ae41`). Fabric loader: Pojlib's "latest" selector.
- **NEC** (`src/main/assets/mods.json`): core Vivecraft `1.3.15-qc-openxr-vulkan4-input-session` (`asset://mods/vivecraft-26.2-1.3.15-quest.bin`), Fabric API `0.153.0+26.2`; default Sodium `0.9.0+mc26.2` (with `src/main/assets/sodium-mixins.properties` disabling three window/context workarounds) and Lithium `0.25.0`. Fabric loader pinned **0.19.3** (`InstanceHandler.java`, `Installer.java`).
- Visor 0.6.1 for 26.2 needs Fabric loader **≥0.19.5** (VERSION_MATRIX §2).

### 2.9 Vulkan

- **QCP:** never renders with Vulkan. Ships only `lwjgl-vulkan` Java classes so `VulkanBackend` can be constructed; OpenGL wins backend selection; no Vulkan natives.
- **NEC:** renders with Vulkan. `MinecraftInstances.java` appends `--graphicsBackend vulkan` for 26.2; Vivecraft `DeviceCompat` gains `MobileVulkan` (`XR_KHR_vulkan_enable`, `XrGraphicsBindingVulkanKHR`, `checkVulkanGraphics` saving required instance/device extensions to settings); `VulkanHelper.java`, `OpenXRStereoRenderer.java`, `MCOpenXR.java` gamma/colour-space fixes (`370d406`, `7c596d2`, `9b057ef`) and render-overhead work (`e0c87d4`); native side in §2.2/§2.6.

### 2.10 Other notable changes

- **QCP** download fixes: `DownloadUtils` treats non-200 as failure and sets a read timeout; `StreamDL` gets a bulk `read(byte[],int,int)` (was one byte at a time) (`9361b33`); `StreamDL.close()`, disconnect on retry, check `renameTo` (`bd3d40261c7ecf6de901a6314a136847909060f4`). Our `Pojlib/src/main/java/pojlib/util/download/StreamDL.java` only overrides `read()` **[read]**.
- **QCP** `src/main/assets/options.txt`: `simulationDistance:5`, `gamma:0.0` (`a078860`); `vivecraft-client-config.json` `weaponCollisionNew:"AUTO"` (`cc7c7ce`).
- **QCP** Vivecraft: took upstream's `ControllerMapping`, deleted `XRBindings.java`, rewrote upstream's stub `DeviceCompat.Mobile` with QuestCraft's logic (`a2bd6a2ec8e3582fd01ceb31311ea39d2a41ac8b`); two per-eye swapchains (`1abde61d9e5324df041e5a1e7a17c66b1dfe209c`).
- **NEC** Vivecraft: based on upstream `openxr` before the 26.2 Vulkan rework (closest to `1cf654c511b4537c25621fd0261aa3b197823ce5`); keeps QuestCraft's `XRBindings.java` and older input classes; `MCOpenXR.java` input tracking and session handling rewrite (`450972324c8d3dfc50b969117db9e09e732c7141`).
- **NEC** Pojlib: `src/hacks/java/oshi/SystemInfo.java` + `ExecutingCommand.java` → `assets/hacks/OshiAndroidShim.jar` (put first on the classpath); fake `/proc`, `/sys`, `/dev` under `files/mg`; OpenAL `alsoft.conf` forcing `opensl,null`; bundled `supportedVersions.json`/`mods.json` read before the network (`APIHandler.copyBundledManifest`); `asset://` mod links always refreshed; `FabricMeta.getLatestVersion` rewritten (prefers stable, fixes the major/minor compare bug); `Installer.refreshLaunchCompatibilityAssets` (26.2 only) re-copies configs and forces the Fabric loader to the front of the classpath.
- **NEC repo hygiene:** commits Unity build logs, a Gradle log, debug video frames, a Git LFS Windows binary, and a gitlink to a TLauncher reference (necat101 also hosts "TLauncher-Craft: cracked questcraft launcher"). **Take nothing from `.reference-tlauncher`.**

## 3. Where the ports differ, and which is more complete or proven

| Area | More proven | More complete | Notes |
|---|---|---|---|
| Rendering on Quest | QCP (OpenGL) **[hw-user]** | NEC (OpenGL and Vulkan code paths) | Visor/AtumVR is GLES-only on Android today, so OpenGL is the only path that fits our VR layer now. |
| JRE 25 | QCP if the User's build has it (R1) | NEC (bundled, per-version) | Both binaries lack published source. |
| LWJGL | QCP (3.3.3 + patches) | NEC (3.3.6 incl. Vulkan) | QCP is whack-a-mole: each missing 3.4.1 method shows up as a crash. NEC's base already has the ones QCP found. |
| OpenXR binding | QCP (3.4.1 split) | QCP | NEC's 3.3.3 jar only works on a 3.3.x core and with a Vivecraft that compiles against it. |
| JVM args | QCP (minimal) | NEC | |
| VR mod code | QCP (upstream-based merge) | NEC (Vulkan, input rewrite) | Neither carries over directly: we use Visor/AtumVR, not Vivecraft. The patterns do. |
| Launcher | QCP (stock) | NEC (handoff) | Ours already has a handoff. |
| Download/installer | QCP | QCP | |

## 4. Corrections to `docs/VERSION_MATRIX.md`

1. **P4 (LWJGL 3.4.x)** is not needed to run 26.2: both ports run it on LWJGL 3.3.x cores. Build a 3.4.x Android LWJGL only if the 3.3.x route dead-ends (likely needed for 26.3's `lwjgl-sdl`, §5.3).
2. **P2 (renderer)** becomes a prerequisite for 26.x: both 26.2 references run on MobileGlues, and nothing shows 26.2 on `libltw.so`.
3. **P6 (Vulkan libraries)**: for the OpenGL path only the `lwjgl-vulkan` **Java classes** are needed (QCP `dd944deb`). `shaderc`/`spvc`/`vma` natives belong to the Vulkan phase. 26.2's JSON lists Vulkan natives only for macOS **[read]**.
4. **Phase 4 order**: do **26.2 first**, then 26.1.2 → 26.1.1 → 26.1, then 26.3. Only 26.2 has known-good references, so a failure there can be diffed against them; the 26.1.x runtime needs are a subset of 26.2's (§5.1).
5. **§2a** says the QCP wrapper is unmodified "except a debug key and headless build script": correct, but QCP's code fixes live on `QuestCraftPlus-Pojlib` branch `questcraftplus-main`, not `main` (which only carries `mods.json`).
6. **Java 25 provenance**: the 61 MB `JRE-25.zip` attributed to "jringo12" also ships in NEC; QCP's `JRE25.zip` is a different build (25.0.4.1.1) with a stated origin but no published scripts.

## 5. Per-version needs

### 5.1 26.1, 26.1.1, 26.1.2
- Java 25 runtime, same as 26.2. JVM args from the version JSON (26.1/26.2 add `--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED`).
- LWJGL: JSON lists 3.4.1 (no Vulkan/shaderc/spvc/vma, GLFW still present). Use the same patched 3.3.3 runtime as 26.2; QCP's patches only add methods, so they are harmless here **[inferred]**. Expect possible different missing methods (26.1 code paths differ); fix the same way.
- No `lwjgl-vulkan` classes needed **[inferred: no Vulkan backend in 26.1]**; including them is harmless.
- Visor/VisorEssentials `0.6.1+mc26.1.2-fabric` (covers 26.1–26.1.2); Fabric loader ≥0.18.4; Fabric API and perf mods for 26.1.x must be looked up (no port has a list). Sodium: same MobileGlues problem expected **[inferred]**, leave it out at first.
- OpenXR binding: same 3.4.1 split as 26.2 (Visor's 26.1 Stonecutter target uses LWJGL 3.4.1 **[read]**).
- Unobfuscated jars (P8): Fabric handles it; QCP/NEC show 26.2 Fabric mods load.
- Neither port supports 26.1.x: every `"26.2"` literal in NEC (`InstanceHandler`, `Installer.refreshLaunchCompatibilityAssets`, `MinecraftInstances`) must become a table lookup.

### 5.2 26.2
- Everything in 5.1, plus: `lwjgl-vulkan` Java classes on the classpath; keep OpenGL (do **not** pass `--graphicsBackend vulkan`; optionally write `preferredGraphicsBackend` to `options.txt`, VERSION_MATRIX P6); Fabric loader ≥0.19.5; Visor `0.6.1+mc26.2-fabric`.

### 5.3 26.3
- **Neither port touched SDL3, LWJGL 3.4.3 or 26.3** (searched both repos for `sdl`, `lwjgl-sdl`, `26.3`) **[read]**.
- Needs: an Android `lwjgl-sdl` equivalent of the GLFW stub (`jre_lwjgl3glfw/.../GLFW.java`, `CallbackBridge`, `input_bridge_v3.c`, `egl_bridge.c`), LWJGL 3.4.3 jars/natives or another patched core, JVM args `-XX:StackShadowPages=32 --add-exports java.base/jdk.internal.misc=ALL-UNNAMED`, Visor `0.6.1+mc26.3-fabric`.
- Before writing an SDL shim, check whether FCL or other Android launchers already ship one for 26.3 **[unverified: not checked]**.

## 6. Implementation plan (ordered)

Each step: one branch, one draft PR, CI build (`Build Pojlib`, `Build Quest APK`), then a device test by the User where marked. Keep 1.20.1 working after every step.

**Step 0. Download robustness (independent of 26.x).**
- Port QCP `9361b33` (only the `DownloadUtils.java`/`StreamDL.java` parts) and `bd3d402` into `Pojlib/src/main/java/pojlib/util/download/`. Reconcile with PR #6's asset fix.
- Why first: same bug class as our first-install asset failure; needed for a 49–61 MB JRE download.
- Device test: fresh 1.20.1 install.

**Step 1. Version table + per-version runtime selection (P0/P3 plumbing).**
- `Pojlib/src/main/java/pojlib/install/VersionInfo.java`: add `javaVersion` (NEC `6492b18`).
- `Installer.installJVM`: per-version selection modelled on NEC, but install to `runtimes/JRE-22` and `runtimes/JRE-25` side by side (no wipe on switch); write a marker with the SHA-256; pin each JRE by URL + SHA-256 (VERSION_MATRIX P3). `JREUtils.relocateLibPath`/`setJavaEnvironment`/`getJavaArgs`/`prelaunchCheck`/`initJavaRuntime` take the selected runtime dir (NEC's `prelaunchCheck` ordering and dlopen changes).
- `FabricMeta.java`: NEC's `getVersion` + fixed `getLatestVersion`; loader pin from the version table.
- Exit: 1.20.1 still installs and launches on JRE 22 (device test).

**Step 2. Renderer: MobileGlues per instance (P2).**
- From upstream `QuestCraftPlusPlus/Pojlib` @ `0e22a02` (same files both ports use): `src/main/jniLibs/arm64-v8a/libmobileglues.so`, `libmobileglues_info_getter.so` (Git LFS; upload to our LFS as we did for the 13 natives), the MobileGlues branch of `JREUtils.loadGraphicsLibrary`/`setJavaEnvironment` (`POJLIB_RENDERER`, `MG_DIR_PATH`), and the matching `egl_bridge.c` changes. Diff 6.0.0 → 6.0.1 for these files only; keep coah80's Unity 6 `UnityPlayerActivity`.
- Renderer chosen from the version table: `libltw` for ≤1.21.x until MobileGlues passes 1.20.1, MobileGlues for 26.x.
- Exit: 1.20.1 renders in VR on MobileGlues (device test). If Visor shows a blank right eye, apply step 7's per-eye fix.

**Step 3. Java 25 runtime spike.**
- Point the 25 entry at QCP `JRE25.zip` (SHA-256 `221acb8e…a4f1`) for testing only.
- JVM args: NEC's Java ≥25 block and LWJGL libname props (`JREUtils.java` `addJava25CompatibilityArgs`, `addLwjglCompatibilityArgs`, ZGC only for <25), QCP heap cap (`cc7c7ce`), and P5: append `arguments.jvm` strings from the version JSON (skip rule-gated and `${…}` entries we already set).
- If the JVM crashes at start, port NEC's launch thread (`JREUtils.launchJavaVM`) and `jre_launcher.c` signal-catcher removal; then try NEC's `JRE-25.zip` as the second binary.
- Exit: a 26.2 vanilla instance (no VR mods) reaches the main menu on the flat screen or logs a clear LWJGL error (device test).

**Step 4. LWJGL 3.3.3 + QCP patches.**
- Copy from `MisterEnglishDev/QuestCraftPlus-Pojlib` @ `a0788606`: `jre_lwjgl3glfw/libs/lwjgl.jar`, `jre_lwjgl3glfw/libs/lwjgl-stb.jar`, the `GLFW.java` additions (diff of `9361b33` and `c98b70a`, merged into our `GLFW.java`), the six `GLFW*Callback(I).java` placeholders, and the `jre_lwjgl3glfw/build.gradle` `META-INF/versions/**` exclusion. Rebuild `src/main/assets/lwjgl/lwjgl-glfw-classes.jar` and bump `version`.
- Commit the ASM patch sources we write for any further gaps (QCP did not publish theirs).
- Exit: 26.2 vanilla reaches the main menu and loads a world (device test).

**Step 5. Visor/AtumVR 26.x Quest build with the 3.4.1 OpenXR split.**
- From `MisterEnglishDev/QuestCraftPlus-VivecraftMod` @ `b1faa22a`: `libs/lwjgl-openxr-3.4.1-patched.jar` (`09fb9e39`) and `libs/questcraft-android-openxr-extras.jar` (`5ecbd351` version, no `XRCapabilities`). Verify both jars' contents against PORTING_NOTES before use.
- `Visor/visor-fabric/build.gradle`: for 26.x targets replace `lwjgl-openxr:${lwjgl_version}` with the two jars; **drop `natives-linux-arm64`** (QCP `8935cccb`); make sure `lwjgl-vulkan` classes are included for 26.2 (line 18 is conditional today).
- `AtumVR/atumvr-api/build.gradle`: a 26.x flavour compiling against the same split (≤1.21.x keeps `lwjgl-openxr-quest-3.3.3.jar`).
- Port the Android provider pattern (Android loader init, `XrInstanceCreateInfoAndroidKHR`, GLES graphics binding) the same way coah80 did for 1.20.1 (P1); QCP's `DeviceCompat.Mobile` is a reference for the call order, not code to copy (licence, §8).
- Bump the Visor `mod_version` (or jar name) whenever nested jars change (Fabric cache, QCP `a5dfd2f`).
- Exit: 26.2 + Visor reaches VR in-world with head and controller tracking (device test).

**Step 6. 26.2 manifest and defaults.**
- Version-table entry for 26.2: Java 25, MobileGlues, Fabric loader ≥0.19.5, Fabric API (QCP's `0.158.0+26.2` or newer that Visor accepts), Visor/VE `0.6.1+mc26.2`, QCP's perf list minus Sodium (§2.8), each with SHA-1.
- `Pojlib/src/main/assets/options.txt`: QCP `a078860` value ranges for 26.x instances.
- Optional: NEC's bundled-manifest/`asset://` mechanism for offline dev builds.
- Exit: full per-version hardware checklist (VERSION_MATRIX §5) on 26.2.

**Step 7. 26.2 hardening (only when the symptom appears).**
- Blank right eye: per-eye swapchains as in QCP `1abde61d` (port the idea into AtumVR's swapchain code).
- OSHI crash/hang: NEC `src/hacks/java/oshi/*`, `OshiAndroidShim.jar`, fake `/proc` (`JREUtils.ensureOshiLinuxPaths`).
- Audio failure: NEC OpenAL config.
- OpenXR session conflict at handoff: compare our `launcherXRStopped` wait with NEC's `vloader.cpp` + `InstanceButton.cs`.

**Step 8. 26.1.2 → 26.1.1 → 26.1.**
- Table entries (Java 25, MobileGlues, loader ≥0.18.4, Visor `0.6.1+mc26.1.2`, mod list looked up fresh). Replace remaining `"26.2"` literals. Expect new LWJGL method gaps; patch per step 4.
- Exit: hardware checklist per version.

**Step 9. Own JRE 25 (before any public APK).**
- Build from `FCL-Team/Android-OpenJDK-Build` branch `Build_JRE_25` (VERSION_MATRIX §2) or obtain QCP's/NEC's sources; publish the build recipe; switch the pin; re-run the 26.2 checklist.

**Step 10 (optional). Vulkan path for 26.2.**
- Only if OpenGL hits a hard limit. Sources: NEC `jre_lwjgl3glfw/base/lwjgl-fcl-vulkan-3.3.6.jar` + `build.gradle`, the FCL natives, all `egl_bridge.c` exports in §2.6, `UnityPlayerActivity` `SurfaceView`, `Android.mk` Vulkan flag, and an AtumVR Vulkan binding modelled on NEC's `MobileVulkan` + `VulkanHelper` fixes. Needs the FCL provenance check (§8) first.

**Step 11. 26.3 (SDL3) spike.**
- Research first (§5.3). Then an `lwjgl-sdl` stub + LWJGL 3.4.3 (or patched core), Visor `0.6.1+mc26.3`, 26.3 JVM args. Last.

## 7. Risks

1. **R1: Java 25 runtime (provenance, licence, stability).** Two binaries, neither with published source or build recipe. QCP's published APK predates its JRE 25 commit, so it is unconfirmed which JRE the User's working install uses; confirm the QCP APK/build the User runs. NEC needed launch-thread and signal changes that QCP did not. Mitigation: steps 3 and 9; pin by SHA-256.
2. **R2: Renderer switch to MobileGlues.** Touches every version and must be merged into coah80's Unity 6 Pojlib. Known MobileGlues issues: Sodium shaders (QCP `341ae41`), a possible array-layer swapchain bug (QCP `1abde61d`, unverified). Visor's own renderer needs (FBO/texture formats) are untested on MobileGlues. Mitigation: per-instance renderer, 1.20.1 regression test in step 2.
3. **R3: Mixed LWJGL generations at runtime.** Minecraft and Visor compiled against 3.4.1 run on a 3.3.3 core with byte-patched classes, and OpenXR 3.4.1 classes run on that core. Each missing method appears as a `NoSuchMethodError`; Visor/AtumVR may hit gaps Vivecraft never touched. QCP's patch scripts are unpublished. Mitigation: commit our patch sources; NEC's 3.3.6 base is the fallback.

Other risks:
- Visor's Fabric build bundles `lwjgl-openxr:natives-linux-arm64` (`Visor/visor-fabric/build.gradle` line 15); on Android it loads a glibc `.so` and crashes (QCP `8935cccb`).
- Fabric nested-jar caching hides jar fixes unless `mod_version` changes.
- NEC natives are Git LFS objects; fetching them via our token returned 403, so availability is **[unverified]**.
- NEC's repo has unfetchable gitlinks, logs, binaries and a cracked-launcher reference; copy only named files.
- Fabric loader pins: NEC's 0.19.3 is below Visor's 0.19.5 minimum.
- APK size if JREs/mods are bundled (NEC's AAR is 86 MB).
- 26.3 has no reference implementation at all.

## 8. Licensing

Neither `MisterEnglishDev/QuestCraftPlus` nor `necat101/questcraft-26.2-experiment` has a top-level licence file. The code inside them does carry licences:

| Component | Where | Licence | Notes |
|---|---|---|---|
| Pojlib (both forks) | QCP-Pojlib root `LICENSE`; NEC `Pojlib/LICENSE` | LGPL-3.0 | Both authors' Pojlib changes are LGPL-3.0 derivative work. Reuse is allowed under LGPL-3.0; keep notices, mark modified files, credit the authors. |
| Vivecraft forks | QCP-VivecraftMod `LICENSE.md`; NEC `Vivecraft-26.2-Quest/LICENSE.md` | LGPL-3.0 (+ listed components) | We do not ship Vivecraft. **Do not paste Vivecraft code into Visor (MPL-2.0) or AtumVR (Apache-2.0):** an LGPL file would have to stay LGPL. Reimplement from the OpenXR spec, or keep any ported file as a separate LGPL file with its notice. |
| QCXR-XR-Wrapper | NEC `QCXR-XR-Wrapper/LICENSE` | LGPL-3.0 text | Upstream README shows a GPLv3 badge; the file is LGPL-3.0. Discrepancy noted. |
| LWJGL jars/natives (Pojav 3.3.3, official 3.4.1, QuestCraft's 13 OpenXR classes + `XRCapabilities`, QCP byte-patched jars) | various | BSD-3-Clause | Keep the LWJGL licence text. Byte-patched classes stay BSD-3. |
| FCL LWJGL 3.3.6 jar + natives (NEC) | `jre_lwjgl3glfw/base/`, `jniLibs/` | Sources carry LWJGL BSD headers **[read: `.tools/fcl-lwjgl-source-20260718/LWJGL-Pojav/.../GLFW.java`]**; FCL itself is GPL-3.0 | Confirm the licence of FCL's LWJGL fork before shipping (step 10 only). |
| MobileGlues (`libmobileglues*.so`) | upstream Pojlib 6.0.1 | LGPL-2.1 (`MobileGL-Dev/MobileGlues`) | Ship notice; keep it a replaceable shared library. Replaces `libltw.so` (PolyForm Shield) for 26.x. |
| OpenJDK JRE 25 binaries | QCP release, NEC asset | GPL-2.0 with Classpath Exception (labs-openjdk repo reports GPL-2.0) | Redistributing a binary requires offering the corresponding source. Neither port publishes it, so neither binary is fit for a public APK (step 9). |
| shaderc, SPIRV-Cross | NEC natives | Apache-2.0 **[memory]** | Vulkan phase only. |
| Vulkan Memory Allocator (inside `liblwjgl_vma.so`) | NEC natives | MIT **[memory]** | Vulkan phase only. |
| OpenXR loader (`libopenxr_loader.so`) | Pojlib | Apache-2.0 **[memory]** | Already shipped. |
| OSHI shim (NEC `src/hacks/java/oshi/*`) | NEC | Inside NEC's Pojlib (LGPL-3.0); OSHI itself is MIT **[memory]** | Original NEC work. |

**Original work that needs attribution (and, as a courtesy, a heads-up or permission):**
- QCP (MisterEnglishDev): the OpenXR split and patched `XRCapabilities` jar idea and jars; the Vivecraft merge and `DeviceCompat.Mobile` rewrite; per-eye swapchains; Pojlib download fixes, JRE 25 marker/install, GLFW 3.4 additions, IME placeholders, LWJGL byte patches, heap cap, option fixes; the `JRE25.zip` build. Some commits are co-authored with Claude Sonnet 5 (commit trailers).
- NEC (necat101 / "Makhi Burroughs"): per-version Java selection, Java 25 JVM arg set, launch thread, OSHI shim and fake `/proc`, OpenAL config, `egl_bridge.c` Vulkan/FCL exports, `vloader.cpp` handoff, Unity handoff, `SurfaceView`, FabricMeta fix, bundled manifests, Vivecraft `MobileVulkan` and gamma fixes, CLI build script.
- Record both in `THIRD_PARTY_NOTICES.md` when the first file is copied, with repo, commit and licence.

## 9. Questions for the User

1. Which QCP build do you play on (APK version, or where it came from)? This decides whether QCP's `JRE25.zip` is the proven runtime (R1).
2. OK to switch 26.x (and later everything) to MobileGlues, with `libltw.so` kept only for ≤1.21.x until MobileGlues passes 1.20.1?
3. OK to start Phase 4 with 26.2 instead of 26.1?
4. Should we contact MisterEnglishDev and necat101 before copying their files (licence allows it under LGPL-3.0; courtesy and their help with unpublished patch scripts and JRE sources)?
5. Vulkan (step 10): wanted at all, or OpenGL only?
