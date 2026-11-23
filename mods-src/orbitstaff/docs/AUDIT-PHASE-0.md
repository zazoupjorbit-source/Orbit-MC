# Phase 0 — Rapport d'audit

Date : 2026-07-12. Poste : Windows 11, dossier de travail `C:\Users\james\Documents\claude_work`.

## Environnement

| Élément | État | Détail / impact |
|---|---|---|
| Code OrbitStaff existant | Aucun | Dossier `orbitstaff/` inexistant → départ propre, zéro conflit. |
| Dépôt Git à la racine | Absent | Un dépôt dédié `orbitstaff/` est prévu (pas de commit sans ordre explicite). |
| Java | JDK 26 sur le PATH, **JAVA_HOME = JDK 21** (Temurin `jdk-21.0.11+10`) | Maven compile en Java 21 → conforme. Ne pas exécuter les `.jar` avec le `java` 26 du PATH sans le préciser. |
| Maven | 3.9.9 | OK. |
| Node.js / npm | v24.16.0 / 11.13.0 | OK pour le bot (Phase 5). |
| **Docker** | **Absent** | `docker compose up` impossible en l'état. `docker-compose.yml` fourni ; installer Docker Desktop est une action manuelle. |
| Git | 2.54 | OK. |
| Autres projets ORBIT | 28 dossiers voisins (dont ORBIT-ARK-ASCENDED) | Non touchés. Mission isolée dans `orbitstaff/`. |

## Risques identifiés

1. **Docker absent** → pas de MariaDB exécutable sur ce poste maintenant. Mitigation : schéma + compose livrés, exécution documentée comme action manuelle.
2. **`java` (PATH) = 26 ≠ JAVA_HOME = 21** → sans impact sur Maven (suit `JAVA_HOME`), mais à surveiller pour l'exécution directe.
3. **Pas de secret disponible** (token Discord, mots de passe) → tout est modélisé dans `.env.example`, le développement continue sans blocage.

## Décisions d'architecture

- **API = Spring Boot 3.3 (Java 21)**, pas Quarkus : écosystème mature (Spring Security, Flyway, HikariCP, Testcontainers), adapté à un produit commercial. Pas de projet existant imposant un choix contraire.
- **Monorepo Maven = agrégateur pur.** Le plugin Paper ne tire jamais Spring ; l'API ne tire jamais Paper. Chaque module gère ses dépendances.
- **UUID Minecraft = identifiant stable** stocké en `BINARY(16)` ; pseudos conservés en historique séparé.
- **Package racine** `games.orbit.orbitstaff`, version `0.1.0-SNAPSHOT`.

## Vérification

`mvn clean verify` sur le reacteur (`parent` + `common`) : **BUILD SUCCESS**, 46 fichiers compilés en Java 21, **86 tests verts**.
