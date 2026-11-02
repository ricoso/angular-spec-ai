package com.autohaus.backend.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SessionTokenService sessionTokenService;

    @BeforeEach
    void setUp() {
        SessionController controller = new SessionController(sessionTokenService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getToken_returnsTokenResponse() throws Exception {
        when(sessionTokenService.generateToken())
            .thenReturn(new SessionTokenService.TokenResult("test.token", "2026-04-16T12:00:00Z"));

        mockMvc.perform(get("/api/v1/session/token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("test.token"))
            .andExpect(jsonPath("$.expiresAt").value("2026-04-16T12:00:00Z"));
    }
}
