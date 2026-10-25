package io.github.tissyboxc.clark_aams_backend.importers.schools;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.school.SchoolEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SchoolAdapterRegistry {
    private final Map<String, SchoolAdapter> adaptersById;
    private final Map<String, SchoolAdapter> adaptersByAlias;

    public SchoolAdapterRegistry(List<SchoolAdapter> adapters) {
        Map<String, SchoolAdapter> byId = new LinkedHashMap<>();
        Map<String, SchoolAdapter> byAlias = new LinkedHashMap<>();

        for (SchoolAdapter adapter : adapters) {
            String schoolId = normalize(adapter.school().id());
            if (schoolId.isBlank()) {
                throw new IllegalStateException("School adapter id must not be blank");
            }
            register(byId, schoolId, adapter);
            for (String alias : aliasesOf(adapter.school())) {
                register(byAlias, alias, adapter);
            }
        }

        this.adaptersById = Map.copyOf(byId);
        this.adaptersByAlias = Map.copyOf(byAlias);
    }

    public SchoolAdapter getBySchoolId(String schoolId) {
        SchoolAdapter adapter = adaptersById.get(normalize(schoolId));
        if (adapter == null) {
            throw new BusinessException(ErrorCode.IMPORTER_NOT_FOUND);
        }
        return adapter;
    }

    public Optional<SchoolAdapter> find(String school) {
        String normalized = normalize(school);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        SchoolAdapter adapter = adaptersById.get(normalized);
        if (adapter != null) {
            return Optional.of(adapter);
        }
        return Optional.ofNullable(adaptersByAlias.get(normalized));
    }

    public List<SchoolEntity> listSchools() {
        return adaptersById.values().stream()
                .map(SchoolAdapter::school)
                .toList();
    }

    private List<String> aliasesOf(SchoolEntity school) {
        List<String> aliases = new ArrayList<>();
        aliases.add(normalize(school.id()));
        aliases.add(normalize(school.name()));
        aliases.add(normalize(school.shortName()));
        return aliases;
    }

    private void register(Map<String, SchoolAdapter> target, String key, SchoolAdapter adapter) {
        if (key.isBlank()) {
            return;
        }
        SchoolAdapter existing = target.putIfAbsent(key, adapter);
        if (existing != null && existing != adapter) {
            throw new IllegalStateException("Duplicate school adapter alias: " + key);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
