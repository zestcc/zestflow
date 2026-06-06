package com.zestflow.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径常见静态资源（浏览器/爬虫默认请求，避免 404 刷 ERROR 日志）。
 */
@Controller
public class RootResourceController {

    /** 部分浏览器仍请求 .ico，转发到 Vite 构建产物中的 favicon.svg */
    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/favicon.svg";
    }
}
