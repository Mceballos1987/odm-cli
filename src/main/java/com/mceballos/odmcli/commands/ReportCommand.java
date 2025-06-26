// src/main/java/com/mceballos/odmcli/commands/ReportCommand.java
package com.mceballos.odmcli.commands;

import com.mceballos.odmcli.config.OdmServerConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Command for generating various ODM reports.
 * 
 * Usage examples:
 *   odm-cli report deployment-summary -o ./reports -f html -e QA
 *   odm-cli report test-summary -o /tmp/reports -f pdf --from 2024-01-01 --to 2024-12-31
 *   odm-cli report impact-analysis -o reports/ -f csv --rule-set "MyRules/1.0"
 */
@Command(name = "report",
         description = "Generates various reports (deployment, testing summary, impact analysis, etc.).",
         mixinStandardHelpOptions = true)
public class ReportCommand extends BaseCommand {

    @Parameters(index = "0", 
                description = "Type of report to generate:\n" +
                             "  • deployment-summary: Summary of recent deployments\n" +
                             "  • test-summary: Summary of test executions\n" +
                             "  • impact-analysis: Analysis of rule changes impact\n" +
                             "  • performance: Performance metrics and trends\n" +
                             "  • audit: Audit trail of ODM operations")
    private String reportType;

    @Option(names = {"-o", "--output"}, 
            description = "Output directory for the report", 
            required = true)
    private File outputDir;

    @Option(names = {"-f", "--format"}, 
            description = "Output format: html, pdf, csv, json, xlsx",
            defaultValue = "html")
    private String format;

    @Option(names = {"--from"}, 
            description = "Start date for report data (yyyy-MM-dd)")
    private String fromDate;

    @Option(names = {"--to"}, 
            description = "End date for report data (yyyy-MM-dd)")
    private String toDate;

    @Option(names = {"--rule-set"}, 
            description = "Filter by specific rule set (for impact analysis)")
    private String ruleSet;

    @Option(names = {"--environment"}, 
            description = "Filter by environment (overrides --env for report filtering)")
    private String environmentFilter;

    @Option(names = {"--include-details"}, 
            description = "Include detailed information in the report")
    private boolean includeDetails;

    @Option(names = {"--template"}, 
            description = "Custom report template file")
    private File templateFile;

    @Option(names = {"--max-records"}, 
            description = "Maximum number of records to include",
            defaultValue = "1000")
    private int maxRecords;

    private static final List<String> VALID_REPORT_TYPES = Arrays.asList(
        "deployment-summary", "test-summary", "impact-analysis", "performance", "audit"
    );

    private static final List<String> VALID_FORMATS = Arrays.asList(
        "html", "pdf", "csv", "json", "xlsx"
    );

    @Override
    protected void validateCommand() throws CommandValidationException {
        // Validate report type
        if (!VALID_REPORT_TYPES.contains(reportType.toLowerCase())) {
            throw new CommandValidationException(
                "Invalid report type: " + reportType,
                "Valid types are: " + String.join(", ", VALID_REPORT_TYPES)
            );
        }

        // Validate output directory
        ensureOutputDirectory(outputDir);

        // Validate format
        if (!VALID_FORMATS.contains(format.toLowerCase())) {
            throw new CommandValidationException(
                "Invalid output format: " + format,
                "Valid formats are: " + String.join(", ", VALID_FORMATS)
            );
        }

        // Validate date range
        validateDateRange();

        // Validate template file if provided
        if (templateFile != null) {
            validateFileExists(templateFile, "template file");
        }

        // Validate max records
        if (maxRecords <= 0) {
            throw new CommandValidationException(
                "Max records must be positive: " + maxRecords,
                "Use a value greater than 0"
            );
        }

        // Validate rule set format if provided
        if (ruleSet != null && !ruleSet.isEmpty()) {
            if (!ruleSet.contains("/")) {
                printWarning("Rule set should include version (e.g., 'RuleSetName/1.0'): " + ruleSet);
            }
        }
    }

