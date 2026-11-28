package pojlib.util.download;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

// Bulk read() and close() added after QuestCraftPlus-Pojlib commits 9361b33 and bd3d402
// (MisterEnglishDev, LGPL-3.0). Listener callbacks keep their per-byte contract.
public class StreamDL extends InputStream {
    private final InputStream in;
    private int count;
    private final Collection<StreamListener> listeners = new ArrayList<>();

    public StreamDL(InputStream in, long totalBytes) {
        this.in = in;
        DownloadManager.addTotalBytes(totalBytes);
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        byteReceived(b);
        return b;
    }

    // Without this override IOUtils.copy() falls back to InputStream's default
    // read(byte[], int, int), which calls read() once per byte and makes every
    // download (assets, libraries, client jar, JRE) far slower than the network.
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) {
            DownloadManager.addBytes(n);
            if (listeners.isEmpty()) {
                count += n;
            } else {
                for (int i = 0; i < n; i++) {
                    count++;
                    for (StreamListener l : listeners) {
                        l.byteReceived(b[off + i] & 0xFF, count);
                    }
                }
            }
        } else if (n == -1) {
            for (StreamListener l : listeners) {
                l.byteReceived(-1, count);
            }
        }
        return n;
    }

    // InputStream.close() does nothing by default, so the try-with-resources in
    // DownloadUtils.download() never closed the HttpURLConnection response stream.
    @Override
    public void close() throws IOException {
        in.close();
    }

    public void addListener(StreamListener listener) {
        listeners.add(listener);
    }

    private void byteReceived(int b) {
        if (b != -1) {
            count++;
            DownloadManager.addBytes(1);
        }

        for (StreamListener l : listeners) {
            l.byteReceived(b, count);
        }
    }
}
