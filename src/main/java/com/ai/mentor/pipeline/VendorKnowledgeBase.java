package com.ai.mentor.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class VendorKnowledgeBase {
    private static final List<VendorRecord> FALLBACK_VENDORS = List.of(
            new VendorRecord("focus-coach", "Focus Coach", "Productivity coaching for planning, focus, and accountability."),
            new VendorRecord("calm-space", "Calm Space", "Guided breathing and wellbeing exercises for stressful moments."),
            new VendorRecord("career-lab", "Career Lab", "Structured support for learning plans, CVs, and career transitions.")
    );
    private final JdbcTemplate jdbcTemplate;

    public VendorKnowledgeBase() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public VendorKnowledgeBase(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VendorRecord> findRelevant(String question) {
        List<VendorRecord> vendors = jdbcTemplate == null
                ? FALLBACK_VENDORS
                : jdbcTemplate.query("select id, name, description from vendors",
                (resultSet, rowNum) -> new VendorRecord(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description")));
        String normalized = question.toLowerCase(Locale.ROOT);
        return vendors.stream()
                .filter(vendor -> containsToken(normalized, vendor.description()))
                .limit(3)
                .toList();
    }

    private boolean containsToken(String question, String description) {
        for (String token : description.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (token.length() > 4 && question.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public record VendorRecord(String id, String name, String description) {}
}
