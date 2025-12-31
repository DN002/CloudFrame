package dev.cloudframe.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Platform-agnostic debug logger.
 * 
 * Logs to both an in-memory buffer and a persistent file (via platform adapters).
 */
public class Debug {

    private final String className;
    
    // Includes date + time in 12-hour format
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");

    public Debug(Class<?> clazz) {
        this.className = clazz.getSimpleName();
    }

    public void log(String method, String msg) {
        String timestamp = timeFormat.format(new Date());
        String line = "[" + timestamp + "] [" + className + "." + method + "()] " + msg;

        DebugFile.write(line);
        DebugBuffer.add(line);
    }
}
