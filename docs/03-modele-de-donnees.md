# 03 — Modèle de données (MongoDB)

> **Source de vérité des champs : le code Kotlin** dans `src/main/kotlin/com/kobe/smels/model/<module>/`. Chaque classe est commentée (KDoc).
> Ce document explique les choix, les index et les données de référence.
> État : 8 sous-packages, **106 classes compilées** contre des annotations Spring simulées (pas d'accès à Maven Central dans l'environnement de rédaction). **À recompiler chez vous** avec `./gradlew compileKotlin`.
> Découpage en modules (futurs microservices) : `08-architecture-modulaire.md`.

## 1. Principes

| Principe | Application |
|---|---|
| **Un module = ses collections** | Chaque collection est préfixée par son module (`academic_classes`, `grading_grades`...). Aucune jointure ni `$lookup` entre modules ; références par identifiant |
| **Multi-établissement dès le début** | Tous les documents (sauf `academic_schools` et `user_accounts`) étendent `TenantDocument` et portent `schoolId`. Tout index composite commence par `schoolId` |
| **Référencer, sauf pour les photographies** | Seul `ReportCard` embarque tout (photographie figée). `TimetableEntry` embarque des libellés d'impression |
| **Données de l'année vs permanentes** | `Student`, `Staff`, `Subject` sont permanents ; classe, inscription, séquences, matières de classe, fiches, notes, bulletins, emplois du temps sont rattachés à une `academicYearId`. **Pas d'écrasement** : un élève de 6ème à seconde conserve une `Enrollment` + ses `Grade`/`SequenceMark`/`ReportCard` par année ; consultation via `GET /students/{id}/academic-record`, `GET /sequence-marks?studentId=`, `GET /report-cards?studentId=` |
| **Dénormalisation maîtrisée** | `Grade` et `SequenceMark` recopient `classId`, `classSubjectId`, `sessionId`, `academicYearId`, `enrollmentId` (immuables) |
| **Pas de suppression physique** | `active = false` ; une note ne se supprime que via `GradeAuditEntry` `DELETED` |
| **Audit** | `createdAt/By`, `updatedAt/By`, `version` hérités de `AuditableDocument` ; journal métier des notes : `grading_audit_entries` (append-only) |
| **Événements** | `shared_outbox_events` (même transaction que la modification) |
| **Identifiants** | `String` (ObjectId hexadécimal) |
| **Nombres décimaux** | `BigDecimal` en `Decimal128` : `@Field(targetType = FieldType.DECIMAL128)` sur **chaque** champ décimal |
| **Une page A4** | Contrainte de rendu (`PageFormat`, `ReportTemplate.minFontSizePt`) et non de stockage |

## 2. Catalogue des collections

