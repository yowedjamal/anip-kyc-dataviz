# Quickstart: Plateforme KYC & Dashboard Analytique ANIP

**Generated**: 19 septembre 2025  
**For**: Validation des scénarios utilisateur et tests d'intégration  
**Architecture**: Microservices (KYC Service + Dashboard Service + Frontend Angular)

## Vue d'Ensemble

Ce guide quickstart valide les workflows critiques de la plateforme KYC ANIP :
1. **Workflow KYC complet** : De la capture document à la validation finale
2. **Consultation dashboard** : Accès aux analytics anonymisées
3. **Administration système** : Gestion utilisateurs et audit

**Objectif Performance** : Workflow KYC complet en moins de 5 secondes ⚡

## Prérequis Environment

### Services Requis
- ✅ **KYC Service** (Spring Boot) : http://localhost:8080
- ✅ **Dashboard Service** (Laravel) : http://localhost:8081
- ✅ **Frontend Angular** : http://localhost:4200
- ✅ **PostgreSQL + TimescaleDB** : localhost:5432
- ✅ **MinIO** : http://localhost:9000
- ✅ **Keycloak** : http://localhost:8090
- ✅ **ElasticSearch** : http://localhost:9200

### Données de Test
```bash
# Utilisateurs de test (créés dans Keycloak)
AGENT_EMAIL="agent.test@anip.gov.ml"
ANALYST_EMAIL="analyst.test@anip.gov.ml"
ADMIN_EMAIL="admin.test@anip.gov.ml"

# Documents de test (images sample dans /test-data/)
SAMPLE_NATIONAL_ID="test-data/documents/mali_national_id_sample.jpg"
SAMPLE_PASSPORT="test-data/documents/mali_passport_sample.jpg"
SAMPLE_SELFIE="test-data/selfies/citizen_selfie_sample.jpg"
SAMPLE_LIVENESS_VIDEO="test-data/videos/liveness_sample.mp4"
```

## Scénario 1: Workflow KYC Complet (Agent ANIP) 🎯

**Persona** : Amadou TRAORE, Agent KYC ANIP  
**Objectif** : Vérifier l'identité d'un citoyen en moins de 5 secondes  
**User Story** : En tant qu'agent ANIP, je veux vérifier l'identité d'un citoyen en capturant ses documents et sa photo pour obtenir une validation automatisée fiable.

### 1.1 Authentification Agent
```bash
# Étape 1: Connexion via Keycloak
curl -X POST "http://localhost:8090/realms/anip/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=anip-frontend" \
  -d "username=agent.test@anip.gov.ml" \
  -d "password=TestPassword123!" \
  -d "scope=openid profile email"

# Réponse attendue (< 500ms):
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
}

# ✅ Validation: Token JWT valide reçu
# ✅ Validation: Rôle AGENT_KYC dans le token
```

### 1.2 Initialisation Session KYC
```bash
# Étape 2: Créer nouvelle session KYC
export ACCESS_TOKEN="eyJhbGciOiJSUzI1NiIs..."

curl -X POST "http://localhost:8080/v1/sessions" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "citizen_reference": "CIT_2025_000123456",
    "metadata": {
      "agent_location": "BAMAKO_CENTRE",
      "session_type": "ENROLLMENT"
    }
  }'

# Réponse attendue (< 200ms):
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "INITIATED",
  "created_at": "2025-09-19T10:30:00Z",
  "expires_at": "2025-09-19T11:00:00Z"
}

export SESSION_ID="550e8400-e29b-41d4-a716-446655440000"

# ✅ Validation: Session créée avec UUID valide
# ✅ Validation: Statut INITIATED
# ✅ Validation: Expiration 30 minutes
```

