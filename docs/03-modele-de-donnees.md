# 03 — Modèle de données (MongoDB)

> **Source de vérité des champs : le code Kotlin** dans `src/main/kotlin/com/kobe/smels/model/`. Chaque classe est commentée (KDoc).
> Ce document explique les choix, les index et les données de référence ; il ne recopie pas chaque champ.
> État : modèle compilé contre des annotations Spring simulées (sandbox sans accès à Maven Central). **À recompiler chez vous** avec `./gradlew compileKotlin`.

## 1. Principes

| Principe | Application |
|---|---|
| **Multi-tenant dès le début** | Tous les documents (sauf `schools` et `user_accounts`) étendent `TenantDocument` et portent `schoolId`. Tout index composite commence par `schoolId`. Toute requête filtre sur `schoolId`. |
| **Référencer, sauf pour les photographies** | Les entités vivantes (élève, classe, note) se référencent par id. Seul `ReportCard` embarque tout (c'est une photographie figée). Les objets sans vie propre (`Term`, `GuardianLink`, `ScoreBand`) sont embarqués. |
| **Données de l'année vs données permanentes** | `Student` et `Staff` sont permanents. Classe, inscription, séquences, matières de classe, notes et bulletins appartiennent à une `academicYearId`. |
| **Dénormalisation maîtrisée** | `Grade` recopie `classId`, `classSubjectId`, `sessionId`, `academicYearId`, `enrollmentId` ; `SchoolClass` recopie `levelCode`, `section`, `cycle`. Ces copies ne changent jamais après création. |
| **Pas de suppression physique** | `active = false` pour élèves, enseignants, matières, classes. Les notes ne se suppriment que via un `GradeAuditEntry` `DELETED`. |
| **Audit technique** | `createdAt/By`, `updatedAt/By`, `version` hérités de `AuditableDocument`. Le journal métier des notes est `grade_audit_entries`. |
| **Identifiants** | `String` (ObjectId hexadécimal de Mongo) pour tous les `id` et toutes les références. |
| **Montants et notes** | `BigDecimal` stocké en `Decimal128` via `@Field(targetType = FieldType.DECIMAL128)` sur chaque champ décimal. |

## 2. Catalogue des collections

| Collection | Classe Kotlin | Package | Rôle |
|---|---|---|---|
| `schools` | `School` | `school` | L'établissement, en-têtes officiels, textes du bulletin |
| `academic_years` | `AcademicYear` (+ `Term`) | `school` | Années scolaires et leurs 3 trimestres |
| `levels` | `Level` | `school` | Niveaux (6ème, Form 3, Tle, CM2...) : données de référence |
| `classes` | `SchoolClass` | `school` | Classes d'une année |
| `rooms` | `Room` | `school` | Salles |
| `subjects` | `Subject` | `curriculum` | Catalogue des matières (bilingue) |
| `subject_groups` | `SubjectGroup` | `curriculum` | Blocs de sous-total |
| `class_subjects` | `ClassSubject` | `curriculum` | **Pivot** : matière dans une classe (coefficient, groupe, enseignant) |
| `staff` | `Staff` | `people` | Enseignants et administration |
| `students` | `Student` (+ `GuardianLink`) | `people` | Élèves |
| `guardians` | `Guardian` | `people` | Parents / tuteurs |
| `enrollments` | `Enrollment` | `people` | Inscription d'un élève dans une classe pour une année |
| `evaluation_sessions` | `EvaluationSession` | `assessment` | Les 6 séquences (DEV1..DEV6) et leur clôture |
| `assessments` | `Assessment` | `assessment` | Évaluations d'une matière pendant une séquence |
| `grades` | `Grade` | `assessment` | Notes (une par élève et par évaluation) |
| `grade_audit_entries` | `GradeAuditEntry` | `assessment` | Journal append-only des changements de notes |
| `grading_schemes` | `GradingScheme` (+ `ScoreBand`, `DistinctionRule`) | `reporting` | Règles de notation configurables |
| `student_period_reviews` | `StudentPeriodReview` | `reporting` | Absences, retards, sanctions, distinctions, décision du conseil |
| `report_cards` | `ReportCard` (+ blocs embarqués) | `reporting` | Bulletins générés (photographies figées) |
| `user_accounts` | `UserAccount` | `security` | **Squelette** des comptes de connexion (non implémenté) |

## 3. Index clés et requêtes chaudes

| Besoin | Requête | Index qui la sert |
|---|---|---|
| Un enseignant liste ses classes et matières | `class_subjects` par `teacherId` + année | `idx_teacher_year` |
| Saisie des notes d'une matière pour une séquence | `grades` par `classSubjectId` + `sessionId` | `idx_classsubject_session` |
| Éviter un doublon de note | `grades` par `assessmentId` + `studentId` | `uk_assessment_student` (unique) |
| Vue en temps réel d'un élève ou d'un parent | `grades` par `studentId` + année (+ séquence) | `idx_student_year_session` |
| Calcul du bulletin d'une classe | `grades` par `classId` + `sessionId` | `idx_class_session` |
| Élèves d'une classe | `enrollments` par `classId` + `status` | `idx_class_status` |
| Retrouver l'inscription d'un élève pour l'année | `enrollments` par `studentId` + année | `uk_student_year` (unique) |
| Enfants d'un parent | `students` par `guardians.guardianId` | `idx_school_guardian` |
| Recherche par matricule | `students` par `matricule` | `uk_school_matricule` (unique) |
| Dernier bulletin d'un élève pour une période | `report_cards` par `enrollmentId` + `period` trié par `revision` décroissant | `idx_enrollment_period` |
| Contrôle d'authenticité d'un bulletin | `report_cards` par `verificationCode` | index unique sur `verificationCode` |
| Historique d'une note | `grade_audit_entries` par `gradeId` | `idx_grade_time` |
| Corrections après clôture | `grade_audit_entries` par `sessionId` + `afterSessionClosed` | `idx_session_closed` |

Les index sont déclarés par annotations (`@CompoundIndexes`). Leur **création automatique est désactivée par défaut** avec Spring Data MongoDB 3+/Spring Boot 3 : activer `spring.data.mongodb.auto-index-creation=true` en dev, et créer les index par un script de migration en production.

## 4. Spécificités Spring Data MongoDB + Kotlin à respecter

1. **Ne jamais utiliser `copy()` sur un document persistant.** `version`, `createdAt`, etc. sont dans la classe parente et ne sont pas recopiés : l'objet serait pris pour un nouveau document. Modifier l'instance chargée puis `save()`.
2. **`@Version` et l'audit** exigent : `@EnableMongoAuditing` et un bean `AuditorAware<String>` qui retourne l'id de l'utilisateur courant (les champs `createdBy/updatedBy`).
3. **`BigDecimal`** : sans `Decimal128`, Spring Data stocke les `BigDecimal` en chaîne de caractères et les agrégations (`$avg`, `$sum`) cessent de fonctionner. C'est pourquoi chaque champ décimal est annoté `@Field(targetType = FieldType.DECIMAL128)`. Ne pas oublier cette annotation sur tout nouveau champ décimal. (Il existe aussi une configuration globale ; l'annotation par champ est plus sûre.)
4. **Transactions** : les multi-documents (générer des bulletins, clôturer une séquence) exigent un **replica set**, même avec un seul nœud. Le prévoir dans le `docker-compose` dès le début.
5. **Plugins Kotlin** : `kotlin("plugin.spring")` (classes ouvertes pour `@Service`/`@Configuration`) et `jackson-module-kotlin` pour les DTO. Les documents sont des `data class` à valeurs par défaut : Spring Data utilise le constructeur Kotlin, pas besoin de plugin `no-arg`.
6. **Objets embarqués** (`val`) : immuables ; pour modifier, reconstruire la liste ou l'objet complet.
7. **Taille** : un document Mongo fait au plus 16 Mo ; un `ReportCard` en fait quelques dizaines de Ko.
8. **Dates** : `Instant` (UTC) pour les horodatages, `LocalDate` pour les dates de calendrier. Fuseau d'affichage : `Africa/Douala`.
9. **Ne jamais exposer un document directement dans l'API** : passer par des DTO (`dto/`), surtout pour `UserAccount.passwordHash` et les données d'élèves.

