-- Base du reseau Minecraft ORBIT (dev local, base dediee MinecraftDev\db, port 3318)
CREATE DATABASE IF NOT EXISTS orbit_mc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'orbit_mc'@'localhost' IDENTIFIED BY 'u3jeTjR8L269emxFaEipKwkJ';
CREATE USER IF NOT EXISTS 'orbit_mc'@'127.0.0.1' IDENTIFIED BY 'u3jeTjR8L269emxFaEipKwkJ';
GRANT ALL PRIVILEGES ON orbit_mc.* TO 'orbit_mc'@'localhost';
GRANT ALL PRIVILEGES ON orbit_mc.* TO 'orbit_mc'@'127.0.0.1';
FLUSH PRIVILEGES;
SELECT 'Base orbit_mc prete' AS resultat;
