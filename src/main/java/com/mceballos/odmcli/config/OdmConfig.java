// src/main/java/com/mceballos/odmcli/config/OdmConfig.java
package com.mceballos.odmcli.config;

import java.util.Map;

public class OdmConfig {
    private Map<String, OdmEnvironmentsConfig> environments;
    private OdmServerConfig defaultServer; // To hold a default server if specified

    // Getters and Setters
    public Map<String, OdmEnvironmentsConfig> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Map<String, OdmEnvironmentsConfig> environments) {
        this.environments = environments;
    }

    public OdmServerConfig getDefaultServer() {
        return defaultServer;
    }

    public void setDefaultServer(OdmServerConfig defaultServer) {
        this.defaultServer = defaultServer;
    }
}