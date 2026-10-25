package io.github.tissyboxc.clark_aams_backend.importers.schools.jlu;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.LoginSessionDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JluClient {
    public String fetchCoursePage(LoginSessionDto loginSession) {
        String html = readExtraHtml(loginSession);
        if (!html.isBlank()) {
            return html;
        }
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "JLU course import requires loginSession.extra.html from the WebView course page");
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
}
