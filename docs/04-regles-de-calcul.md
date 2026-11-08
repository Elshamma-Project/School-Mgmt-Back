# 04 — Règles de calcul des bulletins

> **Statut** : spécification fonctionnelle que les **services** implémentent (`GradeCalculationService`, appelé par `ReportCardService`). Il n'y a pas de « moteur » séparé : les calculs vivent dans la couche `service` (voir § 9). Ce document sert surtout à rédiger des prompts précis pour Cursor / Claude Code.
> Source : recalcul de 7 bulletins réels (voir `01-analyse-bulletins.md`) ; données de test anonymisées dans `../../../Docs/back-office/docs/fixtures`.
> Décisions de Ben prises en compte : Q-14 (arrondi mathématique, on accepte les écarts de centimes), Q-08 (« N° on roll » = effectif), Q-10 (barème clair et modifiable), Q-11 (tableau d'honneur à 12/20, paramétrable).
> Tout ce qui est paramétrable l'est dans `GradingScheme` / `ReportTemplate` (modifiable plus tard depuis l'interface, dans les deux langues). **Aucun seuil n'est codé en dur.**

## 1. Vocabulaire

| Terme | Sens |
|---|---|
| Note de séquence | Résultat final d'un élève dans une matière pour une séquence (DEV1..DEV6) : `SequenceMark`, obtenu en totalisant la fiche de notes de l'enseignant (§ 2, étape 0) |
| Moyenne de trimestre d'une matière | Moyenne des notes de séquence présentes du trimestre (2 séquences) |
| Moyenne d'une matière (annuelle) | Moyenne des moyennes de trimestre présentes |
| Total de ligne | Moyenne de la matière × coefficient |
| Groupe | Bloc de matières avec sous-total (propre à la classe) |
| Moyenne générale | Σ totaux de lignes ÷ Σ coefficients |
| Élève classé | Élève qui reçoit un rang (voir étape 7) |
| Matière comptée | Matière qui a une moyenne pour l'élève, `countsInAverage = true`, non dispensée |

## 2. Algorithme, étape par étape

Entrées : classe, période (séquence, trimestre ou année), `ClassSubject` de la classe, `SequenceMark` des élèves, `GradingScheme`.
Toutes les valeurs intermédiaires sont des `BigDecimal` (`MathContext.DECIMAL128`) **non arrondies**, sauf l'arrondi de trimestre de l'étape 1.

### Étape 0 — Note de séquence (réponse Q-02)
L'enseignant tient **une fiche par séquence et par matière/classe** (`GradeSheet`) : colonnes `Assessment` (notes, bonus, malus), valeurs `Grade`. Le service totalise chaque élève en une note de séquence (`SequenceMark`).
Formule par défaut (**hypothèse, Q-25**, `TotalizationRule`) :
`note = moyenne des colonnes d'évaluation ramenées sur 20 (pondérées par weight si WEIGHTED)` `+ somme des bonus − somme des malus`, bornée entre `minScore` (0) et `maxScore` (20) si `capAtMax`. Cas simple : une seule colonne `SEQUENCE_MARK` = la note saisie directement.
La note de séquence est arrondie à **2 décimales** (HALF_UP) et **stockée** ; c'est elle seule qui alimente le bulletin. Modification d'une `Grade` → recalcul de la `SequenceMark` de l'élève dans la même transaction.

### Étape 1 — Moyenne de trimestre d'une matière
Pour une matière et un trimestre : moyenne des notes de séquence **présentes** (statut `SCORED`). Une note manquante (`PENDING`, « - ») est **ignorée** (`MissingMarkPolicy.IGNORE`, observé) ; un zéro réel (`SCORED` avec valeur 0) est **compté**. Si aucune note : le trimestre n'a pas de moyenne pour cette matière.
Si `roundTermSubjectAverages = true` (défaut, pratique observée) : ce résultat est **arrondi à 2 décimales** avant l'étape 2.
Exemple : DEV5 = 12.75, DEV6 = 12.0 → 12.375 → 12.38 (HALF_UP).

