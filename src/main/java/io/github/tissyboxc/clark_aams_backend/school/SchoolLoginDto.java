package io.github.tissyboxc.clark_aams_backend.school;

import java.util.List;

public record SchoolLoginDto(String mode, String loginUrl, List<String> successUrlPatterns) {
}
