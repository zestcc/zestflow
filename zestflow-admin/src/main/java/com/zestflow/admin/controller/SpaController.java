package com.zestflow.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由转发 Controller
 * 将所有前端路由转发到 index.html，支持 Vue Router 的 HTML5 History 模式
 */
@Controller
public class SpaController {

    @GetMapping({
        "/login", "/register", "/forgot", "/reset-password", "/verify-email", "/force-password",
        "/dashboard",
        "/chains", "/chains/**",
        "/design", "/design/**",
        "/schedules",
        "/logs",
        "/executors",
        "/collectors",
        "/components",
        "/playground", "/playground/**",
        "/settings", "/settings/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
