package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.List;

/**
 * 从浏览器扩展送来的一次导入。
 *
 * <p>{@code html} 是用户当前看到的那一页；{@code frames} 是页面里
 * 各 iframe 的内部 HTML——正方等教务系统把课表放在
 * {@code iframe#iframeautoheight} 里，外层页面一张课表都没有，
 * 只看 {@code html} 会得到空壳。
 *
 * <p>{@code frames} 可为空：用户也可能直接打开了一个不含 iframe 的课表页。
 */
public record HtmlImportCourseRequest(
        String school,
        String schoolName,
        String html,
        List<FrameHtml> frames
) {
    public HtmlImportCourseRequest(String school, String schoolName, String html) {
        this(school, schoolName, html, List.of());
    }

    public List<FrameHtml> framesOrEmpty() {
        return frames == null ? List.of() : frames;
    }

    /**
     * 一个 iframe 的内容。
     *
     * @param src  扩展取到的 src（如 content_xs.aspx），便于排查
     * @param id   iframe 的 id
     * @param html 该 iframe 内部文档的 outerHTML
     */
    public record FrameHtml(String src, String id, String html) {
    }
}
