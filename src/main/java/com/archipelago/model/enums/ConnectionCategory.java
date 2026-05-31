package com.archipelago.model.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ConnectionCategory {
    DIRECTOR("director", "Shared director"),
    CAST("cast", "Shared cast or crew"),
    GENRE("genre", "Genre or subgenre"),
    THEME("theme", "Theme or idea"),
    TONE("tone", "Tone or mood"),
    STRUCTURE("structure", "Structure or form"),
    VISUAL_STYLE("visual-style", "Visual style"),
    FRANCHISE("franchise", "Series or franchise"),
    ERA("era", "Era or movement"),
    INFLUENCE("influence", "Direct influence");

    private final String value;
    private final String label;

    ConnectionCategory(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static Optional<ConnectionCategory> fromValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }

        String normalized = rawValue.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(category -> category.value.equals(normalized))
                .findFirst();
    }
}
