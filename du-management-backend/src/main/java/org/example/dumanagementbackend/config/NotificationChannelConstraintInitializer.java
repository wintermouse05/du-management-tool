package org.example.dumanagementbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationChannelConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer tableExists = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = current_schema()
                      and table_name = 'notification_channels'
                    """, Integer.class);

            if (tableExists == null || tableExists == 0) {
                return;
            }

            jdbcTemplate.execute("""
                    alter table notification_channels
                    drop constraint if exists notification_channels_type_check
                    """);

            jdbcTemplate.execute("""
                    alter table notification_channels
                    add constraint notification_channels_type_check
                    check (type in ('EMAIL', 'WEBHOOK', 'CHAT'))
                    """);

            log.info("Ensured notification_channels_type_check allows EMAIL, WEBHOOK, and CHAT");
        } catch (Exception ex) {
            log.warn("Could not update notification_channels_type_check: {}", ex.getMessage());
        }
    }
}
