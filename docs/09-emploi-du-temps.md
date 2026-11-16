# 09 — Emploi du temps (module `timetable`)

> Source : « EMPLOIS DU TEMPS ANNÉE SCOLAIRE 2026/2027 », **12 pages** A4 paysage (une par classe, **40 cases** chacune, aucune vide).
> Classes : 7 francophones (6ème, 5ème, 4ème, 3ème, 2nde C, 1ère A, 1ère C/D) + 5 anglophones (Form 1, 2, 3, 4 ARTS, 4 SC).
> Réponse de Ben : généré **une seule fois par an**. Exigence PDF : **une seule page A4 paysage** par classe.
> Modèle : `model/timetable/` (`TimeSlotGrid`, `Timetable`, `TimetableEntry`, `TimetableSubjectAlias`).
> Données de test : `fixtures/timetable-*.json`. Décisions v1 : Q-32 (saisie manuelle), Q-33 (samedi hors grille), Q-47 (activités).

## 1. Ce que montre le document

**En-tête** : bilingue (logo, ministère, délégations) ; **contacts propres au PDF EDT** (`Timetable.headerContact`, Q-45 — téléphones souvent différents des bulletins). Titre selon la langue de la classe. Nom de classe **complet** (« FORM 4 ARTS » / « FORM 4 SC », pas seulement « FORM 4 »).

**Grille horaire** (lun–ven, toutes classes) :

| N° | Horaire | | N° | Horaire |
|---|---|---|---|---|
| 1 | 7:35 – 8:25 | | 6 | 12:00 – 12:50 |
| 2 | 8:25 – 9:15 | | — | 12:50 – 13:20 DEUXIÈME PAUSE / SECOND BREAK |
| 3 | 9:15 – 10:05 | | 7 | 13:20 – 14:20 (60 min) |
| — | 10:05 – 10:20 PREMIÈRE PAUSE / FIRST BREAK | | 8 | 14:20 – 15:10 |
| 4 | 10:20 – 11:10 | | — | 15:10 – 15:30 NETTOYAGE GÉNÉRAL / GENERAL CLEAN UP |
| 5 | 11:10 – 12:00 | | — | RENTRÉE / CLOSING |

→ `TimeSlotGrid` : heures **stockées**. Format d’impression EN (« 1 : 20 – 2 : 20 ») ≠ donnée.

**Cases** : matière + enseignant. Double / triple période = `periodCount`.

### 1.1 Séances communes (cours groupés) — phase 0

Le même enseignant apparaît dans **plusieurs classes au même créneau** :
- 6ème + 5ème (LCN, Chinois, EPS)
- 1ère A + 1ère C/D (Anglais, Histoire, ECM, EPS, TM, Chinois)
- Form 1 + Form 2 ; Form 4 ARTS + Form 4 SC
- 3ème + Form 4 (EPS, sections différentes)
- Bibliothèque / Ménage général (plusieurs classes, délégué ou surveillant)

→ `TimetableEntry.sessionGroupId` (String?). Deux cases de classes différentes avec le **même** `sessionGroupId` **peuvent** partager enseignant et salle. L’éditeur et le PDF doivent pouvoir **créer / défaire** une séance commune (Q-55).

### 1.2 Options LV2 en parallèle — phase 0

En 4ème, 3ème et 1ère A : case « ALL (prof. A) / ESP (prof. B) » = **deux cours** pour la même classe au même créneau.

→ `optionGroupCode` (ex. `"LV2"`) + `optionCode` (ex. `"ALL"`, `"ESP"`). Index unique `uk_class_slot_option` inclut `optionCode` (`""` si pas d’option).

**Q-41** : l’EDT 2026-2027 **suggère** une seule classe avec options parallèles. On **ne tranche pas** ici le modèle notes (`ClassSubject` / `Enrollment`) — seulement le modèle EDT.

### 1.3 Libellés ≠ matières de bulletin — phase 0

« FRANÇAIS » / « LITTÉRATURE » (6ème–3ème) recouvrent expression écrite, orale, correction orthographique, étude de texte ; « TM » ≈ Travail manuel + ESF (absente de l’EDT). Heures EDT souvent = coefficient (maths 4, anglais 3…).

→ Collection `TimetableSubjectAlias` : alias (variantes fautées incluses) → `classSubjectIds[]`.  
`TimetableEntry.classSubjectIds` ; **jamais** de saisie par texte libre (~59 libellés). Chargeur : remplit `ClassSubject.weeklyHours` si correspondance 1→1 ; rapport des non résolus (Q-30, Q-56).

### 1.4 Samedi / hors grille — phase 0

Trois cours du samedi (blocs multi-heures), **pas seulement 1ère C/D** (Q-33) :

| Classe | Cours | Horaires |
|---|---|---|
| 3ème | Allemand 3 h | 7h30 – 10h30 |
| 1ère A | Allemand 3 h | 10h30 – 13h30 |
| 1ère C/D | TD Chimie 2 h | 8h00 – 10h00 |

