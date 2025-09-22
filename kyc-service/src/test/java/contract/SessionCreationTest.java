package contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test contractuel pour l'endpoint POST /sessions
 * 
 * Contract: POST /sessions
 * - Input: {user_id: string, verification_type: string, metadata?: object}
 * - Output 201: {session_id: uuid, status: "INITIATED", created_at: iso_date, expires_at: iso_date}
 * - Output 400: {error: "INVALID_REQUEST", message: string, details: object}
 * - Output 401: {error: "UNAUTHORIZED", message: string}
 * 
 * CE TEST DOIT ÉCHOUER avant implémentation du SessionController
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class SessionCreationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testCreateSession_Success() throws Exception {
        String requestBody = """
            {
                "user_id": "user123",
                "verification_type": "FULL_KYC",
                "metadata": {
                    "client_ip": "192.168.1.1",
                    "user_agent": "Mozilla/5.0"
                }
            }
            """;

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.session_id").exists())
                .andExpect(jsonPath("$.session_id").isString())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.expires_at").exists())
                .andExpect(jsonPath("$.verification_type").value("FULL_KYC"))
                .andExpect(jsonPath("$.user_id").value("user123"));
    }

    @Test
    public void testCreateSession_InvalidRequest_MissingUserId() throws Exception {
        String requestBody = """
            {
                "verification_type": "FULL_KYC"
            }
            """;

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.user_id").value("REQUIRED"));
    }

    @Test
    public void testCreateSession_InvalidRequest_InvalidVerificationType() throws Exception {
        String requestBody = """
            {
                "user_id": "user123",
                "verification_type": "INVALID_TYPE"
            }
            """;

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.verification_type").value("INVALID_VALUE"));
    }

    @Test
    public void testCreateSession_Unauthorized_MissingToken() throws Exception {
        String requestBody = """
            {
                "user_id": "user123",
                "verification_type": "FULL_KYC"
            }
            """;

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    public void testCreateSession_Unauthorized_InvalidToken() throws Exception {
        String requestBody = """
            {
                "user_id": "user123",
                "verification_type": "FULL_KYC"
            }
            """;

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    public void testCreateSession_Success_MinimalRequest() throws Exception {
        String requestBody = """
            {
                "user_id": "user456",
                "verification_type": "DOCUMENT_ONLY"
            }
            """;

        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.session_id").exists())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.verification_type").value("DOCUMENT_ONLY"))
                .andExpect(jsonPath("$.user_id").value("user456"))
                .andExpect(jsonPath("$.metadata").isEmpty());
    }
}