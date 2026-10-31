package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 手机端走的是「后端拿 cookie 自己去抓页面」这条通道（{@code JitClient}），
 * 和网页扩展「直接传 HTML」是两条完全不同的路。
 *
 * <p>之前网页端修好了，手机端仍然报解析不到课表，因为 {@code JitClient}
 * 判页面时只认 {@code table#Table6}，遇到格子格式（{@code table#Table1.schedule}）
 * 每一页都判为不符，最后抛 LOGIN_INVALID。
 *
 * <p>这里钉住判定逻辑：真实课表页必须被认出来，外壳页必须不被误认。
 */
class JitClientPageDetectionTests {

    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/jit-grid", name), StandardCharsets.UTF_8);
    }

    @Test
    void gridSchedulePageIsRecognized() throws Exception {
        String html = read("schedule-body.html");
        Assertions.assertTrue(
                JitGridTableParser.looksLikeGridSchedule(html),
                "格子格式课表页必须被识别出来，否则手机端会一路判为未登录"
        );
    }

    @Test
    void outerFramePageIsNotMistakenForSchedule() throws Exception {
        String html = read("frame-outer.html");
        Assertions.assertFalse(
                JitGridTableParser.looksLikeGridSchedule(html),
                "外层框架页没有课表，不该被判成课表页"
        );
    }

    @Test
    void outerFramePageExposesEmbeddedFrameSrc() throws Exception {
        String html = read("frame-outer.html");
        // 课表在 iframe 里，必须能从外壳页找到它的地址，否则拿不到内容
        Assertions.assertTrue(html.contains("iframeautoheight"), "外壳页应当含 iframeautoheight");
        Assertions.assertTrue(html.contains("content_xs.aspx"), "iframe 的 src 指向课表内容页");
    }
}
