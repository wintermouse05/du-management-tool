package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.PointRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRuleRepository extends JpaRepository<PointRule, Long> {

    Optional<PointRule> findByActionCode(String actionCode);
}
