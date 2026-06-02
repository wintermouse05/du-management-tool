package org.example.dumanagementbackend.entity.enums;

public enum TaskStatus {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    BLOCKED("Blocked"),
    DONE("Done"),
    CANCELLED("Cancelled");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