Dépendances Gradle minimales à ajouter à `build.gradle.kts` :
```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
implementation("org.jetbrains.kotlin:kotlin-reflect")
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.testcontainers:mongodb")
```

## 5. Données de référence à charger (seed)

### 5.1 Niveaux (`levels`)
Liste de départ, **à confirmer avec l'école** (équivalences indicatives) :

| Cycle | Section FR : code · nom | Section EN : code · nom | `equivalenceIndex` |
|---|---|---|---|
| MATERNELLE | `PS` Petite section · `MS` Moyenne section · `GS` Grande section | `NURSERY1` · `NURSERY2` · `NURSERY3` | 1 · 2 · 3 |
| PRIMAIRE | `SIL` · `CP` · `CE1` · `CE2` · `CM1` · `CM2` | `CLASS1` … `CLASS6` | 1 … 6 |
| SECONDAIRE_1ER_CYCLE | `6EME` · `5EME` · `4EME` · `3EME` | `FORM1` · `FORM2` · `FORM3` · `FORM4` · `FORM5` | 1 · 2 · 3 · 4 (· 5) |
| SECONDAIRE_2ND_CYCLE | `2NDE` · `1ERE` · `TLE` | `LOWER6` (Lower Sixth) · `UPPER6` (Upper Sixth) | 5 · 6 · 7 (EN : 6 · 7) |

