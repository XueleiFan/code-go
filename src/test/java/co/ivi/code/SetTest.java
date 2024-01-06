/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package co.ivi.code;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
public class SetTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void pureSet() {
        String code = """
                /set
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set feedback normal"));
    }

    @Test
    void feedbackSet() {
        String code = """
                /set feedback
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set feedback normal"));
    }

    @Test
    void modeSet() {
        String code = """
                /set mode
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set format normal"));
    }

    @Test
    void truncationSet() {
        String code = """
                /set truncation
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set truncation normal"));
    }

    @Test
    void formatSet() {
        String code = """
                /set format
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set format normal"));
    }

    @Test
    void verboseFeedback() {
        String code = """
                /set feedback verbose
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(String.format(er.message()));
        assertTrue(er.status());
        assertTrue(er.message().contains("Feedback mode: verbose"));
    }
}
