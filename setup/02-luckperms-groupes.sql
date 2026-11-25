-- Grades LuckPerms du reseau Orbit MC (groupes + permissions des groupes + tracks). Genere par valider.ps1.
-- Aucun joueur, aucun mot de passe ici.
/*M!999999\- enable the sandbox mode */ 

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;
DROP TABLE IF EXISTS `luckperms_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `luckperms_groups` (
  `name` varchar(36) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

set autocommit=0;
INSERT INTO `luckperms_groups` VALUES ('admin');
INSERT INTO `luckperms_groups` VALUES ('amiral');
INSERT INTO `luckperms_groups` VALUES ('astral');
INSERT INTO `luckperms_groups` VALUES ('builder');
INSERT INTO `luckperms_groups` VALUES ('cofondateur');
INSERT INTO `luckperms_groups` VALUES ('commandant');
INSERT INTO `luckperms_groups` VALUES ('default');
INSERT INTO `luckperms_groups` VALUES ('dev');
INSERT INTO `luckperms_groups` VALUES ('fondateur');
INSERT INTO `luckperms_groups` VALUES ('helpeur');
INSERT INTO `luckperms_groups` VALUES ('modo');
INSERT INTO `luckperms_groups` VALUES ('orbit');
INSERT INTO `luckperms_groups` VALUES ('partenaire');
INSERT INTO `luckperms_groups` VALUES ('pilote');
INSERT INTO `luckperms_groups` VALUES ('vip');
INSERT INTO `luckperms_groups` VALUES ('vipplus');
commit;
DROP TABLE IF EXISTS `luckperms_group_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `luckperms_group_permissions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(36) NOT NULL,
  `permission` varchar(200) NOT NULL,
  `value` tinyint(1) NOT NULL,
  `server` varchar(36) NOT NULL,
  `world` varchar(64) NOT NULL,
  `expiry` bigint(20) NOT NULL,
  `contexts` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `luckperms_group_permissions_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

set autocommit=0;
INSERT INTO `luckperms_group_permissions` VALUES (1,'default','prefix.0.<b><gradient:#8C96A3:#B8C0CA>ᴊᴏᴜᴇᴜʀ</gradient></b> <#D7DCE2>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (2,'cofondateur','prefix.95.<b><gradient:#FF8A3B:#FFC83B>ᴄᴏ-ꜰᴏɴᴅᴀᴛᴇᴜʀ</gradient></b> <#FFB347>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (3,'builder','prefix.50.<b><gradient:#F5B400:#FFE066>ʙᴜɪʟᴅᴇʀ</gradient></b> <#FFE066>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (4,'dev','prefix.80.<b><gradient:#3A7FFF:#7FB2FF>ᴅᴇᴠ</gradient></b> <#7FB2FF>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (5,'fondateur','prefix.100.<b><gradient:#FF3B3B:#FF8A3B>ꜰᴏɴᴅᴀᴛᴇᴜʀ</gradient></b> <#FF6B5B>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (6,'modo','group.helpeur',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (7,'admin','group.modo',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (8,'helpeur','prefix.60.<b><gradient:#22C1D6:#7FE7F2>ʜᴇʟᴘᴇᴜʀ</gradient></b> <#7FE7F2>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (9,'default','meta.namecolor.#D7DCE2',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (10,'default','weight.0',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (11,'dev','meta.namecolor.#7FB2FF',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (12,'dev','weight.80',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (13,'admin','prefix.90.<b><gradient:#E0245E:#FF5C8A>ᴀᴅᴍɪɴ</gradient></b> <#FF5C8A>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (14,'admin','meta.namecolor.#FF5C8A',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (15,'default','displayname.Joueur',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (19,'fondateur','meta.namecolor.#FF6B5B',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (20,'fondateur','weight.100',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (21,'admin','weight.90',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (22,'admin','displayname.Admin',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (23,'admin','*',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (25,'dev','displayname.Dev',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (26,'dev','*',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (29,'fondateur','displayname.Fondateur',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (30,'fondateur','*',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (33,'modo','prefix.70.<b><gradient:#1FBF6A:#5EE89A>ᴍᴏᴅᴏ</gradient></b> <#5EE89A>',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (34,'modo','meta.namecolor.#5EE89A',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (35,'modo','weight.70',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (36,'modo','displayname.Modo',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (37,'modo','orbitstaff.kick',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (38,'modo','orbitstaff.notes.delete',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (39,'helpeur','meta.namecolor.#7FE7F2',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (40,'helpeur','weight.60',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (41,'modo','orbitstaff.tempban',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (49,'helpeur','displayname.Helpeur',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (57,'helpeur','tab.staff',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (61,'cofondateur','meta.namecolor.#FFB347',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (62,'cofondateur','weight.95',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (63,'builder','meta.namecolor.#FFE066',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (64,'builder','weight.50',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (65,'builder','displayname.Builder',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (69,'cofondateur','displayname.Co-Fondateur',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (70,'cofondateur','*',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (71,'vip','weight.10',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (72,'vip','displayname.VIP',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (74,'vipplus','weight.20',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (75,'vipplus','displayname.VIP+',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (77,'orbit','weight.30',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (78,'orbit','displayname.ORBIT',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (80,'vipplus','group.vip',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (81,'orbit','group.vipplus',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (82,'vip','meta.namecolor.#F5C542',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (83,'vipplus','meta.namecolor.#5CE0A0',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (84,'orbit','meta.namecolor.#B57BFF',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (85,'vip','prefix.10.ᴠɪᴘ ',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (86,'vipplus','prefix.20.ᴠɪᴘ+ ',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (87,'orbit','prefix.30.ᴏʀʙɪᴛ ',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (88,'pilote','weight.110',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (89,'pilote','displayname.Pilote',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (90,'commandant','weight.120',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (91,'commandant','displayname.Commandant',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (92,'amiral','weight.130',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (93,'amiral','displayname.Amiral',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (94,'astral','weight.140',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (95,'astral','displayname.Astral',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (96,'pilote','orbit.file.bypass',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (97,'commandant','orbit.file.bypass',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (98,'amiral','orbit.file.bypass',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (99,'astral','orbit.file.bypass',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (100,'modo','orbittycoon.staff',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (101,'modo','orbittycoon.staff.ban',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (102,'modo','orbitstaff.staffmode',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (103,'modo','orbitstaff.freeze',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (104,'modo','orbitstaff.inspect',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (105,'modo','orbitstaff.mute',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (106,'modo','orbitstaff.warn',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (107,'modo','orbitstaff.notes.create',1,'global','global',0,'{}');
INSERT INTO `luckperms_group_permissions` VALUES (108,'modo','orbitstaff.notes.view',1,'global','global',0,'{}');
commit;
DROP TABLE IF EXISTS `luckperms_tracks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `luckperms_tracks` (
  `name` varchar(36) NOT NULL,
  `groups` text NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

set autocommit=0;
commit;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*M!100616 SET NOTE_VERBOSITY=@OLD_NOTE_VERBOSITY */;