### Étape 2 — Moyenne d'une matière
- **Bulletin de trimestre** : la moyenne de la matière = moyenne de trimestre de l'étape 1.
- **Bulletin annuel** : moyenne des moyennes de trimestre **présentes** (`SubjectYearAveragePolicy.MEAN_OF_TERM_AVERAGES`). Un trimestre sans aucune note est ignoré.
- Sans aucune note sur la période : la matière n'a pas de moyenne et **n'est pas comptée** (ni au numérateur ni au dénominateur).
Cette règle reproduit la colonne « Moy / 20 » des 98 lignes analysées (voir § 6).

### Étape 3 — Total de ligne
`total = moyenne (non arrondie) × coefficient`. Affiché avec 2 décimales (HALF_UP). Coefficient strictement positif, éventuellement décimal.

### Étape 4 — Groupe
Pour chaque groupe d'une classe : `Σ coefficients` et `Σ totaux de lignes` des matières **comptées**, `moyenne du groupe = Σ totaux ÷ Σ coefficients`. On additionne les totaux **exacts**, on arrondit une seule fois à l'affichage. Un groupe sans matière comptée n'a pas de moyenne.
> ⚠️ L'ancien logiciel calcule les groupes avec la moyenne simple de toutes les notes présentes (≠ lignes) quand des notes manquent ; nous appliquons la même règle que les lignes (Q-24). Sans note manquante les deux coïncident.

### Étape 5 — Moyenne générale
`moyenne = Σ totaux de lignes ÷ Σ coefficients` des matières comptées. `total points = Σ totaux`, `total maximum = Σ coefficients × 20`, affiché sur le bulletin de trimestre (« 420.16 / 720.0 »).
Moyennes de trimestre des « rappels » : même calcul sur les séquences du trimestre. Moyenne annuelle : `AnnualAveragePolicy.WEIGHTED_TOTAL` (défaut) = `Σ totaux annuels ÷ Σ coefficients`. Sans note manquante elle est égale à la moyenne des 3 moyennes de trimestre ; sinon elles peuvent différer (Q-24).

### Étape 6 — Matières validées et matières à améliorer
- « PASSED IN n / m SUBJECTS » : n = matières comptées dont la moyenne >= `passMark` (10, une moyenne exactement à 10 compte) ; m = matières comptées (Form 1 : 12/16 ; Form 4 SC : 12).
- « MAKE EFFORT IN » : matières comptées dont la moyenne < `effortSubjectThreshold` (hypothèse 8, observé : 7,5 et 6,0 listées, 8,5 non listée, **Q-28**). Désactivé si le seuil est vide.

### Étape 7 — Rang de l'élève
- **Élève classé** : `RankingEligibility.HAS_AVERAGE` (défaut) = il a une moyenne générale pour la période. Observé : « rang 0/0 » pour un élève sans moyenne de trimestre 1 ; la règle exacte est **Q-07** (hypothèse alternative `HAS_ALL_TERMS`).
- Rang = 1 + nombre d'élèves classés avec une moyenne **strictement supérieure** (`RankingPolicy.COMPETITION` : 1, 2, 2, 4). **Proposition pour Q-06** : comparer les moyennes **exactes** (non arrondies) ; deux élèves de même moyenne exacte partagent le rang ; le suivant saute (4). Le mode `DENSE` (1, 2, 2, 3) existe si l'école le préfère.
- `effectif` = élèves inscrits dans la classe (c'est le « N° ON ROLL », Q-08) ; `rankedCount` = élèves classés. Rang affiché `rang / rankedCount` (« 10 / 14 »). Élève non classé : rang vide, `rankStatus = NOT_RANKED`.

### Étape 7 bis — Rang et statistiques par matière (bulletin de trimestre seulement)
Pour chaque matière : rang de l'élève parmi les élèves de la classe qui ont une moyenne dans la matière (« 6th » ; ordinal anglais : 1st, 2nd, 3rd, 4th... ; l'ancien logiciel imprime « 13rd » : à ne pas reproduire), moyenne de la classe dans la matière, nombre d'élèves dont la moyenne >= `passMark` (« Av >= 10 »).

