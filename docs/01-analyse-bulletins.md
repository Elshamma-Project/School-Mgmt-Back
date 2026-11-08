# 01 — Analyse des bulletins

> Sources : **7 bulletins 2025-2026** fournis par l'école et **l'emploi du temps 2026-2027** (12 pages) :
> 6ème (FR, annuel) · Form 3 (EN, annuel) · 2NDE C (FR, annuel) · 2NDE ALL (FR, annuel) · FORM 4 SC (EN, annuel) · FORM 4 ARTS (EN, annuel) · **FORM 1 (EN, 1er trimestre)**.
> Tous les bulletins sont en **une seule page A4 portrait** ; l'emploi du temps est en **une page A4 paysage par classe**.
> Élèves et enseignants sont anonymisés ici et dans `../../../Docs/back-office/docs/fixtures` (codes T1, TT01..., ELEVE-FR-01...). Le nom de l'établissement n'est pas utilisé dans ces documents.
>
> Statut : analyse vérifiée par recalcul sur les 7 bulletins (voir `04-regles-de-calcul.md`). Les points non tranchés sont dans `07-questions-ouvertes.md` (Q-xx).

## 1. Ce qu'il faut retenir (résumé)

1. **Une année = 6 séquences (DEV1 à DEV6), 3 trimestres de 2 séquences** (DEV1-2 = T1, DEV3-4 = T2, DEV5-6 = T3). Le bulletin de trimestre affiche les 2 séquences du trimestre sous le nom « Eval1 / Eval2 ».
2. **Il existe au moins DEUX gabarits de bulletin** (réponse Q-03 : le bulletin de trimestre existe) : l'**annuel** (6 colonnes DEV, appréciation par lettres) et le **trimestriel** de Form 1 (2 colonnes, compétence visée par matière, rang et moyenne de classe par matière, appréciation par compétences NA / ECA / A / A+). Il faut donc des **gabarits configurables** (`ReportTemplate`), pas un bulletin codé en dur.
3. **Les groupes de matières et les coefficients changent par classe** : en 2NDE ALL, Histoire, Géographie, Philosophie et ECM sont dans « Langues et littératures » ; en 2NDE C les mathématiques ont le coefficient 5, en 2NDE ALL le coefficient 2. Le regroupement et le coefficient sont donc portés par `ClassSubject`.
4. **Un enseignant est lié à des couples (classe, matière)** : plusieurs matières, plusieurs classes, plusieurs sections (exemples : la même personne enseigne Géographie, Chimie et Aptitude de vie en 2NDE C ; Economics et Commerce en Form 4 ARTS). Le chef d'établissement enseigne aussi une matière.
5. **Effectif ≠ élèves classés** et un élève peut ne pas être classé du tout (« rang 0/0 », profil de classe à zéro).
6. **Une note absente (« - ») n'est pas un zéro** : l'ancien logiciel exclut le « - » du calcul et compte un « 0.0 » comme une vraie note. Cas extrême observé (Form 4 ARTS) : toute une séquence à 0.0 pour un élève arrivé en cours d'année : probablement des notes non saisies enregistrées comme zéro. Le nouveau logiciel doit distinguer les deux (`GradeStatus`).
7. **Le bulletin mélange trois sortes d'informations** : calculé (notes), saisi (absences, sanctions, décisions), configuration (en-tête, textes). Le modèle les sépare.
8. **L'ancien logiciel n'est pas cohérent avec lui-même** quand il manque des notes (§ 7) : ses lignes, ses totaux de groupe et sa moyenne annuelle utilisent trois méthodes différentes. Le nouveau logiciel applique **une seule règle**, documentée dans `04-regles-de-calcul.md`. Les bulletins avec notes manquantes ne se retrouveront donc pas au centime près.
9. **Les libellés mélangent les langues** : « TOTAL ARTS AND NATIONAL CULTURE » sur un bulletin français, « TRAVAIL PASSABLE » sur un bulletin anglais, compétences en français dans un bulletin anglais. Dans le nouveau logiciel tous les libellés (groupes, mentions, légendes) existent dans les deux langues (`LocalizedText`).
10. **Les bulletins portent « Powered by SKYSOFT TECHNOLOGY SARL »**, l'application Easy School Mobile et le code USSD `*141*05#`. Réponse Q-19 : ces logiciels sont bien ceux de l'école, mais l'ancien prestataire n'a plus de contrat avec elle : l'école veut changer de prestataire. Ne reprendre ni sa marque ni ses services.

