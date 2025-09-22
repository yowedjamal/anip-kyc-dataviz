package contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test contractuel pour l'endpoint POST /sessions/{id}/documents
 * 
 * Contract: POST /sessions/{session_id}/documents
 * - Input: multipart/form-data {file: binary, document_type: string, page_number?: int}
 * - Output 201: {document_id: uuid, document_type: string, file_url: string, processing_status: "PENDING", uploaded_at: iso_date}
 * - Output 400: {error: "INVALID_REQUEST", message: string, details: object}
 * - Output 404: {error: "SESSION_NOT_FOUND", message: string}
 * - Output 413: {error: "FILE_TOO_LARGE", message: string, max_size: string}
 * 
 * CE TEST DOIT ÉCHOUER avant implémentation du DocumentController
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class DocumentUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testUploadDocument_Success_IdCard() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "id_card.jpg", 
            "image/jpeg", 
            "fake-image-content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .param("document_type", "ID_CARD_FRONT")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.document_id").exists())
                .andExpect(jsonPath("$.document_id").isString())
                .andExpect(jsonPath("$.document_type").value("ID_CARD_FRONT"))
                .andExpect(jsonPath("$.file_url").exists())
                .andExpect(jsonPath("$.processing_status").value("PENDING"))
                .andExpect(jsonPath("$.uploaded_at").exists())
                .andExpect(jsonPath("$.file_size").exists())
                .andExpect(jsonPath("$.mime_type").value("image/jpeg"));
    }

    @Test
    public void testUploadDocument_Success_Passport() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440001";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "passport.pdf", 
            "application/pdf", 
            "fake-pdf-content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .param("document_type", "PASSPORT")
                .param("page_number", "1")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.document_id").exists())
                .andExpect(jsonPath("$.document_type").value("PASSPORT"))
                .andExpect(jsonPath("$.page_number").value(1))
                .andExpect(jsonPath("$.file_url").exists())
                .andExpect(jsonPath("$.processing_status").value("PENDING"))
                .andExpect(jsonPath("$.mime_type").value("application/pdf"));
    }

    @Test
    public void testUploadDocument_InvalidRequest_MissingFile() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .param("document_type", "ID_CARD_FRONT")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.file").value("REQUIRED"));
    }

    @Test
    public void testUploadDocument_InvalidRequest_MissingDocumentType() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "document.jpg", 
            "image/jpeg", 
            "fake-content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.document_type").value("REQUIRED"));
    }

    @Test
    public void testUploadDocument_InvalidRequest_InvalidDocumentType() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "document.jpg", 
            "image/jpeg", 
            "fake-content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .param("document_type", "INVALID_TYPE")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.document_type").value("INVALID_VALUE"));
    }

    @Test
    public void testUploadDocument_InvalidRequest_UnsupportedFileType() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "document.txt", 
            "text/plain", 
            "text content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .param("document_type", "ID_CARD_FRONT")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.file_type").value("UNSUPPORTED"));
    }

    @Test
    public void testUploadDocument_SessionNotFound() throws Exception {
        String nonExistentSessionId = "00000000-0000-0000-0000-000000000000";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "document.jpg", 
            "image/jpeg", 
            "fake-content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", nonExistentSessionId)
                .file(file)
                .param("document_type", "ID_CARD_FRONT")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.session_id").value(nonExistentSessionId));
    }

    @Test
    public void testUploadDocument_FileTooLarge() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        // Simulation d'un fichier de 10MB (trop volumineux)
        byte[] largeContent = new byte[10 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "large_document.jpg", 
            "image/jpeg", 
            largeContent
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .param("document_type", "ID_CARD_FRONT")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.max_size").exists())
                .andExpect(jsonPath("$.received_size").exists());
    }

    @Test
    public void testUploadDocument_Unauthorized() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "document.jpg", 
            "image/jpeg", 
            "fake-content".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/documents", sessionId)
                .file(file)
                .param("document_type", "ID_CARD_FRONT"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }
}