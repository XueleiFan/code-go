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
public class VarsTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void noVars() {
        String code = """
                /vars
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void oneVars() {
        String code = """
                var i = 0;
                /vars
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("int i = 0"));
    }

    @Test
    void moreVars() {
        String code = """
                var i = 0;
                String s = "Hello!";
                /vars
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("int i = 0"));
        assertTrue(er.message().contains("String s = \"Hello!\""));
    }

    @Test
    void varsWithDrop() {
        String code = """
                var i = 0;
                String s = "Hello!";
                /drop i
                /vars
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertFalse(er.message().contains("int i = 0"));
        assertTrue(er.message().contains("String s = \"Hello!\""));
    }

    @Test
    void varsAll() {
        String code = """
                var i = 0;
                String s = "Hello!";
                /vars -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("int i = 0"));
        assertTrue(er.message().contains("String s = \"Hello!\""));
    }

    @Test
    void varsAllWithDrop() {
        String code = """
                var i = 0;
                String s = "Hello!";
                /drop i
                /vars -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("int i = (not-active)"));
        assertTrue(er.message().contains("String s = \"Hello!\""));
    }

    @Test
    void varsById() {
        String code = """
                var i = 0;
                /vars 1
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("int i = 0"));
    }

    @Test
    void varsByName() {
        String code = """
                var i = 0;
                /vars i
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("int i = 0"));
    }
}
