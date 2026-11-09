package com.kunirank.api.deployment;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("railway-unified")
class UnifiedDataSourceConfiguration {
    @Bean
    HikariDataSource queryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${PGUSER}") String username,
            @Value("${PGPASSWORD}") String password) {
        HikariDataSource pool = pool("query", url, username, password, 2);
        pool.setReadOnly(true);
        pool.setConnectionInitSql("SET default_transaction_read_only = on");
        pool.addDataSourceProperty("options", "-c default_transaction_read_only=on -c statement_timeout=15000");
        return pool;
    }

    @Bean
    HikariDataSource memberDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${MEMBER_DB_USER}") String username,
            @Value("${MEMBER_DB_PASSWORD}") String password) {
        return pool("member", url, username, password, 2);
    }

    @Bean
    HikariDataSource votingDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${VS_DB_USER}") String username,
            @Value("${VS_DB_PASSWORD}") String password) {
        return pool("voting", url, username, password, 3);
    }

    @Bean
    @Primary
    DataSource dataSource(
            @Qualifier("queryDataSource") DataSource query,
            @Qualifier("memberDataSource") DataSource member,
            @Qualifier("votingDataSource") DataSource voting) {
        var routing = new DeploymentDataSource();
        routing.setTargetDataSources(Map.of(
                DeploymentDataSource.Role.QUERY, query,
                DeploymentDataSource.Role.MEMBER, member,
                DeploymentDataSource.Role.VOTING, voting));
        routing.setLenientFallback(false);
        return routing;
    }

    private HikariDataSource pool(String name, String url, String username, String password, int size) {
        var pool = new HikariDataSource();
        pool.setPoolName(name);
        pool.setJdbcUrl(url);
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setMaximumPoolSize(size);
        pool.setMinimumIdle(1);
        pool.setConnectionInitSql("SET statement_timeout = 15000");
        return pool;
    }
}
