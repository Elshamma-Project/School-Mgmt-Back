# 06 — Feuille de route et prompts pour Cursor / Claude Code

## 1. Où en est-on

| Étape | Contenu | État |
|---|---|---|
| 0 | Analyse des bulletins, domaine, modèle, règles de calcul, rôles | ✅ fait (ce dossier `docs/` + package `model`) |
| 1 | Projet : dépendances, configuration, Mongo, Docker, CI | à faire |
| 2 | Référentiels (CRUD) : année, séquences, niveaux, classes, matières, enseignants, élèves, inscriptions | à faire |
| 3 | Évaluations, notes, clôture, audit | à faire |
| 4 | Calculs dans le service (`GradeCalculationService`) + tests sur `docs/fixtures/` | à faire |
| 5 | Bulletins : génération, publication, PDF | à faire |
| 6 | Sécurité : authentification, rôles, appartenance | à faire |
| 7 | Modules suivants : emplois du temps, cartes, inscriptions, portail parents | à faire |

**Ordre conseillé** : 1 → 2 → 3 → 4 (avant 5) → 5 → 6 → 7. Faire les calculs (4) tôt : c'est lui qui permet la démo de rejeu de décembre. La sécurité (6) peut démarrer en parallèle dès l'étape 2, mais les contrôles d'appartenance ne s'écrivent utilement qu'avec les services de l'étape 3.

## 2. Règles à donner à l'assistant IA (déjà dans `AGENTS.md`)

- Ne **jamais** modifier le package `model` sans l'annoncer et mettre à jour `docs/03-modele-de-donnees.md`.
- Un `service` par agrégat ; les contrôleurs sont minces ; les DTO sont séparés des documents.
- Jamais de `copy()` sur un document ; `BigDecimal` avec `@Field(targetType = FieldType.DECIMAL128)` ; toute requête filtre par `schoolId`.
- Aucune donnée d'élève réelle dans le code, les tests, les logs ou Git.
- Écrire les tests avec les `fixtures`, Testcontainers (Mongo en replica set) et JUnit 5.
- Après chaque étape : compiler, lancer les tests, résumer les décisions prises et les questions ouvertes.

## 3. Prompts prêts à coller

Chaque prompt suppose que l'assistant a lu `AGENTS.md` (Claude Code lit `CLAUDE.md`, qui l'importe ; Cursor lit `AGENTS.md`).

### Étape 1 — Projet et infrastructure
```
Lis docs/03-modele-de-donnees.md (sections 3 et 4) puis prépare le socle du projet :
1. build.gradle.kts : ajoute les dépendances listées au § 4, Java 21, Kotlin, plugin.spring.
2. src/main/resources/application.yml avec profils dev et prod : MongoDB via variables d'environnement
   (URI, base), auto-index-creation=true seulement en dev, fuseau Africa/Douala, jackson en ISO-8601.
3. configuration/MongoConfig.kt : @EnableMongoAuditing + AuditorAware<String> (renvoie "system" tant que
   la sécurité n'existe pas), transactions Mongo (MongoTransactionManager), @EnableMongoRepositories.
4. exception/ : @RestControllerAdvice qui renvoie des ProblemDetail (RFC 9457), exceptions NotFound, Conflict,
   BusinessRule, Forbidden.
5. docker-compose.yml : Mongo 7 en replica set à un nœud (avec script d'init), l'API, et un volume de données.
   Dockerfile multi-étapes (build Gradle puis JRE 21), utilisateur non root.
6. .github/workflows/ci.yml : build + tests avec Testcontainers.
Ne touche pas au package model. Ne crée aucun service métier à ce stade.
```

### Étape 2 — Référentiels
```
Lis docs/02-domaine-metier.md (§ 3 et 4) et docs/03-modele-de-donnees.md (§ 5).
Implémente, pour chaque agrégat, le trio repository / service / controller / dto, avec validation (jakarta),
pagination et filtres : AcademicYear (la création génère les 6 EvaluationSession en PLANNED), Level (avec
chargement initial des niveaux du § 5.1), SchoolClass, Room, Subject, SubjectGroup, ClassSubject, Staff,
Student, Guardian, Enrollment, GradingScheme (avec chargement initial du § 5.4).
Règles à faire respecter dans les services : BR-03, BR-04, BR-06, BR-07, BR-40, BR-41, BR-42 ; schoolId
partout ; une seule année "current" par école. Ne pas gérer l'authentification : prends schoolId depuis un
composant SchoolContext injectable, à remplacer plus tard par celui du jeton.
Tests d'intégration avec Testcontainers pour les index uniques et les règles ci-dessus.
```

