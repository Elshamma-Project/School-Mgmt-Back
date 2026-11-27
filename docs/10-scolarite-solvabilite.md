# 10 — Scolarité et solvabilité

> Module `fees` (docs/08) — **v1** (API barèmes / paiements / solvabilité / politique / job rappels-pénalités-exclusion + UI liste, fiche, workspace Solvabilité ; **hub Direction Scolarité** `/director/fees` = onglets Barèmes + Politique, voir § 6.3). Objectif : suivre l’**inscription annuelle** et les **tranches de scolarité**, afficher un **statut de solvabilité** dérivé, et permettre à la **direction / comptabilité** d’enregistrer les paiements.
>
> Fuseau d’affichage et de calcul des échéances : **Africa/Douala**. Montants : `BigDecimal` / `DECIMAL128`, comparaison `compareTo`.

## 1. Processus métier

1. **Chaque année scolaire**, tout élève doit être **inscrit** (`Enrollment`) **et** payer les **frais d’inscription** pour accéder à l’école (**Q-65 ✅** : blocage notes / EDT / portail parent ; Direction/Admin = consultation seule, pas de modification).
2. La **direction** (**Q-66 ✅**) définit, **par année et par niveau** (**Q-67 ✅**) :
   - le montant d’**inscription** ;
   - les montants et échéances de **N tranches** (**Q-68 ✅**, N = 1…12, dates **libres**) ;
   - éventuellement des **frais supplémentaires** (0…6) pour les niveaux d’examen (**Q-81 🟡**, BR-F15) — ex. suggestions FR : 3ème → BEPC, 1ère → Probatoire, Terminale → BAC (montants / échéances saisis, jamais codés en dur).
3. Suggestion UI des échéances : fin de mois `startDate` +(k−1) ; librement modifiable.
4. **Paiement séquentiel** : inscription → T1 → T2… → **extras** (si présents) ; montant du reçu = **exactement** le reste dû (**Q-74 ✅**). Inscription = **CASH** à l’école + **reçu PDF** (**Q-69/Q-75 ✅**) ; tranches / extras = reçu banque papier → saisie Direction/comptable.
5. La **solvabilité** affichée est **dérivée** (**Q-70 ✅**, **Q-72 ✅** — BR-F06).
6. Résolution barème : `Enrollment.classId` → `SchoolClass.levelId` → `TuitionSchedule`.

## 2. Statut de solvabilité (UI) — Q-70 / Q-72 figés

Comparaison par **mois civil** (`YearMonth`, fuseau **Africa/Douala**) de la `dueDate` de chaque tranche — **pas** le jour exact d’échéance.

| Code | Libellé FR | Libellé EN | Couleur | Règle |
|---|---|---|---|---|
| `SOLVENT` | Solvable | Solvent | vert | Inscription soldée ; **aucune** tranche de mois **strictement antérieur** impayée ; et soit pas de tranche échue ce mois-ci, soit celle du mois civil courant est payée. |
| `PENDING` | En attente | Pending | ambre | Inscription soldée ; aucune tranche de mois passé impayée ; **mais** une tranche a son échéance **ce mois-ci** et n’est pas encore soldée. |
| `INSOLVENT` | Insolvable | Insolvent | rouge | Inscription **non** soldée, **ou** au moins une tranche dont le mois d’échéance est **passé** reste impayée. |
| `NOT_APPLICABLE` | — / Non concerné | N/A | neutre | Pas d’inscription année, ou barème non paramétré pour le **niveau** (Q-71). |

**Q-72 — hors période des tranches** :
- **Avant** le mois de la 1ʳᵉ échéance → seul le paiement d’inscription compte (impayé → `INSOLVENT`, payé → `SOLVENT` ; pas de `PENDING` tranche).
- **Après** le mois de la dernière échéance → inscription + tranches **échues** uniquement (plus de `PENDING` « mois courant »).

