# 10 — Scolarité et solvabilité

> Module `fees` (docs/08) — **v1** (API barèmes / paiements / solvabilité / politique / job rappels-pénalités-exclusion + UI liste, fiche, workspace Solvabilité ; **écran Direction Barèmes** = niveau + N tranches). Objectif : suivre l’**inscription annuelle** et les **tranches de scolarité**, afficher un **statut de solvabilité** dérivé, et permettre à la **direction / comptabilité** d’enregistrer les paiements.
>
> Fuseau d’affichage et de calcul des échéances : **Africa/Douala**. Montants : `BigDecimal` / `DECIMAL128`, comparaison `compareTo`.

## 1. Processus métier

1. **Chaque année scolaire**, tout élève (nouveau, promu, redoublant) doit être **inscrit** pour l’année (`Enrollment`) **et** s’acquitter des **frais d’inscription** pour accéder à l’école (règle d’accès — **Q-65 figé**, code différé : bloquer consultation et modification des notes si non-paiement ; garder l’affichage solvabilité).
2. La **direction** (Q-66) définit, **par année et par niveau** (**Q-67 figé**) :
   - le montant d’**inscription** ;
   - les montants et échéances de **N tranches** de scolarité (**Q-68 figé**, N = 1…12, dates saisies).
3. Suggestion UI des échéances : dernier jour calendaire du mois de `AcademicYear.startDate` +(k−1) ; la Direction peut les modifier à la saisie.
4. Les **paiements** sont saisis manuellement (reçu banque papier — **Q-69 figé**) et rattachés à un élève + année (+ tranche ou inscription).
5. La **solvabilité** affichée est **dérivée** (non stockée) de la date du jour et des paiements enregistrés.
6. Résolution barème élève : `Enrollment.classId` → `SchoolClass.levelId` → `TuitionSchedule`.

## 2. Statut de solvabilité (UI)

| Code | Libellé FR | Libellé EN | Couleur | Règle (Q-70) |
|---|---|---|---|---|
| `SOLVENT` | Solvable | Solvent | vert | La **tranche dont le mois civil est en cours** (parmi les N échéances) est **payée** ; **et** aucune tranche **échue** n’est impayée. Si hors période des échéances : toutes les tranches exigibles à ce jour sont payées (+ inscription si exigée). |
| `PENDING` | En attente | Pending | ambre | La tranche du **mois en cours** n’est **pas encore** payée, **mais** toutes les tranches des **mois précédents** (échues) sont payées (et l’inscription si exigée). |
| `INSOLVENT` | Insolvable | Insolvent | rouge | Au moins une tranche **échue** **n’est pas payée**, ou l’**inscription** obligatoire n’est pas payée alors qu’elle est exigée. |
| `NOT_APPLICABLE` | — / Non concerné | N/A | neutre | Pas d’inscription année, ou barème non paramétré pour le **niveau**, ou année hors scolarité (Q-71). |

**Précision mois « en cours »** : mois civil `Africa/Douala` égal au mois d’une `dueDate` de tranche ; avant la 1ʳᵉ échéance → inscription seule ; après la dernière → seules les impayés échus / inscription comptent (Q-70 / Q-72).

## 3. Modèle de données (module `fees`)

Collections préfixées `fees_*` (aucune jointure vers `student_*` / `academic_*` : références par id + libellés figés).

### 3.1 `fees_tuition_schedules` — barème année / niveau

| Champ | Type | Notes |
|---|---|---|
| `schoolId` | String | index composite |
| `academicYearId` | String | |
| `levelId` | String | **Q-67** : barème par niveau |
| `levelLabel` | String | copie figée (FR prioritaire) |
| `enrollmentFee` | Decimal128 | frais d’inscription |
| `installments[1…N]` | embed | `{ index: 1..N, amount, dueDate }` — `dueDate` **saisie** (suggestion fin de mois) ; N ≤ 12 (**Q-68**) |
| `currency` | String | défaut `XAF` (Q-73) |
| `active` | Boolean | |

Index : `uk_school_year_level` unique `(schoolId, academicYearId, levelId)`.

### 3.2 Compte dérivé (pas de collection `fees_student_accounts` en v1)

Le compte élève / année est **calculé en service** à partir de `Enrollment` + `TuitionSchedule` (via `levelId`) + `fees_payments` (BR-F06).

### 3.3 `fees_payments` — écritures de paiement

