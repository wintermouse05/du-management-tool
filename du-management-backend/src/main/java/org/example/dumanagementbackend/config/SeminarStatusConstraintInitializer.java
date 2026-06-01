package org.example.dumanagementbackend.config;

import java.util.Arrays;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeminarStatusConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer tableExists = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = current_schema()
                      and table_name = 'seminars'
                    """, Integer.class);

            if (tableExists == null || tableExists == 0) {
                return;
            }

            String allowedStatuses = Arrays.stream(SeminarStatus.values())
                    .map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));

            jdbcTemplate.execute("""
                    alter table seminars
                    drop constraint if exists seminars_status_check
                    """);

            jdbcTemplate.execute("""
                    alter table seminars
                    add constraint seminars_status_check
                    check (status in (%s))
                    """.formatted(allowedStatuses));

            log.info("Ensured seminars_status_check allows: {}", allowedStatuses);
        } catch (Exception ex) {
            log.warn("Could not update seminars_status_check: {}", ex.getMessage());
        }
    }
}
