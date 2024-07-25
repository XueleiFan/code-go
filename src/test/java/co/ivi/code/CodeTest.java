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
public class CodeTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void helloWorld() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println("Hello, World!");
                    }
                }
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
    }

    @Test
    void printHelloWorld() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println("Hello, World!");
                    }
                }
                
                HelloWorld.main();
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("Hello, World!"));
    }

    @Test
    void helloWorldWithArgs() {
        String code = """
                class HelloWorld {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                
                HelloWorld.main(null);
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("Hello, World!"));
    }

    @Test
    void intAssign() {
        String code = """
                int i = 0;
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
    }

    @Test
    void NoName() {
        String code = """
                0xC
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("int $1 ==> 12"));
    }

    @Test
    void varInt() {
        String code = """
                var i = 0;
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("i ==> 0"));
    }

    @Test
    void varNoAssign() {
        String code = """
                var i;
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertFalse(er.status());
        assertTrue(er.message().contains("cannot infer type"));
    }

    @Test
    void intValue() {
        String code = """
                var i = 0;
                i
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().split("i ==> 0").length > 2);
    }
}
