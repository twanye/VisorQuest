package pojlib.install;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pojlib.util.Constants;
import pojlib.util.Logger;

/**
 * Per-Minecraft-version launch runtime: which JRE directory, which LWJGL GLFW bundle, which GL
 * renderer library and which extra JVM arguments. Resolved from the optional versions.json fields
 * runtime / renderer / lwjglAssetPath / lwjglVulkanClasses / extraJvmArgs.
 *
 * <p>Entries without those fields (1.20.1, 1.21.1) and unknown versions resolve to {@link #LEGACY},
 * which is exactly the behaviour Pojlib had before this class existed: runtimes/JRE from the
 * QuestCraft JRE release, lwjgl3/lwjgl-glfw-classes.jar, LightThinWrapper (libltw.so).
 */
public final class RuntimeProfile {
    public static final String LEGACY_JRE_DIR = "JRE";
    public static final String LEGACY_JRE_URL =
            "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE.zip";
    public static final String LEGACY_LWJGL_ASSET = "lwjgl/lwjgl-glfw-classes.jar";

    public static final String RENDERER_LTW = "LightThinWrapper";
    public static final String RENDERER_MOBILEGLUES = "MobileGlues";

    public static final RuntimeProfile LEGACY = new RuntimeProfile(
            LEGACY_JRE_DIR, LEGACY_JRE_URL, null, 0, LEGACY_LWJGL_ASSET, false, RENDERER_LTW,
            Collections.emptyList());

    /** Directory name under files/runtimes. */
    public final String jreDirName;
    public final String jreUrl;
    /** Expected SHA-256 of the JRE zip, or null to skip the check (legacy "latest" URL). */
    public final String jreSha256;
    public final long jreSize;
    /** APK asset of the GLFW/LWJGL bundle; copied to USER_HOME/lwjgl3/ under the same file name. */
    public final String lwjglAsset;
    /** Keep org.lwjgl:lwjgl-vulkan (Java classes only, no natives) on the classpath. */
    public final boolean lwjglVulkanClasses;
    public final String renderer;
    public final List<String> extraJvmArgs;

    private RuntimeProfile(String jreDirName, String jreUrl, String jreSha256, long jreSize,
                           String lwjglAsset, boolean lwjglVulkanClasses, String renderer,
                           List<String> extraJvmArgs) {
        this.jreDirName = jreDirName;
        this.jreUrl = jreUrl;
        this.jreSha256 = jreSha256;
        this.jreSize = jreSize;
        this.lwjglAsset = lwjglAsset;
        this.lwjglVulkanClasses = lwjglVulkanClasses;
        this.renderer = renderer;
        this.extraJvmArgs = extraJvmArgs;
    }

    public static RuntimeProfile forVersion(Context context, String minecraftVersion) {
        VisorVersionTable.VersionEntry entry = null;
        try {
            entry = VisorVersionTable.find(context, minecraftVersion);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("versions.json unreadable, using the default runtime: " + e.getMessage());
        }
        return fromEntry(entry);
    }

    public static RuntimeProfile fromEntry(VisorVersionTable.VersionEntry entry) {
        if (entry == null) return LEGACY;

        String dir = LEGACY_JRE_DIR, url = LEGACY_JRE_URL, sha256 = null;
        long size = 0;
        if (entry.runtime != null && !isBlank(entry.runtime.id) && !isBlank(entry.runtime.url)) {
            dir = entry.runtime.id;
            url = entry.runtime.url;
            sha256 = isBlank(entry.runtime.sha256) ? null : entry.runtime.sha256.trim();
            size = entry.runtime.size;
        }
        String lwjgl = isBlank(entry.lwjglAssetPath) ? LEGACY_LWJGL_ASSET : entry.lwjglAssetPath.trim();
        String renderer = RENDERER_MOBILEGLUES.equalsIgnoreCase(entry.renderer) ? RENDERER_MOBILEGLUES : RENDERER_LTW;
        List<String> extra = entry.extraJvmArgs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(entry.extraJvmArgs));
        return new RuntimeProfile(dir, url, sha256, size, lwjgl, entry.lwjglVulkanClasses, renderer, extra);
    }

    /** True when launching on the default runtimes/JRE (QuestCraft JRE 22). */
    public boolean usesDefaultJre() {
        return LEGACY_JRE_DIR.equals(jreDirName);
    }

    public boolean isLegacy() {
        return LEGACY_JRE_DIR.equals(jreDirName) && LEGACY_LWJGL_ASSET.equals(lwjglAsset)
                && RENDERER_LTW.equals(renderer) && !lwjglVulkanClasses && extraJvmArgs.isEmpty();
    }

    public File jreDir(Context context) {
        return new File(new File(context.getFilesDir(), "runtimes"), jreDirName);
    }

    /** Where the LWJGL bundle is installed; legacy keeps lwjgl3/lwjgl-glfw-classes.jar. */
    public File lwjglFile() {
        return new File(Constants.USER_HOME + "/lwjgl3", new File(lwjglAsset).getName());
    }

    public boolean usesMobileGlues() {
        return RENDERER_MOBILEGLUES.equals(renderer);
    }

    /** Value for the POJLIB_RENDERER env var read by egl_bridge.c. */
    public String rendererEnv() {
        return usesMobileGlues() ? "MobileGLUES" : RENDERER_LTW;
    }

    public String graphicsLibrary() {
        return usesMobileGlues() ? "libmobileglues.so" : "libltw.so";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
