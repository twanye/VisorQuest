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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import pojlib.util.json.ProjectInfo;

//This class reads data from a game version json and downloads its contents.
//This works for the base game as well as mod loaders
public class Installer {

    private static final int ASSET_DOWNLOAD_THREADS = 8;
    private static final int ASSET_DOWNLOAD_ATTEMPTS = 5;
    // Log asset progress every N assets instead of one line per asset: ~3,600 lines
    // on a first install overflow the launcher's log view and it goes blank.
    private static final int ASSET_PROGRESS_INTERVAL = 250;
    private static final AtomicInteger assetsChecked = new AtomicInteger();
    private static volatile int assetsTotal = 0;

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
            if (minecraftVersionInfo.assetIndex == null || minecraftVersionInfo.assetIndex.url == null) {
                throw new CompletionException(new IOException(
                        "Version " + minecraftVersionInfo.id + " has no asset index"));
            }

            JsonObject assets = APIHandler.getFullUrl(minecraftVersionInfo.assetIndex.url, JsonObject.class);
            if (assets == null || !assets.has("objects") || !assets.get("objects").isJsonObject()) {
                throw new CompletionException(new IOException(
                        "Unable to read the asset index from " + minecraftVersionInfo.assetIndex.url));
            }
            JsonObject objects = assets.getAsJsonObject("objects");
            Logger.getInstance().appendToLog("Asset index lists " + objects.size() + " objects");

            for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
                if (asset != null) {
                    DownloadManager.addTotalBytes(asset.size);
                }
            }

            // Failures are collected instead of thrown from the worker threads:
            // an uncaught exception on a pool thread kills the whole app process
            // on Android (no stack trace in latestlog, no launcher error dialog).
            ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
            ThreadPoolExecutor tp = new ThreadPoolExecutor(ASSET_DOWNLOAD_THREADS, ASSET_DOWNLOAD_THREADS,
                    100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            assetsChecked.set(0);
            assetsTotal = objects.size();
            try {
                for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                    tp.execute(new AsyncDownload(entry, gameDir, failures));
                }
                tp.shutdown();
                while (!tp.awaitTermination(100, TimeUnit.MILLISECONDS)) ;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add("asset download interrupted: " + e.getMessage());
                Logger.getInstance().appendToLog("Download thread interrupted", e);
            } finally {
                tp.shutdownNow();
            }

            if (!failures.isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append(failures.size()).append(" asset(s) could not be downloaded:");
                int shown = 0;
                for (String failure : failures) {
                    message.append("\n  ").append(failure);
                    if (++shown >= 10) {
                        message.append("\n  ...");
                        break;
                    }
                }
                Logger.getInstance().appendToLog(message.toString());
                throw new CompletionException(new IOException(message.toString()));
            }

            File indexJson = new File(gameDir + "/assets/indexes/" + minecraftVersionInfo.assets + ".json");
            if (!indexJson.exists()) {
                try {
                    DownloadUtils.downloadFile(minecraftVersionInfo.assetIndex.url, indexJson);
                } catch (IOException e) {
                    Logger.getInstance().appendToLog("Could not write the asset index to " + indexJson, e);
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
        project.type = artifact.type;
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
        private final ConcurrentLinkedQueue<String> failures;

        public AsyncDownload(Map.Entry<String, JsonElement> entry, String gameDir, ConcurrentLinkedQueue<String> failures) {
            this.entry = entry;
            this.gameDir = gameDir;
            this.fileName = entry.getKey();
            this.failures = failures;
        }

        @Override
        public void run() {
            try {
                VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
                if (asset == null || asset.hash == null || asset.hash.length() < 2) {
                    failures.add(fileName + " (no hash in the asset index)");
                    Logger.getInstance().appendToLog("Skipping asset without a hash: " + fileName);
                    return;
                }
                String path = asset.hash.substring(0, 2) + "/" + asset.hash;
                File assetFile = new File(gameDir + "/assets/objects/", path);

                for (int attempt = 1; attempt <= ASSET_DOWNLOAD_ATTEMPTS; attempt++) {
                    if (assetFile.isFile() && DownloadUtils.compareSHA1(assetFile, asset.hash)) {
                        return;
                    }
                    if (assetFile.exists()) {
                        assetFile.delete();
                    }
                    try {
                        DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + "/" + path, assetFile, 0);
                    } catch (IOException e) {
                        Logger.getInstance().appendToLog(
                                "Asset download attempt " + attempt + "/" + ASSET_DOWNLOAD_ATTEMPTS
                                        + " failed for " + fileName, e);
                    }
                }

                if (assetFile.isFile() && DownloadUtils.compareSHA1(assetFile, asset.hash)) {
                    return;
                }
                failures.add(fileName);
                Logger.getInstance().appendToLog("Giving up on asset " + fileName + " after "
                        + ASSET_DOWNLOAD_ATTEMPTS + " attempts");
            } catch (Throwable t) {
                // Never let a worker die with an uncaught exception: on Android that
                // kills the process and the crash never reaches latestlog.
                failures.add(fileName + " (" + t + ")");
                Logger.getInstance().appendToLog("Unhandled error while downloading asset " + fileName, t);
            } finally {
                int checked = assetsChecked.incrementAndGet();
                int total = assetsTotal;
                if (checked % ASSET_PROGRESS_INTERVAL == 0 || checked == total) {
                    Logger.getInstance().appendToLog("Assets checked: " + checked + "/" + total);
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