| Module | Collection | Classe | Rôle |
|---|---|---|---|
| shared | `shared_outbox_events` | `OutboxEvent` | Événements à publier vers les autres modules |
| user | `user_accounts` | `UserAccount` | Comptes de connexion (auth, rôles, verrouillage) ; `username` = **e-mail** de login |
| user | `user_sessions` | `UserSession` | Sessions de connexion (max 2 actives / compte ; refresh hashé) |
| academic | `academic_schools` | `School` | Établissement, en-têtes officiels par cycle, textes du bulletin |
| academic | `academic_years` | `AcademicYear` (+ `Term`) | Années et trimestres |
| academic | `academic_levels` | `Level` | 6ème, Form 3, Tle... |
| academic | `academic_classes` | `SchoolClass` | Classes d'une année (`2NDE C`, `FORM 4 ARTS`), langues, série |
| academic | `academic_rooms` | `Room` | Salles |
| academic | `academic_subjects` | `Subject` | Catalogue bilingue ; `kind` ACADEMIC/ACTIVITY ; alias d'emploi du temps ; langue d'enseignement |
| academic | `academic_subject_groups` | `SubjectGroup` | Blocs de sous-total |
| academic | `academic_class_subjects` | `ClassSubject` | **Pivot** matière-classe : coefficient, groupe, enseignant, heures, langue |
| academic | `academic_term_competencies` | `TermCompetency` | Compétence visée par matière et trimestre (bulletin de trimestre) |
| student | `student_students` | `Student` (+ `GuardianLink`) | Élèves |
| student | `student_guardians` | `Guardian` | Parents / tuteurs |
| student | `student_enrollments` | `Enrollment` | Inscription d'un élève dans une classe pour une année |
| staff | `staff_members` | `Staff` | Enseignants et administration (civilité pour l'emploi du temps) |
| grading | `grading_sessions` | `EvaluationSession` | Les 6 séquences et leur clôture |
| grading | `grading_sheets` | `GradeSheet` | **Fiche de notes** d'un enseignant (matière de classe × séquence), règle de totalisation |
| grading | `grading_assessments` | `Assessment` | Colonnes d'une fiche : notes, **bonus**, **malus** |
| grading | `grading_grades` | `Grade` | Valeurs saisies (une par élève et par colonne) |
| grading | `grading_sequence_marks` | `SequenceMark` | **Note de séquence** totalisée (colonne DEVn du bulletin) |
| grading | `grading_report_sheets` | `GradeReportSheet` (+ lignes / cellules) | **Fiche de report** trimestrielle (instantané officiel, ≠ GradeSheet) |
| grading | `grading_audit_entries` | `GradeAuditEntry` | Journal append-only (notes, notes de séquence, fiches, report sheets) |
| grading | `grading_schemes` | `GradingScheme` (+ `AppreciationScale`, `ScoreBand`, `DistinctionRule`) | Règles de notation configurables (+ params fiche de report) |
| reporting | `reporting_templates` | `ReportTemplate` | Gabarits de bulletin (annuel, trimestre) |
| reporting | `reporting_report_cards` | `ReportCard` (+ blocs embarqués) | Bulletins générés, figés |
| reporting | `reporting_period_reviews` | `StudentPeriodReview` | Absences, retards, sanctions, distinctions, décision du conseil |
| timetable | `timetable_grids` | `TimeSlotGrid` (+ `GridPeriod`) | Grille horaire de la journée |
| timetable | `timetable_timetables` | `Timetable` | Emploi du temps d'une année (révisions) |
| timetable | `timetable_entries` | `TimetableEntry` | Cases de l'emploi du temps |

## 3. Index clés et requêtes chaudes

| Besoin | Requête | Index |
|---|---|---|
| Un enseignant liste ses classes et matières | `academic_class_subjects` par `teacherId` + année | `idx_teacher_year` |
| Fiches d'un enseignant pour une séquence | `grading_sheets` par `teacherId` + `sessionId` | `idx_teacher_session` |
| Une fiche par matière de classe et séquence | `grading_sheets` | `uk_classsubject_session` (unique) |
| Une fiche de report par matière et trimestre | `grading_report_sheets` | `uk_classsubject_term` (unique) |
| Doublon de note | `grading_grades` par `assessmentId` + `studentId` | `uk_assessment_student` (unique) |
| Note de séquence d'un élève dans une fiche | `grading_sequence_marks` | `uk_sheet_student` (unique) |
| Vue temps réel d'un élève ou d'un parent | `grading_sequence_marks` par `studentId` + année (+ séquence) | `idx_student_year_session` |
| Calcul du bulletin d'une classe | `grading_sequence_marks` par `classId` + `sessionId` | `idx_class_session` |
| Élèves d'une classe | `student_enrollments` par `classId` + statut | `idx_class_status` |
| Inscription d'un élève pour l'année | `student_enrollments` | `uk_student_year` (unique) |
| Enfants d'un parent | `student_students` par `guardians.guardianId` | `idx_school_guardian` |
| Matricule | `student_students` | `uk_school_matricule` (unique) |
| Dernier bulletin d'un élève pour une période | `reporting_report_cards` par `enrollmentId` + `period`, `revision` décroissant | `idx_enrollment_period` |
| Contrôle d'authenticité | `reporting_report_cards` par `verificationCode` | unique |
| Historique d'une note ou d'une note de séquence | `grading_audit_entries` | `idx_grade_time`, `idx_mark_time` |
| Corrections après clôture | `grading_audit_entries` | `idx_session_closed` |
| Cases d'une classe | `timetable_entries` | `uk_class_slot` (unique, `slotKey`) |
| Conflits enseignant / salle | `timetable_entries` | `idx_teacher_day`, `idx_room_day` |
| Heures hebdomadaires d'une matière | `timetable_entries` par `classSubjectId` | `idx_classsubject` |
| Événements à publier | `shared_outbox_events` par `status` + `occurredAt` | `idx_status_time` |

Création automatique des index : désactivée par défaut avec Spring Data MongoDB 3+ : `spring.data.mongodb.auto-index-creation=true` en développement ; script de migration en production.

## 4. Spécificités Spring Data MongoDB + Kotlin

1. **Jamais `copy()` sur un document persistant** : `version`, `createdAt`... sont dans la classe parente et ne sont pas recopiés. Modifier l'instance chargée puis `save()`.
2. **`@Version` et audit** : `@EnableMongoAuditing` + un bean `AuditorAware<String>`.
3. **`BigDecimal`** : sans `Decimal128`, Spring Data stocke une chaîne et les agrégations cessent de fonctionner. Annoter chaque champ décimal.
4. **Transactions multi-documents** (totalisation d'une fiche, clôture d'une séquence, génération de bulletins) : **replica set** obligatoire, même à un nœud. Une transaction ne couvre que les collections d'**un seul module** (règle d'architecture).
5. **Plugins Kotlin** : `kotlin("plugin.spring")`, `jackson-module-kotlin`.
6. **Objets embarqués** immuables (`val`) : reconstruire la liste pour modifier.
7. **Taille** : 16 Mo par document ; un `ReportCard` en fait quelques dizaines de Ko.
8. **Dates** : `Instant` (UTC) pour les horodatages, `LocalDate` pour les dates, `LocalTime` + `DayOfWeek` pour l'emploi du temps ; affichage `Africa/Douala`.
9. **DTO obligatoires** : ne jamais exposer un document (surtout `UserAccount.passwordHash`).

