package org.example.dumanagementbackend.config;

import org.example.dumanagementbackend.logging.RawSqlStatementInspector;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemLogHibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer rawSqlStatementInspectorCustomizer(
            RawSqlStatementInspector rawSqlStatementInspector
    ) {
        return properties -> properties.put(
                "hibernate.session_factory.statement_inspector",
                rawSqlStatementInspector
        );
    }
}
