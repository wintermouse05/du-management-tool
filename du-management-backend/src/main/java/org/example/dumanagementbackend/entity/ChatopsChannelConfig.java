package org.example.dumanagementbackend.entity;

import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chatops_channel_configs")
@Getter
@Setter
@NoArgsConstructor
public class ChatopsChannelConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 40)
    private ChatopsChannelPurpose purpose;

    @Column(name = "base_url", nullable = false, length = 300)
    private String baseUrl;

    @Column(name = "channel_url", nullable = false, length = 500)
    private String channelUrl;

    @Column(name = "channel_id", nullable = false, length = 120)
    private String channelId;

    @Column(name = "encrypted_token", nullable = false, length = 4000)
    private String encryptedToken;
}
