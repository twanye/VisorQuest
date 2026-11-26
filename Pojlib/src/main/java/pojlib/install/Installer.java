package pojlib.install;

import android.app.Activity;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import pojlib.APIHandler;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.install.VisorVersionTable.Artifact;
import pojlib.install.VisorVersionTable.VersionEntry;
import pojlib.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import pojlib.util.json.ProjectInfo;

//This class reads data from a game version json and downloads its contents.
//This works for the base game as well as mod loaders
public class Installer {

    private static final String[] REQUIRED_JRE_FILES = {
            "bin/java",
            "release",
            "lib/libjli.so",
            "lib/libjava.so",
            "lib/libverify.so",
            "lib/libnet.so",
            "lib/libnio.so",
            "lib/libawt.so",
            "lib/libawt_headless.so",
            "lib/libfreetype.so",
            "lib/libfontmanager.so",
            "lib/server/libjvm.so"
    };

    public static synchronized void installJVM(Activity activity) throws IOException {
        Logger.getInstance().appendToLog("Checking JRE");
        File runtimes = new File(activity.getFilesDir(), "runtimes");
        File jre = new File(runtimes, "JRE");
        File installing = new File(runtimes, "JRE.installing");
        File jreZip = new File(runtimes, "JRE.zip.download");
        String jreURL = "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE.zip";

        if (isJreValid(jre)) {
            Logger.getInstance().appendToLog("JRE installed");
            return;
        }

        Logger.getInstance().appendToLog(jre.exists() ? "JRE incomplete, reinstalling" : "Installing JRE");
        FileUtils.forceMkdir(runtimes);
        FileUtils.deleteQuietly(installing);
        FileUtils.deleteQuietly(jreZip);

        try {
            DownloadUtils.downloadFile(jreURL, jreZip);
            DownloadManager.reset();
            FileUtil.unzipArchiveChecked(jreZip.getPath(), installing.getPath());
            Files.copy(
                    Paths.get(activity.getApplicationInfo().nativeLibraryDir, "libawt_xawt.so"),
                    Paths.get(installing.getPath(), "lib", "libawt_xawt.so"),
                    StandardCopyOption.REPLACE_EXISTING
            );

            if (!isJreValid(installing)) {
                throw new IOException("Downloaded JRE is incomplete");
            }

            FileUtils.deleteQuietly(jre);
            Files.move(installing.toPath(), jre.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Logger.getInstance().appendToLog("JRE installed");
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Failed to install JRE: " + e.getMessage());
            throw e;
        } finally {
            FileUtils.deleteQuietly(installing);
            FileUtils.deleteQuietly(jreZip);
            DownloadManager.reset();
        }
    }

    private static boolean isJreValid(File jre) {
        if (!jre.isDirectory()) {
            return false;
        }
        for (String path : REQUIRED_JRE_FILES) {
            File file = new File(jre, path);
            if (!file.isFile() || file.length() == 0) {
                return false;
            }
        }
        return true;
    }

    // Will only download client if it is missing, however it will overwrite if sha1 does not match the downloaded client
    // Returns client classpath
    public static CompletableFuture<String> installClient(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            Logger.getInstance().appendToLog("Checking Client");

            File clientFile = new File(gameDir + "/versions/" + minecraftVersionInfo.id + "/client.jar");

            try {
                for (int i = 0; i < 5; i++) {
                    if (i == 4)
                        throw new RuntimeException("Client download failed after 5 retries");

                    if (!clientFile.exists()) {
                        DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile);
                    } else if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) {
                        clientFile.delete();
                        DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile);
                    }

                    // Check if the downloaded client matches the expected SHA1 hash
                    if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) {
                        Logger.getInstance().appendToLog("Client downloaded");
                        return clientFile.getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                Logger.getInstance().appendToLog("Failed to download client: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        });
    }

    // Will only download library if it is missing, however it will overwrite if sha1 does not match the downloaded library
    // Returns the classpath of the downloaded libraries
    public static CompletableFuture<String> installLibraries(VersionInfo versionInfo, String gameDir) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            Logger.getInstance().appendToLog("Checking Libraries for: " + versionInfo.id);
            StringJoiner classpath = new StringJoiner(File.pathSeparator);

            for (VersionInfo.Library library : versionInfo.libraries) {
                if (library.name.contains("lwjgl") || (library.name.contains("org.ow2.asm")) & !versionInfo.id.contains("fabric")) {
                    continue;
                }
                for (int i = 0; i < 5; i++) {
                    if (i == 4)
                        throw new RuntimeException(String.format("Library download of %s failed after 5 retries", library.name));

                    File libraryFile;
                    String sha1;

                    //Null means mod lib, otherwise vanilla lib
                    try {
                        if (library.downloads == null) {
                            String path = parseLibraryNameToPath(library.name);
                            libraryFile = new File(gameDir + "/libraries/", path);
                            sha1 = APIHandler.getRaw(library.url + path + ".sha1");
                            if (!libraryFile.exists()) {
                                Logger.getInstance().appendToLog("Downloading: " + library.name);
                                DownloadUtils.downloadFile(library.url + path, libraryFile);
                            }
                        } else {
                            VersionInfo.Library.Artifact artifact = library.downloads.artifact;
                            libraryFile = new File(gameDir + "/libraries/", artifact.path);
                            sha1 = artifact.sha1;
                            if (!libraryFile.exists()) {
                                Logger.getInstance().appendToLog("Downloading: " + library.name);
                                DownloadUtils.downloadFile(artifact.url, libraryFile, artifact.size);
                            }
                        }
                        if (DownloadUtils.compareSHA1(libraryFile, sha1)) {
                            classpath.add(libraryFile.getAbsolutePath());
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Add our GLFW
            classpath.add(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");
            // DNS SRV Resolver fix
            classpath.add(Constants.USER_HOME + "/hacks/ResConfHack.jar");

            Logger.getInstance().appendToLog("Libraries installed");
            return classpath.toString();
        });
    }

    //Only works on minecraft, not fabric, quilt, etc...
    //Will only download asset if it is missing
    public static CompletableFuture<String> installAssets(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            Logger.getInstance().appendToLog("Checking assets");
            JsonObject assets = APIHandler.getFullUrl(minecraftVersionInfo.assetIndex.url, JsonObject.class);

            for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
                VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
                DownloadManager.addTotalBytes(asset.size);
            }

            ThreadPoolExecutor tp = new ThreadPoolExecutor(8, 8, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

            for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
                AsyncDownload thread = new AsyncDownload(entry, gameDir);
                tp.execute(thread);
            }

            tp.shutdown();
            try {
                while (!tp.awaitTermination(100, TimeUnit.MILLISECONDS)) ;
            } catch (InterruptedException e) {
                Logger.getInstance().appendToLog("Download thread interrupted" + e.getMessage());
            }

            File indexJson = new File(gameDir + "/assets/indexes/" + minecraftVersionInfo.assets + ".json");
            if (!indexJson.exists()) {
                try {
                    DownloadUtils.downloadFile(minecraftVersionInfo.assetIndex.url, indexJson);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return new File(gameDir + "/assets").getAbsolutePath();
        });
    }

    public static void moveLocalAssets(Activity activity, MinecraftInstances.Instance instance) throws IOException {
        File config = new File(instance.gameDir, "config");
        FileUtil.ensureDirectory(config);
        FileUtils.writeByteArrayToFile(new File(config, "sodium-options.json"), FileUtil.loadFromAssetToByte(activity, "sodium-options.json"));
        FileUtils.writeByteArrayToFile(new File(config, "smoothboot.json"), FileUtil.loadFromAssetToByte(activity, "smoothboot.json"));
        FileUtils.writeByteArrayToFile(new File(config, "immediatelyfast.json"), FileUtil.loadFromAssetToByte(activity, "immediatelyfast.json"));
        FileUtils.writeByteArrayToFile(new File(config, "moreculling.toml"), FileUtil.loadFromAssetToByte(activity,"moreculling.toml"));
        FileUtils.writeByteArrayToFile(new File(config, "modernfix-mixins.properties"), FileUtil.loadFromAssetToByte(activity,"modernfix-mixins.properties"));
        FileUtils.writeByteArrayToFile(new File(instance.gameDir, "options.txt"), FileUtil.loadFromAssetToByte(activity, "options.txt"));
        FileUtils.writeByteArrayToFile(new File(instance.gameDir, "servers.dat"), FileUtil.loadFromAssetToByte(activity, "servers.dat"));
        FileUtils.writeByteArrayToFile(new File(config, "vivecraft-client-config.json"), FileUtil.loadFromAssetToByte(activity, "vivecraft-client-config.json"));
        installLauncherAssets(activity);
    }

    public static void installLauncherAssets(Activity activity) throws IOException {
        File hacks = new File(Constants.USER_HOME, "hacks");
        FileUtil.ensureDirectory(hacks);
        FileUtils.writeByteArrayToFile(new File(hacks, "ResConfHack.jar"), FileUtil.loadFromAssetToByte(activity, "hacks/ResConfHack.jar"));
        FileUtils.writeByteArrayToFile(new File(hacks, "resolv.conf"), FileUtil.loadFromAssetToByte(activity, "hacks/resolv.conf"));
    }

    public static void installVisor(Activity activity, MinecraftInstances.Instance instance) throws IOException {
        VersionEntry version = VisorVersionTable.find(activity, instance.versionName);
        if (version == null) {
            throw new IOException("Visor QuestCraft does not support Minecraft " + instance.versionName);
        }

        applyQuestDefaults(instance);
        File mods = new File(instance.gameDir, "mods");
        mods.mkdirs();
        for (Artifact artifact : version.downloadedMods) {
            installDownloadedMod(mods, artifact);
        }

        // Preserve the original Vivecraft-to-Visor cleanup behaviour.
        File[] existing = mods.listFiles();
        if (existing != null) {
            for (File file : existing) {
                String name = file.getName().toLowerCase();
                if (name.contains("vivecraft")
                        || name.equals("visor.jar")
                        || name.equals("visoressentials.jar")
                        || name.contains("moreculling")
                        || name.contains("noisium")
                        || name.contains("badoptimizations")
                        || name.contains("chunky")
                        || name.contains("c2me")
                        || name.contains("voxy")) {
                    file.delete();
                }
            }
        }

        writeBundledArtifact(activity, mods, version.visor);
        writeBundledArtifact(activity, mods, version.visorEssentials);
        for (Artifact artifact : version.bundledMods) {
            writeBundledArtifact(activity, mods, artifact);
        }

        ArrayList<ProjectInfo> projects = new ArrayList<>();
        if (instance.extProjects != null) {
            projects.addAll(Arrays.asList(instance.extProjects));
        }
        projects.removeIf(project -> project.slug.equalsIgnoreCase("Vivecraft")
                || project.slug.equalsIgnoreCase("Visor")
                || project.slug.equalsIgnoreCase("VisorEssentials")
                || project.slug.equalsIgnoreCase("MoreCulling")
                || project.slug.equalsIgnoreCase("Noisium")
                || project.slug.equalsIgnoreCase("BadOptimizations")
                || project.slug.equalsIgnoreCase("Chunky")
                || project.slug.equalsIgnoreCase("C2ME")
                || project.slug.equalsIgnoreCase("Voxy"));

        projects.add(toProjectInfo(version.visor));
        projects.add(toProjectInfo(version.visorEssentials));
        for (Artifact artifact : version.bundledMods) {
            projects.add(toProjectInfo(artifact));
        }
        instance.extProjects = projects.toArray(new ProjectInfo[0]);
    }

    private static void writeBundledArtifact(Activity activity, File mods, Artifact artifact) throws IOException {
        byte[] bytes = FileUtil.loadFromAssetToByte(activity, artifact.assetPath);
        if (bytes == null) {
            throw new IOException("Unable to read bundled mod asset: " + artifact.assetPath);
        }
        String actualSha1 = DigestUtils.sha1Hex(bytes);
        if (!artifact.sha1.equalsIgnoreCase(actualSha1)) {
            throw new IOException("Bundled mod SHA-1 mismatch for " + artifact.artifactName);
        }
        FileUtils.writeByteArrayToFile(new File(mods, artifact.artifactName), bytes);
    }

    private static void installDownloadedMod(File mods, Artifact artifact) throws IOException {
        File target = new File(mods, artifact.artifactName);
        if (target.isFile() && DownloadUtils.compareSHA1(target, artifact.sha1)) {
            return;
        }

        File update = new File(mods, artifact.artifactName + ".update");
        FileUtils.deleteQuietly(update);
        try {
            DownloadUtils.downloadFile(artifact.url, update, artifact.size);
            if (!DownloadUtils.compareSHA1(update, artifact.sha1)) {
                throw new IOException("Downloaded mod SHA-1 mismatch for " + artifact.artifactName);
            }
            Files.move(update.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            FileUtils.deleteQuietly(update);
        }
    }

    private static ProjectInfo toProjectInfo(Artifact artifact) {
        ProjectInfo project = new ProjectInfo();
        project.slug = artifact.slug;
        project.version = artifact.version;
        project.download_link = artifact.downloadLink;
        if (artifact.type != null && !artifact.type.trim().isEmpty()) {
            project.type = artifact.type;
        }
        return project;
    }

    private static void applyQuestDefaults(MinecraftInstances.Instance instance) throws IOException {
        File marker = new File(instance.gameDir, ".qcx-render-distance-4");
        if (marker.isFile()) {
            return;
        }

        File options = new File(instance.gameDir, "options.txt");
        if (options.isFile()) {
            List<String> lines = Files.readAllLines(options.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("renderDistance:")) {
                    lines.set(i, "renderDistance:4");
                } else if (line.startsWith("simulationDistance:")) {
                    lines.set(i, "simulationDistance:4");
                }
            }
            Files.write(options.toPath(), lines, StandardCharsets.UTF_8);
        }
        marker.createNewFile();
    }

    public static class AsyncDownload implements Runnable {
        private final Map.Entry<String, JsonElement> entry;
        private final String gameDir;
        private final String fileName;

        public AsyncDownload(Map.Entry<String, JsonElement> entry, String gameDir) {
            this.entry = entry;
            this.gameDir = gameDir;
            this.fileName = entry.getKey();
        }

        @Override
        public void run() {
            VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
            String path = asset.hash.substring(0, 2) + "/" + asset.hash;
            File assetFile = new File(gameDir + "/assets/objects/", path);

            for (int i = 0; i < 5; i++) {
                if (i == 4) throw new RuntimeException(String.format("Asset download of %s failed after 5 retries", fileName));

                if (!assetFile.exists()) {
                    Logger.getInstance().appendToLog("Downloading: " + fileName);
                    try {
                        DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + "/" + path, assetFile, 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (DownloadUtils.compareSHA1(assetFile, asset.hash)) {
                    break;
                } else {
                    assetFile.delete();
                }
            }
        }
    }


    //Used for mod libraries, vanilla is handled a different (tbh better) way
    private static String parseLibraryNameToPath(String libraryName) {
        String[] parts = libraryName.split(":");
        String location = parts[0].replace(".", "/");
        String name = parts[1];
        String version = parts[2];

        return String.format("%s/%s/%s/%s", location, name, version, name + "-" + version + ".jar");
    }
}
