package com.saga.wm.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {
    @Bean(name = "dataSourceWmDb1")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.wmdb1")
    public DataSource dataSourceWmDb1() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "dataSourceWmDb2")
    @ConfigurationProperties(prefix = "spring.datasource.wmdb2")
    public DataSource dataSourceWmDb2() {
        return DataSourceBuilder.create().build();
    }
}
