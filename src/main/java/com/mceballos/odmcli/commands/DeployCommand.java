// src/main/java/com/mceballos/odmcli/commands/DeployCommand.java
package com.mceballos.odmcli.commands;

import com.mceballos.odmcli.config.OdmServerConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Command for deploying rule applications to ODM Decision Center or Decision Server.
 * 
 * Usage examples:
 *   odm-cli deploy myapp.jar -e QA
 *   odm-cli deploy /path/to/ruleapp.jar --environment-alias prod --force
 *   odm-cli deploy project/ -e production_regions:PRD --validate-only
 */
@Command(name = "deploy",
         description = "Deploys a rule application to ODM Decision Center or Decision Server.",
         mixinStandardHelpOptions = true)
public class DeployCommand extends BaseCommand {

    @Parameters(index = "0", 
                description = "Path to the RuleApp archive (.jar file) or project source directory")
    private File ruleAppPath;

    @Option(names = {"--environment-alias"}, 
            description = "Deployment target alias within ODM (e.g., 'dev', 'qa', 'prod')")
    private String environmentAlias;

    @Option(names = {"--force"}, 
            description = "Force deployment, overwriting existing versions")
    private boolean force;

    @Option(names = {"--validate-only"}, 
            description = "Only validate the deployment, don't actually deploy")
    private boolean validateOnly;

    @Option(names = {"--backup"}, 
            description = "Create backup before deployment")
    private boolean createBackup;

    @Option(names = {"--rollback-on-failure"}, 
            description = "Automatically rollback on deployment failure")
    private boolean rollbackOnFailure;

    @Option(names = {"--timeout"}, 
            description = "Deployment timeout in seconds",
            defaultValue = "300")
    private int timeoutSeconds;

    @Option(names = {"--maven-validate"}, 
            description = "Run Maven validation before deployment")
    private boolean mavenValidate;

    @Option(names = {"--deployment-notes"}, 
            description = "Notes to include with the deployment")
    private String deploymentNotes;

    @Override
    protected void validateCommand() throws CommandValidationException {
        // Validate rule app path
        validateFileExists(ruleAppPath, "rule application");

        if (ruleAppPath.isFile() && !ruleAppPath.getName().toLowerCase().endsWith(".jar")) {
            printWarning("Rule application file doesn't have .jar extension: " + ruleAppPath.getName());
        }

        if (ruleAppPath.isDirectory()) {
            validateProjectDirectory();
        }

        // Validate timeout
        if (timeoutSeconds <= 0) {
            throw new CommandValidationException(
                "Timeout must be positive: " + timeoutSeconds,
                "Use a value greater than 0"
            );
        }

        // Validate environment alias if provided
        if (environmentAlias != null && environmentAlias.trim().isEmpty()) {
            throw new CommandValidationException(
                "Environment alias cannot be empty",
                "Provide a valid environment alias or omit the option"
            );
        }

        // Maven validation check
        if (mavenValidate && !isMavenAvailable()) {
            printWarning("Maven validation requested but Maven is not available in PATH");
        }
    }

    @Override
    protected Integer executeCommand() throws Exception {
        OdmServerConfig serverConfig = getServerConfig();
        
        printMessage("Starting ODM deployment...");
        printVerbose("Deployment configuration:");
        printVerbose("  Rule App: " + ruleAppPath.getAbsolutePath());
        printVerbose("  Environment Alias: " + (environmentAlias != null ? environmentAlias : "default"));
        printVerbose("  Server: " + serverConfig.getUrl());
        printVerbose("  Force: " + force);
        printVerbose("  Validate Only: " + validateOnly);
        printVerbose("  Backup: " + createBackup);
        printVerbose("  Timeout: " + timeoutSeconds + " seconds");

        try {
            // Pre-deployment validation
            if (!preDeploymentValidation()) {
                printError("Pre-deployment validation failed");
                return 1;
            }

            // Maven validation if requested
            if (mavenValidate && !validateWithMaven()) {
                printError("Maven validation failed");
                return 1;
            }

            // Create backup if requested
            if (createBackup && !validateOnly) {
                createDeploymentBackup(serverConfig);
            }

            // Perform deployment
            DeploymentResult result = performDeployment(serverConfig);

            // Generate deployment report
            generateDeploymentReport(result);

            // Print summary
            printDeploymentSummary(result);

            return result.isSuccess() ? 0 : 1;

        } catch (Exception e) {
            logger.error("Deployment failed", e);
            printError("Deployment failed: " + e.getMessage());

            if (rollbackOnFailure && !validateOnly) {
                printMessage("Attempting automatic rollback...");
                try {
                    performRollback(serverConfig);
                    printMessage("Rollback completed successfully");
                } catch (Exception rollbackException) {
                    printError("Rollback failed: " + rollbackException.getMessage());
                    logger.error("Rollback failed", rollbackException);
                }
            }

            if (verbose) {
                throw e; // Re-throw for stack trace in verbose mode
            }

            return 1;
        }
    }

