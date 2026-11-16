# 05 — Rôles et permissions

> Auth / sessions / rôles : module `user` (JWT + refresh, max 2 sessions actives).
> Chaque module contrôle les droits sur **ses** ressources à partir du jeton (voir `08-architecture-modulaire.md` § 6).
> Les contrôles d'appartenance fins (enseignant ↔ ClassSubject, parent ↔ enfants) se branchent dans les services métier au fur et à mesure.

## 1. Rôles

| `Role` | Qui | Portée |
|---|---|---|
| `STUDENT` | Élève (connexion par **e-mail** ; compte créé par l’admin — futur envoi MDP par e-mail) | Ses propres données |
| `PARENT` | Parent / tuteur | **Tous ses enfants, et uniquement eux** (réponse Q-20), via `Student.guardians` |
| `TEACHER` | Enseignant | Ses `ClassSubject` : fiches de notes / report ; EDT : **ses cours** + consultation **générale par classe** |
| `SURVEILLANT_GENERAL` | Surveillant Général | Lecture + visa SG des fiches de report ; UI `/surveillant-general` (Q-38 v1) |
| `ADMINISTRATION` | Secrétariat, censeur | Tout l'établissement, sauf **Direction** (RH), comptes d'administration (user) et réouverture ; régénération / correction fiche de report avec motif |
| `DIRECTOR` | Directeur / chef d'établissement (= Principal v1, Q-38) | Tout l'établissement ; visa final des fiches de report ; approbation des corrections post-dépôt |
| `SUPER_ADMIN` | Développeur | Plateforme, tous établissements |

Un compte peut cumuler plusieurs rôles (enseignant + administration). Le droit effectif est l'union. **Fonction ≠ rôle** : `Staff.functions` décrit le poste ; `UserAccount.roles` décrit les droits.

## 2. Matrice des permissions

✔ autorisé · ◐ sous condition · ✖ interdit

| Ressource · action (module) | Élève | Parent | Ens. | Admin. | Dir. | Super | Condition |
|---|---|---|---|---|---|---|---|
| Établissement, année, séquences : créer / modifier (academic, grading) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| **Paramètres sensibles** (logo, identité, en-têtes, année courante) | ✖ | ✖ | lecture | ✔ | ✔ | ✔ | enseignant / SG : consultation seule ; modification = admin + directeur |
| Ouvrir / clôturer une séquence (grading) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Rouvrir une séquence clôturée | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | provisoire (Q-04) |
| Niveaux, classes, salles : CRUD (academic) | lecture | lecture | lecture | ✔ | ✔ | ✔ | élève/parent : lecture catalogue pour libellés (notes, EDT) ; **SG** : lecture (hub visas) |
| Matières, groupes, **coefficients** : CRUD (academic) | lecture | lecture | lecture | ✔ | ✔ | ✔ | idem ; écriture = admin/dir. |
| Compétences du trimestre (academic) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : ses ClassSubject |
| Affecter un enseignant à une matière de classe | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Barèmes de notation, gabarits de bulletin (grading, reporting) | ✖ | ✖ | ✖ | lecture | ✔ | ✔ | un changement modifie les calculs |
| Enseignants / Autres : CRUD (staff) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : sa fiche en lecture ; admin : pas Direction ; acteur tracé (`createdBy`/`updatedBy`) |
| Direction (HEAD_OF_SCHOOL, CENSOR) : CRUD (staff) | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | admin : ni lecture ni création |
| Autres admins / soutien (SECRETARY…) : lecture (staff) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | admin : lecture seule, aucune action |
| **Comptes d'administration** : CRUD (user) | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | |
| Élèves, tuteurs : CRUD (student) | ◐ | ◐ | ◐ | ✔ | ✔ | ✔ | lecture de ses propres données ; enseignant : élèves de ses classes |
| Inscriptions : CRUD (student) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| **Fiche de notes** : notes, bonus, malus (grading) | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : `ClassSubject.teacherId` = lui **et** séquence `OPEN` |
| **Proposer correction après clôture** | ✖ | ✖ | ✔ | ✖ | ✖ | ✔ | enseignant titulaire ; motif + preuves (BR-80) |
| **Approuver / rejeter correction** | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | directeur seul ; seule voie de modification persistante post-clôture |
| **Notifications in-app** (lire / accuser) | ◐ | ◐ | ✔ | ✔ | ✔ | ✔ | destinataire = compte ; clôture séquence → enseignants (BR-81) |
| **Signalement officiel** enseignant → admin | ✖ | ✖ | ✔ | ✖ | ✖ | ✔ | BR-82 ; pas de chat |
| **Traiter signalements** (accusé / clôture) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | administration + directeur |
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
| **Emploi du temps** : créer, modifier, publier (timetable) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | SG : si `School.timetableSurveillantCanEdit` (défaut false, Q-32) |
| Emploi du temps : consulter + PDF | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | **tous les acteurs scolaires** : EDT publié + PDF ; élève : sa classe **et** EDT général ; parent : classe de l’enfant **et** EDT général (**pas** de demande de modif) ; enseignant : ses cours + général ; SG : lecture par classe + PDF (édition seulement si `timetableSurveillantCanEdit`) |
| **Proposer modif de créneau** (timetable) | ✖ | ✖ | ✔ | ✖ | ✖ | ✔ | enseignant sur ses cases PUBLISHED ; motif (BR-67) |
| **Approuver / refuser modif de créneau** | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | administration **ou** directeur ; applique sur brouillon / révision |
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
4. **Élèves** : identifiant de connexion = **e-mail** (unicité `(schoolId, username)` minuscules) ; le matricule reste l’identité métier, pas le login. Compte créé par l’admin qui renseigne l’e-mail ; MDP temporaire + `mustChangePassword` (envoi automatique par e-mail = feature future). Pas la date de naissance comme secret.
5. **Parents** : compte créé par l'administration à partir de l'e-mail (téléphone = contact) ; **un compte voit plusieurs enfants**. Unicité e-mail par établissement : un e-mail = un compte (cumul de rôles si besoin, pas deux comptes même e-mail).
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
| Principal vs Directeur (Q-38) | **v1** : Visa Principal = `DIRECTOR` ; pas de rôle `PRINCIPAL` distinct |
| Surveillant Général (Q-38) | **v1** : rôle `SURVEILLANT_GENERAL` ; espace UI `/surveillant-general` ; **lecture catalogue academic** + **visa SG** fiches de report (pas de CRUD academic, pas de journal d’audit, pas de bulletins) |
