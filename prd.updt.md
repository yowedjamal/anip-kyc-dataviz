📄 Product Requirements Document (PRD) – Mise à jour
Projet : Plateforme KYC & Dashboard Analytique pour l’ANIP
1. Contexte & Objectifs

(inchangé, mais avec précision)
La solution doit combiner automatisation par IA et supervision humaine afin de garantir un haut niveau de fiabilité et d’acceptabilité réglementaire.

2. Fonctionnalités Clés
2.1 Module KYC (Phase 1 – Automatique)

📸 Capture document et extraction OCR.

🧑‍💻 Reconnaissance faciale & liveness.

✅ Pré-analyse automatique avec statut provisoire :

Validé automatiquement (si confiance > seuil).

À vérifier manuellement (si doute).

Rejeté (si fraude évidente).

2.2 Validation Humaine (Phase 2 – Manuelle)

👩‍💼 Interface agent (dans Angular) affichant :

Données extraites (texte OCR, photo doc, selfie).

Résultat automatique du moteur KYC.

Score de confiance (fraude, qualité doc, matching visage).

🖊️ Actions possibles :

Confirmer la validation.

Rejeter et préciser la cause.

Marquer "en attente" pour vérification ultérieure.

🧾 Traçabilité :

Chaque décision est loggée (qui, quand, pourquoi).

Historique consultable par administrateur.

2.3 Dashboard Analytique (Anonymisé)

(inchangé, mais basé uniquement sur les données validées par un agent)

Les statistiques et rapports ne prennent en compte que les KYC validés humainement.

Vue comparative : données "auto-validées" vs "corrigées par humain".

3. Architecture Technique – Ajustements
3.1 Flux KYC
Utilisateur → Front Angular (soumission docs)
   ↕
[Service KYC - Spring Boot]
   ↕
Résultat automatique (OCR, Face Match, Liveness)
   ↕
[Base PostgreSQL/MinIO]
   ↕
Agent humain (Angular Admin UI)
   ↕
Validation/Rejet (via Laravel API)
   ↕
Enregistrement final (DB + Audit logs)

3.2 Composants supplémentaires

File d’attente (Kafka/RabbitMQ) → pour gérer les dossiers en attente de validation humaine.

Audit Trail : chaque décision humaine est stockée dans PostgreSQL avec horodatage.

UI Angular dédiée pour agents ANIP avec workflow simple (file de validation, boutons confirmer/rejeter).

4. Contraintes & Non-Fonctionnels (ajout)

⚖️ Supervision obligatoire : aucun KYC n’est considéré comme définitif sans validation humaine.

🕒 Délai max de validation configurable (ex. 24h).

🔍 Auditabilité : toutes les étapes (IA + humain) doivent être retraçables.

5. Roadmap – Mise à jour

Phase 1 – MVP : capture document + OCR + face match + liveness + résultat auto.

Phase 2 – Validation humaine : interface agent, workflow de décision, audit logs.

Phase 3 – Dashboard analytique : statistiques sur données validées.

Phase 4 – Optimisation (anonymisation, interopérabilité, montée en charge).