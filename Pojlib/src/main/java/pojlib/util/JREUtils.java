package pojlib.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArrayMap;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import pojlib.API;

import pojlib.UnityPlayerActivity;
import pojlib.install.Installer;
import pojlib.install.RuntimeProfile;
import pojlib.install.MinecraftMeta;
import pojlib.util.json.MinecraftInstances;

public class JREUtils {
    private JREUtils() {}

    public static String LD_LIBRARY_PATH;
    public static Map<String, String> jreReleaseList;
    public static String instanceHome;
    public static String jvmLibraryPath;
    private static String sNativeLibDir;
    private static String runtimeDir;

    public static String findInLdLibPath(String libName) {
        if(Os.getenv("LD_LIBRARY_PATH")==null) {
            try {
                if (LD_LIBRARY_PATH != null) {
                    Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
                }
            }catch (ErrnoException e) {
                e.printStackTrace();
            }
            return libName;
        }
        for (String libPath : Os.getenv("LD_LIBRARY_PATH").split(":")) {
            File f = new File(libPath, libName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return libName;
    }

    public static ArrayList<File> locateLibs(File path) {
        ArrayList<File> returnValue = new ArrayList<>();
        File[] list = path.listFiles();
        if(list != null) {
            for(File f : list) {
                if(f.isFile() && f.getName().endsWith(".so")) {
                    returnValue.add(f);
                }else if(f.isDirectory()) {
                    returnValue.addAll(locateLibs(f));
                }
            }
        }
        return returnValue;
    }

    public static void initJavaRuntime() {
        dlopen(findInLdLibPath("libjli.so"));
        if(!dlopen("libjvm.so")){
            dlopen(jvmLibraryPath+"/libjvm.so");
        }
        dlopen(findInLdLibPath("libverify.so"));
        dlopen(findInLdLibPath("libjava.so"));
        dlopen(findInLdLibPath("libnet.so"));
        dlopen(findInLdLibPath("libnio.so"));
        dlopen(findInLdLibPath("libawt.so"));
        dlopen(findInLdLibPath("libawt_headless.so"));
        dlopen(findInLdLibPath("libfreetype.so"));
        dlopen(findInLdLibPath("libfontmanager.so"));
        for(File f : locateLibs(new File(runtimeDir + "/lib"))) {
            dlopen(f.getAbsolutePath());
        }
    }


    // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
    public static boolean initializeExtraNatives(MinecraftInstances.Instance instance) {
        if (instance.extraNatives == null || instance.extraNatives.trim().isEmpty()) {
            return false;
        }

        boolean allLoaded = true;
        for (String nativeLib : instance.extraNatives.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (nativeLib == null || nativeLib.trim().isEmpty()) {
                continue;
            }
            boolean loaded = dlopen(nativeLib);
            if (!loaded) {
                Logger.getInstance().appendToLog("ERROR! Could not dlopen extra native: " + nativeLib);
                allLoaded = false;
            }
        }
        return allLoaded;
    }

    public static void redirectAndPrintJRELog() {
        Log.v("jrelog","Log starts here");
        JREUtils.logToLogger(Logger.getInstance());
        new Thread(new Runnable(){
            int failTime = 0;
            ProcessBuilder logcatPb;
            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        logcatPb = new ProcessBuilder().command("logcat", "-v", "brief", "-s", "jrelog:I", "LIBGL:I").redirectErrorStream(true);
                    }
                            Log.i("jrelog-logcat","Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat","Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.getInstance().appendToLog(currStr);
                    }
                            if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.getInstance().appendToLog("ERROR: Unable to get more log.");
                        }
                            }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.getInstance().appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat","Logcat thread started");
    }

    // Runtime for the instance being launched (set by prelaunchCheck/launchJavaVM). Legacy = runtimes/JRE + LTW.
    private static RuntimeProfile profile = RuntimeProfile.LEGACY;

    private static String jreHome(Context ctx) {
        return ctx.getFilesDir() + "/runtimes/" + profile.jreDirName;
    }

    public static void relocateLibPath(final Context ctx) {
        sNativeLibDir = ctx.getApplicationInfo().nativeLibraryDir;

        LD_LIBRARY_PATH = jreHome(ctx) + "/bin:" + jreHome(ctx) + "/lib:" +
                "/system/lib64:/vendor/lib64:/vendor/lib64/hw:" +
                sNativeLibDir;
    }

    public static void setJavaEnvironment(Activity activity, MinecraftInstances.Instance instance) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("POJLIB_NATIVEDIR", activity.getApplicationInfo().nativeLibraryDir);
        envMap.put("JAVA_HOME", jreHome(activity));
        envMap.put("HOME", instance.gameDir);
        //envMap.put("APP_HOME", Constants.USER_HOME);
        envMap.put("TMPDIR", activity.getCacheDir().getAbsolutePath());
        envMap.put("VR_MODEL", API.model);
        envMap.put("POJLIB_RENDERER", profile.rendererEnv());
        if (profile.usesMobileGlues()) {
            // MobileGlues keeps its config/cache here (QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1)
            File mgDir = new File(activity.getFilesDir(), "mg");
            mgDir.mkdirs();
            envMap.put("MG_DIR_PATH", mgDir.getAbsolutePath());
        }

        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("PATH", jreHome(activity) + "/bin:" + Os.getenv("PATH"));

        File customEnvFile = new File(Constants.USER_HOME, "custom_env.txt");
        if (customEnvFile.exists() && customEnvFile.isFile()) {
            BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Not use split() as only split first one
                int index = line.indexOf("=");
                envMap.put(line.substring(0, index), line.substring(index + 1));
            }
            reader.close();
        }
        envMap.put("LIBGL_ES", "2");
        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.getInstance().appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            Os.setenv(env.getKey(), env.getValue(), true);
        }

        File serverFile = new File(jreHome(activity) + "/lib/server/libjvm.so");
        jvmLibraryPath = jreHome(activity) + "/lib/" + (serverFile.exists() ? "server" : "client");
        Log.d("DynamicLoader","Base LD_LIBRARY_PATH: "+LD_LIBRARY_PATH);
        Log.d("DynamicLoader","Internal LD_LIBRARY_PATH: "+jvmLibraryPath+":"+LD_LIBRARY_PATH);
        setLdLibraryPath(jvmLibraryPath+":"+LD_LIBRARY_PATH);
    }

    // Called before game launch to ensure all files are present and correct
    public static void prelaunchCheck(Activity activity, MinecraftInstances.Instance instance) throws IOException, ExecutionException, InterruptedException {
        profile = RuntimeProfile.forVersion(activity, instance.versionName);
        UnityPlayerActivity.installLWJGL(activity, profile);
        Installer.installJVM(activity, profile);
        Installer.installLauncherAssets(activity);
        Installer.installClient(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
        Installer.installLibraries(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME, profile).get();
        Installer.installAssets(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
    }

    public static int launchJavaVM(final Activity activity, final List<String> JVMArgs, MinecraftInstances.Instance instance) throws Throwable {
        profile = RuntimeProfile.forVersion(activity, instance.versionName);
        JREUtils.relocateLibPath(activity);
        setJavaEnvironment(activity, instance);

        final String graphicsLib = loadGraphicsLibrary();
        List<String> userArgs = getJavaArgs(activity, instance);

        //Add automatically generated args
        if (API.customRAMValue) {
            Logger.getInstance().appendToLog("QuestCraft: Setting JVM memory to " + API.memoryValue + "MB (Custom)");
            userArgs.add("-Xms" + API.memoryValue + "M");
            userArgs.add("-Xmx" + API.memoryValue + "M");
        } else {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo ami = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(ami);
            long availMem = (ami.availMem-ami.threshold)/(1024*1024);
            availMem *= 0.8;
            String deviceModel = (Build.MODEL + " " + API.model).toLowerCase(Locale.ROOT);
            boolean quest2 = deviceModel.contains("quest 2")
                    || deviceModel.contains("quest2")
                    || ami.totalMem < 7L * 1024 * 1024 * 1024;
            long heapCap = quest2 ? 1792 : 2560;
            long allocatedRam = Math.max(1536, Math.min(availMem, heapCap));

            Logger.getInstance().appendToLog("QuestCraft: Setting JVM memory to " + allocatedRam
                    + "MB (automatic cap " + heapCap + "MB for " + Build.MODEL + ")");


            userArgs.add("-Xms" + 1024 + "M");
            userArgs.add("-Xmx" + allocatedRam + "M");
        }

        // Garbage collection
        userArgs.add("-XX:+UseZGC");
        if (profile.usesDefaultJre()) {
            // JRE 22 needs it for generational ZGC. Obsolete since JDK 24 (JRE 25 would only warn).
            userArgs.add("-XX:+ZGenerational");
        }
        userArgs.add("-XX:-ZProactive");
        userArgs.add("-XX:+UnlockDiagnosticVMOptions");
        userArgs.add("-XX:-ImplicitNullChecks");
        userArgs.add("-XX:+DisableExplicitGC");

        // Java should run at max
        userArgs.add("-XX:+UnlockExperimentalVMOptions");
        userArgs.add("-XX:+UseCriticalJavaThreadPriority");

        // Android sig fix
        userArgs.add("-XX:+UseSignalChaining");

        userArgs.add("-Dorg.lwjgl.opengl.libname=" + graphicsLib);
        userArgs.add("-Dorg.lwjgl.opengles.libname=" + "/system/lib64/libGLESv3.so");
        userArgs.add("-Dorg.lwjgl.egl.libname=" + "/system/lib64/libEGL_dri.so");

        // Per-version extras from versions.json (26.2: the vanilla Java 25 flags)
        userArgs.addAll(profile.extraJvmArgs);

        userArgs.addAll(JVMArgs);
        System.out.println(JVMArgs);

        runtimeDir = jreHome(activity);
        initJavaRuntime();

        // Ported from QuestCraftPlusPlus/Pojlib QuestCraft-6.0.1
        if (instance.extraNatives != null && !instance.extraNatives.trim().isEmpty()
                && !initializeExtraNatives(instance)) {
            Logger.getInstance().appendToLog("Some extra native libraries failed to load; see the individual dlopen errors above.");
        }

        chdir(instance.gameDir);
        userArgs.add(0,"java"); //argv[0] is the program name according to C standard.

        // A null entry here crashes natively inside JLI_Launch (SIGSEGV, no Java stack trace),
        // so fail early with a message that names the missing argument.
        for (int i = 0; i < userArgs.size(); i++) {
            if (userArgs.get(i) == null) {
                String previous = i > 0 ? userArgs.get(i - 1) : "(start)";
                String message = "Launch argument " + i + " is null, after " + previous;
                Logger.getInstance().appendToLog(message);
                throw new IllegalStateException(message);
            }
        }

        int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
        Logger.getInstance().appendToLog("Java Exit code: " + exitCode);
        return exitCode;
    }

    private static void writeDNS(Context ctx, File out) throws IOException {
        FileWriter writer = new FileWriter(out);

        if(!API.hasConnection(ctx)) {
            writer.write("nameserver 8.8.8.8\n");
            writer.write("nameserver 8.8.4.4");
            writer.flush();
            writer.close();
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        LinkProperties lp = cm.getLinkProperties(activeNetwork);
        if(lp == null)
            return;

        List<InetAddress> dnsServers = lp.getDnsServers();
        for (InetAddress dns : dnsServers) {
            writer.write(String.format("nameserver %s\n", dns.getHostAddress()));
            writer.flush();
        }
        writer.close();
    }

    /**
     *  Gives an argument list filled with both the user args
     *  and the auto-generated ones (eg. the window resolution).
     * @param ctx The application context
     * @return A list filled with args.
     */
    public static List<String> getJavaArgs(Context ctx, MinecraftInstances.Instance instance) {
        File resConfFile = new File(Constants.USER_HOME + "/hacks/resolv.conf");
        try {
            if(!resConfFile.exists()) {
                resConfFile.createNewFile();
            }
            writeDNS(ctx, resConfFile);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Couldn't write DNS servers! " + e.getMessage());
        }
        return new ArrayList<>(Arrays.asList(
                "-Djava.home=" + new File(ctx.getFilesDir(), "runtimes/" + profile.jreDirName),
                "-Djava.io.tmpdir=" + ctx.getCacheDir().getAbsolutePath(),
                "-Duser.home=" + instance.gameDir,
                "-Duser.language=" + System.getProperty("user.language"),
                "-Dos.name=Linux",
                "-Dos.version=Android-" + Build.VERSION.RELEASE,
                "-Dorg.lwjgl.librarypath=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Djna.boot.library.path=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Djna.nosys=true",
                "-Djava.library.path=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Dglfwstub.windowWidth=" + 1280,
                "-Dglfwstub.windowHeight=" + 720,
                "-Dglfwstub.initEgl=false",
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation
                "-Dnet.minecraft.clientmodname=" + "QuestCraft",
                "-Dext.net.resolvPath=" + resConfFile,
                "-Dsodium.checks.issue2561=false",
                "-Dorg.sqlite.lib.path=" + ctx.getApplicationInfo().nativeLibraryDir
        ));
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        //For each prefixes, we separate args.
        for(String prefix : new String[]{"-XX:-","-XX:+", "-XX:","--","-"}){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                //Get the end of the current argument
                int end = args.indexOf("-", start + prefix.length());
                if(end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
            }
        }
        return parsedArguments;
    }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary(){
        return profile.graphicsLibrary();
    }

    public static native long getEGLContextPtr();
    public static native long getEGLDisplayPtr();
    public static native long getEGLConfigPtr();
    public static native int chdir(String path);
    public static native void logToLogger(final Logger logger);
    public static native boolean dlopen(String libPath);
    public static native void setLdLibraryPath(String ldLibraryPath);

    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("istdio");
    }
}
