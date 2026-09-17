package com.sentinelpay.backend.imports;

import com.zaxxer.hikari.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@Profile("uploads")
public class UploadConfiguration {
    @Bean(destroyMethod = "close") HikariDataSource uploadDataSource(
            @Value("${uploads.jdbc-url}") String url, @Value("${uploads.username}") String username,
            @Value("${uploads.password}") String password, Environment environment) {
        if (environment.matchesProfiles("pipeline")) throw new IllegalStateException("Run uploads separately from the local pipeline profile");
        if (!url.contains("sslmode=verify-full")) throw new IllegalArgumentException("Database certificate verification is required");
        var config = new HikariConfig(); config.setJdbcUrl(url); config.setUsername(username); config.setPassword(password);
        config.setMaximumPoolSize(3); config.setConnectionTimeout(10000);
        config.addDataSourceProperty("connectTimeout", "10"); config.addDataSourceProperty("socketTimeout", "30");
        return new HikariDataSource(config);
    }
    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(name="uploads.migrations-enabled", havingValue="true", matchIfMissing=true)
    Flyway uploadMigrations(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/uploads")
                .defaultSchema("sentinelpay_private").schemas("sentinelpay_private").load();
    }
    @Bean PrivateUploadStore privateUploadStore(DataSource source, ObjectProvider<Flyway> migrations) {
        migrations.getIfAvailable(); // Complete enabled migrations before accepting requests.
        return new PrivateUploadStore(source);
    }
}
