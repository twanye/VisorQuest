package pojlib.util.download;

import java.util.concurrent.atomic.AtomicLong;

public class DownloadManager {
    // Progress is updated from up to 8 download threads at once, so plain long
    // counters can lose updates and leave the launcher's play button disabled
    // forever (downloadsCompleted() never becomes true).
    private static final AtomicLong downloadedBytes = new AtomicLong();
    private static final AtomicLong totalBytes = new AtomicLong();

    public static void addBytes(long add) {
        downloadedBytes.addAndGet(add);
    }

    public static void addTotalBytes(long add) {
        totalBytes.addAndGet(add);
    }

    public static void reset() {
        downloadedBytes.set(0);
        totalBytes.set(0);
    }

    public static boolean downloadsCompleted() {
        return downloadedBytes.get() == totalBytes.get();
    }

    public static float getPercentComplete() {
        long downloaded = downloadedBytes.get();
        long total = totalBytes.get();
        if(total == 0 || downloaded > total) {
            return 100.0f;
        }

        return (float) downloaded/total * 100;
    }
}
