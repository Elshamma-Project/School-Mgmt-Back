# 10 — Scolarité et solvabilité

> Module **`fees`** (docs/08) — **v1 livrée** (API barèmes / paiements / solvabilité / politique / job rappels-pénalités-exclusion + UI liste, fiche, workspace Solvabilité). Objectif : suivre l’**inscription annuelle** et les **tranches de scolarité**, afficher un **statut de solvabilité** dérivé, et permettre à la **direction / comptabilité** d’enregistrer les paiements.
>
> Fuseau d’affichage et de calcul des échéances : **Africa/Douala**. Montants : `BigDecimal` / `DECIMAL128`, comparaison `compareTo`.

## 1. Processus métier (proposé, à valider)

1. **Chaque année scolaire**, tout élève (nouveau, promu, redoublant) doit être **inscrit** pour l’année (`Enrollment`) **et** s’acquitter des **frais d’inscription** pour accéder à l’école (règle d’accès — Q-65).
2. La **direction** (ou l’administration, Q-66) définit, **par année et par classe** (ou par niveau — Q-67) :
   - le montant d’**inscription** ;
   - les montants des **3 tranches** de scolarité.
3. Les **3 tranches** ont pour échéance le **dernier jour calendaire** des **3 premiers mois** de la rentrée, **à partir de septembre** (proposition : mois M0 = septembre, M1 = octobre, M2 = novembre → échéances 30/09, 31/10, 30/11 — Q-68 si la rentrée ne tombe pas le 1er septembre).
4. Les **paiements** sont saisis (v1 : manuellement par admin/direction — Q-69) et rattachés à un élève + année (+ tranche ou inscription).
5. La **solvabilité** affichée est **dérivée** (non stockée) de la date du jour et des paiements enregistrés.

## 2. Statut de solvabilité (UI)

| Code | Libellé FR | Libellé EN | Couleur | Règle proposée (à confirmer Q-70) |
|---|---|---|---|---|
| `SOLVENT` | Solvable | Solvent | vert | La **tranche dont le mois civil est en cours** (parmi les 3 mois de rentrée) est **payée** ; **et** aucune tranche **échue** n’est impayée. Si hors période des 3 mois : toutes les tranches exigibles à ce jour sont payées (+ inscription si exigée). |
| `PENDING` | En attente | Pending | ambre | La tranche du **mois en cours** n’est **pas encore** payée, **mais** toutes les tranches des **mois précédents** (échues) sont payées (et l’inscription si exigée). |
| `INSOLVENT` | Insolvable | Insolvent | rouge | Au moins une tranche **échue** (mois précédent ou antérieur dans le calendrier des 3 mois) **n’est pas payée**, ou l’**inscription** obligatoire n’est pas payée alors qu’elle est exigée. |
| `NOT_APPLICABLE` | — / Non concerné | N/A | neutre | Pas d’inscription année, ou barème non paramétré pour la classe, ou année hors scolarité (Q-71). |

**Précision mois « en cours »** : mois civil `Africa/Douala` parmi {septembre, octobre, novembre} de l’année de rentrée ; avant septembre → avant ouverture des tranches (Q-72) ; après novembre → seules les impayés échus / inscription comptent (Q-70).

## 3. Modèle de données proposé (module `fees`)

Collections préfixées `fees_*` (aucune jointure vers `student_*` / `academic_*` : références par id + libellés figés).

### 3.1 `fees_tuition_schedules` — barème année / classe

| Champ | Type | Notes |
|---|---|---|
| `schoolId` | String | index composite |
| `academicYearId` | String | |
| `classId` | String | ou `levelId` si barème par niveau (Q-67) |
| `classLabel` | String | copie figée |
| `enrollmentFee` | Decimal128 | frais d’inscription |
| `installments[3]` | embed | `{ index: 1..3, amount, dueDate }` — `dueDate` calculée à la création / recalculable |
| `currency` | String | défaut `XAF` (Q-73) |
| `active` | Boolean | |

Index : `uk_school_year_class` unique `(schoolId, academicYearId, classId)`.

### 3.2 `fees_student_accounts` — compte scolarité élève / année

