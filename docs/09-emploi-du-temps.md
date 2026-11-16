# 09 — Emploi du temps (module `timetable`)

> **Document de référence** du module EDT. Complet pour métier, modèle, API, front, seed, notifications et questions ouvertes.
>
> Sources terrain : PDF « EMPLOIS DU TEMPS ANNÉE SCOLAIRE 2026/2027 » — **12 pages** A4 paysage (une par classe, **40 cases** chacune).  
> Classes : 7 francophones (6ème, 5ème, 4ème, 3ème, 2nde C, 1ère A, 1ère C/D) + 5 anglophones (Form 1–3, Form 4 ARTS, Form 4 SC).  
> Décision école : généré **une seule fois par an** (semaine type). Exigence PDF : **une seule page A4 paysage** par classe.
>
> Code : `model/timetable/`, `service/timetable/`, `controller/timetable/`, `dto/timetable/`.  
> Front : `front-office` — hub `/…/timetable` par espace de rôle.  
> Fixtures : `docs/fixtures/timetable-*.json`. Seed privé : `docs/seed-private/`.  
> Aide in-app : `front-office/docs/rag/timetable.md`.  
> Renvois : `02` (BR), `03` (collections), `05` (rôles), `07` (Q-xx), `08` (événements).

---

## Table des matières

