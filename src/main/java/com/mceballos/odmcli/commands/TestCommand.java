// src/main/java/com/mceballos/odmcli/commands/TestCommand.java
package com.mceballos.odmcli.commands;

import com.mceballos.odmcli.config.OdmServerConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Command for testing rule applications or rule sets against provided data.
 * 
 * Usage examples:
 *   odm-cli test ruleapp myapp.jar -d data.json -e QA
 *   odm-cli test ruleset "MyRuleSet/1.0" -d input.xml -r xml -e production_regions:PRD
 *   odm-cli test scenario scenario.json -e SYSI --verbose
 */
@Command(name = "test",
         description = "Tests a rule application or rule set against provided data.",
         mixinStandardHelpOptions = true)
public class TestCommand extends BaseCommand {

    @Parameters(index = "0", 
                description = "Type of test: 'ruleapp', 'ruleset', or 'scenario'")
    private String testType;

    @Parameters(index = "1", 
                description = "Path to the rule application/set identifier or scenario file.\n" +
                             "Examples:\n" +
                             "  - For ruleapp: path to .jar file\n" +
                             "  - For ruleset: 'RuleSetName/Version'\n" +
                             "  - For scenario: path to scenario file")
    private String identifier;

    @Option(names = {"-d", "--data"}, 
            description = "Path to input data file (JSON, XML, etc.)", 
            required = true)
    private File dataFile;

    @Option(names = {"-r", "--report-format"}, 
            description = "Output report format: json, xml, html, csv",
            defaultValue = "json")
    private String reportFormat;

    @Option(names = {"-o", "--output"}, 
            description = "Output file for test results. If not specified, prints to console.")
    private File outputFile;

    @Option(names = {"--timeout"}, 
            description = "Timeout for test execution in seconds",
            defaultValue = "60")
    private int timeoutSeconds;

    @Option(names = {"--trace"}, 
            description = "Enable rule execution tracing")
    private boolean enableTrace;

    @Override
    protected void validateCommand() throws CommandValidationException {
        // Validate test type
        if (!isValidTestType(testType)) {
            throw new CommandValidationException(
                "Invalid test type: " + testType,
                "Use 'ruleapp', 'ruleset', or 'scenario'"
            );
        }

        // Validate data file
        validateFileExists(dataFile, "data file");

        // Validate report format
        if (!isValidReportFormat(reportFormat)) {
            throw new CommandValidationException(
                "Invalid report format: " + reportFormat,
                "Use 'json', 'xml', 'html', or 'csv'"
            );
        }

        // Validate identifier based on test type
        validateIdentifier();

        // Validate output file directory if specified
        if (outputFile != null) {
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                ensureOutputDirectory(parentDir);
            }
        }

