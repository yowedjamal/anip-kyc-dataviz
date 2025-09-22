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
 * Test contractuel pour l'endpoint POST /sessions/{id}/face-verification
 * 
 * Contract: POST /sessions/{session_id}/face-verification
 * - Input: {reference_image_id: uuid, live_image: base64_string, verification_type: string}
 * - Output 200: {match_score: float, is_match: boolean, confidence: float, verification_id: uuid, processed_at: iso_date}
 * - Output 400: {error: "INVALID_REQUEST", message: string, details: object}
 * - Output 404: {error: "SESSION_NOT_FOUND" | "REFERENCE_IMAGE_NOT_FOUND", message: string}
 * 
 * CE TEST DOIT ÉCHOUER avant implémentation du FaceVerificationController
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class FaceVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFaceVerification_Success_HighMatch() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        String requestBody = """
            {
                "reference_image_id": "550e8400-e29b-41d4-a716-446655440001",
                "live_image": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...",
                "verification_type": "SIMILARITY_CHECK"
            }
            """;

        mockMvc.perform(post("/sessions/{sessionId}/face-verification", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.verification_id").exists())
                .andExpect(jsonPath("$.match_score").isNumber())
                .andExpect(jsonPath("$.match_score").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.match_score").value(org.hamcrest.Matchers.lessThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.is_match").isBoolean())
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.processed_at").exists())
                .andExpect(jsonPath("$.verification_type").value("SIMILARITY_CHECK"));
    }

    @Test
    public void testFaceVerification_InvalidRequest_MissingReferenceImage() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        String requestBody = """
            {
                "live_image": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...",
                "verification_type": "SIMILARITY_CHECK"
            }
            """;

        mockMvc.perform(post("/sessions/{sessionId}/face-verification", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.reference_image_id").value("REQUIRED"));
    }

    @Test
    public void testFaceVerification_SessionNotFound() throws Exception {
        String nonExistentSessionId = "00000000-0000-0000-0000-000000000000";
        String requestBody = """
            {
                "reference_image_id": "550e8400-e29b-41d4-a716-446655440001",
                "live_image": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...",
                "verification_type": "SIMILARITY_CHECK"
            }
            """;

        mockMvc.perform(post("/sessions/{sessionId}/face-verification", nonExistentSessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }
}