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
        public int javaMajor;
        public String fabricLoaderVersion;
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
                        || isBlank(entry.fabricLoaderVersion) || isBlank(entry.lwjglBundleId)
                        || !isValidArtifact(entry.visor) || !isValidArtifact(entry.visorEssentials)) {
                    throw new IOException("Invalid version entry in " + ASSET_NAME);
                }
                if (entry.bundledMods == null) entry.bundledMods = new ArrayList<>();
                if (entry.downloadedMods == null) entry.downloadedMods = new ArrayList<>();
                for (Artifact artifact : entry.bundledMods) {
                    if (!isValidArtifact(artifact) || isBlank(artifact.assetPath)) {
                        throw new IOException("Invalid bundled mod in " + ASSET_NAME + " for " + entry.id);
                    }
                }
                for (Artifact artifact : entry.downloadedMods) {
                    if (!isValidArtifact(artifact) || isBlank(artifact.url)) {
                        throw new IOException("Invalid downloaded mod in " + ASSET_NAME + " for " + entry.id);
                    }
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

    public String[] getSupportedVersions() {
        if (versions == null) return new String[0];
        List<String> supported = new ArrayList<>();
        for (VersionEntry entry : versions) {
            if (entry != null && !isBlank(entry.id)) supported.add(entry.id);
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

    private static boolean isSha1(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{40}");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
