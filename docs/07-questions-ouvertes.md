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
| Q-20 | Consultation par les parents | ✅ | **Navigateur uniquement** ; connexion à leur compte ; ils voient automatiquement le ou les bulletins de **tous leurs enfants, et uniquement les leurs**. **Implémentation** : règle métier + modèle `guardians` OK ; UI parent encore shell « bientôt » (pas de flux prod) |
| Q-21 | Absences et retards : qui saisit, unité, justifiées | ❓ | Heures entières, saisies par l'administration |
| Q-22 | Enseignant remplacé en cours d'année | ❓ | Dernier enseignant, nom figé à la génération |
| Q-23 | Signature, cachet, copies du bulletin | ❓ | Zone de signature, nom du chef imprimé ; signature manuscrite après impression |
| Q-35 | Fiche de report : sens du « N° » d'en-tête ; fiche > 45 élèves sur 2 pages ? | ✅ | N° = numéro du trimestre ; 2e page OK (`reportSheetMaxRowsPerPage = 45`) |
| Q-36 | Colonne « App » : logiciel ou enseignant ? | ✅ | Calculée (échelle LETTER/COMPETENCY) ; override + version vierge possibles |
| Q-37 | Délai 72 h bloquant ou indicatif ? | ✅ | Indicatif (`reportSheetDeadlineBlocking = false`) |
| Q-38 | Rôle Surveillant Général / Principal ? | 🟡 | SG = `SURVEILLANT_GENERAL` ; Principal = `DIRECTOR` (v1) |
| Q-39 | Mention générale : libellés et seuils FR/EN | ❓ | Barème proposé (< 9 insuffisant … >= 18 excellent) ; voir Q-09 / Q-50 |
| Q-40 | Notes < passMark en rouge discret sur bulletin ? | 💡 | Oui via `passMarkHighlight`, désactivable par gabarit (lié Q-15) |
| Q-41 | LV2 ALL/ESP : classes séparées ou options parallèles ? | ❓ | EDT : options parallèles ; notes : non tranché |
| Q-42 | 4IEME ESP : coef espagnol et groupe LCN ? | ❓ | Hypothèses : espagnol 2, LCN 1 en DEV_PERS ; coef null refusés au seed |
| Q-43 | Regroupements matières : voulus ou héritage ? | ❓ | Reproduits à l'identique (DEV_PERS + VOC) |
| Q-44 | Libellés complets / traductions matières (ECM, SVTEEHB, F/N…) | ❓ | Noms proposés (`_enNameSource = PROPOSED`) |
| Q-45 | Identité école : immatriculation, téléphones, e-mail, B.P. | ❓ | Tél. bulletin ; immatriculation dans `_extra` ; `School.registrationNumber` à créer |
| Q-46 | Calendrier années / trimestres / séquences 2025-2027 | ❓ | Dates approx. marquées `_confirm` |
| Q-47 | CHINOIS, MUSIQUE, CLUB, BIBLIOTHÈQUE : notés ou activités EDT ? | ❓ | Non chargées (catalogue + emploi du temps) |
| Q-48 | Bulletins / niveaux manquants (3ème ESP, 1ères, Tle, Form 5…) | ❓ | Niveaux concernés créés inactifs, aucune classe |
| Q-49 | Nom officiel : « COLLÈGE BILINGUE » vs « … INTERNATIONAL … » ? | ❓ | « COLLÈGE BILINGUE EL SHAMMAH » |
| Q-50 | Table de mentions EN (WEAK / VERY WEAK, ordre inversé) | ❓ | Libellés observés conservés ; lié Q-09 / Q-39 |
| Q-51 | Même barème FR et EN (lettres, compétences, validation 10) ? | 💡 | Oui : FR_SECONDARY / EN_SECONDARY identiques, fusionnables |
| Q-52 | « PASSED IN n SUBJECTS » : interrupteur ou langue EN seule ? | 💡 | `showPassedInSubjects`, actif si langue bulletin = EN |
| Q-53 | Qui utilise TERM_COMPETENCY ? Bulletins trimestre FR ? | ❓ | TERM_COMPETENCY limité à FORM1 (lié Q-26) |
| Q-54 | Form 4 ARTS/SC : coef BIOLOGY 2 et matières absentes — voulu ? | ❓ | Reproduit tel quel |
| Q-55 | Cours groupés multi-classes (y compris entre sections) ? | ❓ | `sessionGroupId` ; PDF sans conflit bloquant |
| Q-56 | Alias FRANÇAIS/LITTÉRATURE/TM/F/N ↔ matières bulletin | ❓ | `TimetableSubjectAlias` + somme weeklyHours |
| Q-57 | Bibliothèque : surveillance multi-classes pendant cours ? | ❓ | ACTIVITY sans conflit enseignant (v1) |
| Q-58 | Doublons PDF (ex. Batoum 6ème+4ème mardi P6) | ❓ | Avertissement au chargement |
| Q-59 | Identité enseignants EDT (prénoms, sexe, contact, homonymes FOYEH / MASSAH / NONO / JOSEPH) | ❓ | Fiches `needsCompletion=true`, `source=EDT_2026_2027` ; pas de trancher |
| Q-60 | Accès enseignants : e-mail réel, invitation, pas de changement d’e-mail | 🟡 | **Option A** : Staff sans compte ; `POST /staff/{id}/login-access` saisit l’e-mail une fois |
| Q-61 | Enseignant d’allemand 3ème / 1ère A imprimé « Mme … » | ❓ | Case sans `teacherId` (pas de fiche fantôme) |
| Q-62 | Envoi e-mail automatique pour mot de passe oublié / invitation | ❓ | v1 : demande publique + notif admin ; reset manuel `POST /users/{id}/reset-password` |
| Q-63 | Mapping EDT `FRANÇAIS` / `?FRANCAIS` → matières bulletin (`LANG_FR` vs `EXP_*`) | ❓ | Seed EDT mappe vers `LANG_FR` ; cases junior peuvent rester sans `classSubjectIds` |
| Q-64 | Examens dans la grille semaine type vs calendrier sessions | 🟡 | **v1** : calendrier `EvaluationSession` synchronisé (onglet Examens + notif) ; pas de cases examen dans l’EDT hebdo |
| Q-65 | Accès école bloqué sans paiement d’inscription ? | ❓ | Affichage solvabilité d’abord ; blocage notes/portail plus tard |
| Q-66 | Qui paramètre les barèmes / saisit les paiements ? | 🟡 | Barèmes = Direction ; paiements = Direction **ou** Administration avec fonction RH `ACCOUNTANT` (BR-F09) |
| Q-67 | Barème par **classe** ou par **niveau** ? | 💡 | Par **classe** (montants peuvent différer 6ème A / 6ème B) |
| Q-68 | Mois des 3 tranches si rentrée ≠ 1er sept. | 💡 | 3 premiers mois civils à partir du mois de `AcademicYear.startDate` |
| Q-69 | Modes de paiement v1 | 💡 | Saisie manuelle (espèces / mobile money / autre + référence) |
| Q-70 | Règles exactes SOLVENT / PENDING / INSOLVENT | 💡 | Voir `10-scolarite-solvabilite.md` § 2 |
| Q-71 | Élève sans barème / sans inscription année | 💡 | `NOT_APPLICABLE` (neutre) |
| Q-72 | Avant septembre / hors 3 mois | 💡 | Avant M0 : inscription seule ; après M2 : échus uniquement |
| Q-73 | Devise | 💡 | `XAF` |
| Q-74 | Paiement partiel d’une tranche | ❓ | v1 : tranche « payée » seulement si montant ≥ dû |
| Q-75 | Catalogue des modes de paiement | ❓ | Texte libre + enum court |
| Q-76 | Annulation / correction d’un paiement | 💡 | Statut `VOID` + motif, pas de delete |

