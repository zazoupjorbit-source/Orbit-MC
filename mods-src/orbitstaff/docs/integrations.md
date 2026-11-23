# Intégrations

Chaque intégration est **facultative** : OrbitStaff démarre et fonctionne même si aucune n'est présente. Le mécanisme repose sur les **ports SPI** (`orbitstaff-common`) et un module d'adaptateurs (`orbitstaff-integrations`).

## Principe

- Ports SPI : `HomeProvider`, `PermissionProvider`, `EconomyProvider`, `BlockLogProvider`, `PunishmentProvider`. Chacun expose `availability()` (`AVAILABLE` / `ABSENT` / `PARTIAL` / `UNSUPPORTED`).
- Pour chaque port, une implémentation **`Unsupported…`** renvoie `ABSENT` + résultat vide. C'est le repli par défaut.
- `IntegrationManager` démarre sur les replis ; au démarrage, `IntegrationBootstrap.wire(...)` détecte les plugins présents et enregistre les adaptateurs réels.
- **Isolation des types tiers** : le plugin n'appelle que `IntegrationBootstrap.wire(manager, server)` et les ports SPI ; il ne référence jamais les API tierces. Les classes d'adaptateur ne sont chargées que lorsque l'intégration est détectée → aucun `NoClassDefFoundError` en leur absence.

## État des intégrations

| Intégration | Plugin détecté | Port | État |
|---|---|---|---|
| **LuckPerms** | `LuckPerms` | `PermissionProvider` | ✅ Adaptateur réel (API `net.luckperms:api:5.4`) : groupe principal, groupes hérités, préfixe/suffixe, permissions effectives |
| **Vault** | `Vault` | `EconomyProvider` | ✅ Adaptateur réel (API `com.github.MilkBowl:VaultAPI:1.7`) : solde via le `ServicesManager` |
| EssentialsX | `Essentials` | `HomeProvider`, `PunishmentProvider` | ⏳ Détection + repli `Unsupported` (adaptateur réel à livrer) |
| CoreProtect | `CoreProtect` | `BlockLogProvider` | ⏳ Détection + repli `Unsupported` |
| PlaceholderAPI | `PlaceholderAPI` | — | ⏳ Détection uniquement |
| WorldGuard | `WorldGuard` | — | ⏳ Détection uniquement |

Les adaptateurs réels LuckPerms et Vault **compilent contre les API officielles** (vérifié par le build). Leur comportement en jeu sera validé au déploiement sur un serveur Paper équipé de ces plugins.

## Dépendances (toutes `provided`, fournies par le serveur au runtime)

```xml
<dependency><groupId>net.luckperms</groupId><artifactId>api</artifactId><version>5.4</version><scope>provided</scope></dependency>
<dependency><groupId>com.github.MilkBowl</groupId><artifactId>VaultAPI</artifactId><version>1.7</version><scope>provided</scope></dependency>
```

VaultAPI provient de JitPack (`https://jitpack.io`). Aucune classe tierce n'est embarquée dans le jar du plugin.

## Utilisation dans le dossier joueur

`/playerinfo` affiche automatiquement le **groupe principal** (LuckPerms) et le **solde** (Vault) quand ces intégrations sont présentes ; sinon la section est omise, sans erreur.
