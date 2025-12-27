package dev.cloudframe.cloudframe.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.bukkit.Bukkit;

public class DebugFile {

    private static File file;
    private static FileWriter writer;

    public static void init() {
        try {
            File folder = Bukkit.getPluginManager()
                    .getPlugin("CloudFrame")
                    .getDataFolder();

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
        try {
            writer.write(line + System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
