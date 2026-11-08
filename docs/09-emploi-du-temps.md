# 09 — Emploi du temps (module `timetable`)

> Source : « EMPLOIS DU TEMPS ANNÉE SCOLAIRE 2026/2027 », 12 pages A4 paysage (une par classe).
> Réponse de Ben : il se génère **une seule fois par an**. Exigence : **une seule page A4** par document imprimé (emploi du temps). Exception documentée ailleurs : fiche de report de notes (`GradeReportSheet`) peut dépasser une page (AGENTS.md règle 9, Q-35).
> Modèle : `model/timetable/` (`TimeSlotGrid`, `Timetable`, `TimetableEntry`). Données de test anonymisées : `fixtures/timetable-*.json`.

## 1. Ce que montre le document

**Pages** : 7 classes francophones (6ème, 5ème, 4ème, 3ème, 2nde C, 1ère A, 1ère C/D) en français (« EMPLOIS DU TEMPS ANNÉE SCOLAIRE 2026/2027 », LUNDI à VENDREDI, HEURES / JOURS) et 5 classes anglophones (Form 1, 2, 3, 4 Arts, 4 Science) en anglais (« 2026/2027 SCHOOL YEAR CLASS TIME TABLE », MONDAY to FRIDAY, PERIODS / DAYS). Même en-tête bilingue que les bulletins (logo, ministère, délégations), signature « L'Administration » en bas.

**Grille horaire** (identique du lundi au vendredi, pour toutes les classes) :

| N° | Horaire | | N° | Horaire |
|---|---|---|---|---|
| 1 | 7:35 – 8:25 | | 6 | 12:00 – 12:50 |
| 2 | 8:25 – 9:15 | | — | 12:50 – 13:20 DEUXIÈME PAUSE / SECOND BREAK |
| 3 | 9:15 – 10:05 | | 7 | 13:20 – 14:20 (60 min) |
| — | 10:05 – 10:20 PREMIÈRE PAUSE / FIRST BREAK | | 8 | 14:20 – 15:10 |
| 4 | 10:20 – 11:10 | | — | 15:10 – 15:30 NETTOYAGE GÉNÉRAL / GENERAL CLEAN UP |
| 5 | 11:10 – 12:00 | | — | RENTRÉE / CLOSING |

→ `TimeSlotGrid` : les heures sont **stockées** (la période 7 dure 60 min, les autres 50), pas calculées. Les horaires s'écrivent différemment en anglais (« 1 : 20 – 2 : 20 » pour 13:20 – 14:20) : c'est du format d'impression, pas de la donnée.

**Cases** : chaque case = **matière + enseignant** (« FRANÇAIS (Mme X) »). Un cours de 2 périodes occupe 2 lignes (la même case répétée) ; certains cours font 3 périodes (Biology, Geography en Form 4 ARTS).

**Cas particuliers observés** :

| Observé | Traitement |
|---|---|
| CLUB, BIBLIOTHÈQUE / LIBRARY, MUSIQUE, ÉDUC. ART, MÉNAGE GÉNÉRAL / GENERAL CLEAN UP (« Délégué ») | `EntryKind.ACTIVITY` + `ActivityType` ; `Subject.kind = ACTIVITY` pour celles qui sont des matières sans note |
| « CLUB / MATHS / SVT » | une case, libellé libre dans `note` |
| « TD CHIMIE », « H.BIOLOGY », « A.MATHS », « F/N » | matière ou libellé abrégé : `Subject.timetableAlias` ; `F/N` à clarifier (Q-30) |
| « SAMEDI : CHIMIE 2H, 8h00 – 10h00 » (1ère C/D) | cours **hors grille** : `customStart` / `customEnd` |
| Case mise en couleur (orange, vert) | purement visuel (non repris) |
| Abréviations de matières : SVTEEHB, PCT, LCN, ECM, TM, EPS | `Subject.timetableAlias` |
| Même enseignant dans plusieurs classes et plusieurs matières | `Staff` unique ; le contrôle de conflits porte sur le créneau |
| Une matière en 2 groupes de la même classe | non observé (à ne pas supposer) |
| Fautes de frappe (« CNINOIS », « ECM (Mme BATOUM)) ») | l'import / la saisie utilise les identifiants, pas le texte |

## 2. Modèle

