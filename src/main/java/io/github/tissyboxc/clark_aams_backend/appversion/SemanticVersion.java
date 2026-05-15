package io.github.tissyboxc.clark_aams_backend.appversion;

import java.util.ArrayList;
import java.util.List;

final class SemanticVersion implements Comparable<SemanticVersion> {
    private final List<Integer> parts;

    private SemanticVersion(List<Integer> parts) {
        this.parts = parts;
    }

    static SemanticVersion parse(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        String numericPart = normalized.split("-", 2)[0].split("\\+", 2)[0];
        String[] tokens = numericPart.split("\\.");
        List<Integer> parts = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                parts.add(0);
                continue;
            }
            String digits = token.replaceAll("[^0-9].*$", "");
            parts.add(digits.isBlank() ? 0 : Integer.parseInt(digits));
        }
        while (parts.size() < 3) {
            parts.add(0);
        }
        return new SemanticVersion(parts);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int max = Math.max(parts.size(), other.parts.size());
        for (int index = 0; index < max; index++) {
            int left = index < parts.size() ? parts.get(index) : 0;
            int right = index < other.parts.size() ? other.parts.get(index) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
