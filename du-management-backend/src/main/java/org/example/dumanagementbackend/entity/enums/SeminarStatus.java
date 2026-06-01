package org.example.dumanagementbackend.entity.enums;

public enum SeminarStatus {
    PENDING,
    APPROVED,
    DONE,
    // Legacy values kept for backward-compatibility with existing DB rows.
    PROPOSED,
    SCHEDULED
}
