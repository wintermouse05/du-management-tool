package org.example.dumanagementbackend.dto.chatops;

import java.time.LocalDateTime;
import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;

public record ChatopsChannelConfigResponse(
        Long id,
        ChatopsChannelPurpose purpose,
        String channelUrl,
        String channelId,
        boolean tokenConfigured,
        String tokenMasked,
        LocalDateTime updatedAt
) {
}