Exemple : échéances 31/10, 30/11, 31/12 ; aujourd’hui = 15/11 ; T1 payée, T2 non → `PENDING` ; T1 non payée → `INSOLVENT`.

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
| `extraFees[0…M]` | embed | optionnel ; `{ index: 1..M, code?, label FR/EN, amount, dueDate }` ; M ≤ 6 (**Q-81**, examens) |
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
| `kind` | `ENROLLMENT` \| `INSTALLMENT` \| `EXTRA` \| `PENALTY` | |
| `installmentIndex` | Int? | 1..N si tranche ; 1..M si `EXTRA` |
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
- **BR-F15** (**Q-81 🟡**) Frais supplémentaires optionnels par niveau (`extraFees`) : libellés bilingues + code libre (BEPC, BAC…) ; UI propose des suggestions selon le code de niveau (FR/EN) **sans** imposer ni montant ni présence. Paiement après toutes les tranches. Solvabilité / soft-block : **même règle mois civil** que les tranches (provisoire tant que Q-81 non figée).
- **BR-F16** Modèle général `fees_schedule_defaults` (année) : inscription + N tranches. À l’enregistrement, **amorce automatique** des niveaux **sans** barème ; niveaux déjà définis **non écrasés** — sync manuelle `POST /fees/schedules/sync-defaults` (conserve `extraFees`).
- **BR-F04** L’inscription annuelle (`Enrollment`) ne suffit pas seule pour « accès école » si BR-F05 est actif : il faut aussi le **paiement d’inscription** (**Q-65**).
- **BR-F05** (**Q-65 ✅**) : si inscription non soldée **ou** statut `INSOLVENT` → bloquer notes / EDT / portail pour parent, élève, enseignant ; Direction/Admin/SG = **lecture** seule (pas de modification des notes). Message métier explicite + notifications.
- **BR-F06** (**Q-70 ✅**, **Q-72 ✅**) Solvabilité calculée dans `SolvencyCalculator` / `service/fees` uniquement (mois civil Douala) ; la liste élèves consomme `FeesApi.solvencySnapshot(...)`. Matrice § 2.
- **BR-F07** Tout `BigDecimal` stocké : `@Field(DECIMAL128)` ; comparaisons `compareTo`.
- **BR-F08** Toute requête filtre `schoolId` ; index composites commencent par `schoolId`.
- **BR-F09** Saisie / annulation des paiements (reçu banque → mise à jour solvabilité) : `DIRECTOR` / `SUPER_ADMIN`, ou `ADMINISTRATION` dont la fiche Staff porte la fonction `ACCOUNTANT`. Barèmes (montants) : Direction seule. Lecture solvabilité : admin / dir. / SG ; **parent = enfants liés uniquement** ; **enseignant : pas d’accès**.
- **BR-F10** Rappels automatiques parents (inbox) **15 / 10 / 5 / 3 / 2 / 1** jours avant l’échéance de la tranche non payée (+ jour J si activé). Paramétrable via `FeesPolicy.reminderDaysBeforeDue`. **Un seul message par compte parent**, même multi-enfants : détail nom, statut, payé / non payé, prochaine échéance.
- **BR-F11** Au passage **INSOLVENT** : notification parent avec montant de pénalité hebdo et délai max d’exclusion (si définis).
- **BR-F12** Pénalité hebdomadaire après insolvabilité : défaut **1000 XAF / semaine / enfant**, montant et devise dans `FeesPolicy` (Direction). Accrual idempotent (`fees_penalty_charges` + `fees_student_states`).
- **BR-F13** Avertissement parent **N jours** avant exclusion (`exclusionWarningDaysBefore`, défaut 7).
- **BR-F14** Après `maxInsolvencyGraceDays` (défaut 28, Direction) sans régularisation : **désactivation** de l’élève (`Student.active=false`, `inactiveReasonCode=FEES_INSOLVENCY_EXCLUSION`) + notification.
- **Q-80** Réactivation = **Direction seule** via `POST /api/v1/fees/students/{id}/reactivate` : inscription + **toutes** les tranches soldées **et** pénalités soldées (`FeePaymentKind.PENALTY`, montant exact). La réactivation générique `POST /students/{id}/reactivate` est **refusée** tant que le motif fees est posé.

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
| `POST` | `/api/v1/fees/payments` | **BR-F09** | enregistrer un paiement (`ENROLLMENT` / `INSTALLMENT` / `PENALTY`) |
| `POST` | `/api/v1/fees/payments/{id}/void` | **BR-F09** | annuler (`VOID` + motif) |
| `POST` | `/api/v1/fees/students/{id}/reactivate` | **DIRECTOR** | Q-80 : réactivation après soldes tuition + pénalités |

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
| Nav **Scolarité** (`/director/fees`) | **Direction seule** (`canManageSchedules`) |

