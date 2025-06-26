// src/main/java/com/mceballos/odmcli/OdmCliApplication.java
package com.mceballos.odmcli;

import com.mceballos.odmcli.commands.DeployCommand;
import com.mceballos.odmcli.commands.ReportCommand;
import com.mceballos.odmcli.commands.TestCommand;
import com.mceballos.odmcli.config.ConfigLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/**
 * Main CLI application for IBM ODM operations.
 * Refactored to use separate command classes for better maintainability.
 */
@Command(name = "odm-cli",
         mixinStandardHelpOptions = true,
         version = "odm-cli 1.0.0",
         description = "A command-line tool for IBM ODM operations with enhanced security and maintainability.",
         subcommands = {
             TestCommand.class,
             DeployCommand.class,
             ReportCommand.class,
             OdmCliApplication.ListCommand.class,
             OdmCliApplication.ConfigCommand.class
         })
public class OdmCliApplication implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(OdmCliApplication.class);

    @Option(names = {"--version"}, 
            versionHelp = true, 
            description = "Display version information")
    private boolean versionRequested;

    public static void main(String[] args) {
        // Set up logging early
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
        
        int exitCode = new CommandLine(new OdmCliApplication())
                .setExecutionExceptionHandler(new CustomExceptionHandler())
                .execute(args);
        
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("ODM CLI - IBM Operational Decision Manager Command Line Interface");
        System.out.println("Version: 1.0.0");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  test     - Test rule applications or rule sets");
        System.out.println("  deploy   - Deploy rule applications to ODM servers");
        System.out.println("  report   - Generate various ODM reports");
        System.out.println("  list     - List available environments and servers");
        System.out.println("  config   - Manage configuration settings");
        System.out.println();
        System.out.println("Use 'odm-cli <command> --help' for detailed command help.");
        System.out.println("Use 'odm-cli --help' for general help and options.");
        
        return 0; // Success
    }

    /**
     * Command to list available environments and servers from configuration
     */
    @Command(name = "list",
             description = "List available environments and servers from configuration")
    static class ListCommand implements Callable<Integer> {

        @Option(names = {"-g", "--groups"}, 
                description = "List only environment groups")
        private boolean groupsOnly;

        @Option(names = {"-s", "--servers"}, 
                description = "List servers in specified group",
                paramLabel = "GROUP_NAME")
        private String groupName;

        @Override
        public Integer call() throws Exception {
            try {
                if (groupsOnly) {
                    listGroups();
                } else if (groupName != null) {
                    listServersInGroup(groupName);
                } else {
                    listAllEnvironments();
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error listing environments: " + e.getMessage());
                return 1;
            }
        }

        private void listGroups() {
            System.out.println("Available environment groups:");
            for (String group : ConfigLoader.getAvailableGroups()) {
                System.out.println("  • " + group);
            }
        }

        private void listServersInGroup(String group) {
            System.out.println("Servers in group '" + group + "':");
            for (String server : ConfigLoader.getAvailableServers(group)) {
                System.out.println("  • " + server);
            }
        }

        private void listAllEnvironments() {
            System.out.println("Available environments:");
            System.out.println();
            
            for (String group : ConfigLoader.getAvailableGroups()) {
                System.out.println("Group: " + group);
                for (String server : ConfigLoader.getAvailableServers(group)) {
                    System.out.println("  • " + server + " (use: -e " + group + ":" + server + ")");
                }
                System.out.println();
            }
            
            System.out.println("Usage examples:");
            System.out.println("  odm-cli test ruleapp myapp.jar -d data.json -e QA");
            System.out.println("  odm-cli deploy app.jar -e lower_regions:SYSI");
            System.out.println("  odm-cli report deployment-summary -o reports -e production_regions:PRD");
        }
    }

    /**
     * Command to manage configuration settings
     */
    @Command(name = "config",
             description = "Manage configuration settings",
             subcommands = {
                 ConfigCommand.ValidateCommand.class,
                 ConfigCommand.ShowCommand.class
             })
    static class ConfigCommand implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            System.out.println("Configuration management commands:");
            System.out.println("  validate - Validate configuration file");
            System.out.println("  show     - Show current configuration (sanitized)");
            System.out.println();
            System.out.println("Use 'odm-cli config <subcommand> --help' for detailed help.");
            return 0;
        }

        @Command(name = "validate",
                 description = "Validate the ODM configuration file")
        static class ValidateCommand implements Callable<Integer> {

            @Option(names = {"-f", "--file"}, 
                    description = "Configuration file to validate")
            private String configFile;

            @Override
            public Integer call() throws Exception {
                try {
                    System.out.println("Validating ODM configuration...");
                    
                    // Load and validate configuration
                    ConfigLoader.loadConfig(configFile);
                    
                    System.out.println("✅ Configuration is valid!");
                    
                    // Show summary
                    System.out.println("\nConfiguration summary:");
                    System.out.println("  Available groups: " + ConfigLoader.getAvailableGroups().size());
                    for (String group : ConfigLoader.getAvailableGroups()) {
                        int serverCount = ConfigLoader.getAvailableServers(group).size();
                        System.out.println("    • " + group + ": " + serverCount + " servers");
                    }
                    
                    return 0;
                    
                } catch (Exception e) {
                    System.err.println("❌ Configuration validation failed:");
                    System.err.println("  " + e.getMessage());
                    
                    if (e.getCause() != null) {
                        System.err.println("  Cause: " + e.getCause().getMessage());
                    }
                    
                    System.err.println("\nConfiguration file locations (in order of precedence):");
                    System.err.println("  1. Explicit path: --config /path/to/config.yaml");
                    System.err.println("  2. Environment variable: ODM_CONFIG_PATH");
                    System.err.println("  3. User home: ~/.odm/odm-config.yaml");
                    System.err.println("  4. Current directory: ./odm-config.yaml");
                    System.err.println("  5. Classpath: src/main/resources/odm-config.yaml");
                    
                    return 1;
                }
            }
        }

        @Command(name = "show",
                 description = "Show current configuration (with sensitive data masked)")
        static class ShowCommand implements Callable<Integer> {

            @Option(names = {"--include-urls"}, 
                    description = "Include server URLs in output")
            private boolean includeUrls;

            @Override
            public Integer call() throws Exception {
                try {
                    System.out.println("Current ODM configuration:");
                    System.out.println();
                    
                    for (String group : ConfigLoader.getAvailableGroups()) {
                        System.out.println("Group: " + group);
                        for (String server : ConfigLoader.getAvailableServers(group)) {
                            System.out.print("  • " + server);
                            
                            if (includeUrls) {
                                var serverConfig = ConfigLoader.resolveServerConfig(group, server);
                                if (serverConfig.isPresent()) {
                                    System.out.print(" (" + serverConfig.get().getUrl() + ")");
                                }
                            }
                            System.out.println();
                        }
                        System.out.println();
                    }
                    
                    System.out.println("Note: Passwords and sensitive data are not displayed for security.");
                    System.out.println("Use environment variables for credentials: ODM_<ENV>_USER, ODM_<ENV>_PASS");
                    
                    return 0;
                    
                } catch (Exception e) {
                    System.err.println("Error reading configuration: " + e.getMessage());
                    return 1;
                }
            }
        }
    }

    /**
     * Custom exception handler for better error messages
     */
    static class CustomExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
            
            // Log the full exception for debugging
            LoggerFactory.getLogger(CustomExceptionHandler.class)
                .error("Command execution failed", ex);
            
            // Provide user-friendly error messages
            System.err.println("❌ Command execution failed:");
            
            if (ex instanceof com.mceballos.odmcli.config.ConfigurationException) {
                com.mceballos.odmcli.config.ConfigurationException configEx = 
                    (com.mceballos.odmcli.config.ConfigurationException) ex;
                System.err.println(configEx.getActionableMessage());
            } else if (ex instanceof com.mceballos.odmcli.commands.CommandValidationException) {
                com.mceballos.odmcli.commands.CommandValidationException validationEx = 
                    (com.mceballos.odmcli.commands.CommandValidationException) ex;
                System.err.println(validationEx.getActionableMessage());
            } else {
                System.err.println("  " + ex.getMessage());
                
                // Show stack trace only if verbose logging is enabled
                String logLevel = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
                if ("DEBUG".equalsIgnoreCase(logLevel) || "TRACE".equalsIgnoreCase(logLevel)) {
                    System.err.println("\nStack trace:");
                    ex.printStackTrace();
                }
            }
            
            System.err.println("\nFor help, run: " + commandLine.getCommandName() + " --help");
            
            return 1; // Error exit code
        }
    }
}