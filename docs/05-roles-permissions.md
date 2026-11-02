# 05 — Rôles et permissions

> **Conception uniquement.** L'authentification et les autorisations ne sont pas implémentées (étape « Sécurité » de la feuille de route).
> Le modèle contient seulement l'énumération `Role` et le squelette `UserAccount`.

## 1. Rôles

| `Role` | Qui | Portée |
|---|---|---|
| `STUDENT` | Élève (connexion avec son matricule, activée plus tard) | Ses propres données |
| `PARENT` | Parent / tuteur | Les élèves liés par `Student.guardians` |
| `TEACHER` | Enseignant | Ses `ClassSubject` (matières et classes affectées) |
| `ADMINISTRATION` | Secrétariat, censeur, surveillants | Tout l'établissement, sauf comptes d'administration et réouverture |
| `DIRECTOR` | Directeur / chef d'établissement | Tout l'établissement |
| `SUPER_ADMIN` | Développeur (vous) | Plateforme, tous établissements |

Un compte peut cumuler plusieurs rôles (un enseignant qui est aussi dans l'administration). Le droit effectif est l'union des rôles.

**Fonction ≠ rôle** : `Staff.functions` (enseignant, secrétaire, chef d'établissement...) décrit le poste ; `UserAccount.roles` décrit les droits d'accès. Le poste suggère le rôle par défaut mais ne le fixe pas.

## 2. Matrice des permissions

✔ autorisé · ◐ autorisé sous condition (voir la colonne « Condition ») · ✖ interdit

| Ressource · action | Élève | Parent | Enseignant | Admin. | Directeur | Super admin | Condition |
|---|---|---|---|---|---|---|---|
| Établissement, année, trimestres, séquences : créer / modifier | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Ouvrir / clôturer une séquence | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Rouvrir une séquence clôturée | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | provisoire (Q-04) |
| Niveaux, classes, salles : CRUD | ✖ | ✖ | lecture | ✔ | ✔ | ✔ | |
| Matières, groupes, **coefficients** : CRUD | ✖ | ✖ | lecture | ✔ | ✔ | ✔ | |
| Affecter un enseignant à une matière de classe | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Barèmes de notation | ✖ | ✖ | ✖ | lecture | ✔ | ✔ | un changement modifie les calculs |
| Enseignants : CRUD | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : sa propre fiche en lecture |
| **Comptes d'administration** : CRUD | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ | |
| Élèves, tuteurs : CRUD | ◐ | ◐ | ◐ | ✔ | ✔ | ✔ | lecture seule de ses propres données ; enseignant : élèves de ses classes |
| Inscriptions : CRUD | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Évaluations et notes : créer / modifier / supprimer | ✖ | ✖ | ◐ | ✔ | ✔ | ✔ | enseignant : `ClassSubject.teacherId` = lui **et** séquence `OPEN` |
| **Corriger une note après clôture** | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | motif obligatoire, trace `afterSessionClosed` |
| Consulter les notes | ◐ | ◐ | ◐ | ✔ | ✔ | ◐ | élève : les siennes ; parent : ses enfants (`canViewGrades`), notes publiées ; enseignant : ses matières |
| Saisir absences, retards, sanctions, décisions du conseil | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Générer des bulletins (brouillon) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Valider / publier un bulletin | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Imprimer un bulletin (PDF) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| **Télécharger le bulletin annuel** | ◐ | ◐ | ✖ | ✔ | ✔ | ◐ | statut `PUBLISHED`, période `ANNUAL`, uniquement pour l'élève concerné |
| Journal d'audit des notes : lecture | ✖ | ✖ | ✖ | ✔ | ✔ | ◐ | |
| Comptes élèves et parents : créer, réinitialiser | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | |
| Plusieurs établissements, support technique | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ | accès aux données d'élèves : journalisé |

## 3. Règles d'appartenance (à vérifier côté serveur, jamais seulement dans l'interface)

| Rôle | Contrôle |
|---|---|
| Tous (sauf `SUPER_ADMIN`) | `schoolId` du jeton = `schoolId` du document |
| `TEACHER` | existe un `ClassSubject` avec `teacherId = staffId de l'utilisateur`, `classId` = classe de l'évaluation, et `EvaluationSession.status = OPEN` |
| `PARENT` | `Student.guardians` contient un `GuardianLink` avec `guardianId = guardianId de l'utilisateur` et `canViewGrades` (ou `canDownloadReportCards`) |
| `STUDENT` | `studentId` demandé = `studentId du profil` du compte |
| Bulletin | un élève ou un parent ne voit un `ReportCard` que si `period.type = ANNUAL` et `status = PUBLISHED` |

Contrôler **dans la couche service**, pas seulement dans les contrôleurs, pour qu'aucun chemin d'appel ne contourne la règle.

## 4. Règles transversales pour l'étape sécurité

1. **Refus par défaut** : toute route sans règle explicite est refusée.
2. **Les identifiants viennent du jeton**, jamais du corps de la requête : l'auteur d'une note est l'utilisateur authentifié.
3. **Authentification** : jetons d'accès courts + jeton de renouvellement ; mots de passe hachés avec bcrypt ou argon2 ; verrouillage après échecs répétés (`failedLoginCount`, `lockedUntil`) ; limitation de débit sur la connexion.
4. **Élèves** : identifiant = matricule ; premier accès par mot de passe temporaire à changer (`mustChangePassword`). Éviter d'utiliser la date de naissance comme secret.
5. **Parents** : compte créé par l'administration à partir du téléphone ou de l'e-mail du tuteur ; un parent voit plusieurs enfants avec un seul compte.
6. **Journaliser** : connexions, échecs, consultations de bulletins, corrections après clôture, accès `SUPER_ADMIN` à des données d'élèves.
7. **Données de mineurs** : minimiser dans les réponses (pas de champs inutiles), aucun identifiant d'élève dans les logs applicatifs.
8. **Séparation** : l'API ne renvoie jamais `passwordHash` ; DTO obligatoires.
9. **Sauvegardes et chiffrement** : chiffrer les sauvegardes de la base ; HTTPS partout.
10. **Un seul chemin d'écriture des notes** : toute écriture passe par le service qui applique les règles BR-10 à BR-15 et écrit l'audit.

## 5. Décisions provisoires à valider

| Sujet | Hypothèse retenue |
|---|---|
| Rouvrir une séquence | Directeur seulement |
| Qui peut modifier le barème | Directeur seulement |
| Saisie des absences et sanctions | Administration seulement (pas le professeur principal) |
| Parents et bulletins trimestriels | Seul le bulletin annuel est téléchargeable (selon votre cahier : « télécharge uniquement en fin d'année ») |
| Élève qui se connecte | Plus tard ; notes publiées uniquement |
