package com.gak.gakstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GnomishArmyKnife 启动类
 * 侏儒军刀 · 自用万花筒应用
 */
@SpringBootApplication(scanBasePackages = "com.gak")
public class GnomishArmyKnifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GnomishArmyKnifeApplication.class, args);
    }

}
