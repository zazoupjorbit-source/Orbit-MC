-- Base du reseau Minecraft ORBIT (dev local, base dediee MinecraftDev\db, port 3318)
-- MODELE : __MDP_BASE__ est remplace par le mot de passe local (setup\db.properties) par installer.ps1.
-- Ne jamais ecrire un vrai mot de passe dans ce fichier : il est suivi par git.
CREATE DATABASE IF NOT EXISTS orbit_mc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'orbit_mc'@'localhost' IDENTIFIED BY '__MDP_BASE__';
CREATE USER IF NOT EXISTS 'orbit_mc'@'127.0.0.1' IDENTIFIED BY '__MDP_BASE__';
GRANT ALL PRIVILEGES ON orbit_mc.* TO 'orbit_mc'@'localhost';
GRANT ALL PRIVILEGES ON orbit_mc.* TO 'orbit_mc'@'127.0.0.1';
FLUSH PRIVILEGES;
SELECT 'Base orbit_mc prete' AS resultat;
