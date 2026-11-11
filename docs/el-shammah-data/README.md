# Données El Shammah (tenant)

El Shammah est un **tenant** au sens SMELS :

| | |
|---|---|
| `schoolId` | `school-elshammah` |
| `schoolCode` | `ELSHAMMAH` |

## `seed-el-shammah.json`

Structure académique réelle (sans élèves, notes ni enseignants) : école, années, niveaux, groupes, matières, barèmes, gabarits, sessions, classes et matières par classe.

### Chargeur au démarrage

`ElShammahAcademicSeedRunner` (`@Order(15)`) lit la copie classpath :

`src/main/resources/seed/seed-el-shammah.json`

| Propriété | Défaut | Effet |
|---|---|---|
| `smels.seed.el-shammah.enabled` | `true` | Active le seed |
| `smels.seed.el-shammah.school-id` | `school-elshammah` | Id Mongo du tenant |
| `smels.seed.el-shammah.resource` | `classpath:seed/seed-el-shammah.json` | Fichier source |
| `smels.seed.el-shammah.overwrite-existing` | `false` | `false` = **ne réécrit pas** les documents déjà présents |

Politique **insert-if-absent** (clés naturelles : `code`, `label`, `name`×année, `classId`+`subjectId`, etc.) :
- 1er démarrage → crée toute la structure ;
- redémarrages suivants → ignore l’existant (les changements faits dans l’UI restent) ;
- nouvelles lignes ajoutées au JSON → insérées si absentes ;
- `overwrite-existing=true` → réapplique le JSON sur l’existant (réinitialisation volontaire).

Les comptes de connexion DEV (`MockUsersBootstrapRunner`) **ne touchent plus** à ce document School ni à la structure académique.

### Après modification de ce JSON

```powershell
Copy-Item -Force docs\el-shammah-data\seed-el-shammah.json src\main\resources\seed\seed-el-shammah.json
```

Ou pointer `smels.seed.el-shammah.resource=file:docs/el-shammah-data/seed-el-shammah.json` en local.
