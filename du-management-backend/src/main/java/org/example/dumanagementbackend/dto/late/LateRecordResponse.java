package org.example.dumanagementbackend.dto.late;

import java.time.LocalDate;
import org.example.dumanagementbackend.entity.enums.LateRecordStatus;

public record LateRecordResponse(
        Long id,
        Long userId,
        String fullName,
        LocalDate recordDate,
        Integer minutesLate,
        String reason,
        LateRecordStatus status,
        Integer fineAmount,
        boolean payable
) {
}
