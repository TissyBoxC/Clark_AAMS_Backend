package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.LoginSessionDto;

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

        for (URI uri : candidates) {
            try {
                String html = fetch(uri, cookie);
                if (JitJwxtParser.pageHasTable6(html)) {
                    return html;
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
        throw new BusinessException(ErrorCode.LOGIN_INVALID);
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