Dépendances Gradle minimales :
```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
implementation("org.jetbrains.kotlin:kotlin-reflect")
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.testcontainers:mongodb")
testImplementation("com.tngtech.archunit:archunit-junit5") // test d'architecture des modules
```

## 5. Données de référence à charger (seed)

### 5.1 Niveaux (`academic_levels`) — à confirmer avec l'école
| Cycle | Section FR | Section EN |
|---|---|---|
| MATERNELLE | `PS` · `MS` · `GS` | `NURSERY1` · `NURSERY2` · `NURSERY3` |
| PRIMAIRE | `SIL` · `CP` · `CE1` · `CE2` · `CM1` · `CM2` | `CLASS1` … `CLASS6` |
| SECONDAIRE_1ER_CYCLE | `6EME` · `5EME` · `4EME` · `3EME` | `FORM1` … `FORM5` |
| SECONDAIRE_2ND_CYCLE | `2NDE` · `1ERE` · `TLE` | `LOWER6` · `UPPER6` |

Observé : 6ème à 3ème, 2nde, 1ère ; Form 1 à 4 ; **Tle et Form 5 non vus**.

### 5.2 Groupes de matières (`academic_subject_groups`)
| Code | Français | Anglais |
|---|---|---|
| `LANG_LIT` | LANGUES ET LITTÉRATURES | LANGUAGE AND LITERATURE |
| `HUM_SOC` | SCIENCES HUMAINES ET SOCIALES *(à valider)* | HUMAN AND SOCIAL SCIENCES |
| `SCI_TECH` | SCIENCES ET TECHNOLOGIE | SCIENCE AND TECHNOLOGY |
| `DEV_PERS` | DÉVELOPPEMENT PERSONNEL | PERSONAL DEVELOPMENT *(à valider)* |
| `VOC` | ÉTUDES PROFESSIONNELLES *(à valider)* | VOCATIONAL STUDIES |
| `ARTS_NC` | ARTS ET CULTURE NATIONALE *(à valider)* | ARTS AND NATIONAL CULTURE |

