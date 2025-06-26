// src/main/java/com/mceballos/odmcli/config/OdmEnvironmentsConfig.java
package com.mceballos.odmcli.config;

import java.util.Map;

public class OdmEnvironmentsConfig {
    private Map<String, OdmServerConfig> servers;

    // Getter and Setter
    public Map<String, OdmServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, OdmServerConfig> servers) {
        this.servers = servers;
    }
}