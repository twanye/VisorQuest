package pojlib.util;

import android.app.Activity;
import android.content.Context;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.Set;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class FileUtil {

    public static String DIR_GAME_NEW;
    public static String DIR_HOME_VERSION;


    public static byte[] loadFromAssetToByte(Context ctx, String inFile) {
        byte[] buffer = null;

        try {
            InputStream stream = ctx.getAssets().open(inFile);

            int size = stream.available();
            buffer = new byte[size];
            stream.read(buffer);
            stream.close();
        } catch (IOException e) {
            // Handle exceptions here
            e.printStackTrace();
        }
        return buffer;
    }

    public static boolean matchingAssetFile(Context ctx, String assetPath, File sourceFile) throws IOException {
        if (!sourceFile.isFile()) {
            return false;
        }
        try (InputStream asset = new BufferedInputStream(ctx.getAssets().open(assetPath));
             InputStream file = new BufferedInputStream(Files.newInputStream(sourceFile.toPath()))) {
            byte[] assetBuffer = new byte[8192];
            byte[] fileBuffer = new byte[8192];
            while (true) {
                int assetLength = asset.read(assetBuffer);
                int fileLength = file.read(fileBuffer);
                if (assetLength != fileLength) {
                    return false;
                }
                if (assetLength == -1) {
                    return true;
                }
                for (int i = 0; i < assetLength; i++) {
                    if (assetBuffer[i] != fileBuffer[i]) {
                        return false;
                    }
                }
            }
        }
    }

    public static void copyAssetToFile(Context ctx, String assetPath, File target) throws IOException {
        ensureParentDirectory(target);
        try (InputStream input = new BufferedInputStream(ctx.getAssets().open(assetPath));
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(target.toPath()))) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
    }

    public static String read(String path) throws IOException {
        return read(Files.newInputStream(Paths.get(path)));
    }

    public static String read(InputStream is) throws IOException {
        StringBuilder out = new StringBuilder();
        int len;
        byte[] buf = new byte[512];
        while((len = is.read(buf))!=-1) {
            out.append(new String(buf, 0, len));
        }
        return out.toString();
    }

    public static void write(String path, byte[] content) throws IOException
    {
        File outPath = new File(path);
        Objects.requireNonNull(outPath.getParentFile()).mkdirs();
        if(!outPath.exists()) {
            outPath.createNewFile();
        }

        BufferedOutputStream fos = new BufferedOutputStream(Files.newOutputStream(outPath.toPath()));
        fos.write(content, 0, content.length);
        fos.close();
    }

    public static void unzipArchive(String archivePath, String extractPath) {
        try {
            unzipArchiveChecked(archivePath, extractPath);
        } catch (IOException e) {
            Logger.getInstance().appendToLog(e.getMessage());
        }
    }

    public static void unzipArchiveChecked(String archivePath, String extractPath) throws IOException {
        try(ZipFile zipFile = new ZipFile(archivePath)) {
            byte[] buf = new byte[8192];
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if(entry.isDirectory()) {
                    continue;
                }

                File newFile = newFile(new File(extractPath), entry);
                ensureParentDirectory(newFile);

                try(InputStream input = zipFile.getInputStream(entry);
                    OutputStream output = new BufferedOutputStream(new FileOutputStream(newFile))) {
                    int len;
                    while ((len = input.read(buf)) > 0) {
                        output.write(buf, 0, len);
                    }
                }
            }
        }
    }

    public static void unzipArchiveFromAsset(Activity activity, String archiveName, String extractPath) {
        try {
            File zip = new File(extractPath, archiveName);
            FileUtils.writeByteArrayToFile(zip, FileUtil.loadFromAssetToByte(activity, archiveName));
            try(ZipFile zipFile = new ZipFile(zip)) {
                byte[] buf = new byte[1024];
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while(entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if(entry.isDirectory()) {
                        continue;
                    }

                    File newFile = newFile(new File(extractPath), entry);
                    newFile.getParentFile().mkdirs();

                    FileOutputStream fos = new FileOutputStream(newFile);
                    InputStream input = zipFile.getInputStream(entry);
                    int len;
                    while ((len = input.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                        fos.flush();
                    }
                    fos.close();
                }
            }
        } catch (IOException e) {
            Logger.getInstance().appendToLog(e.getMessage());
        }
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * @author PojavLauncherTeam
     * Same as ensureDirectorySilently(), but throws an IOException telling why the check failed.
     * @param targetFile the directory to check
     * @throws IOException when the checks fail
     */
    public static void ensureDirectory(File targetFile) throws IOException{
        if(targetFile.isFile()) throw new IOException("Target directory is a file");
        if(targetFile.exists()) {
            if(!targetFile.canWrite()) throw new IOException("Target directory is not writable");
        }else if(!targetFile.mkdirs()) throw new IOException("Unable to create target directory");
    }

    /**
     * @author PojavLauncherTeam
     * Same as ensureParentDirectorySilently(), but throws an IOException telling why the check failed.
     * @param targetFile the File whose parent should be checked
     * @throws IOException when the checks fail
     */
    public static void ensureParentDirectory(File targetFile) throws IOException{
        File parentFile = targetFile.getParentFile();
        if(parentFile == null) throw new IOException("targetFile does not have a parent");
        ensureDirectory(parentFile);
    }

    /**
     * Restores the file permissions used by the launcher for installed instance files.
     * Symbolic links are not followed and directory permissions are left unchanged.
     */
    public static int fixDirectoryPermissions(File targetDir) throws IOException {
        if (targetDir == null || !targetDir.exists()) return 0;
        if (!targetDir.isDirectory() || !targetDir.canRead()) {
            throw new IOException("Instance data path is not a usable directory: " + targetDir);
        }

        final int[] failedFiles = {0};
        Files.walkFileTree(targetDir.toPath(), EnumSet.noneOf(FileVisitOption.class), 12,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (!attributes.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }

                        boolean executable = requiresExecutePermission(targetDir.toPath().relativize(file));
                        Set<PosixFilePermission> expectedPermissions = EnumSet.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_WRITE
                        );
                        if (executable) {
                            expectedPermissions.add(PosixFilePermission.OWNER_EXECUTE);
                        }

                        try {
                            Set<PosixFilePermission> currentPermissions = Files.getPosixFilePermissions(file);
                            if (!currentPermissions.equals(expectedPermissions)) {
                                Files.setPosixFilePermissions(file, expectedPermissions);
                            }
                        } catch (IOException | UnsupportedOperationException | SecurityException posixFailure) {
                            if (!setFallbackPermissions(file.toFile(), executable)) {
                                failedFiles[0]++;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                        if (file.equals(targetDir.toPath())) {
                            throw exception;
                        }
                        failedFiles[0]++;
                        return FileVisitResult.CONTINUE;
                    }
                });
        Logger.getInstance().appendToLog("Permission repair completed; files failed: " + failedFiles[0]);
        return failedFiles[0];
    }

    private static boolean requiresExecutePermission(Path relativePath) {
        String fileName = relativePath.getFileName() == null
                ? ""
                : relativePath.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (fileName.endsWith(".so")) {
            return true;
        }
        for (Path component : relativePath) {
            if ("bin".equalsIgnoreCase(component.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean setFallbackPermissions(File file, boolean executable) {
        try {
            boolean success = file.setReadable(true, true);
            success = file.setWritable(true, true) && success;
            if (executable) {
                success = file.setExecutable(true, true) && success;
            }
            return success;
        } catch (SecurityException exception) {
            return false;
        }
    }
}
