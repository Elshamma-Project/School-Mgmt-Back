# 07 — Questions ouvertes, réponses reçues et décisions provisoires

> Les questions sont posées **à Ben**, qui les transforme en questions simples pour la directrice (jamais en questionnaire à remplir).
> **Statuts** : ✅ répondue · 🟡 partielle (le reste doit être clarifié plus tard) · ❓ ouverte (pas de réponse) · 💡 proposition à valider.
> Conformément à la demande de Ben, **toute question non répondue en totalité reste dans cette liste** jusqu'à clarification. Les identifiants Q-xx sont cités dans le code (KDoc) et dans les autres documents.

## 1. Tableau de suivi

| N° | Sujet | Statut | Décision / défaut retenu en attendant |
|---|---|---|---|
| Q-01 | « Bilingue » | 🟡 | Réponse : certaines matières entièrement dans une langue, d'autres dans l'autre ; documents dans les 2 langues, langue de la section prioritaire. **Reste** : un bulletin de classe bilingue affiche-t-il les matières des deux langues ensemble ? un élève peut-il choisir sa langue de bulletin ? |
| Q-02 | Note de séquence | ✅ | Fiche de travail par séquence ; notes, bonus, malus ; totalisation automatique. Comp/20 sur la fiche de report = `SequenceMark` (pas les bonus/malus bruts). Formule : Q-25 confirmée. |
| Q-03 | Bulletin de trimestre / séquence | 🟡 | Réponse : le bulletin de trimestre existe (Form 1, T1). **Reste** : bulletin de séquence ? T2 et T3 identiques à T1 ? gabarit pour les autres classes → Q-26 |
| Q-04 | Clôture d'une séquence : globale ou par classe ? qui clôture, qui rouvre ? | ✅ | **Confirmé (école)** : clôture **globale** (toute l'école). Clôture : **directeur ou administrateur**. Réouverture : **directeur seul**. |
| Q-05 | Matricule : règle, génération, conservation entre années | ❓ | Texte libre unique dans l'école. Observé : « 250369841 » (numérique) et « 25F4A327 » (« 25 » + code de classe + numéro ?) |
| Q-06 | Classement en cas d'ex aequo | 💡 | Proposition : mêmes rang et saut ensuite (1, 2, 2, 4), sur la moyenne **exacte** ; `RankingPolicy.DENSE` si l'école préfère 1, 2, 2, 3 |
| Q-07 | Pourquoi un élève n'est pas classé (effectif 13 / 12 classés ; rang « 0/0 ») | ❓ | Non classé = pas de moyenne calculable. Hypothèse : pas de moyenne de trimestre 1 (cas FORM 4 ARTS) ; option `HAS_ALL_TERMS` |
| Q-08 | « N° ON ROLL » | ✅ | C'est l'**effectif de la classe** |
| Q-09 | Mention générale : seuils et libellés | 🟡 | Valeurs observées + barème proposé (`01` § 6) ; libellés EN manquants à valider ; borne INSUFFISANT/MÉDIOCRE entre 8,81 et 9,62 (9 par défaut) |
| Q-10 | Appréciation par matière | ✅ | À ma charge : échelle `LETTER` (A >= 16, B+ >= 14, B >= 12, C en dessous) et échelle `COMPETENCY` (NA < 10, ECA [10,14[, A >= 14, A+ au-dessus) ; **modifiable plus tard depuis l'interface** |
| Q-11 | Tableau d'honneur, encouragements, félicitations, avertissements, blâmes | 🟡 | Réponse : tableau d'honneur à partir de **12/20** de moyenne (paramétrable). **Reste** : seuils des encouragements et félicitations ; avertissements et blâmes (automatiques ou décidés ?) → Q-29 |
| Q-12 | Note manquante / absent : divise-t-on par les séquences notées ou compte-t-on 0 ? | ❓ | `IGNORE` (conforme à l'ancien logiciel : le « - » est ignoré, un « 0.0 » compte) |
| Q-13 | Élève dispensé (EPS...) | ❓ | Matière exclue de la moyenne de cet élève |
| Q-14 | Arrondi | ✅ | Arrondi mathématique (`HALF_UP`) et on **accepte les écarts de centimes** ; pas de troncature |
| Q-15 | Couleurs des notes (bleu, rouge, noir) | ❓ | Sans sens fonctionnel (basse priorité) |
| Q-16 | Maternelle et primaire : notation et bulletins | ❓ | Non modélisé : secondaire seulement |
| Q-17 | 2nd cycle : séries avec coefficients différents | ✅ | **Oui** (confirmé par 2NDE C / 2NDE ALL, FORM 4 ARTS / SC) ; `SchoolClass.trackCode` + `ClassSubject` par classe |
| Q-18 | Passage en classe supérieure, procès-verbal du conseil | ❓ | `councilDecision` saisi à la main |
| Q-19 | Relation avec SKYSOFT / Easy School Mobile | 🟡 | Réponse : ces logiciels sont bien ceux de l'école ; l'ancien prestataire n'a plus de contrat ; l'école veut changer de prestataire (l'école ne dit pas pourquoi). **Reste** : peut-on récupérer les données (élèves, notes, historique) ? en quel format ? |
| Q-20 | Consultation par les parents | ✅ | **Navigateur uniquement** ; connexion à leur compte ; ils voient automatiquement le ou les bulletins de **tous leurs enfants, et uniquement les leurs** |
| Q-21 | Absences et retards : qui saisit, unité, justifiées | ❓ | Heures entières, saisies par l'administration |
| Q-22 | Enseignant remplacé en cours d'année | ❓ | Dernier enseignant, nom figé à la génération |
| Q-23 | Signature, cachet, copies du bulletin | ❓ | Zone de signature, nom du chef imprimé ; signature manuscrite après impression |

## 2. Nouvelles questions (issues des bulletins et de l'emploi du temps)

| N° | Question | Statut | Défaut retenu | Pourquoi |
|---|---|---|---|---|
| Q-24 | **Règle de calcul avec notes manquantes** : l'ancien logiciel calcule les lignes (moyenne des trimestres arrondis), les groupes (moyenne de toutes les notes présentes) et la moyenne annuelle (moyenne des trimestres imprimés, parfois calculée avant la dernière saisie) de trois façons différentes. Faut-il une règle unique ? Laquelle est « juste » aux yeux de l'école ? Le trimestre 3 imprimé n'inclut-il vraiment pas DEV6 ? D'où vient 7,42 (FORM 4 ARTS) ? | 💡 | Règle unique : lignes comme imprimées, groupes = somme des lignes, moyenne annuelle = Σ totaux ÷ Σ coefficients, tout en une passe | Les bulletins avec notes manquantes ne se retrouveront pas au centime ; voir `01` § 7 |
| Q-25 | **Totalisation de la fiche de notes** : comment se combinent notes, bonus, malus ? (moyenne des notes + bonus − malus ? bonus plafonné ? malus par absence ? pondération entre devoirs ?) Un exemplaire de fiche Excel serait idéal | ✅ | **Confirmé (école)** : moyenne des notes + bonus − malus, plafonné à 20 (`AVERAGE_PLUS_BONUS_MINUS_MALUS`). Une fiche Excel chiffrée reste utile pour les cas limites (pondération, plafond bonus). | Détermine `SequenceMark` |
| Q-26 | **Gabarits de bulletin** : le bulletin de trimestre de Form 1 vaut-il pour toutes les classes ? Les bulletins de T2 et T3 ? Les classes de 6ème à 3ème francophones ont-elles aussi un bulletin de trimestre par compétences ? Un bulletin de séquence existe-t-il ? | ❓ | Deux gabarits : `ANNUAL_STANDARD` et `TERM_COMPETENCY`, choisis par période et niveau | Quels gabarits PDF écrire |
| Q-27 | **Seuil de A+** (parfaitement acquis) sur l'échelle des compétences | ❓ | Seuil vide (A au-dessus de 14) | Observé : A jusqu'à 17,25 |
| Q-28 | **« MAKE EFFORT IN »** : quelle règle (moyenne < 8 ?) et combien de matières au maximum ? | 💡 | Moyenne < 8 | Observé : 7,5 et 6,0 listées, 8,5 non listée |
| Q-29 | **Tableau d'honneur et sanctions** : un avertissement / blâme de conduite empêche-t-il le tableau d'honneur ? Seuils des encouragements et félicitations ? Avertissements automatiques sous un seuil de moyenne ? Pourquoi le profil de classe utilise-t-il 10 en Form 1 (trimestre) et 12 ailleurs ? | ❓ | Tableau d'honneur >= 12 et bloqué par une sanction ; seuil de classe 12 (annuel) / 10 (trimestre Form 1) ; le reste saisi à la main | Observé : élève à 12,30 sans tableau d'honneur avec « Avertissement conduite : Oui » |
| Q-30 | **Matière « F/N »** de Form 4 (Etoundi) : French/National language ? Et « LCN », « PCT », « H.BIOLOGY », « SVTEEHB » : noms complets FR et EN ? | ❓ | Alias d'emploi du temps libres | Catalogue de matières bilingue |
| Q-31 | **Zéro et « non saisi »** : dans l'ancien logiciel, une séquence non faite apparaît parfois comme 0.0 (FORM 4 ARTS). Faut-il distinguer « absent / 0 réel / pas encore noté » sur les fiches ? | 💡 | Oui : `PENDING` n'est jamais un zéro | Évite des moyennes fausses |
| Q-32 | **Génération de l'emploi du temps** : saisie manuelle avec contrôle de conflits, ou génération automatique ? Contraintes (disponibilités, salles, maximum d'heures par jour) ? Qui le fait aujourd'hui, avec quel outil ? | ❓ | Saisie manuelle guidée + duplication d'une année sur l'autre | Périmètre du module timetable |
| Q-33 | **Samedi et remplacements** : le cours du samedi (1ère C/D) est-il régulier ? Un remplacement ponctuel d'enseignant modifie-t-il l'emploi du temps annuel ? | ❓ | Cours hors grille régulier ; pas de remplacement ponctuel | `customStart` / `customEnd` |
| Q-34 | **Reprise des données** (élèves, historique de notes) : format fourni par l'ancien prestataire ? Import des élèves plus tard ou création un par un ? | ❓ | Création un par un au début | Hors périmètre actuel |
| Q-35 | **Fiche de report** : sens du « N° » d'en-tête ; une fiche > 45 élèves peut-elle tenir sur 2 pages ? | ✅ | **N° = numéro du trimestre**. Oui, 2 pages OK (en-tête de tableau répété) ; `reportSheetMaxRowsPerPage = 45` | Design papier remis |
| Q-36 | Qui remplit la colonne « App » ? Logiciel ou enseignant ? Impression vierge ? | ✅ | **Logiciel** (échelle du barème, Q-10). Garder une **version vierge** (noms seuls) pour saisie manuscrite — le logiciel ne remplace pas le workflow papier | Réponse école |
| Q-37 | Le délai de 72 h est-il bloquant ? | ✅ | **Non** (indicatif) : `reportSheetDeadlineBlocking = false` ; avertissement seulement | Réponse école |
| Q-38 | Rôle Surveillant Général / rôle Principal ? | ❓ | `SURVEILLANT_GENERAL` ajouté au modèle ; Principal = `DIRECTOR` en attendant confirmation d'un rôle `PRINCIPAL` distinct | Pas encore de réponse RH |

## 3. Décisions provisoires (résumé)

| Sujet | Décision |
|---|---|
| Séquences et trimestres | 6 séquences, 3 trimestres de 2 séquences |
| Note de séquence | Totalisation automatique de la fiche de notes (notes + bonus − malus, plafond 20) |
| Note manquante | Ignorée ; zéro réel compté |
| Moyenne d'une matière | Moyenne des moyennes de trimestre (arrondies à 2 décimales) |
| Groupe | Σ totaux de lignes ÷ Σ coefficients |
| Moyenne générale / annuelle | Σ totaux ÷ Σ coefficients |
| Rang | Moyenne exacte, parmi les classés, ex aequo en compétition |
| Seuils | Validation de matière 10 ; profil de classe 12 (annuel) / 10 (trimestre Form 1) ; tableau d'honneur 12 |
| Arrondi | `HALF_UP`, écarts de centimes acceptés |
| Effectif | Effectif de la classe |
| Bilinguisme | Documents dans les 2 langues ; langue de la section prioritaire |
| Parents | Navigateur ; tous leurs enfants, uniquement les leurs |
| Une page A4 | Bulletin (portrait) et emploi du temps (paysage) ; fiche de report paysage multi-pages si > 45 lignes |
| Fiche de report | Instantané trimestriel des SequenceMark ; App auto ; délai 72 h non bloquant ; SG + Principal |
| Architecture | Monolithe modulaire, modules = futurs microservices |
| Marque tierce | Aucun élément du prestataire précédent sur les documents |

## 4. Documents complémentaires à demander à la collaboratrice

Priorité 1 (avant la démo) :
1. *(Optionnel)* Une fiche Excel chiffrée avec bonus/malus pour valider les cas limites de Q-25 (déjà confirmée : moyenne + bonus − malus, plafond 20).
1 bis. Confirmation RH : périmètre du Surveillant Général et distinction Principal / Directeur (Q-38).
2. Un **bulletin de séquence** s'il existe ; les **bulletins de trimestre 2 et 3** et un bulletin de trimestre d'une classe francophone (Q-03, Q-26).
3. Un bulletin d'une classe **bilingue** (Q-01).
4. Le **règlement d'évaluation** et le **procès-verbal d'un conseil de classe** (Q-09, Q-11, Q-18, Q-29).
5. Un bulletin de **Tle** ou **Form 5**, et de **maternelle / primaire** (Q-16).

Priorité 2 :
6. Un bulletin d'**élève redoublant** et d'un élève **non classé** (Q-07).
7. Le **document d'inscription** et une **carte d'élève** (modules suivants).
8. Les emplois du temps **par enseignant** s'ils existent, la **liste des salles**, les **heures hebdomadaires par matière** (Q-32).
9. À l'oral avec la secrétaire : Q-05, Q-12, Q-13, Q-21, Q-22.
