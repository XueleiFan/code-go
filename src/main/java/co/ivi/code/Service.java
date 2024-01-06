/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package co.ivi.code;

import jakarta.servlet.http.HttpSession;
import org.springframework.boot.ApplicationArguments;

import java.util.HashMap;

@org.springframework.stereotype.Service
public class Service {
    private final static String NAME_EV = "code-evaluator";
    private final HashMap<String, Evaluator> evaluatorMap = new HashMap<>();

    public Service(ApplicationArguments args) {
        // System.out.println("Application arguments are available");
    }

    public EvaluationResult evaluateCode(String code, HttpSession session) {
        Evaluator evaluator = null;
        if (session.getAttribute(NAME_EV) instanceof String id) {
            evaluator = evaluatorMap.get(id);
        }

        if (evaluator == null) {
            String sessionID = session.getId();
            try {
                evaluator = new Evaluator(ev -> evaluatorMap.remove(sessionID)).start();
            } catch (Exception ex) {
                return new EvaluationResult(false, "Service load failed!");
            }
            evaluatorMap.put(session.getId(), evaluator);
            session.setAttribute(NAME_EV, session.getId());
        }

        return evaluator.evaluate(code);
    }
}
