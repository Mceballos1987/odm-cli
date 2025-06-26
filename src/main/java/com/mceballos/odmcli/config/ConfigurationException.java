// src/main/java/com/mceballos/odmcli/config/ConfigurationException.java
package com.mceballos.odmcli.config;

/**
 * Custom exception for configuration-related errors.
 * Provides specific error handling for configuration issues.
 */
public class ConfigurationException extends RuntimeException {
    
    private final String suggestion;
    private final String configPath;
    
    public ConfigurationException(String message) {
        super(message);
        this.suggestion = null;
        this.configPath = null;
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.suggestion = null;
        this.configPath = null;
    }
    
    public ConfigurationException(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
        this.configPath = null;
    }
    
    public ConfigurationException(String message, String suggestion, String configPath) {
        super(message);
        this.suggestion = suggestion;
        this.configPath = configPath;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public String getConfigPath() {
        return configPath;
    }
    
    public String getActionableMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Error: ").append(getMessage());
        
        if (configPath != null) {
            sb.append("\nConfig Path: ").append(configPath);
        }
        
        if (suggestion != null) {
            sb.append("\nSuggestion: ").append(suggestion);
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return getActionableMessage();
    }
}