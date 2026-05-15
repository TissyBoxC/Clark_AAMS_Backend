package io.github.tissyboxc.clark_aams_backend.appversion;

public enum UpdateType {
    NONE("none"),
    OPTIONAL("optional"),
    REQUIRED("required");

    private final String value;

    UpdateType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
