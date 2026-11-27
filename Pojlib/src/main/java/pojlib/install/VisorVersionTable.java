package pojlib.install;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the launcher-supported Visor/Minecraft version catalogue from the APK assets. */
public final class VisorVersionTable {
    private static final String ASSET_NAME = "versions.json";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    public int schemaVersion;
    public List<VersionEntry> versions;

    public static final class VersionEntry {
        public String id;
        /**
         * True only when a Quest build of Visor exists for this version. Entries that are false stay
         * in the table (loader pin, planning data) but are hidden from the launcher and refused by
         * Installer.installVisor. Gson leaves a missing field false, so new entries start hidden.
         */
        public boolean questReady;
        public int javaMajor;
        public String fabricLoaderVersion;
        /** Informational: the Fabric API build this version is tested against (installed via mods.json coreMods). */
        public Artifact fabricApi;
        /** Extra mod slugs to remove from the instance for this version (in addition to Vivecraft/Visor/VisorEssentials). */
        public List<String> removeMods;
        public Artifact visor;
        public Artifact visorEssentials;
        public List<Artifact> bundledMods;
        public List<Artifact> downloadedMods;
        public String lwjglBundleId;
    }

    public static final class Artifact {
        public String slug;
        public String artifactName;
        public String version;
        public String assetPath;
        public String url;
        public String sha1;
        public long size;
        public String downloadLink;
        public String type;
    }


    public static VisorVersionTable load(Context context) throws IOException {
        if (context == null) {
            throw new IOException("Cannot load versions.json without an Android context");
        }

        try (InputStream input = context.getAssets().open(ASSET_NAME);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            VisorVersionTable table = new Gson().fromJson(reader, VisorVersionTable.class);
            if (table == null || table.schemaVersion != SUPPORTED_SCHEMA_VERSION || table.versions == null) {
                throw new IOException("Invalid or unsupported " + ASSET_NAME);
            }
            for (VersionEntry entry : table.versions) {
                if (entry == null || isBlank(entry.id) || entry.javaMajor <= 0
                        || isBlank(entry.fabricLoaderVersion) || isBlank(entry.lwjglBundleId)) {
                    throw new IOException("Invalid version entry in " + ASSET_NAME);
                }
                if (entry.bundledMods == null) entry.bundledMods = new ArrayList<>();
                if (entry.downloadedMods == null) entry.downloadedMods = new ArrayList<>();
                if (entry.removeMods == null) entry.removeMods = new ArrayList<>();
                // Visor and VisorEssentials must be installable before an entry can be offered.
                if (entry.questReady
                        && (!isInstallableArtifact(entry.visor) || !isInstallableArtifact(entry.visorEssentials))) {
                    throw new IOException("Quest-ready entry " + entry.id + " in " + ASSET_NAME
                            + " has no installable Visor/VisorEssentials artifact");
                }
            }
            return table;
        }
    }

    public VersionEntry find(String minecraftVersion) {
        if (minecraftVersion == null || versions == null) return null;
        for (VersionEntry entry : versions) {
            if (entry != null && minecraftVersion.equals(entry.id)) return entry;
        }
        return null;
    }

    /** Versions the launcher may offer: only entries with a Quest Visor build. */
    public String[] getSupportedVersions() {
        if (versions == null) return new String[0];
        List<String> supported = new ArrayList<>();
        for (VersionEntry entry : versions) {
            if (entry != null && entry.questReady && !isBlank(entry.id)) supported.add(entry.id);
        }
        return supported.toArray(new String[0]);
    }

    public static VersionEntry find(Context context, String minecraftVersion) throws IOException {
        return load(context).find(minecraftVersion);
    }

    private static boolean isValidArtifact(Artifact artifact) {
        return artifact != null && !isBlank(artifact.slug) && !isBlank(artifact.artifactName)
                && !isBlank(artifact.version) && isSha1(artifact.sha1);
    }

    /** A bundled artifact needs an asset path, a downloaded one a URL; both need a SHA-1. */
    public static boolean isInstallableArtifact(Artifact artifact) {
        return isValidArtifact(artifact) && (!isBlank(artifact.assetPath) || !isBlank(artifact.url));
    }

    private static boolean isSha1(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{40}");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
