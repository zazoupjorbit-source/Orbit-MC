package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import fr.orbit.vol.Catalogue.Mutation;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tout ce qui se gagne ou se collectionne sans argent réel : récompenses de la semaine (classement remis à zéro
 * chaque lundi), titres, auras, paliers de l'Orbidex, couleurs de la base, musique d'ambiance.
 */
final class Recompenses {

    // ------------------------------------------------------------------ catalogues

    static final Map<String, String> TITRES = new LinkedHashMap<>();
    static {
        TITRES.put("recrue", "<#7CFC9A>Recrue d'ORBIT");
        TITRES.put("ascendant", "<#E0B864>Ascendant");
        TITRES.put("transcendant", "<gradient:#E0B864:#FF4D6D>Transcendant");
        TITRES.put("collection_25", "<#A64DFF>Collectionneur");
        TITRES.put("collection_50", "<#A64DFF>Archiviste stellaire");
        TITRES.put("collection_100", "<gradient:#A64DFF:#62CFF4>Maître de l'Orbidex");
        TITRES.put("podium", "<#62CFF4>Sur le podium");
        TITRES.put("magnat", "<#FFE08A>Magnat de la semaine");
        TITRES.put("roi_voleurs", "<#FF4D6D>Roi des voleurs");
        TITRES.put("fusion", "<#FF7A1A>Alchimiste");
    }

    /** Auras : nom, comment l'obtenir (prix en crédits, 0 = seulement à gagner). */
    record Aura(String id, String nom, long prix, String comment, Material icone) {
    }

    static final List<Aura> AURAS = List.of(
            new Aura("notes", "<#7CFC9A>Notes", 20_000, "En boutique", Material.NOTE_BLOCK),
            new Aura("flammes", "<#FF7A1A>Flammes", 75_000, "En boutique", Material.BLAZE_POWDER),
            new Aura("coeurs", "<#FF4D6D>Cœurs", 250_000, "En boutique", Material.RED_DYE),
            new Aura("etoiles", "<#E0B864>Étoiles", 0, "Orbidex à 25 %", Material.NETHER_STAR),
            new Aura("neige", "<#9FE8FF>Neige", 0, "Orbidex à 50 %", Material.SNOWBALL),
            new Aura("arcenciel", "<rainbow>Arc-en-ciel</rainbow>", 0, "Orbidex à 100 %", Material.PRISMARINE_CRYSTALS),
            new Aura("couronne", "<#FFE08A>Couronne", 0, "1er du classement de la semaine", Material.GOLDEN_HELMET));

    static final Material[] SOLS = {Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.CYAN_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE, Material.BLACK_CONCRETE,
            Material.WHITE_CONCRETE, Material.GREEN_CONCRETE, Material.MAGENTA_CONCRETE};
    static final Material[] VITRES = {Material.WHITE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
            Material.PINK_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.GLASS, Material.TINTED_GLASS};
    static final long PRIX_COULEUR = 2_000;

    final Jeu jeu;
    final File fichierSemaine;
    /** Couleurs actuellement posées dans chaque base (pour savoir quoi repeindre). */
    final Map<Integer, Material[]> peinture = new HashMap<>();
    final Map<UUID, Long> musiqueDepuis = new HashMap<>();
    int tic = 0;

    Recompenses(Jeu jeu) {
        this.jeu = jeu;
        this.fichierSemaine = new File(jeu.plugin.getDataFolder(), "semaine.txt");
    }

    static String semaineActuelle() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-S" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Remet les compteurs de la semaine à zéro si on a changé de semaine. */
    static void verifierSemaine(Profil pr) {
        String s = semaineActuelle();
        if (!s.equals(pr.semaine)) {
            pr.semaine = s;
            pr.volsSemaine = 0;
            pr.gainsSemaine = 0;
        }
    }

    static String titre(Profil pr) {
        String t = TITRES.get(pr.titre);
        return t == null ? "" : t;
    }

    // ------------------------------------------------------------------ connexion

