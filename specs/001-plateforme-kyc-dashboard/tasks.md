# Tasks: Plateforme KYC & Dashboard Analytique pour l'ANIP

**Input**: Design documents from `/specs/001-plateforme-kyc-dashboard/`
**Prerequisites**: plan.md (required), research.md, data-model.md, contracts/, quickstart.md

## Execution Flow (main)
```
1. Load plan.md from feature directory
   → Implementation plan loaded: Microservices architecture Spring Boot + Laravel + Angular
   → Extract: Java 17+, PHP 8.1+, TypeScript 4.9+, PostgreSQL, TimescaleDB, MinIO, Keycloak
2. Load design documents:
   → data-model.md: 9 entities extracted (Document, KycSession, FaceMatch, etc.)
   → contracts/: KYC Service (6 endpoints) + Dashboard Service (8 endpoints)
   → research.md: Technology decisions validated (Tesseract, OpenCV, DeepFace, etc.)
   → quickstart.md: 3 user scenarios identified
3. Generate tasks by category:
   → Setup: microservices init, dependencies, Docker infrastructure
   → Tests: contract tests (14 endpoints), integration tests (3 scenarios)
   → Core: models (9 entities), services, controllers
   → Integration: DB, auth, monitoring, storage
   → Polish: unit tests, performance, documentation
4. Apply task rules:
   → Different files = [P] for parallel execution
   → Same file = sequential (no [P])
   → Tests before implementation (TDD mandatory)
5. Number tasks sequentially (T001-T098)
6. Generate dependency graph and parallel execution examples
7. Validate task completeness:
   → All 14 API endpoints have contract tests ✅
   → All 9 entities have model creation tasks ✅
   → All 3 user scenarios have integration tests ✅
8. Return: SUCCESS (98 tasks ready for execution)
```

## Format: `[ID] [P?] Description`
- **[P]**: Can run in parallel (different files, no dependencies)
- Include exact file paths in descriptions

## Path Conventions
```
# Microservices Architecture
kyc-service/             # Spring Boot microservice
dashboard-service/       # Laravel microservice  
frontend/                # Angular 17 application
docker/                  # Infrastructure orchestration
```

## Phase 3.1: Infrastructure Setup
- [ ] T001 Create microservices project structure (kyc-service/, dashboard-service/, frontend/, docker/)
- [ ] T002 [P] Initialize Spring Boot project with Maven in kyc-service/pom.xml
- [ ] T003 [P] Initialize Laravel project with Composer in dashboard-service/composer.json
- [ ] T004 [P] Initialize Angular 17 project with npm in frontend/package.json
- [ ] T005 [P] Configure Docker Compose orchestration in docker/docker-compose.yml
- [ ] T006 [P] Configure PostgreSQL + TimescaleDB in docker/postgres/
- [ ] T007 [P] Configure MinIO S3-compatible storage in docker/minio/
- [ ] T008 [P] Configure Keycloak authentication in docker/keycloak/
- [ ] T009 [P] Configure ElasticSearch + Kibana logging in docker/elasticsearch/
- [ ] T010 [P] Configure linting Checkstyle + SpotBugs in kyc-service/
- [ ] T011 [P] Configure linting PHPStan + PHP-CS-Fixer in dashboard-service/
- [ ] T012 [P] Configure linting ESLint + Prettier in frontend/

## Phase 3.2: Contract Tests First (TDD) ⚠️ MUST COMPLETE BEFORE 3.3
**CRITICAL: Ces tests DOIVENT être écrits et DOIVENT échouer avant TOUTE implémentation**

### KYC Service Contract Tests
- [ ] T013 [P] Contract test POST /sessions in kyc-service/src/test/java/contract/SessionCreationTest.java
- [ ] T014 [P] Contract test POST /sessions/{id}/documents in kyc-service/src/test/java/contract/DocumentUploadTest.java
- [ ] T015 [P] Contract test POST /sessions/{id}/face-verification in kyc-service/src/test/java/contract/FaceVerificationTest.java
- [ ] T016 [P] Contract test POST /sessions/{id}/liveness in kyc-service/src/test/java/contract/LivenessDetectionTest.java
- [ ] T017 [P] Contract test GET /sessions/{id} in kyc-service/src/test/java/contract/SessionStatusTest.java
- [ ] T018 [P] Contract test GET /health in kyc-service/src/test/java/contract/HealthCheckTest.java

