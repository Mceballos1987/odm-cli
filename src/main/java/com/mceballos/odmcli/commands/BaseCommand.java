// src/main/java/com/mceballos/odmcli/commands/BaseCommand.java
package com.mceballos.odmcli.commands;

import com.mceballos.odmcli.config.ConfigLoader;
import com.mceballos.odmcli.config.ConfigurationException;
import com.mceballos.odmcli.config.OdmServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Base class for all ODM CLI commands.
 * Provides common functionality like server configuration resolution,
 * error handling, and logging setup.
 */
public abstract class BaseCommand implements Callable<Integer> {
    
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Spec
    protected CommandSpec spec; // Injected by Picocli
    
    // Common options inherited by all commands
    @Option(names = {"-e", "--env"},
            description = "Environment/Server to target (e.g., 'QA', 'PRD', 'lower_regions:SYSI', 'production_regions:PRD'). " +
                          "If only a group name is given (e.g., 'lower_regions'), the first server in that group will be used. " +
                          "If omitted, 'default' server from config will be used.",
            paramLabel = "GROUP[:SERVER_NAME]")
    protected String environmentTarget;
    
    @Option(names = {"-c", "--config"},
            description = "Path to configuration file. If not specified, will look in standard locations.",
            paramLabel = "CONFIG_PATH")
    protected String configPath;
    
    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose output")
    protected boolean verbose;
    
    @Option(names = {"-q", "--quiet"},
            description = "Suppress non-essential output")
    protected boolean quiet;
    
    private OdmServerConfig resolvedServerConfig;
    
    /**
     * Template method that handles common setup and delegates to subclass implementation
     */
    @Override
    public final Integer call() throws Exception {
        try {
            // Setup logging level based on verbosity
            setupLogging();
            
            // Resolve server configuration
            resolvedServerConfig = resolveServerConfig();
            
            // Validate command-specific requirements
            validateCommand();
            
            // Execute the actual command
            return executeCommand();
            
        } catch (ConfigurationException e) {
            handleConfigurationError(e);
            return 1; // Error exit code
        } catch (CommandValidationException e) {
            handleValidationError(e);
            return 1;
        } catch (Exception e) {
            handleUnexpectedError(e);
            return 2; // Unexpected error exit code
        }
    }
    
    /**
     * Abstract method that subclasses must implement for their specific logic
     */
    protected abstract Integer executeCommand() throws Exception;
    
    /**
     * Override this method to add command-specific validation
     */
    protected void validateCommand() throws CommandValidationException {
        // Default implementation - no additional validation
    }
    
    /**
     * Get the resolved server configuration for this command
     */
    protected OdmServerConfig getServerConfig() {
        if (resolvedServerConfig == null) {
            throw new IllegalStateException("Server configuration not resolved. This should not happen.");
        }
        return resolvedServerConfig;
    }
    
    /**
     * Resolve server configuration based on environment target
     */
    protected OdmServerConfig resolveServerConfig() throws ConfigurationException {
        logger.debug("Resolving server configuration for target: {}", environmentTarget);
        
        String groupName = null;
        String serverName = null;
        
        if (environmentTarget != null && !environmentTarget.isEmpty()) {
            String[] parts = environmentTarget.split(":");
            groupName = parts[0];
            if (parts.length > 1) {
                serverName = parts[1];
            }
        } else {
            groupName = "default";
        }
        
        Optional<OdmServerConfig> resolvedConfig = ConfigLoader.resolveServerConfig(groupName, serverName);
        
        if (resolvedConfig.isPresent()) {
            OdmServerConfig config = resolvedConfig.get();
            if (!quiet) {
                System.out.println("Targeting ODM server: " + config.getUrl());
            }
            logger.info("Resolved server configuration - URL: {}, Username: {}", 
                config.getUrl(), config.getUsername());
            return config;
        } else {
            String target = environmentTarget != null ? environmentTarget : "default";
            throw new ConfigurationException(
                String.format("Environment/server '%s' not found in configuration.", target),
                "Check your odm-config.yaml file or use --env with a valid target. " +
                "Available groups: " + ConfigLoader.getAvailableGroups()
            );
        }
    }
    