| Champ | Type | Notes |
|---|---|---|
| `schoolId` | String | |
| `academicYearId` | String | |
| `studentId` | String | |
| `enrollmentId` | String | |
| `classId` | String | classe au moment du paiement (figée) |
| `scheduleId` | String | barème appliqué |
| `kind` | `ENROLLMENT` \| `INSTALLMENT` | |
| `installmentIndex` | Int? | 1..N si tranche |
| `amount` | Decimal128 | |
| `paidAt` | Instant | |
| `method` | enum / texte | espèces, mobile money… (Q-75) |
| `reference` | String? | reçu |
| `recordedBy` | String | `UserAccount.id` |
| `note` | String? | |
| `status` | `RECORDED` \| `VOID` | |

Pas de suppression physique : annulation = statut `VOID` + motif (Q-76).

## 4. Règles métier (BR-fees)

- **BR-F01** Un barème scolarité est unique par `(école, année, niveau)` (**Q-67**).
- **BR-F02** Les montants et le nombre de tranches sont paramétrables depuis l’UI (FR/EN), sans seuil codé en dur (hors bornes 1…12).
- **BR-F03** Les échéances de tranche sont **saisies** ; suggestion = dernier jour du mois de `AcademicYear.startDate` +(index−1), fuseau Douala (**Q-68**).
- **BR-F04** L’inscription annuelle (`Enrollment`) ne suffit pas seule pour « accès école » si BR-F05 est actif : il faut aussi le **paiement d’inscription** (**Q-65**).
- **BR-F05** Bloquer consultation **et** modification des notes tant que l’inscription / échéances exigibles ne sont pas soldées — **figé Q-65**, **implémentation différée** après UI Barèmes ; v1 actuelle = affichage solvabilité seulement.
- **BR-F06** Solvabilité calculée dans `service/fees` uniquement ; la liste élèves consomme `FeesApi.solvencyForStudents(...)`.
- **BR-F07** Tout `BigDecimal` stocké : `@Field(DECIMAL128)` ; comparaisons `compareTo`.
- **BR-F08** Toute requête filtre `schoolId` ; index composites commencent par `schoolId`.
- **BR-F09** Saisie / annulation des paiements (reçu banque → mise à jour solvabilité) : `DIRECTOR` / `SUPER_ADMIN`, ou `ADMINISTRATION` dont la fiche Staff porte la fonction `ACCOUNTANT`. Barèmes (montants) : Direction seule. Lecture solvabilité : admin / dir. / SG ; **parent = enfants liés uniquement** ; **enseignant : pas d’accès**.
- **BR-F10** Rappels automatiques parents (inbox) **15 / 10 / 5 / 3 / 2 / 1** jours avant l’échéance de la tranche non payée (+ jour J si activé). Paramétrable via `FeesPolicy.reminderDaysBeforeDue`. **Un seul message par compte parent**, même multi-enfants : détail nom, statut, payé / non payé, prochaine échéance.
- **BR-F11** Au passage **INSOLVENT** : notification parent avec montant de pénalité hebdo et délai max d’exclusion (si définis).
- **BR-F12** Pénalité hebdomadaire après insolvabilité : défaut **1000 XAF / semaine / enfant**, montant et devise dans `FeesPolicy` (Direction). Accrual idempotent (`fees_penalty_charges` + `fees_student_states`).
- **BR-F13** Avertissement parent **N jours** avant exclusion (`exclusionWarningDaysBefore`, défaut 7).
- **BR-F14** Après `maxInsolvencyGraceDays` (défaut 28, Direction) sans régularisation : **désactivation** de l’élève (`Student.active=false`, motif `FEES_INSOLVENCY_EXCLUSION`) + notification. Réactivation = **Direction seule** ; avant réinscription année : solder pénalités + tranches (**Q-80 figé**, workflow UI différé).

### Fuseau et job

- Calculs et cron : **Africa/Douala**, cron défaut `0 0 7 * * `* (`beesm.fees.reminder-cron`).
- Idempotence : clé `dedupeKey` = sourceEventId inbox ; réexécution le même jour ne duplique pas.

## 5. API publique

Module `fees` :

