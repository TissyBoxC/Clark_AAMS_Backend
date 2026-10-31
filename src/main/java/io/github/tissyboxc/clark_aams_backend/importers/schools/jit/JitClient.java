package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.LoginSessionDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JitClient {
    private static final String BASE_URL = "https://jwxt.jit.edu.cn";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient httpClient;

    public JitClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String fetchCoursePage(LoginSessionDto loginSession) {
        String extraHtml = readExtraHtml(loginSession);
        if (!extraHtml.isBlank()) {
            return extraHtml;
        }

        String cookie = loginSession == null ? "" : safeTrim(loginSession.cookie());
        if (cookie.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "缺少 cookie");
        }

        List<URI> candidates = buildCandidateUris(loginSession);
        BusinessException lastBusinessException = null;
        IOException lastIOException = null;
        InterruptedException lastInterruptedException = null;
        // 已经拿到、但里面只是外壳（课表在 iframe 里）的页面，最后兜底用
        String outerShell = null;

        for (URI uri : candidates) {
            try {
                String html = fetch(uri, cookie);
                if (looksLikeSchedulePage(html)) {
                    return html;
                }

                /*
                 * 这一页本身没有课表，也许课表在它的 iframe 里。
                 * 实测该校 content_xs.aspx 就只是个框架，表格在内嵌页。
                 */
                String frameSrc = findScheduleFrameSrc(html);
                if (frameSrc != null) {
                    try {
                        URI frameUri = uri.resolve(frameSrc);
                        String frameHtml = fetch(frameUri, cookie);
                        if (looksLikeSchedulePage(frameHtml)) {
                            return frameHtml;
                        }
                    } catch (BusinessException | IOException ignored) {
                        // 内嵌页取不到不算失败，继续试下一个候选地址
                    }
                }

                if (outerShell == null) {
                    outerShell = html;
                }
            } catch (BusinessException exception) {
                lastBusinessException = exception;
            } catch (IOException exception) {
                lastIOException = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                lastInterruptedException = exception;
            }
        }

        if (lastBusinessException != null) {
            throw lastBusinessException;
        }
        if (lastIOException != null || lastInterruptedException != null) {
            throw new BusinessException(ErrorCode.ACADEMIC_REQUEST_FAILED, ErrorCode.ACADEMIC_REQUEST_FAILED.defaultMessage(),
                    lastIOException != null ? lastIOException : lastInterruptedException);
        }

        /*
         * 一页都没判成课表，但确实抓到了内容。交给上层去解析：
         * 解析器解不出会报「课表解析失败」，比报「登录失效」准确得多——
         * cookie 是好的，问题在页面形态对不上。
         */
        if (outerShell != null) {
            return outerShell;
        }
        throw new BusinessException(ErrorCode.LOGIN_INVALID);
    }

    /**
     * 这一页是不是课表页。
     *
     * <p>两种形态都认：
     * <ul>
     *   <li>块格式 {@code table#Table6} + {@code p.kblsbk}——早期对接的模板</li>
     *   <li>格子格式 {@code table#Table1.schedule}——用 {@code <br>} 分行的纯文本格子</li>
     * </ul>
     *
     * <p>之前只认 Table6，遇到格子格式的学校每一页都判为不符，
     * 最后抛 LOGIN_INVALID，App 端就显示解析不到课表。
     */
    private boolean looksLikeSchedulePage(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        if (JitJwxtParser.pageHasTable6(html)) {
            return true;
        }
        return JitGridTableParser.looksLikeGridSchedule(html);
    }

    /**
     * 课表常常不在 {@code content_xs.aspx} 本身，而在它内嵌的 iframe 里。
     *
     * <p>实测这个学校的结构是
     * {@code mainbody > bodydiv > rightdiv > iframe#iframeautoheight}，
     * iframe 的 src 指向 {@code content_xs.aspx}，真正的表格在那一层。
     * 只取外层就等于拿到一个空壳。
     *
     * <p>返回内嵌页的相对地址；没有 iframe 或读不出来时返回 null。
     */
    private String findScheduleFrameSrc(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(html);
        for (org.jsoup.nodes.Element frame : document.select("iframe")) {
            String src = frame.attr("src");
            if (src == null || src.isBlank()) {
                continue;
            }
            // 只跟同站相对路径，避免被页面内容带去别处
            if (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("//")) {
                continue;
            }
            return src;
        }
        return null;
    }

    private String fetch(URI uri, String cookie) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(READ_TIMEOUT)
                .header("Cookie", cookie)
                .header("User-Agent", "ClarkAamsBackend/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", BASE_URL + "/")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException(ErrorCode.ACADEMIC_REQUEST_FAILED, "学校教务系统返回异常状态码：" + statusCode);
        }

        return response.body();
    }

    private List<URI> buildCandidateUris(LoginSessionDto loginSession) {
        List<URI> uris = new ArrayList<>();
        String studentId = loginSession == null ? "" : safeTrim(loginSession.studentId());
        String successUrl = loginSession == null ? "" : safeTrim(loginSession.successUrl());

        if (!successUrl.isBlank()) {
            URI successUri = safeJitUri(successUrl);
            if (successUri != null) {
                uris.add(successUri);
            }
        }

        if (!studentId.isBlank()) {
            String encodedStudentId = URLEncoder.encode(studentId, StandardCharsets.UTF_8);
            uris.add(URI.create(BASE_URL + "/xs_main.aspx?xh=" + encodedStudentId));
            uris.add(URI.create(BASE_URL + "/content_xs.aspx?xh=" + encodedStudentId));
            uris.add(URI.create(BASE_URL + "/xskbcx.aspx?xh=" + encodedStudentId + "&xm=&gnmkdm=N121603"));
            uris.add(URI.create(BASE_URL + "/xskbcx.aspx?xh=" + encodedStudentId));
        } else {
            uris.add(URI.create(BASE_URL + "/xs_main.aspx"));
            uris.add(URI.create(BASE_URL + "/content_xs.aspx"));
            uris.add(URI.create(BASE_URL + "/xskbcx.aspx"));
        }

        return uris;
    }

    private URI safeJitUri(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase("jwxt.jit.edu.cn")) {
                return null;
            }
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String readExtraHtml(LoginSessionDto loginSession) {
        if (loginSession == null || loginSession.extra() == null) {
            return "";
        }
        for (String key : List.of("html", "pageHtml", "tableHtml")) {
            Object value = loginSession.extra().get(key);
            if (value instanceof String html && !html.isBlank()) {
                return html;
            }
        }
        return "";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