    void connexion(Player p, Profil pr) {
        verifierSemaine(pr);
        if (pr.aVerser > 0) {
            pr.credits += pr.aVerser;
            Txt.send(p, "<#E0B864>Récompense de la semaine versée :</#E0B864> " + Txt.cr(pr.aVerser));
            pr.aVerser = 0;
        }
        for (String m : pr.messages) Txt.send(p, m);
        pr.messages.clear();
        if (pr.ascension >= 1) pr.titres.add("ascendant");
        if (pr.ascension >= 5) pr.titres.add("transcendant");
        musiqueDepuis.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ chaque seconde

    void seconde() {
        tic++;
        long now = System.currentTimeMillis();
        for (Player p : jeu.w.getPlayers()) {
            Profil pr = jeu.profils.get(p.getUniqueId());
            if (pr == null) continue;
            // Musique d'ambiance : une piste toutes les 4 minutes.
            if (pr.musique) {
                Long d = musiqueDepuis.get(p.getUniqueId());
                if (d == null || now - d > 240_000) {
                    musiqueDepuis.put(p.getUniqueId(), now);
                    if (jeu.pack.charge(p)) p.playSound(p, "orbitvol:musique", SoundCategory.MUSIC, 0.7f, 1f);
                }
            }
        }
        if (tic % 60 == 5) semaine();
    }

    /** Toutes les 10 ticks : particules des auras. */
    void auras() {
        for (Player p : jeu.w.getPlayers()) {
            Profil pr = jeu.profils.get(p.getUniqueId());
            if (pr == null || pr.aura.isEmpty() || p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) continue;
            Location l = p.getLocation();
            World w = l.getWorld();
            switch (pr.aura) {
                case "notes" -> w.spawnParticle(Particle.NOTE, l.clone().add(0, 2.3, 0), 1, 0.4, 0.1, 0.4, 1);
                case "flammes" -> w.spawnParticle(Particle.FLAME, l.clone().add(0, 0.1, 0), 4, 0.35, 0.05, 0.35, 0.01);
                case "coeurs" -> w.spawnParticle(Particle.HEART, l.clone().add(0, 2.2, 0), 1, 0.4, 0.2, 0.4, 0);
                case "etoiles" -> w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1, 0), 2, 0.5, 0.7, 0.5, 0.01);
                case "neige" -> w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 2.2, 0), 3, 0.5, 0.1, 0.5, 0.01);
                case "arcenciel" -> {
                    Color c = Color.fromRGB(java.awt.Color.HSBtoRGB((tic * 10 + System.currentTimeMillis() / 50 % 360) / 360f, 1f, 1f) & 0xFFFFFF);
                    w.spawnParticle(Particle.DUST, l.clone().add(0, 1, 0), 6, 0.45, 0.7, 0.45, 0, new Particle.DustOptions(c, 1.2f));
                }
                case "couronne" -> {
                    double a = System.currentTimeMillis() / 300.0;
                    for (int i = 0; i < 6; i++) {
                        double t = a + i * Math.PI / 3;
                        w.spawnParticle(Particle.DUST, l.clone().add(Math.cos(t) * 0.35, 2.25, Math.sin(t) * 0.35), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(0xFFE08A), 0.9f));
                    }
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ paliers de l'Orbidex

