ENCRYPTION & KEY MANAGEMENT (kyc-service)

But: ce document explique comment fonctionne l'encryption dans le service KYC, comment activer l'AES-GCM, comment générer une clé pour l'environnement Docker/dev, et une feuille de route pour utiliser HashiCorp Vault en production.

1) Modes d'encryption disponibles

- Dev (par défaut)
  - Classe : `com.anip.kyc.config.security.DevEncryptionService`
  - Active si `app.encryption.enabled=false` ou propriété absente.
  - Fonctionnement : Base64 encode/decode. C'est une transformation réversible, non sécurisée. Usage : développement local, tests automatisés.

- AES-GCM (production/dev sécurisé)
  - Classe : `com.anip.kyc.config.security.AesGcmEncryptionService`
  - Active si `app.encryption.enabled=true`.
  - Fonctionnement : AES-256 GCM, IV de 12 octets, tag 16 octets. Le ciphertext stocké contient : [4 bytes IV length][IV][ciphertext]. Les méthodes exposées sont : `encrypt`, `decrypt`, `encryptBytes`, `decryptBytes`.
  - Exige : `app.encryption.key` (Base64) qui représente exactement 32 octets (AES-256).

2) Comment activer AES pour Docker (exemple)

- Générer une clé AES-256 aléatoire et l'encoder en Base64.
  - PowerShell (Windows) :
    ```powershell
    # génère 32 octets aléatoires et les encode en Base64
    [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 } | ForEach-Object { [byte]$_ }))
    ```
  - Linux/macOS :
    ```bash
    openssl rand -out key.bin 32 && base64 key.bin
    ```

- Ajouter dans la configuration (ex. `application-docker.properties` ou via variables d'environnement) :
  ```properties
  app.encryption.enabled=true
  app.encryption.key=<CLE_BASE64_32_OCTETS>
  ```

  Ou via `docker-compose.yml` (variables d'environnement) :
  ```yaml
  services:
    kyc-service:
      environment:
        - SPRING_PROFILES_ACTIVE=docker
        - APP_ENCRYPTION_ENABLED=true
        - APP_ENCRYPTION_KEY=<CLE_BASE64_32_OCTETS>
  ```

- Redémarrer le service :
  ```powershell
  docker compose up --build
  ```

3) Sécurité et bonnes pratiques (production)

- NE JAMAIS COMMITTER LA CLÉ DANS LE REPO.
- Utiliser un service de gestion de secrets (ex: HashiCorp Vault, AWS KMS/Secrets Manager, Azure Key Vault).
- Préférer l'injection de la clé via variables d'environnement ou secrets Docker/Kubernetes, fournis par la solution de secrets.
- Mettre en place la rotation de la clé et versioning : stocker la version de la clé avec chaque chiffrement (si nécessaire).
- Assurer logging/monitoring des accès aux secrets et rotation automatisée.

4) Feuille de route pour intégrer HashiCorp Vault

- Objectif : utiliser Vault pour stocker et fournir `app.encryption.key` au démarrage et supporter la rotation.

Étapes proposées :

1. Déployer Vault (dev/test) ou utiliser une instance managée.
2. Créer un secret engine (KV v2) ; stocker la clé sous `kv/data/kyc-service/encryption-key` (Base64-encoded 32 bytes).
3. Configurer l'authentification (approle, Kubernetes auth, AWS IAM, etc.) selon votre environnement.
4. Au démarrage de l'application, récupérer la clé depuis Vault et la fournir à Spring (options) :
   - Option A (recommandée) : utiliser Spring Cloud Vault (dépendance) pour charger `app.encryption.key` automatiquement depuis Vault vers la configuration Spring.
   - Option B : écrire un petit bootstrap (before context refresh) qui contacte Vault, récupère la clé et l'injecte dans l'environnement Spring (PropertySource).
5. Gérer la rotation : Vault peut avoir des endpoints de rotation/renouvellement. Décidez si vous voulez que l'app recharge la clé à chaud (reloading) ou redémarrage orchestré.

5) Exemple rapide d'intégration avec Spring Cloud Vault

- Ajouter dépendances Maven :
  ```xml
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
  </dependency>
  ```
- Exemples de propriétés `application-vault.properties` :
  ```properties
  spring.cloud.vault.uri=https://vault.example.com:8200
  spring.cloud.vault.token=${VAULT_TOKEN}
  spring.cloud.vault.kv.enabled=true
  spring.cloud.vault.kv.backend=secret
  spring.cloud.vault.kv.default-context=kyc-service
  ```
  Ensuite stocker la clé dans `secret/kyc-service` avec la clé `encryption.key` (Base64).

- Spring Cloud Vault exposera la propriété comme `app.encryption.key` et vous pourrez activer `app.encryption.enabled=true`.

6) Tests et validation

- Test local : activer AES et vérifier un round-trip encrypt/decrypt dans les tests d'intégration.
- Teste la détection d'erreurs sur clé absente ou mauvaise longueur afin d'éviter des plantages inattendus en prod.

---

Si vous voulez, je peux :
- ajouter un script utilitaire pour générer la clé et injecter dans `.env` pour Docker compose;
- ajouter un exemple `docker-compose.override.yml` montrant comment monter un secret ou variable d'environnement pour la clé ;
- ajouter un petit bootstrap Spring Cloud Vault example si vous souhaitez que j'ajoute les dépendances et un exemple d'usage.

