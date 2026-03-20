package com.mobigen.aiop.nttpoc.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
public class MybatisConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionFactory sqlSessionFactoryNttpocDb1(@Qualifier("dataSourceNttpocDb1") DataSource dataSourceNttpocDb1) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSourceNttpocDb1);
        sessionFactory.setConfigLocation(
            new PathMatchingResourcePatternResolver().getResource("classpath:mapper/MybatisConfig.xml")
        );
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mapper/mybatis/mysql/**/*.xml")
        );
        return sessionFactory.getObject();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionTemplate sqlSessionTemplateNttpocDb1(@Qualifier("sqlSessionFactoryNttpocDb1") SqlSessionFactory sqlSessionFactoryNttpocDb1) {
        return new SqlSessionTemplate(sqlSessionFactoryNttpocDb1);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactoryNttpocDb2(@Qualifier("dataSourceNttpocDb2") DataSource dataSourceNttpocDb2) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSourceNttpocDb2);
        sessionFactory.setConfigLocation(
            new PathMatchingResourcePatternResolver().getResource("classpath:mapper/MybatisConfig.xml")
        );
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mapper/mybatis/mysql/**/*.xml")
        );
        return sessionFactory.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplateNttpocDb2(@Qualifier("sqlSessionFactoryNttpocDb2") SqlSessionFactory sqlSessionFactoryNttpocDb2) {
        return new SqlSessionTemplate(sqlSessionFactoryNttpocDb2);
    }
}
