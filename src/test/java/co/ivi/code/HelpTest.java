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
public class HelpTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void cmdHelp() {
        String code = """
                /help
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("Type a Java language expression, statement, or declaration"));
    }

    @Test
    void helpIntro() {
        String code = """
                /help intro
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("intro"));
    }

    @Test
    void helpQuest() {
        String code = """
                /?
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("Type a Java language expression, statement, or declaration"));
    }

    @Test
    void helpId() {
        String code = """
                /help id
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("unique snippet ID"));
    }

    @Test
    void helpContext() {
        String code = """
                /help context
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("context"));
    }

    @Test
    void helpSlashList() {
        String code = """
                /help /list
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/list"));
    }

    @Test
    void helpList() {
        String code = """
                /help list
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/list"));
    }

    @Test
    void helpHelp() {
        String code = """
                /help help
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/help"));
    }

    @Test
    void helpDrop() {
        String code = """
                /help drop
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/drop"));
    }

    @Test
    void helpVars() {
        String code = """
                /help vars
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/vars"));
    }

    @Test
    void helpMethods() {
        String code = """
                /help methods
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/methods"));
    }

    @Test
    void helpTypes() {
        String code = """
                /help types
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/types"));
    }

    @Test
    void helpImports() {
        String code = """
                /help imports
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/imports"));
    }

    @Test
    void helpExit() {
        String code = """
                /help exit
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/exit"));
    }

    @Test
    void helpEnv() {
        String code = """
                /help env
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/env"));
    }

    @Test
    void helpReset() {
        String code = """
                /help reset
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/reset"));
    }

    @Test
    void helpSet() {
        String code = """
                /help set
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set"));
    }

    @Test
    void helpSetMode() {
        String code = """
                /help set mode
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set mode"));
    }

    @Test
    void helpSetFormat() {
        String code = """
                /help set format
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set format"));
    }

    @Test
    void helpSetFeedback() {
        String code = """
                /help set feedback
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set feedback"));
    }

    @Test
    void helpSetTruncation() {
        String code = """
                /help set truncation
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("/set truncation"));
    }
}
