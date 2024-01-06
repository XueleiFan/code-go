/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package co.ivi.code;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class IdTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void noIdSupport() {
        String code = """
                var i = 0;
                /1
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertFalse(er.status());
        assertTrue(er.message().contains("Invalid command: /1"));
    }
}