### Étape 3 — Évaluations, notes, clôture, audit
```
Lis docs/02-domaine-metier.md (BR-10 à BR-15, BR-20 à BR-24) et docs/05-roles-permissions.md (§ 3).
Implémente : EvaluationSessionService (ouvrir, clôturer, rouvrir ; transitions de l'état PLANNED/OPEN/CLOSED),
AssessmentService, GradeService (création, modification, suppression, saisie en lot pour une classe).
GradeService est l'UNIQUE chemin d'écriture des notes : il vérifie que l'élève a une inscription ENROLLED dans la
classe, que la note est entre 0 et maxScore avec 2 décimales, que la séquence est OPEN (sauf correction par
l'administration avec motif obligatoire), et écrit un GradeAuditEntry dans la même transaction.
L'identité de l'auteur et son rôle viennent d'un composant CurrentUser injectable (bouchon pour l'instant).
Tests : concurrence (deux saisies simultanées sur @Version), séquence close, note hors bornes, doublon,
correction après clôture avec trace.
```

### Étape 4 — Calculs dans le service
```
Lis docs/04-regles-de-calcul.md en entier (c'est la spécification). Implémente les calculs dans la couche service :
GradeCalculationService (package service) avec calculateClass(classId, period) qui charge ClassSubject, Grade,
Enrollment et GradingScheme, applique les étapes 0 à 9 du document, et retourne par élève : lignes, totaux de
groupe, moyenne de groupe, moyenne générale, rang, et pour la classe le profil (moyenne, % réussite, etc.).
Utilise BigDecimal et MathContext.DECIMAL128, les politiques d'arrondi HALF_UP et LEGACY_DOUBLE, les tranches
d'appréciation, MissingMarkPolicy, AbsencePolicy et RankingPolicy lues dans GradingScheme (rien en dur).
Garde l'arithmétique dans des méthodes privées/internal sans accès base, pour les tester sans Mongo.
Tests à partir de docs/fixtures/*.json : chaque valeur "printed" doit être retrouvée selon les tolérances du § 6 ;
les écarts listés dans "knownDeviations" sont documentés dans le test, pas masqués. Ajoute les cas limites du § 6.
Si une règle est ambiguë, ne l'invente pas : TODO(Q-xx) et signale-le.
```

### Étape 5 — Bulletins
```
Lis docs/01-analyse-bulletins.md, docs/02-domaine-metier.md (BR-30 à BR-36) et docs/04-regles-de-calcul.md.
Implémente ReportCardService : génération par classe et par période (DRAFT), figement des noms (matières,
enseignants, classe), révision et remplacement (SUPERSEDED), validation, publication, code de vérification
aléatoire non devinable (13 chiffres), verrouillage si la séquence n'est pas clôturée.
Génération du PDF A4 portrait fidèle aux zones Z1 à Z10 de l'analyse, en français ou en anglais selon
SchoolClass.reportLanguage, avec en-tête bilingue de School, photo facultative, code-barres/QR.
N'emploie ni la marque, ni le texte "Powered by" d'un tiers, ni le numéro USSD d'un tiers.
Endpoint public de vérification d'un bulletin par verificationCode qui ne renvoie que : valide / invalide,
classe, année et date de génération.
```

### Étape 6 — Sécurité
```
Lis docs/05-roles-permissions.md en entier. Implémente l'authentification (jetons d'accès + renouvellement),
UserAccount (activation, verrouillage, changement de mot de passe), Spring Security avec refus par défaut,
les rôles de l'énumération Role, et les contrôles d'appartenance du § 3 dans la couche service (pas seulement
dans les contrôleurs). Remplace SchoolContext, CurrentUser et AuditorAware par des versions basées sur le jeton.
Ajoute les tests de la matrice du § 2 (un test par ligne sensible) et les tests d'accès croisés
(un parent ne voit jamais l'enfant d'un autre ; un enseignant ne saisit jamais hors de ses ClassSubject).
```

### Étape 7 — Modules suivants (un prompt par module, plus tard)
```
Lis docs/02-domaine-metier.md § 6. Conçois d'abord le modèle (documents, index, règles) et mets à jour
docs/03-modele-de-donnees.md, puis attends ma validation avant d'écrire les services.
```

## 4. Définition de « terminé » pour chaque étape

1. Le projet compile et tous les tests passent (`./gradlew build`).
2. Les règles métier citées sont couvertes par un test.
3. Aucune modification du package `model` sans mise à jour du doc 03.
4. Les questions rencontrées sont ajoutées à `07-questions-ouvertes.md`.
5. Aucun secret, aucune donnée réelle d'élève dans le dépôt.