→ `DayOfWeek.SATURDAY` + `customStart` / `customEnd` ; conflits sur **heures réelles**.

### 1.5 Activités sans note (v1, Q-47)

CHINOIS, MUSIQUE, CLUB, BIBLIOTHÈQUE, MÉNAGE GÉNÉRAL / GENERAL CLEAN UP → `EntryKind.ACTIVITY`, `Subject.kind = ACTIVITY` si catalogue, **aucun** `ClassSubject`. Une case ACTIVITY **n’engage pas** l’enseignant pour les conflits (surveillance). Si l’école les note plus tard : changement de type.

Autres cas : « CLUB / MATHS / SVT » → `note` ; couleurs de case = purement visuel (non repris).

## 2. Modèle

| Document | Rôle |
|---|---|
| `TimeSlotGrid` | Journée type lun–ven ; périodes, pauses, nettoyage |
| `Timetable` | EDT d’une année ; `revision` ; `DRAFT` → `PUBLISHED` → `ARCHIVED` ; `headerContact` (PDF) |
| `TimetableEntry` | Case : jour, période / hors grille, `slotKey`, `classSubjectIds`, enseignant, salle, `sessionGroupId`, options LV2, libellés figés |
| `TimetableSubjectAlias` | Alias imprimé → `classSubjectIds` (par année / section) |

Index : `uk_class_slot_option`, `idx_teacher_day`, `idx_room_day`, `idx_classsubjects`, `idx_session_group`, `uk_alias_year_section`.

## 3. Règles

- **BR-60** Une fois par an ; semaine type ; une révision `PUBLISHED` par année ; modif → nouvelle révision.
- **BR-61** Une case = classe + jour + créneau (+ option) + matière(s) + enseignant (+ salle) ; ou ACTIVITY.
- **BR-09** (réécrit phase 0) :
  - **Classe** : pas deux cases qui se chevauchent pour la même classe **sauf** options parallèles distinctes (`optionCode` différent dans le même `optionGroupCode`).
  - **Enseignant / salle** : conflit seulement entre cases qui **n’ont pas** le même `sessionGroupId` ; une case `ACTIVITY` n’engage pas l’enseignant.
  - Doublons apparents du PDF source (ex. mardi P6 Mme Batoum en 6ème et 4ème) → **avertissement**, pas blocage au chargement (Q-58).
- **BR-63** Périodes EDT d’une matière (somme des cases liées) ↔ **somme** des `weeklyHours` des `classSubjectIds` : écart = avertissement.
- **BR-64** Enseignant hors `ClassSubject.teacherId` → avertissement (remplacement ?).
- **BR-65** `ACTIVITY` : pas de `classSubjectIds` obligatoires ; pas d’obligation d’enseignant pour le conflit.
- **BR-66** Case hors pause / nettoyage (sauf hors grille explicite).

## 4. Génération, édition, impression

1. **v1 (Q-32)** : saisie manuelle + conflits + duplication d’année ; pas de solveur. Droit SG modifiable : `School.timetableSurveillantCanEdit` (défaut **false**).
2. **Vues** : par classe (page principale), par enseignant, par salle.
3. **PDF** : A4 paysage, **1 page / classe** ; en-tête + `headerContact` ; 5 colonnes × 8 lignes + pauses ; signature Administration.
4. **Test de référence** : charger le PDF 12 × 40 cases → 0 conflit **bloquant** ; régénérer 12 pages ; avertissements pour doublons réels signalés.

## 5. Événements

Publie `timetable.published`. Consomme `academic.class-subject.changed`, `academic.class.changed`, `academic.subject.renamed`, `staff.member.changed` (doc 08). Alias : mise à jour manuelle / chargeur (pas d’événement dédié en v1).

## 6. Permissions (conception)

| Action | Ens. | SG | Admin. | Dir. |
|---|---|---|---|---|
| Lire (ses cours / tout) | ses cours | lecture | tout | tout |
| Modifier / publier | non | si `timetableSurveillantCanEdit` | oui | oui |
| Élève / parent | lecture classe / enfants (plus tard) | | | |

## 7. Plan d’implémentation

| Phase | Contenu | État |
|---|---|---|
| **0** | Modèle : séances communes, options LV2, alias, samedi, activités ; docs 03/08/09/07 | ✅ |
| **A** | API lecture + grille + brouillon | ✅ |
| **B** | Saisie + conflits (BR-09) + séances communes / options | ❌ |
| **C** | Publier + dupliquer + événement | ❌ |
| **D** | PDF 1 page + `headerContact` | ❌ |
| **E** | Front admin/dir (éditeur) | ❌ |
| **F** | Front enseignant (lecture) | ❌ |

Ordre : **0 → A → B → C → D → E → F** (B avant E).

## 8. Questions ouvertes liées
Q-30, Q-32, Q-33, Q-41, Q-45, Q-47, Q-55 (cours groupés), Q-56 (alias FRANÇAIS/TM), Q-57 (bibliothèque / surveillance), Q-58 (doublons PDF). Voir `07-questions-ouvertes.md`.
