package io.github.tissyboxc.clark_aams_backend.security;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import io.github.tissyboxc.clark_aams_backend.user.UserController;
import io.github.tissyboxc.clark_aams_backend.user.UserProfileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 统一的「当前用户」解析。
 *
 * <p>背景（审查条目 A2/D1）：`UserController` 内部有一套私有的
 * session 读取 + `X-Clark-Aams-Login-Code` 头回退逻辑，但识别、取件码、
 * 题目解析等接口完全没有校验，任何人都能匿名调用并消耗 AI 额度。
 *
 * <p>这里把解析逻辑抽出来，供过滤器与各控制器共用，保证判定一致。
 */
@Component
public class CurrentUserResolver {

    /** App 通过该头携带登录码，兼容未建立 session 的场景。 */
    public static final String LOGIN_CODE_HEADER = "X-Clark-Aams-Login-Code";

    private final UserAccountService userAccountService;

    public CurrentUserResolver(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /**
     * 解析当前用户 id；未登录返回 null。
     *
     * <p>顺序：先看 session，再退回登录码头。登录码头命中时会顺带
     * 建立 session，避免每个请求都查一次库。
     */
    public Long resolveUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(UserController.SESSION_KEY);
            if (value instanceof Long longValue) {
                return longValue;
            }
            if (value instanceof Integer intValue) {
                return intValue.longValue();
            }
        }

        String loginCode = header(request, LOGIN_CODE_HEADER);
        if (loginCode.isBlank()) {
            return null;
        }
        try {
            UserProfileDto user = userAccountService.getByLoginCode(loginCode);
            request.getSession(true).setAttribute(UserController.SESSION_KEY, user.id());
            return user.id();
        } catch (BusinessException exception) {
            return null;
        }
    }

    /** 解析当前用户 id；未登录抛 401。 */
    public long requireUserId(HttpServletRequest request) {
        Long id = resolveUserId(request);
        if (id == null) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "请先登录");
        }
        return id;
    }

    /** 解析当前用户；未登录抛 401。 */
    public UserProfileDto requireUser(HttpServletRequest request) {
        return userAccountService.getById(requireUserId(request));
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
