package org.example.dumanagementbackend.entity.enums;

public enum ProjectRole {
    BACKEND_DEVELOPER("Backend Developer"),
    BUSINESS_ANALYST("Business Analyst"),
    DEVOPS_ENGINEER("DevOps Engineer"),
    FLUTTER_DEVELOPER("Flutter Developer"),
    FRONTEND_DEVELOPER("Frontend Developer"),
    PROJECT_MANAGER("Project Manager"),
    QA_ENGINEER("QA Engineer"),
    QUALITY_CONTROL("Quality Control"),
    TEAM_LEAD("Team Lead"),
    TECH_LEAD("Tech Lead"),
    UI_UX_DESIGNER("UI/UX Designer"),
    XAMARIN_DEVELOPER("Xamarin Developer");

    private final String label;

    ProjectRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
