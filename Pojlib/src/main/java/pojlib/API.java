package pojlib;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import org.lwjgl.glfw.CallbackBridge;

import pojlib.account.MinecraftAccount;
import pojlib.util.JREUtils;
import pojlib.util.GsonUtils;
import pojlib.install.Installer;
import pojlib.install.VisorVersionTable;
import pojlib.util.Logger;
import pojlib.util.FileUtil;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.Constants;
import pojlib.account.LoginHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

/**
 * This class is the only class used by the launcher to communicate and talk to pojlib. This keeps pojlib and launcher separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API {

    public static String msaMessage = "";
    public static String model = "Quest";
    private static boolean hasQueried = false;
    private static JsonObject initialResponse;
    public static boolean ignoreInstanceName;
    public static boolean customRAMValue = false;
    public static String profileImage;
    public static String profileName;
    public static String profileUUID;
    public static String memoryValue = "2560";
    public static boolean developerMods;
    public static MinecraftAccount currentAcc;
    public static boolean isDemoMode;
    public static MinecraftInstances.Instance currentInstance;
    private static boolean hasWifi;
    public static boolean advancedDebugger;
    public static boolean gameReady = false;
    public static boolean launcherXRStopped = false;


    /**
     * Add a mod to an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#(Activity, MinecraftInstances, String, String, boolean, String, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @param version Project version
     * @param url Mod download URL
     */
    public static void addExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String fileName, String version, String url, String type) {
        InstanceHandler.addExtraProject(instances, instance, name, fileName, version, url, type);
    }

    public static boolean isDownloadsCompleted() {
        return DownloadManager.downloadsCompleted();
    }

    public static float getDownloadPercentage() {
        return DownloadManager.getPercentComplete();
    }

    /**
     * Check if an instance has a mod
     *
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @return True if the project is already in the instance
     */
    public static boolean hasExtraProject(MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.hasExtraProject(instance, name);
    }

    /**
     * Remove a mod from an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name project name
     * @return True if the project was deleted
     */
    public static boolean removeExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.removeExtraProject(instances, instance, name);
    }

    public static String[] getQCSupportedVersions(Activity ctx) {
        try {
            return VisorVersionTable.load(ctx).getSupportedVersions();
        } catch (IOException | RuntimeException exception) {
            Logger.getInstance().appendToLog("Failed to load supported Visor versions: " + exception);
            return new String[0];
        }
    }

    /**
     * Restore the expected permissions on files inside installed Minecraft instances.
     * Called by the Unity launcher through JNI; the boolean result is part of that API.
     */
    public static boolean fixDataPermissions() {
        try {
            if (Constants.USER_HOME == null) {
                throw new IOException("Pojlib data directory has not been initialized");
            }
            File instancesDirectory = new File(Constants.USER_HOME, "instances");
            if (!instancesDirectory.exists()) {
                return true;
            }
            FileUtil.fixDirectoryPermissions(instancesDirectory);
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            Logger.getInstance().appendToLog("Couldn't fix directory permissions! | " + exception);
            return false;
        }
    }

    /**
     * Loads all instances from the filesystem.
     *
     * @return                  A minecraft instance object
     */
    public static MinecraftInstances loadAll() throws IOException {
        return InstanceHandler.load(Constants.USER_HOME);
    }

    /**
     * Load a specific instance by name
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param name Name of the instance
     * @return The instance, or null if an instance with name does not exist
     */
    public static MinecraftInstances.Instance load(MinecraftInstances instances, String name) {
        return instances.load(name);
    }

    /**
     * Delete an instance
     * NOTE: Only deletes the instance, not the correlated mods for said instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Instance object
     * @return True if it deletes successfully, false otherwise.
     */
    public static boolean deleteInstance(MinecraftInstances instances, MinecraftInstances.Instance instance) throws IOException {
        return InstanceHandler.delete(instances, instance);
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param useDefaultMods    Use QC's default mods for the version (Core mods are automatically included)
     * @param minecraftVersion  The version of minecraft to install
     * @param imageURL          Modpack image url, nullable
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, boolean useDefaultMods, String minecraftVersion, String modLoader, String imageURL) throws IOException {
        return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, useDefaultMods, minecraftVersion, modLoader, imageURL, null);
    }

    /**
     * Creates a new game instance from a mrpack file.
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param imageURL          Modpack image url, nullable
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, String imageURL, String modLoader, String mrpackFile) throws IOException {
        if(ignoreInstanceName) {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, modLoader, mrpackFile, imageURL);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, modLoader, mrpackFile, imageURL);
        }
    }

    /**
     * Update the mods for the selected instance
     *
     * @param instance The instance to update
     */
    public static boolean prelaunch(Activity activity, MinecraftInstances instances, MinecraftInstances.Instance instance) {
        gameReady = false;
        launcherXRStopped = false;
        instance.updateMods(instances);
        try {
            Installer.installVisor(activity, instance);
            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException exception) {
            Logger.getInstance().appendToLog("Failed to install Visor: " + exception);
            return false;
        }
        if (hasConnection(activity)) {
            try {
                JREUtils.prelaunchCheck(activity, instance);
            } catch (IOException | ExecutionException | InterruptedException e) {
                Logger.getInstance().appendToLog("WARN! Instance launch failed!" + e);
                return false;
            }
        } else {
            Logger.getInstance().appendToLog("Skipping prelaunch check due to no wifi connection!");
        }
        DownloadManager.reset();
        return true;
    }

    /**
     * Launch an instance
     *
     * @param activity Android activity object
     * @param account Account object
     * @param instance Instance object from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     */
    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        InstanceHandler.launchInstance(activity, account, instance);
    }

    /**
     * Kill the current instance
     *
     * @param activity Android activity object
     */
    public static void restartLauncher(Activity activity) {
        Logger.getInstance().appendToLog("QuestCraft: Restarting launcher...");
        CallbackBridge.restartUnitySession(activity);
    }

    /**
     * Removes the user account
     *
     * @param activity The base directory where minecraft should be setup
     * @param uuid The uuid of the profile to remove
     * @return True if removal was successful
     */
    public static boolean removeAccount(Activity activity, String uuid) {
        if(currentAcc.uuid.equals(uuid)) {
            currentAcc = null;
        }
        return MinecraftAccount.removeAccount(activity, uuid);
    }

    /**
     * Start the login process
     *
     * @param activity Android activity object
     */
    public static void login(Activity activity, @Nullable String accountUUID)
    {
        if(accountUUID == null) {
            currentAcc = null;
            LoginHelper.login(activity);
            return;
        }

        MinecraftAccount acc = MinecraftAccount.load(activity.getFilesDir() + "/accounts", accountUUID);
        if(acc != null && (acc.expiresOn >= System.currentTimeMillis() || !hasConnection(activity) || acc.isDemoMode)) {
            API.profileImage = MinecraftAccount.getSkinFaceUrl(acc);
            API.profileName = acc.username;
            API.profileUUID = acc.uuid;
            API.isDemoMode = acc.isDemoMode;
            currentAcc = acc;
            return;
        } else if(acc != null && acc.expiresOn < System.currentTimeMillis()) {
            MinecraftAccount refreshed = LoginHelper.refreshAccount(activity, accountUUID);
            if(refreshed != null) {
                API.profileImage = MinecraftAccount.getSkinFaceUrl(refreshed);
                API.profileName = refreshed.username;
                API.profileUUID = refreshed.uuid;
                API.isDemoMode = refreshed.isDemoMode;
                currentAcc = refreshed;
                return;
            }
        }

        LoginHelper.login(activity);
    }

    /**
     * Check if the device has a valid wifi connection
     *
     * @param activity activity object
     * @return true if the device has a valid wifi connection
     */
    public static boolean hasConnection(Context activity) {
        boolean hasNetwork = false;
        ConnectivityManager connManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());

        if(capabilities != null) {
            hasNetwork = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }

        if (hasNetwork) {
            try {
                URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.connect();
                conn.disconnect();
                hasWifi = true;
                return true;
            } catch (Exception e) {
                Logger.getInstance().appendToLog("WARN! Unable to reach Microsoft servers!");
                hasWifi = false;
                return false;
            }
        } else {
            Logger.getInstance().appendToLog("WARN! Device has no wifi connection!");
            hasWifi = false;
            return false;
        }
    }

    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static void unzipSavesFromFolder(MinecraftInstances.Instance instance, String path) {
        try {
            Files.walkFileTree(Paths.get(path), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    installSave(instance, file);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Logger.getInstance().appendToLog("ERROR! Failed to read save import file: " + file + " | " + exc);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        Logger.getInstance().appendToLog("ERROR! Failed to finish save import directory: " + dir + " | " + exc);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            fixDataPermissions();
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to bulk install saves from path: " + path + " | " + e);
        }
    }

    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static void installSave(MinecraftInstances.Instance instance, Path file) {
        try {
            Path outPath = Paths.get(instance.gameDir, "saves");
            Files.createDirectories(outPath);
            FileUtil.unzipArchive(file.toString(), outPath.toString());
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to install save by file " + file + " | " + e);
        }
    }

    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static void mirrorNativesInFolder(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance, String path) {
        try {
            Files.createDirectories(activity.getDataDir().toPath().resolve(instance.instanceName));
            removeNatives(activity, instances, instance);

            Files.walkFileTree(Paths.get(path), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    installNativeFromExisting(activity, instances, instance, file.toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Logger.getInstance().appendToLog("ERROR! Failed to read native import file: " + file + " | " + exc);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        Logger.getInstance().appendToLog("ERROR! Failed to finish native import directory: " + dir + " | " + exc);
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to bulk install natives from path: " + path + " | " + e);
        }
    }

    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static void installNativeFromExisting(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance, String nativePath) {
        try {
            Path nativeDirectory = activity.getDataDir().toPath().resolve(instance.instanceName);
            Files.createDirectories(nativeDirectory);
            Path source = Paths.get(nativePath);
            Path out = nativeDirectory.resolve(source.getFileName());

            Files.copy(source, out);
            if (instance.extraNatives == null || instance.extraNatives.isEmpty()) {
                instance.extraNatives = out.toString();
            } else {
                instance.extraNatives += File.pathSeparator + out;
            }

            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to install native from path: " + nativePath + " | " + e);
        }
    }

    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static void installNative(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance, String downloadPath, String downloadName) {
        try {
            Path nativeDirectory = activity.getDataDir().toPath().resolve(instance.instanceName);
            Files.createDirectories(nativeDirectory);
            Path out = nativeDirectory.resolve(downloadName);

            DownloadUtils.downloadFile(downloadPath, out.toFile());
            if (instance.extraNatives == null || instance.extraNatives.isEmpty()) {
                instance.extraNatives = out.toString();
            } else {
                instance.extraNatives += File.pathSeparator + out;
            }

            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to install native from url: " + downloadPath + " | " + e);
        }
    }

    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static void removeNatives(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance) {
        try {
            Path nativeDirectory = activity.getDataDir().toPath().resolve(instance.instanceName);
            if (Files.exists(nativeDirectory)) {
                Files.walkFileTree(nativeDirectory, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        Logger.getInstance().appendToLog("ERROR! Failed to delete native file: " + file + " | " + exc);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (exc != null) {
                            Logger.getInstance().appendToLog("ERROR! Failed to clean native directory: " + dir + " | " + exc);
                        }
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            instance.extraNatives = "";
            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to delete natives by folder | " + e);
        }
    }

}