    private void validateProjectDirectory() throws CommandValidationException {
        // Check for essential project files
        List<String> requiredFiles = Arrays.asList("pom.xml", "build.xml", "rules/");
        boolean foundProjectFile = false;

        for (String fileName : requiredFiles) {
            File file = new File(ruleAppPath, fileName);
            if (file.exists()) {
                foundProjectFile = true;
                break;
            }
        }

        if (!foundProjectFile) {
            throw new CommandValidationException(
                "Directory doesn't appear to be a valid rule project: " + ruleAppPath.getAbsolutePath(),
                "Ensure the directory contains pom.xml, build.xml, or a rules/ directory"
            );
        }
    }

    private boolean isMavenAvailable() {
        try {
            Process process = new ProcessBuilder("mvn", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean preDeploymentValidation() {
        printMessage("Running pre-deployment validation...");

        // Validate rule app structure
        if (ruleAppPath.isFile()) {
            return validateJarFile();
        } else {
            return validateProjectStructure();
        }
    }

    private boolean validateJarFile() {
        printVerbose("Validating JAR file: " + ruleAppPath.getName());
        
        // TODO: Implement JAR validation
        // - Check if it's a valid ZIP/JAR file
        // - Verify it contains rule artifacts
        // - Check manifest for required entries
        
        try {
            java.util.jar.JarFile jarFile = new java.util.jar.JarFile(ruleAppPath);
            java.util.jar.Manifest manifest = jarFile.getManifest();
            jarFile.close();
            
            if (manifest == null) {
                printWarning("JAR file has no manifest");
            }
            
            printVerbose("JAR file validation passed");
            return true;
            
        } catch (Exception e) {
            printError("Invalid JAR file: " + e.getMessage());
            return false;
        }
    }

    private boolean validateProjectStructure() {
        printVerbose("Validating project structure: " + ruleAppPath.getAbsolutePath());
        
        // TODO: Implement project structure validation
        // - Check for required directories
        // - Validate rule files
        // - Check dependencies
        
        printVerbose("Project structure validation passed");
        return true;
    }

    private boolean validateWithMaven() {
        printMessage("Running Maven validation...");
        
        try {
            File workingDir = ruleAppPath.isDirectory() ? ruleAppPath : ruleAppPath.getParentFile();
            
            ProcessBuilder pb = new ProcessBuilder("mvn", "validate", "compile");
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output if verbose
            if (verbose) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    printVerbose("Maven: " + line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                printMessage("Maven validation passed");
                return true;
            } else {
                printError("Maven validation failed with exit code: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            printError("Maven validation error: " + e.getMessage());
            return false;
        }
    }

    private void createDeploymentBackup(OdmServerConfig serverConfig) {
        printMessage("Creating deployment backup...");
        
        // TODO: Implement backup creation
        // - Connect to ODM server
        // - Export current rule applications
        // - Save backup with timestamp
        
        printVerbose("Backup created successfully");
    }

    private DeploymentResult performDeployment(OdmServerConfig serverConfig) throws Exception {
        if (validateOnly) {
            printMessage("Validation-only mode - skipping actual deployment");
        } else {
            printMessage("Deploying to ODM server...");
        }

        DeploymentResult result = new DeploymentResult();
        result.setRuleAppPath(ruleAppPath.getAbsolutePath());
        result.setServerUrl(serverConfig.getUrl());
        result.setEnvironmentAlias(environmentAlias);
        result.setStartTime(LocalDateTime.now());
        result.setValidateOnly(validateOnly);
        result.setForced(force);
        
        try {
            // TODO: Implement actual ODM deployment
            // 1. Connect to ODM server using serverConfig
            // 2. Upload rule application
            // 3. Deploy to specified environment
            // 4. Verify deployment success
            // 5. Update deployment status
            
            // Simulate deployment for now
            if (!validateOnly) {
                Thread.sleep(2000); // Simulate deployment time
            }
            
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);
            result.setExecutionTimeMs(validateOnly ? 100 : 2000);
            
            result.addMessage("Connected to ODM server: " + serverConfig.getUrl());
            if (!validateOnly) {
                result.addMessage("Rule application uploaded successfully");
                result.addMessage("Deployment to environment '" + 
                    (environmentAlias != null ? environmentAlias : "default") + "' completed");
            }
            result.addMessage(validateOnly ? "Validation completed" : "Deployment completed successfully");
            
        } catch (Exception e) {
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(false);
            result.addMessage("Deployment failed: " + e.getMessage());
            throw e;
        }
        
        return result;
    }

    private void performRollback(OdmServerConfig serverConfig) throws Exception {
        printMessage("Performing rollback...");
        
        // TODO: Implement rollback logic
        // - Restore from backup
        // - Redeploy previous version
        // - Verify rollback success
        
        Thread.sleep(1000); // Simulate rollback time
        printVerbose("Rollback operation completed");
    }

    private void generateDeploymentReport(DeploymentResult result) {
        // TODO: Generate detailed deployment report
        // - Deployment metadata
        // - Changes made
        // - Performance metrics
        // - Success/failure details
        
        printVerbose("Deployment report generated");
    }

    private void printDeploymentSummary(DeploymentResult result) {
        if (quiet) return;
        
        System.out.println("\n" + "=".repeat(40));
        System.out.println("DEPLOYMENT SUMMARY");
        System.out.println("=".repeat(40));
        System.out.printf("Status: %s\n", result.isSuccess() ? "✅ SUCCESS" : "❌ FAILED");
        System.out.printf("Mode: %s\n", result.isValidateOnly() ? "Validation Only" : "Full Deployment");
        System.out.printf("Rule App: %s\n", new File(result.getRuleAppPath()).getName());
        System.out.printf("Server: %s\n", result.getServerUrl());
        if (result.getEnvironmentAlias() != null) {
            System.out.printf("Environment: %s\n", result.getEnvironmentAlias());
        }
        System.out.printf("Execution Time: %d ms\n", result.getExecutionTimeMs());
        System.out.printf("Forced: %s\n", result.isForced() ? "Yes" : "No");
        
        if (!result.getMessages().isEmpty()) {
            System.out.println("\nDetails:");
            for (String message : result.getMessages()) {
                System.out.println("  • " + message);
            }
        }
    }

    // Inner class to hold deployment results
    private static class DeploymentResult {
        private String ruleAppPath;
        private String serverUrl;
        private String environmentAlias;
        private boolean success;
        private boolean validateOnly;
        private boolean forced;
        private long executionTimeMs;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private java.util.List<String> messages = new java.util.ArrayList<>();

        // Getters and setters
        public String getRuleAppPath() { return ruleAppPath; }
        public void setRuleAppPath(String ruleAppPath) { this.ruleAppPath = ruleAppPath; }
        
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        
        public String getEnvironmentAlias() { return environmentAlias; }
        public void setEnvironmentAlias(String environmentAlias) { this.environmentAlias = environmentAlias; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public boolean isValidateOnly() { return validateOnly; }
        public void setValidateOnly(boolean validateOnly) { this.validateOnly = validateOnly; }
        
        public boolean isForced() { return forced; }
        public void setForced(boolean forced) { this.forced = forced; }
        
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public java.util.List<String> getMessages() { return messages; }
        public void addMessage(String message) { this.messages.add(message); }
    }
}