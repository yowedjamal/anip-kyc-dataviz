# Implementation Plan: Plateforme KYC & Dashboard Analytique pour l'ANIP

**Branch**: `001-plateforme-kyc-dashboard` | **Date**: 19 septembre 2025 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-plateforme-kyc-dashboard/spec.md`

## Execution Flow (/plan command scope)
```
1. Load feature spec from Input path
   → Spec loaded: Complete KYC & Analytics platform for ANIP
2. Fill Technical Context (scan for NEEDS CLARIFICATION)
   → Detect Project Type: Web application (frontend+backend+dashboard)
   → Set Structure Decision: Option 2 (microservices architecture)
3. Fill the Constitution Check section based on constitution
4. Evaluate Constitution Check section
   → No violations detected, aligned with constitutional requirements
   → Update Progress Tracking: Initial Constitution Check
5. Execute Phase 0 → research.md
   → All technical choices defined in PRD, no NEEDS CLARIFICATION
6. Execute Phase 1 → contracts, data-model.md, quickstart.md, agent update
7. Re-evaluate Constitution Check section
   → Verify alignment with microservices architecture
   → Update Progress Tracking: Post-Design Constitution Check
8. Plan Phase 2 → Describe task generation approach
9. STOP - Ready for /tasks command
```

## Summary
Plateforme open source KYC & Dashboard Analytique pour l'ANIP remplaçant les solutions propriétaires. Architecture microservices avec Spring Boot (KYC), Laravel (Dashboard), Angular 17 (Frontend). Module KYC complet (OCR multilingue, reconnaissance faciale, liveness detection) + dashboard analytique avec données anonymisées. Performance <5 sec, sécurité AES-256, conformité RGPD.

## Technical Context
**Language/Version**: Java 17+ (Spring Boot), PHP 8.1+ (Laravel), TypeScript 4.9+ (Angular 17)  
**Primary Dependencies**: Spring Boot 3.1, Laravel 10, Angular 17, Material UI, OpenCV, Tesseract/Kraken, DeepFace/FaceNet, InsightFace  
**Storage**: PostgreSQL 15+ avec TimescaleDB, MinIO S3-compatible, Redis cache  
**Testing**: JUnit 5 (Java), PHPUnit (PHP), Jasmine/Karma (Angular), Testcontainers  
**Target Platform**: Linux server, Docker/Kubernetes, navigateurs modernes (Chrome 100+, Firefox 100+, Safari 15+)  
**Project Type**: Web application microservices (3 services principaux)  
**Performance Goals**: Traitement KYC complet <5 sec, API response <200ms p95, Dashboard render <2 sec  
**Constraints**: Chiffrement AES-256, anonymisation RGPD, Open source uniquement, scalabilité horizontale  
**Scale/Scope**: Support 10k+ utilisateurs concurrent, 100k+ sessions KYC/jour, retention 7 ans anonymisées  

## Constitution Check
*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ I. Open Source Souveraineté
- Spring Boot, Laravel, Angular : Open source validés
- PostgreSQL, TimescaleDB, MinIO : Open source
- Tesseract/Kraken OCR : Alternatives open source à Regula
- OpenCV + DeepFace/FaceNet : Computer vision open source
- Aucune dépendance propriétaire

### ✅ II. Sécurité par Design
- Chiffrement AES-256 côté serveur implémenté
- Authentification OAuth2/OpenID Connect via Keycloak
- Communications HTTPS/TLS obligatoires
- Stockage sécurisé avec MinIO chiffré

### ✅ III. Architecture Microservices (NON-NÉGOCIABLE)
- Service KYC : Spring Boot dédié OCR/Face/Liveness
- Service Dashboard : Laravel dédié analytics/API
- Frontend : Angular 17 séparé
- Déploiement Docker/Kubernetes indépendant

### ✅ IV. Performance et Scalabilité
- Objectif <5 sec KYC complet respecté dans design
- Load balancing microservices planifié
- Mise à l'échelle horizontale via Kubernetes

### ✅ V. Anonymisation et Conformité
- Pipeline anonymisation Dashboard service
- Séparation données opérationnelles (KYC) / analytiques (Dashboard)
- Politique rétention RGPD avec purge automatique

**Constitution Status**: ✅ PASS - Tous les principes respectés

## Project Structure

### Documentation (this feature)
```
specs/001-plateforme-kyc-dashboard/
├── plan.md              # This file (/plan command output)
├── research.md          # Phase 0 output (/plan command)
├── data-model.md        # Phase 1 output (/plan command)
├── quickstart.md        # Phase 1 output (/plan command)
├── contracts/           # Phase 1 output (/plan command)
│   ├── kyc-service/     # Spring Boot API contracts
│   ├── dashboard-service/ # Laravel API contracts
│   └── frontend/        # Angular service interfaces
└── tasks.md             # Phase 2 output (/tasks command - NOT created by /plan)
```

### Source Code (repository root)
```
# Microservices Architecture (Option 2 Extended)
kyc-service/             # Spring Boot microservice
├── src/main/java/
│   ├── models/          # JPA entities (Document, KycSession, etc.)
│   ├── services/        # Business logic (OCR, Face, Liveness)
│   ├── controllers/     # REST endpoints
│   ├── config/          # Security, DB config
│   └── utils/           # Encryption, validation
├── src/test/java/
│   ├── contract/        # API contract tests
│   ├── integration/     # Service integration tests
│   └── unit/            # Unit tests
└── pom.xml