À valider : où classer Form 5 et 2nde (cycles différents selon les sous-systèmes), et la correspondance maternelle.

### 5.2 Groupes de matières (`subject_groups`) observés
| Code | Français | Anglais |
|---|---|---|
| `LANG_LIT` | LANGUES ET LITTÉRATURES | LANGUAGE AND LITERATURE |
| `SCI_TECH` | SCIENCES ET TECHNOLOGIE | SCIENCE AND TECHNOLOGY |
| `DEV_PERS` | DÉVELOPPEMENT PERSONNEL | *(non imprimé)* |
| `HUM_SOC` | *(non imprimé)* | HUMAN AND SOCIAL SCIENCES |
| `VOC` | *(non imprimé)* | VOCATIONAL STUDIES |

Les traductions manquantes sont à valider avec l'école avant impression.

### 5.3 Séquences d'une année (`evaluation_sessions`)
| `number` | `shortLabel` | `termNumber` |
|---|---|---|
| 1, 2 | DEV1, DEV2 | 1 |
| 3, 4 | DEV3, DEV4 | 2 |
| 5, 6 | DEV5, DEV6 | 3 |

À créer automatiquement avec la création de l'année scolaire (statut `PLANNED`).

### 5.4 Barèmes observés (`grading_schemes`)
`SECONDAIRE_FR` (sections FRANCOPHONE) et `SECONDARY_EN` (ANGLOPHONE), cycles secondaires : `maxScore 20`, `passMark 10`, `classSuccessThreshold 12`, `decimals 2`, `rankingPolicy COMPETITION`, `missingMarkPolicy IGNORE`, `absencePolicy EXCLUDE`, `roundingPolicy HALF_UP` (ou `LEGACY_DOUBLE` pour la démo de rejeu), et les tranches d'appréciation :

| code | minInclusive | maxExclusive |
|---|---|---|
| C | 0 | 12 |
| B | 12 | 14 |
| B+ | 14 | 16 |
| A | 16 | — |

Les mentions générales et les règles de distinction restent vides tant que Q-09 et Q-11 ne sont pas tranchées.

## 6. Ce qui n'est volontairement pas dans le modèle

| Sujet | Où sera-t-il traité ? |
|---|---|
| Emplois du temps (`TimetableSlot`) | module suivant, voir `02-domaine-metier.md` § 6 |
| Cartes d'élèves | module suivant |
| Frais de scolarité et paiements | à décider (Q-19/Q-20), module suivant |
| Détail des absences (une ligne par absence) | seulement des compteurs pour l'instant (`StudentPeriodReview`) |
| Pièces jointes et fichiers (photos, logo, PDF) | stockage de fichiers à choisir ; le modèle ne contient que des références (`photoFileId`, `logoFileId`) |
| Authentification et autorisations | `UserAccount` est un squelette ; voir `05-roles-permissions.md` |
| Notifications (SMS, WhatsApp, e-mail) | module futur |
