# 01 — Analyse des bulletins

> Source : deux bulletins annuels 2025-2026 fournis par l'école : une **6ème (section francophone)** et une **Form 3 (section anglophone)**.
> Les deux ont le même gabarit A4, une page, et le même en-tête bilingue. Les élèves et enseignants sont anonymisés ici
> (les enseignants sont désignés par des codes T1, T2... identiques à ceux des fichiers `fixtures`).
>
> Statut : analyse vérifiée par recalcul complet (voir `04-regles-de-calcul.md`). Les points non tranchés sont renvoyés à `07-questions-ouvertes.md` (Q-xx).

## 1. Ce qu'il faut retenir (résumé)

1. **Une année = 6 séquences (DEV1 à DEV6), 3 trimestres de 2 séquences.** DEV1-2 = trimestre 1, DEV3-4 = trimestre 2, DEV5-6 = trimestre 3 (confirmé par recalcul des moyennes de trimestre).
2. **Le bulletin annuel affiche les 6 séquences par matière**, la moyenne de la matière, le coefficient, le total (moyenne × coefficient) et une appréciation en lettre.
3. **Les matières sont regroupées en blocs avec un sous-total.** Les groupes et les matières qu'ils contiennent **changent selon la classe et la section** (l'Histoire est dans « Sciences et technologie » en 6ème, dans « Human and Social Sciences » en Form 3). Le regroupement est donc une donnée de la *classe*, pas de la matière.
4. **Un enseignant n'est pas lié à une classe, il est lié à des couples (classe, matière).** Il enseigne souvent plusieurs matières dans la même classe (T2 : 4 matières en 6ème, T3 : 3, T6 : 3, T18 : 3 en Form 3) et plusieurs classes, y compris dans les deux sections (T1 enseigne l'anglais en 6ème francophone ET l'English Language et la Literature en Form 3). Le chef d'établissement (T22) enseigne aussi une matière. Le même enseignant apparaît avec des orthographes différentes sur une même page : il faut une fiche enseignant, pas du texte libre.
5. **Effectif ≠ nombre d'élèves classés.** 13 élèves dans la classe mais rang « 12 / 12 » en 6ème ; 18 en Form 3 mais rang « 13 / 17 ». Dans les deux cas un élève n'est pas classé (Q-07).
6. **Le bulletin mélange trois sortes d'informations** : (a) ce qui vient des notes (calculé), (b) ce qui se saisit (absences, retards, sanctions, distinctions, décision du conseil), (c) ce qui est de la configuration de l'école (en-tête, titres, textes de pied de page). Le modèle les sépare en conséquence.
7. **Les règles de calcul sont simples et entièrement retrouvées** : 0 écart sur 37 moyennes de matière, 0 écart sur les moyennes générales, de trimestre et les pourcentages. Les seuls écarts d'un centième sont des effets d'arrondi (section 6).
8. ⚠️ **Les bulletins portent « Powered by SKYSOFT TECHNOLOGY SARL » et mentionnent l'application « Easy School Mobile » et le service USSD `*141*05#` (MTN).** Voir section 8.

