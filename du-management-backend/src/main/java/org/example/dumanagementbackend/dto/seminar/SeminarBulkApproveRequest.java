package org.example.dumanagementbackend.dto.seminar;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SeminarBulkApproveRequest(
        @NotEmpty(message = "seminarIds is required")
        List<@NotNull(message = "seminarId is required") @Positive(message = "seminarId must be greater than 0") Long> seminarIds
) {
}
