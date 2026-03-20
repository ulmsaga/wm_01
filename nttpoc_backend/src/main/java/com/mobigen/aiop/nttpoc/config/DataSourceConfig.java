package com.mobigen.aiop.nttpoc.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {
    @Bean(name = "dataSourceNttpocDb1")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.nttpocdb1")
    public DataSource dataSourceNttpocDb1() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "dataSourceNttpocDb2")
    @ConfigurationProperties(prefix = "spring.datasource.nttpocdb2")
    public DataSource dataSourceNttpocDb2() {
        return DataSourceBuilder.create().build();
    }
}
