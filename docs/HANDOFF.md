# VisorQuest — Developer Handoff (for Claude)

Maintained by Hark (PM). Tucker (twanye) is Executive Director and has final say.
Last updated: 2026-11-23 15:10 CST.

## What VisorQuest is
QuestCraft (standalone Minecraft: Java Edition on Meta Quest: Pojlib + the QCXR Unity launcher) with **Visor** (VR core mod, OpenXR via AtumVR) and **VisorEssentials** replacing Vivecraft. Based on coah80's port (github.com/coah80/questcraft-visor-releases: QuestCraft 6.0.1 rc4 + Visor + VisorEssentials, Minecraft 1.20.1, v1.1.1, July 2026).

## Repo layout (twanye/VisorQuest, private)
- `main`: coah80/QCXR-XR-Wrapper (master) with full history: the Unity launcher / APK.
- Draft PR #1 `import/visor` -> `visor`: coah80/Visor (dev) under `Visor/`.
- Draft PR #2 `import/pojlib` -> `pojlib`: coah80/Pojlib (QuestCraft-6.0.0) under `Pojlib/`.
- Draft PR #3 `import/atumvr` -> `atumvr`: coah80/AtumVR (master) under `AtumVR/`.
- Imports are subtree-style merges with history. Nothing has been built or tested yet.
- Note: history contains `QCXR-3.1.0.rar` (~102 MB), just under GitHub's 100 MiB hard limit.

## Goal: multi-version support
Keep 1.20.1 working and add:
- 1.20.5, 1.20.6
- 1.21.1, 1.21.5, 1.21.7, 1.21.11
- Full 26.x: 26.1, 26.1.1, 26.1.2, 26.2, 26.3
- (Tucker also listed 1.20.7 and 1.20.8; Mojang never released those, so they're on hold pending Tucker.)

Upstream Visor and VisorEssentials (Modrinth, latest 0.6.1, Oct 31 2026) already publish builds for every target above, on Fabric/Forge/NeoForge.

## Known constraints to plan around
- Minecraft 1.20.5+ requires Java 21; QuestCraft 6.0.x's Pojlib runtime was built around 1.20.1 (check the bundled JRE and LWJGL versions).
- 26.x moved to the new versioning scheme; check its renderer/LWJGL and launcher-meta changes.
- Pojlib holds install logic, bundled mods, and native bridges; the QCXR launcher holds the version picker and OpenXR config.
- Visor is MPL-2.0 (older releases LGPL-3.0); VisorEssentials is LGPL-3.0. Keep license headers.

## Your first task (substantial, one pass)
1. Read all three import PRs and main. Write `docs/ARCHITECTURE.md`: how launcher -> Pojlib -> Minecraft + Visor/AtumVR fit together in coah80's build, and exactly where Vivecraft was swapped for Visor.
2. Write `docs/VERSION_MATRIX.md`: for each target version, list the Java runtime, LWJGL, Fabric loader, Visor/VisorEssentials build, Pojlib changes, and launcher changes needed, plus risk (low/med/high).
3. Propose the implementation order (suggested: 1.20.5/1.20.6 first since they force the Java 21 jump, then 1.21.x, then 26.x).
4. Work on your own branch (e.g. `claude/version-matrix`), open a **draft** PR, and do not merge.

## Team rules
- Separate branches, draft PRs, Tucker approves merges and major architecture changes.
- Never claim a feature works unless it was tested on hardware; say "builds" or "untested".
- Hark coordinates and keeps this file current; Hark doesn't write feature code.
