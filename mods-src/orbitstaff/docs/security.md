# Sécurité

La sécurité d'OrbitStaff repose sur trois piliers : **secrets séparés**, **API comme unique porte vers la base**, et **vérification systématique côté serveur**.

## Gestion des secrets

- Aucun secret n'est versionné. `.gitignore` exclut `.env`, `*.key`, `*.pem`, `secrets/`.
- Tous les secrets sont fournis via variables d'environnement (modèle : `.env.example`).
- **Identifiants distincts par service** : le plugin (`ORBITSTAFF_CORE_SERVICE_KEY`), le bot (`ORBITSTAFF_BOT_SERVICE_KEY`) et la base ont chacun leurs identifiants.
- Le **token Discord n'est jamais dans le plugin**. Le **mot de passe MariaDB n'est jamais dans le code**.
- Rotation possible : révoquer un `service_account` (colonne `revoked`) et en générer un nouveau.

## Clés de service

- Générées par `ServiceKeyGenerator` : préfixe `oss_` + 256 bits d'entropie (`SecureRandom`, base64url).
- L'API ne stocke **que le hash SHA-256** (`ServiceKeyHasher`), jamais la clé en clair.
- Vérification en **temps constant** (`MessageDigest.isEqual`) pour ne pas fuiter d'information par timing.
- Les clés sont à haute entropie : SHA-256 est suffisant (bcrypt est réservé aux mots de passe à faible entropie).

## Frontière d'accès aux données

- **Seule l'API accède à MariaDB.** Le plugin et le bot passent exclusivement par l'API en HTTPS.
- Le bot ne se connecte **jamais** directement à la base.
- Requêtes **préparées** uniquement : aucune concaténation de SQL avec des entrées utilisateur.

## Permissions

- OrbitStaff a son **propre système de permissions métier** (grades + nœuds), en plus de l'intégration LuckPerms.
- **Toute permission est vérifiée côté serveur.** Masquer un bouton ne protège rien.
- Grades et poids : `HELPER(1) < MODERATOR(2) < SENIOR_MODERATOR(3) < ADMINISTRATOR(4) < MANAGER(5)`.
- La visibilité des notes suit le même poids (`StaffRole.canView(NoteVisibility)`).

## Actions sensibles

- **Idempotence** obligatoire sur les créations de sanction (clé unique par compte de service).
- **Confirmation + motif + snapshot + journalisation** avant toute modification d'inventaire ou de permission.
- Aucune sanction **permanente** appliquée automatiquement sans validation humaine.
- Le bot ne dispose **d'aucune commande console arbitraire** et ne peut exécuter aucune commande Minecraft libre.

## Liaison Minecraft ↔ Discord

- Code temporaire, **aléatoire, à usage unique, expirant**, limité en tentatives.
- Seul le **hash** du code est stocké (`discord_link_codes.code_hash`).

## Journalisation

- Ne journalise **jamais** : mot de passe, token, clé API, cookie, secret, contenu complet d'un fichier sensible.
- Le journal d'audit (`audit_events`) ne porte pas de FK sur les UUID afin que la journalisation n'échoue jamais.

## Vie privée

- **Aucune collecte d'IP par défaut.** Si activée un jour : désactivée par défaut, rétention courte, accès restreint, jamais affichée dans Discord.
- Rétention configurable par catégorie (`retention.yml`), purge planifiée, suppression logique, anonymisation possible.

## Checklist de sécurité (avant mise en production)

- [ ] `.env` renseigné, absent du dépôt, permissions fichier restreintes.
- [ ] Clés de service générées, distinctes par service, stockées hors code.
- [ ] MariaDB non exposée publiquement ; utilisateur applicatif à privilèges minimaux.
- [ ] API derrière HTTPS (TLS) avec un certificat valide.
- [ ] Rate limiting activé sur l'API.
- [ ] Rôles Discord autorisés configurés ; salons de logs restreints.
- [ ] Sauvegarde base + stockage de preuves planifiée et testée.
- [ ] Revue des permissions par grade validée par un responsable.