### 6.2 Surfaces

- **Liste élèves** : colonne **Solvabilité** ; onglets Tous / Actifs / Inactifs / **Solvabilité** ; cartes stats (Total, Actifs, Inactifs, Insolvables) + ligne impayés (Inscription + N tranches via `installmentsUnpaid[]`).
- **Fiche élève** : section **Scolarité & solvabilité** (sous Photo / Tuteurs) ; héros **fond blanc** (pas de dégradé bleu).
- **Q-80 UI** : bannière exclusion ; paiement `PENALTY` (espèces, montant exact, après tuition) ; bouton **Réactiver (scolarité)** Direction uniquement quand `canReactivateAfterFees` ; réactivation générique masquée si `inactiveReasonCode=FEES_INSOLVENCY_EXCLUSION`.
- **Workspace Solvabilité** (`/director|administration/students/solvency`) : KPI montants (dû / encaissé / reste), inscription non soldée, pénalités (BR-F12), risque d’exclusion (grâce), donuts solvabilité & ponctualité, barres impayés / niveaux, **courbe** encaissements 30 j. (montant + cumul + volume) via `GET /fees/dashboard-summary`.
- Portail parent : inbox `FEES_*` ; accès notes/EDT soumis à BR-F05.

### 6.3 Hub Direction **Scolarité** (`/director/fees`)

Paramétrage **réservé à la Direction** (pas Administration, pas comptable). L’UI n’affiche pas de bandeau explicatif : le titre vient de la nav ; le détail métier est ici.

| Onglet | Contenu | Notes |
|---|---|---|
| **Barèmes** | **1)** Paramètres généraux (modèle année) puis **2)** barèmes par niveau (FR/EN) ; amorçage auto des niveaux vides ; sync manuelle si personnalisé ; frais d’examen optionnels (BEPC / Probatoire / BAC…). | Q-66…Q-68, Q-81, BR-F16 ; année verrouillée = lecture seule |
| **Politique** | Rappels avant échéance (jours paramétrables, défaut 15/10/5/3/2/1 + jour J), pénalité hebdomadaire (XAF), délai de grâce / avertissement / exclusion auto | Q-77…Q-79 ; chaque enregistrement notifie Direction + Administration |

Réactivation après exclusion (**Q-80**) : **pas** dans ce hub — sur la **fiche élève**, une fois inscription + tranches + pénalités soldées.

### 6.4 Soft-block Q-65 / BR-F05

- Critère : inscription non soldée **ou** solvabilité `INSOLVENT` (`PENDING` ne bloque pas).
- **Parent / élève / enseignant** : pas de consultation notes, listes/PDF bulletins, EDT (entries + PDF avec `forStudentId`).
- **Direction / Admin / SG** : consultation OK ; **modification** notes refusée.
- API : `GET /api/v1/fees/students/{id}/academic-access?academicYearId=` → `FeesAccessGateDto` (messages FR/EN).
- UI : bandeau `AcademicFeesSoftBlock` sur portail parent (notes, bulletins, EDT) et EDT élève.

### 6.5 VOID paiement (Q-76)

- API : `POST /api/v1/fees/payments/{id}/void` + motif (BR-F09).
- UI : workspace Solvabilité → liste « Paiements enregistrés » → **Annuler** + dialogue motif ; historique `voidedPayments` sur le compte.

## 7. Périmètre livré vs suite

| Livré / en cours | Suite |
|---|---|
| Barèmes niveau + N tranches + hub Politique | Remises, bourses |
| Paiement séquentiel + montant exact + CASH inscription | — |
| Reçu PDF inscription (en-tête école) | Reçu tranche / cahier de caisse |
| Rappels / pénalités / exclusion paramétrables | SMS / e-mail opt-in |
| BR-F05 + soft-block portail (notes / EDT / bulletins) + `GET …/academic-access` | — |
| Q-76 VOID UI (workspace Solvabilité, motif) | — |
| Q-80 réactivation Direction | — |
| Solvabilité + dashboard KPI (Q-70/Q-72 ✅) | — |
| Seed démo | Import grille réelle |

## 8. Questions

**Q-65…Q-80 ✅ figées** ; **Q-81 🟡** frais d’examen optionnels — détail `07` (matrice solvabilité Q-70/Q-72 incluse).
