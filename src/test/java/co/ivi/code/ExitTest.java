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

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ExitTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void mixedExit() {
        String code = """
                int i = 0;
                /exit
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("Goodbye"));
        assertTrue(er.message().contains("Playground session terminated"));
    }

    @Test
    void pureExit() {
        String code = """
                /exit
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("Goodbye"));
        assertTrue(er.message().contains("Playground session terminated"));
    }

    @Test
    void afterExit() {
        String code = """
                int i = 0;
                /exit
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("Goodbye"));
        assertTrue(er.message().contains("Playground session terminated"));

        code = """
                /list
                """;
        er = service.evaluateCode(code, session);
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }
}
