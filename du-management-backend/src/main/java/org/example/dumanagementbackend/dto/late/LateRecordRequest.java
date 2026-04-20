package org.example.dumanagementbackend.dto.late;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LateRecordRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        @NotNull(message = "recordDate is required")
        LocalDate recordDate,

        @NotNull(message = "minutesLate is required")
        @Positive(message = "minutesLate must be greater than 0")
        Integer minutesLate,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
