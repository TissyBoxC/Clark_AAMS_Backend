package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolConfigProvider;
import io.github.tissyboxc.clark_aams_backend.school.SchoolEntity;
import org.springframework.stereotype.Component;

@Component
public class JitSchoolConfig implements SchoolConfigProvider {
    @Override
    public SchoolEntity school() {
        return new SchoolEntity(
                "jit",
                "金陵科技学院",
                "JIT",
                true,
                true,
                false,
                "webview",
                "https://jwxt.jit.edu.cn/default2.aspx",
                new String[]{"https://jwxt.jit.edu.cn/xs_main.aspx*"},
                "jit",
                1
        );
    }
}
