Projet : Plateforme KYC & Dashboard Analytique pour l’ANIP
1. Contexte & Objectifs

L’ANIP recherche une solution alternative open source aux solutions propriétaires comme Regula, capable de :

Fournir un module KYC fiable (OCR, reconnaissance faciale, comparaison de documents, liveness).

Mettre en place un dashboard analytique basé sur les données KYC anonymisées, pour dégager des tendances et produire des rapports décisionnels.

Notre solution doit être sécurisée, scalable et souveraine, en respectant les standards d’interopérabilité et en évitant le vendor lock-in.

2. Cibles Utilisateurs

Agents ANIP : valident les enrôlements et supervisent les dossiers.

Analystes / Décideurs : consultent le dashboard de données anonymisées.

Administrateurs Système : gèrent la plateforme, la sécurité et l’intégration avec d’autres systèmes étatiques.

3. Fonctionnalités Clés
3.1 Module KYC

📸 Capture document (pièce d’identité, passeport, carte d’électeur, etc.).

🔍 OCR multilingue (français + langues locales si possible).

🧑‍💻 Reconnaissance faciale :

Vérification d’identité (selfie vs document).

Détection de vivacité (liveness detection).

✅ Résultats KYC : statut (validé, rejeté, à vérifier).

🔒 Stockage chiffré des données sensibles.

3.2 Dashboard Analytique (anonymisé)

📊 Statistiques globales : nombre d’enrôlés, taux de réussite OCR, répartition par région/genre/âge.

🗺️ Carte interactive (données géographiques anonymisées).

📈 Graphiques dynamiques (tendances temporelles, taux de fraude, etc.).

📥 Export en formats ouverts (CSV, XLSX, JSON).

3.3 Sécurité & Conformité

🔐 Authentification & autorisation via Keycloak (OAuth2, OpenID Connect).

🧾 Conformité RGPD-like (anonymisation, rétention limitée).

📡 API REST/GraphQL sécurisée (HTTPS + JWT).

4. Architecture Technique
4.1 Frontend

Angular 17

UI moderne et modulaire (Material UI).

Intégration carte (Leaflet / Mapbox).

Dashboards avec Apache ECharts ou D3.js.

4.2 Backend

Spring Boot (Java) → Microservice KYC

Traitement OCR avec Tesseract ou Kraken.

Reconnaissance faciale avec OpenCV + DeepFace (ou FaceNet).

Liveness avec InsightFace.

Laravel (PHP) → Microservice Gestion/Dashboard

API REST pour les données anonymisées.

Orchestration des workflows KYC.

Exposition des données vers le frontend Angular.

4.3 Stockage & Données

PostgreSQL : base relationnelle sécurisée.

TimescaleDB (extension PostgreSQL) pour les séries temporelles (logs, stats).

MinIO : stockage S3-compatible pour images/documents.

4.4 Analytics

Apache Superset ou Metabase (open source) → visualisations avancées.

Export vers Angular pour intégration front.

4.5 Sécurité

Keycloak pour IAM (Identity & Access Management).

Chiffrement AES-256 côté serveur.

Gestion des logs avec ElasticSearch + Kibana.

5. Architecture Système (vue d’ensemble)
Utilisateur → Angular (Frontend) 
             ↕ (API HTTPS)
    [Laravel - API/Dashboard] 
             ↔ [Spring Boot - Service KYC] 
             ↔ [PostgreSQL + TimescaleDB]
             ↔ [MinIO - stockage]
             ↔ [Keycloak - Auth]
             ↔ [Superset/Metabase - Analytics]

6. Contraintes & Non-Fonctionnels

⏱️ Performance : traitement OCR + face match < 5 sec.

📦 Scalabilité : microservices déployables sur Kubernetes/Docker.

🌍 Open source uniquement (pas de dépendance à Regula).

🛠️ Intégration future avec registres nationaux.

7. Roadmap

Phase 1 : MVP (OCR + Reconnaissance faciale + Liveness).

Phase 2 : Intégration Dashboard analytique.

Phase 3 : Anonymisation avancée & interopérabilité (API externe).

Phase 4 : Optimisation UX et montée en charge.