        // Validate timeout
        if (timeoutSeconds <= 0) {
            throw new CommandValidationException(
                "Timeout must be positive: " + timeoutSeconds,
                "Use a value greater than 0"
            );
        }
    }

    @Override
    protected Integer executeCommand() throws Exception {
        OdmServerConfig serverConfig = getServerConfig();
        
        printMessage("Starting ODM test execution...");
        printVerbose("Test configuration:");
        printVerbose("  Type: " + testType);
        printVerbose("  Identifier: " + identifier);
        printVerbose("  Data file: " + dataFile.getAbsolutePath());
        printVerbose("  Report format: " + reportFormat);
        printVerbose("  Server: " + serverConfig.getUrl());
        printVerbose("  Timeout: " + timeoutSeconds + " seconds");
        printVerbose("  Tracing: " + (enableTrace ? "enabled" : "disabled"));

        try {
            // Execute the test based on type
            TestResult result = executeTest(serverConfig);
            
            // Generate and save/display report
            generateReport(result);
            
            // Print summary
            printTestSummary(result);
            
            return result.isSuccess() ? 0 : 1;
            
        } catch (Exception e) {
            logger.error("Test execution failed", e);
            printError("Test execution failed: " + e.getMessage());
            
            if (verbose) {
                throw e; // Re-throw for stack trace in verbose mode
            }
            
            return 1;
        }
    }

    private void validateIdentifier() throws CommandValidationException {
        switch (testType.toLowerCase()) {
            case "ruleapp":
                validateRuleAppIdentifier();
                break;
            case "ruleset":
                validateRuleSetIdentifier();
                break;
            case "scenario":
                validateScenarioIdentifier();
                break;
        }
    }

    private void validateRuleAppIdentifier() throws CommandValidationException {
        File ruleAppFile = new File(identifier);
        validateFileExists(ruleAppFile, "rule application file");
        
        if (!identifier.toLowerCase().endsWith(".jar")) {
            printWarning("Rule application file doesn't have .jar extension: " + identifier);
        }
    }

    private void validateRuleSetIdentifier() throws CommandValidationException {
        if (!identifier.contains("/")) {
            throw new CommandValidationException(
                "Rule set identifier must include version: " + identifier,
                "Use format 'RuleSetName/Version' (e.g., 'MyRuleSet/1.0')"
            );
        }
        
        String[] parts = identifier.split("/");
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            throw new CommandValidationException(
                "Invalid rule set identifier format: " + identifier,
                "Use format 'RuleSetName/Version' (e.g., 'MyRuleSet/1.0')"
            );
        }
    }

    private void validateScenarioIdentifier() throws CommandValidationException {
        File scenarioFile = new File(identifier);
        validateFileExists(scenarioFile, "scenario file");
    }

    private TestResult executeTest(OdmServerConfig serverConfig) throws Exception {
        printMessage("Executing " + testType + " test...");
        
        // TODO: Implement actual ODM integration
        // This is where you would:
        // 1. Connect to ODM server using serverConfig
        // 2. Load the data file
        // 3. Execute the rule application/set
        // 4. Collect results and trace information
        
        // Simulate test execution for now
        Thread.sleep(1000); // Simulate processing time
        
        // Create mock result
        TestResult result = new TestResult();
        result.setTestType(testType);
        result.setIdentifier(identifier);
        result.setDataFile(dataFile.getAbsolutePath());
        result.setServerUrl(serverConfig.getUrl());
        result.setStartTime(LocalDateTime.now().minusSeconds(1));
        result.setEndTime(LocalDateTime.now());
        result.setSuccess(true);
        result.setExecutionTimeMs(1000);
        
        // Add some mock output
        result.addMessage("Rule execution completed successfully");
        result.addMessage("Processed 1 input object");
        result.addMessage("Applied 5 rules");
        
        if (enableTrace) {
            result.addMessage("Trace: Rule 'ValidateInput' fired");
            result.addMessage("Trace: Rule 'CalculateDiscount' fired");
            result.addMessage("Trace: Rule 'ApplyBusinessLogic' fired");
        }
        
        printVerbose("Test execution completed in " + result.getExecutionTimeMs() + "ms");
        
        return result;
    }

    private void generateReport(TestResult result) throws Exception {
        String reportContent = formatReport(result);
        
        if (outputFile != null) {
            // Save to file
            java.nio.file.Files.write(outputFile.toPath(), reportContent.getBytes());
            printMessage("Test report saved to: " + outputFile.getAbsolutePath());
        } else {
            // Print to console
            if (!quiet) {
                System.out.println("\n" + "=".repeat(50));
                System.out.println("TEST REPORT");
                System.out.println("=".repeat(50));
                System.out.println(reportContent);
                System.out.println("=".repeat(50));
            }
        }
    }

    private String formatReport(TestResult result) {
        switch (reportFormat.toLowerCase()) {
            case "json":
                return formatJsonReport(result);
            case "xml":
                return formatXmlReport(result);
            case "html":
                return formatHtmlReport(result);
            case "csv":
                return formatCsvReport(result);
            default:
                return formatJsonReport(result); // Default fallback
        }
    }

    private String formatJsonReport(TestResult result) {
        // TODO: Use a proper JSON library like Jackson
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"testType\": \"").append(result.getTestType()).append("\",\n");
        json.append("  \"identifier\": \"").append(result.getIdentifier()).append("\",\n");
        json.append("  \"dataFile\": \"").append(result.getDataFile()).append("\",\n");
        json.append("  \"serverUrl\": \"").append(result.getServerUrl()).append("\",\n");
        json.append("  \"success\": ").append(result.isSuccess()).append(",\n");
        json.append("  \"executionTimeMs\": ").append(result.getExecutionTimeMs()).append(",\n");
        json.append("  \"startTime\": \"").append(result.getStartTime()).append("\",\n");
        json.append("  \"endTime\": \"").append(result.getEndTime()).append("\",\n");
        json.append("  \"messages\": [\n");
        
        for (int i = 0; i < result.getMessages().size(); i++) {
            json.append("    \"").append(result.getMessages().get(i)).append("\"");
            if (i < result.getMessages().size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
    }

    private String formatXmlReport(TestResult result) {
        // TODO: Use a proper XML library
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testResult>\n");
        xml.append("  <testType>").append(result.getTestType()).append("</testType>\n");
        xml.append("  <identifier>").append(result.getIdentifier()).append("</identifier>\n");
        xml.append("  <dataFile>").append(result.getDataFile()).append("</dataFile>\n");
        xml.append("  <serverUrl>").append(result.getServerUrl()).append("</serverUrl>\n");
        xml.append("  <success>").append(result.isSuccess()).append("</success>\n");
        xml.append("  <executionTimeMs>").append(result.getExecutionTimeMs()).append("</executionTimeMs>\n");
        xml.append("  <startTime>").append(result.getStartTime()).append("</startTime>\n");
        xml.append("  <endTime>").append(result.getEndTime()).append("</endTime>\n");
        xml.append("  <messages>\n");
        
        for (String message : result.getMessages()) {
            xml.append("    <message>").append(message).append("</message>\n");
        }
        
        xml.append("  </messages>\n");
        xml.append("</testResult>");
        
        return xml.toString();
    }

    private String formatHtmlReport(TestResult result) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<title>ODM Test Report</title>\n");
        html.append("<style>body{font-family:Arial,sans-serif;margin:20px;}</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<h1>ODM Test Report</h1>\n");
        html.append("<table border='1' cellpadding='5'>\n");
        html.append("<tr><td><strong>Test Type</strong></td><td>").append(result.getTestType()).append("</td></tr>\n");
        html.append("<tr><td><strong>Identifier</strong></td><td>").append(result.getIdentifier()).append("</td></tr>\n");
        html.append("<tr><td><strong>Data File</strong></td><td>").append(result.getDataFile()).append("</td></tr>\n");
        html.append("<tr><td><strong>Server URL</strong></td><td>").append(result.getServerUrl()).append("</td></tr>\n");
        html.append("<tr><td><strong>Success</strong></td><td>").append(result.isSuccess() ? "✅ Yes" : "❌ No").append("</td></tr>\n");
        html.append("<tr><td><strong>Execution Time</strong></td><td>").append(result.getExecutionTimeMs()).append(" ms</td></tr>\n");
        html.append("</table>\n");
        html.append("<h2>Messages</h2>\n<ul>\n");
        
        for (String message : result.getMessages()) {
            html.append("<li>").append(message).append("</li>\n");
        }
        
        html.append("</ul>\n</body>\n</html>");
        
        return html.toString();
    }

    private String formatCsvReport(TestResult result) {
        StringBuilder csv = new StringBuilder();
        csv.append("Field,Value\n");
        csv.append("Test Type,").append(result.getTestType()).append("\n");
        csv.append("Identifier,").append(result.getIdentifier()).append("\n");
        csv.append("Data File,").append(result.getDataFile()).append("\n");
        csv.append("Server URL,").append(result.getServerUrl()).append("\n");
        csv.append("Success,").append(result.isSuccess()).append("\n");
        csv.append("Execution Time (ms),").append(result.getExecutionTimeMs()).append("\n");
        csv.append("Start Time,").append(result.getStartTime()).append("\n");
        csv.append("End Time,").append(result.getEndTime()).append("\n");
        
        return csv.toString();
    }

    private void printTestSummary(TestResult result) {
        if (quiet) return;
        
        System.out.println("\n" + "=".repeat(30));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(30));
        System.out.printf("Status: %s\n", result.isSuccess() ? "✅ PASSED" : "❌ FAILED");
        System.out.printf("Execution Time: %d ms\n", result.getExecutionTimeMs());
        System.out.printf("Messages: %d\n", result.getMessages().size());
        
        if (outputFile != null) {
            System.out.printf("Report: %s\n", outputFile.getAbsolutePath());
        }
    }

    private boolean isValidTestType(String type) {
        return type != null && 
               ("ruleapp".equalsIgnoreCase(type) || 
                "ruleset".equalsIgnoreCase(type) || 
                "scenario".equalsIgnoreCase(type));
    }

    private boolean isValidReportFormat(String format) {
        return format != null && 
               ("json".equalsIgnoreCase(format) || 
                "xml".equalsIgnoreCase(format) || 
                "html".equalsIgnoreCase(format) || 
                "csv".equalsIgnoreCase(format));
    }

    // Inner class to hold test results
    private static class TestResult {
        private String testType;
        private String identifier;
        private String dataFile;
        private String serverUrl;
        private boolean success;
        private long executionTimeMs;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private java.util.List<String> messages = new java.util.ArrayList<>();

        // Getters and setters
        public String getTestType() { return testType; }
        public void setTestType(String testType) { this.testType = testType; }
        
        public String getIdentifier() { return identifier; }
        public void setIdentifier(String identifier) { this.identifier = identifier; }
        
        public String getDataFile() { return dataFile; }
        public void setDataFile(String dataFile) { this.dataFile = dataFile; }
        
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
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