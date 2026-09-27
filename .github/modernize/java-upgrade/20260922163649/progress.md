# Upgrade Progress: Institutions (20260922163649)

- **Started**: 2026-09-22
- **Plan Location**: `.github/modernize/java-upgrade/20260922163649/plan.md`
- **Total Steps**: 6

## Step Details

- **Step 1: Setup Environment**
  - **Status**: 🔘 Not Started
  - **Changes Made**:
    - Aucun changement pour l'instant
  - **Review Code Changes**:
    - Sufficiency: Pending
    - Necessity: Pending
      - Functional Behavior: Pending
      - Security Controls: Pending
  - **Verification**:
    - Command: `java -version && mvn -version`
    - JDK: Pending
    - Build tool: Maven (version à confirmer)
    - Result: Pending
    - Notes: Vérifier que le JDK cible est installé et que `JAVA_HOME` est correctement configuré.
  - **Deferred Work**: None
  - **Commit**: N/A - version control unavailable

- **Step 2: Setup Baseline**
  - **Status**: 🔘 Not Started
  - **Changes Made**:
    - Aucun changement pour l'instant
  - **Review Code Changes**:
    - Sufficiency: Pending
    - Necessity: Pending
      - Functional Behavior: Pending
      - Security Controls: Pending
  - **Verification**:
    - Command: `mvn clean compile && mvn test`
    - JDK: Pending
    - Build tool: Maven
    - Result: Pending
    - Notes: Documenter l'état initial (nombre de tests, échecs, warnings). Nécessite l'activation des tests (skipTests actuellement à true).
  - **Deferred Work**: None
  - **Commit**: N/A - version control unavailable

- **Step 3: Upgrade Java Runtime Target**
  - **Status**: 🔘 Not Started
  - **Changes Made**:
    - Aucun changement pour l'instant
  - **Review Code Changes**:
    - Sufficiency: Pending
    - Necessity: Pending
      - Functional Behavior: Pending
      - Security Controls: Pending
  - **Verification**:
    - Command: `mvn clean compile`
    - JDK: Pending (cible à définir : 11, 17 ou 21)
    - Build tool: Maven
    - Result: Pending
    - Notes: Mettre à jour `maven.compiler.source`, `target` et `release` dans `pom.xml`. Corriger les erreurs de compilation liées aux API dépréciées ou supprimées.
  - **Deferred Work**: None
  - **Commit**: N/A - version control unavailable

- **Step 4: Final Validation**
  - **Status**: 🔘 Not Started
  - **Changes Made**:
    - Aucun changement pour l'instant
  - **Review Code Changes**:
    - Sufficiency: Pending
    - Necessity: Pending
      - Functional Behavior: Pending
      - Security Controls: Pending
  - **Verification**:
    - Command: `mvn exec:java`
    - JDK: Pending
    - Build tool: Maven
    - Result: Pending
    - Notes: Tester manuellement les fonctionnalités critiques (connexion, création d'institution, création d'examen, téléchargement de fichier).
  - **Deferred Work**: None
  - **Commit**: N/A - version control unavailable

- **Step 5: CVE Validation & Fix**
  - **Status**: 🔘 Not Started
  - **Changes Made**:
    - Aucun changement pour l'instant
  - **Review Code Changes**:
    - Sufficiency: Pending
    - Necessity: Pending
      - Functional Behavior: Pending
      - Security Controls: Pending
  - **Verification**:
    - Command: `mvn org.owasp:dependency-check-maven:check && mvn versions:display-dependency-updates`
    - JDK: Pending
    - Build tool: Maven
    - Result: Pending
    - Notes: Identifier les dépendances obsolètes et vulnérables. Mettre à jour en priorité les CVE critiques et élevées.
  - **Deferred Work**: None
  - **Commit**: N/A - version control unavailable

- **Step 6: Final Validation**
  - **Status**: 🔘 Not Started
  - **Changes Made**:
    - Aucun changement pour l'instant
  - **Review Code Changes**:
    - Sufficiency: Pending
    - Necessity: Pending
      - Functional Behavior: Pending
      - Security Controls: Pending
  - **Verification**:
    - Command: `mvn clean verify`
    - JDK: Pending
    - Build tool: Maven
    - Result: Pending
    - Notes: Validation finale complète. Vérifier les logs, les fonctionnalités, et l'absence de régression.
  - **Deferred Work**: None
  - **Commit**: N/A - version control unavailable

---

## Notes

- ⚠️ **Version control unavailable** : Aucune branche ni commit ne peut être créé dans l'espace de travail actuel. Il est **fortement recommandé** d'initialiser Git avant de commencer :
  ```bash
  git init
  git add .
  git commit -m "Snapshot avant migration Java"