    @Override
    protected Integer executeCommand() throws Exception {
        OdmServerConfig serverConfig = getServerConfig();
        
        printMessage("Generating ODM report...");
        printVerbose("Report configuration:");
        printVerbose("  Type: " + reportType);
        printVerbose("  Output: " + outputDir.getAbsolutePath());
        printVerbose("  Format: " + format);
        printVerbose("  Server: " + serverConfig.getUrl());
        if (fromDate != null) printVerbose("  From Date: " + fromDate);
        if (toDate != null) printVerbose("  To Date: " + toDate);
        if (ruleSet != null) printVerbose("  Rule Set Filter: " + ruleSet);
        if (environmentFilter != null) printVerbose("  Environment Filter: " + environmentFilter);
        printVerbose("  Include Details: " + includeDetails);
        printVerbose("  Max Records: " + maxRecords);

        try {
            // Collect data for the report
            ReportData reportData = collectReportData(serverConfig);
            
            // Generate the report
            ReportResult result = generateReport(reportData);
            
            // Save the report to file
            File reportFile = saveReport(result);
            
            // Print summary
            printReportSummary(result, reportFile);
            
            return 0; // Success
            
        } catch (Exception e) {
            logger.error("Report generation failed", e);
            printError("Report generation failed: " + e.getMessage());
            
            if (verbose) {
                throw e; // Re-throw for stack trace in verbose mode
            }
            
            return 1;
        }
    }

    private void validateDateRange() throws CommandValidationException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        LocalDateTime fromDateTime = null;
        LocalDateTime toDateTime = null;
        
        if (fromDate != null) {
            try {
                fromDateTime = LocalDateTime.parse(fromDate + "T00:00:00");
            } catch (Exception e) {
                throw new CommandValidationException(
                    "Invalid from date format: " + fromDate,
                    "Use format yyyy-MM-dd (e.g., 2024-01-01)"
                );
            }
        }
        
        if (toDate != null) {
            try {
                toDateTime = LocalDateTime.parse(toDate + "T23:59:59");
            } catch (Exception e) {
                throw new CommandValidationException(
                    "Invalid to date format: " + toDate,
                    "Use format yyyy-MM-dd (e.g., 2024-12-31)"
                );
            }
        }
        
