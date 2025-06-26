// src/main/java/com/mceballos/odmcli/commands/CommandValidationException.java
package com.mceballos.odmcli.commands;

/**
 * Exception thrown when command validation fails.
 * Provides actionable error messages to help users fix command issues.
 */
public class CommandValidationException extends Exception {
    
    private final String suggestion;
    
    public CommandValidationException(String message) {
        super(message);
        this.suggestion = null;
    }
    
    public CommandValidationException(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
    }
    
    public CommandValidationException(String message, Throwable cause) {
        super(message, cause);
        this.suggestion = null;
    }
    
    public CommandValidationException(String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.suggestion = suggestion;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public String getActionableMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Error: ").append(getMessage());
        
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