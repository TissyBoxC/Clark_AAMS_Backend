package io.github.tissyboxc.clark_aams_backend.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping({"/admin", "/admin/"})
    public String entry() {
        return "redirect:/admin/index.html";
    }
}
