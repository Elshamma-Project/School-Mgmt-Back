# 05 — Rôles et permissions

> Auth / sessions / rôles : module `user` (JWT + refresh, max 2 sessions actives).
> Chaque module contrôle les droits sur **ses** ressources à partir du jeton (voir `08-architecture-modulaire.md` § 6).
> Les contrôles d'appartenance fins (enseignant ↔ ClassSubject, parent ↔ enfants) se branchent dans les services métier au fur et à mesure.

## 1. Rôles

| `Role` | Qui | Portée |
|---|---|---|
| `STUDENT` | Élève (connexion par matricule, activée plus tard) | Ses propres données |
| `PARENT` | Parent / tuteur | **Tous ses enfants, et uniquement eux** (réponse Q-20), via `Student.guardians` |
| `TEACHER` | Enseignant | Ses `ClassSubject` : ses fiches de notes, ses fiches de report, son emploi du temps |
| `SURVEILLANT_GENERAL` | Surveillant Général | Lecture + visa SG des fiches de report (Q-38 : périmètre exact à confirmer) |
| `ADMINISTRATION` | Secrétariat, censeur | Tout l'établissement, sauf comptes d'administration et réouverture ; régénération / correction fiche de report avec motif |
| `DIRECTOR` | Directeur / chef d'établissement (= Principal provisoirement, Q-38) | Tout l'établissement ; visa final des fiches de report ; approbation des corrections post-dépôt |
| `SUPER_ADMIN` | Développeur | Plateforme, tous établissements |

Un compte peut cumuler plusieurs rôles (enseignant + administration). Le droit effectif est l'union. **Fonction ≠ rôle** : `Staff.functions` décrit le poste ; `UserAccount.roles` décrit les droits.

## 2. Matrice des permissions

✔ autorisé · ◐ sous condition · ✖ interdit

| Ressource · action (module) | Élève | Parent | Ens. | Admin. | Dir. | Super | Condition |
|---|---|---|---|---|---|---|---|
| Établissement, année, séquences : créer / modifier (academic, grading) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Ouvrir / clôturer une séquence (grading) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Rouvrir une séquence clôturée | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | provisoire (Q-04) |
| Niveaux, classes, salles : CRUD (academic) | ✖ | ✖ | lecture | ✔ | ✔ | ✔ | |
| Matières, groupes, **coefficients** : CRUD (academic) | ✖ | ✖ | lecture | ✔ | ✔ | ✔ | |
| Compétences du trimestre (academic) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : ses ClassSubject |
| Affecter un enseignant à une matière de classe | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Barèmes de notation, gabarits de bulletin (grading, reporting) | ✖ | ✖ | ✖ | lecture | ✔ | ✔ | un changement modifie les calculs |
| Enseignants : CRUD (staff) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : sa fiche en lecture |
| **Comptes d'administration** : CRUD (user) | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | |
| Élèves, tuteurs : CRUD (student) | ◐ | ◐ | ◐ | ✔ | ✔ | ✔ | lecture de ses propres données ; enseignant : élèves de ses classes |
| Inscriptions : CRUD (student) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| **Fiche de notes** : notes, bonus, malus (grading) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : `ClassSubject.teacherId` = lui **et** séquence `OPEN` |
| **Corriger après clôture** | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | motif obligatoire, trace `afterSessionClosed` |
| Consulter les notes de séquence | ◐ | ◐ | ◐ | ✔ | ✔ | ◐ | élève : les siennes ; parent : ses enfants (`canViewGrades`) ; enseignant : ses matières |
| Exporter sa fiche de notes (plusieurs pages) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : ses fiches |
| **Fiche de report** : générer / soumettre (grading) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : ses ClassSubject |
| Fiche de report : visa Surveillant Général | ✖ | ✖ | ✖ | ◐ | ✔ | ✔ | rôle `SURVEILLANT_GENERAL` (ou admin/dir.) |
| Fiche de report : visa Principal | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | `DIRECTOR` (= Principal, Q-38) |
| Fiche de report : rejeter / régénérer / corriger | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | motif ; correction post-dépôt : approbation Principal |
| Fiche de report : lire / PDF | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : les siennes ; SG/admin/dir. : toutes ; **élève/parent : ✖** |
| Saisir absences, retards, sanctions, décisions (reporting) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Générer, valider, publier, imprimer des bulletins | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| **Télécharger le bulletin annuel** | ◐ | ◐ | ✖ | ✔ | ✔ | ◐ | `PUBLISHED`, période `ANNUAL`, élève concerné (parent : tous ses enfants) |
| Journal d'audit des notes | ✖ | ✖ | ✖ | ✔ | ✔ | ◐ | |
| **Emploi du temps** : créer, modifier, publier (timetable) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Emploi du temps : consulter | ◐ | ◐ | ◐ | ✔ | ✔ | ✔ | élève : sa classe ; parent : classes de ses enfants ; enseignant : ses cours |
| Comptes élèves et parents : créer, réinitialiser (user) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Plusieurs établissements, support | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ | accès journalisé |

