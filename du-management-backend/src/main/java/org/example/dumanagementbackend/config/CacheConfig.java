package org.example.dumanagementbackend.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache("gamificationLeaderboard", Duration.ofSeconds(30), 200),
                buildCache("pointRuleByActionCode", Duration.ofMinutes(30), 200),
                buildCache("notificationTemplateByCode", Duration.ofMinutes(10), 200),
                buildCache("notificationEnabledChannels", Duration.ofMinutes(2), 50),
                buildCache("orderSessionSummary", Duration.ofSeconds(30), 500),
                buildCache("surveyProgress", Duration.ofSeconds(30), 500),
                buildCache("lateMonthlySummary", Duration.ofMinutes(5), 200),
                buildCache("rolesPage", Duration.ofMinutes(30), 50),
                buildCache("roleById", Duration.ofMinutes(30), 200)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, Duration ttl, long maximumSize) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maximumSize)
                        .recordStats()
                        .build()
        );
    }
}
