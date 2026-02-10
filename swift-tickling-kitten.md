# Plan : Systeme de Cellules Prison avec Generation en Spirale

## Contexte

Le plugin Prison a besoin d'un systeme complet de cellules (parcelles) que les joueurs achetent et ameliorent. Les cellules sont generees dans un monde dedie sur une grille en spirale. Toutes les donnees (blocs, niveaux, templates) sont stockees en MySQL pour pouvoir re-generer l'etat exact sur un autre serveur.

**Algorithme de placement** : Spirale (algo fourni par l'utilisateur) - chaque nouvelle cellule est placee en tournant autour du centre avec un espacement large (ex: 500 blocs entre centres) pour :
- Eviter de voir les cellules voisines
- Laisser de la marge pour les agrandissements futurs

---

## Architecture

### Nouvelles tables SQL

```sql
-- Templates de cellules (schematics admin)
CREATE TABLE prison_cell_templates (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    width INT NOT NULL,
    height INT NOT NULL,
    depth INT NOT NULL,
    block_data LONGBLOB NOT NULL,           -- blocs serialises (compresse gzip)
    created_at BIGINT DEFAULT 0
);

-- Niveaux d'upgrade configurables
CREATE TABLE prison_cell_levels (
    level INT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    zone_radius INT NOT NULL,               -- demi-taille zone constructible
    zone_height INT NOT NULL DEFAULT 30,
    price DECIMAL(20,2) NOT NULL,
    template_name VARCHAR(64),              -- template schematic associe (nullable)
    max_members INT NOT NULL DEFAULT 3,     -- limite d'amis pour ce niveau
    description VARCHAR(255)
);

-- Snapshots de blocs des cellules joueurs
CREATE TABLE prison_cell_blocks (
    cell_id VARCHAR(64) NOT NULL,
    rx SMALLINT NOT NULL,
    ry SMALLINT NOT NULL,
    rz SMALLINT NOT NULL,
    block_type VARCHAR(128) NOT NULL,
    rotation TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (cell_id, rx, ry, rz),
    INDEX idx_cell (cell_id)
);

-- Membres des cellules (systeme d'amis)
CREATE TABLE prison_cell_members (
    cell_id VARCHAR(64) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    player_name VARCHAR(32) NOT NULL,
    role ENUM('VISITOR', 'MEMBER', 'MANAGER') NOT NULL DEFAULT 'VISITOR',
    added_at BIGINT NOT NULL,
    added_by CHAR(36) NOT NULL,             -- UUID de celui qui a invite
    PRIMARY KEY (cell_id, player_uuid),
    INDEX idx_player (player_uuid),
    INDEX idx_cell (cell_id)
);
```

### Modification table existante `prison_cells`

```sql
ALTER TABLE prison_cells ADD COLUMN grid_x INT DEFAULT 0;
ALTER TABLE prison_cells ADD COLUMN grid_z INT DEFAULT 0;
ALTER TABLE prison_cells ADD COLUMN current_level INT DEFAULT 0;
ALTER TABLE prison_cells ADD COLUMN world_name VARCHAR(64) DEFAULT 'prison_cells';
ALTER TABLE prison_cells ADD COLUMN is_public BOOLEAN DEFAULT false;
ALTER TABLE prison_cells ADD COLUMN max_members INT DEFAULT 3;
ALTER TABLE prison_cells ADD COLUMN dirty BOOLEAN DEFAULT false;
ALTER TABLE prison_cells ADD COLUMN last_snapshot BIGINT DEFAULT 0;
```

---

## Systeme de Membres / Amis

### Roles

| Role | Teleport | Voir | Placer/Casser blocs | Inviter | Expulser | Changer roles |
|------|----------|------|---------------------|---------|----------|---------------|
| **VISITOR** | Oui | Oui | Non | Non | Non | Non |
| **MEMBER** | Oui | Oui | Oui | Non | Non | Non |
| **MANAGER** | Oui | Oui | Oui | Oui (visitor/member) | Oui (visitor/member) | Oui (visitor/member) |
| **OWNER** (implicite) | Oui | Oui | Oui | Oui (tous) | Oui (tous) | Oui (tous) |

### Regles d'acces

- **Cellule publique** (`is_public = true`) : tout le monde peut entrer et visiter (read-only)
- **Cellule privee** (`is_public = false`) : seuls le proprio + membres peuvent entrer
- **Construction** : seuls MEMBER, MANAGER et OWNER peuvent placer/casser des blocs
- **Limite de membres** : `max_members` sur la cellule (default depuis le niveau, modifiable par admin ou achat futur)
- Le MANAGER peut inviter/expulser des VISITOR et MEMBER, mais pas d'autres MANAGER
- Seul l'OWNER peut promouvoir en MANAGER

---

## Fichiers a creer/modifier

### Nouveaux fichiers (dans `islandium-prison/src/main/java/com/islandium/prison/cell/`)

1. **`CellGridAllocator.java`** - Algorithme spirale pour attribuer des positions
   - `allocateNext()` : prochaine position libre en spirale
   - `gridToWorld(gridX, gridZ)` : grille -> coordonnees monde
   - `worldToGrid(worldX, worldZ)` : inverse
   - Espacement configurable (default: 500 blocs)

2. **`CellTemplateManager.java`** - Gestion des schematics admin
   - `saveTemplate(name, world, bounds)` : copie blocs et sauvegarde en DB (gzip)
   - `loadTemplate(name)` : charge depuis DB
   - `pasteTemplate(name, world, centerX, centerY, centerZ)` : colle le template
   - `listTemplates()` / `deleteTemplate(name)`

3. **`CellBlockStorage.java`** - Sauvegarde/restauration de l'etat des blocs
   - `saveSnapshot(cell)` : lit blocs non-air -> batch INSERT dans prison_cell_blocks
   - `loadSnapshot(cell)` : SELECT blocs -> place dans le monde (batched)
   - `deleteSnapshot(cellId)` : purge
   - Pattern batch : 4096 blocs/batch + delay

4. **`CellLevelManager.java`** - Gestion des niveaux d'upgrade
   - CRUD niveaux en DB
   - `getLevel(int)`, `getAllLevels()`, `getMaxLevel()`

5. **`CellLevel.java`** - POJO niveau
   - `level`, `name`, `zoneRadius`, `zoneHeight`, `price`, `templateName`, `maxMembers`, `description`

6. **`CellMemberManager.java`** - Gestion des membres/amis
   - `addMember(cellId, playerUuid, playerName, role, addedBy)` : ajoute un membre
   - `removeMember(cellId, playerUuid)` : retire un membre
   - `setRole(cellId, playerUuid, role)` : change le role
   - `getMembers(cellId)` : liste les membres d'une cellule
   - `getCellsForPlayer(playerUuid)` : cellules ou le joueur est membre
   - `getRole(cellId, playerUuid)` : role du joueur (null si pas membre)
   - `canBuild(cell, playerUuid)` : verifie si le joueur peut construire
   - `canEnter(cell, playerUuid)` : verifie si le joueur peut entrer
   - `canInvite(cell, playerUuid)` : verifie si le joueur peut inviter
   - `canKick(cell, playerUuid, targetUuid)` : verifie si peut expulser
   - `getMemberCount(cellId)` : nombre de membres actuels
   - `isFull(cell)` : verifie si la limite est atteinte
   - Cache en memoire : `Map<String, List<CellMember>>` par cellId

7. **`CellMember.java`** - POJO membre
   - `cellId`, `playerUuid`, `playerName`, `role` (enum), `addedAt`, `addedBy`

8. **`CellRole.java`** - Enum des roles
   - `VISITOR`, `MEMBER`, `MANAGER`
   - Methodes : `canBuild()`, `canInvite()`, `canKick()`, `canChangeRole()`

9. **`CellAutoSaver.java`** - Tache periodique de sauvegarde
   - ScheduledExecutorService toutes les 5 min
   - Sauvegarde uniquement cellules "dirty"
   - Sur deconnexion joueur : sauvegarde immediate

10. **`CellWorldManager.java`** - Gestion du monde dedie
    - `getOrCreateWorld()` : monde "prison_cells"
    - `generatePlatform(centerX, centerY, centerZ, radius)` : sol basique

### Fichiers a modifier

11. **`Cell.java`** - Ajouter champs :
    - `gridX`, `gridZ` (position grille)
    - `currentLevel` (niveau upgrade, default 0)
    - `worldName` (default "prison_cells")
    - `isPublic` (acces public ou prive, default false)
    - `maxMembers` (limite amis, init depuis niveau)
    - `dirty` (flag auto-save)
    - `lastSnapshot` (timestamp)

12. **`CellManager.java`** - Refactorer :
    - Integrer tous les nouveaux managers
    - `createAndGenerateCell(player)` : alloue grille + colle template + assigne
    - `upgradeCell(player)` : verifie argent, augmente niveau, agrandit zone
    - `regenerateCell(cellId)` : restaure depuis DB
    - `regenerateAll()` : restaure toutes les cellules
    - `togglePublic(player)` : bascule public/prive
    - MAJ des requetes SQL pour les nouveaux champs

13. **`PrisonPlugin.java`** - Init des nouveaux managers dans `setup()` et `teardown()`

14. **`PrisonConfig.java`** - Ajouter :
    - `cellWorldName` (default: "prison_cells")
    - `cellGridSpacing` (default: 500)
    - `cellBaseY` (default: 80)
    - `cellAutoSaveMinutes` (default: 5)
    - `cellDefaultMaxMembers` (default: 3)

### Commandes

15. **`CellAdminCommand.java`** - Commandes admin :
    - `/celladmin settemplate <nom>` - sauvegarde selection comme template
    - `/celladmin templates` - liste templates
    - `/celladmin deletetemplate <nom>` - supprime template
    - `/celladmin setlevel <level> <name> <radius> <height> <price> <maxMembers> [template]`
    - `/celladmin levels` - liste niveaux
    - `/celladmin regen <joueur>` - regenere cellule
    - `/celladmin regenall` - regenere toutes
    - `/celladmin info <joueur>` - info cellule
    - `/celladmin forcesave` - force sauvegarde dirty
    - `/celladmin setmembers <joueur> <nombre>` - change limite membres d'une cellule

16. **`CellCommand.java`** - Commandes joueur :
    - `/cell` - teleporte a sa cellule
    - `/cell buy` - achete une cellule
    - `/cell upgrade` - ameliore au niveau suivant
    - `/cell info` - infos (niveau, taille, membres, public/prive)
    - `/cell invite <joueur> [role]` - invite un joueur (default: VISITOR)
    - `/cell kick <joueur>` - expulse un membre
    - `/cell promote <joueur>` - monte le role (VISITOR->MEMBER->MANAGER)
    - `/cell demote <joueur>` - descend le role (MANAGER->MEMBER->VISITOR)
    - `/cell members` - liste des membres avec leurs roles
    - `/cell public` - toggle public/prive
    - `/cell visit <joueur>` - visite la cellule d'un autre joueur (si publique ou ami)

### Event listener

17. **`CellBlockListener.java`** (ECS event system) :
    - Intercepte place/break de bloc
    - Verifie si dans une cellule
    - Verifie permission : owner OU (MEMBER/MANAGER via CellMemberManager.canBuild)
    - Si autorise : marque cellule "dirty"
    - Si refuse : annule + message
    - Lookup rapide : worldX/spacing -> gridX -> cell

18. **`CellAccessListener.java`** - Controle d'acces :
    - Detecte quand un joueur entre dans une zone de cellule
    - Si cellule privee et joueur pas membre/owner : repousse ou bloque

---

## Algorithme spirale (CellGridAllocator)

```
Espacement: 500 blocs entre centres
Centre Y: 80

Spirale (algo de l'utilisateur adapte) :
  i=0: (0, 0)
  i=1: (-500, 0)       // gauche
  i=2: (-500, 500)      // bas
  i=3: (0, 500)         // droite
  i=4: (500, 500)       // droite
  i=5: (500, 0)         // haut
  i=6: (500, -500)      // haut
  ...

Directions: gauche(-X), bas(+Z), droite(+X), haut(-Z)
Longueur: 1,1,2,2,3,3,4,4... (increment tous les 2 tours)

allocateNext() parcourt la spirale et retourne
la premiere position qui n'est pas deja occupee dans la Map<gridX:gridZ, cellId>.
```

---

## Serialisation des blocs

**Templates (prison_cell_templates.block_data)** :
```
GZIP( JSON: {
  "width": 30, "height": 20, "depth": 30,
  "blocks": { "x,y,z": "BlockType", "x,y,z": "BlockType|rotation", ... }
})
```

**Snapshots (prison_cell_blocks)** : une ligne SQL par bloc non-air. Batch INSERT.

---

## Flux principaux

### Creation cellule
1. `/cell buy` -> verifie argent + pas deja de cellule
2. `CellGridAllocator.allocateNext()` -> (gridX, gridZ)
3. `CellWorldManager.getOrCreateWorld()` + `generatePlatform()`
4. `CellTemplateManager.pasteTemplate("starter", ...)` -> colle schematic
5. Cree Cell (gridX, gridZ, level=0, maxMembers=3, isPublic=false)
6. Sauvegarde DB + teleport joueur

### Upgrade cellule
1. `/cell upgrade` -> verifie niveau suivant + argent
2. Debite argent, cell.currentLevel++
3. MAJ corner1/corner2 avec nouveau zoneRadius
4. MAJ maxMembers depuis le niveau (si superieur a l'actuel)
5. Si template associe au niveau -> colle (blocs joueur preserves)
6. Sauvegarde DB

### Inviter un ami
1. `/cell invite <joueur> [VISITOR|MEMBER|MANAGER]`
2. Verifie : proprio OU manager avec droit d'inviter
3. Verifie : pas deja membre + limite pas atteinte
4. `CellMemberManager.addMember()` -> INSERT DB
5. Notifie le joueur invite

### Sauvegarde snapshot
1. Timer 5 min OU deconnexion joueur
2. Pour chaque cellule dirty :
   - Lire blocs non-air via WorldChunk
   - DELETE FROM prison_cell_blocks WHERE cell_id = ?
   - Batch INSERT tous les blocs
   - dirty = false, lastSnapshot = now

### Restauration serveur
1. `/celladmin regenall`
2. Pour chaque cellule en DB : alloue position, genere sol, place blocs depuis snapshot
3. Batched (4096 blocs/batch + delay)

---

## Performance
- Batch block ops : 4096 blocs/batch + delay
- Index spatial O(1) : worldX/spacing -> gridX
- Dirty flag : ne sauvegarde que les cellules modifiees
- GZIP templates
- Async SQL (CompletableFuture)
- Cache membres en memoire (ConcurrentHashMap)

---

## Verification / Test

1. `./gradlew islandium-prison:build && ./gradlew copyJarsToOutput`
2. Deployer sur serveur
3. Admin : construire cellule test -> `/celladmin settemplate starter` -> `/celladmin setlevel 0 ...`
4. Joueur : `/cell buy` -> construire -> deconnexion -> reconnexion -> blocs la
5. Amis : `/cell invite <ami> MEMBER` -> ami construit -> `/cell kick <ami>`
6. Public : `/cell public` -> autre joueur visite
7. Restauration : `/celladmin regen <joueur>`
