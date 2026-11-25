package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import fr.orbit.vol.Catalogue.Mutation;
import fr.orbit.vol.Catalogue.Orbito;
import fr.orbit.vol.Catalogue.Rarete;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Toute la partie : tapis, bases, achats, vols, bouclier, revenus, météores, HUD. */
public final class Jeu implements Listener {

    enum Etat { TAPIS, ROUTE, POSE, PORTE, LIBRE }

    /** Un Orbito présent dans le monde. */
    static final class Spec {
        Mob mob;
        Orbito o;
        Mutation m;
        Etat etat;
        UUID proprio;      // propriétaire (null sur le tapis)
        int place = -1;    // emplacement dans la base du propriétaire
        UUID voleur;       // joueur qui le porte
        long debutVol;
        int niveau = 1;
        boolean geant;     // Orbito géant d'événement (sans propriétaire)
        double revenu() {
            return o.revenu() * m.mult * Ameliorations.multNiveau(niveau);
        }
        String cle() {
            return o.id() + "|" + m.name() + "|" + niveau;
        }
        /** Ce que le joueur a mis dedans : prix d'achat + niveaux payés. */
        double valeur() {
            double v = prix(this);
            for (int k = 1; k < niveau; k++) v += Ameliorations.prixNiveau(prix(this), k);
            return v;
        }
    }

    static final long BOUCLIER_MS = 60_000, RECHARGE_MS = 30_000, VOL_MAX_MS = 45_000;
    static final long METEORES_TOUTES_S = 15 * 60, METEORES_DUREE_S = 120;

