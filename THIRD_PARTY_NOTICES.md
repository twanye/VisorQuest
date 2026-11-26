# Third-Party Notices for VisorQuest

VisorQuest is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0); see `LICENSE`.
It includes or builds on the third-party open-source components below. Each keeps its own license.

> Status: first draft (2026-11-26), from a license review by Gemini (Research & Legal) checked by Hark. Not formal legal advice. Items marked **[unverified]** need confirming before a public APK release.

## Included in this repository

### 1. QCXR-XR-Wrapper (Unity launcher, `main`)
- Source: https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper, via coah80's fork https://github.com/coah80/QCXR-XR-Wrapper
- License: LGPL-3.0
- Obligations: keep license and copyright notices; publish source for any modifications.

### 2. Pojlib (Java launcher core and native `.so` libraries, `Pojlib/`)
- Source: https://github.com/QuestCraftPlusPlus/Pojlib, via coah80's fork https://github.com/coah80/Pojlib (branch `QuestCraft-6.0.0`)
- License: LGPL-3.0
- Obligations: keep notices; keep library source available; users must be able to replace or relink the LGPL libraries.
- [unverified] Check that no bundled native library is GPL-3.0 without an exception.

### 3. Visor (`Visor/`)
- Source: https://github.com/VisorModStudio/Visor, via coah80's fork https://github.com/coah80/Visor (branch `dev`, July 2026)
- License: upstream Visor moved from LGPL-3.0 to MPL-2.0 in August 2026 (see upstream `RELICENSING.md`; not retroactive). coah80's fork predates that change, so its Quest patches are treated as LGPL-3.0 [inferred].
- Obligations: keep MPL-2.0 notices and file headers on MPL files; modified MPL files stay under MPL-2.0 with source published.
- Note: sending coah80's Quest patches upstream to VisorModStudio under MPL-2.0 needs coah80's consent.

### 4. AtumVR (`AtumVR/`)
- Source: https://github.com/Phoenix-Ra/AtumVR, via coah80's fork https://github.com/coah80/AtumVR
- License: Apache License 2.0
- Obligations: include the Apache-2.0 text, keep copyright headers and any NOTICE file, and mark modified files.

## Downloaded at runtime (not bundled)

Pojlib installs these per instance from a version manifest at first launch. They are not shipped inside the APK.

### 5. Visor and VisorEssentials release jars
- Source: Modrinth (https://modrinth.com/mod/visor, https://modrinth.com/mod/visoressentials)
- License: Visor MPL-2.0. VisorEssentials [unverified]: https://github.com/VisorModStudio/VisorEssentials has no license file GitHub can detect.

### 6. Fabric Loader, Fabric API and performance mods
- License: per each mod's own listing. [unverified] Record in the version manifest.

### 7. Minecraft
- Proprietary, under the Minecraft EULA. Never bundled or redistributed. Players sign in with their own Microsoft account and the game is downloaded from Mojang.

## Planned (Minecraft 26.x, not yet included)

### 8. LWJGL 3 Android OpenXR classes
- Source: https://github.com/LWJGL/lwjgl3 (approach from https://github.com/MisterEnglishDev/QuestCraftPlus)
- License: BSD 3-Clause
- Obligations: keep the BSD-3 copyright notice, conditions and disclaimer.

### 9. OpenJDK 25 runtime for Android
- Source: https://github.com/openjdk/jdk
- License: GPL-2.0 with the Classpath Exception
- Obligations: include the license text and link the exact OpenJDK source used.
- Do **not** copy FCL-Team/Android-OpenJDK-Build's scripts into this repo: that repo has no license. Use our own build script, or ask FCL-Team to add a license.

---
Full license texts (LGPL-3.0, MPL-2.0, Apache-2.0, BSD-3-Clause, GPL-2.0 with Classpath Exception) will go in a `licenses/` directory before the first public APK release.
