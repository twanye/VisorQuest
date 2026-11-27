package pojlib.util;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;

/** Singleton class made to log on one file
 * The singleton part can be removed but will require more implementation from the end-dev
 */
@Keep
public class Logger {

    /* Instance variables */
    private final File mLogFile;
    private PrintStream mLogStream;
    private WeakReference<eventLogListener> mLogListenerWeakReference = null;

    /* No public construction */
    private Logger(){
        this("latestlog.txt");
    }

    private Logger(String fileName){
        mLogFile = new File(Constants.USER_HOME, fileName);
        // Make a new instance of the log file
        mLogFile.delete();
        try {
            mLogFile.createNewFile();
            mLogStream = new PrintStream(mLogFile.getAbsolutePath());
        }catch (IOException e){e.printStackTrace();}

    }

    private static final class SLoggerSingletonHolder {
        static final Logger sLoggerSingleton = new Logger();
    }

    public static Logger getInstance(){
        return SLoggerSingletonHolder.sLoggerSingleton;
    }


    /** Print the text to the log file if not censored */
    public void appendToLog(String text){
        if(shouldCensorLog(text)) return;
        appendToLogUnchecked(text);
    }

    /** Print the text and the stack trace of a failure to the log file */
    public void appendToLog(String text, Throwable throwable){
        if(shouldCensorLog(text)) return;
        if(throwable == null) {
            appendToLogUnchecked(text);
            return;
        }
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        appendToLogUnchecked(text + "\n" + trace);
    }

    /** Print the text to the log file, no china censoring there */
    public synchronized void appendToLogUnchecked(String text){
        // Never let logging itself take down the caller (e.g. a download worker
        // when storage is full and the stream could not be opened).
        try {
            if (mLogStream != null) mLogStream.println(text);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        notifyLogListener(text);
    }

    /** Reset the log file, effectively erasing any previous logs */
    public synchronized void reset(){
        try{
            mLogFile.delete();
            mLogFile.createNewFile();
            mLogStream = new PrintStream(mLogFile.getAbsolutePath());
        }catch (IOException e){ e.printStackTrace();}
    }

    /** Disables the printing */
    public synchronized void shutdown(){
        if (mLogStream != null) mLogStream.close();
    }

    /**
     * Perform various checks to see if the log is safe to print
     * Subclasses may want to override this behavior
     * @param text The text to check
     * @return Whether the log should be censored
     */
    private static boolean shouldCensorLog(String text){
        return text.contains("Session ID is");
    }

    /** Small listener for anything listening to the log */
    public interface eventLogListener {
        void onEventLogged(String text);
    }

    /** Link a log listener to the logger */
    public void setLogListener(eventLogListener logListener){
        this.mLogListenerWeakReference = new WeakReference<>(logListener);
    }

    /** Notifies the event listener, if it exists */
    private void notifyLogListener(String text){
        if(mLogListenerWeakReference == null) return;
        eventLogListener logListener = mLogListenerWeakReference.get();
        if(logListener == null){
            mLogListenerWeakReference = null;
            return;
        }
        // Listeners are invoked from download worker threads; a broken listener
        // must not be able to kill the process.
        try {
            logListener.onEventLogged(text);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
}
