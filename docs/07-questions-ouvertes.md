# 07 — Questions ouvertes et décisions provisoires

> Les questions sont posées **à Ben**, qui les transforme en vraies questions simples pour la directrice (jamais en questionnaire à remplir).
> Colonne « Défaut retenu » : ce que le modèle suppose en attendant la réponse. « Impact » : ce qui changerait si la réponse est autre.
> Les identifiants Q-xx sont cités dans le code (KDoc) et dans les autres documents.

## 1. Questions bloquantes pour la démo de décembre

| N° | Question | Pourquoi c'est important | Défaut retenu | Impact si autre |
|---|---|---|---|---|
| **Q-02** | La colonne DEV1..DEV6 est-elle une **note unique saisie** par l'enseignant, ou la **moyenne de plusieurs évaluations** (devoir, interro...) ? Où sont-elles calculées aujourd'hui (Excel) ? | Détermine l'écran de saisie et l'entité `Assessment` | Les deux cas sont supportés ; une évaluation `SEQUENCE_MARK` par séquence suffit | Si moyenne de plusieurs notes : règle d'arrondi à fixer (les notes observées ne sont ni toutes tronquées ni toutes arrondies) |
| **Q-03** | Existe-t-il un **bulletin de trimestre** ou **de séquence** imprimé ? Sa mise en page ? | Le modèle les gère (`ReportPeriodType`), mais le gabarit n'est pas connu | Seul l'annuel est connu | Gabarits PDF supplémentaires |
| **Q-14** | Pour la démo : faut-il **retrouver exactement** les bulletins déjà imprimés (arrondi de l'ancien logiciel) ou accepter un écart d'un centième sur 2 moyennes ? | Choix de `LEGACY_DOUBLE` ou `HALF_UP` | `HALF_UP` en production ; `LEGACY_DOUBLE` pour le rejeu | Aucun changement de modèle |
| **Q-19** | Quelle est la relation de l'école avec **SKYSOFT / Easy School Mobile** ? Garde-t-elle ce service (USSD, application parents) ? Peut-on exporter ses données ? Ces bulletins sont-ils bien ceux de l'école cliente ? | Positionnement commercial, migration, périmètre « portail parents » | Hypothèse : l'école imprime ses bulletins avec ce logiciel ; notes sur Excel | Périmètre et argumentaire de l'offre |
| **Q-01** | Que veut dire **« bilingue / les deux »** pour une classe : classe mixte avec matières dans les deux langues ? Une langue de bulletin par classe ou par élève ? | Langue du bulletin et catalogue de matières | `SchoolClass.reportLanguage` unique par classe, choisi à la création | Langue par élève → champ sur `Enrollment` |

## 2. Règles de bulletin

| N° | Question | Défaut retenu |
|---|---|---|
| **Q-06** | Classement en cas d'**ex aequo** : même rang (1, 2, 2, 4) ou (1, 2, 2, 3) ? Départage sur la moyenne exacte ou arrondie ? | `COMPETITION` (1, 2, 2, 4), moyenne exacte |
| **Q-07** | Pourquoi **effectif 13 mais 12 classés** (et 18 / 17) ? Élève arrivé en cours d'année, sans notes, abandon ? Qui décide de ne pas classer ? | Non classé = pas de moyenne calculable ; compté dans l'effectif |
| **Q-08** | « **N° ON ROLL** » : le numéro de l'élève dans la liste, ou l'effectif de la classe ? | Effectif de la classe ; `Enrollment.rollNumber` reste disponible |
| **Q-09** | **Mention générale** (« TRAVAIL INSUFFISANT », « FAIR »...) : liste complète et seuils, en français et en anglais ? | Non renseignée (`overallMentionBands` vide) |
| **Q-10** | **Appréciation par matière** : y a-t-il des lettres sous C (D, E) ? A à C suffisent-ils pour les notes très basses ? | A ≥ 16 ; B+ ≥ 14 ; B ≥ 12 ; C en dessous |
| **Q-11** | **Tableau d'honneur, encouragements, félicitations, avertissements, blâmes** : seuils automatiques (moyenne, absences) ou décision du conseil ? | Décision saisie manuellement ; `distinctionRules` vide |
| **Q-12** | **Note manquante** (élève entré en cours d'année, absent, séquence non faite) : on divise par les séquences notées ou on compte 0 ? Une matière sans aucune note est-elle exclue de la moyenne ? | `IGNORE` ; matière exclue |
| **Q-13** | Élève **dispensé** (EPS...) : la matière sort-elle de la moyenne de cet élève ? | Oui, exclue pour lui |
| **Q-15** | Les **couleurs des notes** (bleu, rouge, noir) ont-elles un sens (≥10, <10, séquence) ? | Sans sens fonctionnel |
| **Q-18** | **Passage en classe supérieure** : règle (moyenne annuelle ≥ 10 ? conseil de classe souverain ?). Procès-verbal du conseil ? | `StudentPeriodReview.councilDecision` saisi à la main |
| **Q-21** | **Absences et retards** : qui les saisit, en quelle unité (heures entières ou demi-heures), absences justifiées comment ? | Heures entières, saisies par l'administration |
| **Q-22** | **Enseignant remplacé** en cours d'année : le bulletin annuel affiche-t-il le dernier enseignant ou les deux ? | Dernier enseignant (nom figé à la génération) |
| **Q-23** | **Signature, cachet, copies** : le bulletin papier est-il signé et tamponné à la main après impression ? Signature numérique souhaitée ? | Zone de signature vide, nom du chef imprimé |

## 3. Structure de l'école

| N° | Question | Défaut retenu |
|---|---|---|
| **Q-04** | La **clôture d'une séquence** est-elle faite pour toute l'école ou classe par classe ? Qui clôture, qui peut rouvrir ? | Globale ; administration clôture ; directeur rouvre |
| **Q-05** | **Matricule** : quelle règle (« 25 » = année puis numéro ?) ? Généré par l'école, par un organisme, ou par le logiciel ? Conservé d'une année à l'autre ? | Texte libre unique dans l'école ; génération à définir |
| **Q-16** | **Maternelle et primaire** : notes chiffrées sur 20, lettres, compétences ? Bulletins par séquence, trimestre ? | Non modélisé : bulletin du secondaire seulement |
| **Q-17** | **2nd cycle** : séries (A4, C, D, TI, Arts, Sciences...) avec coefficients différents par série ? | `SchoolClass.trackCode` + `ClassSubject` par classe, donc géré sans changement |
| **Q-20** | **Parents** : comment doivent-ils consulter les notes (navigateur, téléphone, USSD, WhatsApp) ? Un numéro de téléphone par parent ? Consentement pour les données des enfants ? | Navigateur uniquement, plus tard |

## 4. Décisions provisoires (résumé)

| Sujet | Décision |
|---|---|
| Séquences et trimestres | 6 séquences, 3 trimestres de 2 séquences |
| Formule générale | Σ(moyenne matière × coefficient) ÷ Σ coefficients |
| Moyenne annuelle | Σ totaux ÷ Σ coefficients, égale à la moyenne des 3 trimestres quand tout est noté |
| Rang | Sur la moyenne exacte, parmi les élèves classés, ex aequo en compétition |
| Seuils | Validation de matière : 10 ; « Moyenne ≥ » de la classe : 12 |
| Enseignants | Entité unique `Staff` ; lien matière-classe-enseignant par `ClassSubject` |
| Bulletin | Photographie figée, révisions successives, parent : annuel publié seulement |
| Arrondi | `HALF_UP` ; `LEGACY_DOUBLE` disponible pour la comparaison avec l'ancien logiciel |
| Marque | Aucun élément de marque d'un prestataire tiers dans les bulletins du nouveau système |

## 5. Documents complémentaires à demander à la collaboratrice

À ajouter à la liste du guide de collecte (priorité 1, avant la démo) :

1. Un **bulletin de trimestre** et un **bulletin de séquence**, s'ils existent (Q-03).
2. Un bulletin de **maternelle** et de **primaire**, en français et en anglais (Q-16).
3. Un bulletin de **2nde, 1ère ou Tle** avec série ; un bulletin d'une classe **bilingue** (Q-17, Q-01).
4. Un bulletin d'**élève redoublant** et d'**élève non classé** (Q-07).
5. Le **fichier Excel de notes d'origine** d'une classe (Q-02, Q-12).
6. Le **règlement d'évaluation** et le **procès-verbal de conseil de classe** (Q-09, Q-11, Q-18).
7. Le **document d'inscription** et une **carte d'élève** (modules suivants).
8. À l'oral, avec la secrétaire : Q-05, Q-12, Q-13, Q-21.
