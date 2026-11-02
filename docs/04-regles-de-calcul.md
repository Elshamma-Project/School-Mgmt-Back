# 04 — Règles de calcul des bulletins

> Toutes les règles ci-dessous sont **retrouvées par recalcul** des deux bulletins (6ème FR, Form 3 EN) puis confirmées.
> Ce qui est une hypothèse est marqué **[à confirmer]** et renvoie à `07-questions-ouvertes.md`.
> Les jeux de test sont dans `docs/fixtures/` (données anonymisées + valeurs imprimées par l'ancien logiciel).

## 1. Vocabulaire du calcul

| Terme | Définition |
|---|---|
| Note de séquence (`DEVn`) | Note d'un élève dans une matière pour la séquence n (sur 20) |
| Moyenne de matière | Moyenne des notes de séquence de la période |
| Total de ligne | Moyenne de matière × coefficient |
| Groupe | Bloc de matières avec sous-total |
| Moyenne générale | Σ totaux de toutes les matières comptées ÷ Σ coefficients de ces matières |
| Période | Séquence (n = 1..6), trimestre (n = 1..3) ou année |

## 2. Algorithme, étape par étape

### Étape 0 — Note de séquence
Pour un élève, une matière (`ClassSubject`) et une séquence :
- s'il n'y a qu'une évaluation (type `SEQUENCE_MARK`) : la note ramenée sur 20 : `note × 20 ÷ maxScore` ;
- sinon : **moyenne pondérée** des évaluations de la séquence, chacune ramenée sur 20, pondérée par `Assessment.weight` **[à confirmer, Q-02]**.

Seules les notes `SCORED` entrent en compte ; le traitement des autres statuts dépend du barème (§ 4).

### Étape 1 — Moyenne de matière
```
moyenneMatière(période) = Σ notesDeSéquence(période) ÷ nombreDeSéquencesNotées(période)
```
- Annuelle : 6 séquences (DEV1..DEV6). Trimestre 1 : DEV1 et DEV2 ; T2 : DEV3 et DEV4 ; T3 : DEV5 et DEV6.
- Si une séquence n'a pas de note : `missingMarkPolicy = IGNORE` (on divise par le nombre de séquences notées) ou `COUNT_AS_ZERO`.
- Si aucune séquence n'est notée pour la matière : la matière est **exclue** du total et des coefficients **[à confirmer, Q-12]**.

### Étape 2 — Total de ligne
`total = moyenneMatière × coefficient` (avec la moyenne **non arrondie**, arrondi du résultat à 2 décimales à l'affichage).

### Étape 3 — Groupe
```
Σcoefficients(groupe) = somme des coefficients des matières comptées
Σtotaux(groupe)       = somme des totaux EXACTS (non arrondis) des lignes, arrondie une seule fois
moyenne(groupe)       = Σtotaux ÷ Σcoefficients
```
⚠️ Observé : c'est la somme des totaux **exacts** et non celle des totaux de ligne arrondis (Form 3, groupe sciences : somme des lignes arrondies = 117,01, total imprimé = 117,00).

### Étape 4 — Moyenne générale
```
moyenneGénérale = Σtotaux(tous groupes) ÷ Σcoefficients(tous groupes)
```
Seules les matières avec `countsInAverage = true` et une moyenne calculable comptent.

### Étape 5 — Moyennes de trimestre et moyenne annuelle
- La moyenne du trimestre k se calcule avec la même formule, sur les deux séquences du trimestre.
- **Observé et prouvé** : la moyenne annuelle est égale à la fois à Σtotaux ÷ Σcoefficients sur les 6 séquences **et** à la moyenne des trois moyennes de trimestre (8,8108 dans les deux cas pour la 6ème ; 11,6453 / 11,6452 pour la Form 3). Les deux méthodes ne divergent que si des notes manquent. **Retenue : Σtotaux ÷ Σcoefficients sur les séquences notées** **[à confirmer si des notes manquent, Q-12]**.

### Étape 6 — Rang
- Classement décroissant sur la moyenne **non arrondie** de la période, parmi les élèves *classés* (moyenne calculable).
- `rank` ∈ [1, rankedCount]. `effectif` = nombre d'inscrits `ENROLLED`, `rankedCount` = nombre de classés (≤ effectif, Q-07).
- Ex aequo : `RankingPolicy.COMPETITION` par défaut (1, 2, 2, 4) **[à confirmer, Q-06]**. Départage sur la moyenne arrondie ou sur la moyenne exacte : **[à confirmer, Q-06]**.
- Le rang de chaque trimestre est calculé de la même façon sur la moyenne du trimestre.

### Étape 7 — Profil de la classe
Sur les élèves classés de la classe, pour la période :
```
countAtOrAboveThreshold = nombre d'élèves dont moyenne ≥ classSuccessThreshold (12)
classAverage            = moyenne des moyennes des élèves classés
successRate             = countAtOrAboveThreshold ÷ rankedCount × 100
highestAverage / lowestAverage = max / min des moyennes
```
Vérifié : 5/12 = 41,67 % ; 8/17 = 47,06 %.

### Étape 8 — Appréciation par matière
Selon la moyenne de matière, première tranche telle que `minInclusive ≤ moyenne` (tranches triées de la plus haute à la plus basse) :

| Tranche | Moyenne de matière | Observations dans les bulletins |
|---|---|---|
| **A** | ≥ 16 | 16,00 → A |
| **B+** | ≥ 14 et < 16 | 14,00 → B+ ; 14,08, 14,20 → B+ |
| **B** | ≥ 12 et < 14 | 12,00 ; 12,17 ; 12,54 ; 13,00 → B |
| **C** | < 12 | de 5,67 à 11,94 → C |

La borne basse est **incluse** (12,00 → B ; 14,00 → B+). On n'a observé aucune lettre sous C, alors que des notes très basses existent (5,67) : soit C couvre tout, soit il existe d'autres tranches non rencontrées **[à confirmer, Q-10]**.

### Étape 9 — Mention générale et matières validées
- **Matières validées** : nombre de matières dont la moyenne ≥ `passMark` (10). Vérifié : 15 sur 18 en Form 3.
- **Mention générale** (« TRAVAIL INSUFFISANT » à 8,81 ; « FAIR » à 11,65) : seuils inconnus, `GradingScheme.overallMentionBands` à renseigner **[Q-09]**.

## 3. Arrondi : le point sensible

L'ancien logiciel arrondit à 2 décimales **en double précision**. Cela change le résultat quand la valeur tombe pile sur un demi-centième :

| Cas | Valeur exacte | Imprimé par l'école | `HALF_UP` décimal exact |
|---|---|---|---|
| 6ème, Expression écrite (moyenne) | 8,555 | **8,55** | 8,56 |
| 6ème, Langues et cultures nationales (moyenne) | 10,055 | **10,05** | 10,06 |
| Form 3, Logic (total de ligne) | 21,2767 | **21,27** | 21,28 *(écart non expliqué)* |

Sur 37 lignes, 2 diffèrent d'un centième selon la méthode, et 1 reste inexpliquée.

**Deux politiques sont donc prévues dans `GradingScheme.roundingPolicy`** :
- `HALF_UP` : arrondi décimal exact. Correct mathématiquement, recommandé en production.
- `LEGACY_DOUBLE` : reproduit l'ancien logiciel (calcul en `Double`, arrondi `Math.round(x * 100) / 100.0`). À utiliser pour la **démo de rejeu** si l'objectif est « zéro écart » avec les bulletins déjà imprimés.

Règles d'implémentation :
1. Calculer en `BigDecimal` avec une précision large (`MathContext.DECIMAL128`), **sans arrondir les valeurs intermédiaires**.
2. N'arrondir qu'au moment de produire une valeur affichée ou stockée dans `ReportCard` (moyenne, total, rang calculé sur la valeur non arrondie).
3. Ne jamais comparer des `BigDecimal` avec `equals` (l'échelle compte : 14.00 ≠ 14.0) : utiliser `compareTo`.
4. Pour `LEGACY_DOUBLE` : passer par `toDouble()` uniquement à l'étape d'arrondi final.

## 4. Statuts de notes

| `GradeStatus` | Valeur | Traitement par défaut |
|---|---|---|
| `SCORED` | oui | compte |
| `PENDING` | non | `missingMarkPolicy` (IGNORE) |
| `ABSENT` | non | `absencePolicy` (EXCLUDE ; ou COUNT_AS_ZERO) |
| `ABSENT_EXCUSED` | non | exclu |
| `EXEMPTED` | non | exclu, matière non comptée pour l'élève (Q-13) |

## 5. Exemple pas à pas (6ème francophone, anonymisé)

**Ligne ANGLAIS** : notes 6,5 · 8,0 · 6,0 · 8,5 · 9,0 · 7,0
- moyenne = 45 ÷ 6 = **7,50** ; coefficient 3 ; total = **22,5** ; appréciation **C**.

**Groupe LANGUES ET LITTÉRATURES** : 8 matières, Σcoefficients = 13, Σtotaux = 115,6967 → **115,70** ; moyenne = 115,6967 ÷ 13 = **8,90**.

**Moyenne générale** : (115,6967 + 125,4167 + 58,455) ÷ (13 + 16 + 5) = 299,5684 ÷ 34 = 8,8108 → **8,81**.

**Moyenne du trimestre 1** (séquences 1-2) : 9,3132 → **9,31**. Trimestre 2 : 9,2810 → 9,28. Trimestre 3 : 7,8382 → 7,84. Moyenne des trois = 8,8108 ✓.

**Rang** : dernière des 12 élèves classés → 12 / 12 (la classe en compte 13).

## 6. Critères d'acceptation (tests)

À automatiser avec `docs/fixtures/*.json` (JUnit 5) :

| Test | Tolérance |
|---|---|
| Moyenne de chaque matière = valeur imprimée | 0 avec `LEGACY_DOUBLE` ; ±0,01 avec `HALF_UP` |
| Total de chaque ligne | ±0,01 (anomalie LOGIC connue, listée dans `knownDeviations`) |
| Total et moyenne de chaque groupe | 0 |
| Moyenne générale | 0 |
| Moyennes de trimestre | 0 |
| Appréciation de chaque ligne | exacte |
| `% Réussite`, nombre ≥ 12 (nécessite les autres élèves : non fournis dans les fixtures) | à ajouter avec un bulletin de classe complet |
| Rang | à tester avec une classe complète fabriquée |

Tests supplémentaires à écrire : note manquante, absent, dispensé, ex aequo, élève non classé, coefficient décimal, matière sans aucune note, classe d'un seul élève, borne exacte d'une tranche (12,00 → B), résultat identique quel que soit l'ordre des lignes.

## 7. Où vivent les calculs : dans la couche `service`

Les calculs sont réalisés dans les services (`GradeCalculationService`, utilisé par `ReportCardService`). Ce document est la **spécification** que ces services implémentent ; il n'y a pas de module « moteur » à part.

Découpage conseillé (simple, sans sur-ingénierie) :
- `GradeCalculationService.calculateClass(classId, period)` : charge `ClassSubject`, `Grade`, `Enrollment` et le `GradingScheme`, applique les étapes 0 à 9 de ce document, retourne un résultat par élève (lignes, groupes, moyenne, rang) et le profil de classe.
- `ReportCardService` : appelle ce service, puis construit et enregistre les `ReportCard` (photographie figée).
- Les méthodes de calcul arithmétique (moyenne de matière, total, arrondi, rang) restent des fonctions privées ou `internal` du service, sans accès base : on peut ainsi les tester par des tests unitaires simples avec les fixtures, sans Mongo.
- Le service lit les règles dans `GradingScheme` (arrondi, seuils, politiques) ; rien de ces règles n'est écrit en dur.
