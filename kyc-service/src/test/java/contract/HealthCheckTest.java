package contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test contractuel pour l'endpoint GET /health
 * 
 * Contract: GET /health
 * - Input: aucun
 * - Output 200: {status: "UP", timestamp: iso_date, version: string, services: object}
 * - Output 503: {status: "DOWN", timestamp: iso_date, services: object, errors: array}
 * 
 * CE TEST DOIT ÉCHOUER avant implémentation du HealthController
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class HealthCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testHealthCheck_Success() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.services").exists())
                .andExpect(jsonPath("$.services.database").exists())
                .andExpect(jsonPath("$.services.minio").exists())
                .andExpect(jsonPath("$.services.redis").exists());
    }

    @Test
    public void testHealthCheck_Detailed() throws Exception {
        mockMvc.perform(get("/health?detail=true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.services.database.status").exists())
                .andExpect(jsonPath("$.services.database.response_time").exists())
                .andExpect(jsonPath("$.services.minio.status").exists())
                .andExpect(jsonPath("$.services.redis.status").exists())
                .andExpect(jsonPath("$.memory_usage").exists())
                .andExpect(jsonPath("$.disk_usage").exists());
    }
}