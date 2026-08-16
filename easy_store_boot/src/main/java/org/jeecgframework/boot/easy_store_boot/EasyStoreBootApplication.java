package org.jeecgframework.boot.easy_store_boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "org.jeecgframework.boot.easy_store_boot.app"
})
public class EasyStoreBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyStoreBootApplication.class, args);
    }

}
