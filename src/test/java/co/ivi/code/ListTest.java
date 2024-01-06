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
public class ListTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void pureList() {
        String code = """
                /list
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void mixedList() {
        String code = """
                var i = 0;
                /list
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("var i = 0;"));
    }

    @Test
    void listStart() {
        String code = """
                /list -start
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void listAll() {
        String code = """
                /list -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void listAllWithCode() {
        String code = """
                var i = 0;
                /list -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("var i = 0;"));
    }

    @Test
    void listWithId() {
        String code = """
                var i = 0;
                /list 1
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("1 : var i = 0;"));
    }

    @Test
    void listWithName() {
        String code = """
                var i = 0;
                /list i
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("1 : var i = 0;"));
    }
}
