package io.github.teamcreating.mtrnav.server;

import io.github.teamcreating.mtrnav.core.Model.Settings;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;

public final class NavigationConfig {
    private NavigationConfig() {}
    public static Settings load(Path configDirectory) {
        var path = configDirectory.resolve("mtrnav.properties");
        var properties = new Properties();
        try {
            Files.createDirectories(configDirectory);
            if (Files.exists(path)) try (var input = Files.newInputStream(path)) { properties.load(input); }
            else {
                properties.setProperty("horizonHours", "6");
                properties.setProperty("maxTransfers", "4");
                properties.setProperty("transferBufferSeconds", "30");
                try (var output = Files.newOutputStream(path)) { properties.store(output, "MTR Navigation server settings"); }
            }
            return new Settings(Integer.parseInt(properties.getProperty("horizonHours", "6")), Integer.parseInt(properties.getProperty("maxTransfers", "4")),
                    Integer.parseInt(properties.getProperty("transferBufferSeconds", "30")));
        } catch (IOException | IllegalArgumentException e) {
            System.getLogger("MTR Navigation").log(System.Logger.Level.WARNING, "Invalid settings; using defaults", e);
            return Settings.defaults();
        }
    }
}
