// src/main/java/com/mceballos/odmcli/OdmCli.java
package com.mceballos.odmcli;

import com.mceballos.odmcli.config.ConfigLoader;
import com.mceballos.odmcli.config.OdmServerConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine.ParentCommand;


import java.io.File;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Main CLI application for IBM ODM operations.
 * This class uses Picocli to define commands and options.
 */
@Command(name = "odm-cli",
         mixinStandardHelpOptions = true,
         version = "odm-cli 0.0.1",
         description = "A command-line tool for IBM ODM operations.",
         subcommands = {
             OdmCli.TestCommand.class,
             OdmCli.DeployCommand.class,
             OdmCli.ReportCommand.class
         })
public class OdmCli implements Callable<Integer> {

    @Spec
    CommandSpec spec; // Injected by Picocli

    // Common options for all commands (inherited by subcommands)
    @Option(names = {"-e", "--env"},
            description = "Environment/Server to target (e.g., 'QA', 'PRD', 'lower_regions:SYSI', 'production_regions:PRD'). " +
                          "If only a group name is given (e.g., 'lower_regions'), the first server in that group will be used. " +
                          "If omitted, 'default' server from config will be used.",
            paramLabel = "GROUP[:SERVER_NAME]")
    private String environmentTarget; // This will hold "QA" or "lower_regions:SYSI"

    // Removed direct -u, -usr, -p options as they will be loaded from config
    // @Option(names = {"-u", "--url"}, description = "URL of the ODM Decision Center or Decision Server.")
    // private String odmUrl;
    // ...

    private OdmServerConfig currentServerConfig; // Resolved server config for the current command

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OdmCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Welcome to ODM CLI! Use 'odm-cli --help' for available commands.");
        return 0; // Success
    }

    // Method to resolve server config based on the provided environmentTarget
    // This will be called by each subcommand
    private OdmServerConfig resolveServerConfig() {
        if (currentServerConfig != null) {
            return currentServerConfig; // Already resolved
        }

        String groupName = null;
        String serverName = null;

        if (environmentTarget != null && !environmentTarget.isEmpty()) {
            String[] parts = environmentTarget.split(":");
            groupName = parts[0];
            if (parts.length > 1) {
                serverName = parts[1];
            }
        } else {
            // No --env specified, try to use "default" from config
            groupName = "default";
        }

        Optional<OdmServerConfig> resolvedConfig = ConfigLoader.resolveServerConfig(groupName, serverName);

        if (resolvedConfig.isPresent()) {
            this.currentServerConfig = resolvedConfig.get();
            System.out.println("Targeting ODM server: " + this.currentServerConfig.getUrl());
            // TODO: In a real app, you'd use username/password for authentication
            // System.out.println("Username: " + this.currentServerConfig.getUsername()); // For debugging, remove in prod
            return this.currentServerConfig;
        } else {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Environment/server '%s' not found in configuration. " +
                                  "Please check 'odm-config.yaml' or specify a valid target.", environmentTarget != null ? environmentTarget : "default"));
        }
    }


    /**
     * Subcommand for testing rule applications or rule sets.
     */
    @Command(name = "test",
             description = "Tests a rule application or rule set against provided data.")
    static class TestCommand implements Callable<Integer> {

        @ParentCommand
        OdmCli parent; // Injected by Picocli to access parent command's fields

        @Parameters(index = "0", description = "Type of test (e.g., 'ruleapp', 'ruleset', 'scenario').")
        private String testType;

        @Parameters(index = "1", description = "Path to the rule application/set identifier or scenario file.")
        private String identifier;

        @Option(names = {"-d", "--data"}, description = "Path to input data file (e.g., JSON, XML).", required = true)
        private File dataFile;

        @Option(names = {"-r", "--report-format"}, description = "Output report format (e.g., 'json', 'xml', 'html').", defaultValue = "json")
        private String reportFormat;

        @Override
        public Integer call() throws Exception {
            OdmServerConfig serverConfig = parent.resolveServerConfig(); // Resolve server config

            System.out.printf("Running %s test for '%s' on %s with data from '%s'...\n",
                    testType, identifier, serverConfig.getUrl(), dataFile.getAbsolutePath());

            // TODO:
            // 1. Use serverConfig.getUrl(), serverConfig.getUsername(), serverConfig.getPassword()
            //    to authenticate and connect to the specific ODM RES server.
            // 2. Load and parse the input data from dataFile.
            // 3. Invoke ODM Decision Server to execute the rule app/set.
            // 4. Process the execution results.
            // 5. Generate a testing report in the specified reportFormat.
            // 6. Print success/failure status and report path.

            System.out.println("Test simulation complete. (Integration logic to be added here)");
            return 0; // Success
        }
    }

    /**
     * Subcommand for deploying rule applications.
     */
    @Command(name = "deploy",
             description = "Deploys a rule application to ODM Decision Center or Decision Server.")
    static class DeployCommand implements Callable<Integer> {

        @ParentCommand
        OdmCli parent;

        @Parameters(index = "0", description = "Path to the RuleApp archive (.jar file) or project source.")
        private File ruleAppPath;

        @Option(names = {"-env", "--environment-alias"}, description = "Deployment target alias (e.g., 'dev', 'qa', 'prod').")
        private String environmentAlias; // Renamed to avoid clash with --env option

        @Option(names = {"--force"}, description = "Force deployment, overwriting existing versions.")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            OdmServerConfig serverConfig = parent.resolveServerConfig(); // Resolve server config

            System.out.printf("Deploying '%s' to environment '%s' on %s (Force: %b)...\n",
                    ruleAppPath.getAbsolutePath(), environmentAlias, serverConfig.getUrl(), force);

            // TODO:
            // 1. Use serverConfig.getUrl(), username, password for ODM interactions.
            // 2. Validate the build using Maven (programmatically or via ProcessBuilder).
            // 3. Integrate with Eclipse plug-ins for validation (if applicable).
            // 4. Perform the actual deployment using ODM APIs.
            // 5. Generate a deployment report.

            System.out.println("Deployment simulation complete. (Integration logic to be added here)");
            return 0; // Success
        }
    }

    /**
     * Subcommand for generating various reports.
     */
    @Command(name = "report",
             description = "Generates various reports (e.g., deployment, testing summary).")
    static class ReportCommand implements Callable<Integer> {

        @ParentCommand
        OdmCli parent;

        @Parameters(index = "0", description = "Type of report to generate (e.g., 'deployment-summary', 'test-summary', 'impact-analysis').")
        private String reportType;

        @Option(names = {"-o", "--output"}, description = "Output directory for the report.", required = true)
        private File outputDir;

        @Option(names = {"-f", "--format"}, description = "Output format (e.g., 'pdf', 'csv', 'html').", defaultValue = "html")
        private String format;

        @Override
        public Integer call() throws Exception {
            OdmServerConfig serverConfig = parent.resolveServerConfig(); // Resolve server config

            System.out.printf("Generating '%s' report in '%s' format to '%s' using %s...\n",
                    reportType, format, outputDir.getAbsolutePath(), serverConfig.getUrl());

            // TODO:
            // 1. Collect data based on reportType from ODM using serverConfig.
            // 2. Process and format the data using a reporting library.
            // 3. Save the report to the outputDir.

            System.out.println("Report generation simulation complete. (Integration logic to be added here)");
            return 0; // Success
        }
    }
}