| Document | Rôle |
|---|---|
| `TimeSlotGrid` | La journée type : périodes de cours numérotées, pauses, nettoyage, fermeture ; jours de cours (lundi-vendredi). Une par année scolaire (copiable d'une année à l'autre) |
| `Timetable` | L'emploi du temps d'une année : grille utilisée, `revision`, statut `DRAFT` → `PUBLISHED` → `ARCHIVED`. Une seule révision `PUBLISHED` à la fois |
| `TimetableEntry` | Une case d'une classe : jour, période de départ, nombre de périodes, `slotKey` (« P4 » ou « T0800 »), matière de classe, enseignant, salle, activité, libellé, copies d'impression (`subjectLabel`, `teacherLabel`) |

Liens par **identifiants** vers `academic` (classe, `ClassSubject`, salle) et `staff` (enseignant) ; le module `timetable` ne lit jamais leurs bases (voir `08-architecture-modulaire.md`). Les libellés copiés permettent d'imprimer sans appeler d'autre module ; ils sont rafraîchis par les événements `academic.subject.renamed` et `staff.member.changed` et à la publication.

Index clés : `uk_class_slot` (une case par classe, jour et créneau), `idx_teacher_day`, `idx_room_day` (contrôle de conflits), `idx_classsubject` (comptage des heures).

## 3. Règles

- **BR-60** Généré **une fois par an** ; semaine type répétée ; une révision publiée par année. Une modification en cours d'année crée une nouvelle révision (l'ancienne passe en `ARCHIVED`).
- **BR-61** Une case = classe + jour + créneau + matière + enseignant (+ salle).
- **BR-09** Conflits : un enseignant, une salle ou une classe ne peut pas être sur deux cases qui se chevauchent. Le contrôle se fait dans le service (MongoDB ne peut pas l'exprimer) sur les cases de la même révision, en tenant compte de `periodCount` et des cours hors grille.
- **BR-63** Le nombre de périodes d'une matière dans la semaine doit correspondre à `ClassSubject.weeklyHours` : écart = avertissement (pas de blocage).
- **BR-64** Un enseignant ne peut être placé que sur un `ClassSubject` dont il est `teacherId` (sinon avertissement : remplacement ?).
- **BR-65** Une case `ACTIVITY` n'a ni `classSubjectId` ni obligation d'enseignant.
- **BR-66** Une case ne peut tomber sur une pause ni sur le nettoyage (sauf cours hors grille explicite).

## 4. Génération et impression : UNE page A4

1. **Génération (une fois par an)** : à partir de `ClassSubject.weeklyHours` et des enseignants. Deux modes, à décider (Q-32) : **saisie manuelle** guidée par l'interface (glisser-déposer matière → créneau, contrôle de conflits en direct) ou **génération automatique** par un solveur de contraintes (non prévu à ce stade). La première version du logiciel doit au minimum permettre la **saisie manuelle avec contrôle de conflits** et la **duplication d'une année sur l'autre**.
2. **Vues** : par classe (page principale), par enseignant (tous ses créneaux, toutes classes), par salle.
3. **PDF** : A4 **paysage**, une page par vue (`PageFormat.A4_LANDSCAPE`) ; en-tête bilingue, titre selon la langue de la classe (`SchoolClass.defaultLanguage`) ; 5 colonnes (jours) × 8 lignes + pauses ; la hauteur des lignes et la taille de police s'ajustent pour tenir sur une page (un cours à nom long passe sur 2 lignes comme dans l'original) ; signature « L'Administration » / « Administration ».
4. **Langues** : français ou anglais pour chaque classe ; une classe bilingue doit pouvoir être imprimée dans les deux (Q-01). Les noms de matières viennent de `Subject.name` / `timetableAlias`, les jours et libellés de pause de `LocalizedText`.
5. **Lots** : un PDF par classe, ou un lot de toutes les classes (une page par classe, pas de page vide).

## 5. Événements

Publie `timetable.published`. Consomme `academic.class-subject.changed` (heures, enseignant), `academic.class.changed`, `academic.subject.renamed`, `staff.member.changed` (voir § 4 du doc 08).

## 6. Permissions (conception)

| Action | Enseignant | Administration | Directeur |
|---|---|---|---|
| Lire l'emploi du temps de sa classe / de ses cours | ses cours | tout | tout |
| Modifier / publier | non | oui | oui |
| Élève et parent | lecture de la classe de l'élève (parent : classes de ses enfants) | | |

## 7. Questions ouvertes
Q-30 (matière « F/N »), Q-32 (génération manuelle ou automatique, contraintes de disponibilité des enseignants), Q-33 (cours du samedi, remplacements ponctuels d'enseignants absents : modifient-ils l'emploi du temps annuel ?). Voir `07-questions-ouvertes.md`.
