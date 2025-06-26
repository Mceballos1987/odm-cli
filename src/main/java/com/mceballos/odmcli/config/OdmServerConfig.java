// src/main/java/com/mceballos/odmcli/config/OdmServerConfig.java
package com.mceballos.odmcli.config;

public class OdmServerConfig {
    private String url;
    private String username;
    private String password; // Remember security considerations!

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "OdmServerConfig{" +
               "url='" + url + '\'' +
               ", username='" + username + '\'' +
               ", password='[HIDDEN]'" +
               '}';
    }
}