### Étape 8 — Profil de la classe
Calculé sur les élèves **classés** : nombre avec moyenne >= seuil, moyenne de classe (moyenne des moyennes générales), `% réussite = nombre >= seuil ÷ classés × 100` (2 décimales), moyenne max, moyenne min.
Seuil : `ReportTemplate.classProfileThreshold` sinon `GradingScheme.classSuccessThreshold` : **12** sur les bulletins annuels, **10** sur le bulletin de trimestre de Form 1 (Q-29).
Aucun élève classé : tout à 0,00 (comportement observé, repris pour la compatibilité d'affichage ; en base les valeurs sont nulles).

### Étape 9 — Appréciations
- **Par matière** : `GradingScheme.appreciationScales`, échelle choisie par `ReportTemplate.appreciationScaleCode`. Borne basse **incluse**, haute exclue.
  - `LETTER` (annuel) : A >= 16 · B+ [14, 16[ · B [12, 14[ · C < 12.
  - `COMPETENCY` (trimestre Form 1) : NA < 10 · ECA [10, 14[ · A >= 14 · A+ au-dessus d'un seuil non observé (**Q-27**). Légende : NA = non acquis, ECA = en cours d'acquisition, A = acquis, A+ = parfaitement acquis.
- **Mention générale** : `GradingScheme.overallMentionBands`, barème proposé dans `01-analyse-bulletins.md` § 6 (< 9 INSUFFISANT/WEAK · [9,10[ MÉDIOCRE · [10,12[ PASSABLE/FAIR · [12,14[ ASSEZ BON · [14,16[ BON · [16,18[ TRÈS BON · >= 18 EXCELLENT).
- **Tableau d'honneur** (réponse Q-11) : moyenne >= `honourRollMinAverage` (12). Hypothèse Q-29 : `honourRollBlockedBySanction = true` (une élève à 12,30 n'a pas le tableau d'honneur avec « Avertissement conduite : Oui »). Encouragements et félicitations : seuils à définir ; en attendant, saisie manuelle (`distinctionRules` vide).
- **Avertissements et blâmes** : saisis (`StudentPeriodReview`), jamais calculés pour l'instant.

## 3. Arrondi

**Décision (Q-14)** : on ne tronque pas, on arrondit mathématiquement (`HALF_UP`, ex. 8,555 → 8,56) et **on accepte les écarts de centimes** avec les anciens bulletins.
Règles :
1. Aucun arrondi sur les valeurs intermédiaires, **sauf** la moyenne de trimestre d'une matière (étape 1), arrondie à 2 décimales parce que c'est la pratique de l'école : c'est ce qui explique les écarts de centimes dans **toutes** les lignes analysées (voir § 6).
2. Tout le reste est arrondi une seule fois, à l'affichage, avec `decimals` du barème (2).
3. Les comparaisons de seuils (>= 10, >= 12, bornes d'appréciation) se font sur la **valeur non arrondie** (une moyenne de 11,996 n'est pas « >= 12 »). Le rang aussi.
4. `RoundingPolicy.HALF_EVEN` (arrondi au pair, 8,625 → 8,62) existe pour comparer avec l'ancien logiciel : avec lui les 98 lignes se retrouvent exactement ; avec HALF_UP un seul cas diffère d'un centime (2NDE C, Mathématiques : 39,80 au lieu de 39,78, parce que 8,625 s'arrondit à 8,63 au lieu de 8,62).
5. Écarts attendus à signaler à l'école (inoffensifs) : un total de ligne ou une moyenne de groupe qui diffèrent de 0,01 de l'ancien bulletin.

## 4. Statuts de notes

| Statut | Sens | Dans la moyenne |
|---|---|---|
| `SCORED` | note saisie (0 compris) | oui |
| `PENDING` | pas encore saisie (« - ») | non (ignorée) ; **ne jamais écrire 0 à la place** |
| `ABSENT` | absent non justifié | selon `AbsencePolicy` (EXCLUDE par défaut ; Q-12) |
| `ABSENT_EXCUSED` | absent justifié | exclu |
| `EXEMPTED` | dispensé | exclu et la matière sort de la moyenne de l'élève (Q-13) |

Le cas « DEV3 à 0.0 pour toute une classe » (Form 4 ARTS) est exactement ce que cette distinction évite : à l'ouverture d'une fiche, toutes les notes sont `PENDING`, pas 0.

## 5. Exemples pas à pas

**Ligne avec notes manquantes** (2NDE ALL, Education artistique, coefficient 1) : DEV1..DEV6 = 8,6 · 11,0 · 4,0 · 0,0 · 4,0 · « - ».
T1 = (8,6 + 11) ÷ 2 = 9,80 ; T2 = (4 + 0) ÷ 2 = 2,00 ; T3 = 4,0 (DEV6 absente, ignorée). Moyenne = (9,80 + 2,00 + 4,00) ÷ 3 = 5,2667 ; total = 5,27 ✓ (imprimé 5,27).
Le zéro de DEV4 est compté, le « - » de DEV6 est ignoré.

**Arrondi de trimestre** (FORM 4 SC, French, coefficient 5) : T1 = 15,25 ; T2 = 15,25 ; T3 = (12,75 + 12) ÷ 2 = 12,375 → **12,38**. Moyenne = (15,25 + 15,25 + 12,38) ÷ 3 = 14,2933 ; total = 71,47 ✓ (sans l'arrondi de trimestre : 71,46).

**Moyenne générale** (FORM 1, trimestre 1) : Σ totaux = 420,16 ; Σ coefficients = 36 ; moyenne = 11,67 ✓ ; « 420.16 / 720.0 » ✓. 12 matières sur 16 à >= 10.

## 6. Critères d'acceptation (tests)

| Fixture | Ce qui doit être retrouvé |
|---|---|
| `bulletin-6eme-fr`, `bulletin-form3-en` | lignes (avec arrondi de trimestre : 37/37), groupes, moyenne générale, trimestres, % réussite |
| `bulletin-form4-sc-en` | tout, sans note manquante : 14/14 lignes, 4/4 groupes, moyenne 11,67, trimestres 11,55 / 12,11 / 11,35 |
| `bulletin-2nde-c-fr` | 16/16 lignes (tolérance 0,01 sur MATHEMATIQUES en HALF_UP) ; groupes **selon notre règle** (pas les imprimés : écart attendu, voir `knownDeviations` à ajouter par le test) |
| `bulletin-2nde-all-fr` | 16/16 lignes ; notes manquantes (DEV5, DEV6) ignorées ; classe de 2 élèves |
| `bulletin-form4-arts-en` | 15/15 lignes ; zéros réels comptés ; élève non classé (rang vide, profil de classe nul) |
| `bulletin-form1-term1-en` | Σ totaux 420,16 / 720,0 ; moyenne 11,67 ; 12/16 matières validées ; matières à améliorer (7,5 et 6,0) ; appréciations par compétences |

Tests de cas limites à écrire : note manquante, absent, dispensé, ex aequo, élève non classé, coefficient décimal, matière sans aucune note, classe d'un seul élève, borne exacte d'une tranche (12,00 → B), résultat identique quel que soit l'ordre des lignes, séquence modifiée après génération (nouvelle révision), `HALF_UP` contre `HALF_EVEN`.

## 7. Régularité de l'ancien logiciel (pour mémoire)

Trois méthodes coexistent (détail et chiffres : `01-analyse-bulletins.md` § 7) : lignes = moyenne des trimestres arrondis ; groupes = moyenne de toutes les notes présentes ; moyenne annuelle = moyenne des trimestres imprimés, avec des trimestres 3 qui semblent calculés avant la dernière saisie. **Ne pas reproduire ces incohérences** ; les présenter à l'école (Q-24) pour valider la règle unique ci-dessus.

## 8. Reproductibilité

Chaque `ReportCard` enregistre `gradingSchemeId`, `templateCode` et `calculationVersion` (ex. « 1.0 »). Un bulletin déjà généré ne se recalcule jamais : une correction de note produit une nouvelle révision (BR-31).

## 9. Où vivent les calculs : dans la couche `service`

Les calculs s'exécutent dans les services du module concerné (le calcul des bulletins dans `service/reporting`, la totalisation des fiches dans `service/grading`) :

| Service | Responsabilité |
|---|---|
| `service/grading/SequenceMarkService` | étape 0 : totalise une fiche, écrit `SequenceMark`, trace l'audit |
| `service/reporting/GradeCalculationService` | étapes 1 à 9 pour une classe et une période (lecture des `SequenceMark` via l'interface publique du module grading, voir `08-architecture-modulaire.md`) |
| `service/reporting/ReportCardService` | appelle le calcul, construit et enregistre les `ReportCard` figés, révisions, publication, PDF |

Recommandation de structure : l'arithmétique (moyennes, arrondis, rang, profil) reste dans des méthodes privées ou `internal` du service qui n'accèdent pas à la base, afin de les tester par des tests unitaires avec `../../../Docs/back-office/docs/fixtures` sans lancer Mongo. Cela ne change pas l'endroit où le calcul s'exécute : c'est le service.

## 10. Fiche de report de notes (GradeReportSheet)

Document **officiel trimestriel** distinct de la `GradeSheet` (fichier de travail avec bonus/malus). Une fiche = `ClassSubject` × trimestre ; deux blocs (séquence 1 et 2). Calculs dans `ReportSheetCalculator` / `GradeReportSheetService` (sans accès base pour l'arithmétique).

| Calcul | Règle |
|---|---|
| `mark` | `SequenceMark.value` de la séquence (jamais les bonus/malus bruts) ; `PENDING` → cellule vide, **jamais 0** |
| `weightedMark` | `mark × coefficient` (coefficient de `ClassSubject`), 2 décimales HALF_UP |
| `rank` | Parmi les élèves **SCORED** de la matière pour cette séquence ; moyenne **exacte** ; `RankingPolicy.COMPETITION` → 1, 2, 2, 4 |
| `appreciation` (App) | Échelle du barème (`GradingScheme.reportSheetAppreciationScaleCode`, défaut `LETTER`, Q-10 / Q-36) ; `PENDING` → vide ; override manuel possible (`appreciationOverride`) |
| Compteurs | `headcount` = inscrits `ENROLLED` ; `boysCount` / `girlsCount` selon `Student.sex` ; si `boys + girls ≠ headcount` → `sexCountWarning` |
| Délai | Alerte si dépôt > `reportSheetDeadlineHours` (72) après `examDate` ; bloquant seulement si `reportSheetDeadlineBlocking` (Q-37 : **non**) |
| Pages PDF | `reportSheetMaxRowsPerPage` (45) ; au-delà 2e page OK (Q-35) |

### Exemple chiffré anonymisé (fixture `report-sheet-6eme-t1.json`)

Coefficient 2, échelle LETTER. Séquence 1 :

| Élève | Comp/20 | N×Coef | Rang | App |
|---|---|---|---|---|
| Élève 01 | 15,00 | 30,00 | 1 | B+ |
| Élève 02 | 14,00 | 28,00 | 2 | B+ |
| Élève 03 | 14,00 | 28,00 | 2 | B+ |
| Élève 04 | 10,00 | 20,00 | 4 | C |
| Élève 05 | — (PENDING) | — | — | — |
| Élève 06 | 12,00 | 24,00 | 5 | B |

Élève 05 sans sexe → effectif 6, G 3 + F 2, avertissement « sexe manquant ». Version **vierge** (`blank=true`) : mêmes noms / N° / sexe, cellules Comp/N×Coef/Rang/App vides pour saisie manuscrite (Q-36).