### 1.3 Upload et OCR Document d'Identité
```bash
# Étape 3: Upload carte d'identité nationale malienne
START_TIME=$(date +%s%3N)

curl -X POST "http://localhost:8080/v1/sessions/$SESSION_ID/documents" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "document_image=@$SAMPLE_NATIONAL_ID" \
  -F "document_type=NATIONAL_ID" \
  -F "capture_metadata={\"capture_quality\":\"HIGH\",\"lighting_conditions\":\"GOOD\"}"

END_TIME=$(date +%s%3N)
PROCESSING_TIME=$((END_TIME - START_TIME))

# Réponse attendue (< 3000ms):
{
  "document_id": "doc_123e4567-e89b-12d3-a456-426614174000",
  "document_type": "NATIONAL_ID",
  "ocr_results": {
    "extracted_text": {
      "full_name": "TRAORE Amadou",
      "document_number": "ML123456789",
      "date_of_birth": "1990-05-15",
      "issue_date": "2020-01-10",
      "expiry_date": "2030-01-10",
      "issuing_authority": "MINISTERE DE L'ADMINISTRATION TERRITORIALE"
    },
    "confidence_score": 0.94,
    "language_detected": "fra"
  },
  "validation_status": "VALID",
  "processing_time_ms": 2450
}

export DOCUMENT_ID="doc_123e4567-e89b-12d3-a456-426614174000"

# ✅ Validation: OCR processing < 3 secondes
# ✅ Validation: Confidence score ≥ 0.70
# ✅ Validation: Texte français détecté correctement
# ✅ Validation: Champs obligatoires extraits
echo "OCR processing time: ${PROCESSING_TIME}ms (target: <3000ms)"
```

### 1.4 Vérification Faciale
```bash
# Étape 4: Comparaison selfie vs photo document
START_TIME=$(date +%s%3N)

curl -X POST "http://localhost:8080/v1/sessions/$SESSION_ID/face-verification" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "selfie_image=@$SAMPLE_SELFIE" \
  -F "document_id=$DOCUMENT_ID" \
  -F "verification_settings={\"similarity_threshold\":0.85}"

END_TIME=$(date +%s%3N)
FACE_PROCESSING_TIME=$((END_TIME - START_TIME))

# Réponse attendue (< 2000ms):
{
  "face_match_id": "face_789e4567-e89b-12d3-a456-426614174000",
  "similarity_score": 0.92,
  "match_status": "MATCH",
  "algorithm_used": "DEEPFACE_VGG",
  "face_detection_confidence": 0.98,
  "processing_time_ms": 1200
}

export FACE_MATCH_ID="face_789e4567-e89b-12d3-a456-426614174000"

# ✅ Validation: Face recognition < 2 secondes
# ✅ Validation: Similarity score ≥ 0.85 (auto-validation)
# ✅ Validation: Face detection confidence ≥ 0.90
echo "Face verification time: ${FACE_PROCESSING_TIME}ms (target: <2000ms)"
```

### 1.5 Détection de Vivacité (Liveness)
```bash
# Étape 5: Anti-spoofing liveness detection
START_TIME=$(date +%s%3N)

curl -X POST "http://localhost:8080/v1/sessions/$SESSION_ID/liveness" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "liveness_video=@$SAMPLE_LIVENESS_VIDEO" \
  -F "detection_method=COMBINED"

END_TIME=$(date +%s%3N)
LIVENESS_PROCESSING_TIME=$((END_TIME - START_TIME))

# Réponse attendue (< 1000ms):
{
  "liveness_id": "live_456e4567-e89b-12d3-a456-426614174000",
  "liveness_score": 0.87,
  "liveness_status": "LIVE",
  "detection_details": {
    "eye_blink_detected": true,
    "head_movement_detected": true,
    "texture_analysis_score": 0.82,
    "spoofing_indicators": []
  },
  "processing_time_ms": 950
}

# ✅ Validation: Liveness detection < 1 seconde
# ✅ Validation: Liveness score ≥ 0.80
# ✅ Validation: Blink ET mouvement détectés
# ✅ Validation: Aucun indicateur de spoofing
echo "Liveness detection time: ${LIVENESS_PROCESSING_TIME}ms (target: <1000ms)"
```

### 1.6 Validation Statut Session Final
```bash
# Étape 6: Vérifier statut final de la session
curl -X GET "http://localhost:8080/v1/sessions/$SESSION_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Réponse attendue:
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "citizen_reference": "CIT_2025_000123456",
  "status": "COMPLETED",
  "overall_confidence": 0.91,
  "documents": [{
    "document_id": "doc_123e4567-e89b-12d3-a456-426614174000",
    "document_type": "NATIONAL_ID",
    "validation_status": "VALID",
    "ocr_confidence": 0.94
  }],
  "face_verification": {
    "similarity_score": 0.92,
    "match_status": "MATCH"
  },
  "liveness_detection": {
    "liveness_score": 0.87,
    "liveness_status": "LIVE"
  },
  "processing_duration_ms": 4600,
  "created_at": "2025-09-19T10:30:00Z",
  "completed_at": "2025-09-19T10:30:04.6Z"
}

TOTAL_TIME=$((PROCESSING_TIME + FACE_PROCESSING_TIME + LIVENESS_PROCESSING_TIME))

# ✅ Validation: Statut COMPLETED
# ✅ Validation: Overall confidence ≥ 0.85
# ✅ Validation: Processing total < 5 secondes 🎯
echo "🎯 TOTAL KYC WORKFLOW TIME: ${TOTAL_TIME}ms (target: <5000ms)"

if [ $TOTAL_TIME -lt 5000 ]; then
  echo "✅ PERFORMANCE TARGET MET!"
else
  echo "❌ PERFORMANCE TARGET MISSED!"
fi
```

