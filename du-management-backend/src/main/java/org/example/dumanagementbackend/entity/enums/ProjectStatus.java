package org.example.dumanagementbackend.entity.enums;

public enum ProjectStatus {
    PLANNED("Planned"),
    ACTIVE("Active"),
    ON_HOLD("On Hold"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