## 2. Décomposition du bulletin en zones

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
| CLASSE | CLASS | 6IEME / FORM 3 | `SchoolClass.name` |
| MATRICULE | MATRICULE NUMBER | 9 chiffres | `Student.matricule` |
| SEXE | SEX | FEMININ / MALE | `Student.sex` |
| DATE ET LIEU DE NAISSANCE | DATE OF BIRTH | « NÉ(E) LE jj/mm/aaaa à LIEU » / « jj/mm/aaaa at LIEU » | `Student.dateOfBirth`, `placeOfBirth` |
| REDOUBLANT | REPEATER | Non | `Enrollment.repeater` (propre à l'année) |
| PROFESSEUR PRINCIPAL | CLASS MASTER/MISTRESS | un enseignant | `SchoolClass.mainTeacherId` |
| EFFECTIF | N° ON ROLL | 13 / 18 | `ClassSnapshot.effectif` ou `Enrollment.rollNumber` (**Q-08**) |
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
« Moyenne de l'élève 8,81 / 20 » · « Rang 12 / 12 » · une **mention générale** en grand (FR : « TRAVAIL INSUFFISANT » ; EN : « FAIR ») · EN uniquement : « PASSED IN 15 SUBJECTS ».
→ `ReportCard.overall`. Les seuils de la mention générale ne sont pas connus (**Q-09**).

### Z6 — Rappels moyennes
Tableau EXAM / RANG / MOY. pour TRIM1, TRIM2, TRIM3 (FR) ou TERM1..TERM3 (EN) : rang et moyenne de l'élève à chaque trimestre. Une ligne vide en plus.
→ `ReportCard.termRecaps`.

### Z7 — Profil de la classe
« Moyenne ≥ 12 » (nombre d'élèves) · « Moyenne classe » · « % Réussite » · « Moyenne max » · « Moyenne min ».
Vérifié : `% Réussite = nombre d'élèves avec moyenne ≥ 12 ÷ nombre d'élèves classés` (5/12 = 41,67 % ; 8/17 = 47,06 %). Attention : le seuil de ce bloc est **12**, alors que « PASSED IN n SUBJECTS » utilise **10**.
→ `ReportCard.classProfile`, seuil dans `GradingScheme.classSuccessThreshold`.

### Z8 — Travail de l'élève
Tableau d'honneur · T.H + Encouragement · T.H + Félicitation · Avertissement Travail · Blâme Travail (EN : Honour Roll · H.R + Encouragement · H.R + Congratulation · Academic Warning · S. Academic Warning). Oui / Non.
→ `StudentPeriodReview.distinction`, `workSanction` (saisie, **Q-11**).

### Z9 — Conduite de l'élève
Absences totales (en heures) · Absences non justifiées · Total retards · Avertissement conduite · Blâme conduite.
→ `StudentPeriodReview.absenceHoursTotal`, `absenceHoursUnjustified`, `lateCount`, `conductSanction` (saisie).

### Z10 — Pied de bulletin
Texte « Dorénavant, consultez les bulletins de vos enfants en composant `*141*05#`... ou l'application Easy School Mobile » · trois cases : **Visa du parent**, **Décision du conseil de classe** (vide sur nos exemples : à remplir à la main ou à saisir), **Visa du chef d'établissement** (« YAOUNDE, le 26 Mai 2026 — LE PRINCIPAL — NOM ») · **code-barres à 13 chiffres** (identifiant d'authenticité) · date et heure d'impression · « Le bulletin est délivré sans rature, ni surcharge ».
→ `School.reportCardNotice`, `ReportCard.signature`, `ReportCard.verificationCode`, `ReportCard.generatedAt`, `School.reportCardDisclaimer`, `StudentPeriodReview.councilDecision`.

## 3. Comparaison des deux sections

| Aspect | 6ème (francophone) | Form 3 (anglophone) |
|---|---|---|
| Titre | BULLETIN ANNUEL DE NOTES | ANNUAL REPORT CARD |
| Nombre de matières | 19 | 18 |
| Nombre de groupes | 3 | 4 |
| Total des coefficients | 34 | 40 |
| Coefficient le plus élevé | Mathématiques (4) | English Language, French, Mathematics (4) |
| Langue étrangère | Anglais (coef 3) + Allemand (coef 1) | French (coef 4) |
| Sous-disciplines de langue | Expression écrite, Expression orale, Correction orthographique, Étude de texte (4 matières) | English Language, Literature in English (2 matières) |
| Éducation civique / Histoire / Géographie | dans « Sciences et technologie » | dans « Human and Social Sciences » |
| Photo de l'élève | non | oui |
| Mention générale | TRAVAIL INSUFFISANT | FAIR |
| « Matières validées » | absent | « PASSED IN 15 SUBJECTS » |
| Libellé trimestres | TRIM1..3 | TERM1..3 |
| Libellé effectif | EFFECTIF | N° ON ROLL |
| Libellé avertissements | Avertissement / Blâme | Warning / S. (Severe) Warning |

**Conséquence de conception** : une matière du catalogue n'a ni groupe, ni coefficient, ni enseignant ; tout cela vit dans `ClassSubject`. Les deux sections utilisent exactement la même structure.

## 4. Ce que les bulletins révèlent sur les règles d'organisation

| Constat | Règle retenue |
|---|---|
| T2 enseigne Expression écrite, Expression orale, Allemand et EPS dans la même 6ème | Un enseignant peut avoir plusieurs `ClassSubject` dans une même classe |
| T1 enseigne en 6ème (francophone) et en Form 3 (anglophone) | Un enseignant n'est pas rattaché à une section ni à une classe |
| T22 signe comme chef d'établissement et enseigne Life Aptitude | Une personne = une fiche `Staff` avec plusieurs `functions` |
| Même enseignant orthographié « Aime » et « Aimé » sur la même page | L'enseignant est une entité référencée, pas un texte |
| T8 est professeur principal de la 6ème et enseigne les mathématiques ; T1 est « class master » de la Form 3 | Le professeur principal est un attribut de la classe, pas de la matière |
| Matières communes aux deux sections (Mathematics/Mathématiques) avec des enseignants différents | Le catalogue de matières est partagé, les affectations sont par classe |
| Effectif 13 mais 12 classés ; 18 mais 17 | Stocker séparément `effectif` et `rankedCount` |
| « Redoublant » propre à l'élève pour l'année | Porté par `Enrollment`, pas par `Student` |
| Photo présente sur un seul bulletin | La photo est facultative |

## 5. Enseignants et salles : ce que les bulletins ne disent pas

Les salles n'apparaissent pas sur le bulletin. La règle « un enseignant donne plusieurs matières dans plusieurs salles » relève du module **emplois du temps** (phase suivante). Le modèle pose déjà `Room`, `ClassSubject.weeklyHours` et `SchoolClass.homeRoomId`. Le document d'emploi du temps (créneau = jour + heure + classe + matière + enseignant + salle) sera ajouté ensuite, avec contrôle de conflits sur l'enseignant, la salle et la classe.

## 6. Vérification par recalcul (résumé)

Les 37 lignes de matière des deux bulletins ont été recalculées à partir des 6 notes de séquence :

| Contrôle | 6ème FR | Form 3 EN |
|---|---|---|
| Moyenne de matière = moyenne des 6 séquences | 19/19 | 18/18 |
| Total de ligne = moyenne × coefficient | 19/19 | 17/18 (LOGIC : écart de 0,01) |
| Moyenne de groupe = Σ totaux ÷ Σ coefficients | 3/3 | 4/4 |
| Moyenne générale = Σ totaux ÷ Σ coefficients | 8,8108 → **8,81** ✓ | 11,6453 → **11,65** ✓ |
| Moyennes de trimestre (séquences 1-2, 3-4, 5-6) | 9,31 / 9,28 / 7,84 ✓ | 12,72 / 11,60 / 10,62 ✓ |
| % Réussite | 41,67 % ✓ | 47,06 % ✓ |
| « Passed in n subjects » (moyenne ≥ 10) | n/a (non imprimé) | 15 / 18 ✓ |
| Lettres d'appréciation | cohérentes avec A ≥ 16, B+ ≥ 14, B ≥ 12, C sinon | idem |

**Écarts d'arrondi** (détail et recommandation dans `04-regles-de-calcul.md`) :
- 2 moyennes tombent exactement sur un demi-centième (8,555 et 10,055) et l'ancien logiciel imprime 8,55 et 10,05 (arrondi en double précision), là où un arrondi décimal exact donnerait 8,56 et 10,06.
- 1 total de ligne (21,2767 imprimé 21,27) reste inexpliqué.

## 7. Anomalies et particularités à connaître

- La moyenne « TRIM3 » de la 6ème est imprimée `7.84` (point) alors que les autres sont à la virgule : simple incohérence de format.
- Les notes de séquence ont parfois des décimales de type tiers (8.66, 11.33, 9.66) mais aussi 6.67 : elles ne sont ni systématiquement tronquées ni arrondies, donc **saisies ou calculées ailleurs**. Le logiciel doit **stocker la note telle qu'entrée** (2 décimales), sans la recalculer (Q-02).
- Le total de groupe est la somme des totaux **exacts** puis arrondie, et non la somme des totaux de ligne arrondis (Form 3, sciences : 117,01 contre 117,00 imprimé).
- Le rang n'est pas indiqué par matière sur le bulletin.
- Aucune matière « dispensée » ni « absent » visible dans nos deux exemples.

## 8. Remarque stratégique : un logiciel de bulletins existe déjà

Les deux PDF indiquent **« Powered by SKYSOFT TECHNOLOGY SARL »**, proposent la consultation des notes par **USSD (`*141*05#`, MTN)** et via **« Easy School Mobile »**. Donc, soit l'école utilise déjà un logiciel tiers pour **imprimer les bulletins** (les notes pouvant rester saisies sur Excel), soit ces bulletins viennent d'un autre établissement.

À clarifier avec la directrice (Q-19) : quelle relation a l'école avec ce prestataire, ce qui est conservé ou remplacé, et si ses données sont exportables. À respecter : **reproduire la structure réglementaire du bulletin, pas la marque ni les services de ce prestataire** (nom, mentions « Powered by », application mobile, code USSD).
La consultation par les parents est déjà un service existant : ce sera un argument à prendre en compte dans la proposition.

## 9. Ce qui n'a pas été vu (documents à collecter en priorité)

1. Un **bulletin trimestriel** (T1 ou T2) et un **bulletin de séquence** s'ils existent (**Q-03**).
2. Un bulletin de **maternelle** et de **primaire**, en FR et en EN (**Q-16**).
3. Un bulletin de **2nde / 1ère / Tle** avec une série (**Q-17**).
4. Un bulletin d'élève **redoublant**, d'élève **non classé**, et d'une classe **bilingue** (**Q-01**, **Q-07**).
5. Le fichier **Excel de notes** d'origine d'une classe (pour voir comment les notes de séquence sont produites, **Q-02**).
6. Le **règlement d'évaluation** : passage, mentions générales, tableau d'honneur, sanctions (**Q-09**, **Q-11**, **Q-18**).
7. Un **procès-verbal de conseil de classe** (**Q-18**).