| Champ | Type | Notes |
|---|---|---|
| `schoolId` | String | |
| `academicYearId` | String | |
| `studentId` | String | |
| `enrollmentId` | String | lien métier année |
| `classId` | String | classe au moment du calcul / maj |
| `scheduleId` | String | barème appliqué |
| `enrollmentFeeDue` | Decimal128 | snapshot |
| `enrollmentFeePaid` | Boolean / amount | Q-74 : booléen « soldé » ou montant payé |
| `installments[]` | embed | snapshot montants + `dueDate` + `paidAt` / `paidAmount` |
| `status` | enum dérivé cache optionnel | **v1 : recalculé en service**, pas de source de vérité stockée |

Index : `uk_school_year_student` unique.

### 3.3 `fees_payments` — écritures de paiement

| Champ | Type | Notes |
|---|---|---|
| `schoolId` | String | |
| `studentAccountId` | String | |
| `kind` | `ENROLLMENT` \| `INSTALLMENT` | |
| `installmentIndex` | Int? | 1..3 si tranche |
| `amount` | Decimal128 | |
| `paidAt` | Instant | |
| `method` | enum / texte | espèces, mobile money… (Q-75) |
| `reference` | String? | reçu |
| `recordedBy` | String | `UserAccount.id` |
| `note` | String? | |

Pas de suppression physique des paiements : annulation = écriture inverse ou statut `VOID` (Q-76).

## 4. Règles métier proposées (BR-fees)

- **BR-F01** Un barème scolarité est unique par `(école, année, classe)` (ou niveau — Q-67).
- **BR-F02** Les montants sont paramétrables depuis l’UI (FR/EN), sans seuil codé en dur.
- **BR-F03** Les 3 échéances de tranche = dernier jour des mois M0, M1, M2 de la rentrée (défaut sept./oct./nov.) fuseau Douala.
- **BR-F04** L’inscription annuelle (`Enrollment`) ne suffit pas seule pour « accès école » si BR-F05 est actif : il faut aussi le **paiement d’inscription** (Q-65).
- **BR-F05** (option) Bloquer la saisie de notes / présence / portail parent tant que l’inscription n’est pas payée — **hors v1 UI** tant que Q-65 non tranché ; sinon affichage seulement.
- **BR-F06** Solvabilité calculée dans `service/fees` uniquement ; la liste élèves consomme `FeesApi.solvencyForStudents(...)`.
- **BR-F07** Tout `BigDecimal` stocké : `@Field(DECIMAL128)` ; comparaisons `compareTo`.
- **BR-F08** Toute requête filtre `schoolId` ; index composites commencent par `schoolId`.
- **BR-F09** Saisie / annulation des paiements (reçu banque → mise à jour solvabilité) : **`DIRECTOR`** / **`SUPER_ADMIN`**, ou **`ADMINISTRATION`** dont la fiche Staff porte la fonction **`ACCOUNTANT`**. Barèmes (montants) : Direction seule. Lecture solvabilité : admin / dir. / SG ; **parent = enfants liés uniquement** ; **enseignant : pas d’accès**.
- **BR-F10** Rappels automatiques parents (inbox) **15 / 10 / 5 / 3 / 2 / 1** jours avant l’échéance de la tranche non payée (+ jour J si activé). Paramétrable via `FeesPolicy.reminderDaysBeforeDue`. **Un seul message par compte parent**, même multi-enfants : détail nom, statut, payé / non payé, prochaine échéance.
- **BR-F11** Au passage **INSOLVENT** : notification parent avec montant de pénalité hebdo et délai max d’exclusion (si définis).
- **BR-F12** Pénalité hebdomadaire après insolvabilité : défaut **1000 XAF / semaine / enfant**, montant et devise dans `FeesPolicy` (Direction). Accrual idempotent (`fees_penalty_charges` + `fees_student_states`).
- **BR-F13** Avertissement parent **N jours** avant exclusion (`exclusionWarningDaysBefore`, défaut 7).
- **BR-F14** Après `maxInsolvencyGraceDays` (défaut 28, Direction) sans régularisation : **désactivation** de l’élève (`Student.active=false`, motif `FEES_INSOLVENCY_EXCLUSION`) + notification. Réactivation = action manuelle Direction/Admin (hors job).

