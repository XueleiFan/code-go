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
public class TypesTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void noMethods() {
        String code = """
                /types
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void oneVars() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                /types
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("  class HelloWorld"));
    }

    @Test
    void moreVars() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                interface Func {
                    void doIt();
                }
                /types
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("created interface Func"));
        assertTrue(er.message().contains("  class HelloWorld"));
        assertTrue(er.message().contains("  interface Func"));
    }

    @Test
    void typesWithDrop() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                interface Func {
                    void doIt();
                }
                /drop Func
                /types
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("created interface Func"));
        assertTrue(er.message().contains("dropped interface Func"));
        assertTrue(er.message().contains("  class HelloWorld"));
        assertFalse(er.message().contains("  interface Func"));
    }

    @Test
    void typesAll() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                interface Func {
                    void doIt();
                }
                /types -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("created interface Func"));
        assertTrue(er.message().contains("  class HelloWorld"));
        assertTrue(er.message().contains("  interface Func"));
    }

    @Test
    void typesAllWithDrop() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                interface Func {
                    void doIt();
                }
                /drop Func
                /types -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("created interface Func"));
        assertTrue(er.message().contains("dropped interface Func"));
        assertTrue(er.message().contains("  class HelloWorld"));
        assertTrue(er.message().contains("  interface Func(not-active)"));
    }

    @Test
    void typesById() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                interface Func {
                    void doIt();
                }
                /types 1
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("created interface Func"));
        assertTrue(er.message().contains("  class HelloWorld"));
    }

    @Test
    void typesByName() {
        String code = """
                class HelloWorld {
                    public static void main() {
                        System.out.println();
                    }
                }
                interface Func {
                    void doIt();
                }
                /types HelloWorld
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created class HelloWorld"));
        assertTrue(er.message().contains("created interface Func"));
        assertTrue(er.message().contains("  class HelloWorld"));
    }
}
