package com.autohaus.backend.session;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final SessionTokenService sessionTokenService;

    public SessionController(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/token")
    public SessionTokenService.TokenResult getToken() {
        return sessionTokenService.generateToken();
    }
}
