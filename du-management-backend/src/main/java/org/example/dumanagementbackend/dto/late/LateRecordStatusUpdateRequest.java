package org.example.dumanagementbackend.dto.late;

import jakarta.validation.constraints.NotNull;
import org.example.dumanagementbackend.entity.enums.LateRecordStatus;

public record LateRecordStatusUpdateRequest(
        @NotNull(message = "status is required")
        LateRecordStatus status
) {
}
