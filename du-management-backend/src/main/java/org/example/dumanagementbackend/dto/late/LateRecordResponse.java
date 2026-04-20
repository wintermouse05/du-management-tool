package org.example.dumanagementbackend.dto.late;

import java.time.LocalDate;

public record LateRecordResponse(
        Long id,
        Long userId,
        String fullName,
        LocalDate recordDate,
        Integer minutesLate,
        String reason
) {
}
