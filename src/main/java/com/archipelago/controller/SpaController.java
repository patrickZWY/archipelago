package com.archipelago.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/explore",
            "/connections",
            "/network",
            "/global-graphs",
            "/friend",
            "/shared/{token}",
            "/verify",
            "/reset-password"
    })
    public String forwardToSpaShell() {
        return "forward:/index.html";
    }
}