## Scénario 2: Consultation Dashboard Analytics (Analyste) 📊

**Persona** : Fatoumata KEITA, Analyste de données ANIP  
**Objectif** : Consulter statistiques anonymisées pour rapport mensuel  
**User Story** : En tant qu'analyste ANIP, je veux consulter des statistiques anonymisées sur les enrôlements pour prendre des décisions stratégiques et produire des rapports gouvernementaux.

### 2.1 Authentification Analyste
```bash
# Connexion avec rôle ANALYST_DATA
curl -X POST "http://localhost:8090/realms/anip/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=anip-frontend" \
  -d "username=analyst.test@anip.gov.ml" \
  -d "password=TestPassword123!" \
  -d "scope=openid profile email"

export ANALYST_TOKEN="eyJhbGciOiJSUzI1NiIs..."

# ✅ Validation: Token avec rôle ANALYST_DATA
```

### 2.2 Statistiques Générales
```bash
# Récupérer vue d'ensemble des 30 derniers jours
curl -X GET "http://localhost:8081/v1/analytics/overview?period=LAST_30_DAYS" \
  -H "Authorization: Bearer $ANALYST_TOKEN"

# Réponse attendue:
{
  "period": {
    "start_date": "2025-08-20",
    "end_date": "2025-09-19",
    "period_type": "LAST_30_DAYS"
  },
  "metrics": {
    "total_sessions": 15420,
    "successful_sessions": 13876,
    "success_rate": 89.98,
    "avg_processing_time": 4.2,
    "fraud_attempts": 127,
    "manual_reviews": 1417
  },
  "trends": {
    "daily_registrations": [
      {"date": "2025-09-19", "count": 523},
      {"date": "2025-09-18", "count": 487}
    ],
    "success_rate_trend": [
      {"date": "2025-09-19", "rate": 91.2},
      {"date": "2025-09-18", "rate": 88.7}
    ]
  },
  "updated_at": "2025-09-19T10:35:00Z"
}

# ✅ Validation: Données anonymisées (pas d'identifiants personnels)
# ✅ Validation: Métriques business pertinentes
# ✅ Validation: Tendances temporelles disponibles
```

### 2.3 Répartition Démographique Anonymisée
```bash
# Analyser répartition par tranches d'âge (k-anonymity)
curl -X GET "http://localhost:8081/v1/analytics/demographics?breakdown_by=AGE_GROUP&period=LAST_30_DAYS" \
  -H "Authorization: Bearer $ANALYST_TOKEN"

# Réponse attendue:
{
  "breakdown_type": "AGE_GROUP",
  "total_population": 15420,
  "demographics": [
    {"category": "18-25", "count": 3420, "percentage": 22.18, "success_rate": 91.2},
    {"category": "26-35", "count": 4890, "percentage": 31.72, "success_rate": 89.8},
    {"category": "36-50", "count": 4210, "percentage": 27.30, "success_rate": 88.5},
    {"category": "51+", "count": 2900, "percentage": 18.80, "success_rate": 87.1}
  ],
  "privacy_notice": "Données anonymisées selon standards k-anonymity (k≥5)"
}

# ✅ Validation: Tous les groupes ≥ 5 individus (k-anonymity)
# ✅ Validation: Pas de données personnelles identifiables
# ✅ Validation: Métriques utiles pour analyse business
```

