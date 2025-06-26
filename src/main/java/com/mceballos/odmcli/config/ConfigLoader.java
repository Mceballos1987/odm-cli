// src/main/java/com/mceballos/odmcli/config/ConfigLoader.java
package com.mceballos.odmcli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    private static OdmConfig odmConfig;
    private static final String DEFAULT_CONFIG_FILE = "odm-config.yaml";
    
    private ConfigLoader() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Loads configuration with multiple fallback locations and environment variable substitution
     */
    public static synchronized OdmConfig loadConfig() {
        return loadConfig(null);
    }
    
    /**
     * Loads configuration from specified path or falls back to default locations
     */
    public static synchronized OdmConfig loadConfig(String configPath) {
        if (odmConfig == null) {
            odmConfig = loadConfigInternal(configPath);
        }
        return odmConfig;
    }
    
    private static OdmConfig loadConfigInternal(String configPath) {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(OdmConfig.class, loaderOptions));
        
        try (InputStream inputStream = getConfigInputStream(configPath)) {
            if (inputStream == null) {
                logger.error("Configuration file not found. Checked locations: classpath, ~/.odm/, ./");
                throw new ConfigurationException("Configuration file not found in any expected location");
            }
            
            OdmConfig config = yaml.load(inputStream);
            if (config == null) {
                logger.error("Configuration file is empty or invalid");
                throw new ConfigurationException("Configuration file is empty or contains invalid YAML");
            }
            
            // Process environment variable substitutions
            processEnvironmentVariables(config);
            
            // Validate configuration
            validateConfiguration(config);
            
            logger.info("Configuration loaded successfully");
            return config;
            
        } catch (Exception e) {
            logger.error("Error loading configuration: {}", e.getMessage(), e);
            throw new ConfigurationException("Failed to load configuration", e);
        }
    }
    
    /**
     * Gets configuration input stream from multiple possible locations
     */
    private static InputStream getConfigInputStream(String configPath) {
        // 1. Explicit path provided
        if (configPath != null && !configPath.isEmpty()) {
            try {
                Path path = Paths.get(configPath);
                if (Files.exists(path)) {
                    logger.debug("Loading config from explicit path: {}", configPath);
                    return Files.newInputStream(path);
                }
            } catch (Exception e) {
                logger.warn("Could not load config from explicit path: {}", configPath, e);
            }
        }
        
        // 2. Environment variable ODM_CONFIG_PATH
        String envConfigPath = System.getenv("ODM_CONFIG_PATH");
        if (envConfigPath != null) {
            try {
                Path path = Paths.get(envConfigPath);
                if (Files.exists(path)) {
                    logger.debug("Loading config from environment variable path: {}", envConfigPath);
                    return Files.newInputStream(path);
                }
            } catch (Exception e) {
                logger.warn("Could not load config from environment path: {}", envConfigPath, e);
            }
        }
        
        // 3. User home directory
        try {
            Path homePath = Paths.get(System.getProperty("user.home"), ".odm", DEFAULT_CONFIG_FILE);
            if (Files.exists(homePath)) {
                logger.debug("Loading config from user home: {}", homePath);
                return Files.newInputStream(homePath);
            }
        } catch (Exception e) {
            logger.debug("Could not load config from user home", e);
        }
        
        // 4. Current directory
        try {
            Path currentPath = Paths.get(".", DEFAULT_CONFIG_FILE);
            if (Files.exists(currentPath)) {
                logger.debug("Loading config from current directory: {}", currentPath);
                return Files.newInputStream(currentPath);
            }
        } catch (Exception e) {
            logger.debug("Could not load config from current directory", e);
        }
        
        // 5. Classpath (original behavior)
        InputStream classpathStream = ConfigLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_CONFIG_FILE);
        if (classpathStream != null) {
            logger.debug("Loading config from classpath");
            return classpathStream;
        }
        
        return null;
    }
    
    /**
     * Process environment variable substitutions in configuration
     */
    private static void processEnvironmentVariables(OdmConfig config) {
        if (config.getEnvironments() != null) {
            config.getEnvironments().forEach((groupName, envConfig) -> {
                if (envConfig.getServers() != null) {
                    envConfig.getServers().forEach((serverName, serverConfig) -> {
                        processServerConfigVariables(serverConfig, groupName, serverName);
                    });
                }
            });
        }
        
        if (config.getDefaultServer() != null) {
            processServerConfigVariables(config.getDefaultServer(), "default", "default");
        }
    }
    
    private static void processServerConfigVariables(OdmServerConfig serverConfig, String groupName, String serverName) {
        try {
            if (serverConfig.getUrl() != null) {
                serverConfig.setUrl(substituteEnvironmentVariables(serverConfig.getUrl()));
            }
            if (serverConfig.getUsername() != null) {
                serverConfig.setUsername(substituteEnvironmentVariables(serverConfig.getUsername()));
            }
            if (serverConfig.getPassword() != null) {
                serverConfig.setPassword(substituteEnvironmentVariables(serverConfig.getPassword()));
            }
        } catch (Exception e) {
            logger.warn("Error processing environment variables for {}:{} - {}", 
                groupName, serverName, e.getMessage());
        }
    }
    
    /**
     * Substitute environment variables in string values
     */
    private static String substituteEnvironmentVariables(String value) {
        if (value == null) return null;
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String envVarName = matcher.group(1);
            String envVarValue = System.getenv(envVarName);
            
            if (envVarValue != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(envVarValue));
                logger.debug("Substituted environment variable: {}", envVarName);
            } else {
                logger.warn("Environment variable not found: {} - keeping original value", envVarName);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Basic configuration validation
     */
    private static void validateConfiguration(OdmConfig config) throws ConfigurationException {
        if (config.getEnvironments() == null || config.getEnvironments().isEmpty()) {
            if (config.getDefaultServer() == null) {
                throw new ConfigurationException("No environments or default server configured");
            }
        }
        
        // Validate each environment
        if (config.getEnvironments() != null) {
            for (var entry : config.getEnvironments().entrySet()) {
                String groupName = entry.getKey();
                OdmEnvironmentsConfig envConfig = entry.getValue();
                
                if (envConfig.getServers() == null || envConfig.getServers().isEmpty()) {
                    throw new ConfigurationException("Environment group '" + groupName + "' has no servers configured");
                }
                
                for (var serverEntry : envConfig.getServers().entrySet()) {
                    String serverName = serverEntry.getKey();
                    OdmServerConfig serverConfig = serverEntry.getValue();
                    validateServerConfig(serverConfig, groupName, serverName);
                }
            }
        }
        
        // Validate default server if present
        if (config.getDefaultServer() != null) {
            validateServerConfig(config.getDefaultServer(), "default", "default");
        }
    }
    
    private static void validateServerConfig(OdmServerConfig serverConfig, String groupName, String serverName) 
            throws ConfigurationException {
        String serverPath = groupName + ":" + serverName;
        
        if (serverConfig.getUrl() == null || serverConfig.getUrl().trim().isEmpty()) {
            throw new ConfigurationException("Missing URL for server " + serverPath);
        }
        
        if (!isValidUrl(serverConfig.getUrl())) {
            throw new ConfigurationException("Invalid URL format for server " + serverPath + ": " + serverConfig.getUrl());
        }
        
        if (serverConfig.getUsername() == null || serverConfig.getUsername().trim().isEmpty()) {
            logger.warn("Missing username for server {} - authentication may fail", serverPath);
        }
        
        if (serverConfig.getPassword() == null || serverConfig.getPassword().trim().isEmpty()) {
            logger.warn("Missing password for server {} - authentication may fail", serverPath);
        }
    }
    
    private static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            new java.net.URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves an ODM server configuration based on group and optional server name.
     * Enhanced with better error messages and logging.
     */
    public static Optional<OdmServerConfig> resolveServerConfig(String groupName, String serverName) {
        OdmConfig config = loadConfig();

        if (config == null) {
            logger.error("Configuration not loaded");
            return Optional.empty();
        }

        // Handle default server
        if (groupName == null || groupName.isEmpty() || "default".equalsIgnoreCase(groupName)) {
            if (config.getDefaultServer() != null) {
                logger.debug("Using default server configuration");
                return Optional.of(config.getDefaultServer());
            } else {
                logger.error("No default server configuration found");
                return Optional.empty();
            }
        }

        // Try to find by group
        if (config.getEnvironments() != null) {
            OdmEnvironmentsConfig envConfig = config.getEnvironments().get(groupName);
            if (envConfig != null && envConfig.getServers() != null) {
                if (serverName != null && !serverName.isEmpty()) {
                    // Specific server within a group
                    OdmServerConfig serverConfig = envConfig.getServers().get(serverName);
                    if (serverConfig != null) {
                        logger.debug("Resolved server configuration: {}:{}", groupName, serverName);
                        return Optional.of(serverConfig);
                    } else {
                        logger.error("Server '{}' not found in group '{}'. Available servers: {}", 
                            serverName, groupName, envConfig.getServers().keySet());
                    }
                } else {
                    // If only group is specified, return the first server
                    if (!envConfig.getServers().isEmpty()) {
                        String firstServerName = envConfig.getServers().keySet().iterator().next();
                        logger.warn("No specific server name provided for group '{}'. Using first available server: {}", 
                            groupName, firstServerName);
                        return envConfig.getServers().values().stream().findFirst();
                    }
                }
            } else {
                logger.error("Environment group '{}' not found. Available groups: {}", 
                    groupName, config.getEnvironments().keySet());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Force reload configuration (useful for testing)
     */
    public static synchronized void reloadConfig() {
        odmConfig = null;
        logger.debug("Configuration cache cleared - will reload on next access");
    }
    
    /**
     * Get available environment groups
     */
    public static java.util.Set<String> getAvailableGroups() {
        OdmConfig config = loadConfig();
        if (config.getEnvironments() != null) {
            return config.getEnvironments().keySet();
        }
        return java.util.Collections.emptySet();
    }
    
    /**
     * Get available servers in a group
     */
    public static java.util.Set<String> getAvailableServers(String groupName) {
        OdmConfig config = loadConfig();
        if (config.getEnvironments() != null) {
            OdmEnvironmentsConfig envConfig = config.getEnvironments().get(groupName);
            if (envConfig != null && envConfig.getServers() != null) {
                return envConfig.getServers().keySet();
            }
        }
        return java.util.Collections.emptySet();
    }
}