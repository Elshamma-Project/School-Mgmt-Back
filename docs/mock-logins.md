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
- Les fiches `Staff` démo reçoivent le **même e-mail** que le compte lié (`contact.email`), pour l’affichage RH.
- Seed académique minimal : année `2026-2027` (+ 6 sessions DEV1–DEV6), matières Maths/PC, classes 6ème A / 5ème A, affectées à `enseignant@elshammah.demo`.
- Ne jamais committer de vrais mots de passe ni de données nominatives réelles.

## Sessions d’évaluation (DEV1…DEV6)

Cycle de vie (docs/02 § 5.3) :

| Statut | Signification | UI / droits |
|---|---|---|
| `PLANNED` | Pas encore ouverte | Admin peut **ouvrir** |
| `OPEN` | Saisie enseignants active | Admin peut **clôturer** |
| `CLOSED` | Saisie bloquée | Directeur peut **rouvrir** (motif) |

Jeu de test réaligné **à chaque démarrage** du back (`mock-users.enabled=true`) :

| Séquences | Statut seed |
|---|---|
| DEV1, DEV2 | `CLOSED` |
| DEV3, DEV4 | `OPEN` |
| DEV5, DEV6 | `PLANNED` |

### Réinitialiser manuellement (sans redémarrer)

1. **Via l’UI** (hub Notes / Séquences) : cliquer une ligne → ouvrir / clôturer / rouvrir.
2. **Via l’API** (compte admin ou directeur) :
   - `POST /api/v1/evaluation-sessions/{id}/open`
   - `POST /api/v1/evaluation-sessions/{id}/close`
   - `POST /api/v1/evaluation-sessions/{id}/reopen` + `{ "reason": "…" }` (directeur)
   - IDs démo : `session-demo-1` … `session-demo-6`

### Réinitialiser automatiquement

- **Redémarrer le back** avec `smels.security.mock-users.enabled=true` : le seed **réécrit** les statuts DEV1…6 (et les fiches associées) vers le tableau ci-dessus.
- Remise à zéro Mongo plus large (DEV local uniquement), puis redémarrage :

```bash
mongosh --eval 'db.getSiblingDB("smels").grading_sessions.deleteMany({ schoolId: "school-demo-elshammah" })'
```

(ou vider la base démo entière si besoin, puis relancer `./gradlew bootRun`).
