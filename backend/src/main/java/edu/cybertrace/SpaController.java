package edu.cybertrace;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sends client-side pages back to React while keeping API routes separate. */
@Controller
class SpaController {
    @GetMapping({"/login", "/register", "/register-evidence", "/evidence", "/evidence/{id}",
            "/verify", "/custody", "/audit", "/alerts", "/cases", "/users", "/reports"})
    String appPage() {
        return "forward:/index.html";
    }
}