    final OrbitVol plugin;
    final World w;
    final Base[] bases;
    final File dossierJoueurs;
    final Map<UUID, Spec> specs = new HashMap<>();          // par uuid d'entité
    final Map<UUID, Profil> profils = new HashMap<>();
    final Map<UUID, Base> baseDe = new HashMap<>();
    final Map<Integer, UUID> proprioBase = new HashMap<>();
    final Map<Integer, Spec[]> poses = new HashMap<>();
    final Map<Integer, List<TextDisplay>> holos = new HashMap<>();
    final Map<Integer, long[]> boucliers = new HashMap<>(); // {fin, pret}
    final Map<UUID, Spec> porte = new HashMap<>();         // voleur -> Orbito porté
    final Map<UUID, Long> venteDemandee = new HashMap<>();
    final BossBar barreMeteores = BossBar.bossBar(Txt.mm("<#FF7A1A><b>PLUIE DE MÉTÉORES</b> <#E6EDF3>— mutations ×2 et Orbitos Météore ×8 !"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    long meteoresFin = 0;
    long prochainsMeteores;
    int tic = 0;
    long secondes = 0;
    final Map<UUID, Long> alarmes = new HashMap<>();   // intrus -> dernière alarme

    final Pack pack;
    final HudJeu hudJeu;
    final Decor decor;
    final Gadgets gadgets;
    final Classement classement;
    final Evenements evenements;
    final Recompenses recompenses;
    final Echange echange;
    /** Profils lus en base pendant la connexion (hors du fil du serveur). */
    final Map<UUID, String> precharges = new java.util.concurrent.ConcurrentHashMap<>();

    Jeu(OrbitVol plugin, World w, Pack pack) {
        this.plugin = plugin;
        this.w = w;
        this.pack = pack;
        this.hudJeu = new HudJeu(plugin, pack);
        this.decor = new Decor(plugin, w);
        this.gadgets = new Gadgets(this);
        this.classement = new Classement(this);
        this.evenements = new Evenements(this);
        this.recompenses = new Recompenses(this);
        this.echange = new Echange(this);
        pack.quandCharge = p -> { Objets.habiller(p, true); hud(p); };
        this.bases = Arene.bases(w);
        this.dossierJoueurs = new File(plugin.getDataFolder(), "joueurs");
        this.prochainsMeteores = System.currentTimeMillis() + METEORES_TOUTES_S * 1000;
    }

    // ------------------------------------------------------------------ cycle

    void demarrer() {
        for (Entity e : w.getEntities()) if (!(e instanceof Player)) e.remove();
        Bukkit.getScheduler().runTaskTimer(plugin, this::ticDeplacement, 2, 2);
        Bukkit.getScheduler().runTaskTimer(plugin, this::ticVols, 5, 5);
        Bukkit.getScheduler().runTaskTimer(plugin, this::ticSeconde, 20, 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tapisSuivant, 40, 50);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sauverTout, 1200, 1200);
        Bukkit.getScheduler().runTaskTimer(plugin, classement::calculer, 60, 1200);
        Bukkit.getScheduler().runTaskTimer(plugin, recompenses::auras, 10, 10);
        for (Player p : Bukkit.getOnlinePlayers()) rejoindre(p);
    }

    void arreter() {
        classement.retirer();
        if (evenements.actif != null) evenements.arreter();
        decor.toutRetirer();
        for (Spec s : new ArrayList<>(porte.values())) rendre(s, false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(barreMeteores);
            quitter(p);
        }
        for (Spec s : specs.values()) s.mob.remove();
        specs.clear();
    }

    void sauverTout() {
        for (Profil p : profils.values()) p.sauver(dossierJoueurs, plugin.getLogger());
    }

    // ------------------------------------------------------------------ joueurs

    void rejoindre(Player p) {
        // Base (lue pendant la connexion) ou fichier de secours : on garde le plus récent.
        Profil pr = Profil.charger(dossierJoueurs, p.getUniqueId(), p.getName());
        String texte = precharges.remove(p.getUniqueId());
        if (texte != null) {
            Profil b = Profil.depuisTexte(p.getUniqueId(), p.getName(), texte);
            if (b != null && b.sauveLe >= pr.sauveLe) pr = b;
        }
        pr.pseudo = p.getName();
        profils.put(p.getUniqueId(), pr);
        recompenses.connexion(p, pr);
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setHealth(20);
        Objets.donnerDepart(p);
        Objets.habiller(p, false);
        pack.envoyer(p);
        Quetes.verifier(pr, pr.revenu);
        Base b = null;
        for (Base x : bases) if (!proprioBase.containsKey(x.idx)) { b = x; break; }
        if (b == null) {
            p.teleport(new Location(w, 0.5, Arene.Y + 1, 6.5));
            Txt.send(p, "<#FF4D6D>Toutes les bases sont prises.</#FF4D6D> Tu peux visiter, et tu auras une base dès qu'une place se libère.");
        } else {
            attribuer(p, pr, b);
            p.teleport(b.spawn());
            hudJeu.banniere(p, "b_bienvenue", "<#E0B864><b>VOLE MON ORBITO</b>", "Achète sur le tapis, vole chez les autres !");
            Txt.send(p, "Ta base est la <b>" + (b.idx + 1) + "</b>. Clique un Orbito sur le <#62CFF4>Tapis stellaire</#62CFF4> pour l'acheter. "
                    + "Il te rapporte des crédits : marche sur la <#E0B864>plaque dorée</#E0B864> pour encaisser. "
                    + "Clique l'Orbito d'un autre pour le voler et ramène-le chez toi !");
        }
        if (meteoresFin > System.currentTimeMillis()) p.showBossBar(barreMeteores);
        evenements.montrer(p);
        gainsHorsLigne(p, pr);
        if (pr.protege()) Txt.send(p, "<#62CFF4>🛡 Protection de débutant :</#62CFF4> pendant tes " + Math.max(1, (Profil.PROTECTION_S - pr.tempsJeu) / 60)
                + " premières minutes de jeu, personne ne peut te voler (sauf si tu voles toi-même).");
        final Profil prf = pr;
        if (pr.tuto < Profil.TUTO_FINI) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) Tuto.annoncer(p, prf); }, 80);
        hud(p);
    }

    /** Lecture du profil en base pendant la connexion, hors du fil du serveur. */
    @EventHandler
    public void onPreConnexion(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) {
        if (Stockage.instance == null) return;
        String t = Stockage.instance.lire(e.getUniqueId());
        if (t != null) precharges.put(e.getUniqueId(), t);
    }

    // ------------------------------------------------------------------ outils pour œufs, fusion, échanges

    /** Inscrit un Orbito à une place du profil (il apparaîtra avec {@link #apparaitre}). */
    void reserver(Profil pr, int place, Orbito o, Mutation m, int niveau) {
        pr.places[place] = o.id() + "|" + m.name() + "|" + niveau;
    }

    /** Fait apparaître dans la base l'Orbito inscrit à cette place, s'il n'y est pas déjà. */
    void apparaitre(Player p, int place) {
        Profil pr = profils.get(p.getUniqueId());
        Base b = baseDe.get(p.getUniqueId());
        if (pr == null || b == null || pr.places[place] == null) return;
        Spec[] tab = poses.get(b.idx);
        if (tab == null || tab[place] != null) return;
        String[] t = pr.places[place].split("\\|");
        Spec s = creer(Catalogue.get(t[0]), t.length > 1 ? Mutation.valueOf(t[1]) : Mutation.AUCUNE, b.place(place), Etat.POSE);
        s.proprio = p.getUniqueId();
        s.place = place;
        s.niveau = t.length > 2 ? Integer.parseInt(t[2]) : 1;
        tab[place] = s;
        nommer(s);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, s.mob.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
    }

    /** Enlève un Orbito posé de la base et du profil de son propriétaire. */
    void retirerDeBase(Profil pr, Spec s) {
        pr.places[s.place] = null;
        Base b = baseDe.get(s.proprio);
        if (b != null && poses.get(b.idx) != null) poses.get(b.idx)[s.place] = null;
        supprimer(s);
    }

    Spec specA(Player p, int place) {
        Base b = baseDe.get(p.getUniqueId());
        Spec[] tab = b == null ? null : poses.get(b.idx);
        return tab == null ? null : tab[place];
    }

    /** Texte du panneau au-dessus de la base : nom, titre, protection. */
    String titreBase(Profil pr) {
        String t = "<#E0B864><b>BASE DE " + pr.pseudo.toUpperCase() + "</b>";
        String ti = Recompenses.titre(pr);
        if (!ti.isEmpty()) t += "\n" + ti;
        if (pr.protege()) t += "\n<#62CFF4>🛡 Débutant protégé <#5A6270>(" + Math.max(1, (Profil.PROTECTION_S - pr.tempsJeu) / 60) + " min)";
        return t;
    }

    void attribuer(Player p, Profil pr, Base b) {
        Objets.limiter(p);
        proprioBase.put(b.idx, p.getUniqueId());
        baseDe.put(p.getUniqueId(), b);
        int nt = pr.niveau("tourelle");
        if (nt > 0) {
            long rendu = 0;
            for (int i = 0; i < nt && i < Ameliorations.PRIX_TOURELLE.length; i++) rendu += Ameliorations.PRIX_TOURELLE[i];
            pr.credits += rendu;
            pr.niveaux.remove("tourelle");
            Txt.send(p, "Les tourelles laser sont retirées du jeu : tu récupères " + Txt.cr(rendu) + ".");
        }
        Spec[] tab = new Spec[Profil.PLACES];
        poses.put(b.idx, tab);
        for (int i = 0; i < Profil.PLACES; i++) {
            if (pr.places[i] == null) continue;
            String[] t = pr.places[i].split("\\|");
            Mutation m = t.length > 1 ? Mutation.valueOf(t[1]) : Mutation.AUCUNE;
            Spec s = creer(Catalogue.get(t[0]), m, b.place(i), Etat.POSE);
            s.proprio = p.getUniqueId();
            s.place = i;
            s.niveau = t.length > 2 ? Math.max(1, Math.min(Ameliorations.NIVEAU_MAX, Integer.parseInt(t[2]))) : 1;
            tab[i] = s;
            nommer(s);
        }
        List<TextDisplay> l = new ArrayList<>();
        l.add(holo(b.titre(), titreBase(pr)));
        int[] pl = b.plaque();
        l.add(holo(new Location(w, pl[0] + 0.5, pl[1] + 1.4, pl[2] + 0.5), ""));
        int[] bo = b.bouton();
        l.add(holo(new Location(w, bo[0] + 0.5, bo[1] + 1.4, bo[2] + 0.5), ""));
        l.add(holo(b.tourelle().add(0, 1.2, 0), ""));
        holos.put(b.idx, l);
        boucliers.put(b.idx, new long[]{0, 0});
        decor.installer(b, pr.placesDebloquees(), false);
        recompenses.peindre(b, pr);
    }

    double revenuBase(Profil pr, Base b) {
        double r = 0;
        Spec[] tab = b == null ? null : poses.get(b.idx);
        if (tab != null) for (Spec s : tab) if (s != null && s.etat != Etat.ROUTE) r += s.revenu();
        return r * pr.multiplicateur() * evenements.multRevenu();
    }

    /** Gains de la banque orbitale pendant l'absence. */
    void gainsHorsLigne(Player p, Profil pr) {
        int n = pr.niveau("banque");
        if (n == 0 || pr.deconnexion == 0 || pr.revenuDeconnexion <= 0) return;
        long absent = Math.min(System.currentTimeMillis() - pr.deconnexion, Ameliorations.dureeBanqueMs(n));
        double gain = pr.revenuDeconnexion * Ameliorations.partBanque(n) * absent / 1000.0;
        pr.deconnexion = 0;
        if (gain < 1) return;
        pr.enAttente += gain;
        Txt.send(p, "<#E0B864>Ta banque orbitale</#E0B864> a gardé " + Txt.cr(gain) + " pendant ton absence. Va les encaisser sur ta plaque dorée !");
    }

    void decouverte(Player p, Profil pr, Spec s) {
        if (!pr.decouvrir(s.o.id(), s.m)) return;
        Txt.send(p, "<#A64DFF><b>Nouveau dans ton Orbidex :</b></#A64DFF> " + s.o.nomComplet(s.m) + " <#E6EDF3>! <#7CFC9A>+1 %</#7CFC9A> de revenus pour toujours ("
                + pr.dex.size() + "/" + Catalogue.TOUS.size() * Mutation.values().length + ").");
        Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 2f);
        recompenses.palierOrbidex(p, pr);
    }

    void quitter(Player p) {
        UUID u = p.getUniqueId();
        Spec porté = porte.get(u);
        if (porté != null) rendre(porté, false);
        // Ses Orbitos portés par d'autres repartent avec lui.
        for (Spec s : new ArrayList<>(porte.values())) if (u.equals(s.proprio)) rendre(s, false);
        Profil pr = profils.remove(u);
        if (pr != null) {
            pr.deconnexion = System.currentTimeMillis();
            pr.revenuDeconnexion = revenuBase(pr, baseDe.get(u));
            pr.sauver(dossierJoueurs, plugin.getLogger());
        }
        Base b = baseDe.remove(u);
        if (b != null) liberer(b);
        venteDemandee.remove(u);
        hudJeu.oublier(p);
        gadgets.oublier(p.getUniqueId());
    }

    void liberer(Base b) {
        proprioBase.remove(b.idx);
        Spec[] tab = poses.remove(b.idx);
        if (tab != null) for (Spec s : tab) if (s != null) supprimer(s);
        // Les achats en route vers cette base disparaissent aussi.
        for (Spec s : new ArrayList<>(specs.values())) if (s.etat == Etat.ROUTE && !profils.containsKey(s.proprio)) supprimer(s);
        List<TextDisplay> l = holos.remove(b.idx);
        if (l != null) l.forEach(Entity::remove);
        boucliers.remove(b.idx);
        decor.retirer(b);
        recompenses.effacer(b);
    }

    // ------------------------------------------------------------------ entités

    Spec creer(Orbito o, Mutation m, Location loc, Etat etat) {
        Spec s = new Spec();
        s.o = o;
        s.m = m;
        s.etat = etat;
        @SuppressWarnings("unchecked")
        Class<? extends Entity> c = (Class<? extends Entity>) o.type().getEntityClass();
        Entity e = w.spawn(loc, c, ent -> {
            if (!(ent instanceof Mob mob)) return;
            mob.setAI(false);
            mob.setSilent(true);
            mob.setInvulnerable(true);
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
            mob.setCollidable(false);
            mob.setCustomNameVisible(true);
            if (mob instanceof Ageable a) a.setAdult();
            Apparences.appliquer(mob, o);
            AttributeInstance t = mob.getAttribute(Attribute.SCALE);
            if (t != null) t.setBaseValue(o.taille());
            if (m != Mutation.AUCUNE) mob.setGlowing(true);
        });
        s.mob = (Mob) e;
        specs.put(e.getUniqueId(), s);
        return s;
    }

    void supprimer(Spec s) {
        specs.remove(s.mob.getUniqueId());
        if (s.voleur != null) {
            porte.remove(s.voleur);
            Player v = Bukkit.getPlayer(s.voleur);
            if (v != null) v.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        s.mob.remove();
    }

    void nommer(Spec s) {
        String nom = s.o.nomComplet(s.m);
        String ligne;
        if (s.etat == Etat.TAPIS) {
            ligne = nom + " <#5A6270>[" + s.o.rarete().etiquette() + "<#5A6270>] " + Txt.cr(prix(s)) + " <#7CFC9A>+" + Txt.n(s.revenu()) + "/s";
        } else {
            ligne = nom + " <#5A6270>Niv." + (s.niveau >= Ameliorations.NIVEAU_MAX ? "<#E0B864>MAX" : "<#E6EDF3>" + s.niveau) + " <#7CFC9A>+" + Txt.n(s.revenu()) + "/s";
        }
        s.mob.customName(Txt.mm(ligne));
    }

    static double prix(Spec s) {
        return s.o.prix() * (double) s.m.mult;
    }

    TextDisplay holo(Location l, String texte) {
        return w.spawn(l, TextDisplay.class, t -> {
            t.text(Txt.mm(texte));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.setShadowed(true);
        });
    }

    // ------------------------------------------------------------------ tapis

    void tapisSuivant() {
        boolean met = meteoresFin > System.currentTimeMillis();
        Orbito o = Catalogue.tirer();
        Mutation m = Catalogue.tirerMutation(met);
        Spec s = creer(o, m, new Location(w, Arene.TAPIS_X0 + 0.5, Arene.Y + 1, 0.5, -90, 0), Etat.TAPIS);
        nommer(s);
        evenements.arriveeRare(s);
        Rarete r = o.rarete();
        if (r == Rarete.SECRET) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                hudJeu.banniere(p, "b_secret", r.couleur + "<b>SECRET !</b>", o.nomComplet(m) + " <#E6EDF3>est sur le tapis !");
                Txt.sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
            }
        } else if (r.ordinal() >= Rarete.LEGENDAIRE.ordinal() || m.mult >= 5) {
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + o.nomComplet(m) + " <#5A6270>[" + r.etiquette() + "<#5A6270>] <#E6EDF3>arrive sur le tapis !"));
            for (Player p : Bukkit.getOnlinePlayers()) Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
        }
    }

    void ticDeplacement() {
        tic++;
        for (Spec s : new ArrayList<>(specs.values())) {
            if (!s.mob.isValid()) { specs.remove(s.mob.getUniqueId()); continue; }
            if (s.etat == Etat.TAPIS) {
                Location l = s.mob.getLocation().add(0.15, 0, 0);
                if (l.getX() > Arene.TAPIS_X1 + 0.5) { supprimer(s); continue; }
                s.mob.teleport(l);
            } else if (s.etat == Etat.ROUTE) {
                Base b = baseDe.get(s.proprio);
                if (b == null) { supprimer(s); continue; }
                Location cible = b.place(s.place);
                Location l = s.mob.getLocation();
                Vector d = cible.toVector().subtract(l.toVector());
                d.setY(0);
                if (d.length() < 0.6) {
                    s.mob.teleport(cible);
                    s.etat = Etat.POSE;
                    poses.get(b.idx)[s.place] = s;
                    nommer(s);
                } else {
                    Vector pas = d.normalize().multiply(0.45);
                    Location n = l.clone().add(pas);
                    n.setYaw((float) Math.toDegrees(Math.atan2(-pas.getX(), pas.getZ())));
                    s.mob.teleport(n);
                }
            }
            if (s.m != Mutation.AUCUNE && tic % 5 == 0 && s.etat != Etat.PORTE) particules(s);
        }
    }

    void particules(Spec s) {
        Location l = s.mob.getLocation().add(0, s.mob.getHeight() * 0.6, 0);
        Color c = s.m.couleur;
        if (s.m == Mutation.ARCENCIEL) c = Color.fromRGB(java.awt.Color.HSBtoRGB((tic % 60) / 60f, 1f, 1f) & 0xFFFFFF);
        if (s.m == Mutation.METEORE) w.spawnParticle(Particle.FLAME, l, 3, 0.3, 0.3, 0.3, 0.01);
        w.spawnParticle(Particle.DUST, l, 4, 0.35, 0.35, 0.35, 0, new Particle.DustOptions(c, 1.2f));
    }

    // ------------------------------------------------------------------ actions

    void cliquer(Player p, Spec s) {
        switch (s.etat) {
            case TAPIS -> acheter(p, s);
            case POSE -> {
                if (p.getUniqueId().equals(s.proprio)) Objets.ouvrirOrbito(p, s);
                else voler(p, s);
            }
            case LIBRE -> voler(p, s);
            default -> { }
        }
    }

    void acheter(Player p, Spec s) {
        Profil pr = profils.get(p.getUniqueId());
        Base b = baseDe.get(p.getUniqueId());
        if (pr == null || b == null) { Txt.send(p, "Tu n'as pas encore de base."); return; }
        int place = pr.placeLibre();
        if (place < 0) { Txt.send(p, "<#FF4D6D>Ta base est pleine.</#FF4D6D> Revends un Orbito (Maj + clic sur le tien) pour faire de la place."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
        double prix = prix(s);
        if (pr.credits < prix) { Txt.send(p, "Il te manque " + Txt.cr(prix - pr.credits) + "."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
        pr.credits -= prix;
        s.proprio = p.getUniqueId();
        s.place = place;
        s.etat = Etat.ROUTE;
        pr.places[place] = s.cle();
        poses.get(b.idx)[place] = s;
        nommer(s);
        Txt.send(p, "Tu as acheté " + s.o.nomComplet(s.m) + " <#E6EDF3>! Il part vers ta base.");
        Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.4f);
        decouverte(p, pr, s);
        Quetes.avancer(this, p, pr, Quetes.Type.ACHAT, 1);
        if (s.o.rarete().ordinal() >= Rarete.EPIQUE.ordinal()) Quetes.avancer(this, p, pr, Quetes.Type.ACHAT_RARE, 1);
        if (s.m != Mutation.AUCUNE) Quetes.avancer(this, p, pr, Quetes.Type.MUTATION, 1);
        Tuto.avancer(this, p, pr, 0);
        hud(p);
    }

    /** Revend un de ses Orbitos : la moitié de ce qu'on y a mis. */
    void vendre(Player p, Spec s) {
        Profil pr = profils.get(p.getUniqueId());
        Base b = baseDe.get(p.getUniqueId());
        if (pr == null || b == null || s.etat != Etat.POSE || !p.getUniqueId().equals(s.proprio)) return;
        double gain = s.valeur() / 2;
        pr.credits += gain;
        pr.places[s.place] = null;
        poses.get(b.idx)[s.place] = null;
        supprimer(s);
        Txt.send(p, "Revendu pour " + Txt.cr(gain) + ".");
        Txt.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f);
        hud(p);
    }

    /** Monte un de ses Orbitos d'un niveau. */
    void ameliorer(Player p, Spec s) {
        Profil pr = profils.get(p.getUniqueId());
        if (pr == null || s.etat != Etat.POSE || !p.getUniqueId().equals(s.proprio)) return;
        if (s.niveau >= Ameliorations.NIVEAU_MAX) { Txt.send(p, "Cet Orbito est déjà au niveau maximum."); return; }
        double cout = Ameliorations.prixNiveau(prix(s), s.niveau);
        if (pr.credits < cout) { Txt.send(p, "Il te manque " + Txt.cr(cout - pr.credits) + "."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
        pr.credits -= cout;
        s.niveau++;
        pr.places[s.place] = s.cle();
        nommer(s);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, s.mob.getLocation().add(0, s.mob.getHeight() / 2, 0), 30, 0.4, 0.5, 0.4, 0.2);
        Txt.sound(p, Sound.BLOCK_ANVIL_USE, 1.6f);
        if (s.niveau == Ameliorations.NIVEAU_MAX) Txt.send(p, s.o.nomComplet(s.m) + " <#E6EDF3>est au <#E0B864><b>niveau MAX</b></#E0B864> !");
        Quetes.avancer(this, p, pr, Quetes.Type.AMELIORER, 1);
        Tuto.avancer(this, p, pr, 4);
        hud(p);
    }

    /** Achète le niveau suivant d'une amélioration de base. */
    void ameliorerBase(Player p, String id) {
        Profil pr = profils.get(p.getUniqueId());
        Ameliorations.Amelioration a = Ameliorations.get(id);
        if (pr == null || a == null) return;
        int n = pr.niveau(id);
        if (n >= a.max()) { Txt.send(p, "Déjà au maximum."); return; }
        long cout = a.prix()[n];
        if (pr.credits < cout) { Txt.send(p, "Il te manque " + Txt.cr(cout - pr.credits) + "."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
        pr.credits -= cout;
        pr.niveaux.put(id, n + 1);
        Base b = baseDe.get(p.getUniqueId());
        if (b != null) {
            if (id.equals("places")) decor.socles(b, pr.placesDebloquees());
            if (id.equals("tourelle")) decor.tourelle(b, true);
        }
        Txt.send(p, a.nom() + " <#E6EDF3>→ " + a.effets()[n + 1]);
        Txt.sound(p, Sound.BLOCK_BEACON_POWER_SELECT, 1.4f);
        hud(p);
    }

    void voler(Player p, Spec s) {
        UUID u = p.getUniqueId();
        Base mienne = baseDe.get(u);
        Profil pr = profils.get(u);
        if (mienne == null || pr == null) { Txt.send(p, "Il te faut une base pour voler."); return; }
        if (porte.containsKey(u)) { Txt.send(p, "Tu portes déjà un Orbito !"); return; }
        if (pr.placeLibre() < 0) { Txt.send(p, "<#FF4D6D>Ta base est pleine</#FF4D6D> : fais de la place avant de voler."); return; }
        Base sa = baseDe.get(s.proprio);
        if (sa != null && boucliers.getOrDefault(sa.idx, new long[2])[0] > System.currentTimeMillis()) {
            Txt.send(p, "Cette base est protégée par son bouclier."); return;
        }
        Profil victimePr = s.geant ? null : profils.get(s.proprio);
        if (victimePr != null && victimePr.protege()) {
            Txt.send(p, "<#62CFF4>🛡 " + victimePr.pseudo + " débute</#62CFF4> : protégé encore " + Math.max(1, (Profil.PROTECTION_S - victimePr.tempsJeu) / 60) + " min. Vise quelqu'un d'autre !");
            return;
        }
        if (victimePr != null && pr.ascension >= victimePr.ascension + 2) {
            Txt.send(p, "<#FF4D6D>Trop facile !</#FF4D6D> Tu as " + (pr.ascension - victimePr.ascension) + " Ascensions de plus que " + victimePr.pseudo + " : vole des joueurs de ton niveau.");
            return;
        }
        s.etat = Etat.PORTE;
        s.voleur = u;
        s.debutVol = System.currentTimeMillis();
        porte.put(u, s);
        p.addPassenger(s.mob);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, s.geant ? 1 : 0, false, false, true));
        if (s.geant) Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + p.getName() + "</b> emporte l'Orbito géant ! Tapez-le pour qu'il le lâche !"));
        Txt.sound(p, Sound.ENTITY_ITEM_PICKUP, 0.7f);
        Player victime = s.proprio == null ? null : Bukkit.getPlayer(s.proprio);
        if (victime != null) {
            hudJeu.banniere(victime, "b_voleur", "<#FF4D6D><b>AU VOLEUR !</b>", p.getName() + " emporte " + s.o.nomComplet(s.m));
            Txt.sound(victime, Sound.BLOCK_BELL_USE, 1.2f);
        }
    }

    /** Remet l'Orbito porté à sa place chez son propriétaire. */
    void rendre(Spec s, boolean annonce) {
        Player v = s.voleur == null ? null : Bukkit.getPlayer(s.voleur);
        if (s.voleur != null) porte.remove(s.voleur);
        s.voleur = null;
        if (v != null) {
            v.removePassenger(s.mob);
            v.removePotionEffect(PotionEffectType.SLOWNESS);
            if (annonce) Txt.bar(v, "<#FF4D6D>Tu as lâché " + s.o.nomComplet(s.m) + " <#FF4D6D>!");
        }
        if (s.geant) {
            // Le géant lâché reste là où il est tombé, libre pour le suivant.
            s.etat = Etat.LIBRE;
            if (v != null) s.mob.teleport(v.getLocation());
            return;
        }
        Base b = baseDe.get(s.proprio);
        if (b == null) { supprimer(s); return; }
        s.etat = Etat.POSE;
        s.mob.teleport(b.place(s.place));
    }

    void transferer(Player voleur, Spec s) {
        Profil pv = profils.get(voleur.getUniqueId());
        Profil victime = profils.get(s.proprio);
        Base mienne = baseDe.get(voleur.getUniqueId());
        int place = pv.placeLibre();
        if (place < 0) { rendre(s, true); return; }
        String nomVictime = victime != null ? victime.pseudo : "?";
        if (victime != null) victime.places[s.place] = null;
        Base sa = baseDe.get(s.proprio);
        if (sa != null) poses.get(sa.idx)[s.place] = null;
        porte.remove(voleur.getUniqueId());
        voleur.removePassenger(s.mob);
        voleur.removePotionEffect(PotionEffectType.SLOWNESS);
        s.voleur = null;
        s.proprio = voleur.getUniqueId();
        s.place = place;
        s.etat = Etat.POSE;
        pv.places[place] = s.cle();
        pv.volsReussis++;
        Recompenses.verifierSemaine(pv);
        pv.volsSemaine++;
        if (s.geant) evenements.geantPris(voleur, s);
        else if (pv.protege()) {
            pv.protectionRompue = true;
            Txt.send(voleur, "<#62CFF4>🛡 Tu as volé :</#62CFF4> ta protection de débutant est terminée, on peut te voler aussi maintenant !");
        }
        poses.get(mienne.idx)[place] = s;
        s.mob.teleport(mienne.place(place));
        nommer(s);
        decouverte(voleur, pv, s);
        hudJeu.banniere(voleur, "b_vole", "<#7CFC9A><b>VOLÉ !</b>", s.o.nomComplet(s.m) + " <#E6EDF3>est à toi");
        Quetes.avancer(this, voleur, pv, Quetes.Type.VOL, 1);
        if (s.m != Mutation.AUCUNE) Quetes.avancer(this, voleur, pv, Quetes.Type.MUTATION, 1);
        Txt.sound(voleur, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f);
        Player pvict = victime != null ? Bukkit.getPlayer(victime.uuid) : null;
        if (pvict != null) Txt.send(pvict, "<#FF4D6D>" + voleur.getName() + " t'a volé " + s.o.nomComplet(s.m) + "<#FF4D6D>.");
        if (s.o.rarete().ordinal() >= Rarete.MYTHIQUE.ordinal())
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + voleur.getName() + "</b> a volé " + s.o.nomComplet(s.m) + " <#E6EDF3>à <b>" + nomVictime + "</b> !"));
        hud(voleur);
    }

    void ticVols() {
        long now = System.currentTimeMillis();
        for (Spec s : new ArrayList<>(porte.values())) {
            Player v = Bukkit.getPlayer(s.voleur);
            if (v == null) { rendre(s, false); continue; }
            long reste = VOL_MAX_MS - (now - s.debutVol);
            if (reste <= 0) { rendre(s, true); continue; }
            Base mienne = baseDe.get(v.getUniqueId());
            if (mienne != null && mienne.dedans(v.getLocation())) { transferer(v, s); continue; }
            hudJeu.barreVol(v, reste / (double) VOL_MAX_MS, reste / 1000,
                    "<#E0B864><b>Ramène " + s.o.nomComplet(s.m) + " <#E0B864><b>dans ta base !</b> <#E6EDF3>" + (reste / 1000) + " s");
        }
    }

    void bouclier(Player p, Base b) {
        if (!p.getUniqueId().equals(proprioBase.get(b.idx))) { Txt.bar(p, "<#FF4D6D>Ce n'est pas ton bouclier."); return; }
        long now = System.currentTimeMillis();
        long[] t = boucliers.get(b.idx);
        if (t[0] > now) { Txt.bar(p, "<#62CFF4>Bouclier déjà actif."); return; }
        if (t[1] > now) { Txt.bar(p, "<#FF4D6D>Bouclier en recharge : " + ((t[1] - now) / 1000 + 1) + " s"); return; }
        int nb = profils.get(p.getUniqueId()).niveau("bouclier");
        t[0] = now + Ameliorations.dureeBouclierMs(nb);
        t[1] = t[0] + Ameliorations.rechargeBouclierMs(nb);
        decor.porte(b, true);
        for (Player o : w.getPlayers())
            if (!o.equals(p) && b.dedans(o.getLocation())) {
                Spec s = porte.get(o.getUniqueId());
                o.teleport(b.dehors());
                if (s != null && p.getUniqueId().equals(s.proprio)) rendre(s, true);
            }
        Txt.sound(p, Sound.BLOCK_BEACON_ACTIVATE, 1.4f);
        Txt.send(p, "<#62CFF4>Bouclier laser activé pour " + Ameliorations.dureeBouclierMs(nb) / 1000 + " secondes.");
        Quetes.avancer(this, p, profils.get(p.getUniqueId()), Quetes.Type.BOUCLIER, 1);
        Tuto.avancer(this, p, profils.get(p.getUniqueId()), 2);
    }

    void encaisser(Player p, Base b) {
        if (!p.getUniqueId().equals(proprioBase.get(b.idx))) return;
        Profil pr = profils.get(p.getUniqueId());
        if (pr.enAttente < 1) return;
        Txt.bar(p, "<#7CFC9A>+ " + Txt.cr(pr.enAttente));
        Quetes.avancer(this, p, pr, Quetes.Type.ENCAISSER, pr.enAttente);
        pr.credits += pr.enAttente;
        Recompenses.verifierSemaine(pr);
        pr.gainsSemaine += pr.enAttente;
        pr.enAttente = 0;
        Txt.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        Tuto.avancer(this, p, pr, 1);
        hud(p);
    }

    void ascension(Player p, boolean confirme) {
        Profil pr = profils.get(p.getUniqueId());
        if (pr == null) return;
        long cout = pr.coutAscension();
        if (pr.credits < cout) { Txt.send(p, "L'Ascension " + (pr.ascension + 1) + " coûte " + Txt.cr(cout) + ". Il te manque " + Txt.cr(cout - pr.credits) + "."); return; }
        if (!confirme) {
            Txt.send(p, "<b>Ascension " + (pr.ascension + 1) + "</b> : tu perds tes crédits et tes Orbitos, mais tous tes revenus sont multipliés par <#7CFC9A>×" + (1 + 0.5 * (pr.ascension + 1)) + "</#7CFC9A> pour toujours. Tape <#E0B864>/vol ascension confirmer</#E0B864>.");
            return;
        }
        for (Spec s : new ArrayList<>(porte.values())) if (p.getUniqueId().equals(s.proprio)) rendre(s, false);
        Base b = baseDe.get(p.getUniqueId());
        if (b != null) {
            Spec[] tab = poses.get(b.idx);
            for (int i = 0; i < tab.length; i++) if (tab[i] != null) { supprimer(tab[i]); tab[i] = null; }
        }
        for (Spec s : new ArrayList<>(specs.values())) if (s.etat == Etat.ROUTE && p.getUniqueId().equals(s.proprio)) supprimer(s);
        java.util.Arrays.fill(pr.places, null);
        pr.credits = Profil.DEPART;
        pr.enAttente = 0;
        pr.ascension++;
        pr.titres.add("ascendant");
        if (pr.ascension >= 5) pr.titres.add("transcendant");
        pr.sauver(dossierJoueurs, plugin.getLogger());
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + p.getName() + "</b> atteint l'<#E0B864><b>Ascension " + pr.ascension + "</b></#E0B864> !"));
        for (Player o : Bukkit.getOnlinePlayers()) Txt.sound(o, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f);
        hudJeu.banniere(p, "b_ascension", "<#E0B864><b>ASCENSION !</b>", "Tous tes revenus ×" + (1 + 0.5 * pr.ascension) + " pour toujours");
        hud(p);
    }

    // ------------------------------------------------------------------ seconde

    void ticSeconde() {
        long now = System.currentTimeMillis();
        // Revenus.
        secondes++;
        decor.balayer(secondes);
        evenements.tic();
        recompenses.seconde();
        for (Player p : w.getPlayers()) {
            Profil pr = profils.get(p.getUniqueId());
            if (pr == null) continue;
            boolean avant = pr.protege();
            pr.tempsJeu++;
            if (avant && !pr.protege()) Txt.send(p, "<#62CFF4>🛡 Ta protection de débutant est terminée.</#62CFF4> Défends bien ta base !");
            Tuto.guider(this, p, pr);
        }
        for (Map.Entry<UUID, Base> e : baseDe.entrySet()) {
            Profil pr = profils.get(e.getKey());
            if (pr == null) continue;
            pr.enAttente += revenuBase(pr, e.getValue());
            int cadence = Ameliorations.cadenceTourelle(pr.niveau("tourelle"));
            if (cadence > 0 && secondes % cadence == 0) tirer(e.getValue(), pr);
        }
        // Boucliers et hologrammes.
        for (Map.Entry<Integer, long[]> e : boucliers.entrySet()) {
            long[] t = e.getValue();
            Base b = bases[e.getKey()];
            if (t[0] != 0 && t[0] <= now) { t[0] = 0; decor.porte(b, false); }
            List<TextDisplay> l = holos.get(b.idx);
            Profil pr = profils.get(proprioBase.get(b.idx));
            if (l == null || pr == null) continue;
            if (secondes % 5 == 0) l.get(0).text(Txt.mm(titreBase(pr)));
            l.get(1).text(Txt.mm("<#E0B864><b>ENCAISSER</b>\n" + Txt.cr(pr.enAttente)));
            String bo = t[0] > now ? "<#62CFF4><b>BOUCLIER ACTIF</b>\n<#E6EDF3>" + ((t[0] - now) / 1000 + 1) + " s"
                    : t[1] > now ? "<#5A6270><b>BOUCLIER</b>\n<#FF4D6D>recharge " + ((t[1] - now) / 1000 + 1) + " s"
                    : "<#62CFF4><b>BOUCLIER</b>\n<#7CFC9A>prêt";
            l.get(2).text(Txt.mm(bo));
        }
        // Pluie de météores.
        if (meteoresFin == 0 && now >= prochainsMeteores) lancerMeteores();
        if (meteoresFin != 0) {
            if (now >= meteoresFin) {
                meteoresFin = 0;
                prochainsMeteores = now + METEORES_TOUTES_S * 1000;
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barreMeteores);
                Bukkit.broadcast(Txt.mm(Txt.PREFIX + "La pluie de météores est finie."));
            } else {
                barreMeteores.progress(Math.max(0f, Math.min(1f, (meteoresFin - now) / (METEORES_DUREE_S * 1000f))));
                for (int i = 0; i < 6; i++) {
                    double x = ThreadLocalRandom.current().nextDouble(-Arene.BORD_X, Arene.BORD_X);
                    double z = ThreadLocalRandom.current().nextDouble(-Arene.BORD_Z, Arene.BORD_Z);
                    w.spawnParticle(Particle.FLAME, x, Arene.Y + 20, z, 20, 0.2, 6, 0.2, 0.02);
                }
            }
        }
        for (Player p : w.getPlayers()) hud(p);
    }

    /** La tourelle vise l'intrus le plus proche dans la base et le repousse vers la sortie. */
    void tirer(Base b, Profil pr) {
        Location t = b.tourelle();
        Player cible = null;
        double best = Double.MAX_VALUE;
        for (Player o : w.getPlayers()) {
            if (o.getUniqueId().equals(pr.uuid) || !b.dedans(o.getLocation()) || o.getGameMode() == GameMode.SPECTATOR) continue;
            double d = o.getLocation().distanceSquared(t);
            if (d < best) { best = d; cible = o; }
        }
        if (cible == null) return;
        Location vise = cible.getLocation().add(0, 1, 0);
        Vector ray = vise.toVector().subtract(t.toVector());
        double len = ray.length();
        ray.normalize();
        Particle.DustOptions rouge = new Particle.DustOptions(Color.fromRGB(0xFF4D6D), 1f);
        decor.tir(b, vise);
        w.spawnParticle(Particle.DUST, vise, 12, 0.25, 0.4, 0.25, 0, rouge);
        w.playSound(t, Sound.ENTITY_GUARDIAN_ATTACK, 0.8f, 1.8f);
        Vector sortie = b.dehors().toVector().subtract(cible.getLocation().toVector()).setY(0);
        if (sortie.lengthSquared() > 0.01) cible.setVelocity(sortie.normalize().multiply(1.3).setY(0.5));
        cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, true));
        Spec s = porte.get(cible.getUniqueId());
        if (s != null && pr.niveau("tourelle") >= 3) rendre(s, true);
        Txt.bar(cible, "<#FF4D6D><b>Tourelle laser !</b>");
    }

    void lancerMeteores() {
        meteoresFin = System.currentTimeMillis() + METEORES_DUREE_S * 1000;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(barreMeteores);
            hudJeu.banniere(p, "b_meteores", "<#FF7A1A><b>PLUIE DE MÉTÉORES</b>", "Les mutations tombent du ciel !");
            Txt.sound(p, Sound.ENTITY_GENERIC_EXPLODE, 0.6f);
        }
    }

    // ------------------------------------------------------------------ HUD

    void hud(Player p) {
        Profil pr = profils.get(p.getUniqueId());
        if (pr == null) return;
        Scoreboard sb = p.getScoreboard();
        if (sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(sb);
        }
        Objective ob = sb.getObjective("orbito");
        if (ob == null) {
            ob = sb.registerNewObjective("orbito", Criteria.DUMMY, Txt.mm("<#E0B864><b>VOLE MON ORBITO</b>"));
            ob.setDisplaySlot(DisplaySlot.SIDEBAR);
            ob.numberFormat(NumberFormat.blank());
        }
        double revenu = revenuBase(pr, baseDe.get(p.getUniqueId()));
        pr.revenu = revenu;
        hudJeu.panneau(p, pr, revenu);
        long quetesFaites = Quetes.liste(pr).stream().filter(q -> q.faite).count();
        // { icône, texte } ; icône 0 = aucune.
        String entete = pr.tuto < Profil.TUTO_FINI ? "<#E0B864>Tuto " + (pr.tuto + 1) + "/5 : <#E6EDF3>" + Tuto.COURTS[pr.tuto]
                : pr.protege() ? "<#62CFF4>🛡 Protégé encore " + Math.max(1, (Profil.PROTECTION_S - pr.tempsJeu) / 60) + " min" : " ";
        Object[][] lignes = {
                {(char) 0, entete},
                {'', "<#E6EDF3>Crédits : " + Txt.cr(pr.credits)},
                {'', "<#E6EDF3>Revenu : <#7CFC9A>+" + Txt.n(revenu) + "/s"},
                {'', "<#E6EDF3>À encaisser : " + Txt.cr(pr.enAttente)},
                {(char) 0, "  "},
                {'', "<#E6EDF3>Orbitos : <#62CFF4>" + pr.nombre() + "/" + pr.placesDebloquees()},
                {'', "<#E6EDF3>Bonus : <#7CFC9A>×" + String.format(java.util.Locale.FRANCE, "%.2f", pr.multiplicateur())},
                {'', "<#E6EDF3>Orbidex : <#A64DFF>" + pr.dex.size() + "/" + Catalogue.TOUS.size() * Mutation.values().length},
                {'', "<#E6EDF3>Quêtes : <#62CFF4>" + quetesFaites + "/3" + (pr.serie > 0 ? " <#5A6270>· série " + pr.serie : "")},
                {'', "<#E6EDF3>Ascension : <#E0B864>" + pr.ascension},
                {(char) 0, "   "},
                {(char) 0, "<#3A7FFF>orbit-mc"}
        };
        for (int i = 0; i < lignes.length; i++) {
            var sc = ob.getScore("l" + i);
            sc.setScore(lignes.length - i);
            char ic = (Character) lignes[i][0];
            net.kyori.adventure.text.Component c = Txt.mm((String) lignes[i][1]);
            sc.customName(ic == 0 ? c : hudJeu.icone(p, ic).append(c));
        }
    }

    // ------------------------------------------------------------------ événements

    Spec spec(Entity e) {
        return e == null ? null : specs.get(e.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) rejoindre(e.getPlayer()); }, 2);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        quitter(e.getPlayer());
        precharges.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Spec s = spec(e.getRightClicked());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        cliquer(e.getPlayer(), s);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getWorld() != w) return;
        Spec s = spec(e.getEntity());
        if (s != null) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p) cliquer(p, s);
            return;
        }
        if (!(e.getEntity() instanceof Player victime)) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent d) {
            Player attaquant = d.getDamager() instanceof Player p ? p : null;
            if (attaquant == null || attaquant.equals(victime)) return;
            boolean marteau = Objets.est(attaquant.getInventory().getItemInMainHand(), "marteau");
            boolean batte = marteau || Objets.est(attaquant.getInventory().getItemInMainHand(), "batte");
            if (marteau) gadgets.marteau(attaquant, victime);
            Vector dir = victime.getLocation().toVector().subtract(attaquant.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() < 0.01) dir = attaquant.getLocation().getDirection().setY(0);
            victime.setVelocity(dir.normalize().multiply(marteau ? 2.4 : batte ? 1.6 : 0.6).setY(marteau ? 0.7 : batte ? 0.45 : 0.3));
            w.playSound(victime.getLocation(), batte ? Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK : Sound.ENTITY_PLAYER_ATTACK_WEAK, 1f, 1f);
            Spec porté = porte.get(victime.getUniqueId());
            if (porté != null) {
                rendre(porté, true);
                Profil pa = profils.get(attaquant.getUniqueId());
                if (pa != null) Quetes.avancer(this, attaquant, pa, Quetes.Type.DEFENSE, 1);
            }
        }
    }

    @EventHandler
    public void onProjectile(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (pr.getWorld() != w || !(pr instanceof Snowball sb)) return;
        if (gadgets.impact(sb, e.getHitEntity() != null ? e.getHitEntity().getLocation() : pr.getLocation())) return;
        if (!(e.getHitEntity() instanceof Player cible)) return;
        cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true, true));
        w.spawnParticle(Particle.ITEM_SNOWBALL, cible.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0);
        Spec porté = porte.get(cible.getUniqueId());
        if (porté != null) rendre(porté, true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld() != w) return;
        Block bl = e.getClickedBlock();
        if (e.getAction() == Action.PHYSICAL && bl != null) {
            for (Base b : bases) {
                int[] pl = b.plaque();
                if (bl.getX() == pl[0] && bl.getY() == pl[1] && bl.getZ() == pl[2]) { encaisser(p, b); return; }
            }
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && bl != null) {
            for (Base b : bases) {
                int[] k = b.bouton();
                if (bl.getX() == k[0] && bl.getY() == k[1] && bl.getZ() == k[2]) { bouclier(p, b); return; }
            }
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            var item = p.getInventory().getItemInMainHand();
            if (Objets.est(item, "boutique")) { e.setCancelled(true); Objets.ouvrirMenu(p, profils.get(p.getUniqueId())); }
            else if (Objets.est(item, "filet")) {
                // Le filet part tout seul (boule de neige) : on bloque seulement s'il recharge.
                if (!gadgets.pret(p, "filet")) e.setCancelled(true);
            } else if (Objets.est(item, "cape")) {
                e.setCancelled(true);
                if (!gadgets.pret(p, "cape")) return;
                item.setAmount(item.getAmount() - 1);
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0, false, false, true));
                Txt.sound(p, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f);
            } else if (gadgets.utiliser(p, item)) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Player p = e.getPlayer();
        if (p.getWorld() != w) return;
        if (e.getTo().getY() < Arene.Y - 10) {
            Spec s = porte.get(p.getUniqueId());
            if (s != null) rendre(s, true);
            Base b = baseDe.get(p.getUniqueId());
            p.teleport(b != null ? b.spawn() : new Location(w, 0.5, Arene.Y + 1, 6.5));
            return;
        }
        // Plaque d'encaissement (modèle 3D, plus de plaque de pression).
        for (Base b : bases) {
            int[] pl = b.plaque();
            if (e.getTo().getBlockX() == pl[0] && e.getTo().getBlockY() == pl[1] && e.getTo().getBlockZ() == pl[2]) { encaisser(p, b); break; }
        }
        long now = System.currentTimeMillis();
        for (Base b : bases) {
            long[] t = boucliers.get(b.idx);
            UUID proprio = proprioBase.get(b.idx);
            if (t == null || p.getUniqueId().equals(proprio)) continue;
            if (!b.dedans(e.getTo()) || b.dedans(e.getFrom())) continue;
            if (t[0] > now) {
                e.setCancelled(true);
                Txt.bar(p, "<#62CFF4>Bouclier laser actif !");
                continue;
            }
            // Alarme : le propriétaire est prévenu et l'intrus brille.
            Profil pr = profils.get(proprio);
            Player owner = Bukkit.getPlayer(proprio);
            if (pr == null || owner == null || pr.niveau("alarme") == 0) continue;
            Long der = alarmes.get(p.getUniqueId());
            if (der != null && now - der < 10_000) continue;
            alarmes.put(p.getUniqueId(), now);
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, false));
            owner.showTitle(Title.title(Txt.mm("<#FFD447><b>ALARME</b>"), Txt.mm("<#E6EDF3>" + p.getName() + " est entré dans ta base !"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(300))));
            Txt.sound(owner, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f);
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getLocation().getWorld() == w && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler
    public void onFaim(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld() == w) e.setCancelled(true);
    }

    @EventHandler
    public void onCasse(BlockBreakEvent e) {
        if (e.getBlock().getWorld() == w && !e.getPlayer().hasPermission("orbitvol.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void onPose(BlockPlaceEvent e) {
        if (e.getBlock().getWorld() == w && !e.getPlayer().hasPermission("orbitvol.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void onJeter(PlayerDropItemEvent e) {
        if (Objets.est(e.getItemDrop().getItemStack(), "boutique")) e.setCancelled(true);
    }

    /** Clic gauche sur un Orbito (il est invulnérable, donc aucun événement de dégâts). */
    @EventHandler
    public void onFrappe(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        Spec s = spec(e.getAttacked());
        if (s == null) return;
        e.setCancelled(true);
        cliquer(e.getPlayer(), s);
    }
}