1. [Vision et principes](#1-vision-et-principes)
2. [Ce que montre le document source](#2-ce-que-montre-le-document-source)
3. [Glossaire](#3-glossaire)
4. [Modèle de données](#4-modèle-de-données)
5. [Cycle de vie d’un EDT](#5-cycle-de-vie-dun-edt)
6. [Cases : LESSON, ACTIVITY, options, séances communes, hors grille](#6-cases)
7. [Alias de matières](#7-alias-de-matières)
8. [Conflits et avertissements](#8-conflits-et-avertissements)
9. [Demandes de modification (BR-67)](#9-demandes-de-modification-br-67)
10. [PDF](#10-pdf)
11. [Notifications](#11-notifications)
12. [API REST](#12-api-rest)
13. [Permissions](#13-permissions)
14. [Front (espaces UI)](#14-front-espaces-ui)
15. [Seed et import El Shammah](#15-seed-et-import-el-shammah)
16. [Événements (outbox)](#16-événements-outbox)
17. [Règles métier (BR-xx)](#17-règles-métier-br-xx)
18. [État d’implémentation](#18-état-dimplémentation)
19. [Questions ouvertes liées](#19-questions-ouvertes-liées)
20. [Fichiers de référence](#20-fichiers-de-référence)

---

## 1. Vision et principes

### 1.1 Pourquoi un module séparé

L’emploi du temps est un **module** (`timetable`) au sens de `08-architecture-modulaire.md` :

- il **ne lit / n’écrit** que ses collections Mongo ;
- il référence academic / staff / student **par identifiants** ;
- les **libellés d’impression** (`subjectLabel`, `teacherLabel`, `classLabel` sur les demandes) sont **copiés** (figés) ;
- les mises à jour croisées passent par l’**outbox** (pas de jointure inter-modules).

Demain : microservices. Aujourd’hui : monolithe modulaire + ArchUnit.

### 1.2 Ce que l’EDT n’est pas

| Non | Oui |
|---|---|
| Calendrier semaine par semaine | **Semaine type** répétée toute l’année |
| Solveur automatique de créneaux (v1) | **Saisie manuelle** + détection de conflits (Q-32) |
| Remplacements ponctuels | Hors EDT annuel (Q-33) |
| Source de vérité des notes / coefficients | Catalogue `academic` + `ClassSubject` |
| Chat / messagerie | Notifications in-app professionnelles uniquement |

### 1.3 Acteurs

| Acteur | Rôle principal sur l’EDT |
|---|---|
| Administration / Directeur | Créer, éditer, publier, dupliquer, revue BR-67, PDF |
| Surveillant général | Lecture (+ édition si `School.timetableSurveillantCanEdit`) |
| Enseignant | Lecture de ses cours + EDT général ; **proposer** une modif (BR-67) |
| Parent | Lecture classe de l’enfant + EDT général + PDF |
| Élève | Lecture de sa classe + EDT général + PDF |
| Super-admin | Gestion multi-tenant (journalisée) |

---

## 2. Ce que montre le document source

### 2.1 En-tête

Bilingue (logo, ministère, délégations). **Contacts propres au PDF EDT** : champ `Timetable.headerContact` (Q-45) — souvent différents des téléphones des bulletins (`School.contact`).  
Titre selon la **langue de la section** de la classe. Nom de classe **complet** (« FORM 4 ARTS » / « FORM 4 SC », pas seulement « FORM 4 »).

### 2.2 Grille horaire (lun–ven)

Identique pour toutes les classes. Les heures sont **stockées** (pas calculées) : P7 dure 60 min, les autres ~50.

| N° | Horaire | | N° | Horaire |
|---|---|---|---|---|
| 1 | 7:35 – 8:25 | | 6 | 12:00 – 12:50 |
| 2 | 8:25 – 9:15 | | — | 12:50 – 13:20 DEUXIÈME PAUSE / SECOND BREAK |
| 3 | 9:15 – 10:05 | | 7 | 13:20 – 14:20 (60 min) |
| — | 10:05 – 10:20 PREMIÈRE PAUSE / FIRST BREAK | | 8 | 14:20 – 15:10 |
| 4 | 10:20 – 11:10 | | — | 15:10 – 15:30 NETTOYAGE GÉNÉRAL / GENERAL CLEAN UP |
| 5 | 11:10 – 12:00 | | — | RENTRÉE / CLOSING |

→ Document `TimeSlotGrid` + lignes `GridPeriod` (`LESSON` | `BREAK` | `CLEANUP` | `CLOSING` | `ASSEMBLY`).  
Fixture : `docs/fixtures/timetable-grid.json`.  
Format d’impression EN (« 1 : 20 – 2 : 20 ») ≠ donnée stockée.

### 2.3 Contenu des cases

Matière + enseignant. Double / triple période = `periodCount` (ex. 2 → `startPeriod=3`, `periodCount=2`).

### 2.4 Séances communes (cours groupés)

Le même enseignant apparaît dans **plusieurs classes au même créneau** (ex. 6ème+5ème LCN/Chinois/EPS ; 1ère A + 1ère C/D ; Form 1+2 ; Form 4 ARTS+SC ; parfois 3ème+Form 4 EPS).

→ `TimetableEntry.sessionGroupId`. Cases avec le **même** id **ne se conflictuent pas** (enseignant / salle).  
Éditeur et seed : créer / défaire / auto-lier (Q-55). Voir § 6.4 et § 8.

### 2.5 Options LV2 en parallèle

En 4ème, 3ème, 1ère A : case « ALL (prof. A) / ESP (prof. B) » = **deux cours** pour la même classe au même créneau.

→ `optionGroupCode` (ex. `"LV2"`) + `optionCode` (ex. `"ALL"`, `"ESP"`).  
Index unique `uk_class_slot_option` inclut `optionCode` (`""` si pas d’option).

**Q-41** : l’EDT 2026-2027 **suggère** une seule classe avec options. Le modèle **notes** (`ClassSubject` / `Enrollment`) n’est **pas** tranché ici.

### 2.6 Libellés EDT ≠ matières de bulletin

« FRANÇAIS » / « LITTÉRATURE » recouvrent souvent plusieurs matières bulletin ; « TM » ≈ Travail manuel (+ ESF absente de l’EDT) ; fautes d’impression (« CNINOIS », « NFORMATIQUE »…).

→ Collection `TimetableSubjectAlias` — **jamais** de matching texte libre à la saisie / au seed (Q-30, Q-56, Q-63). Voir § 7.

### 2.7 Samedi / hors grille (Q-33)

Trois cours du samedi (blocs multi-heures), **pas seulement 1ère C/D** :

| Classe | Cours | Horaires |
|---|---|---|
| 3ème | Allemand 3 h | 7h30 – 10h30 |
| 1ère A | Allemand 3 h | 10h30 – 13h30 |
| 1ère C/D | TD Chimie 2 h | 8h00 – 10h00 |

→ `DayOfWeek.SATURDAY` + `customStart` / `customEnd` ; `slotKey` = `T0730` (heure de début). Les conflits portent sur les **heures réelles**.

### 2.8 Activités sans note (Q-47 v1)

CHINOIS, MUSIQUE, CLUB, BIBLIOTHÈQUE, MÉNAGE GÉNÉRAL → `EntryKind.ACTIVITY` (+ `ActivityType`). **Aucun** `ClassSubject` obligatoire. Une case ACTIVITY **n’engage pas** l’enseignant pour les conflits enseignant (BR-65, Q-57).  
Cases mixtes (« CLUB / MATHS / SVT ») → champ `note`. Couleurs du PDF source = purement visuel (non stockées).

---

## 3. Glossaire

| Terme | Sens |
|---|---|
| **Grille** (`TimeSlotGrid`) | Journée type lun–ven (périodes, pauses, nettoyage) |
| **EDT / Timetable** | Document d’une année : révision + statut + lien grille + contacts PDF |
| **Case / Entry** | Une occupe de créneau pour une classe (+ option éventuelle) |
| **`slotKey`** | Clé d’unicité du créneau : `P4` (période) ou `T0800` (hors grille) |
| **Révision** | Numéro monotone par année ; une seule `PUBLISHED` à la fois |
| **Séance commune** | Plusieurs classes, même créneau, même `sessionGroupId` |
| **Option parallèle** | Deux (ou plus) cases même classe / créneau, `optionCode` distincts |
| **Alias** | Libellé imprimé → `classSubjectIds[]` |
| **Bloquant** | Empêche la publication (`CLASS_OVERLAP`, `TEACHER_OVERLAP`, `ROOM_OVERLAP`) |
| **Avertissement** | N’empêche pas la publication (`HOURS_MISMATCH`, enseignant hors ClassSubject, doublons PDF…) |

---

## 4. Modèle de données

Collections (préfixe `timetable_`). Tout document est multi-tenant (`schoolId`). Index composites **commencent par `schoolId`**.

### 4.1 Vue d’ensemble

| Collection | Document | Rôle |
|---|---|---|
| `timetable_grids` | `TimeSlotGrid` (+ `GridPeriod`) | Grille horaire lun–ven |
| `timetable_timetables` | `Timetable` | EDT d’une année (révisions) + `headerContact` |
| `timetable_entries` | `TimetableEntry` | Cases |
| `timetable_subject_aliases` | `TimetableSubjectAlias` | Alias → ClassSubject(s) |
| `timetable_change_requests` | `TimetableChangeRequest` (+ `TimetableEntrySnapshot`) | BR-67 |

Flag école : `School.timetableSurveillantCanEdit` (défaut `false`, Q-32).

### 4.2 `TimeSlotGrid`

```
schoolId, academicYearId, name, days (lun–ven), periods[], active
```

`GridPeriod` : `number?` (1..8 si `LESSON`), `kind`, `start`, `end`, `label` bilingue (`LocalizedText`).

Index : `idx_school_year` `{ schoolId, academicYearId }`.

### 4.3 `Timetable`

```
schoolId, academicYearId, gridId, revision, status,
generatedAt, generatedBy, publishedAt, publishedBy,
headerContact?, note?
```

`TimetableStatus` : `DRAFT` | `PUBLISHED` | `ARCHIVED`.

Index :

- `uk_year_revision` unique `{ schoolId, academicYearId, revision }`
- `idx_year_status` `{ schoolId, academicYearId, status }`

### 4.4 `TimetableEntry`

Champs principaux :

| Champ | Rôle |
|---|---|
| `timetableId`, `academicYearId`, `classId`, `day` | Ancrage |
| `startPeriod`, `periodCount` | Créneau grille |
| `customStart`, `customEnd` | Hors grille (samedi…) |
| `slotKey` | `P{n}` ou `T{HHmm}` — calculé par le service (`SlotKeys`) |
| `kind` | `LESSON` \| `ACTIVITY` |
| `classSubjectIds` | Liens matières de classe (vide si ACTIVITY) |
| `subjectId`, `teacherId`, `roomId` | Références optionnelles |
| `activityType` | `CLUB`, `LIBRARY`, `MUSIC`, `CHINESE`, `GENERAL_CLEANING`, `STUDY_HALL`, `OTHER` |
| `sessionGroupId` | Séance commune |
| `optionGroupCode`, `optionCode` | Options parallèles (`optionCode=""` si aucune) |
| `label` | Libellé libre bilingue (activités, TD…) |
| `note` | Texte libre |
| `subjectLabel`, `teacherLabel` | Copies d’impression |

Index :

| Nom | Définition | Usage |
|---|---|---|
| `uk_class_slot_option` | unique `{ schoolId, timetableId, classId, day, slotKey, optionCode }` | Une case / option / créneau |
| `idx_teacher_day` | `{ schoolId, timetableId, teacherId, day }` | Conflits enseignant |
| `idx_room_day` | `{ schoolId, timetableId, roomId, day }` | Conflits salle |
| `idx_classsubjects` | `{ schoolId, timetableId, classSubjectIds }` | BR-63 heures |
| `idx_session_group` | `{ schoolId, timetableId, sessionGroupId }` | Séances communes |

### 4.5 `TimetableSubjectAlias`

```
schoolId, academicYearId, section?, sectionKey ("_" si toutes),
aliasNormalized, aliasVariants[], classSubjectIds[],
unresolved, note?, active
```

Index unique `uk_alias_year_section` `{ schoolId, academicYearId, sectionKey, aliasNormalized }`.

### 4.6 `TimetableChangeRequest` (BR-67)

```
sourceTimetableId, sourceRevision, sourceEntryId?,
sourceClassId, sourceDay, sourceSlotKey, sourceOptionCode,
changeKind (UPDATE|REMOVE|ADD),
currentSnapshot?, proposed (TimetableEntrySnapshot),
classLabel?, teacherId, teacherLabel?,
reason (≥ 15 car.), status (PENDING|APPROVED|REJECTED|CANCELLED),
requestedBy/At, reviewedBy/At, reviewNote?,
appliedTimetableId?, appliedEntryId?
```

Index : `idx_tt_change_status`, `idx_tt_change_teacher`, `idx_tt_change_entry_pending`.

### 4.7 Frontières de modules

Le module `timetable` **n’importe pas** les `repository` / `model` d’autres modules. Il passe par :

- `service/academic/api` (année, classes, ClassSubject, School flags…),
- `service/staff/api`,
- DTOs partagés si besoin,
- outbox pour notifier `notification`.

Test ArchUnit : `ModuleBoundariesArchTest`.

---

## 5. Cycle de vie d’un EDT

```
                    createDraft
                         │
                         ▼
              ┌──── DRAFT (rév. n) ◄──── revise (depuis PUBLISHED)
              │         │                      │
              │    edit entries                │ copie cases + header
              │         │                      │
              │      publish ──────────────────┘
              │         │
              │         ▼
              │   PUBLISHED (rév. n) ──► (ancienne PUBLISHED → ARCHIVED)
              │         │
              │         └── revise → nouveau DRAFT (rév. n+1)
              │
              └── duplicateToYear → DRAFT sur une autre année
```

### 5.1 Créer un brouillon

`POST /api/v1/timetables` → `DRAFT`, `revision` = max(année)+1 (souvent 1). Lie une `gridId`.

### 5.2 Éditer

Uniquement si `status == DRAFT` : métadonnées (`PUT`), upsert / delete cases, lier / délier séances communes.  
Un EDT `PUBLISHED` est **immuable** : pour changer → `revise` puis éditer le nouveau brouillon.

### 5.3 Publier (BR-60)

`POST /api/v1/timetables/{id}/publish` :

1. Vérifie droits manage + année writable.
2. Analyse conflits : **si `blocking` non vide → erreur `PUBLISH_BLOCKED`**.
3. Archive l’éventuelle révision `PUBLISHED` de la même année.
4. Passe le brouillon en `PUBLISHED` (`publishedAt`, `publishedBy`).
5. Écrit l’outbox `timetable.published` → notifications (voir § 11).

Les **warnings** n’empêchent pas la publication.

### 5.4 Réviser

`POST /api/v1/timetables/{id}/revise` (source `PUBLISHED`) : crée un nouveau `DRAFT` (rév. suivante) avec copie des cases (`TimetableCopySupport`). L’EDT publié reste consultable jusqu’à la prochaine publication.

### 5.5 Dupliquer vers une autre année

`POST /api/v1/timetables/{id}/duplicate` + `targetAcademicYearId` (+ grille cible). Utile pour démarrer l’année N+1 à partir de N.

---

## 6. Cases

### 6.1 Règle d’une case (BR-61)

Une case = classe + jour + créneau (+ option) + matière(s) et/ou activité + enseignant (+ salle).

Deux modes horaires (mutuellement exclusifs — `SlotKeys`) :

| Mode | Champs | `slotKey` |
|---|---|---|
| Période de grille | `startPeriod`, `periodCount` | `P{startPeriod}` |
| Heures libres | `customStart`, `customEnd` | `T{HHmm}` du début |

### 6.2 LESSON vs ACTIVITY

| | LESSON | ACTIVITY |
|---|---|---|
| `classSubjectIds` | souvent ≥ 1 | souvent vide |
| Conflit enseignant | oui si `teacherId` | **non** (BR-65) |
| Notes / bulletin | via ClassSubject | hors notes v1 (Q-47) |

### 6.3 Options parallèles

Même `classId` + même `day` + même `slotKey` autorisé **si** `optionGroupCode` identique et `optionCode` **différents** et non vides. Sinon → `CLASS_OVERLAP`.

### 6.4 Séances communes (`sessionGroupId`)

- Lien manuel : `POST .../session-groups` avec liste d’`entryIds`.
- Déliaison : `POST .../session-groups/{id}/unlink`.
- Seed : `EdtJointSessionIndex` + auto-heal post-import (§ 15).
- Front conflits : actions « Lier en séance commune » / « Lier automatiquement ».

### 6.5 BR-66 (pauses / nettoyage)

Une case **sur grille** doit démarrer sur une période `LESSON`. Un créneau custom **entièrement** dans une pause / nettoyage un jour de grille est refusé. Le samedi (jour hors `grid.days`) est autorisé.

---

## 7. Alias de matières

Objectif : relier les ~59 libellés imprimés (fautes incluses) aux `ClassSubject` sans saisie libre.

| Correspondance | Effet |
|---|---|
| 1 → 1 | Seed peut remplir `ClassSubject.weeklyHours` |
| 1 → N | BR-63 : somme des `weeklyHours` vs périodes EDT |
| Non résolu | `unresolved=true` → rapport admin |

API : `POST/GET/DELETE /api/v1/timetable-subject-aliases` (désactivation logique).  
Normalisation : majuscules, sans accents de comparaison (`aliasNormalized`).

---

## 8. Conflits et avertissements

Moteur pur : `TimetableConflictEngine` (sans accès base). Orchestration : `TimetableConflictService`.

### 8.1 Bloquants (empêchent publication et upsert conflictuel)

| Code | Condition |
|---|---|
| `CLASS_OVERLAP` | Même classe, chevauchement horaire, options non parallèles |
| `TEACHER_OVERLAP` | Même `teacherId`, chevauchement, **pas** même `sessionGroupId`, aucune case ACTIVITY |
| `ROOM_OVERLAP` | Même `roomId`, chevauchement, pas même `sessionGroupId` |

Chevauchement : `startA < endB && startB < endA` (heures résolues via grille ou custom).

### 8.2 Avertissements (publication OK)

| Code / type | Sens |
|---|---|
| `HOURS_MISMATCH` | Somme des périodes EDT liées ≠ somme `weeklyHours` des ClassSubject (BR-63) |
| Enseignant hors `ClassSubject.teacherId` | Remplacement possible (BR-64) |
| Doublons PDF source (Q-58) | Au chargement : warning, pas blocage |

### 8.3 Endpoint

`GET /api/v1/timetables/{id}/conflicts` → `{ blocking: [], warnings: [] }`.  
Front : onglet Conflits — clic ligne → éditeur ; TEACHER_OVERLAP → lier séance.

---

## 9. Demandes de modification (BR-67)

Miroir des corrections de notes (BR-80) : l’enseignant **propose**, admin/directeur **décide**.

### 9.1 Parcours enseignant

1. EDT `PUBLISHED` ; case dont il est titulaire (`teacherId`).
2. Ouvre le détail → **Demander une modification**.
3. Motif ≥ 15 caractères ; `changeKind` = `UPDATE` | `REMOVE` | `ADD`.
4. Une seule demande `PENDING` par case (`sourceEntryId`).
5. Notif admin/directeur : catégorie `TIMETABLE_CHANGE_REQUESTED`, deep-link `/administration/timetable?tab=changes` (réécrit vers l’espace du rôle).

### 9.2 Parcours admin / directeur

| Action | Effet |
|---|---|
| Approuver | Applique sur un `DRAFT` existant de l’année, ou lance `revise` puis applique ; émet `timetable.change.approved` |
| Refuser | Motif de revue ; `timetable.change.rejected` |
| (Enseignant) Annuler | Statut `CANCELLED` tant que `PENDING` |

Notif enseignant : `TIMETABLE_CHANGE_DECIDED`, deep-link consultation EDT.

### 9.3 API

| Méthode | Chemin |
|---|---|
| `POST` | `/api/v1/timetable-change-requests` |
| `GET` | `/api/v1/timetable-change-requests/pending` |
| `GET` | `/api/v1/timetable-change-requests/mine` |
| `POST` | `/api/v1/timetable-change-requests/{id}/approve` |
| `POST` | `/api/v1/timetable-change-requests/{id}/reject` |
| `POST` | `/api/v1/timetable-change-requests/{id}/cancel` |

---

## 10. PDF

- Format : **A4 paysage**, **exactement 1 page** par classe (BR-62).
- Contenu : en-tête + `headerContact`, grille 5 jours × 8 périodes + pauses, cases (libellés figés), signature Administration.
- Langue : paramètre `language` ; priorité à la langue de la **section** de la classe.
- Dépassement de page → erreur claire (`TIMETABLE_PDF_OVERFLOW`) — **jamais** une 2ᵉ page.
- Endpoint : `GET /api/v1/timetables/{id}/pdf?classId=…&language=…`
- Test auto : le PDF a 1 page.
- Aucune marque tierce (« Powered by… ») sur le document.

Vues complémentaires (conception) : par enseignant / par salle — même contrainte 1 page si exportées.

---

## 11. Notifications

### 11.1 Synchronisation « nouvelle classe ↔ EDT publié »

```
EDT PUBLISHED
    │
    ▼
Nouvelle classe créée (ou réactivée)  ──academic.class.changed──►
    │
    ▼
Classe sans aucune case sur l’EDT publié ?
    │ oui
    ▼
Notif TIMETABLE_UPDATE_NEEDED → admin / directeur
                 (+ SG si timetableSurveillantCanEdit)
    │  deep-link /timetable?tab=editor&classId=…
    ▼
Éditeurs : revise → ajouter les créneaux → publish
    │
    ▼
timetable.published → notif TIMETABLE_PUBLISHED à **tous** les acteurs scolaires
```

Handler : `TimetableUpdateNeededNotificationHandler` (consomme `academic.class.changed`, actions `CREATED` / `ACTIVATED` uniquement).  
Si aucun EDT `PUBLISHED` pour l’année, ou si des cases existent déjà → **pas** de notif.

### 11.2 Publication / republication (BR-60)

Événement : `timetable.published`  
Handler : `TimetablePublishedNotificationHandler`

| | |
|---|---|
| Destinataires | **Tous** les comptes scolaires **actifs** : `STUDENT`, `PARENT`, `TEACHER`, `SURVEILLANT_GENERAL`, `ADMINISTRATION`, `DIRECTOR` |
| Catégorie | `TIMETABLE_PUBLISHED` |
| Révision 1 | Titre « Emploi du temps publié », sévérité `SUCCESS` |
| Révision > 1 | Titre « Emploi du temps mis à jour », sévérité `INFO` |
| Deep-link stocké | `/timetable` (logique) |
| Front | `resolveNotificationDeepLink` → `/{espace}/timetable` (ex. `/parent/timetable`) |
| Cloche | Clic aperçu → suit le deep-link (marque lu) |

Idempotence : couple `(sourceEventId, recipientAccountId)`.

### 11.3 Autres catégories EDT

| Événement | Catégorie | Destinataires typiques |
|---|---|---|
| `academic.class.changed` (CREATED/ACTIVATED, sans cases) | `TIMETABLE_UPDATE_NEEDED` | Éditeurs EDT |
| `timetable.change.requested` | `TIMETABLE_CHANGE_REQUESTED` | Admin / directeur |
| `timetable.change.approved` / `.rejected` | `TIMETABLE_CHANGE_DECIDED` | Enseignant demandeur |

---

## 12. API REST

Préfixe : `/api/v1`. Auth JWT. `schoolId` en query pour super-admin multi-tenant.

### 12.1 Grilles — `/timetable-grids`

| Méthode | Chemin | Rôle |
|---|---|---|
| `POST` | `/` | Créer |
| `GET` | `/?academicYearId=` | Lister |
| `GET` | `/{id}` | Détail |
| `PUT` | `/{id}` | Mettre à jour |

### 12.2 EDT — `/timetables`

| Méthode | Chemin | Rôle |
|---|---|---|
| `POST` | `/` | Créer brouillon |
| `GET` | `/?academicYearId=` | Lister |
| `GET` | `/{id}` | Détail |
| `PUT` | `/{id}` | Maj brouillon (header, note…) |
| `POST` | `/{id}/publish` | Publier |
| `POST` | `/{id}/revise` | Nouvelle révision DRAFT |
| `POST` | `/{id}/duplicate` | Dupliquer vers une année |
| `GET` | `/{id}/pdf?classId=` | PDF 1 page |
| `GET` | `/{id}/entries` | Lister cases (`classId` / `teacherId` opt.) |
| `POST` | `/{id}/entries` | Upsert case |
| `GET` | `/{id}/conflicts` | Rapport conflits |
| `POST` | `/{id}/session-groups` | Lier séance commune |
| `POST` | `/{id}/session-groups/{sgId}/unlink` | Délier |

### 12.3 Cases — `/timetable-entries`

| Méthode | Chemin |
|---|---|
| `GET` | `/{id}` |
| `PUT` | `/{id}` |
| `DELETE` | `/{id}` |

### 12.4 Alias — `/timetable-subject-aliases`

`POST` upsert · `GET` liste / détail · `DELETE` désactiver.

### 12.5 Demandes — `/timetable-change-requests`

Voir § 9.3.

Contrôleurs minces ; logique dans `service/timetable/*` ; DTO uniquement (jamais de document exposé).

---

## 13. Permissions

Implémentation : `TimetableAccess` (couche service).

| Action | Élève | Parent | Ens. | SG | Admin | Dir. |
|---|---|---|---|---|---|---|
| Lire + PDF | sa classe + général | enfant + général | ses cours + général | lecture | tout | tout |
| Créer / éditer / publier / dupliquer | ✖ | ✖ | ✖ | si flag école | ✔ | ✔ |
| Proposer modif (BR-67) | ✖ | ✖ | ses cases | ✖ | ✖ | ✔ (aussi) |
| Approuver / refuser | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |

Flag : `School.timetableSurveillantCanEdit` (défaut `false`).  
Matrice globale : `05-roles-permissions.md`.

---

## 14. Front (espaces UI)

Chaque rôle a un espace avec `/timetable` :

| Rôle | Chemin |
|---|---|
| Enseignant | `/teacher/timetable` |
| Parent | `/parent/timetable` |
| Élève | `/student/timetable` |
| SG | `/surveillant-general/timetable` |
| Administration | `/administration/timetable` (+ onglets édition) |
| Directeur | `/director/timetable` |

### 14.1 Hub admin / directeur

Composant principal : `timetable-hub-view` — onglets vue d’ensemble, édition par classe, conflits, demandes de changement, PDF.

- Ligne de révision / conflit cliquable → ouvre l’éditeur de case.
- Dialogue case : `timetable-entry-dialog` (sections Créneau / Contenu / Options ; supprimer dans le corps ; footer Annuler | Enregistrer).
- Empty state enseignant (pas de cases) : signal fort (rouge) + lien aide.

### 14.2 Deep-links notifications

`front-office/lib/notifications/deep-link.ts` :

- `/timetable` ou `/{autreEspace}/timetable` → `/{espaceCourant}/timetable` ;
- SUPER_ADMIN → `/notifications` (pas d’EDT établissement).

### 14.3 Aide in-app

`front-office/docs/rag/timetable.md` — textes FR/EN pour la cloche d’aide (publication, BR-67, conflits).

---

## 15. Seed et import El Shammah

### 15.1 Structure académique (public)

`docs/el-shammah-data/seed-el-shammah.json` → `ElShammahAcademicSeedRunner` (`@Order(15)`).  
**Sans** noms d’enseignants ni élèves (règle 12).

### 15.2 EDT privé (hors Git)

```
docs/seed-private/edt-2026-2027-extraction.json
```

Runner : `ElShammahEdtPrivateSeedRunner` (`@Order(16)`).

| Propriété | Défaut | Effet |
|---|---|---|
| `smels.seed.el-shammah-edt.enabled` | `true` | Active |
| `smels.seed.el-shammah-edt.resource` | fichier seed-private | Extraction |
| `smels.seed.el-shammah-edt.grid-resource` | `docs/fixtures/timetable-grid.json` | Grille |
| `overwrite-existing` | `false` | Insert-if-absent vs réécriture |
| `auto-heal-teacher-overlaps` | `true` | Lie les TEACHER_OVERLAP multi-classes |

Comportement :

1. Crée / enrichit fiches `Staff` **sans** `UserAccount` (Q-60 — accès via `POST /staff/{id}/login-access`).
2. Crée un `Timetable` **DRAFT** rév. 1 + cases (anti-doublons upsert).
3. Applique `EdtJointSessionIndex` (hints de séances jumelées).
4. Auto-heal : groupes les `TEACHER_OVERLAP` entre classes distinctes sous un `sessionGroupId`.
5. Logs : **ids** seulement, jamais de noms d’élèves / enseignants.

Fichier absent → runner ignore (pas d’échec au boot).

### 15.3 Fixtures anonymisées

| Fichier | Contenu |
|---|---|
| `docs/fixtures/timetable-grid.json` | Grille 8 périodes + pauses |
| `docs/fixtures/timetable-2nde-c-fr.json` | Exemple classe FR |
| `docs/fixtures/timetable-form4-arts-en.json` | Exemple classe EN |

---

## 16. Événements (outbox)

### 16.1 Publiés par `timetable`

| `eventType` | Payload principal | Consommateurs |
|---|---|---|
| `timetable.published` | `timetableId`, `academicYearId`, `revision` | `notification` (broadcast) |
| `timetable.change.requested` | `requestId`, `timetableId`, `teacherId`, libellés, `changeKind` | `notification` |
| `timetable.change.approved` | `requestId`, `appliedTimetableId`, … | `notification` |
| `timetable.change.rejected` | `requestId`, `reviewNote`, … | `notification` |

### 16.2 Consommés (conception)

`academic.class-subject.changed`, `academic.class.changed`, `academic.subject.renamed`, `staff.member.changed` — rafraîchissement des libellés / cohérence (pas d’événement dédié alias en v1 ; republish après maj manuelle).

Catalogue : `08-architecture-modulaire.md` § 4.

---

## 17. Règles métier (BR-xx)

| BR | Résumé | Où |
|---|---|---|
| **BR-60** | Une fois / an ; semaine type ; une `PUBLISHED` / année ; modif → nouvelle révision ; **notif tous les comptes scolaires** à chaque publish | `TimetableService`, `TimetablePublishedNotificationHandler` |
| **BR-61** | Structure d’une case | `TimetableEntry`, upsert |
| **BR-62** | PDF 1 page A4 paysage / classe | `TimetablePdfService` |
| **BR-09** | Conflits classe / enseignant / salle + options + `sessionGroupId` | `TimetableConflictEngine` |
| **BR-63** | Périodes EDT ↔ somme `weeklyHours` (warning) | `TimetableConflictService` |
| **BR-64** | Enseignant ≠ titulaire ClassSubject (warning) | idem |
| **BR-65** | ACTIVITY sans engagement enseignant au conflit | `TimetableConflictEngine` |
| **BR-66** | Pas de case sur pause / nettoyage (sauf hors grille) | `assertNotOnBreakOrCleanup` |
| **BR-67** | Demande de modif enseignant → revue admin/dir. | `TimetableChangeRequestService` |

Rappels transverses AGENTS.md : `schoolId` partout ; pas de `copy()` sur document persistant ; bilingue `LocalizedText` ; pas de DTO = pas d’API.

---

## 18. État d’implémentation

| Phase | Contenu | État |
|---|---|---|
| **0** | Modèle : séances, options, alias, samedi, activités ; docs | ✅ |
| **A** | API lecture + grille + brouillon | ✅ |
| **B** | Saisie + conflits BR-09 + séances / options | ✅ |
| **C** | Publier + dupliquer + `timetable.published` | ✅ |
| **D** | PDF 1 page + `headerContact` | ✅ |
| **E** | Front admin / directeur (éditeur) | ✅ |
| **F** | Front enseignant (lecture + BR-67) | ✅ |
| **G** | Demandes de modif (BR-67) | ✅ |
| **H** | Front parent / élève / SG (lecture + PDF) | ✅ |
| **I** | Seed privé 2026-2027 + joint index + auto-heal | ✅ |
| **J** | Notif broadcast publish + deep-link `/timetable` | ✅ |

Hors v1 : solveur automatique, disponibilités enseignants, remplacements ponctuels dans l’EDT annuel.

---

## 19. Questions ouvertes liées

Voir le détail dans `07-questions-ouvertes.md`. Synthèse EDT :

| Q | Sujet | Statut v1 |
|---|---|---|
| Q-30 | Libellés F/N, LCN, PCT… | Alias ; pas de match libre |
| Q-32 | Saisie manuelle vs solveur ; droit SG | **Figé** : manuel ; flag SG `false` |
| Q-33 | Samedi / remplacements | **Figé** : 3 cours samedi hors grille ; remplacements hors EDT |
| Q-41 | LV2 classes vs options | EDT = options ; notes **non tranché** |
| Q-45 | Contacts / immatriculation | `headerContact` EDT distinct |
| Q-47 | Chinois, musique, club… notés ? | ACTIVITY v1 |
| Q-55 | Quels cours groupés réels ? | `sessionGroupId` prêt |
| Q-56 / Q-63 | Alias FRANÇAIS / TM / LANG_FR | Table alias + seed provisoire |
| Q-57 | Bibliothèque / surveillance | ACTIVITY sans conflit ens. |
| Q-58 | Doublons PDF | Warning au chargement |
| Q-59 | Identité enseignants seed | `needsCompletion` ; ne pas inventer |
| Q-60 | Accès login enseignants | Option A : Staff sans compte + `login-access` |
| Q-61 | « Mme … » allemand | Case sans `teacherId` |

---

## 20. Fichiers de référence

### Back-office

| Zone | Chemins |
|---|---|
| Modèle | `src/main/kotlin/.../model/timetable/` |
| Services | `src/main/kotlin/.../service/timetable/` (+ `api/TimetableApi.kt`) |
| Contrôleurs | `src/main/kotlin/.../controller/timetable/TimetableControllers.kt` |
| DTOs | `src/main/kotlin/.../dto/timetable/` |
| Repos | `src/main/kotlin/.../repository/timetable/` |
| Notifs | `.../notification/Timetable*NotificationHandler.kt` |
| Seed | `.../configuration/seed/ElShammahEdtPrivateSeedRunner.kt`, `EdtJointSessionIndex.kt` |
| Tests | `src/test/kotlin/.../service/timetable/`, `.../notification/TimetablePublishedNotificationHandlerTest.kt` |
| Docs | `docs/09` (ce fichier), `02`, `03` § 5.6, `05`, `07`, `08` |
| Fixtures | `docs/fixtures/timetable-*.json` |
| Seed privé | `docs/seed-private/` (hors Git) |

### Front-office

| Zone | Chemins |
|---|---|
| Hub | `components/timetable/timetable-hub-view.tsx` |
| Dialogue case | `components/timetable/timetable-entry-dialog.tsx` |
| Deep-link | `lib/notifications/deep-link.ts` |
| Nav | `lib/auth/nav-config.ts` (entrée `timetable` par rôle) |
| Aide RAG | `docs/rag/timetable.md` |
| i18n | `messages/fr.json` / `en.json` → clés `timetableHub`, `notifications.category.TIMETABLE_*` |

---

*Dernière mise à jour doc : nov. 2026 — phases 0–J. Toute évolution du `model` doit être annoncée et reflétée ici + dans `03-modele-de-donnees.md`.*
