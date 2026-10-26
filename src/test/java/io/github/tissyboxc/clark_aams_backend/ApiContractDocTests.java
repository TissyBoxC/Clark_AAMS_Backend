package io.github.tissyboxc.clark_aams_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口文档与实现的一致性校验（P6-1）。
 *
 * <p>背景：`docs/api-contract.md` 手工维护 39 个端点的说明，
 * 但**没有任何环节验证它与代码一致**。这类漂移有两个方向，
 * 而且都不报错：
 *
 * <ul>
 *   <li>文档写了某个接口，代码里没有 —— 对接方照着文档调，得到 404</li>
 *   <li>代码里有某个接口，文档没写 —— 没人知道它存在，
 *       也没人意识到它需要鉴权或已经废弃</li>
 * </ul>
 *
 * <p>两个方向都要查，只查一边只能发现一半问题。
 *
 * <p>实现方式：扫描 Controller 上的 {@code @XxxMapping} 注解，
 * 与文档里用反引号包起来的路径比对。路径参数
 * （{@code {courseId}} 之类）统一归一成 {@code {}}，
 * 因为它们只是命名差异。
 */
class ApiContractDocTests {

    /** 仓库根目录：测试运行在子模块目录下，文档在上一层的 docs/。 */
    private static final Path DOC = locateDoc();

