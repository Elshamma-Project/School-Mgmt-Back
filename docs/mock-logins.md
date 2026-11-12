# Comptes mock (développement)

> **DEV uniquement.** Créés au démarrage si `smels.security.mock-users.enabled=true`  
> (défaut local). Désactivés en prod (`application-prod.properties`).  
> Aucune donnée réelle d’élève / enseignant (BR-43).

## Tenant El Shammah

La **structure académique** (école, années, niveaux, matières, classes, barèmes, sessions…)  
provient du seed JSON `docs/el-shammah-data/seed-el-shammah.json`  
(`ElShammahAcademicSeedRunner`, insert-if-absent).  
Ce fichier mock **ne crée plus** d’années / classes / notes fictives.

| | |
|---|---|
| `schoolId` canonique | `school-elshammah` |
| `schoolCode` | `ELSHAMMAH` |
| Front | `NEXT_PUBLIC_DEFAULT_SCHOOL_ID=school-elshammah` |

> Base locale encore sur `school-demo-elshammah` : le seed se rattache en douceur,  
> mais le plus propre est de **vider Mongo** puis redémarrer.

## Paramètres communs

| | |
|---|---|
| Mot de passe (tous) | `DemoPass123!` |
| API login | `POST /api/v1/auth/login` avec **email** + password |
| `mustChangePassword` | `false` (prêts à l’emploi) |

## Logins (e-mail)

| Rôle | E-mail | schoolId dans le body* | Profil lié |
|---|---|---|---|
| SUPER_ADMIN | `superadmin@smels.local` | auto (repli plateforme) | — |
| DIRECTOR (Principal) | `directeur@elshammah.demo` | `school-elshammah` | `staff-demo-director` |
| ADMINISTRATION | `admin@elshammah.demo` | `school-elshammah` | `staff-demo-admin` |
| SURVEILLANT_GENERAL | `surveillant@elshammah.demo` | `school-elshammah` | `staff-demo-sg` |
| TEACHER | `enseignant@elshammah.demo` | `school-elshammah` | `staff-demo-teacher` |
| PARENT | `parent.demo@elshammah.demo` | `school-elshammah` | `guardian-demo-parent` |
| STUDENT | `eleve.demo@elshammah.demo` | `school-elshammah` | `student-demo-01` |

\* Le front envoie le `schoolId` résolu depuis `.env` / domaine ; le SUPER_ADMIN est détecté automatiquement.

## Exemple

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "directeur@elshammah.demo",
  "password": "DemoPass123!",
  "schoolId": "school-elshammah"
}
```

## Configuration

```properties
smels.security.mock-users.enabled=true
smels.security.mock-users.password=DemoPass123!
smels.security.mock-users.school-id=school-elshammah
smels.security.mock-users.school-code=ELSHAMMAH
smels.seed.el-shammah.enabled=true
smels.seed.el-shammah.school-id=school-elshammah
smels.security.bootstrap.username=superadmin@smels.local
```

## Notes

- Le seed comptes est **idempotent** et migre les anciens usernames courts (`directeur`, …) vers l’e-mail.
- Les fiches `Staff` démo reçoivent le **même e-mail** que le compte lié (`contact.email`).
- Pas d’inscription ni de notes auto pour l’élève démo tant que le rattachement aux vraies classes El Shammah n’est pas fait (passe suivante).
- Sessions DEV1…DEV6 : celles du JSON El Shammah (par année) ; les gérer via l’UI hub Notes / Séquences.
- Ne jamais committer de vrais mots de passe ni de données nominatives réelles.

### Jeu de démo enseignant (`TeacherDemoSeedRunner`, @Order 25)

Actif si `smels.security.mock-users.enabled=true` (insert-if-absent, ne réécrit jamais une saisie UI).

- `enseignant@elshammah.demo` peut ouvrir **`/teacher/grades`** et **`/teacher/classes`** après le seed.
- Classe : **6ème** francophone de l’année courante (copie de structure depuis la 6ème d’une autre année si elle n’existe pas encore pour l’année courante).
- Affectation de `staff-demo-teacher` sur 1–2 matières (Maths, puis langue française) **uniquement si `teacherId` est vide**.
- **Roster fictif** (aucun nom réel) : `stu-demo-01` … `stu-demo-10`, nom `DEMO …`, matricules `DEMO-2026-01` … `DEMO-2026-10`, inscriptions `enr-demo-01` … `enr-demo-10` (rollNumber 1…10).
- Séquence 1 ouverte (via `EvaluationSessionService`), une colonne `SEQUENCE_MARK` par fiche, quelques notes (certaines **PENDING**), puis fiche de report du trimestre 1 générée.

### Remise à zéro locale

```bash
# Vider la base puis relancer le back (recrée le tenant propre)
mongosh --eval 'db.getSiblingDB("smels-db").dropDatabase()'
./gradlew bootRun
```