### Fuseau et job

- Calculs et cron : **Africa/Douala**, cron défaut `0 0 7 * * *` (`beesm.fees.reminder-cron`).
- Idempotence : clé `dedupeKey` = sourceEventId inbox ; réexécution le même jour ne duplique pas.

## 5. API publique

Module `fees` :

| Méthode | Chemin | Qui | Notes |
|---|---|---|---|
| `GET` | `/api/v1/fees/access` | authentifié | `canRead`, `canManageSchedules`, `canRecordPayments`, `canManagePolicy` |
| `GET` / `PUT` | `/api/v1/fees/policy` | lecture Admin+ ; écriture **DIRECTOR** | rappels / pénalités / exclusion |
| `GET` / `PUT` | `/api/v1/fees/schedules` | lecture comptable+ ; écriture **DIRECTOR** | barèmes année / classe |
| `GET` | `/api/v1/fees/students/{studentId}/account?academicYearId=` | lecture scoped | détail + solvabilité + paiements |
| `GET` | `/api/v1/fees/solvency?academicYearId=&studentIds=` | lecture scoped | batch liste élèves (virgules) |
| `GET` | `/api/v1/fees/unpaid-summary?academicYearId=` | **staff** Admin / Dir. / SG / SuperAdmin | compteurs hub : inscription + 3 tranches impayées + total insolvables |
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
| Hub élèves — cartes impayés / insolvables | Direction / Admin (lecture summary) ; clic → workspace Solvabilité |

### 6.2 Surfaces livrées

- **Liste élèves** : colonne **Solvabilité** (vert / ambre / rouge / neutre) ; onglets Tous / Actifs / Inactifs ; cartes stats (Total, Actifs, Inactifs, Insolvables) + 2ᵉ ligne (Inscription / 1ʳᵉ / 2ᵉ / 3ᵉ tranche impayées via `unpaid-summary`).
- **Fiche élève** : section **Scolarité & solvabilité** (sous Photo / Tuteurs) — échéances, ponctualité, historique paiements ; bouton paiement si droit.
- **Workspace Solvabilité** (`/director|administration/students/solvency`) :
  1. grande barre de **recherche élève** ;
  2. sans sélection : **diagramme** répartition SOLVENT / PENDING / INSOLVENT / N/A + **barres** d’impayés par échéance ;
  3. au clic élève : détail échéances / soldes / paiements + enregistrement paiement (même dialog que la fiche).
- Direction : écrans **Barèmes** et **Politique scolarité** (jours de rappel, pénalité hebdo, délai max, exclusion).
- Portail parent : inbox rappels `FEES_*` (deep-link roadmap).
- L’**actif / inactif** reste sur les onglets liste et la fiche (indépendant de la solvabilité).

## 7. Périmètre v1 vs plus tard / idées d’amélioration

| v1 (livré) | Roadmap (bonnes pratiques) |
|---|---|
| Barèmes + 3 tranches + inscription | Remises, bourses, échelonnement custom |
| Saisie manuelle des paiements (dialog) | Mobile money / reçu PDF (sans marque tierce) |
| Rappels inbox 15/10/5/3/2/1 + insolvabilité | SMS / e-mail opt-in (canal hors inbox) |
| Pénalité 1000 XAF/sem. paramétrable | Plafond de pénalités ; remise de peine si paiement |
| Exclusion auto après grâce | Soft-block portail notes avant exclusion ; appeal workflow |
| Un message multi-enfants | Récap mensuel PDF « état de compte famille » |
| Devise XAF | Multi-devises |
| Solvabilité liste + fiche + workspace + unpaid-summary | Blocage notes / présence si insolvable (Q-65) |
| Seed démo variété solvabilité (`FeesDemoSeedRunner`) | Import cahier de caisse / grille réelle |

## 8. Questions ouvertes

Voir **Q-65 à Q-76** et **Q-77…Q-80** (rappels / pénalités) dans `07-questions-ouvertes.md`.
