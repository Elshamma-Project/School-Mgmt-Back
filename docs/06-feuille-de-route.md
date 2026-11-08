# 06 — Feuille de route et prompts pour Cursor / Claude Code

## 1. Où en est-on

| Étape | Contenu | État |
|---|---|---|
| 0 | Analyse (7 bulletins + emploi du temps), domaine, **modèle par modules**, règles de calcul, rôles, architecture modulaire | ✅ fait (`docs/` + package `model`) |
| 1 | Projet : dépendances, configuration, Mongo, Docker, CI, **test d'architecture des modules** | à faire |
| 2 | Référentiels (module `academic`, `staff`, `student`) | à faire |
| 3 | Fiches de notes, notes de séquence, clôture, audit, **fiche de report** (`grading`) | 🟡 services grading + report sheet faits ; export PDF GradeSheet multi-pages encore à peaufiner |
| 4 | Calculs dans les services (`reporting`) + tests sur `docs/fixtures/` | ✅ `GradeCalculationEngine` + `GradeCalculationService` + tests fixtures docs/04 §6 |
| 5 | Bulletins : gabarits, génération, publication, PDF une page A4 | ✅ templates seed, `ReportCardService`, PDF Z1–Z10 1 page FR/EN (annuel + TERM_COMPETENCY) |
| 6 | Emploi du temps (`timetable`) : grille, saisie, conflits, PDF une page A4 | à faire |
| 7 | Sécurité (`user`) : authentification, rôles, sessions (max 2), appartenance | 🟡 bases auth/roles/sessions faites ; appartenance métier module par module |
| 8 | Modules suivants : cartes, inscriptions, notifications, fichiers, import des élèves | à faire |

**Ordre conseillé** : 1 → 2 → 3 → 4 → 5 → 6 ; la sécurité (7) peut démarrer dès l'étape 2. Le calcul (4) tôt : il permet la démo de rejeu de décembre.

## 2. Règles à donner à l'assistant IA (déjà dans `AGENTS.md`)

- Respecter la **frontière des modules** (`08-architecture-modulaire.md` § 3) : pas de repository ni de model d'un autre module ; passer par `service/<module>/api` et les DTO ; événements via `OutboxEvent`.
- Ne jamais modifier `model` sans l'annoncer et mettre à jour `03-modele-de-donnees.md`.
- Les calculs sont dans les services ; le seuil et les politiques viennent de `GradingScheme` / `ReportTemplate`, jamais du code.
- Jamais de `copy()` sur un document ; `BigDecimal` en `DECIMAL128` ; toute requête filtre par `schoolId`.
- Bulletin et emploi du temps : **une seule page A4**.
- Aucune donnée d'élève réelle dans le code, les tests, les logs ou Git.
- Tout est paramétrable depuis l'interface dans les deux langues : exposer des CRUD pour ce qui est une donnée.
- Après chaque étape : compiler, tester, résumer décisions et questions ouvertes.

## 3. Prompts prêts à coller

Chaque prompt suppose que l'assistant a lu `AGENTS.md` (Claude Code lit `CLAUDE.md` qui l'importe ; Cursor lit `AGENTS.md`).

### Étape 1 — Projet et infrastructure
```
Lis docs/03-modele-de-donnees.md (§ 3 et 4) et docs/08-architecture-modulaire.md, puis prépare le socle :
1. build.gradle.kts : dépendances du § 4 de 03, Java 21, plugin.spring, ArchUnit en test.
2. application.yml (profils dev et prod) : Mongo par variables d'environnement, auto-index-creation=true en dev
   seulement, fuseau Africa/Douala, Jackson ISO-8601.
3. configuration/MongoConfig.kt : @EnableMongoAuditing, AuditorAware<String> (renvoie "system" tant que la
   sécurité n'existe pas), MongoTransactionManager.
4. exception/ : @RestControllerAdvice -> ProblemDetail (RFC 9457) ; exceptions NotFound, Conflict, BusinessRule, Forbidden.
5. shared/ : composant OutboxPublisher (lit shared_outbox_events PENDING, publie en interne, marque PUBLISHED).
6. docker-compose.yml : Mongo 7 en replica set à un nœud (script d'init), l'API, un volume ; Dockerfile multi-étapes, non root.
7. .github/workflows/ci.yml : build + tests Testcontainers.
8. Un test ArchUnit qui échoue si une classe de service.<A> importe repository.<B> ou model.<B> avec B différent de A
   (B = shared exclu).
Ne touche pas au package model. Aucun service métier à ce stade.
```

