package com.gak.gakstart.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hello World 测试接口
 */
@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello Gnomish Army Knife 🗡️";
    }
}
