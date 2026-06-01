package org.example.dumanagementbackend.config;

import java.util.Arrays;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduleConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer tableExists = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = current_schema()
                      and table_name = 'notification_schedules'
                    """, Integer.class);

            if (tableExists == null || tableExists == 0) {
                return;
            }

            String allowedTypes = Arrays.stream(NotificationScheduleType.values())
                    .map(type -> "'" + type.name() + "'")
                    .collect(Collectors.joining(", "));

            jdbcTemplate.execute("""
                    alter table notification_schedules
                    drop constraint if exists notification_schedules_type_check
                    """);

            jdbcTemplate.execute("""
                    alter table notification_schedules
                    add constraint notification_schedules_type_check
                    check (type in (%s))
                    """.formatted(allowedTypes));

            log.info("Ensured notification_schedules_type_check allows: {}", allowedTypes);
        } catch (Exception ex) {
            log.warn("Could not update notification_schedules_type_check: {}", ex.getMessage());
        }
    }
}