### Dashboard Service Contract Tests
- [ ] T019 [P] Contract test GET /analytics/overview in dashboard-service/tests/Feature/AnalyticsOverviewTest.php
- [ ] T020 [P] Contract test GET /analytics/demographics in dashboard-service/tests/Feature/DemographicsTest.php
- [ ] T021 [P] Contract test GET /analytics/geographic in dashboard-service/tests/Feature/GeographicTest.php
- [ ] T022 [P] Contract test GET /analytics/trends in dashboard-service/tests/Feature/TrendsTest.php
- [ ] T023 [P] Contract test POST /reports/export in dashboard-service/tests/Feature/ReportExportTest.php
- [ ] T024 [P] Contract test GET /reports/exports/{id} in dashboard-service/tests/Feature/ExportStatusTest.php
- [ ] T025 [P] Contract test GET /users/profile in dashboard-service/tests/Feature/UserProfileTest.php
- [ ] T026 [P] Contract test GET /audit/events in dashboard-service/tests/Feature/AuditEventsTest.php

### Integration Scenario Tests
- [ ] T027 [P] Integration test Workflow KYC complet (Scénario 1) in tests/integration/kyc_workflow_complete_test.py
- [ ] T028 [P] Integration test Dashboard Analytics consultation (Scénario 2) in tests/integration/analytics_consultation_test.py
- [ ] T029 [P] Integration test Administration système (Scénario 3) in tests/integration/system_administration_test.py

## Phase 3.3: Database Models & Entities (SEULEMENT après échec des tests)

### KYC Service Models (JPA Entities)
- [ ] T030 [P] Document entity in kyc-service/src/main/java/com/anip/kyc/models/Document.java
- [ ] T031 [P] KycSession entity in kyc-service/src/main/java/com/anip/kyc/models/KycSession.java
- [ ] T032 [P] FaceMatch entity in kyc-service/src/main/java/com/anip/kyc/models/FaceMatch.java
- [ ] T033 [P] LivenessResult entity in kyc-service/src/main/java/com/anip/kyc/models/LivenessResult.java

### Dashboard Service Models (Eloquent)
- [ ] T034 [P] SystemUser model in dashboard-service/app/Models/SystemUser.php
- [ ] T035 [P] AuditEvent model in dashboard-service/app/Models/AuditEvent.php
- [ ] T036 [P] AnalyticsData model in dashboard-service/app/Models/AnalyticsData.php
- [ ] T037 [P] DemographicStat model in dashboard-service/app/Models/DemographicStat.php
- [ ] T038 [P] GeographicData model in dashboard-service/app/Models/GeographicData.php

### Database Migrations
- [ ] T039 [P] KYC operational schema migration in kyc-service/src/main/resources/db/migration/
- [ ] T040 [P] Analytics anonymous schema migration in dashboard-service/database/migrations/
- [ ] T041 [P] TimescaleDB time-series tables setup

## Phase 3.4: Core Business Services

### KYC Service Business Logic
- [ ] T042 [P] OCR multilingue service in kyc-service/src/main/java/com/anip/kyc/services/OcrService.java
- [ ] T043 [P] Face recognition service in kyc-service/src/main/java/com/anip/kyc/services/FaceRecognitionService.java
- [ ] T044 [P] Liveness detection service in kyc-service/src/main/java/com/anip/kyc/services/LivenessDetectionService.java
- [ ] T045 KYC orchestrator service in kyc-service/src/main/java/com/anip/kyc/services/KycOrchestrationService.java
- [ ] T046 [P] Encryption AES-256 service in kyc-service/src/main/java/com/anip/kyc/services/EncryptionService.java
- [ ] T047 [P] MinIO file storage service in kyc-service/src/main/java/com/anip/kyc/services/FileStorageService.java

### Dashboard Service Business Logic
- [ ] T048 [P] Data anonymization service in dashboard-service/app/Services/AnonymizationService.php
- [ ] T049 [P] Analytics aggregation service in dashboard-service/app/Services/AnalyticsAggregationService.php
- [ ] T050 [P] Report export service in dashboard-service/app/Services/ReportExportService.php
- [ ] T051 [P] Audit logging service in dashboard-service/app/Services/AuditLoggingService.php
- [ ] T052 ETL pipeline service in dashboard-service/app/Services/EtlPipelineService.php

## Phase 3.5: API Controllers & Endpoints

### KYC Service Controllers
- [ ] T053 Session controller (POST /sessions, GET /sessions/{id}) in kyc-service/src/main/java/com/anip/kyc/controllers/SessionController.java
- [ ] T054 Document controller (POST /sessions/{id}/documents) in kyc-service/src/main/java/com/anip/kyc/controllers/DocumentController.java
- [ ] T055 Face verification controller (POST /sessions/{id}/face-verification) in kyc-service/src/main/java/com/anip/kyc/controllers/FaceVerificationController.java
- [ ] T056 Liveness controller (POST /sessions/{id}/liveness) in kyc-service/src/main/java/com/anip/kyc/controllers/LivenessController.java
- [ ] T057 [P] Health controller (GET /health) in kyc-service/src/main/java/com/anip/kyc/controllers/HealthController.java

