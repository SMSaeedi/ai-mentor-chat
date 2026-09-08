package com.ai.mentor.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                create table if not exists vendors (
                    id varchar(100) primary key,
                    name varchar(200) not null,
                    description varchar(1000) not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists response_cache (
                    cache_key varchar(1000) primary key,
                    response clob not null,
                    updated_at timestamp not null
                )
                """);
        jdbcTemplate.update("""
                merge into vendors (id, name, description) key (id) values
                    ('focus-coach', 'Focus Coach', 'Productivity coaching for planning, focus, and accountability.'),
                    ('calm-space', 'Calm Space', 'Guided breathing and wellbeing exercises for stressful moments.'),
                    ('career-lab', 'Career Lab', 'Structured support for learning plans, CVs, and career transitions.')
                """);
    }
}