    void palierOrbidex(Player p, Profil pr) {
        int total = Catalogue.TOUS.size() * Mutation.values().length;
        int[][] paliers = {{25, 0}, {50, 1}, {100, 2}};
        String[] titres = {"collection_25", "collection_50", "collection_100"};
        String[] auras = {"etoiles", "neige", "arcenciel"};
        for (int[] pa : paliers) {
            int pc = pa[0];
            if (pr.paliers.contains(pc) || pr.dex.size() * 100 < pc * total) continue;
            pr.paliers.add(pc);
            pr.titres.add(titres[pa[1]]);
            pr.auras.add(auras[pa[1]]);
            double prime = Math.max(10_000, pr.revenu * 600) * (pa[1] + 1);
            pr.credits += prime;
            jeu.hudJeu.banniere(p, "b_quete", "<#A64DFF><b>ORBIDEX " + pc + " %</b>", "Titre " + TITRES.get(titres[pa[1]]) + " <#E6EDF3>+ aura + " + Txt.cr(prime));
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + p.getName() + "</b> a rempli <#A64DFF>" + pc + " %</#A64DFF> de son Orbidex !"));
        }
    }

    // ------------------------------------------------------------------ semaine

    private void semaine() {
        String actuelle = semaineActuelle();
        String passee;
        try {
            passee = fichierSemaine.exists() ? Files.readString(fichierSemaine.toPath()).trim() : "";
        } catch (IOException e) {
            return;
        }
        if (passee.equals(actuelle)) return;
        ecrireSemaine(actuelle);
        if (passee.isEmpty()) return;
        jeu.plugin.getLogger().info("Nouvelle semaine : récompenses de " + passee + ".");
        // Lecture de tous les profils hors du fil du serveur, puis distribution sur le fil du serveur.
        Bukkit.getScheduler().runTaskAsynchronously(jeu.plugin, () -> {
            Map<UUID, Profil> tous = new HashMap<>();
            File[] fichiers = jeu.dossierJoueurs.listFiles((d, n) -> n.endsWith(".yml"));
            if (fichiers != null) for (File f : fichiers) {
                try {
                    UUID u = UUID.fromString(f.getName().replace(".yml", ""));
                    tous.put(u, Profil.charger(jeu.dossierJoueurs, u, "?"));
                } catch (RuntimeException ignore) {
                }
            }
            if (Stockage.instance != null) for (var e : Stockage.instance.lireTous().entrySet()) {
                Profil b = Profil.depuisTexte(e.getKey(), "?", e.getValue());
                Profil f = tous.get(e.getKey());
                if (b != null && (f == null || b.sauveLe >= f.sauveLe)) tous.put(e.getKey(), b);
            }
            Bukkit.getScheduler().runTask(jeu.plugin, () -> distribuer(passee, tous));
        });
    }

    private void ecrireSemaine(String s) {
        try {
            jeu.plugin.getDataFolder().mkdirs();
            Files.writeString(fichierSemaine.toPath(), s);
        } catch (IOException e) {
            jeu.plugin.getLogger().warning("semaine.txt non écrit : " + e.getMessage());
        }
    }

    private void distribuer(String semaine, Map<UUID, Profil> lus) {
        // Les joueurs connectés : leur profil en mémoire fait foi.
        Map<UUID, Profil> tous = new HashMap<>(lus);
        tous.putAll(jeu.profils);
        List<Profil> l = new ArrayList<>();
        for (Profil p : tous.values()) if (semaine.equals(p.semaine)) l.add(p);
        List<Profil> parGains = new ArrayList<>(l);
        parGains.sort(Comparator.comparingDouble((Profil p) -> p.gainsSemaine).reversed());
        List<Profil> parVols = new ArrayList<>(l);
        parVols.sort(Comparator.comparingLong((Profil p) -> p.volsSemaine).reversed());
        double[] parts = {1800, 1200, 600};
        StringBuilder annonce = new StringBuilder(Txt.PREFIX + "<#E0B864><b>Classement de la semaine</b></#E0B864>");
        for (int i = 0; i < Math.min(3, parGains.size()); i++) {
            Profil p = parGains.get(i);
            if (p.gainsSemaine <= 0) break;
            double prime = Math.max(5_000, p.revenu * parts[i]);
            recompenser(p, prime, i == 0 ? "magnat" : "podium", i == 0 ? "couronne" : null,
                    "<#E0B864>Tu as fini " + (i + 1) + "e des gains de la semaine</#E0B864> : " + Txt.cr(prime) + " et le titre " + TITRES.get(i == 0 ? "magnat" : "podium") + "<#E6EDF3>.");
            annonce.append("\n").append(Classement.rang(i)).append(" <#E6EDF3>").append(p.pseudo).append(" <#5A6270>(gains : ").append(Txt.cr(p.gainsSemaine)).append("<#5A6270>)");
        }
        for (int i = 0; i < Math.min(3, parVols.size()); i++) {
            Profil p = parVols.get(i);
            if (p.volsSemaine <= 0) break;
            double prime = Math.max(5_000, p.revenu * parts[i]);
            recompenser(p, prime, i == 0 ? "roi_voleurs" : "podium", null,
                    "<#FF4D6D>Tu as fini " + (i + 1) + "e des voleurs de la semaine</#FF4D6D> : " + Txt.cr(prime) + " et le titre " + TITRES.get(i == 0 ? "roi_voleurs" : "podium") + "<#E6EDF3>.");
            annonce.append("\n").append(Classement.rang(i)).append(" <#E6EDF3>").append(p.pseudo).append(" <#5A6270>(").append(p.volsSemaine).append(" vols)");
        }
        Bukkit.broadcast(Txt.mm(annonce.toString()));
        for (Profil p : tous.values()) {
            verifierSemaine(p);
            if (!jeu.profils.containsKey(p.uuid)) p.sauver(jeu.dossierJoueurs, jeu.plugin.getLogger());
        }
    }

    private void recompenser(Profil p, double prime, String titre, String aura, String message) {
        p.titres.add(titre);
        if (aura != null) p.auras.add(aura);
        Player en = Bukkit.getPlayer(p.uuid);
        if (en != null && jeu.profils.get(p.uuid) == p) {
            p.credits += prime;
            Txt.send(en, message);
            Txt.sound(en, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        } else {
            p.aVerser += prime;
            p.messages.add(message);
        }
    }

    // ------------------------------------------------------------------ couleurs de la base

    private static int[] zones(Base b) {
        return new int[]{b.cx - 11, b.cx + 11, Math.min(b.s * 12, b.s * 34), Math.max(b.s * 12, b.s * 34)};
    }

    void peindre(Base b, Profil pr) {
        Material sol = pr.sol.isEmpty() ? Arene.SOLS[b.idx] : Material.matchMaterial(pr.sol);
        Material vitre = pr.vitre.isEmpty() ? Material.WHITE_STAINED_GLASS : Material.matchMaterial(pr.vitre);
        if (sol == null) sol = Arene.SOLS[b.idx];
        if (vitre == null) vitre = Material.WHITE_STAINED_GLASS;
        appliquer(b, sol, vitre);
    }

    void effacer(Base b) {
        appliquer(b, Arene.SOLS[b.idx], Material.WHITE_STAINED_GLASS);
    }

    private void appliquer(Base b, Material sol, Material vitre) {
        Material[] avant = peinture.getOrDefault(b.idx, new Material[]{Arene.SOLS[b.idx], Material.WHITE_STAINED_GLASS});
        int[] z = zones(b);
        World w = jeu.w;
        if (avant[0] != sol)
            for (int x = z[0]; x <= z[1]; x++)
                for (int zz = z[2]; zz <= z[3]; zz++)
                    if (w.getBlockAt(x, Arene.Y, zz).getType() == avant[0]) w.getBlockAt(x, Arene.Y, zz).setType(sol, false);
        if (avant[1] != vitre)
            for (int y = Arene.Y + 1; y <= Arene.Y + 3; y++)
                for (int x = z[0]; x <= z[1]; x++)
                    for (int zz = z[2]; zz <= z[3]; zz++) {
                        boolean mur = x == z[0] || x == z[1] || zz == z[2] || zz == z[3];
                        if (mur && w.getBlockAt(x, y, zz).getType() == avant[1]) w.getBlockAt(x, y, zz).setType(vitre, false);
                    }
        peinture.put(b.idx, new Material[]{sol, vitre});
    }

    // ------------------------------------------------------------------ menus

    static String joli(Material m) {
        String n = m.name().replace("_CONCRETE", "").replace("_STAINED_GLASS", "").replace("TINTED_GLASS", "FUMÉ").replace("GLASS", "TRANSPARENT");
        return switch (n) {
            case "RED" -> "Rouge"; case "ORANGE" -> "Orange"; case "YELLOW" -> "Jaune"; case "LIME" -> "Vert clair";
            case "CYAN" -> "Cyan"; case "BLUE" -> "Bleu"; case "PURPLE" -> "Violet"; case "PINK" -> "Rose";
            case "BLACK" -> "Noir"; case "WHITE" -> "Blanc"; case "GREEN" -> "Vert"; case "MAGENTA" -> "Magenta";
            case "FUMÉ" -> "Fumé"; case "TRANSPARENT" -> "Transparent";
            default -> n;
        };
    }

    void ouvrirBase(Player p) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        Gui g = new Gui(54, "<#62CFF4><b>Couleurs de ma base</b> <#5A6270>· " + Txt.cr(pr.credits));
        g.case_(4, Objets.objet(Material.PAINTING, 1, null, "m_perso", "<#62CFF4><b>Ta base, tes couleurs</b>",
                "<gray>Chaque changement coûte " + Txt.cr(PRIX_COULEUR) + "<gray>.", "<gray>En haut : le sol. En bas : les vitres."), null);
        for (int i = 0; i < SOLS.length; i++) {
            Material m = SOLS[i];
            boolean actuel = m.name().equals(pr.sol);
            g.case_(i < 7 ? 10 + i : 19 + (i - 7), Objets.objet(m, 1, null, null, "<#E6EDF3>Sol " + joli(m), actuel ? "<#7CFC9A>Actuel" : "<gray>Clic : " + Txt.cr(PRIX_COULEUR)),
                    x -> changer(x, true, m));
        }
        for (int i = 0; i < VITRES.length; i++) {
            Material m = VITRES[i];
            boolean actuel = m.name().equals(pr.vitre) || (pr.vitre.isEmpty() && m == Material.WHITE_STAINED_GLASS);
            g.case_(i < 7 ? 28 + i : 37 + (i - 7), Objets.objet(m, 1, null, null, "<#E6EDF3>Vitres " + joli(m), actuel ? "<#7CFC9A>Actuel" : "<gray>Clic : " + Txt.cr(PRIX_COULEUR)),
                    x -> changer(x, false, m));
        }
        g.case_(45, Objets.retour(), x -> Objets.ouvrirMenu(x, jeu.profils.get(x.getUniqueId())));
        g.ouvrir(p);
    }

    private void changer(Player p, boolean sol, Material m) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        Base b = jeu.baseDe.get(p.getUniqueId());
        if (pr == null || b == null) return;
        if ((sol ? pr.sol : pr.vitre).equals(m.name())) return;
        if (pr.credits < PRIX_COULEUR) { Txt.send(p, "Il te manque " + Txt.cr(PRIX_COULEUR - pr.credits) + "."); return; }
        pr.credits -= PRIX_COULEUR;
        if (sol) pr.sol = m.name(); else pr.vitre = m.name();
        peindre(b, pr);
        Txt.sound(p, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.2f);
        ouvrirBase(p);
        jeu.hud(p);
    }

    void ouvrirCollection(Player p) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        Gui g = new Gui(54, "<#E0B864><b>Titres et auras</b>");
        g.case_(4, Objets.objet(Material.NAME_TAG, 1, null, "m_collection", "<#E0B864><b>Ta collection</b>",
                "<gray>Rangées 2-3 : titres (affichés sur ta base).", "<gray>Rangées 4-5 : auras (autour de toi).", "<gray>Clic pour porter / enlever."), null);
        int i = 0;
        for (Map.Entry<String, String> e : TITRES.entrySet()) {
            String id = e.getKey();
            boolean a = pr.titres.contains(id), porte = id.equals(pr.titre);
            g.case_(9 + i++, Objets.objet(a ? Material.NAME_TAG : Material.GRAY_DYE, 1, null, null, e.getValue(),
                    a ? (porte ? "<#7CFC9A>Porté — clic pour l'enlever" : "<gray>Clic pour le porter") : "<#5A6270>Pas encore débloqué",
                    "<#5A6270>" + commentTitre(id)), a ? x -> { pr.titre = porte ? "" : id; Txt.sound(x, Sound.UI_BUTTON_CLICK, 1.2f); ouvrirCollection(x); } : null);
        }
        i = 0;
        for (Aura au : AURAS) {
            boolean a = pr.auras.contains(au.id()), porte = au.id().equals(pr.aura);
            String l1 = a ? (porte ? "<#7CFC9A>Portée — clic pour l'enlever" : "<gray>Clic pour la porter")
                    : au.prix() > 0 ? "<#E6EDF3>Clic pour l'acheter : " + Txt.cr(au.prix()) : "<#5A6270>Pas encore débloquée";
            g.case_(27 + i++, Objets.objet(a ? au.icone() : Material.GRAY_DYE, 1, null, null, "<b>Aura</b> " + au.nom(), l1, "<#5A6270>" + au.comment()),
                    x -> aura(x, au));
        }
        g.case_(45, Objets.retour(), x -> Objets.ouvrirMenu(x, jeu.profils.get(x.getUniqueId())));
        g.ouvrir(p);
    }

    private static String commentTitre(String id) {
        return switch (id) {
            case "recrue" -> "Finir le tutoriel";
            case "ascendant" -> "Atteindre l'Ascension 1";
            case "transcendant" -> "Atteindre l'Ascension 5";
            case "collection_25" -> "Orbidex à 25 %";
            case "collection_50" -> "Orbidex à 50 %";
            case "collection_100" -> "Orbidex à 100 %";
            case "podium" -> "Top 3 d'un classement de la semaine";
            case "magnat" -> "1er des gains de la semaine";
            case "roi_voleurs" -> "1er des voleurs de la semaine";
            case "fusion" -> "Réussir une fusion";
            default -> "";
        };
    }

    private void aura(Player p, Aura au) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        if (!pr.auras.contains(au.id())) {
            if (au.prix() <= 0) return;
            if (pr.credits < au.prix()) { Txt.send(p, "Il te manque " + Txt.cr(au.prix() - pr.credits) + "."); return; }
            pr.credits -= au.prix();
            pr.auras.add(au.id());
            pr.aura = au.id();
            Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.6f);
        } else pr.aura = au.id().equals(pr.aura) ? "" : au.id();
        ouvrirCollection(p);
        jeu.hud(p);
    }

    void basculerMusique(Player p) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        pr.musique = !pr.musique;
        if (!pr.musique) p.stopSound("orbitvol:musique", SoundCategory.MUSIC);
        musiqueDepuis.remove(p.getUniqueId());
        Txt.send(p, "Musique d'ambiance " + (pr.musique ? "<#7CFC9A>activée" : "<#FF4D6D>coupée") + "<#E6EDF3>.");
    }
}
