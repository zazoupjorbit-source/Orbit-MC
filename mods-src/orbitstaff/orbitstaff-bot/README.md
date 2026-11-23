# OrbitStaff Bot Discord

Bot Discord d'OrbitStaff : consultation joueur, sanctions, notes, logs, liaison de compte. **Il parle uniquement à l'API OrbitStaff** (jamais à MariaDB) et n'expose que des commandes slash — aucune commande console arbitraire, aucune exécution de commande Minecraft libre.

## Stack

- TypeScript (CommonJS via `nodenext`), Node.js ≥ 20
- [discord.js](https://discord.js.org) v14
- [Zod](https://zod.dev) v4 (validation config + réponses API)
- Vitest (tests), ESLint (lint)

## Scripts

```bash
npm install
npm run build      # compilation TypeScript -> dist/
npm test           # tests unitaires (vitest)
npm run typecheck  # vérification de types sans émission
npm run lint       # ESLint
npm run dev        # exécution en watch (tsx)
npm start          # node dist/index.js (nécessite un .env valide)
```

## Configuration

Copier `.env.example` vers `.env` et renseigner : token Discord, application/guild id, URL + clé de service de l'API, rôles staff autorisés. La configuration est **validée par Zod** au démarrage (une variable manquante empêche le démarrage avec une erreur claire).

## Architecture

- `src/config.ts` — configuration validée (Zod).
- `src/api/` — client HTTP vers l'API (`transport` injectable, `schemas` Zod, `client`, `errors`).
- `src/auth/authorization.ts` — contrôle serveur / salon / rôle (logique pure).
- `src/format/embeds.ts` — construction des embeds (données pures).
- `src/util/duration.ts` — validation des durées côté bot.
- `src/discord/` — définitions des commandes slash et routage des interactions.
- `src/index.ts` — démarrage, enregistrement des commandes, connexion.

## Commandes

- `/link code:<code>` — lie le compte Discord au compte Minecraft (code généré en jeu par `/discord link`).
- `/player consulter|sanctions <uuid>` — dossier / historique (rôle staff requis).
- `/mod warn|mute|kick|tempban|ban <uuid> …` — sanctions ; `/mod note` ; `/mod dossier`.
- `/server statut` — disponibilité de l'API.
- Réponses **éphémères**. Autorisation vérifiée côté bot (serveur/rôle) **et** côté API (permission métier).

## Vérification

`npm run build` et `npm test` passent (16 tests : client API, autorisation, config, embeds, durées). La couche discord.js compile ; le comportement en ligne (connexion à la passerelle Discord, enregistrement des commandes) requiert un token et sera validé au déploiement.

## Périmètre restant

Sous-commandes marquées « à venir » (inventaire/enderchest/homes/permissions côté `/player`, `/staff` stats, `/server joueurs|tps`, `/mod unban`) : elles nécessitent des endpoints API additionnels et arriveront ensuite.