    /**
     * 仓库内的契约文档。
     *
     * <p>**必须是仓库内的一份**。此前只读 {@code ../docs/api-contract.md}，
     * 那是开发者机器上才有的路径——CI 只 checkout 本仓库，
     * 那条路径不存在，于是这些测试在本地全绿、在 CI 上全红。
     * 现在以本仓库副本为准，CI 里也能跑。
     */
    private static Path locateDoc() {
        List<String> candidates = List.of(
                "docs/api-contract.md",
                "../docs/api-contract.md"
        );
        for (String candidate : candidates) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                return path;
            }
        }
        return Paths.get("docs/api-contract.md").toAbsolutePath().normalize();
    }

    /** 从 Controller 源码里提取全部端点。 */
    private static Set<String> implementedEndpoints() throws IOException {
        Set<String> endpoints = new TreeSet<>();
        Path controllersDir = Paths.get("src/main/java")
                .toAbsolutePath().normalize();

        if (!Files.exists(controllersDir)) {
            return endpoints;
        }

        Pattern classMapping = Pattern.compile(
                "@RequestMapping\\((?:value\\s*=\\s*)?\"([^\"]+)\"");
                // 只匹配注解名本身，路径单独在括号里找。
        // 直接把整个括号当路径会踩坑：像
        //     @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, ...)
        // 这种没有路径、只有属性的写法，会把 "consumes = MediaType..." 当成路径。
        Pattern methodMapping = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping");

        try (Stream<Path> files = Files.walk(controllersDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher baseMatcher = classMapping.matcher(source);
                String base = baseMatcher.find() ? baseMatcher.group(1) : "";

                Matcher matcher = methodMapping.matcher(source);
                while (matcher.find()) {
                    String verb = matcher.group(1).toUpperCase();
                    String path = pathOf(source, matcher.end());
                    endpoints.add(normalize(verb + " " + base + path));
                }
            }
        }
        return endpoints;
    }

    /** 从文档里提取被反引号标注的路径。 */
    private static Set<String> documentedEndpoints() throws IOException {
        Set<String> documented = new TreeSet<>();
        if (!Files.exists(DOC)) {
            return documented;
        }

        // 表格行的形式：| GET | `/api/v1/...` | 权限 | 说明 |
        Pattern row = Pattern.compile(
                "^\\|\\s*((?:GET|POST|PUT|DELETE|PATCH)(?:\\s*/\\s*(?:GET|POST|PUT|DELETE|PATCH))*)\\s*\\|\\s*`([^`]+)`",
                Pattern.MULTILINE);
        String text = Files.readString(DOC, StandardCharsets.UTF_8);
        Matcher matcher = row.matcher(text);
        while (matcher.find()) {
            // 一行可能合并多个方法，例如 "GET/PUT"，逐个展开
            for (String verb : matcher.group(1).split("/")) {
                documented.add(normalize(verb.trim() + " " + matcher.group(2)));
            }
        }
        return documented;
    }


    /**
     * 从注解结束位置开始，取出该注解的路径参数。
     *
     * <p>只认括号里**以引号开头的第一个字符串字面量**。
     * 这样 `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
     * 这种没有路径的写法会正确返回空串，
     * 而不是把属性名当成路径。
     */
    private static String pathOf(String source, int searchFrom) {
        int cursor = searchFrom;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= source.length() || source.charAt(cursor) != '(') {
            // 注解后没有括号，说明用的是类级路径
            return "";
        }

        // 找到配对的右括号
        int depth = 0;
        int end = cursor;
        for (int i = cursor; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        String args = source.substring(cursor + 1, end);

        // value = "/x" / path = "/x" / 直接 "/x"
        Matcher explicit = Pattern.compile(
                "(?:value|path)\\s*=\\s*\\{\\s*\"([^\"]*)\"|(?:value|path)\\s*=\\s*\"([^\"]*)\"")
                .matcher(args);
        if (explicit.find()) {
            return explicit.group(1) != null ? explicit.group(1) : explicit.group(2);
        }

        // 位置参数：第一个字符串字面量，但必须前面没有 "xxx =" 的赋值
        Matcher leading = Pattern.compile("^\\s*\\{\\s*\"([^\"]*)\"|^\\s*\"([^\"]*)\"").matcher(args);
        if (leading.find()) {
            return leading.group(1) != null ? leading.group(1) : leading.group(2);
        }

        return "";
    }

    /** 把路径参数统一成 {} ，并把多余的斜杠收掉。 */
    private static String normalize(String endpoint) {
        String cleaned = endpoint
                .replaceAll("\\{[^}]*}", "{}")
                .replaceAll("/+", "/")
                .replaceAll("\\s+", " ")
                .trim();
        // 去掉尾部的单独斜杠
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    @Test
    @DisplayName("文档能找到")
    void docExists() {
        assertTrue(Files.exists(DOC),
                "找不到 docs/api-contract.md，实际查找路径：" + DOC);
    }

    @Test
    @DisplayName("仓库内副本与顶层 docs 副本内容一致")
    void repoCopyMatchesWorkspaceCopy() throws IOException {
        // 顶层 docs/ 只在开发机上存在（CI 里没有），所以本测试在 CI 上直接跳过。
        // 但只要它存在，两份就不能不一样——否则改了顶层那份、
        // 以为生效了，实际校验的却是旧内容。
        Path workspaceCopy = Paths.get("../docs/api-contract.md").toAbsolutePath().normalize();
        if (!Files.exists(DOC) || !Files.exists(workspaceCopy)) {
            return;
        }
        if (DOC.toAbsolutePath().normalize().equals(workspaceCopy)) {
            return;
        }

        String repoText = normalizeNewlines(Files.readString(DOC, StandardCharsets.UTF_8));
        String workspaceText = normalizeNewlines(
                Files.readString(workspaceCopy, StandardCharsets.UTF_8));

        assertTrue(repoText.equals(workspaceText),
                "两份 api-contract.md 内容不一致：\n  "
                        + DOC + "\n  " + workspaceCopy
                        + "\n\n顶层那份是需要同步进仓库的（CI 只读仓库内那份）。"
                        + "确认哪份是对的之后，把另一份覆盖掉。");
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }

    @Test
    @DisplayName("文档里写到的端点，代码里都实现了")
    void documentedEndpointsExist() throws IOException {
        Set<String> implemented = implementedEndpoints();
        Set<String> documented = documentedEndpoints();

        assertTrue(documented.size() > 20,
                "文档里只解析出 " + documented.size() + " 个端点，解析规则可能已失效");

        Set<String> missing = new TreeSet<>(documented);
        missing.removeAll(implemented);

        assertTrue(missing.isEmpty(),
                "以下端点在文档里有、代码里没有（对接方照文档调会 404）：\n  "
                        + String.join("\n  ", missing)
                        + "\n\n若接口已下线，请同步删除文档条目。");
    }

    @Test
    @DisplayName("代码里实现的端点，文档里都写了")
    void implementedEndpointsDocumented() throws IOException {
        Set<String> implemented = implementedEndpoints();
        Set<String> documented = documentedEndpoints();

        assertTrue(implemented.size() > 20,
                "只扫描到 " + implemented.size() + " 个端点，扫描规则可能已失效");

        Set<String> undocumented = new LinkedHashSet<>(implemented);
        undocumented.removeAll(documented);

        assertTrue(undocumented.isEmpty(),
                "以下端点在代码里实现了、文档里没写（没人知道它存在）：\n  "
                        + String.join("\n  ", undocumented)
                        + "\n\n请在 docs/api-contract.md 补上说明，包括权限要求。");
    }
}