## 3. Règles d'appartenance (côté serveur, jamais seulement dans l'interface)

| Rôle | Contrôle |
|---|---|
| Tous (sauf `SUPER_ADMIN`) | `schoolId` du jeton = `schoolId` du document |
| `TEACHER` | il existe un `ClassSubject` avec `teacherId = staffId du jeton`, bonne classe, et la séquence est `OPEN` |
| `PARENT` | `Student.guardians` contient un lien avec `guardianId = guardianId du jeton` et `canViewGrades` (ou `canDownloadReportCards`) ; la liste « mes enfants » vient de là |
| `STUDENT` | `studentId` demandé = `studentId` du profil du compte |
| Bulletin | élève ou parent : seulement `period.type = ANNUAL` et `status = PUBLISHED` |

Contrôler **dans la couche service** de chaque module. Entre modules, le module appelant transmet l'identité du jeton ; le module appelé revérifie.

## 4. Règles transversales pour l'étape sécurité

1. **Refus par défaut** : toute route sans règle explicite est refusée.
2. **Les identifiants viennent du jeton**, jamais du corps de la requête.
3. **Authentification** : jeton d'accès court + jeton de renouvellement ; mots de passe bcrypt ou argon2 ; verrouillage après échecs répétés ; limitation de débit.
4. **Élèves** : identifiant = matricule ; mot de passe temporaire à changer (`mustChangePassword`) ; pas la date de naissance comme secret.
5. **Parents** : compte créé par l'administration à partir du téléphone ou de l'e-mail ; **un compte voit plusieurs enfants**.
6. **Journaliser** : connexions, échecs, consultations de bulletins, corrections après clôture, accès `SUPER_ADMIN`.
7. **Données de mineurs** : minimiser les réponses, aucun identifiant d'élève dans les logs.
8. **DTO obligatoires** : jamais de `passwordHash`.
9. **Chiffrer** les sauvegardes ; HTTPS partout.
10. **Un seul chemin d'écriture des notes** (`GradeService` / `SequenceMarkService`).
11. **Jeton unique pour tous les modules** (futur microservices) : identité, `schoolId`, rôles, profil.

## 5. Décisions provisoires à valider

| Sujet | Hypothèse |
|---|---|
| Rouvrir une séquence | Directeur seulement |
| Modifier le barème et les gabarits | Directeur seulement |
| Saisie des absences et sanctions | Administration seulement |
| Parents et bulletins | Seul le bulletin annuel est téléchargeable ; les notes sont visibles en temps réel |
| Élève qui se connecte | Plus tard |
| Principal vs Directeur (Q-38) | Visa Principal = `DIRECTOR` jusqu'à confirmation d'un rôle `PRINCIPAL` distinct |
| Surveillant Général (Q-38) | Rôle `SURVEILLANT_GENERAL` créé côté modèle ; périmètre RH à confirmer |
