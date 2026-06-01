package org.example.dumanagementbackend.dto.chatops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatopsChannelConfigUpsertRequest(
        @Size(max = 2000, message = "token must be at most 2000 characters")
        String token,

        @NotBlank(message = "channelUrl is required")
        @Size(max = 500, message = "channelUrl must be at most 500 characters")
        String channelUrl
) {
}
