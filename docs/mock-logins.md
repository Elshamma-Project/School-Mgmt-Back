# Comptes mock (développement)

> **DEV uniquement.** Créés au démarrage si `beesm.security.mock-users.enabled=true`  
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
| SUPER_ADMIN | `superadmin@bee-sm.local` | auto (repli plateforme) | — |
| DIRECTOR (Principal) | `directeur@elshammah.demo` | `school-elshammah` | `staff-demo-director` |
| ADMINISTRATION | `admin@elshammah.demo` | `school-elshammah` | `staff-demo-admin` |
| SURVEILLANT_GENERAL | `surveillant@elshammah.demo` | `school-elshammah` | `staff-demo-sg` (UI `/surveillant-general`) |
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
beesm.security.mock-users.enabled=true
beesm.security.mock-users.password=DemoPass123!
beesm.security.mock-users.school-id=school-elshammah
beesm.security.mock-users.school-code=ELSHAMMAH
beesm.seed.el-shammah.enabled=true
beesm.seed.el-shammah.school-id=school-elshammah
beesm.security.bootstrap.username=superadmin@bee-sm.local
```

## Notes

- Le seed comptes est **idempotent** et migre les anciens usernames courts (`directeur`, …) vers l’e-mail.
- Les fiches `Staff` démo reçoivent le **même e-mail** que le compte lié (`contact.email`).
- Pas d’inscription ni de notes auto pour l’élève démo tant que le rattachement aux vraies classes El Shammah n’est pas fait (passe suivante).
- Sessions DEV1…DEV6 : celles du JSON El Shammah (par année) ; les gérer via l’UI hub Notes / Séquences.
- Ne jamais committer de vrais mots de passe ni de données nominatives réelles.

### Jeu de démo enseignant (`TeacherDemoSeedRunner`, @Order 25)

Actif si `beesm.security.mock-users.enabled=true` (insert-if-absent ; **enrichit** les champs vides DOB / contact / tuteurs sans écraser une saisie UI).

- `enseignant@elshammah.demo` peut ouvrir **`/teacher/grades`** et **`/teacher/classes`** après le seed.
- Classe : **6ème** francophone de l’année courante (copie de structure depuis la 6ème d’une autre année si elle n’existe pas encore pour l’année courante).
- Affectation de `staff-demo-teacher` sur 1–2 matières (Maths, puis langue française) **uniquement si `teacherId` est vide**.
- **Roster fictif enrichi** (`stu-demo-01` … `stu-demo-10`) : sexe, date / lieu de naissance, nationalité, téléphone / e-mail / adresse, tuteurs.
  - `stu-demo-01`…`03` liés à `guardian-demo-parent` **et** `guardian-demo-father` (espace parent multi-enfants).
  - `stu-demo-09` inactif (filtre Actifs / Inactifs).
- Séquences 1 et 2 ouvertes ; notes / bonus / malus + fiche de report T1 (voir historique).
- Visas de test : enseignant → SG → Directeur via hub Notes.

### Jeu de démo scolarité (`FeesDemoSeedRunner`, @Order 26)

- Politique `FeesPolicy` (rappels 15/10/5/3/2/1, pénalité 1000 XAF/sem., grâce 28 j).
- Barème pour la classe démo : inscription **25 000** + 3× **50 000** XAF.
- Paiements variés pour visualiser la colonne Solvabilité **et** le workspace `/director/students/solvency` :
  - `stu-demo-01` / `05` → **SOLVENT** (tout payé)
  - `stu-demo-02` → **PENDING** (tranche du mois en cours manquante)
  - `stu-demo-03` / sans paiement → **INSOLVENT**
- **Qui teste quoi** :
  - `directeur@elshammah.demo` → nav **Élèves → Solvabilité**, paiements, unpaid-summary.
  - `admin@elshammah.demo` → fiche Staff démo = `SECRETARY` + `ACCOUNTANT` → paiements + nav **Solvabilité** (`canRecordPayments=true`).
  - `enseignant@elshammah.demo` → **pas** de colonne / API solvabilité.
  - `parent.demo@elshammah.demo` → inbox `FEES_*` + solvabilité **enfants liés** uniquement.

### Remise à zéro locale

```bash
# Vider la base puis relancer le back (recrée le tenant propre)
mongosh --eval 'db.getSiblingDB("bee-sm-db").dropDatabase()'
./gradlew bootRun
```