dashboard-service/       # Laravel microservice
├── app/
│   ├── Models/          # Eloquent models (Analytics, Demographics)
│   ├── Services/        # Anonymization, Aggregation
│   ├── Http/Controllers/ # REST API controllers
│   └── Jobs/            # Background processing
├── tests/
│   ├── Feature/         # API feature tests
│   ├── Integration/     # Service integration tests
│   └── Unit/            # Unit tests
├── database/migrations/ # Database schema
└── composer.json

frontend/                # Angular 17 application
├── src/app/
│   ├── kyc/             # KYC feature module
│   │   ├── document-capture/
│   │   ├── face-verification/
│   │   └── session-status/
│   ├── dashboard/       # Analytics feature module
│   │   ├── analytics/
│   │   ├── maps/
│   │   └── charts/
│   ├── shared/          # Shared components
│   └── services/        # API services
├── src/tests/
└── package.json

docker/                  # Infrastructure
├── docker-compose.yml   # Orchestration complète
├── postgres/            # PostgreSQL + TimescaleDB
├── minio/               # S3-compatible storage
├── keycloak/            # Authentication
└── elasticsearch/       # Logging stack
```

**Structure Decision**: Option 2 Extended - Web application microservices avec séparation claire des responsabilités selon constitution

## Phase 0: Outline & Research
1. **Extract unknowns from Technical Context** above:
   - ✅ Toutes les technologies spécifiées dans PRD et constitution
   - ✅ Architecture microservices définie
   - ✅ Stack technique complet documenté

2. **Generate and dispatch research agents**:
   ```
   Task: "Research OCR libraries comparison Tesseract vs Kraken for multilingual processing"
   Task: "Research facial recognition OpenCV + DeepFace vs FaceNet performance benchmarks"
   Task: "Research liveness detection InsightFace implementation patterns"
   Task: "Research PostgreSQL TimescaleDB best practices for analytics time series"
   Task: "Research Keycloak OAuth2/OIDC integration patterns for microservices"
   Task: "Research data anonymization techniques for GDPR compliance"
   ```

3. **Consolidate findings** in `research.md` using format:
   - Decision: [technology/pattern chosen]
   - Rationale: [performance, compliance, maintenance reasons]
   - Alternatives considered: [other options evaluated]

**Output**: research.md with all technology choices justified

## Phase 1: Design & Contracts
*Prerequisites: research.md complete*

1. **Extract entities from feature spec** → `data-model.md`:
   - Document d'Identité: type, numéro, date_émission, données_ocr, statut_validation
   - Session KYC: id, timestamp, statut, résultats_ocr, score_facial, résultat_liveness
   - Utilisateur Système: id, rôle, permissions, dernière_connexion
   - Données Analytiques: métriques anonymisées, période, région, démographie
   - Événement d'Audit: timestamp, action, utilisateur_id, détails

2. **Generate API contracts** from functional requirements:
   - KYC Service: POST /documents, POST /verify-face, POST /liveness, GET /session/{id}
   - Dashboard Service: GET /analytics/stats, GET /demographics, GET /geographic, POST /export
   - Output OpenAPI 3.0 schemas to `/contracts/`

3. **Generate contract tests** from contracts:
   - Un fichier test par endpoint avec assertion schéma
   - Tests doivent échouer (pas d'implémentation)

4. **Extract test scenarios** from user stories:
   - Agent ANIP workflow KYC complet
   - Analyste consultation dashboard
   - Admin configuration utilisateurs

5. **Update agent file incrementally**:
   - Ajouter contexte architectural microservices
   - Technologies récentes : Spring Boot 3.1, Laravel 10, Angular 17
   - Conserver instructions manuelles existantes

**Output**: data-model.md, /contracts/*, failing tests, quickstart.md, .github/copilot-instructions.md

## Phase 2: Task Planning Approach
*This section describes what the /tasks command will do - DO NOT execute during /plan*

**Task Generation Strategy**:
- Charger `.specify/templates/tasks-template.md` comme base
- Générer tâches depuis docs Phase 1 (contracts, data model, quickstart)
- Chaque contract → contract test task [P]
- Chaque entité → model creation task [P] 
- Chaque user story → integration test task
- Tâches implémentation pour faire passer les tests

**Ordering Strategy**:
- Ordre TDD : Tests avant implémentation 
- Ordre dépendance : Models avant services avant endpoints avant UI
- Marquer [P] pour exécution parallèle (fichiers indépendants)
- Setup infrastructure avant tout
- Services backend avant frontend

**Estimated Output**: 90-100 tâches numérotées et ordonnées dans tasks.md
- Phase 3.1: Setup (12 tâches) - Docker, services, configuration
- Phase 3.2: Tests First (12 tâches) - Contract tests, integration tests
- Phase 3.3: Models (11 tâches) - Entités JPA, Eloquent, migrations
- Phase 3.4: Services (9 tâches) - Business logic KYC et Dashboard
- Phase 3.5: APIs (12 tâches) - Controllers et endpoints REST
- Phase 3.6: Frontend (14 tâches) - Composants Angular et services
- Phase 3.7: Integration (8 tâches) - DB, auth, monitoring
- Phase 3.8: Polish (22 tâches) - Tests unitaires, performance, docs

**IMPORTANT**: Cette phase est exécutée par la commande /tasks, PAS par /plan

## Phase 3+: Future Implementation
*Ces phases sont au-delà du scope de la commande /plan*

**Phase 3**: Exécution tâches (commande /tasks crée tasks.md)  
**Phase 4**: Implémentation (exécuter tasks.md selon principes constitutionnels)  
**Phase 5**: Validation (lancer tests, exécuter quickstart.md, validation performance)

## Complexity Tracking
*No constitutional violations identified - section left empty*

## Progress Tracking
*This checklist is updated during execution flow*

**Phase Status**:
- [x] Phase 0: Research complete (/plan command)
- [x] Phase 1: Design complete (/plan command)
- [ ] Phase 2: Task planning complete (/plan command - describe approach only)
- [ ] Phase 3: Tasks generated (/tasks command)
- [ ] Phase 4: Implementation complete
- [ ] Phase 5: Validation passed

**Gate Status**:
- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved
- [x] Complexity deviations documented (N/A)

**Artifacts Generated**:
- [x] research.md: Technology decisions and justifications
- [x] data-model.md: Complete entity model with relationships
- [x] contracts/kyc-service/openapi.yaml: KYC Service API contract
- [x] contracts/dashboard-service/openapi.yaml: Dashboard Service API contract  
- [x] quickstart.md: User scenarios and integration test validation
- [x] .github/copilot-instructions.md: Updated agent context

---
*Based on Constitution v1.0.0 - See `.specify/memory/constitution.md`*