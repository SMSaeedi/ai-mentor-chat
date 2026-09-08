package com.ai.mentor.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResponseCache {
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;

    public ResponseCache() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public ResponseCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> get(String key) {
        if (jdbcTemplate != null) {
            return jdbcTemplate.query(
                    "select response from response_cache where cache_key = ?",
                    (resultSet, rowNum) -> resultSet.getString("response"),
                    key
            ).stream().findFirst();
        }
        return Optional.ofNullable(responses.get(key));
    }

    public void put(String key, String response) {
        if (jdbcTemplate != null) {
            jdbcTemplate.update("""
                    merge into response_cache (cache_key, response, updated_at)
                    key (cache_key) values (?, ?, current_timestamp)
                    """, key, response);
            return;
        }
        responses.put(key, response);
    }
}
