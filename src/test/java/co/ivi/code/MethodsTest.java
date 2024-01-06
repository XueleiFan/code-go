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
public class MethodsTest {
    @Autowired
    private Service service;

    @MockBean
    private HttpSession session;

    @Test
    void noMethods() {
        String code = """
                /methods
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().isEmpty());
    }

    @Test
    void oneVars() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                /methods
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("void main()"));
    }

    @Test
    void moreVars() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                boolean func() {
                    return false;
                }
                /methods
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("created method func()"));
        assertTrue(er.message().contains("void main()"));
        assertTrue(er.message().contains("boolean func()"));
    }

    @Test
    void methodsWithDrop() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                boolean func() {
                    return false;
                }
                /drop main
                /methods
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("created method func()"));
        assertTrue(er.message().contains("boolean func()"));
    }

    @Test
    void methodsAll() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                boolean func() {
                    return false;
                }
                /methods -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("created method func()"));
        assertTrue(er.message().contains("void main()"));
        assertTrue(er.message().contains("boolean func()"));
    }

    @Test
    void methodsAllWithDrop() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                boolean func() {
                    return false;
                }
                /drop main
                /methods -all
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("created method func()"));
        assertTrue(er.message().contains("void main()(not-active)"));
        assertTrue(er.message().contains("boolean func()"));
    }

    @Test
    void methodsById() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                boolean func() {
                    return false;
                }
                /methods 1
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("created method func()"));
        assertTrue(er.message().contains("void main()"));
    }

    @Test
    void methodsByName() {
        String code = """
                public static void main() {
                   System.out.println("Hello!");
                }
                boolean func() {
                    return false;
                }
                /methods main
                """;
        EvaluationResult er = service.evaluateCode(code, session);
        System.out.println(er.message());
        assertTrue(er.status());
        assertTrue(er.message().contains("created method main()"));
        assertTrue(er.message().contains("created method func()"));
        assertTrue(er.message().contains("void main()"));
    }
}
