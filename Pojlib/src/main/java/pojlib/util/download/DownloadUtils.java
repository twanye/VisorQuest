package pojlib.util.download;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import pojlib.API;
import pojlib.util.Logger;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import javax.annotation.Nullable;

public class DownloadUtils {
    private static final int MAX_RETRIES = 3;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    private static void download(URL url, OutputStream os, long size) throws IOException {
        IOException failure = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "QuestCraft");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoInput(true);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // Previously a non-200 response fell through and re-issued the
                    // request in a tight loop forever: one 404 asset hung the whole
                    // "Checking assets" stage until Android killed the app.
                    failure = new IOException("HTTP " + responseCode + " for " + url);
                    if (responseCode >= 400 && responseCode < 500
                            && responseCode != 408 && responseCode != 429) {
                        // Permanent error, retrying cannot help.
                        break;
                    }
                    continue;
                }

                if (size == -1) {
                    size = conn.getContentLengthLong();
                }

                try (InputStream is = new StreamDL(conn.getInputStream(), size)) {
                    IOUtils.copy(is, os);
                }
                return;
            } catch (IOException e) {
                failure = e;
                if (e instanceof SSLException) {
                    break;
                }
            } finally {
                if (conn != null) {
                    // Thousands of asset requests share one process; leaking
                    // connections exhausts sockets/file descriptors.
                    conn.disconnect();
                }
            }
        }

        throw new IOException("Unable to download from " + url, failure);
    }

    public static void downloadFile(String url, File out) throws IOException {
        downloadFile(url, out, -1);
    }

    public static void downloadFile(String url, File out, long size) throws IOException {
        Objects.requireNonNull(out.getParentFile()).mkdirs();
        File tempOut = File.createTempFile(out.getName(), ".part", out.getParentFile());
        try {
            try (OutputStream bos2 = new BufferedOutputStream(Files.newOutputStream(tempOut.toPath()))) {
                download(new URL(url), bos2, size);
            }
            // renameTo() can fail silently, leaving the caller with a missing or
            // stale file that is then re-downloaded on every attempt.
            Files.move(tempOut.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (tempOut.exists()) tempOut.delete();
        }
    }

    public static boolean compareSHA1(File f, @Nullable String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = Files.newInputStream(f.toPath())) {
                sha1_dst = new String(Hex.encodeHex(DigestUtils.sha1(is)));
            }
            if (sourceSHA != null) return sha1_dst.equalsIgnoreCase(sourceSHA);
            else return true; // No hash provided

        } catch (IOException e) {
            Logger.getInstance().appendToLog("Issue while comparing SHA1: " + e);
            return false;
        }
    }
}
