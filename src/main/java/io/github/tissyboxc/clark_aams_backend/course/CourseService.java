package io.github.tissyboxc.clark_aams_backend.course;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表服务（P3-1）：把导入结果落库，并提供增删改查。
 *
 * <p>周次的唯一真相源是 {@code weekRanges + weekRule}。
 * 对外返回时**同时**给出展开后的 {@code weeks}，
 * 这样 App 与 Web 都能直接用，不必各自实现一遍展开逻辑。
 */
@Service
public class CourseService {

    private static final TypeReference<List<int[]>> RANGE_LIST = new TypeReference<>() {
    };

    private final CourseRepository repository;
    private final ObjectMapper objectMapper;

    public CourseService(CourseRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 某用户的全部课程。 */
    public List<CourseDto> list(long ownerId) {
        List<CourseDto> result = new ArrayList<>();
        for (CourseRepository.CourseRecord record : repository.findByOwner(ownerId)) {
            result.add(toDto(record));
        }
        return result;
    }

    /**
     * 用导入结果覆盖某用户的课表。
     *
     * <p>导入是整体动作，采用「先删后插」而不是逐条合并：
     * 用户在教务系统退选一门课后，逐条合并会把旧记录留下来。
     */
    public List<CourseDto> replaceFromImport(long ownerId, List<CourseDto> courses) {
        List<CourseRepository.CourseRecord> records = new ArrayList<>();
        int index = 0;
        for (CourseDto course : courses) {
            records.add(toRecord(course, index++));
        }
        List<CourseDto> saved = new ArrayList<>();
        for (CourseRepository.CourseRecord record : repository.replaceAll(ownerId, records)) {
            saved.add(toDto(record));
        }
        return saved;
    }

    /** 新增一门课。 */
    public CourseDto create(long ownerId, CourseDto course) {
        validate(course);
        CourseRepository.CourseRecord saved = repository.insert(ownerId, toRecord(course, 0));
        return toDto(saved);
    }

    /** 更新一门课；不存在时抛 404。 */
    public CourseDto update(long ownerId, long courseId, CourseDto course) {
        validate(course);
        if (!repository.update(ownerId, courseId, toRecord(course, 0))) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "课程不存在");
        }
        return toDto(toRecord(course, 0).withId(courseId));
    }

    /** 删除一门课；不存在时抛 404。 */
    public void delete(long ownerId, long courseId) {
        if (!repository.delete(ownerId, courseId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "课程不存在");
        }
    }

    /** 清空课表，返回删除条数。 */
    public int clear(long ownerId) {
        return repository.deleteAll(ownerId);
    }

    private void validate(CourseDto course) {
        if (course == null || course.name() == null || course.name().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课程名称不能为空");
        }
        if (course.dayOfWeek() < 1 || course.dayOfWeek() > 7) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "星期必须在 1-7 之间");
        }
        if (course.startLesson() < 1 || course.endLesson() < course.startLesson()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "节次范围不合法");
        }
    }

    private CourseRepository.CourseRecord toRecord(CourseDto course, int sortOrder) {
        WeekPattern pattern = course.weekPattern();
        String rangesJson;
        try {
            rangesJson = objectMapper.writeValueAsString(pattern.ranges());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "周次序列化失败", exception);
        }
        List<Integer> expanded = pattern.expand();
        int startWeek = expanded.isEmpty() ? 1 : expanded.get(0);
        int endWeek = expanded.isEmpty() ? 1 : expanded.get(expanded.size() - 1);
        return new CourseRepository.CourseRecord(
                0L,
                course.name() == null ? "" : course.name().trim(),
                course.dayOfWeek(),
                course.startLesson(),
                course.endLesson(),
                course.location() == null ? "" : course.location(),
                course.teacher() == null ? "" : course.teacher(),
                course.rawTime() == null ? "" : course.rawTime(),
                rangesJson,
                pattern.rule().name(),
                startWeek,
                endWeek,
                null
        );
    }

    private CourseDto toDto(CourseRepository.CourseRecord record) {
        List<int[]> ranges = new ArrayList<>();
        try {
            List<int[]> parsed = objectMapper.readValue(record.weekRangesJson(), RANGE_LIST);
            if (parsed != null) {
                ranges.addAll(parsed);
            }
        } catch (Exception exception) {
            // 存储损坏时退化为「单周」，而不是让整个课表读不出来
            ranges.add(new int[]{record.startWeek(), record.endWeek()});
        }
        WeekPattern pattern = new WeekPattern(ranges, record.rule());
        List<Integer> weeks = pattern.expand();
        return new CourseDto(
                record.name(),
                record.startWeek(),
                record.endWeek(),
                record.dayOfWeek(),
                record.startLesson(),
                record.endLesson(),
                record.location(),
                record.teacher(),
                weeks,
                record.rawTime(),
                ranges,
                record.rule()
        );
    }
}
