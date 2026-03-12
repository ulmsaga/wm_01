package com.saga.wm.config;

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
    public SqlSessionFactory sqlSessionFactoryWmDb1(@Qualifier("dataSourceWmDb1") DataSource dataSourceWmDb1) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSourceWmDb1);
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
    public SqlSessionTemplate sqlSessionTemplateWmDb1(@Qualifier("sqlSessionFactoryWmDb1") SqlSessionFactory sqlSessionFactoryWmDb1) {
        return new SqlSessionTemplate(sqlSessionFactoryWmDb1);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactoryWmDb2(@Qualifier("dataSourceWmDb2") DataSource dataSourceWmDb2) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSourceWmDb2);
        sessionFactory.setConfigLocation(
            new PathMatchingResourcePatternResolver().getResource("classpath:mapper/MybatisConfig.xml")
        );
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mapper/mybatis/mysql/**/*.xml")
        );
        return sessionFactory.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplateWmDb2(@Qualifier("sqlSessionFactoryWmDb2") SqlSessionFactory sqlSessionFactoryWmDb2) {
        return new SqlSessionTemplate(sqlSessionFactoryWmDb2);
    }
}
