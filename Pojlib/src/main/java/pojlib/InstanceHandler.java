package pojlib;

import android.app.Activity;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import pojlib.account.MinecraftAccount;
import pojlib.install.FabricMeta;
import pojlib.install.Installer;
import pojlib.install.MinecraftMeta;
import pojlib.install.QuiltMeta;
import pojlib.install.VersionInfo;
import pojlib.install.VisorVersionTable;
import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.VLoader;
import pojlib.util.download.DownloadManager;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ModsJson;
import pojlib.install.RuntimeProfile;
import pojlib.util.json.ProjectInfo;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.json.ModrinthIndexJson;

public class InstanceHandler {
    public static final String MODS = Constants.UPSTREAM_QUESTCRAFT_MANIFEST_BASE_URL + "mods.json";
    public static final String DEV_MODS = Constants.UPSTREAM_QUESTCRAFT_MANIFEST_BASE_URL + "devmods.json";

    public static MinecraftInstances.Instance create(Activity activity, MinecraftInstances instances, String instanceName, String userHome, String modLoader, String mrpackFilePath, String imageURL) {
        File mrpackJson = new File(Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_") + "/setup/modrinth.index.json");

        mrpackJson.getParentFile().mkdirs();
        File setupFile = new File(Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_") + "/setup");
        FileUtil.unzipArchive(mrpackFilePath, setupFile.getPath());

        ModrinthIndexJson index = GsonUtils.jsonFileToObject(mrpackJson.getAbsolutePath(), ModrinthIndexJson.class);
        if(index == null) {
            Logger.getInstance().appendToLog("Couldn't install the modpack with path " + mrpackJson.getAbsolutePath());
            return null;
        }

        return create(activity, instances, instanceName, userHome, false, index.dependencies.minecraft, modLoader, imageURL, (instance) -> {
            if (instance.extProjects == null) {
                instance.extProjects = new ProjectInfo[0];
            }
            ArrayList<ProjectInfo> mods = Lists.newArrayList(instance.extProjects);
            for (ModrinthIndexJson.ModpackFile file : index.files) {
                if (file.path.contains("mods")) {
                    ProjectInfo info = new ProjectInfo();
                    info.slug = file.path
                            .replaceAll(".*/", "")
                            .replaceAll("\\..*", "");
                    info.version = "1.0.0";
                    info.download_link = file.downloads[0];
                    info.type = "mod";
                    if(isCoreMod(instance, info)) {
                        continue;
                    }

                    mods.add(info);
                }
            }
            instance.extProjects = mods.toArray(new ProjectInfo[0]);
            try {
                Files.walkFileTree(Paths.get(setupFile + "/overrides"), new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String filtered = file.toString().replaceAll(setupFile.getAbsolutePath() + "/overrides/", "");
                        File newFile = new File(setupFile.getParentFile().getAbsolutePath() + "/" + filtered);
                        newFile.getParentFile().mkdirs();

                        Files.copy(file, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            instance.updateMods(instances);
            GsonUtils.objectToJsonFile(userHome + "/instances.json", instances);
        });
    }

    //creates a new instance of a minecraft version, install game + mod loader, stores non login related launch info to json
    public static MinecraftInstances.Instance create(Activity activity, MinecraftInstances instances, String instanceName, String gameDir, boolean useDefaultMods, String minecraftVersion, String modLoader, String imageURL, Consumer<MinecraftInstances.Instance> postInstall) {
        File instancesFile = new File(gameDir + "/instances.json");
        if (instancesFile.exists()) {
            for (MinecraftInstances.Instance instance : instances.instances) {
                if (instance.instanceName.equals(instanceName)) {
                    Logger.getInstance().appendToLog("Instance " + instanceName + " already exists! Using original instance.");
                    return instance;
                }
            }
        }

        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        instance.instanceName = instanceName;
        instance.instanceImageURL = imageURL;
        instance.versionName = minecraftVersion;
        instance.gameDir = Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_");
        instance.defaultMods = useDefaultMods;

        File gameDirFile = new File(instance.gameDir);
        if(!gameDirFile.exists()) {
            gameDirFile.mkdirs();
        }

        VersionInfo modLoaderVersionInfo = null;
        switch (modLoader) {
            case "Fabric": {
                String pinnedLoaderVersion = null;
                try {
                    VisorVersionTable.VersionEntry versionEntry = VisorVersionTable.find(activity, minecraftVersion);
                    if (versionEntry != null) pinnedLoaderVersion = versionEntry.fabricLoaderVersion;
                } catch (IOException exception) {
                    Logger.getInstance().appendToLog("Unable to read pinned Fabric loader version: " + exception);
                }

                FabricMeta.FabricVersion fabricVersion = FabricMeta.getVersionForMinecraftVersion(
                        minecraftVersion, pinnedLoaderVersion);
                if (fabricVersion == null) {
                    throw new IllegalStateException("No stable Fabric loader is available for Minecraft " + minecraftVersion);
                }
                modLoaderVersionInfo = FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
                break;
            }
            case "Quilt": {
                QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
                assert quiltVersion != null;
                modLoaderVersionInfo = QuiltMeta.getVersionInfo(quiltVersion, minecraftVersion);
                break;
            }
            case "Forge":
            case "NeoForge": {
                break;
            }
        }

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        instance.mainClass = modLoaderVersionInfo.mainClass;

        // Install minecraft
        VersionInfo finalModLoaderVersionInfo = modLoaderVersionInfo;

        if(instances.instances == null) {
            instances.instances = new MinecraftInstances.Instance[0];
        }

        ArrayList<MinecraftInstances.Instance> instances1 = Lists.newArrayList(instances.instances);
        instances1.add(instance);
        instances.instances = instances1.toArray(new MinecraftInstances.Instance[0]);

        CompletableFuture.supplyAsync(() ->
        {
            try {
                CompletableFuture<String> clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir);
                RuntimeProfile runtime = RuntimeProfile.forVersion(activity, minecraftVersion);
                CompletableFuture<String> minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir, runtime);
                CompletableFuture<String> modLoaderClasspath = Installer.installLibraries(finalModLoaderVersionInfo, gameDir, runtime);
                CompletableFuture<String> assetsFuture = Installer.installAssets(minecraftVersionInfo, gameDir);
                String lwjgl = UnityPlayerActivity.installLWJGL(activity, runtime);

                CompletableFuture<Void> installFuture = CompletableFuture.allOf(clientClasspath, minecraftClasspath, modLoaderClasspath, assetsFuture);
                installFuture.get();

                instance.classpath = clientClasspath.get() + File.pathSeparator + minecraftClasspath.get() + File.pathSeparator + modLoaderClasspath.get() + File.pathSeparator + lwjgl;

                instance.assetsDir = assetsFuture.get();
                Installer.moveLocalAssets(activity, instance);
            } catch (Exception e) {
                // Previously only printed to logcat, which left a half-installed instance with no
                // visible error. Log it so the launcher shows what failed.
                Logger.getInstance().appendToLog("Instance install failed for " + instanceName, e);
            }
            instance.assetIndex = minecraftVersionInfo.assetIndex.id;

            // Write instance to json file. A broken instance is still saved so the next launch
            // can repair it (see repairIfBroken) or the user can delete it.
            instance.updateMods(instances);
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);

            if(postInstall != null)
                postInstall.accept(instance);

            DownloadManager.reset();
            if (isBroken(instance)) {
                Logger.getInstance().appendToLog("Instance " + instanceName + " was not fully installed. Launching it will try to repair it.");
            } else {
                Logger.getInstance().appendToLog("Finished Creating Instance!");
            }
            return null;
        });

        return instance;
    }

    // True when the instance is missing data it needs to launch (for example after an install failed part way)
    public static boolean isBroken(MinecraftInstances.Instance instance) {
        return instance.classpath == null || instance.mainClass == null
                || instance.assetsDir == null || instance.assetIndex == null || instance.gameDir == null;
    }

    // Re-runs the install steps for an instance saved without its launch data and fills in what is missing.
    // Returns true if the instance can be launched afterwards.
    public static boolean repairIfBroken(Activity activity, MinecraftInstances.Instance instance) {
        if (!isBroken(instance)) {
            return true;
        }
        Logger.getInstance().appendToLog("Instance " + instance.instanceName + " is missing launch data, attempting repair");
        try {
            VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(instance.versionName);
            if (minecraftVersionInfo == null) {
                throw new IOException("No version info for Minecraft " + instance.versionName);
            }

            // The mod loader is not stored on the instance; infer it from the main class (QuestCraft default is Fabric)
            VersionInfo modLoaderVersionInfo;
            if (instance.mainClass != null && instance.mainClass.contains("quilt")) {
                QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
                modLoaderVersionInfo = quiltVersion == null ? null : QuiltMeta.getVersionInfo(quiltVersion, instance.versionName);
            } else {
                FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestVersion();
                modLoaderVersionInfo = fabricVersion == null ? null : FabricMeta.getVersionInfo(fabricVersion, instance.versionName);
            }
            if (modLoaderVersionInfo == null) {
                throw new IOException("No mod loader info for Minecraft " + instance.versionName);
            }

            String gameDir = Constants.USER_HOME;
            String clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir).get();
            RuntimeProfile runtime = RuntimeProfile.forVersion(activity, instance.versionName);
            String minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir, runtime).get();
            String modLoaderClasspath = Installer.installLibraries(modLoaderVersionInfo, gameDir, runtime).get();
            String assetsDir = Installer.installAssets(minecraftVersionInfo, gameDir).get();
            String lwjgl = UnityPlayerActivity.installLWJGL(activity, runtime);

            if (instance.classpath == null) {
                instance.classpath = clientClasspath + File.pathSeparator + minecraftClasspath + File.pathSeparator + modLoaderClasspath + File.pathSeparator + lwjgl;
            }
            if (instance.mainClass == null) {
                instance.mainClass = modLoaderVersionInfo.mainClass;
            }
            if (instance.versionType == null) {
                instance.versionType = minecraftVersionInfo.type;
            }
            if (instance.assetsDir == null) {
                instance.assetsDir = assetsDir;
            }
            if (instance.assetIndex == null && minecraftVersionInfo.assetIndex != null) {
                instance.assetIndex = minecraftVersionInfo.assetIndex.id;
            }
        } catch (Exception e) {
            Logger.getInstance().appendToLog("Instance repair failed for " + instance.instanceName, e);
            return false;
        }

        boolean repaired = !isBroken(instance);
        Logger.getInstance().appendToLog(repaired
                ? "Instance " + instance.instanceName + " repaired"
                : "Instance " + instance.instanceName + " is still missing launch data after repair");
        return repaired;
    }

