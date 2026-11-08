# Comptes mock (développement)

> **DEV uniquement.** Créés au démarrage si `smels.security.mock-users.enabled=true`  
> (défaut local). Désactivés en prod (`application-prod.properties`).  
> Aucune donnée réelle d’élève / enseignant (BR-43).

## Paramètres communs

| | |
|---|---|
| Mot de passe (tous) | `DemoPass123!` |
| `schoolId` (comptes établissement) | `school-demo-elshammah` |
| API login | `POST /api/v1/auth/login` |
| `mustChangePassword` | `false` (prêts à l’emploi) |

## Logins

| Rôle | Username | schoolId dans le body | Profil lié |
|---|---|---|---|
| SUPER_ADMIN | `superadmin` | *omis* (`null`) | — |
| DIRECTOR (Principal) | `directeur` | `school-demo-elshammah` | `staff-demo-director` |
| ADMINISTRATION | `admin` | `school-demo-elshammah` | `staff-demo-admin` |
| SURVEILLANT_GENERAL | `surveillant` | `school-demo-elshammah` | `staff-demo-sg` |
| TEACHER | `enseignant` | `school-demo-elshammah` | `staff-demo-teacher` |
| PARENT | `parent.demo` | `school-demo-elshammah` | `guardian-demo-parent` |
| STUDENT | `250000001` | `school-demo-elshammah` | `student-demo-01` |

## Exemples de requêtes

### Super admin (pas de schoolId)

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "superadmin",
  "password": "DemoPass123!"
}
```

> Au démarrage, le seed mock **réaligne** le mot de passe des comptes listés ci-dessus  
> sur `DemoPass123!` (pratique en local uniquement).

### Compte établissement (schoolId obligatoire)

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "enseignant",
  "password": "DemoPass123!",
  "schoolId": "school-demo-elshammah"
}
```

Même schéma pour `directeur`, `admin`, `surveillant`, `parent.demo`, `250000001`.

## Configuration

```properties
# application.properties (local)
smels.security.mock-users.enabled=true
smels.security.mock-users.password=DemoPass123!
smels.security.mock-users.school-id=school-demo-elshammah
smels.security.mock-users.school-code=ELSHAMMAH-DEMO
```

```properties
# application-prod.properties
smels.security.mock-users.enabled=false
```

## Notes

- Le seed est **idempotent** : redémarrer l’API ne duplique pas les comptes.
- L’école démo `ELSHAMMAH-DEMO` et les fiches Staff / Parent / Élève fictives sont créées si absentes.
- Compte élève : la connexion par matricule est préparée ; le périmètre métier élève reste partiel (docs/05).
- Ne jamais committer de vrais mots de passe ni de données nominatives réelles.