Les traductions marquées sont des propositions : l'ancien logiciel imprimait un seul libellé par groupe (« TOTAL ARTS AND NATIONAL CULTURE » sur un bulletin français).

### 5.3 Séquences d'une année
| `number` | `shortLabel` | `termNumber` |
|---|---|---|
| 1, 2 | DEV1, DEV2 | 1 |
| 3, 4 | DEV3, DEV4 | 2 |
| 5, 6 | DEV5, DEV6 | 3 |

Créées avec l'année (statut `PLANNED`). Sur le bulletin de trimestre, les deux séquences s'appellent « Eval1 / Eval2 » (`ReportTemplate`).

### 5.4 Barème par défaut (`grading_schemes`)
`maxScore 20`, `passMark 10`, `classSuccessThreshold 12`, `decimals 2`, `roundingPolicy HALF_UP`, `roundTermSubjectAverages true`, `missingMarkPolicy IGNORE`, `absencePolicy EXCLUDE`, `rankingPolicy COMPETITION`, `rankingEligibility HAS_AVERAGE`, `annualAveragePolicy WEIGHTED_TOTAL`, `subjectYearAveragePolicy MEAN_OF_TERM_AVERAGES`, `honourRollMinAverage 12`, `honourRollBlockedBySanction true`, `effortSubjectThreshold 8` (hypothèse Q-28), `reportSheetDeadlineHours 72`, `reportSheetDeadlineBlocking false` (Q-37), `reportSheetMaxRowsPerPage 45` (Q-35), `reportSheetAppreciationScaleCode LETTER` (Q-36).

**Évolutions modèle (fiche de report)** : `EvaluationSession.examDate` ; `Student.sex` nullable ; `Role.SURVEILLANT_GENERAL` ; `GradeAuditEntry` cible `REPORT_SHEET` + `approvedBy`/`approvedAt` ; collection `grading_report_sheets`.

**Évolutions modèle (bulletins)** : `StudentSnapshot.sex` devient `Sex?` (aligné sur `Student.sex` nullable). Calculs et PDF dans `service/reporting` (`GradeCalculationService`, `ReportCardService`, `ReportCardPdfService`) ; gabarits seed `ANNUAL_STANDARD` / `TERM_COMPETENCY`.

| Échelle | Tranches |
|---|---|
| `LETTER` | C [0,12[ · B [12,14[ · B+ [14,16[ · A [16,20] |
| `COMPETENCY` | NA [0,10[ · ECA [10,14[ · A [14,…[ (A+ : seuil à définir, Q-27) |
| Mentions générales | voir `01-analyse-bulletins.md` § 6 |

### 5.5 Gabarits de bulletin (`reporting_templates`)
| Code | Période | Colonnes | Échelle | Options |
|---|---|---|---|---|
| `ANNUAL_STANDARD` | ANNUAL | DEV1..DEV6 | LETTER | groupes, rappels des 3 trimestres, profil de classe (seuil 12) |
| `TERM_COMPETENCY` | TERM | Eval1, Eval2 | COMPETENCY | compétence, rang / moyenne / nombre >= 10 par matière, total sur maximum, matières à améliorer, profil de classe (seuil 10) |

Quel gabarit pour quels niveaux : Q-26.

### 5.6 Grille horaire (`timetable_grids`)
Voir `fixtures/timetable-grid.json` : 8 périodes (7:35 → 15:10), 2 pauses, nettoyage général, fermeture ; lundi-vendredi.

## 6. Ce qui n'est volontairement pas dans le modèle

| Sujet | Traitement |
|---|---|
| Disponibilités des enseignants, contraintes de génération automatique de l'emploi du temps | module `timetable`, évolution (Q-32) |
| Cartes d'élèves, frais, notifications, stockage de fichiers | modules futurs ; le modèle ne contient que des références (`photoFileId`, `logoFileId`, `pdfFileId`) |
| Détail de chaque absence | compteurs seulement (`StudentPeriodReview`) |
| Authentification et autorisations | `UserAccount` + `UserSession` (max 2 sessions) ; voir `05-roles-permissions.md` |
| Import des élèves | plus tard ; création un par un en attendant |