### 2.4 Données Géographiques pour Cartes
```bash
# Données pour carte interactive (précision régionale uniquement)
curl -X GET "http://localhost:8081/v1/analytics/geographic?metric=REGISTRATIONS&period=LAST_30_DAYS" \
  -H "Authorization: Bearer $ANALYST_TOKEN"

# Réponse attendue:
{
  "metric_type": "REGISTRATIONS",
  "regions": [
    {
      "region_code": "ML-1",
      "region_name": "Kayes",
      "country_code": "MLI",
      "coordinates": {"latitude": 14.4478, "longitude": -11.4444},
      "metrics": {
        "total_registrations": 2340,
        "success_rate": 87.5,
        "fraud_rate": 1.2,
        "avg_processing_time": 4.1
      }
    },
    {
      "region_code": "ML-2",
      "region_name": "Koulikoro",
      "country_code": "MLI",
      "coordinates": {"latitude": 12.8622, "longitude": -7.5560},
      "metrics": {
        "total_registrations": 3890,
        "success_rate": 91.3,
        "fraud_rate": 0.8,
        "avg_processing_time": 3.9
      }
    }
  ],
  "privacy_boundaries": {
    "min_population_per_region": 100,
    "coordinate_noise_radius_km": 5
  }
}

# ✅ Validation: Précision régionale uniquement (pas de GPS exact)
# ✅ Validation: Population minimum 100 par région
# ✅ Validation: Coordonnées avec noise ±5km
```

### 2.5 Export Rapport Analytique
```bash
# Générer export CSV pour rapport mensuel
curl -X POST "http://localhost:8081/v1/reports/export" \
  -H "Authorization: Bearer $ANALYST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "report_type": "OVERVIEW",
    "format": "CSV",
    "period": {
      "start_date": "2025-08-01",
      "end_date": "2025-08-31"
    },
    "filters": {
      "regions": ["ML-1", "ML-2", "ML-3"]
    },
    "anonymization_level": "STANDARD"
  }'

# Réponse attendue:
{
  "export_id": "exp_789e4567-e89b-12d3-a456-426614174000",
  "status": "INITIATED",
  "estimated_completion": "2025-09-19T10:37:00Z",
  "download_url": null
}

export EXPORT_ID="exp_789e4567-e89b-12d3-a456-426614174000"

# Vérifier statut après 30 secondes
sleep 30
curl -X GET "http://localhost:8081/v1/reports/exports/$EXPORT_ID" \
  -H "Authorization: Bearer $ANALYST_TOKEN"

# ✅ Validation: Export CSV généré avec données anonymisées
# ✅ Validation: Format ouvert compatible analyse externe
```

## Scénario 3: Administration Système (Admin) ⚙️

**Persona** : Ibrahim SANGARE, Administrateur Système ANIP  
**Objectif** : Gérer utilisateurs et consulter audit trail  
**User Story** : En tant qu'administrateur système, je veux configurer les accès utilisateurs et consulter les logs d'audit pour assurer la sécurité et la conformité.

### 3.1 Authentification Admin
```bash
curl -X POST "http://localhost:8090/realms/anip/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=anip-frontend" \
  -d "username=admin.test@anip.gov.ml" \
  -d "password=TestPassword123!" \
  -d "scope=openid profile email"

export ADMIN_TOKEN="eyJhbGciOiJSUzI1NiIs..."

# ✅ Validation: Token avec rôle ADMIN_SYSTEM
```

### 3.2 Consultation Profil Utilisateur
```bash
curl -X GET "http://localhost:8081/v1/users/profile" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Réponse attendue:
{
  "user_id": "usr_123e4567-e89b-12d3-a456-426614174000",
  "email": "admin.test@anip.gov.ml",
  "first_name": "Ibrahim",
  "last_name": "SANGARE",
  "role": "ADMIN_SYSTEM",
  "department": "IT_SECURITY",
  "permissions": [
    "READ_ANALYTICS",
    "EXPORT_REPORTS", 
    "MANAGE_USERS",
    "VIEW_AUDIT_LOGS",
    "SYSTEM_ADMINISTRATION"
  ],
  "last_login": "2025-09-19T10:25:00Z",
  "preferences": {
    "language": "fr",
    "timezone": "Africa/Bamako",
    "dashboard_layout": "DETAILED"
  }
}

# ✅ Validation: Rôle admin avec permissions complètes
```

### 3.3 Audit Trail et Conformité
```bash
# Consulter événements d'audit des dernières 24h
curl -X GET "http://localhost:8081/v1/audit/events?start_date=2025-09-18&end_date=2025-09-19&limit=50" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Réponse attendue:
{
  "events": [
    {
      "event_id": "audit_123e4567-e89b-12d3-a456-426614174000",
      "timestamp": "2025-09-19T10:30:04Z",
      "event_type": "KYC_SESSION_COMPLETED",
      "user_id": "usr_agent_456e4567-e89b-12d3-a456-426614174000",
      "user_email": "agent.test@anip.gov.ml",
      "entity_type": "KYC_SESSION",
      "entity_id": "550e8400-e29b-41d4-a716-446655440000",
      "action": "UPDATE",
      "ip_address": "192.168.1.100",
      "user_agent": "Mozilla/5.0...",
      "details": {
        "session_status": "COMPLETED",
        "processing_time_ms": 4600,
        "overall_confidence": 0.91
      }
    }
  ],
  "pagination": {
    "total_count": 1247,
    "current_page": 1,
    "total_pages": 25,
    "has_next": true
  }
}

# ✅ Validation: Audit trail complet avec détails
# ✅ Validation: Traçabilité utilisateur et IP
# ✅ Validation: Conformité RGPD et audit
```