| Méthode | Chemin | Qui | Notes |
|---|---|---|---|
| `GET` | `/api/v1/fees/access` | authentifié | `canRead`, `canManageSchedules`, `canRecordPayments`, `canManagePolicy` |
| `GET` / `PUT` | `/api/v1/fees/policy` | lecture Admin+ ; écriture **DIRECTOR** | rappels / pénalités / exclusion |
| `GET` / `PUT` | `/api/v1/fees/schedules` | lecture comptable+ ; écriture **DIRECTOR** | barèmes année / **niveau** |
| `GET` | `/api/v1/fees/students/{studentId}/account?academicYearId=` | lecture scoped | détail + solvabilité + paiements |
| `GET` | `/api/v1/fees/solvency?academicYearId=&studentIds=` | lecture scoped | batch liste élèves (virgules) |
| `GET` | `/api/v1/fees/unpaid-summary?academicYearId=` | **staff** Admin / Dir. / SG / SuperAdmin | compteurs hub : inscription + `installmentsUnpaid[]` + total insolvables |
| `GET` | `/api/v1/fees/dashboard-summary?academicYearId=` | **staff** Admin / Dir. / SG / SuperAdmin | KPI workspace : montants XAF, inscription seule, pénalités, grâce J+14/21/max, niveaux, encaissements 7/30 j., ponctualité, série quotidienne 30 j. |
| `POST` | `/api/v1/fees/payments` | **BR-F09** | enregistrer un paiement |
| `POST` | `/api/v1/fees/payments/{id}/void` | **BR-F09** | annuler (`VOID` + motif) |

API inter-modules :

- `FeesApi.solvencySnapshot(schoolId, academicYearId, studentIds)` — liste élèves / portal (pas d’import `repository`/`model` hors module).

Événements (outbox) :

- `fees.tuition.reminder` → inbox parent (`FEES_REMINDER` / `FEES_INSOLVENT` / `FEES_PENALTY` / `FEES_EXCLUSION`) ; payload bilingue multi-enfants.
- `fees.payment.recorded` → confirmation parent (roadmap).
- `academic.class.changed` / `student.enrollment.*` → rattacher ou invalider le compte scolarité (idempotent).

## 6. UI (front Next.js, dépôt séparé)

### 6.1 Droits d’écran

| Surface | Qui la voit |
|---|---|
| Colonne / pastille solvabilité (liste, fiche) | Direction, Administration, SG ; **pas** enseignant ; parent = enfants liés |
| Bouton **Enregistrer un paiement** | `GET /fees/access` → `canRecordPayments` (**DIRECTOR** / **SUPER_ADMIN**, ou **ADMINISTRATION** + `StaffFunction.ACCOUNTANT`) **et** barème applicable |
| Nav **Élèves → Solvabilité** (`/{role}/students/solvency`) | Même règle que `canRecordPayments` (masquée sinon) |
| Hub élèves — onglet **Solvabilité** (après Inactifs) + cartes impayés | `canRecordPayments` ; clic → workspace Solvabilité |
| **Scolarité → Barèmes** (`/director/fees/schedules`) | Direction seule (`canManageSchedules`) |

### 6.2 Surfaces

- **Liste élèves** : colonne **Solvabilité** ; onglets Tous / Actifs / Inactifs / **Solvabilité** ; cartes stats (Total, Actifs, Inactifs, Insolvables) + ligne impayés (Inscription + N tranches via `installmentsUnpaid[]`).
- **Fiche élève** : section **Scolarité & solvabilité** (sous Photo / Tuteurs) ; héros **fond blanc** (pas de dégradé bleu).
- **Workspace Solvabilité** (`/director|administration/students/solvency`) : KPI montants (dû / encaissé / reste), inscription non soldée, pénalités (BR-F12), risque d’exclusion (grâce), donuts solvabilité & ponctualité, barres impayés / niveaux, **courbe** encaissements 30 j. (montant + cumul + volume) via `GET /fees/dashboard-summary`.
- Direction : écran **Barèmes** (niveaux + N tranches) ; écran **Politique** (roadmap UI).
- Portail parent : inbox rappels `FEES_*` (deep-link roadmap).

## 7. Périmètre livré vs suite

| Livré / en cours | Suite |
|---|---|
| Barèmes par **niveau** + **N** tranches + inscription | Remises, bourses |
| Saisie manuelle des paiements (dialog) | Mobile money / reçu PDF (sans marque tierce) |
| Rappels inbox 15/10/5/3/2/1 + insolvabilité | SMS / e-mail opt-in |
| Pénalité 1000 XAF/sem. paramétrable | Plafond ; remise de peine |
| Exclusion auto après grâce | Soft-block portail avant exclusion |
| Un message multi-enfants | Récap mensuel PDF famille |
| Devise XAF | Multi-devises |
| Solvabilité liste + fiche + workspace + unpaid-summary dynamique | **Q-65** blocage notes ; UI Politique ; VOID UI ; **Q-80** réactivation ; portail parent état de compte |
| Seed démo variété solvabilité (`FeesDemoSeedRunner`) | Import cahier de caisse / grille réelle |

## 8. Questions

**Q-65, Q-67, Q-68, Q-69, Q-80 figées** — détail dans `07-questions-ouvertes.md`. Q-70…Q-76 et Q-77…Q-79 restent les défauts déjà documentés.