### Étape 2 — Référentiels (academic, staff, student)
```
Lis docs/02-domaine-metier.md (§ 4 et 5), docs/03-modele-de-donnees.md (§ 5) et docs/08-architecture-modulaire.md.
Pour chacun des modules academic, staff, student : repository / service / controller / dto dans les sous-dossiers
du module, avec validation jakarta, pagination, filtres, DTO. Dans service/<module>/api expose les interfaces
(ClassSubjectApi, StaffApi, EnrollmentApi...) que les autres modules pourront appeler.
academic : School, AcademicYear (la création génère les 6 EvaluationSession... via l'API du module grading : en attendant,
émets l'événement academic.year.created), Level (chargement initial § 5.1), SchoolClass, Room, Subject, SubjectGroup
(chargement § 5.2), ClassSubject, TermCompetency.
Règles : BR-01 à BR-10, BR-40 à BR-43, BR-50 à BR-53 ; schoolId partout (SchoolContext injectable, à remplacer par le jeton).
Chaque modification d'un ClassSubject, d'une classe, d'un enseignant, d'une inscription écrit un OutboxEvent (catalogue § 4 de 08).
Tout libellé est bilingue (LocalizedText). Tests Testcontainers : index uniques, règles, événements.
```

### Étape 3 — Fiches de notes, notes de séquence, clôture, audit (grading)
```
Lis docs/02 (BR-11 à BR-24), docs/04 (étape 0 et § 4) et docs/05 (§ 3).
Implémente dans le module grading : EvaluationSessionService (ouvrir, clôturer, rouvrir ; l'ouverture crée les GradeSheet
de chaque ClassSubject de l'année, via ClassSubjectApi), GradeSheetService, AssessmentService (colonnes note / bonus / malus),
GradeService (créer, modifier, supprimer, saisie en lot pour une classe) et SequenceMarkService.
GradeService est l'UNIQUE chemin d'écriture : il vérifie l'inscription ENROLLED (via EnrollmentApi), les bornes 0..maxScore
avec 2 décimales, la séquence OPEN (sauf correction administration avec motif), écrit un GradeAuditEntry, recalcule la
SequenceMark de l'élève selon TotalizationRule (formule par défaut de docs/04 étape 0, Q-25 ouverte : TODO(Q-25)) et émet
grading.mark.changed, le tout dans la même transaction (collections de grading seulement).
Une note non saisie est PENDING, jamais 0. Export de la fiche en PDF/Excel sur plusieurs pages (autorisé pour ce document).
Tests : concurrence @Version, séquence close, note hors bornes, doublon, bonus/malus, plafond à 20, correction après clôture.

Ajout fiche de report (design papier) : GradeReportSheet (ClassSubject × trimestre), génération depuis SequenceMark,
soumission enseignant, visa SURVEILLANT_GENERAL puis DIRECTOR (Principal, Q-38), rejet, régénération, PDF A4 paysage
(maxRowsPerPage paramétrable, 2e page OK), version vierge imprimable (Q-35..Q-37). BR-70..BR-78. Fixture
docs/fixtures/report-sheet-6eme-t1.json.
```

### Étape 4 — Calculs dans les services (reporting)
```
Lis docs/04-regles-de-calcul.md en entier (c'est la spécification). Implémente GradeCalculationService dans
service/reporting : calculateClass(classId, period) lit les SequenceMark via GradingApi, les ClassSubject via
ClassSubjectApi, les inscriptions via EnrollmentApi et le GradingScheme, applique les étapes 1 à 9 et retourne par élève :
lignes, groupes, moyenne générale, rang, statistiques par matière (si le gabarit les demande), et le profil de classe.
BigDecimal + MathContext.DECIMAL128 ; arrondi de trimestre (roundTermSubjectAverages) puis arrondi unique à l'affichage ;
RoundingPolicy HALF_UP et HALF_EVEN ; toutes les politiques et tous les seuils lus dans GradingScheme / ReportTemplate.
Garde l'arithmétique dans des méthodes privées ou internal sans accès base pour les tester sans Mongo.
Tests à partir de docs/fixtures/bulletin-*.json (tableau des critères d'acceptation § 6 de 04) : les valeurs "printed" des lignes,
et les écarts documentés dans "observations" doivent apparaître dans le test, pas être masqués. Si une règle est ambiguë :
TODO(Q-xx) et signale-le.
```

