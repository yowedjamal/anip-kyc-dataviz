"""
Test d'intégration - Workflow KYC complet (Scénario 1)

Ce test valide le scénario utilisateur complet du quickstart.md :
1. Agent KYC initie une session de vérification
2. Upload document d'identité (carte d'identité)
3. Capture photo pour vérification faciale
4. Test de vivacité (liveness detection)
5. Récupération du statut final

Performance attendue : < 5 secondes pour le workflow complet
CE TEST DOIT ÉCHOUER avant implémentation complète
"""

import unittest
import requests
import time
import base64
from typing import Dict, Any


class KycWorkflowCompleteTest(unittest.TestCase):
    """Test d'intégration pour le workflow KYC complet"""
    
    def setUp(self):
        """Configuration initiale des tests"""
        self.kyc_service_url = "http://localhost:8081"
        self.dashboard_service_url = "http://localhost:8082"
        self.auth_token = "Bearer mock-jwt-token"
        self.headers = {
            "Authorization": self.auth_token,
            "Content-Type": "application/json"
        }
        self.workflow_start_time = None

    def test_complete_kyc_workflow_success(self):
        """Test du workflow KYC complet avec succès"""
        self.workflow_start_time = time.time()
        
        # Étape 1: Créer une session KYC
        session_response = self._create_kyc_session()
        self.assertEqual(session_response.status_code, 201)
        self.assertIn("session_id", session_response.json())
        
        session_id = session_response.json()["session_id"]
        session_data = session_response.json()
        
        # Vérifier la structure de la réponse
        self.assertEqual(session_data["status"], "INITIATED")
        self.assertEqual(session_data["verification_type"], "FULL_KYC")
        self.assertIn("expires_at", session_data)
        
        # Étape 2: Upload document d'identité
        document_response = self._upload_identity_document(session_id)
        self.assertEqual(document_response.status_code, 201)
        
        document_data = document_response.json()
        self.assertEqual(document_data["document_type"], "ID_CARD_FRONT")
        self.assertEqual(document_data["processing_status"], "PENDING")
        self.assertIn("document_id", document_data)
        
        # Attendre le traitement OCR (simulation)
        time.sleep(1)
        
        # Étape 3: Vérification faciale
        face_verification_response = self._perform_face_verification(
            session_id, 
            document_data["document_id"]
        )
        self.assertEqual(face_verification_response.status_code, 200)
        
        face_data = face_verification_response.json()
        self.assertIn("match_score", face_data)
        self.assertIn("is_match", face_data)
        self.assertIn("confidence", face_data)
        self.assertGreaterEqual(face_data["match_score"], 0.0)
        self.assertLessEqual(face_data["match_score"], 1.0)
        
        # Étape 4: Test de vivacité
        liveness_response = self._perform_liveness_detection(session_id)
        self.assertEqual(liveness_response.status_code, 200)
        
        liveness_data = liveness_response.json()
        self.assertIn("is_live", liveness_data)
        self.assertIn("confidence", liveness_data)
        self.assertIn("liveness_score", liveness_data)
        
        # Étape 5: Vérifier le statut final
        final_status_response = self._get_session_status(session_id)
        self.assertEqual(final_status_response.status_code, 200)
        
        final_data = final_status_response.json()
        self.assertIn(final_data["status"], ["COMPLETED", "APPROVED", "PENDING_REVIEW"])
        self.assertIn("verification_results", final_data)
        
        # Vérification des performances
        total_time = time.time() - self.workflow_start_time
        self.assertLess(total_time, 5.0, f"Workflow trop lent: {total_time:.2f}s > 5s")
        
        print(f"✓ Workflow KYC complet terminé en {total_time:.2f} secondes")

    def test_complete_kyc_workflow_document_rejected(self):
        """Test workflow avec document rejeté"""
        # Étape 1: Créer session
        session_response = self._create_kyc_session()
        session_id = session_response.json()["session_id"]
        
        # Étape 2: Upload document invalide
        invalid_document_response = self._upload_invalid_document(session_id)
        self.assertEqual(invalid_document_response.status_code, 201)
        
        # Attendre traitement
        time.sleep(1)
        
        # Étape 3: Vérifier le statut (devrait être FAILED)
        status_response = self._get_session_status(session_id)
        status_data = status_response.json()
        
        self.assertIn(status_data["status"], ["FAILED", "REJECTED"])
        self.assertIn("rejection_reasons", status_data)

    def test_complete_kyc_workflow_face_verification_failed(self):
        """Test workflow avec échec de vérification faciale"""
        # Créer session et upload document valide
        session_response = self._create_kyc_session()
        session_id = session_response.json()["session_id"]
        
        document_response = self._upload_identity_document(session_id)
        document_id = document_response.json()["document_id"]
        
        # Vérification faciale avec photo différente
        different_face_response = self._perform_face_verification_with_different_person(
            session_id, document_id
        )
        self.assertEqual(different_face_response.status_code, 200)
        
        face_data = different_face_response.json()
        self.assertFalse(face_data["is_match"])
        self.assertLess(face_data["match_score"], 0.7)

    def _create_kyc_session(self) -> requests.Response:
        """Créer une session KYC"""
        payload = {
            "user_id": "test_user_12345",
            "verification_type": "FULL_KYC",
            "metadata": {
                "client_ip": "192.168.1.100",
                "user_agent": "Integration-Test/1.0"
            }
        }
        return requests.post(
            f"{self.kyc_service_url}/sessions",
            json=payload,
            headers=self.headers
        )

    def _upload_identity_document(self, session_id: str) -> requests.Response:
        """Upload document d'identité valide"""
        # Simulation d'une image de carte d'identité
        fake_image_content = base64.b64encode(b"fake-id-card-image-content").decode()
        
        files = {
            'file': ('id_card.jpg', base64.b64decode(fake_image_content), 'image/jpeg')
        }
        data = {
            'document_type': 'ID_CARD_FRONT'
        }
        headers_without_content_type = {k: v for k, v in self.headers.items() if k != "Content-Type"}
        
        return requests.post(
            f"{self.kyc_service_url}/sessions/{session_id}/documents",
            files=files,
            data=data,
            headers=headers_without_content_type
        )

    def _upload_invalid_document(self, session_id: str) -> requests.Response:
        """Upload document invalide pour test d'échec"""
        files = {
            'file': ('blurry_document.jpg', b"very-blurry-unreadable-content", 'image/jpeg')
        }
        data = {
            'document_type': 'ID_CARD_FRONT'
        }
        headers_without_content_type = {k: v for k, v in self.headers.items() if k != "Content-Type"}
        
        return requests.post(
            f"{self.kyc_service_url}/sessions/{session_id}/documents",
            files=files,
            data=data,
            headers=headers_without_content_type
        )

    def _perform_face_verification(self, session_id: str, reference_image_id: str) -> requests.Response:
        """Effectuer vérification faciale"""
        # Image de visage correspondant au document
        live_image_b64 = base64.b64encode(b"matching-face-image-content").decode()
        
        payload = {
            "reference_image_id": reference_image_id,
            "live_image": f"data:image/jpeg;base64,{live_image_b64}",
            "verification_type": "SIMILARITY_CHECK"
        }
        
        return requests.post(
            f"{self.kyc_service_url}/sessions/{session_id}/face-verification",
            json=payload,
            headers=self.headers
        )

    def _perform_face_verification_with_different_person(
        self, session_id: str, reference_image_id: str
    ) -> requests.Response:
        """Vérification faciale avec personne différente"""
        # Image d'une personne différente
        different_face_b64 = base64.b64encode(b"different-person-face-content").decode()
        
        payload = {
            "reference_image_id": reference_image_id,
            "live_image": f"data:image/jpeg;base64,{different_face_b64}",
            "verification_type": "SIMILARITY_CHECK"
        }
        
        return requests.post(
            f"{self.kyc_service_url}/sessions/{session_id}/face-verification",
            json=payload,
            headers=self.headers
        )

    def _perform_liveness_detection(self, session_id: str) -> requests.Response:
        """Effectuer détection de vivacité"""
        # Simulation d'une vidéo de détection de vivacité
        video_b64 = base64.b64encode(b"live-person-video-content").decode()
        
        payload = {
            "video_data": f"data:video/mp4;base64,{video_b64}",
            "challenge_type": "BLINK_DETECTION",
            "duration_seconds": 3
        }
        
        return requests.post(
            f"{self.kyc_service_url}/sessions/{session_id}/liveness",
            json=payload,
            headers=self.headers
        )

    def _get_session_status(self, session_id: str) -> requests.Response:
        """Récupérer le statut de la session"""
        return requests.get(
            f"{self.kyc_service_url}/sessions/{session_id}",
            headers=self.headers
        )


if __name__ == '__main__':
    # Configuration pour les tests d'intégration
    unittest.main(verbosity=2)