package com.archipelago.model.enums;

import com.archipelago.exception.IllegalStateException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public enum MovieGraphStatus {
    ALL,
    IN_GRAPH,
    NOT_IN_GRAPH;

    public static MovieGraphStatus fromQuery(String value) {
        if (!StringUtils.hasText(value)) {
            return ALL;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Graph status must be all, in_graph, or not_in_graph"));
    }
}