## Tests de Performance et Limites 🚀

### Test de Charge API
```bash
# Test concurrent de sessions KYC (simulation 10 agents simultanés)
for i in {1..10}; do
  (
    echo "Testing concurrent session $i"
    curl -X POST "http://localhost:8080/v1/sessions" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"citizen_reference\": \"CIT_2025_TEST_$i\"}" &
  )
done
wait

# ✅ Validation: 10 sessions simultanées < 1 seconde
# ✅ Validation: Pas d'erreur de rate limiting
# ✅ Validation: Base de données reste responsive
```

### Test Limite Taille Fichier
```bash
# Test avec image 5MB (limite maximale)
curl -X POST "http://localhost:8080/v1/sessions/$SESSION_ID/documents" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "document_image=@test-data/large_document_5mb.jpg" \
  -F "document_type=NATIONAL_ID"

# ✅ Validation: Accepte fichiers jusqu'à 5MB
# ✅ Validation: Rejette fichiers > 5MB avec erreur 413
```

## Critères de Succès ✅

### Performance Requirements
- [ ] **Workflow KYC complet < 5 secondes** 🎯 (Constitutional requirement)
- [ ] **OCR processing < 3 secondes** per document
- [ ] **Face verification < 2 secondes** per comparison  
- [ ] **Liveness detection < 1 seconde** per video
- [ ] **API response time < 200ms** (95th percentile)
- [ ] **Dashboard render < 2 secondes** for analytics

### Functional Requirements
- [ ] **OCR multilingue** français + langues locales
- [ ] **Reconnaissance faciale** similarity ≥ 0.85 for auto-validation
- [ ] **Liveness detection** anti-spoofing effective
- [ ] **Anonymisation complète** dashboard data (k-anonymity k≥5)
- [ ] **Export formats ouverts** CSV, XLSX, JSON, PDF
- [ ] **Authentification centralisée** via Keycloak OAuth2/OIDC

### Security & Compliance
- [ ] **Chiffrement AES-256** données sensibles at rest
- [ ] **HTTPS/TLS 1.3** toutes communications
- [ ] **Audit trail complet** tous événements utilisateur  
- [ ] **Conformité RGPD** anonymisation et rétention
- [ ] **Rate limiting** protection contre abus
- [ ] **Gestion rôles** granulaire (agents, analystes, admins)

### Integration & Reliability
- [ ] **High availability** services avec health checks
- [ ] **Scalabilité horizontale** microservices
- [ ] **Monitoring** métriques business + techniques
- [ ] **Error handling** graceful avec messages utilisateur
- [ ] **Data consistency** entre services KYC/Dashboard
- [ ] **Backup & recovery** procédures testées

## Troubleshooting Common Issues 🔧

### Performance Issues
```bash
# Si workflow KYC > 5 secondes, vérifier:
# 1. Resource utilization
curl http://localhost:8080/actuator/metrics/process.cpu.usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 2. Database connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# 3. OCR processing queue
curl http://localhost:8080/v1/health
```

### Authentication Issues
```bash
# Vérifier token Keycloak validity
curl -X POST "http://localhost:8090/realms/anip/protocol/openid-connect/token/introspect" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$ACCESS_TOKEN" \
  -d "client_id=anip-frontend"
```

### Data Quality Issues
```bash
# Vérifier qualité OCR pour debugging
curl -X GET "http://localhost:8080/v1/sessions/$SESSION_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq '.documents[0].ocr_confidence'

# Confidence < 0.70 = manual review required
# Confidence < 0.50 = document quality issue
```

## Next Steps After Quickstart ➡️

1. **Load Testing** : Stress test avec Apache JMeter (1000+ users concurrent)
2. **Security Audit** : Penetration testing et vulnerability scan
3. **Performance Tuning** : Optimize based on production metrics
4. **User Training** : Guide formation agents/analystes ANIP
5. **Production Deployment** : Kubernetes manifests + CI/CD pipeline

**Ready for Production** : Once all criteria ✅ validated successfully 🚀