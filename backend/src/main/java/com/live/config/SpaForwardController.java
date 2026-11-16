package com.live.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** React Router 경로를 새로고침해도 index.html 을 돌려준다. (/api 와 정적 파일 제외) */
@Controller
public class SpaForwardController {
    @GetMapping({"/", "/{path:^(?!api|assets|actuator)[^.]*}", "/{path:^(?!api|assets|actuator)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