        if (fromDateTime != null && toDateTime != null) {
            if (fromDateTime.isAfter(toDateTime)) {
                throw new CommandValidationException(
                    "From date cannot be after to date",
                    "Ensure from date (" + fromDate + ") is before to date (" + toDate + ")"
                );
            }
        }
    }

    private ReportData collectReportData(OdmServerConfig serverConfig) throws Exception {
        printMessage("Collecting data for " + reportType + " report...");
        
        ReportData data = new ReportData();
        data.setReportType(reportType);
        data.setServerUrl(serverConfig.getUrl());
        data.setGenerationTime(LocalDateTime.now());
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        data.setRuleSetFilter(ruleSet);
        data.setEnvironmentFilter(environmentFilter);
        data.setMaxRecords(maxRecords);
        
        // TODO: Implement actual data collection based on report type
        // This would connect to ODM and gather the required information
        
        switch (reportType.toLowerCase()) {
            case "deployment-summary":
                collectDeploymentData(data, serverConfig);
                break;
            case "test-summary":
                collectTestData(data, serverConfig);
                break;
            case "impact-analysis":
                collectImpactAnalysisData(data, serverConfig);
                break;
            case "performance":
                collectPerformanceData(data, serverConfig);
                break;
            case "audit":
                collectAuditData(data, serverConfig);
                break;
        }
        
        printVerbose("Collected " + data.getRecordCount() + " records");
        return data;
    }

    private void collectDeploymentData(ReportData data, OdmServerConfig serverConfig) {
        printVerbose("Collecting deployment data...");
        
        // TODO: Connect to ODM and collect deployment information
        // - Recent deployments
        // - Success/failure rates
        // - Deployment times
        // - Rule set versions
        
        // Mock data for now
        data.addRecord("deployment", "MyRuleApp", "2024-01-15", "SUCCESS", "QA");
        data.addRecord("deployment", "AnotherApp", "2024-01-14", "FAILED", "PROD");
        data.addRecord("deployment", "TestApp", "2024-01-13", "SUCCESS", "DEV");
    }

    private void collectTestData(ReportData data, OdmServerConfig serverConfig) {
        printVerbose("Collecting test execution data...");
        
        // TODO: Collect test execution history
        // - Test results
        // - Execution times
        // - Rule coverage
        // - Error patterns
        
        // Mock data for now
        data.addRecord("test", "RuleApp Test", "2024-01-15", "PASSED", "98%");
        data.addRecord("test", "Integration Test", "2024-01-14", "FAILED", "75%");
        data.addRecord("test", "Smoke Test", "2024-01-13", "PASSED", "100%");
    }

    private void collectImpactAnalysisData(ReportData data, OdmServerConfig serverConfig) {
        printVerbose("Collecting impact analysis data...");
        
        // TODO: Analyze rule dependencies and impacts
        // - Rule dependencies
        // - Change impact
        // - Affected components
        
        // Mock data for now
        data.addRecord("impact", "Rule: ValidateInput", "High", "3 dependent rules", "Core validation");
        data.addRecord("impact", "Rule: CalculateDiscount", "Medium", "1 dependent rule", "Pricing logic");
    }

    private void collectPerformanceData(ReportData data, OdmServerConfig serverConfig) {
        printVerbose("Collecting performance data...");
        
        // TODO: Collect performance metrics
        // - Execution times
        // - Memory usage
        // - Throughput
        // - Trends
        
        // Mock data for now
        data.addRecord("performance", "Average Response Time", "125ms", "Improved", "Last 30 days");
        data.addRecord("performance", "Memory Usage", "512MB", "Stable", "Peak usage");
    }

    private void collectAuditData(ReportData data, OdmServerConfig serverConfig) {
        printVerbose("Collecting audit trail data...");
        
        // TODO: Collect audit information
        // - User actions
        // - System changes
        // - Access logs
        
        // Mock data for now
        data.addRecord("audit", "user123", "DEPLOY", "2024-01-15 10:30", "MyRuleApp");
        data.addRecord("audit", "user456", "TEST", "2024-01-15 09:15", "TestSuite");
    }

    private ReportResult generateReport(ReportData data) throws Exception {
        printMessage("Generating " + format + " report...");
        
        ReportResult result = new ReportResult();
        result.setReportType(reportType);
        result.setFormat(format);
        result.setRecordCount(data.getRecordCount());
        result.setGenerationTime(LocalDateTime.now());
        
        String content;
        String fileExtension;
        
        switch (format.toLowerCase()) {
            case "html":
                content = generateHtmlReport(data);
                fileExtension = ".html";
                break;
            case "pdf":
                content = generatePdfReport(data);
                fileExtension = ".pdf";
                break;
            case "csv":
                content = generateCsvReport(data);
                fileExtension = ".csv";
                break;
            case "json":
                content = generateJsonReport(data);
                fileExtension = ".json";
                break;
            case "xlsx":
                content = generateExcelReport(data);
                fileExtension = ".xlsx";
                break;
            default:
                throw new CommandValidationException("Unsupported format: " + format);
        }
        
        result.setContent(content);
        result.setFileExtension(fileExtension);
        
        return result;
    }

    private String generateHtmlReport(ReportData data) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<title>ODM ").append(data.getReportType().toUpperCase()).append(" Report</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("table { border-collapse: collapse; width: 100%; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f2f2f2; }\n");
        html.append(".header { background-color: #4CAF50; color: white; padding: 10px; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        
        html.append("<div class='header'>\n");
        html.append("<h1>ODM ").append(data.getReportType().toUpperCase()).append(" Report</h1>\n");
        html.append("<p>Generated: ").append(data.getGenerationTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        html.append("<p>Server: ").append(data.getServerUrl()).append("</p>\n");
        if (data.getFromDate() != null || data.getToDate() != null) {
            html.append("<p>Date Range: ");
            if (data.getFromDate() != null) html.append(data.getFromDate());
            html.append(" to ");
            if (data.getToDate() != null) html.append(data.getToDate());
            html.append("</p>\n");
        }
        html.append("</div>\n");
        
        html.append("<h2>Summary</h2>\n");
        html.append("<p>Total Records: ").append(data.getRecordCount()).append("</p>\n");
        
        html.append("<h2>Data</h2>\n");
        html.append("<table>\n");
        
        // Add table headers based on report type
        html.append("<tr>");
        switch (data.getReportType().toLowerCase()) {
            case "deployment-summary":
                html.append("<th>Type</th><th>Application</th><th>Date</th><th>Status</th><th>Environment</th>");
                break;
            case "test-summary":
                html.append("<th>Type</th><th>Test Name</th><th>Date</th><th>Result</th><th>Coverage</th>");
                break;
            default:
                html.append("<th>Category</th><th>Item</th><th>Value</th><th>Status</th><th>Details</th>");
        }
        html.append("</tr>\n");
        
        // Add data rows
        for (java.util.Map<String, String> record : data.getRecords()) {
            html.append("<tr>");
            for (String value : record.values()) {
                html.append("<td>").append(value != null ? value : "").append("</td>");
            }
            html.append("</tr>\n");
        }
        
        html.append("</table>\n");
        html.append("</body>\n</html>");
        
        return html.toString();
    }

    private String generatePdfReport(ReportData data) {
        // TODO: Implement PDF generation using a library like iText
        // For now, return a placeholder
        return "PDF report generation not yet implemented. Use HTML format instead.";
    }

    private String generateCsvReport(ReportData data) {
        StringBuilder csv = new StringBuilder();
        
        // Add headers based on report type
        switch (data.getReportType().toLowerCase()) {
            case "deployment-summary":
                csv.append("Type,Application,Date,Status,Environment\n");
                break;
            case "test-summary":
                csv.append("Type,Test Name,Date,Result,Coverage\n");
                break;
            default:
                csv.append("Category,Item,Value,Status,Details\n");
        }
        
        // Add data rows
        for (java.util.Map<String, String> record : data.getRecords()) {
            csv.append(String.join(",", record.values())).append("\n");
        }
        
        return csv.toString();
    }

    private String generateJsonReport(ReportData data) {
        // TODO: Use a proper JSON library like Jackson
        StringBuilder json = new StringBuilder();
        
        json.append("{\n");
        json.append("  \"reportType\": \"").append(data.getReportType()).append("\",\n");
        json.append("  \"serverUrl\": \"").append(data.getServerUrl()).append("\",\n");
        json.append("  \"generationTime\": \"").append(data.getGenerationTime()).append("\",\n");
        json.append("  \"recordCount\": ").append(data.getRecordCount()).append(",\n");
        
        if (data.getFromDate() != null) {
            json.append("  \"fromDate\": \"").append(data.getFromDate()).append("\",\n");
        }
        if (data.getToDate() != null) {
            json.append("  \"toDate\": \"").append(data.getToDate()).append("\",\n");
        }
        
        json.append("  \"records\": [\n");
        
        for (int i = 0; i < data.getRecords().size(); i++) {
            java.util.Map<String, String> record = data.getRecords().get(i);
            json.append("    {\n");
            
            int j = 0;
            for (var entry : record.entrySet()) {
                json.append("      \"").append(entry.getKey()).append("\": \"")
                    .append(entry.getValue()).append("\"");
                if (j < record.size() - 1) json.append(",");
                json.append("\n");
                j++;
            }
            
            json.append("    }");
            if (i < data.getRecords().size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
    }

    private String generateExcelReport(ReportData data) {
        // TODO: Implement Excel generation using Apache POI
        // For now, return CSV format as fallback
        return generateCsvReport(data);
    }

    private File saveReport(ReportResult result) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("odm_%s_report_%s%s", 
            result.getReportType().replace("-", "_"), 
            timestamp, 
            result.getFileExtension());
        
        File reportFile = new File(outputDir, fileName);
        
        // Save the report content to file
        java.nio.file.Files.write(reportFile.toPath(), result.getContent().getBytes());
        
        printVerbose("Report saved to: " + reportFile.getAbsolutePath());
        return reportFile;
    }

    private void printReportSummary(ReportResult result, File reportFile) {
        if (quiet) return;
        
        System.out.println("\n" + "=".repeat(40));
        System.out.println("REPORT GENERATION SUMMARY");
        System.out.println("=".repeat(40));
        System.out.printf("Report Type: %s\n", result.getReportType());
        System.out.printf("Format: %s\n", result.getFormat().toUpperCase());
        System.out.printf("Records: %d\n", result.getRecordCount());
        System.out.printf("File: %s\n", reportFile.getName());
        System.out.printf("Location: %s\n", reportFile.getAbsolutePath());
        System.out.printf("Size: %.2f KB\n", reportFile.length() / 1024.0);
        System.out.printf("Generated: %s\n", 
            result.getGenerationTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        System.out.println("\n✅ Report generated successfully!");
    }

    // Inner classes for data structures
    private static class ReportData {
        private String reportType;
        private String serverUrl;
        private LocalDateTime generationTime;
        private String fromDate;
        private String toDate;
        private String ruleSetFilter;
        private String environmentFilter;
        private int maxRecords;
        private java.util.List<java.util.Map<String, String>> records = new java.util.ArrayList<>();

        public void addRecord(String... values) {
            java.util.Map<String, String> record = new java.util.LinkedHashMap<>();
            String[] keys = getKeysForReportType();
            
            for (int i = 0; i < Math.min(values.length, keys.length); i++) {
                record.put(keys[i], values[i]);
            }
            
            records.add(record);
        }

        private String[] getKeysForReportType() {
            switch (reportType.toLowerCase()) {
                case "deployment-summary":
                    return new String[]{"type", "application", "date", "status", "environment"};
                case "test-summary":
                    return new String[]{"type", "testName", "date", "result", "coverage"};
                default:
                    return new String[]{"category", "item", "value", "status", "details"};
            }
        }

        // Getters and setters
        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }
        
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        
        public LocalDateTime getGenerationTime() { return generationTime; }
        public void setGenerationTime(LocalDateTime generationTime) { this.generationTime = generationTime; }
        
        public String getFromDate() { return fromDate; }
        public void setFromDate(String fromDate) { this.fromDate = fromDate; }
        
        public String getToDate() { return toDate; }
        public void setToDate(String toDate) { this.toDate = toDate; }
        
        public String getRuleSetFilter() { return ruleSetFilter; }
        public void setRuleSetFilter(String ruleSetFilter) { this.ruleSetFilter = ruleSetFilter; }
        
        public String getEnvironmentFilter() { return environmentFilter; }
        public void setEnvironmentFilter(String environmentFilter) { this.environmentFilter = environmentFilter; }
        
        public int getMaxRecords() { return maxRecords; }
        public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
        
        public java.util.List<java.util.Map<String, String>> getRecords() { return records; }
        public int getRecordCount() { return records.size(); }
    }

    private static class ReportResult {
        private String reportType;
        private String format;
        private int recordCount;
        private LocalDateTime generationTime;
        private String content;
        private String fileExtension;

        // Getters and setters
        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public int getRecordCount() { return recordCount; }
        public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
        
        public LocalDateTime getGenerationTime() { return generationTime; }
        public void setGenerationTime(LocalDateTime generationTime) { this.generationTime = generationTime; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getFileExtension() { return fileExtension; }
        public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
    }
}