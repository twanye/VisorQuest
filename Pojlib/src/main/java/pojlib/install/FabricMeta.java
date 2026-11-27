package pojlib.install;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

import pojlib.APIHandler;
import pojlib.util.Constants;

public class FabricMeta {

    private static final APIHandler handler = new APIHandler(Constants.FABRIC_META_URL);

    public static class FabricVersion {
        @SerializedName("version")
        public String version;
        @SerializedName("stable")
        public boolean stable;
        @SerializedName("separator")
        public String separator;
    }

    private static class GameVersionEntry {
        @SerializedName("loader")
        public FabricVersion loader;
    }

    public static FabricVersion[] getVersions() {
        return handler.get("versions/loader", FabricVersion[].class);
    }

    private static FabricVersion[] getVersions(String minecraftVersion) {
        GameVersionEntry[] entries = handler.get("versions/loader/" + minecraftVersion, GameVersionEntry[].class);
        if (entries == null) return new FabricVersion[0];

        List<FabricVersion> versions = new ArrayList<>();
        for (GameVersionEntry entry : entries) {
            if (entry != null && entry.loader != null) versions.add(entry.loader);
        }
        return versions.toArray(new FabricVersion[0]);
    }

    /** Returns the pinned version when it exists for this game version; otherwise the newest stable one. */
    public static FabricVersion getVersionForMinecraftVersion(String minecraftVersion, String pinnedVersion) {
        FabricVersion[] compatibleVersions = getVersions(minecraftVersion);
        if (pinnedVersion != null && !pinnedVersion.trim().isEmpty()) {
            for (FabricVersion version : compatibleVersions) {
                if (pinnedVersion.equals(version.version)) return version;
            }
        }
        return getLatestStableVersion(compatibleVersions);
    }

    /** Fallback selector: chooses the newest stable Fabric loader, independent of API response order. */
    public static FabricVersion getLatestVersion() {
        return getLatestStableVersion(getVersions());
    }

    private static FabricVersion getLatestStableVersion(FabricVersion[] versions) {
        if (versions == null) return null;
        FabricVersion latest = null;
        for (FabricVersion candidate : versions) {
            if (candidate == null || !candidate.stable || parseVersion(candidate.version) == null) continue;
            if (latest == null || compareVersions(candidate.version, latest.version) > 0) {
                latest = candidate;
            }
        }
        return latest;
    }

    private static int[] parseVersion(String value) {
        if (value == null) return null;
        String[] parts = value.split("\\.");
        if (parts.length < 3) return null;
        int[] parsed = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                int end = 0;
                while (end < parts[i].length() && Character.isDigit(parts[i].charAt(end))) end++;
                if (end == 0) return null;
                parsed[i] = Integer.parseInt(parts[i].substring(0, end));
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = parseVersion(left);
        int[] rightParts = parseVersion(right);
        if (leftParts == null || rightParts == null) return 0;
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            int leftPart = i < leftParts.length ? leftParts[i] : 0;
            int rightPart = i < rightParts.length ? rightParts[i] : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    public static VersionInfo getVersionInfo(FabricVersion fabricVersion, String minecraftVersion) {
        return handler.get(String.format("versions/loader/%s/%s/profile/json", minecraftVersion, fabricVersion.version), VersionInfo.class);
    }
}
