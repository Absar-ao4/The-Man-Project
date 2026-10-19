package com.example.fitapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class OpenApiAndHealthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApiDocumentIsPublicAndListsTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("The Man Project API"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/me/profile']").exists())
                .andExpect(jsonPath("$.paths['/api/me/targets/suggest']").exists());
    }

    @Test
    void swaggerUiIsReachableWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui")).andExpect(status().is3xxRedirection());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unauthenticatedCallersGetAProblemDetailEvenOnUnknownRoutes() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
