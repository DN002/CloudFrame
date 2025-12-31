package dev.cloudframe.common.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Platform-agnostic debug file writer.
 * 
 * Platform adapters must call init() with the plugin data folder path.
 */
public class DebugFile {

    private static File file;
    private static FileWriter writer;

    /**
     * Initialize the debug log file.
     * 
     * @param dataFolderPath Absolute path to plugin data folder (platform-specific)
     */
    public static void init(String dataFolderPath) {
        try {
            File folder = new File(dataFolderPath);

            if (!folder.exists()) {
                folder.mkdirs();
            }

            // Write directly to the plugin root folder
            file = new File(folder, "debug.log");

            // Overwrite every startup
            writer = new FileWriter(file, false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void write(String line) {
        if (writer == null) return;
        try {
            writer.write(line + System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