    // Load an instance from json
    public static MinecraftInstances load(String gameDir) {
        MinecraftInstances instances;
        try {
            instances = GsonUtils.jsonFileToObject(gameDir + "/instances.json", MinecraftInstances.class);
        } catch (Exception e) {
            instances = new MinecraftInstances();
            instances.instances = new MinecraftInstances.Instance[0];
        }
        if(instances == null) {
            instances = new MinecraftInstances();
            instances.instances = new MinecraftInstances.Instance[0];
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
        }

        return instances;
    }

    public static void addExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String fileName, String version, String url, String type) {
        ProjectInfo info = new ProjectInfo();
        info.slug = name;
        info.fileName = fileName;
        info.download_link = url;
        info.version = version;
        info.type = type;

        if(instance.extProjects == null) {
            instance.extProjects = new ProjectInfo[0];
        }

        ArrayList<ProjectInfo> mods = Lists.newArrayList(instance.extProjects);
        mods.add(info);
        instance.extProjects = mods.toArray(mods.toArray(new ProjectInfo[0]));

        GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
    }

    public static boolean hasExtraProject(MinecraftInstances.Instance instance, String name) {
        for(ProjectInfo info : instance.extProjects) {
            if(info.slug.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCoreMod(MinecraftInstances.Instance instance, ProjectInfo oldInfo) {
        if(oldInfo.slug.equalsIgnoreCase("Visor") || oldInfo.slug.equalsIgnoreCase("VisorEssentials")) {
            return true;
        }
        // Check if its a coremod
        ModsJson oldMods = instance.parseModsJson(Constants.USER_HOME + "/mods.json");
        if(oldMods != null) {
            Optional<ModsJson.Version> ver = Arrays.stream(oldMods.versions).filter((v) -> v.name.equals(instance.versionName)).findFirst();
            if(ver.isPresent()) {
                ModsJson.Version version = ver.get();
                Optional<ProjectInfo> info = Arrays.stream(version.coreMods).filter((mod) -> mod.slug.equals(oldInfo.slug)).findFirst();
                return info.isPresent();
            }
        }

        return false;
    }

    public static boolean removeExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        ProjectInfo oldInfo = Arrays.stream(instance.extProjects).filter(info -> info.slug.equalsIgnoreCase(name)).findFirst().orElse(null);

        if(oldInfo != null) {
            boolean isMod = oldInfo.type.equals("mod");
            boolean legacyMod = oldInfo.fileName == null;

            if(isCoreMod(instance, oldInfo)) {
                return false;
            }

            // Delete the mod
            File modFile = new File(instance.gameDir + (isMod ? "/mods/" : "/resourcepacks/") + (legacyMod ? oldInfo.slug : oldInfo.fileName) + (isMod ? ".jar" : ".zip"));
            modFile.delete();

            ArrayList<ProjectInfo> mods = Lists.newArrayList(instance.extProjects);
            mods.remove(oldInfo);
            instance.extProjects = mods.toArray(mods.toArray(new ProjectInfo[0]));
            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
            return true;
        }

        return false;
    }

    // Return true if instance was deleted
    public static boolean delete(MinecraftInstances instances, MinecraftInstances.Instance instance) throws IOException {
        File instanceDir = new File(instance.gameDir);
        Files.walkFileTree(instanceDir.toPath(), new FileVisitor<Path>() {
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
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        ArrayList<MinecraftInstances.Instance> instances1 = Lists.newArrayList(instances.instances);
        instances1.remove(instance);
        instances.instances = instances1.toArray(new MinecraftInstances.Instance[0]);
        GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);

        return instanceDir.delete();
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        try {
            API.currentInstance = instance;
            JREUtils.redirectAndPrintJRELog();
            VLoader.setAndroidInitInfo(activity);
            JREUtils.launchJavaVM(activity, instance.generateLaunchArgs(account), instance);
        } catch (Throwable e) {
            e.printStackTrace();
            Logger.getInstance().appendToLog("Instance launch failed: " + e);
        }
    }
}