### Étape 5 — Bulletins
```
Lis docs/01 (§ 2 et 3), docs/02 (BR-30 à BR-39) et docs/04. Implémente ReportTemplateService (CRUD des gabarits,
chargement initial ANNUAL_STANDARD et TERM_COMPETENCY) et ReportCardService : génération par classe et par période
(DRAFT), figement des noms, révisions (SUPERSEDED), validation, publication, verificationCode à 13 chiffres aléatoire,
refus si la séquence n'est pas clôturée (sauf brouillon).
PDF : UNE SEULE PAGE A4 portrait, fidèle aux zones Z1 à Z10 et au gabarit choisi ; ajuste la taille de police entre
minFontSizePt et maxFontSizePt ; si ça ne tient pas, échoue avec une erreur claire (jamais de 2e page) ; test automatique :
le PDF généré a exactement 1 page. FR ou EN selon la langue demandée (par défaut celle de la classe) ; en-tête bilingue.
N'emploie ni la marque, ni "Powered by", ni le numéro USSD d'un tiers.
Endpoint public de vérification par verificationCode (valide/invalide, classe, année, date seulement).
```

### Étape 6 — Emploi du temps (timetable)
```
Lis docs/09-emploi-du-temps.md en entier et docs/08. Implémente le module timetable : TimeSlotGridService (chargement
initial depuis fixtures/timetable-grid.json), TimetableService (création, révisions, publication, duplication d'une année
à l'autre), TimetableEntryService avec contrôle de conflits (enseignant, salle, classe, pauses, periodCount, cours hors
grille), comptage des heures par rapport à ClassSubject.weeklyHours (avertissement), libellés copiés rafraîchis par
événements. Vues par classe, par enseignant, par salle. PDF : UNE SEULE PAGE A4 PAYSAGE par vue, FR ou EN ; test : 1 page.
Tests avec fixtures/timetable-*.json (conflits, double période, activité, case hors grille, enseignant dans plusieurs classes).
N'accède jamais aux repositories d'academic ou staff : passe par leurs API.
```

### Étape 7 — Sécurité (user)
```
Lis docs/05-roles-permissions.md en entier. Implémente l'authentification (jeton d'accès + renouvellement), UserAccount,
Spring Security avec refus par défaut, les rôles, et les contrôles d'appartenance du § 3 dans la couche service de chaque
module. Le jeton porte : compte, schoolId, rôles, profil (staffId / studentId / guardianId). Remplace SchoolContext,
CurrentUser et AuditorAware par les versions basées sur le jeton. Un parent voit tous ses enfants et seulement eux.
Tests : un test par ligne sensible de la matrice (§ 2) et accès croisés (parent / enfant d'un autre, enseignant hors
de ses ClassSubject, élève / bulletin trimestriel).
```

### Étape 8 — Modules suivants
```
Lis docs/02 § 7 et docs/08. Conçois d'abord le modèle (documents, index, règles, événements) du module demandé, mets à jour
docs/03 et docs/08, puis attends ma validation avant d'écrire les services.
```

## 4. Définition de « terminé » pour chaque étape

1. Le projet compile, tous les tests passent (`./gradlew build`), le test d'architecture passe.
2. Les règles citées (BR-xx) sont couvertes par un test.
3. Aucune modification de `model` sans mise à jour de `03-modele-de-donnees.md`.
4. Les questions rencontrées sont ajoutées à `07-questions-ouvertes.md`.
5. Aucun secret ni donnée réelle d'élève dans le dépôt.
6. Tout PDF de bulletin ou d'emploi du temps : exactement une page A4.
