/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package co.ivi.code;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "https://code.ivi.co,https://note.ivi.co")
public class Controller {
    private final Service service;

    @Autowired
    public Controller(Service service) {
        this.service = service;
    }

    @PostMapping("/go")
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request,
                                     HttpSession session) {
        // Forward the received code to the service for evaluation
        return service.evaluateCode(request.code(), session);
    }
}

