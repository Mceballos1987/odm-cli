// src/main/java/com/mceballos/odmcli/config/ConfigLoader.java
package com.mceballos.odmcli.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.Optional;

public class ConfigLoader {

    private static OdmConfig odmConfig;

    private ConfigLoader() {
        // Private constructor to prevent instantiation
    }

    public static synchronized OdmConfig loadConfig() {
        if (odmConfig == null) {
            Yaml yaml = new Yaml(new Constructor(OdmConfig.class));
            try (InputStream inputStream = ConfigLoader.class.getClassLoader()
                    .getResourceAsStream("odm-config.yaml")) {
                if (inputStream == null) {
                    throw new IllegalStateException("odm-config.yaml not found in classpath.");
                }
                odmConfig = yaml.load(inputStream);
            } catch (Exception e) {
                System.err.println("Error loading configuration: " + e.getMessage());
                e.printStackTrace();
                odmConfig = new OdmConfig(); // Initialize empty config to avoid NPE
            }
        }
        return odmConfig;
    }

    /**
     * Resolves an ODM server configuration based on group and optional server name.
     * @param groupName The name of the server group (e.g., "lower_regions", "production_regions", or "default").
     * @param serverName Optional specific server name within the group (e.g., "SYSI", "PRD").
     * @return An Optional containing the OdmServerConfig if found, otherwise empty.
     */
    public static Optional<OdmServerConfig> resolveServerConfig(String groupName, String serverName) {
        OdmConfig config = loadConfig();

        if (config == null) {
            return Optional.empty();
        }

        // Handle default server if no group/server specified or groupName is "default"
        if (groupName == null || groupName.isEmpty() || "default".equalsIgnoreCase(groupName)) {
            return Optional.ofNullable(config.getDefaultServer());
        }

        // Try to find by group
        if (config.getEnvironments() != null) {
            OdmEnvironmentsConfig envConfig = config.getEnvironments().get(groupName);
            if (envConfig != null && envConfig.getServers() != null) {
                if (serverName != null && !serverName.isEmpty()) {
                    // Specific server within a group
                    return Optional.ofNullable(envConfig.getServers().get(serverName));
                } else {
                    // If only group is specified, but no server, return the first one or throw error
                    // For now, let's just return the first available server in the group as a default
                    // Or you might want to force user to specify exact server if group is chosen.
                    if (!envConfig.getServers().isEmpty()) {
                        System.out.println("Warning: No specific server name provided for group '" + groupName + "'. Using the first available server.");
                        return envConfig.getServers().values().stream().findFirst();
                    }
                }
            }
        }
        return Optional.empty(); // Not found
    }
}