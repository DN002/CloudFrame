package dev.cloudframe.cloudframe.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class Debug {

    private final String className;
    
    // Includes date + time in 12-hour format
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");

    public Debug(Class<?> clazz, Logger logger) {
        this.className = clazz.getSimpleName();
    }

    public void log(String method, String msg) {
        String timestamp = timeFormat.format(new Date());
        String line = "[" + timestamp + "] [" + className + "." + method + "()] " + msg;

        DebugFile.write(line);
        DebugBuffer.add(line);
    }
}
