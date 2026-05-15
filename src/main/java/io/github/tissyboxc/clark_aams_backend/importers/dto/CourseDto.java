package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.List;

public record CourseDto(
        String name,
        Integer startWeek,
        Integer endWeek,
        Integer dayOfWeek,
        Integer startLesson,
        Integer endLesson,
        String location,
        String teacher,
        List<Integer> weeks,
        String rawTime
) {
}
