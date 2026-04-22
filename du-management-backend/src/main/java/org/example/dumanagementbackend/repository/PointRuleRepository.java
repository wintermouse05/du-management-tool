package org.example.dumanagementbackend.repository;

import java.util.Optional;

import org.example.dumanagementbackend.entity.PointRule;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRuleRepository extends JpaRepository<PointRule, Long> {

    @Cacheable(cacheNames = "pointRuleByActionCode", key = "#actionCode")
    Optional<PointRule> findByActionCode(String actionCode);
}
