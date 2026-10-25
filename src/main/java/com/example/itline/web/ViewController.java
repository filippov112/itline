package com.example.itline.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Отдаёт SPA-страницу по /itline.
 */
@Controller
public class ViewController {

    @GetMapping("/itline")
    public String itline() {
        // forward, а не redirect: URL в браузере остаётся /itline
        return "forward:/itline.html";
    }
}