### Dashboard Service Controllers
- [ ] T058 Analytics controller (GET /analytics/*) in dashboard-service/app/Http/Controllers/AnalyticsController.php
- [ ] T059 Reports controller (POST /reports/export, GET /reports/exports/{id}) in dashboard-service/app/Http/Controllers/ReportsController.php
- [ ] T060 User controller (GET /users/profile) in dashboard-service/app/Http/Controllers/UserController.php
- [ ] T061 [P] Audit controller (GET /audit/events) in dashboard-service/app/Http/Controllers/AuditController.php

### Security & Middleware
- [ ] T062 JWT authentication filter in kyc-service/src/main/java/com/anip/kyc/security/JwtAuthenticationFilter.java
- [ ] T063 JWT middleware in dashboard-service/app/Http/Middleware/JwtAuthMiddleware.php
- [ ] T064 Rate limiting middleware in kyc-service/ and dashboard-service/
- [ ] T065 Request validation and sanitization across services
- [ ] T066 Error handling and logging middleware

## Phase 3.6: Frontend Angular Components

### KYC Feature Module
- [ ] T067 [P] Document capture component in frontend/src/app/kyc/document-capture/document-capture.component.ts
- [ ] T068 [P] Face verification component in frontend/src/app/kyc/face-verification/face-verification.component.ts
- [ ] T069 [P] Liveness detection component in frontend/src/app/kyc/liveness-detection/liveness-detection.component.ts
- [ ] T070 [P] Session status component in frontend/src/app/kyc/session-status/session-status.component.ts

### Dashboard Analytics Module
- [ ] T071 [P] Analytics overview component in frontend/src/app/dashboard/analytics-overview/analytics-overview.component.ts
- [ ] T072 [P] Interactive maps component (Leaflet) in frontend/src/app/dashboard/maps/interactive-maps.component.ts
- [ ] T073 [P] Dynamic charts component (ECharts) in frontend/src/app/dashboard/charts/dynamic-charts.component.ts
- [ ] T074 [P] Demographics component in frontend/src/app/dashboard/demographics/demographics.component.ts

### Shared Services & Components
- [ ] T075 [P] KYC API service in frontend/src/app/services/kyc-api.service.ts
- [ ] T076 [P] Dashboard API service in frontend/src/app/services/dashboard-api.service.ts
- [ ] T077 [P] Authentication service (Keycloak) in frontend/src/app/services/auth.service.ts
- [ ] T078 [P] File upload service in frontend/src/app/services/file-upload.service.ts
- [ ] T079 Angular routing with auth guards in frontend/src/app/app-routing.module.ts
- [ ] T080 Material UI integration and theming in frontend/src/app/shared/

## Phase 3.7: System Integration

### Database Integration
- [ ] T081 PostgreSQL connection configuration KYC service
- [ ] T082 PostgreSQL connection configuration Dashboard service
- [ ] T083 TimescaleDB time-series optimization setup
- [ ] T084 Database connection pooling and performance tuning

### Authentication & Authorization
- [ ] T085 Keycloak realm configuration for ANIP
- [ ] T086 OAuth2/OpenID Connect integration across services
- [ ] T087 Role-based access control (AGENT_KYC, ANALYST_DATA, ADMIN_SYSTEM)
- [ ] T088 Single Sign-On (SSO) between microservices

### Storage & Monitoring
- [ ] T089 MinIO bucket configuration for documents and videos
- [ ] T090 ElasticSearch log aggregation configuration
- [ ] T091 Kibana dashboards for monitoring and analytics
- [ ] T092 Prometheus metrics collection setup
- [ ] T093 Health checks and service discovery

## Phase 3.8: Polish & Production Readiness

### Unit Tests
- [ ] T094 [P] Unit tests for KYC services in kyc-service/src/test/java/unit/
- [ ] T095 [P] Unit tests for Dashboard services in dashboard-service/tests/Unit/
- [ ] T096 [P] Unit tests for Angular components in frontend/src/app/**/*.spec.ts

### Performance & Documentation
- [ ] T097 Performance optimization (KYC workflow < 5 seconds target)
- [ ] T098 API documentation generation (OpenAPI/Swagger) and deployment guide

## Dependencies
```
Setup (T001-T012) → Tests (T013-T029) → Models (T030-T041) → Services (T042-T052) 
→ Controllers (T053-T066) → Frontend (T067-T080) → Integration (T081-T093) → Polish (T094-T098)
```

**Critical Dependencies:**
- All contract tests (T013-T029) MUST fail before ANY implementation
- T045 (KYC Orchestrator) depends on T042-T044 (OCR, Face, Liveness services)
- T052 (ETL Pipeline) depends on T048-T049 (Anonymization, Aggregation)
- T053-T061 (Controllers) depend on T042-T052 (Services)
- T081-T088 (Integration) depends on T030-T066 (Models, Services, Controllers)

## Parallel Execution Examples

### Phase 3.2 - Contract Tests (All Parallel)
```bash
# Launch all contract tests simultaneously (different files):
Task: "Contract test POST /sessions in kyc-service/src/test/java/contract/SessionCreationTest.java"
Task: "Contract test GET /analytics/overview in dashboard-service/tests/Feature/AnalyticsOverviewTest.php"
Task: "Contract test POST /sessions/{id}/documents in kyc-service/src/test/java/contract/DocumentUploadTest.java"
Task: "Integration test Workflow KYC complet in tests/integration/kyc_workflow_complete_test.py"
```

### Phase 3.3 - Models (All Parallel)
```bash
# Launch all model creation simultaneously (different files):
Task: "Document entity in kyc-service/src/main/java/com/anip/kyc/models/Document.java"
Task: "SystemUser model in dashboard-service/app/Models/SystemUser.php"
Task: "KycSession entity in kyc-service/src/main/java/com/anip/kyc/models/KycSession.java"
Task: "AnalyticsData model in dashboard-service/app/Models/AnalyticsData.php"
```

### Phase 3.4 - Services (Mixed Parallel/Sequential)
```bash
# Launch independent services in parallel:
Task: "OCR multilingue service in kyc-service/src/main/java/com/anip/kyc/services/OcrService.java"
Task: "Data anonymization service in dashboard-service/app/Services/AnonymizationService.php"
Task: "Face recognition service in kyc-service/src/main/java/com/anip/kyc/services/FaceRecognitionService.java"

# Then sequentially: KYC Orchestrator (depends on OCR + Face + Liveness)
Task: "KYC orchestrator service in kyc-service/src/main/java/com/anip/kyc/services/KycOrchestrationService.java"
```

## Validation Checklist
*GATE: Checked before task execution*

### Contract Coverage
- [x] KYC Service: 6 endpoints → 6 contract tests (T013-T018)
- [x] Dashboard Service: 8 endpoints → 8 contract tests (T019-T026)
- [x] Integration scenarios: 3 scenarios → 3 integration tests (T027-T029)

### Entity Coverage
- [x] KYC entities: 4 entities → 4 model tasks (T030-T033)
- [x] Dashboard entities: 5 entities → 5 model tasks (T034-T038)
- [x] Database migrations: 3 schema groups → 3 migration tasks (T039-T041)

### Implementation Coverage
- [x] All contract tests come before implementation (T013-T029 before T030-T098)
- [x] All services implemented before controllers (T042-T052 before T053-T066)
- [x] All backend before frontend (T030-T066 before T067-T080)
- [x] All core before integration (T030-T080 before T081-T093)

### Parallel Task Safety
- [x] All [P] tasks operate on different files
- [x] No [P] task modifies same file as another [P] task
- [x] Sequential tasks respect dependency order

## Performance Targets
- **KYC Workflow Total**: < 5 seconds (T097 validates)
- **OCR Processing**: < 3 seconds per document
- **Face Verification**: < 2 seconds per comparison
- **Liveness Detection**: < 1 second per video
- **API Response Time**: < 200ms (95th percentile)

## Notes
- Tests MUST fail before implementing (TDD mandatory)
- Commit after completing each task
- Use constitutional guidelines for all security implementations
- Performance monitoring throughout development
- RGPD compliance validation at each stage
- Tâches [P] = fichiers différents, aucune dépendance
- Vérifier échec des tests avant implémentation
- Commit après chaque tâche terminée
- Performance <5sec pour workflow KYC complet
- Sécurité AES-256 pour données sensibles
- Anonymisation obligatoire pour analytics

## Validation Checklist
*GATE: Vérifié avant retour*

- [x] Tous les FR-001 à FR-018 couverts par les tâches
- [x] Architecture microservices respectée
- [x] Tests avant implémentation (TDD)
- [x] Tâches parallèles indépendantes
- [x] Chemins de fichiers exacts spécifiés
- [x] Aucune tâche [P] ne modifie le même fichier