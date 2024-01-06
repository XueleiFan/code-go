/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package co.ivi.code;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@SpringBootApplication
@EnableSpringHttpSession // Enable Spring Session
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