## 2. Nouvelles questions (issues des bulletins et de l'emploi du temps)

| N° | Question | Statut | Défaut retenu | Pourquoi |
|---|---|---|---|---|
| Q-24 | **Règle de calcul avec notes manquantes** : l'ancien logiciel calcule les lignes (moyenne des trimestres arrondis), les groupes (moyenne de toutes les notes présentes) et la moyenne annuelle (moyenne des trimestres imprimés, parfois calculée avant la dernière saisie) de trois façons différentes. Faut-il une règle unique ? Laquelle est « juste » aux yeux de l'école ? Le trimestre 3 imprimé n'inclut-il vraiment pas DEV6 ? D'où vient 7,42 (FORM 4 ARTS) ? | 💡 | Règle unique : lignes comme imprimées, groupes = somme des lignes, moyenne annuelle = Σ totaux ÷ Σ coefficients, tout en une passe | Les bulletins avec notes manquantes ne se retrouveront pas au centime ; voir `01` § 7 |
| Q-25 | **Totalisation de la fiche de notes** : comment se combinent notes, bonus, malus ? (moyenne des notes + bonus − malus ? bonus plafonné ? malus par absence ? pondération entre devoirs ?) Un exemplaire de fiche Excel serait idéal | ✅ | **Confirmé (école)** : moyenne des notes + bonus − malus, plafonné à 20 (`AVERAGE_PLUS_BONUS_MINUS_MALUS`). Une fiche Excel chiffrée reste utile pour les cas limites (pondération, plafond bonus). | Détermine `SequenceMark` |
| Q-26 | **Gabarits de bulletin** : le bulletin de trimestre de Form 1 vaut-il pour toutes les classes ? Les bulletins de T2 et T3 ? Les classes de 6ème à 3ème francophones ont-elles aussi un bulletin de trimestre par compétences ? Un bulletin de séquence existe-t-il ? | ❓ | Deux gabarits : `ANNUAL_STANDARD` et `TERM_COMPETENCY`, choisis par période et niveau | Quels gabarits PDF écrire |
| Q-27 | **Seuil de A+** (parfaitement acquis) sur l'échelle des compétences | ❓ | Seuil vide (A au-dessus de 14) | Observé : A jusqu'à 17,25 |
| Q-28 | **« MAKE EFFORT IN »** : quelle règle (moyenne < 8 ?) et combien de matières au maximum ? | 💡 | Moyenne < 8 | Observé : 7,5 et 6,0 listées, 8,5 non listée |
| Q-29 | **Tableau d'honneur et sanctions** : un avertissement / blâme de conduite empêche-t-il le tableau d'honneur ? Seuils des encouragements et félicitations ? Avertissements automatiques sous un seuil de moyenne ? Pourquoi le profil de classe utilise-t-il 10 en Form 1 (trimestre) et 12 ailleurs ? | ❓ | Tableau d'honneur >= 12 et bloqué par une sanction ; seuil de classe 12 (annuel) / 10 (trimestre Form 1) ; le reste saisi à la main | Observé : élève à 12,30 sans tableau d'honneur avec « Avertissement conduite : Oui » |
| Q-30 | **Matière « F/N »** de Form 4 (Etoundi) : French/National language ? Et « LCN », « PCT », « H.BIOLOGY », « SVTEEHB », « A.MATHS » : noms complets FR et EN ? | ❓ | **Hypothèse EDT** : F/N = Food and Nutrition ; H.BIOLOGY = Human Biology ; A.MATHS = Additional Maths ; table `TimetableSubjectAlias` (jamais de match texte libre) | Catalogue bilingue + alias EDT |
| Q-31 | **Zéro et « non saisi »** : dans l'ancien logiciel, une séquence non faite apparaît parfois comme 0.0 (FORM 4 ARTS). Faut-il distinguer « absent / 0 réel / pas encore noté » sur les fiches ? | 💡 | Oui : `PENDING` n'est jamais un zéro | Évite des moyennes fausses |
| Q-32 | **Génération de l'emploi du temps** : saisie manuelle ou automatique ? Qui le fait (admin, SG…) ? | 🟡 | **v1** : saisie manuelle + conflits + duplication ; `School.timetableSurveillantCanEdit = false` par défaut (SG activable) ; pas de solveur | Périmètre timetable |
| Q-33 | **Samedi et remplacements** : cours du samedi réguliers ? Remplacements ponctuels ? | 🟡 | **v1** : **3** cours samedi hors grille (3ème ALL 7h30–10h30, 1ère A ALL 10h30–13h30, 1ère C/D TD Chimie 8h–10h) ; remplacements ponctuels **hors** EDT annuel | `customStart` / `customEnd` |
| Q-34 | **Reprise des données** (élèves, historique de notes) : format fourni par l'ancien prestataire ? Import des élèves plus tard ou création un par un ? | 🟡 | **v1 figée** : création un par un (CRUD UI) ; import massif / reprise SKYSOFT hors périmètre jusqu'à format fourni (lié Q-19). | Hors périmètre actuel |
| Q-35 | Que signifie le champ « N° » de l'en-tête de la fiche de report de notes ? Une fiche de plus de 45 élèves peut-elle tenir sur 2 pages ? | ✅ | Champ libre laissé vide ; fiche A4 paysage, au-delà de 45 lignes une 2e page avec en-tête de tableau répété — **répondu** : N° = numéro du trimestre ; `reportSheetMaxRowsPerPage = 45` | Contenu de l'en-tête et exception à la règle « une seule page A4 » |
| Q-36 | Qui remplit la colonne « App » (appréciation) de la fiche : l'enseignant à la main, ou le logiciel avec l'échelle de la matière ? | ✅ | Calculée par le logiciel (échelle LETTER ou COMPETENCY), modifiable par l'enseignant (`appreciationOverride`) ; version vierge imprimable | Valeur imprimée et responsabilité de l'enseignant |
| Q-37 | Le délai « à déposer au plus tard 72 h après la composition de chaque séquence » est-il bloquant ou seulement indicatif ? | ✅ | Avertissement non bloquant ; paramètres `reportSheetDeadlineHours = 72` et `reportSheetDeadlineBlocking = false` | Règle de dépôt de la fiche |
| Q-38 | Le Surveillant Général est-il un rôle à créer dans le logiciel (visa de la fiche) ? Le « Principal » est-il le rôle directeur ou un rôle distinct ? | 🟡 | Rôle `SURVEILLANT_GENERAL` ajouté ; Principal = directeur (`DIRECTOR`) — v1 figée, confirmation RH bienvenue | Circuit de visas (enseignant → SG → Principal) et matrice des rôles |
| Q-39 | Mention générale : libellés et seuils validés par l'école, en français et en anglais (voir aussi Q-09 et Q-50) | ❓ | Barème proposé : < 9 insuffisant, [9,10[ médiocre, [10,12[ passable, [12,14[ assez bon, [14,16[ bon, [16,18[ très bon, >= 18 excellent | Pastille de mention du bulletin |
| Q-40 | Les notes inférieures à la note de validation (10) peuvent-elles être imprimées en rouge discret sur le bulletin ? (voir Q-15) | 💡 | Oui, option `passMarkHighlight` activée, désactivable par gabarit | Design du bulletin ; la couleur n'avait pas de sens dans l'ancien logiciel |
| Q-41 | LV2 allemand / espagnol : classes séparées (ALL, ESP) ou une seule classe avec choix élève ? | ❓ | **Côté EDT** : l’EDT 2026-2027 suggère **une classe unique avec options parallèles** (`optionGroupCode`/`optionCode`). **Côté notes** (ClassSubject / Enrollment) : **non tranché** | EDT vs modèle bulletin |
| Q-42 | 4IEME ESP : quel est le coefficient de l'espagnol (2 ?) et dans quel groupe est la LCN (DÉVELOPPEMENT PERSONNEL comme en 4IEME ALL, ou ARTS ET CULTURE NATIONALE comme imprimé) ? | ❓ | Hypothèses : espagnol 2, LCN 1 en DEV_PERS ; les deux lignes restent à coefficient null et sont refusées par le chargeur | L'exemplaire 4IEME ESP n'a aucune note pour ces deux matières |
| Q-43 | Les différences de regroupement d'un niveau ou d'une section à l'autre sont-elles voulues (programme) ou un héritage de l'ancien logiciel ? Faut-il les conserver à l'identique ? (ECM / GEO / HIST dans SCIENCES ET TECHNOLOGIE en 6ème-5ème mais LANGUES ET LITTÉRATURES dès la 4ème ; groupe « ARTS ET CULTURE NATIONALE » au contenu variable ; « VOCATIONAL STUDIES » anglophone = « DÉVELOPPEMENT PERSONNEL » francophone ?) | ❓ | Reproduit à l'identique ; deux groupes DEV_PERS et VOC conservés | Fidélité à l'ancien bulletin |
| Q-44 | Libellés complets et traductions : ECM (imprimé tronqué), SVTEEHB, « SCIENCES » (SVT ?), PCT, TM, APTITUDE DE VIE ; noms français des matières anglophones (ENGLISH LANGUAGE, LITERATURE IN ENGLISH, FRENCH, ECONOMICS, COMMERCE, LOGIC, FOOD AND NUTRITION, BIOLOGY, ADDITIONAL MATHS, HUMAN BIOLOGY) ; FOOD AND NUTRITION (Form 3) = HOME ECONOMICS ? | ❓ | Noms proposés, marqués `_enNameSource = PROPOSED` | Catalogue de matières bilingue |
| Q-45 | Identité de l'école : numéro d'immatriculation (lu « 5LI2GSAD114132125 » ou « SL12... »), numéros de téléphone à imprimer (trois jeux différents selon bulletin, emploi du temps et fiche), e-mail complet, B.P. complète | ❓ | Numéros du bulletin ; immatriculation dans `_extra`, champ `School.registrationNumber` à créer | En-tête officiel de tous les documents |
| Q-46 | Calendrier : dates de début et de fin de l'année et des trimestres 2025-2026 et 2026-2027, dates des séquences | ❓ | Dates approximatives marquées `_confirm` | Année scolaire, trimestres, `examDate` des séquences |
| Q-47 | CHINOIS (imprimé « CNINOIS »), MUSIQUE, CLUB, BIBLIOTHÈQUE (vus sur l'emploi du temps 2026-2027, absents des bulletins) : matières notées avec coefficient, ou activités sans note ? | ❓ | Non chargées | Catalogue de matières et emploi du temps |
| Q-48 | Bulletins manquants : 3ème ESP, 1ère A, 1ère C/D, Terminale, Form 5, Lower Sixth, Upper Sixth s'ils existent, bulletins de trimestre 2 et 3 et bulletins de trimestre francophones | ❓ | Niveaux concernés créés inactifs, aucune classe | Compléter le seed |
| Q-49 | Nom officiel de l'établissement : « COLLÈGE BILINGUE EL SHAMMAH » (bulletins annuels) ou « COLLÈGE BILINGUE INTERNATIONAL EL SHAMMAH » (bulletin de trimestre Form 1) ? Une seule entité ou deux ? | ❓ | « COLLÈGE BILINGUE EL SHAMMAH » | En-tête des bulletins |
| Q-50 | Mentions anglaises observées : WEAK WORK à 7,42 et VERY WEAK WORK à 9,65 (ordre inversé par rapport à la logique). Quelle table de mentions l'école veut-elle, en anglais et en français ? | ❓ | Libellés observés conservés (9,65 = VERY WEAK WORK) | Mention générale du bulletin anglophone |
| Q-51 | Les sections francophone et anglophone suivent-elles exactement le même barème (lettres, compétences, validation 10, profil de classe 12) ? | 💡 | Oui : FR_SECONDARY et EN_SECONDARY identiques, fusionnables | Un ou deux barèmes |
| Q-52 | « PASSED IN n SUBJECTS » : ligne imprimée seulement sur les bulletins anglophones ? À ajouter au gabarit (interrupteur) ou à réserver à la langue anglaise ? | 💡 | Interrupteur `showPassedInSubjects`, actif quand la langue du bulletin est EN | Contenu du bulletin |
| Q-53 | Quels niveaux et quels trimestres utilisent le bulletin de trimestre par compétences (Form 1, trimestre 1 seulement vu) ? Les classes francophones ont-elles un bulletin de trimestre ? (voir Q-26) | ❓ | TERM_COMPETENCY limité à FORM1 | Choix du gabarit par niveau et par période |
| Q-54 | Form 4 ARTS : coefficient 2 pour BIOLOGY (toutes les autres matières académiques ont 5) et absence de CHEMISTRY / PHYSICS ; Form 4 SC : pas de LITERATURE IN ENGLISH, CITIZENSHIP, HISTORY. Voulu ou à corriger ? | ❓ | Reproduit tel quel | Fiabilité du coefficient de Form 4 |
| Q-55 | **Cours groupés** : lesquels sont réellement des séances communes (même enseignant / salle), y compris **entre sections** (ex. 3ème + Form 4 EPS) ? | ❓ | Modèle : `sessionGroupId` ; chargement PDF sans conflit bloquant | Éditeur + BR-09 |
| Q-56 | **Alias EDT** : FRANÇAIS / LITTÉRATURE ↔ expression écrite, orale, correction orthographique, étude de texte ? TM ↔ TM + ESF ? F/N en Form 4 ? | ❓ | Table `TimetableSubjectAlias` ; BR-63 = somme des `weeklyHours` | Chargeur + heures |
| Q-57 | **Bibliothèque** : un enseignant surveille plusieurs classes pendant ses propres cours ? | ❓ | v1 : `ACTIVITY` sans engagement enseignant au conflit | Séances communes / surveillance |
| Q-58 | **Doublons PDF** : vérifier avec l’école les collisions apparentes (ex. mardi période 6, Mme Batoum en 6ème et 4ème) | ❓ | Au chargement : **avertissement**, pas blocage | Qualité des données source |
| Q-59 | **Identité des enseignants** issus du PDF EDT : prénoms, sexe, date de naissance, contact, matricule ; homonymes / ambiguïtés **FOYEH** (Mme Biologie vs Mr English), **MASSAH** (Fabiola vs Jenny sur bulletins 2025-2026), **NONO** (Mr vs Shekina), **JOSEPH** (Maths Form 4) | ❓ | Fiches minimales `needsCompletion=true`, `source=EDT_2026_2027`, `seedKey` ; **ne pas inventer** ; signaler sans trancher | RH + seed privé |
| Q-60 | **Quand et comment** les enseignants reçoivent un accès : e-mail réel, invitation, activation ; la règle « e-mail de login non modifiable » doit rester vraie pour les vrais e-mails | 🟡 | **Option A (retenue)** : fiche `Staff` **sans** `UserAccount` ; action admin `POST /api/v1/staff/{id}/login-access` saisit le vrai e-mail et crée un compte `PENDING` (une seule fois). Pas d’e-mail `@…invalid`. Option B (e-mail réservé + réclamation) rejetée tant que A suffit. | Auth + onboarding enseignants |
| Q-61 | Enseignant d’**allemand** de la **3ème** et de la **1ère A** imprimé « Mme … » (inconnu) | ❓ | Créer la case EDT **sans** `teacherId` plutôt qu’une fiche fantôme | Chargeur EDT |
| Q-62 | **Mot de passe oublié / invitation** : envoi automatique d’e-mail (lien ou MDP temporaire) ? Canal (SMTP école) ? | ❓ | **v1** : `POST /api/v1/auth/forgot-password` (réponse uniforme) + notif in-app admin/directeur ; réinitialisation manuelle via `POST /users/{id}/reset-password` | Auth publique |
| Q-63 | **FRANÇAIS** imprimé sur l’EDT (`?FRANCAIS`) : une matière catalogue, ou répartition vers `EXP_ECR` / `EXP_ORA` / `CORR_ORTH` / `ETUDE_TXT` / `LANG_FR` selon le niveau ? (lié à Q-56) | ❓ | Seed EDT : code cible provisoire `LANG_FR` ; case créée même si aucun `ClassSubject` junior ne matche | Chargeur EDT |
| Q-64 | **Examens dans l’EDT semaine type** : faut-il des cases `ACTIVITY` « composition » qui remplacent les cours la semaine d’examens, ou seulement le calendrier des `EvaluationSession.examDate` (synchro live) ? | 🟡 | **v1** : source unique = sessions (onglet Examens + notif `SESSION_SCHEDULE_CHANGED`) ; **pas** de cases examen dans la grille hebdo (cohérent Q-33 remplacements hors EDT annuel) | EDT ↔ grading |
| Q-65 | **Sans paiement des frais d’inscription**, bloque-t-on l’accès école (notes, EDT, portail parent) ou seulement un **indicateur** de solvabilité ? | ❓ | Proposition v1 : **indicateur** + saisie paiements ; blocages métier en v2 | fees |
| Q-66 | **Qui** définit les montants (inscription + tranches) et **qui** enregistre les paiements ? Direction seule, ou aussi Administration ? | 🟡 | **Figé v1** : barèmes = `DIRECTOR` ; paiements = `DIRECTOR` **ou** `ADMINISTRATION` + `StaffFunction.ACCOUNTANT` (BR-F09) | fees + roles |
| Q-67 | Barème **par classe** ou **par niveau** (toutes les 6èmes identiques) ? Exceptions possibles ? | 💡 | Proposition : **par classe** | fees |
| Q-68 | Si la rentrée est le **7 septembre** (ex. 2026-2027), les 3 mois sont-ils toujours **sept. / oct. / nov.** (dernier jour de chaque mois), ou décalés depuis `startDate` ? | 💡 | Proposition : mois civils du mois de `startDate` puis +1, +2 | fees |
| Q-69 | **Comment** l’école enregistre un paiement en v1 (caisse, Mobile Money, virement) ? Faut-il un reçu PDF ? | 💡 | Proposition v1 : saisie manuelle + référence ; pas de PDF reçu | fees |
| Q-70 | Confirmer la matrice **Solvable / En attente / Insolvable** (`10` § 2) : surtout le cas « n’a pas payé le mois **en cours** » = En attente, et « n’a pas payé le mois **précédent** échu » = Insolvable | 💡 | Proposition figée dans `10` tant que non contredite | fees |
| Q-71 | Élève **sans** inscription année, ou classe **sans** barème : que montrer ? | 💡 | `NOT_APPLICABLE` / « — » | fees + UI |
| Q-72 | **Avant** le 1er des 3 mois, et **après** le 3e mois : quelle solvabilité si tout est payé / si inscription seule manque ? | 💡 | Avant : inscription ; après : toutes tranches + inscription | fees |
| Q-73 | Devise affichée et stockée ? | 💡 | `XAF` | fees |
| Q-74 | Un parent paie **une partie** d’une tranche : la tranche est-elle « payée » seulement au montant complet ? | ❓ | Proposition : payée ssi `paidAmount >= due` | fees |
| Q-75 | Liste fermée des **modes de paiement** ou texte libre ? | ❓ | Proposition : enum court + « Autre » | fees |
| Q-76 | Peut-on **supprimer** un paiement erroné ou seulement l’**annuler** (VOID) ? | 💡 | VOID + motif (audit) | fees |

## 3. Décisions provisoires (résumé)

| Sujet | Décision |
|---|---|
| Séquences et trimestres | 6 séquences, 3 trimestres de 2 séquences ; **BR-25** : ≤ 2 OPEN, même trimestre (un trimestre actif paramétré par l’admin) |
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
1 bis. *(Optionnel)* Confirmation RH si un rôle `PRINCIPAL` distinct du Directeur est requis (Q-38 déjà figé v1 : Principal = DIRECTOR ; SG = visas fiches).
2. Un **bulletin de séquence** s'il existe ; les **bulletins de trimestre 2 et 3** et un bulletin de trimestre d'une classe francophone (Q-03, Q-26).
3. Un bulletin d'une classe **bilingue** (Q-01).
4. Le **règlement d'évaluation** et le **procès-verbal d'un conseil de classe** (Q-09, Q-11, Q-18, Q-29).
5. Un bulletin de **Tle** ou **Form 5**, et de **maternelle / primaire** (Q-16).

Priorité 2 :
6. Un bulletin d'**élève redoublant** et d'un élève **non classé** (Q-07).
7. Le **document d'inscription** et une **carte d'élève** (modules suivants).
8. Les emplois du temps **par enseignant** s'ils existent, la **liste des salles**, les **heures hebdomadaires par matière** (Q-32).
9. À l'oral avec la secrétaire : Q-05, Q-12, Q-13, Q-21, Q-22.
10. **Grille des frais** (inscription + 3 tranches) pour 2026-2027 par classe, et un **exemple de reçu** / cahier de caisse (Q-65…Q-76, doc `10`).
