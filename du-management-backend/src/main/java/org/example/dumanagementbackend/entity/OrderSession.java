package org.example.dumanagementbackend.entity;

import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "order_sessions",
        indexes = {
                @Index(name = "idx_order_sessions_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OrderSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderSessionStatus status = OrderSessionStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime deadline;
}
