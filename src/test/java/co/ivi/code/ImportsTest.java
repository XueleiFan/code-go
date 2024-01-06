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
public class ImportsTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void noMethods() {
        String code = """
                /imports
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void oneImports() {
        String code = """
                import java.net.*;
                /imports
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("  import java.net.*"));
    }

    @Test
    void moreImports() {
        String code = """
                import java.net.*;
                import java.io.*;
                /imports
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("  import java.net.*"));
        assertTrue(er.message().contains("  import java.io.*"));
    }

    @Test
    void importsWithDrop() {
        String code = """
                import java.net.*;
                import java.io.*;
                /drop 1
                /imports
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("  import java.io.*"));
        assertFalse(er.message().contains("  import java.net.*"));
    }
}
