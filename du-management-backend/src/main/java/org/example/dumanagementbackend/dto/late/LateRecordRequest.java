package org.example.dumanagementbackend.dto.late;

import java.time.LocalDate;

public record LateRecordRequest(
        Long userId,
        LocalDate recordDate,
        Integer minutesLate,
        String reason
) {
}
