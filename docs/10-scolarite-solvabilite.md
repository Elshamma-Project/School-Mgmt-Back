# 10 — Scolarité et solvabilité

> Module **`fees`** (docs/08) — **v1 partiellement livrée** (API barèmes / paiements / solvabilité + colonne liste élèves). Objectif : suivre l’**inscription annuelle** et les **tranches de scolarité**, puis afficher un **statut de solvabilité** sur la liste élèves (remplace la colonne « Statut actif/inactif » côté UI liste — l’actif/inactif reste sur la fiche / onglets).
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

## 5. API publique (esquisse)

Module `fees` :

- `PUT/GET /api/v1/fees/schedules` — barèmes par année/classe (DIRECTOR / ADMINISTRATION).
- `GET /api/v1/fees/students/{studentId}/account?academicYearId=` — détail compte + solvabilité.
- `POST /api/v1/fees/payments` — enregistrer un paiement.
- `FeesApi.solvencySnapshot(schoolId, academicYearId, studentIds): Map<studentId, SolvencyDto>` — pour la liste élèves / portal.

Événements (outbox) :

- `fees.payment.recorded` → notification parent (plus tard).
- `academic.class.changed` / `student.enrollment.*` → rattacher ou invalider le compte scolarité (idempotent).

## 6. UI

- Liste élèves : colonne **Solvabilité** (vert / ambre / rouge / neutre) à la place de **Statut actif**.
- Fiche élève : section Scolarité (barème, échéances, paiements, bouton « Enregistrer un paiement »).
- Administration / Direction : écran **Barèmes de scolarité** (par année, grille des classes).
- L’**actif/inactif** de la fiche `Student` reste ailleurs (filtre onglet Actifs / Inactifs, fiche détail).

## 7. Périmètre v1 vs plus tard

| v1 (après réponses Q-65…Q-76) | Plus tard |
|---|---|
| Barèmes par classe + 3 tranches + inscription | Remises, bourses, échelonnement custom |
| Saisie manuelle des paiements | Mobile money / reçu PDF / USSD (**interdit** marque tierce sur docs) |
| Solvabilité sur liste + fiche | Blocage notes / portail si insolvable |
| Devise XAF | Multi-devises |

## 8. Questions ouvertes

Voir **Q-65 à Q-76** dans `07-questions-ouvertes.md`.