    /**
     * Setup logging based on verbosity flags
     */
    protected void setupLogging() {
        // This would integrate with your logging framework
        // For now, just store the settings for use in output methods
        if (verbose) {
            logger.debug("Verbose mode enabled");
        }
        if (quiet) {
            logger.debug("Quiet mode enabled");
        }
    }
    
    /**
     * Print message respecting quiet/verbose flags
     */
    protected void printMessage(String message) {
        if (!quiet) {
            System.out.println(message);
        }
    }
    
    /**
     * Print verbose message (only in verbose mode)
     */
    protected void printVerbose(String message) {
        if (verbose && !quiet) {
            System.out.println("[VERBOSE] " + message);
        }
    }
    
    /**
     * Print error message (always shown unless quiet)
     */
    protected void printError(String message) {
        if (!quiet) {
            System.err.println("ERROR: " + message);
        }
        logger.error(message);
    }
    
    /**
     * Print warning message
     */
    protected void printWarning(String message) {
        if (!quiet) {
            System.err.println("WARNING: " + message);
        }
        logger.warn(message);
    }
    
    /**
     * Handle configuration errors with helpful messages
     */
    protected void handleConfigurationError(ConfigurationException e) {
        printError(e.getActionableMessage());
        
        if (verbose) {
            logger.error("Configuration error details", e);
        }
        
        // Provide additional help
        if (!quiet) {
            System.err.println("\nFor help with configuration, run: " + getCommandName() + " --help");
            System.err.println("Available environment groups: " + ConfigLoader.getAvailableGroups());
        }
    }
    
    /**
     * Handle command validation errors
     */
    protected void handleValidationError(CommandValidationException e) {
        printError(e.getMessage());
        
        if (e.getSuggestion() != null) {
            System.err.println("Suggestion: " + e.getSuggestion());
        }
        
        if (verbose) {
            logger.error("Validation error details", e);
        }
    }
    
    /**
     * Handle unexpected errors
     */
    protected void handleUnexpectedError(Exception e) {
        printError("An unexpected error occurred: " + e.getMessage());
        
        if (verbose) {
            System.err.println("Stack trace:");
            e.printStackTrace();
        }
        
        logger.error("Unexpected error in command execution", e);
        
        if (!quiet) {
            System.err.println("Please report this issue if it persists.");
        }
    }
    
    /**
     * Get the command name for help messages
     */
    protected String getCommandName() {
        return spec != null ? spec.name() : "odm-cli";
    }
    
    /**
     * Utility method to check if a file exists and is readable
     */
    protected void validateFileExists(java.io.File file, String description) throws CommandValidationException {
        if (file == null) {
            throw new CommandValidationException("No " + description + " specified");
        }
        if (!file.exists()) {
            throw new CommandValidationException(
                description + " not found: " + file.getAbsolutePath(),
                "Ensure the file exists and the path is correct"
            );
        }
        if (!file.canRead()) {
            throw new CommandValidationException(
                "Cannot read " + description + ": " + file.getAbsolutePath(),
                "Check file permissions"
            );
        }
    }
    
    /**
     * Utility method to create output directory if it doesn't exist
     */
    protected void ensureOutputDirectory(java.io.File outputDir) throws CommandValidationException {
        if (outputDir == null) {
            throw new CommandValidationException("No output directory specified");
        }
        
        if (!outputDir.exists()) {
            printVerbose("Creating output directory: " + outputDir.getAbsolutePath());
            if (!outputDir.mkdirs()) {
                throw new CommandValidationException(
                    "Failed to create output directory: " + outputDir.getAbsolutePath(),
                    "Check parent directory permissions"
                );
            }
        }
        
        if (!outputDir.isDirectory()) {
            throw new CommandValidationException(
                "Output path is not a directory: " + outputDir.getAbsolutePath()
            );
        }
        
        if (!outputDir.canWrite()) {
            throw new CommandValidationException(
                "Cannot write to output directory: " + outputDir.getAbsolutePath(),
                "Check directory permissions"
            );
        }
    }
}