## 2. Deux gabarits, une même structure

| | Bulletin annuel | Bulletin de trimestre (Form 1, T1) |
|---|---|---|
| Titre | BULLETIN ANNUEL DE NOTES / ANNUAL REPORT CARD | REPORT CARD / FIRST TERM |
| Colonnes de notes | DEV1 à DEV6 | Eval1, Eval2 |
| Sous le nom de la matière | enseignant | enseignant + **compétence visée** (texte) |
| Colonnes de droite | Moy/20, Coef., Total, Appréciation (lettre) | Av/20, Coef., Av×Coef, **Pos.** (rang dans la matière), **Class Average**, **Av >= 10** (nb d'élèves), Appréciation (compétence) |
| Échelle d'appréciation | A, B+, B, C | A+, A, ECA, NA (« Legende » en pied) |
| Total général | non | « 420.16 / 720.0 » (points / (coef × 20)) |
| Profil de classe | « Moyenne >= 12 » | « Average >= 10 » |
| Rappels de moyennes | 3 trimestres | une récapitulation vide (TERM / POS. / AV.) |
| Décision | (vide) | « PASSED IN 12/16 SUBJECTS » + « MAKE EFFORT IN : LIFE APT., MATHEMATICS » |
| Conduite | heures d'absences, retards | heures d'absences, « Absenteisme : 0 Days » |
| Date | 26 mai 2026 | 17 décembre 2025 |

Ce qui est commun (en-tête bilingue, identité, groupes avec sous-totaux, résultat de l'élève, profil de classe, travail, conduite, signatures, code-barres à 13 chiffres) est modélisé une fois. Ce qui diffère est décrit par `ReportTemplate` (interrupteurs : `showCompetency`, `showSubjectRank`, `showSubjectClassAverage`, `showSubjectPassedCount`, `appreciationScaleCode`, `sequenceColumns`, seuil du profil de classe).

## 3. Décomposition du bulletin annuel en zones

```
┌─────────────────────────────────────────────────────────────────────┐
│ Z1  En-tête officiel bilingue + logo + année scolaire + titre        │
├─────────────────────────────────────────────────────────────────────┤
│ Z2  Identité de l'élève et de la classe            [photo : EN seul.]│
├─────────────────────────────────────────────────────────────────────┤
│ Z3  Tableau des matières                                              │
│     Discipline (+ enseignant) | DEV1..DEV6 | Moy/20 | Coef | Total | App.│
│     ┌ Z4 ligne de sous-total de groupe : Σcoef | Σtotal | Moyenne ┐   │
├─────────────────────────────────────────────────────────────────────┤
│ Z5 Résultat annuel │ Z6 Rappels    │ Z7 Profil de │ Z8 Travail │ Z9 Conduite│
│ (moy., rang,       │ moyennes      │ la classe    │ de l'élève │ de l'élève │
│  mention)          │ (T1,T2,T3)    │              │            │            │
├─────────────────────────────────────────────────────────────────────┤
│ Z10 Avis parents / Visa parent / Décision du conseil / Visa chef /   │
│     lieu-date / code-barres / horodatage / mention « sans rature »   │
└─────────────────────────────────────────────────────────────────────┘
```

### Z1 — En-tête
Deux colonnes miroir (français à gauche, anglais à droite) : République du Cameroun / Paix-Travail-Patrie, Ministère des Enseignements Secondaires, Délégation régionale, Délégation départementale, nom de l'établissement, B.P. et téléphones, logo au centre, « ANNÉE SCOLAIRE 2025-2026 », titre « BULLETIN ANNUEL DE NOTES » / « ANNUAL REPORT CARD ».
→ `School` (`officialHeaders`, `contact`, `logoFileId`). L'en-tête est **identique dans les deux sections** (toujours bilingue) ; seul le titre et le tableau suivent la langue de la classe. Le ministère change avec le cycle (secondaire : MINESEC ; maternelle et primaire : autre ministère, à vérifier) d'où un en-tête **par cycle**.

### Z2 — Identité

| Libellé FR | Libellé EN | Exemple (anonymisé) | Champ du modèle |
|---|---|---|---|
| NOMS ET PRÉNOMS | FULL NAMES | « NOM PRÉNOMS » (une seule cellule) | `Student.lastName` + `firstName` |
| CLASSE | CLASS | 6IEME / FORM 3 / 2NDE C / 2NDE ALL / FORM 4 ARTS / FORM 4 SC | `SchoolClass.name` |
| MATRICULE | MATRICULE NUMBER / REGISTRATION NUMBER | 9 chiffres (« 250369841 ») ou alphanumérique (« 25F4A327 ») | `Student.matricule` |
| SEXE | SEX | FEMININ / MALE | `Student.sex` |
| DATE ET LIEU DE NAISSANCE | DATE OF BIRTH | « NÉ(E) LE jj/mm/aaaa à LIEU » / « jj/mm/aaaa at LIEU » | `Student.dateOfBirth`, `placeOfBirth` |
| REDOUBLANT | REPEATER | Non | `Enrollment.repeater` (propre à l'année) |
| PROFESSEUR PRINCIPAL | CLASS MASTER/MISTRESS | un enseignant | `SchoolClass.mainTeacherId` |
| EFFECTIF | N° ON ROLL | 13 / 18 / 2 / 14 | `ClassSnapshot.effectif` : réponse Q-08, c'est l'**effectif de la classe** (vérifié : 14 sur le bulletin Form 1 avec rang 10/14) |
| (photo) | (photo) | présente seulement sur le bulletin EN | `Student.photoFileId` |

### Z3 — Tableau des matières
Colonnes : **Discipline** (nom de la matière en gras, **nom de l'enseignant en petit dessous**), **DEV1 à DEV6**, **Moy / 20**, **Coef.**, **Total**, **Appréciations**.
Les notes DEV sont sur 20, avec 1 ou 2 décimales (11.33, 8.66, 6.67, 15.3...). Elles sont colorées (bleu, rouge, noir) : la signification n'est pas établie (**Q-15**), probablement purement visuelle.

### Z4 — Sous-total de groupe
Ligne orange : `TOTAL <GROUPE>` | Σ coefficients | Σ totaux | « Moyenne : x / 20 ».

| Section | Groupes observés (dans l'ordre) |
|---|---|
| 6ème FR | LANGUES ET LITTÉRATURES (13) · SCIENCES ET TECHNOLOGIE (16) · DÉVELOPPEMENT PERSONNEL (5) |
| Form 3 EN | LANGUAGE AND LITERATURE (10) · HUMAN AND SOCIAL SCIENCES (14) · SCIENCE AND TECHNOLOGY (12) · VOCATIONAL STUDIES (4) |

→ `SubjectGroup` (catalogue) + `ClassSubject.groupId` (appartenance par classe).

### Z5 — Résultat annuel
« Moyenne de l'élève 8,81 / 20 » · « Rang 12 / 12 » · une **mention générale** en grand · « PASSED IN 15 SUBJECTS » (anglophone seulement). Rang « 0/0 » possible (élève non classé).
→ `ReportCard.overall`. Mentions observées et proposition de barème : voir § 6 et **Q-09**.

### Z6 — Rappels moyennes
Tableau EXAM / RANG / MOY. pour TRIM1, TRIM2, TRIM3 (FR) ou TERM1..TERM3 (EN) : rang et moyenne de l'élève à chaque trimestre. Une ligne vide en plus.
→ `ReportCard.termRecaps`.

### Z7 — Profil de la classe
« Moyenne ≥ 12 » (nombre d'élèves) · « Moyenne classe » · « % Réussite » · « Moyenne max » · « Moyenne min ».
Vérifié : `% Réussite = nombre d'élèves avec moyenne ≥ 12 ÷ nombre d'élèves classés` (5/12 = 41,67 % ; 8/17 = 47,06 %). Attention : le seuil de ce bloc est **12** sur les bulletins annuels (mais **10** sur le bulletin de trimestre de Form 1), alors que « PASSED IN n SUBJECTS » utilise **10**.
→ `ReportCard.classProfile`, seuil dans `GradingScheme.classSuccessThreshold`.

### Z8 — Travail de l'élève
Tableau d'honneur (réponse Q-11 : moyenne >= 12) · T.H + Encouragement · T.H + Félicitation · Avertissement Travail · Blâme Travail (EN : Honour Roll · H.R + Encouragement · H.R + Congratulation · Academic Warning · S. Academic Warning). Oui / Non.
→ `StudentPeriodReview.distinction`, `workSanction` (saisie, **Q-11**).

### Z9 — Conduite de l'élève
Absences totales (en heures) · Absences non justifiées (« NJ ») · Total retards (ou « Absenteisme : n Days » sur le bulletin de trimestre) · Avertissement conduite · Blâme conduite.
→ `StudentPeriodReview.absenceHoursTotal`, `absenceHoursUnjustified`, `lateCount`, `conductSanction` (saisie).

### Z10 — Pied de bulletin
Texte « Dorénavant, consultez les bulletins de vos enfants en composant `*141*05#`... ou l'application Easy School Mobile » · trois cases : **Visa du parent**, **Décision du conseil de classe** (vide sur nos exemples : à remplir à la main ou à saisir), **Visa du chef d'établissement** (« YAOUNDE, le 26 Mai 2026 — LE PRINCIPAL — NOM ») · **code-barres à 13 chiffres** (identifiant d'authenticité) · date et heure d'impression · « Le bulletin est délivré sans rature, ni surcharge ».
→ `School.reportCardNotice`, `ReportCard.signature`, `ReportCard.verificationCode`, `ReportCard.generatedAt`, `School.reportCardDisclaimer`, `StudentPeriodReview.councilDecision`.

## 4. Comparaison des classes observées

| Classe | Section | Matières | Groupes | Σ coef. | Effectif | Particularités |
|---|---|---|---|---|---|---|
| 6ème | FR | 19 | 3 | 34 | 13 (12 classés) | sous-disciplines de français (expression écrite, orale, étude de texte...) |
| Form 3 | EN | 18 | 4 | 40 | 18 (17 classés) | photo de l'élève |
| 2NDE C | FR | 16 | 4 | 33 | 4 | « Arts and national culture » imprimé en anglais ; 2 notes DEV6 manquantes |
| 2NDE ALL | FR | 16 | 4 | 31 | 2 | allemand (coef 3), philosophie ; 6 notes manquantes |
| FORM 4 SC | EN | 14 | 4 | 59 | 2 | aucune note manquante ; coefficient 5 sur presque toutes les matières |
| FORM 4 ARTS | EN | 15 | 4 | 61 | 3 | élève sans DEV1-2, zéros massifs, aucun élève classé |
| FORM 1 (T1) | EN | 16 | 5 | 36 | 14 | bulletin de trimestre, compétences |

Les classes de 2nde et Form 4 sont **minuscules** (2 à 4 élèves) : tester les statistiques de classe sur de petits effectifs (rang 1/2, % de réussite 0 %).
Les séries/filières sont dans le nom de la classe : `2NDE C`, `2NDE ALL`, `FORM 4 ARTS`, `FORM 4 SC` → `SchoolClass.trackCode` (réponse Q-17 : oui, des séries avec des coefficients différents).

**Bilinguisme (réponse Q-01)** : dans le système « bilingue », certaines matières sont enseignées entièrement dans une langue et d'autres entièrement dans l'autre (« le meilleur des deux mondes »). Les documents (bulletins, emplois du temps...) doivent pouvoir être produits dans les deux langues ; on privilégie la langue de la section, et pour une section bilingue les deux langues doivent être opérationnelles. Le modèle : `Subject.teachingLanguage`, `ClassSubject.teachingLanguage`, `SchoolClass.defaultLanguage` + `availableLanguages`.

## 5. Ce que les bulletins révèlent sur les règles d'organisation

| Constat | Règle retenue |
|---|---|
| Un enseignant donne plusieurs matières dans la même classe et dans plusieurs classes / sections | Un enseignant peut avoir plusieurs `ClassSubject` |
| Le chef d'établissement enseigne et signe | Une personne = une fiche `Staff` avec plusieurs `functions` |
| Le même enseignant est orthographié différemment sur une page | L'enseignant est une entité référencée, pas un texte |
| Professeur principal commun à deux classes (2NDE C et 2NDE ALL) | `SchoolClass.mainTeacherId`, pas d'unicité entre classes |
| Matière sans enseignant (BIOLOGY, Form 4 ARTS) | `ClassSubject.teacherId` peut être vide |
| Effectif ≠ classés ; rang 0/0 | Stocker `effectif`, `rankedCount` et `rankStatus` |
| Redoublant propre à l'année | Porté par `Enrollment` |
| Matricule numérique ou alphanumérique (« 25F4A327 ») | Texte libre unique dans l'école (Q-05) |
| Lieu de naissance parfois absent | `placeOfBirth` facultatif |
| Matières sans note (musique, club, bibliothèque, ménage général) | `Subject.kind = ACTIVITY`, présentes seulement à l'emploi du temps |


## 6. Mentions générales observées (Q-09)

| Moyenne | Langue | Mention imprimée |
|---|---|---|
| 7,42 | EN | WEAK WORK |
| 8,81 | FR | TRAVAIL INSUFFISANT |
| 9,62 | FR | TRAVAIL MEDIOCRE |
| 11,65 et 11,67 | EN | FAIR |
| 11,67 | EN (trimestre) | TRAVAIL PASSABLE (en français sur un bulletin anglais) |
| 12,30 | FR | ASSEZ BON TRAVAIL |

**Proposition de barème** (bilingue, modifiable depuis l'interface ; les bornes en gras sont déduites, le reste est une proposition à valider) :

| Moyenne | FR | EN |
|---|---|---|
| < **9** | TRAVAIL INSUFFISANT | WEAK WORK |
| [**9**, 10[ | TRAVAIL MÉDIOCRE | POOR WORK *(libellé EN à valider)* |
| [10, 12[ | TRAVAIL PASSABLE | FAIR |
| [12, 14[ | ASSEZ BON TRAVAIL | FAIRLY GOOD WORK *(à valider)* |
| [14, 16[ | BON TRAVAIL | GOOD WORK |
| [16, 18[ | TRÈS BON TRAVAIL | VERY GOOD WORK |
| >= 18 | EXCELLENT TRAVAIL | EXCELLENT WORK |

La borne entre « insuffisant » et « médiocre » est quelque part entre 8,81 et 9,62 : 9 est choisi par défaut.

## 7. Vérification par recalcul

**Annuel complet** (6ème, Form 3, Form 4 SC : aucune note manquante) : toutes les moyennes de matière, de groupe, générales et de trimestre sont retrouvées (voir `04`).

**Avec notes manquantes** (2NDE C, 2NDE ALL, FORM 4 ARTS) : trois méthodes coexistent dans le logiciel actuel.

| Valeur imprimée | Méthode de l'ancien logiciel | Vérifié sur |
|---|---|---|
| **Moyenne d'une ligne de matière** | moyenne des 3 moyennes de trimestre, chacune arrondie à 2 décimales (arrondi au pair), un trimestre sans aucune note étant ignoré ; dans un trimestre, la note manquante est ignorée | 61 lignes sur 61 (+ les 37 lignes des deux premiers bulletins) |
| **Total et moyenne d'un groupe** | moyenne simple de **toutes les notes présentes** de chaque matière | 16 groupes sur 16 |
| **Moyenne annuelle de l'élève** | pas Σ totaux ÷ Σ coefficients ; apparemment la moyenne des moyennes de trimestre **imprimées** (certaines semblent calculées avant la saisie complète) | voir Q-24 |

Exemples : 2NDE ALL, Education artistique : notes 8,6 · 11 · 4 · 0 · 4 · « - » → ligne 5,27 (moyenne de 9,8 / 2 / 4) mais groupe calculé avec 5,52 (moyenne des 5 notes présentes). 2NDE C : moyenne annuelle imprimée 12,30, alors que Σ totaux ÷ Σ coefficients = 12,56 ; le trimestre 3 imprimé (11,56) est la moyenne pondérée de DEV5 seul, alors que la ligne affiche DEV6. FORM 4 ARTS : moyenne annuelle imprimée 7,42 = moyenne pondérée de DEV4 seul.

**Décision pour le nouveau logiciel** (à valider, Q-24) : une seule règle partout (celle des lignes, la plus visible), totaux de groupe = somme des totaux de lignes, moyenne annuelle = Σ totaux ÷ Σ coefficients, tout calculé dans la même passe à partir du même état des notes. Sans note manquante, c'est identique à l'ancien logiciel ; avec notes manquantes, les moyennes de groupe et la moyenne annuelle changent de quelques centièmes à quelques dixièmes.

## 8. Anomalies et particularités à connaître

- Format incohérent : `7.84` (point) parmi des `7,84` (virgule). Les nombres sont imprimés avec 2 décimales.
- Les notes de séquence peuvent avoir des décimales de type tiers (8.66, 11.33, 13.66) : elles sont **calculées** par la fiche de notes de l'enseignant puis saisies. Le nouveau logiciel stocke la note de séquence totalisée (2 décimales) et garde le détail (notes, bonus, malus) dans la fiche (réponse Q-02).
- Le rang n'est indiqué par matière que sur le bulletin de trimestre de Form 1.
- Aucune matière « dispensée » ni « absent » visible dans nos exemples.
- Le principal figure en bas du bulletin annuel (« LE PRINCIPAL » + nom) mais pas sur celui de trimestre : zone de signature configurable.
- Le même établissement a deux noms imprimés (« Collège bilingue international » sur le bulletin de trimestre, « Collège bilingue » sur les autres) : un en-tête par cycle ou par gabarit (`School.officialHeaders`).
- Date et heure d'édition imprimées en bas : propres à chaque impression.
- Le bulletin de trimestre contient « Dorénavant, consultez... *141*05# » : texte du prestataire, à ne pas reprendre.

## 8 bis. Fiche de report de notes (document papier enseignant)

Design remis par l'école : **FICHE DE REPORT DE NOTES**, **par trimestre** (pas par séquence). Une fiche = une classe + une matière + un trimestre, avec **deux blocs** (séquence 1 | séquence 2).

| Zone | Contenu |
|---|---|
| En-tête | Classe, enseignant, matière, coefficient, **N° = trimestre** (Q-35), effectif, garçons, filles, date de chaque séquence |
| Tableau | N° (rollNumber), noms et prénoms, sexe ; par séquence : Comp/20, Coef, Note×Coef, Rang, App |
| Pied | Visas Enseignant / Surveillant Général / Principal ; « toute rature doit être visée par le Principal » ; dépôt ≤ 72 h après composition (indicatif, Q-37) |

Ce document **ne montre pas** les bonus/malus (ceux-ci restent dans la fiche de travail `GradeSheet`). La colonne Comp/20 = note finale de séquence (`SequenceMark`, Q-02 confirmé sur ce point). App = automatique (échelle du barème) ; version vierge (noms seuls) pour workflow manuscrit (Q-36). PDF A4 paysage, multi-pages si > 45 élèves (Q-35). Modèle : `GradeReportSheet` ; règles : `02` BR-70..78, `04` § 10.

## 9. Emploi du temps (résumé ; détail dans `09-emploi-du-temps.md`)

12 pages A4 paysage (7 classes francophones : 6ème, 5ème, 4ème, 3ème, 2nde C, 1ère A, 1ère C/D ; 5 anglophones : Form 1, 2, 3, 4 Arts, 4 Science). Lundi-vendredi, 8 périodes de 50 min (la 7ᵉ dure 60 min), 2 pauses, nettoyage général puis « rentrée » ; chaque case = matière + enseignant ; cases particulières : CLUB, BIBLIOTHÈQUE, MUSIQUE, MÉNAGE GÉNÉRAL (« Délégué »), « CLUB / MATHS / SVT », « TD CHIMIE », et un cours le samedi 8h-10h en 1ère C/D. Il est **généré une seule fois par an**.

## 10. Ce qui n'a pas été vu (documents à collecter)

Voir `07-questions-ouvertes.md` § 6. En résumé : bulletin de séquence (s'il existe), bulletins de trimestres 2 et 3, maternelle et primaire, Tle / 1ère avec série, élève redoublant, fiche Excel de notes d'un enseignant (avec bonus / malus), règlement d'évaluation, procès-verbal de conseil, un bulletin d'une classe bilingue.
