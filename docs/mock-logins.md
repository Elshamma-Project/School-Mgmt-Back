# Comptes mock (développement)

> **DEV uniquement.** Créés au démarrage si `smels.security.mock-users.enabled=true`  
> (défaut local). Désactivés en prod (`application-prod.properties`).  
> Aucune donnée réelle d’élève / enseignant (BR-43).

## Paramètres communs

| | |
|---|---|
| Mot de passe (tous) | `DemoPass123!` |
| `schoolId` (comptes établissement) | `school-demo-elshammah` |
| API login | `POST /api/v1/auth/login` avec **email** + password |
| `mustChangePassword` | `false` (prêts à l’emploi) |

## Logins (e-mail)

| Rôle | E-mail | schoolId dans le body* | Profil lié |
|---|---|---|---|
| SUPER_ADMIN | `superadmin@smels.local` | auto (repli plateforme) | — |
| DIRECTOR (Principal) | `directeur@elshammah.demo` | `school-demo-elshammah` | `staff-demo-director` |
| ADMINISTRATION | `admin@elshammah.demo` | `school-demo-elshammah` | `staff-demo-admin` |
| SURVEILLANT_GENERAL | `surveillant@elshammah.demo` | `school-demo-elshammah` | `staff-demo-sg` |
| TEACHER | `enseignant@elshammah.demo` | `school-demo-elshammah` | `staff-demo-teacher` |
| PARENT | `parent.demo@elshammah.demo` | `school-demo-elshammah` | `guardian-demo-parent` |
| STUDENT | `eleve.demo@elshammah.demo` | `school-demo-elshammah` | `student-demo-01` |

\* Le front envoie le `schoolId` résolu depuis `.env` / domaine ; le SUPER_ADMIN est détecté automatiquement.

## Exemple

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "directeur@elshammah.demo",
  "password": "DemoPass123!",
  "schoolId": "school-demo-elshammah"
}
```

## Configuration

```properties
smels.security.mock-users.enabled=true
smels.security.mock-users.password=DemoPass123!
smels.security.mock-users.school-id=school-demo-elshammah
smels.security.bootstrap.username=superadmin@smels.local
```

## Notes

- Le seed est **idempotent** et migre les anciens usernames courts (`directeur`, …) vers l’e-mail.
- Ne jamais committer de vrais mots de passe ni de données nominatives réelles.
