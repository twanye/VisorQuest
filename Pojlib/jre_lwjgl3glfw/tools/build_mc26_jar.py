#!/usr/bin/env python3
"""Rebuild src/main/assets/lwjgl/lwjgl-glfw-classes-26.jar (VisorQuest, Minecraft 26.x LWJGL bundle).

Starts from the committed 1.20.1/1.21.1 bundle (lwjgl-glfw-classes.jar) and swaps in only:
  - org/lwjgl/glfw/GLFW*.class and the IME/preedit callback classes, compiled from src/mc26/java
    (javac --release 8, same target as the Gradle toolchain) against libs/ + libs-mc26/;
  - org/lwjgl/system/MemoryUtil.class from libs-mc26/lwjgl.jar (QuestCraftPlus+ c98b70a/b85338d);
  - org/lwjgl/stb/STBImageResize.class from libs-mc26/lwjgl-stb.jar (QuestCraftPlus+ 398d559).
Every other entry stays byte-identical to the 1.20.1 bundle.

Usage: JAVAC=/path/to/javac python3 tools/build_mc26_jar.py   (run from jre_lwjgl3glfw/)
The Gradle task jarMc26 builds an equivalent jar from source during `./gradlew assemble`.
"""
import glob, os, shutil, subprocess, sys, tempfile, zipfile

here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
assets = os.path.join(here, "..", "src", "main", "assets", "lwjgl")
javac = os.environ.get("JAVAC", "javac")

tmp = tempfile.mkdtemp()
src, cls = os.path.join(tmp, "src"), os.path.join(tmp, "cls")
shutil.copytree(os.path.join(here, "src", "main", "java"), src)
os.remove(os.path.join(src, "org", "lwjgl", "glfw", "GLFW.java"))
shutil.copytree(os.path.join(here, "src", "mc26", "java"), src, dirs_exist_ok=True)
cp = [j for j in glob.glob(os.path.join(here, "libs", "*.jar"))
      if os.path.basename(j) not in ("lwjgl.jar", "lwjgl-stb.jar")]
cp += glob.glob(os.path.join(here, "libs-mc26", "*.jar"))
files = [os.path.join(r, f) for r, _, fs in os.walk(src) for f in fs if f.endswith(".java")]
os.makedirs(cls)
subprocess.check_call([javac, "--release", "8", "-nowarn", "-encoding", "UTF-8",
                       "-cp", os.pathsep.join(cp), "-d", cls] + files)

repl = {}
for f in glob.glob(os.path.join(cls, "org", "lwjgl", "glfw", "*.class")):
    b = os.path.basename(f)
    if b == "GLFW.class" or b.startswith("GLFW$") or "Preedit" in b or "IMEStatus" in b:
        repl["org/lwjgl/glfw/" + b] = open(f, "rb").read()
repl["org/lwjgl/system/MemoryUtil.class"] = zipfile.ZipFile(
    os.path.join(here, "libs-mc26", "lwjgl.jar")).read("org/lwjgl/system/MemoryUtil.class")
repl["org/lwjgl/stb/STBImageResize.class"] = zipfile.ZipFile(
    os.path.join(here, "libs-mc26", "lwjgl-stb.jar")).read("org/lwjgl/stb/STBImageResize.class")

old = zipfile.ZipFile(os.path.join(assets, "lwjgl-glfw-classes.jar"))
out_path = os.path.join(assets, "lwjgl-glfw-classes-26.jar")
with zipfile.ZipFile(out_path, "w") as out:
    done = set()
    for info in old.infolist():
        name = info.filename
        if name.startswith("org/lwjgl/glfw/GLFW$") and name not in repl:
            continue
        out.writestr(info, repl.get(name, old.read(info)), compress_type=info.compress_type)
        done.add(name)
    for name, data in sorted(repl.items()):
        if name not in done:
            out.writestr(zipfile.ZipInfo(name, (1980, 2, 1, 0, 0, 0)), data, zipfile.ZIP_DEFLATED)
shutil.rmtree(tmp)
print("wrote", out_path, "replaced/added:", ", ".join(sorted(repl)))
