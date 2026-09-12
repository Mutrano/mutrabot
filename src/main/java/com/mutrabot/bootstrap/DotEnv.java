package com.mutrabot.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class DotEnv {

    private static final Path DEFAULT_FILE = Path.of(".env");

    private final Map<String, String> values;

    private DotEnv(Map<String, String> values) {
        this.values = values;
    }

    static DotEnv load() {
        return load(DEFAULT_FILE);
    }

    static DotEnv load(Path file) {
        Map<String, String> parsed = new HashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    parseLine(line).ifPresent(entry -> parsed.put(entry[0], entry[1]));
                }
            } catch (IOException e) {
                System.err.println("Não consegui ler " + file + ": " + e.getMessage());
            }
        }
        return new DotEnv(parsed);
    }

    private static Optional<String[]> parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        int separator = trimmed.indexOf('=');
        if (separator <= 0) {
            return Optional.empty();
        }
        String key = trimmed.substring(0, separator).trim();
        String value = trimmed.substring(separator + 1).trim();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return Optional.of(new String[] {key, value});
    }

    String get(String key) {
        String fromEnvironment = System.getenv(key);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return values.get(key);
    }
}
