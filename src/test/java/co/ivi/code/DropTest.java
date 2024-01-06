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
public class DropTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void noDrop() {
        String code = """
                var i = 0;
                i
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().split("i ==> 0").length > 2);
    }

    @Test
    void dropByName() {
        String code = """
                var i = 0;
                /drop i
                i
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertFalse(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("dropped variable i"));
        assertTrue(er.message().contains("cannot find symbol"));
    }

    @Test
    void dropById() {
        String code = """
                var i = 0;
                /drop 1
                i
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertFalse(er.status());
        assertTrue(er.message().contains("i ==> 0"));
        assertTrue(er.message().contains("dropped variable i"));
        assertTrue(er.message().contains("cannot find symbol"));
    }
}
