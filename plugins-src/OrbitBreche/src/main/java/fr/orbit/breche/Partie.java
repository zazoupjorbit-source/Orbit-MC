package fr.orbit.breche;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Une partie de BRÈCHE sur une carte : Bleu contre Or, manches de pose et désamorçage du Noyau. Les attaquants
 * doivent poser le Noyau sur un site (A ou B) et le défendre jusqu'à l'explosion ; les défenseurs l'empêchent ou
 * le désamorcent. Phase d'achat, économie de crédits (¤), changement de côté à la mi-temps. Les places vides sont
 * tenues par des bots.
 */
final class Partie {

    enum Phase { ATTENTE, ACHAT, COMBAT, NOYAU, FIN_MANCHE, FIN }

    static final int POSE = 70, DESAMORCE = 140, RAYON_NOYAU = 14;
    static final String[] NOMS_BOTS = {"Vega", "Orion", "Lyra", "Sirius", "Altaïr", "Rigel", "Deneb", "Nova", "Pulsar", "Quasar", "Zénith", "Atlas",
            "Castor", "Pollux", "Mira", "Electra"};

    final OrbitBreche pl;
    final Carte carte;
    final Plan plan;
    final World w;
    final Random rnd = new Random();
    final List<Combattant> tous = new ArrayList<>();
    final Map<UUID, Combattant> parEntite = new HashMap<>();
    final Map<UUID, Etat> etats = new HashMap<>();
    final List<Player> renforts = new ArrayList<>();
    final Pouvoirs pouvoirs;
    final Tir tir;
    final Bots bots;
    final int parEquipe, victoire, niveau;
    final UUID hote;
    /** Partie d'essai : que des bots, lancée par un admin (ou la console), racontée dans la console ; vitesse = ticks de jeu par tick serveur. */
    boolean essai;
    int vitesse = 1;

    Phase phase = Phase.ATTENTE;
    int t, reste, manche, attaque;
    final int[] score = new int[2], serieDefaites = new int[2];
    boolean poseCetteManche;
    // Noyau
    boolean noyauPose, noyauAuSol;
    double noyauX, noyauZ;
    char noyauSite;
    int noyauReste;
    ItemDisplay noyauVue;
    private BossBar barre;
    private int prochainBip;
    private String raisonFin = "";
    private boolean arretee;

    Partie(OrbitBreche pl, Carte carte, Player hote, int parEquipe, int niveau) {
        this.pl = pl;
        this.carte = carte;
        this.plan = carte.plan;
        this.w = carte.w;
        this.hote = hote == null ? null : hote.getUniqueId();
        this.parEquipe = Math.max(1, Math.min(5, parEquipe));
        this.niveau = Math.max(1, Math.min(3, niveau));
        this.victoire = Math.max(2, Math.min(13, pl.getConfig().getInt("partie.manches-victoire", 4)));
        this.pouvoirs = new Pouvoirs(this);
        this.tir = new Tir(this);
        this.bots = new Bots(this);
        reste = pl.getConfig().getInt("partie.attente-secondes", 30) * 20;
        barre = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
        carte.barrieres(true);
        carte.debloquer();
        carte.poserLettres();
    }

    // ------------------------------------------------------------------ joueurs

    List<Combattant> humains() {
        List<Combattant> l = new ArrayList<>();
        for (Combattant c : tous) {
            if (c.p != null) {
                l.add(c);
            }
        }
        return l;
    }

    boolean contient(Player p) {
        return parEntite.containsKey(p.getUniqueId()) || renforts.contains(p);
    }

    Combattant de(Player p) {
        return parEntite.get(p.getUniqueId());
    }

    int effectif(int e) {
        int n = 0;
        for (Combattant c : tous) {
            if (c.equipe == e) {
                n++;
            }
        }
        return n;
    }

    /** Un joueur rejoint : tout de suite pendant l'attente, sinon il prendra la place d'un bot à la prochaine phase d'achat. */
    boolean rejoindre(Player p) {
        if (phase == Phase.FIN || contient(p)) {
            return false;
        }
        etats.put(p.getUniqueId(), new Etat(p));
        Etat.preparer(p);
        if (phase == Phase.ATTENTE) {
            int[] humains = new int[2];
            for (Combattant c : tous) {
                humains[c.equipe]++;
            }
            if (humains[0] >= parEquipe && humains[1] >= parEquipe) {
                pl.rendre(p, etats.remove(p.getUniqueId()));
                return false;
            }
            Combattant c = new Combattant(p, p.getName());
            c.equipe = humains[0] <= humains[1] && humains[0] < parEquipe ? 0 : 1;
            c.agent = pl.profils().agentPrefere(p);
            if (c.agent != null && agentPris(c.equipe, c.agent, c)) {
                c.agent = null;
            }
            tous.add(c);
            parEntite.put(p.getUniqueId(), c);
            pl.teleporter(p, carte.base(c.equipe == attaque ? 't' : 'd', tous.size(), rnd));
            hotbarAttente(c);
            p.showBossBar(barre);
            annoncer(c.nomCouleur() + " <#9AA4B2>rejoint l'équipe " + Txt.equipe(c.equipe) + ".");
            Txt.send(p, "Choisis ton agent (étoile de la barre) · départ dans <#E0B864>" + Txt.chrono(reste) + "</#E0B864>"
                    + (p.getUniqueId().equals(hote) ? " · <#7CFF8A>/breche lancer</#7CFF8A> pour partir tout de suite." : "."));
            pl.menus().ouvrirAgents(p, this);
            return true;
        }
        renforts.add(p);
        p.setGameMode(GameMode.SPECTATOR);
        pl.teleporter(p, new Location(w, plan.cx(), Plan.Y + 14, plan.cz(), 0f, 70f));
        p.showBossBar(barre);
        Txt.send(p, "Partie en cours : tu remplaces un bot dès la prochaine phase d'achat. En attendant, regarde la manche !");
        return true;
    }

    boolean agentPris(int equipe, Agent a, Combattant sauf) {
        for (Combattant o : tous) {
            if (o != sauf && o.equipe == equipe && o.agent == a) {
                return true;
            }
        }
        return false;
    }

    /** Un joueur s'en va (commande, déconnexion, téléportation ailleurs). Un bot reprend sa place si la partie continue. */
    void quitter(Player p, boolean teleporter) {
        if (renforts.remove(p)) {
            p.hideBossBar(barre);
            pl.rendre(p, etats.remove(p.getUniqueId()));
            if (teleporter) {
                pl.versSalle(p);
            }
            return;
        }
        Combattant c = parEntite.remove(p.getUniqueId());
        if (c == null) {
            return;
        }
        p.hideBossBar(barre);
        tous.remove(c);
        pouvoirs.oublier(c);
        pl.rendre(p, etats.remove(p.getUniqueId()));
        if (teleporter) {
            pl.versSalle(p);
        }
        if (!essai && humains().isEmpty() && renforts.isEmpty()) {
            arreter("plus aucun joueur");
            return;
        }
        if (phase != Phase.ATTENTE && phase != Phase.FIN) {
            Combattant b = nouveauBot(c.equipe, c.agent);
            b.credits = c.credits;
            b.primaire = c.primaire;
            b.secondaire = c.secondaire;
            b.bouclier = c.bouclier;
            b.ult = c.ult;
            System.arraycopy(c.charges, 0, b.charges, 0, 4);
            b.kills = c.kills;
            b.morts = c.morts;
            b.vivant = c.vivant && phase == Phase.ACHAT;
            if (b.vivant) {
                Location l = c.pieds(w);
                b.x = l.getX();
                b.z = l.getZ();
                b.yaw = l.getYaw();
                b.hp = c.hp;
                b.pleinMunitions();
                apparaitreBot(b);
            }
            if (c.porteNoyau) {
                c.porteNoyau = false;
                if (b.vivant) {
                    b.porteNoyau = true;
                } else {
                    lacherNoyau(c.pieds(w).getX(), c.pieds(w).getZ());
                }
            }
            annoncer(c.nomCouleur() + " <#9AA4B2>a quitté la partie : un bot prend sa place.");
            verifierFin();
        } else {
            annoncer(c.nomCouleur() + " <#9AA4B2>a quitté la partie.");
        }
    }

    Combattant nouveauBot(int equipe, Agent agent) {
        List<String> noms = new ArrayList<>(List.of(NOMS_BOTS));
        Collections.shuffle(noms, rnd);
        String nom = "Bot " + noms.get(0);
        for (String n : noms) {
            boolean pris = false;
            for (Combattant o : tous) {
                if (o.nom.equals("Bot " + n)) {
                    pris = true;
                    break;
                }
            }
            if (!pris) {
                nom = "Bot " + n;
                break;
            }
        }
        Combattant b = new Combattant(null, nom);
        b.equipe = equipe;
        b.niveau = niveau;
        b.agent = agent != null && !agentPris(equipe, agent, null) ? agent : agentLibre(equipe);
        tous.add(b);
        return b;
    }

    Agent agentLibre(int equipe) {
        List<Agent> l = new ArrayList<>(List.of(Agent.values()));
        Collections.shuffle(l, rnd);
        for (Agent a : l) {
            if (!agentPris(equipe, a, null)) {
                return a;
            }
        }
        return l.get(0);
    }

    // ------------------------------------------------------------------ déroulement

    void lancer() {
        if (phase == Phase.ATTENTE) {
            reste = 1;
        }
    }

    private void demarrer() {
        for (Combattant c : tous) {
            if (c.agent == null) {
                c.agent = agentLibre(c.equipe);
                if (c.p != null) {
                    Txt.send(c.p, "Agent tiré au sort : " + c.agent.nomCouleur() + " <#9AA4B2>(" + c.agent.role + ").");
                }
            }
            if (c.p != null) {
                pl.profils().retenirAgent(c.p, c.agent);
                c.p.closeInventory();
            }
        }
        for (int e = 0; e < 2; e++) {
            while (effectif(e) < parEquipe) {
                nouveauBot(e, null);
            }
        }
        annoncer("<#E0B864><b>" + plan.nom + "</b></#E0B864> <#9AA4B2>· " + parEquipe + " contre " + parEquipe + " · premier à " + victoire
                + " manches. " + Txt.equipe(attaque) + " <#9AA4B2>attaque en premier.");
        for (int e = 0; e < 2; e++) {
            StringBuilder sb = new StringBuilder(Txt.equipe(e) + " <#5A6270>:</#5A6270> ");
            for (Combattant c : tous) {
                if (c.equipe == e) {
                    sb.append("<#E6EDF3>").append(c.nom).append("</#E6EDF3> <#5A6270>(</#5A6270>").append(c.agent.nomCouleur()).append("<#5A6270>)</#5A6270>  ");
                }
            }
            annoncer(sb.toString());
        }
        debutManche();
    }

    private void debutManche() {
        manche++;
        phase = Phase.ACHAT;
        reste = pl.getConfig().getInt("partie.achat-secondes", 20) * 20 + (manche == 1 || manche == victoire ? 100 : 0);
        poseCetteManche = false;
        noyauPose = false;
        noyauAuSol = false;
        retirerNoyau();
        pouvoirs.nettoyer();
        carte.debloquer();
        carte.barrieres(true);
        // les renforts prennent la place d'un bot
        for (Player r : new ArrayList<>(renforts)) {
            Combattant bot = null;
            int[] humains = new int[2];
            for (Combattant c : tous) {
                if (c.p != null) {
                    humains[c.equipe]++;
                }
            }
            int e = humains[0] <= humains[1] ? 0 : 1;
            for (int essai = 0; essai < 2 && bot == null; essai++) {
                for (Combattant c : tous) {
                    if (c.bot() && c.equipe == e) {
                        bot = c;
                        break;
                    }
                }
                e = 1 - e;
            }
            if (bot == null) {
                continue;
            }
            renforts.remove(r);
            Combattant c = new Combattant(r, r.getName());
            c.equipe = bot.equipe;
            c.agent = bot.agent;
            c.credits = bot.credits;
            c.primaire = bot.primaire;
            c.ult = bot.ult;
            System.arraycopy(bot.charges, 0, c.charges, 0, 4);
            retirerBot(bot);
            tous.remove(bot);
            tous.add(c);
            parEntite.put(r.getUniqueId(), c);
            annoncer(c.nomCouleur() + " <#9AA4B2>remplace " + bot.nom + " (" + c.agent.nomCouleur() + "<#9AA4B2>).");
        }
        int[] n = new int[2];
        for (Combattant c : tous) {
            if (!c.vivant) {
                c.primaire = null;
                c.bouclier = 0;
                c.secondaire = Arme.ETINCELLE;
            }
            c.vivant = true;
            c.hp = 100;
            c.porteNoyau = false;
            c.pose = 0;
            c.aveugle = c.lent = c.revele = c.gel = c.commotion = c.myope = c.soinFin = 0;
            c.rechargeFin = -1;
            c.recul = 0;
            c.blessePar.clear();
            c.achats.clear();
            c.pleinMunitions();
            for (int i = 0; i < 3; i++) {
                Agent.Pouvoir pv = c.agent.pouvoirs[i];
                c.charges[i] = Math.max(c.charges[i], pv.gratuit());
                c.rechargeT[i] = 0;
            }
            Location l = carte.base(c.equipe == attaque ? 't' : 'd', n[c.equipe]++, rnd);
            if (c.p != null) {
                Player p = c.p;
                p.setGameMode(GameMode.ADVENTURE);
                for (PotionEffect pe : p.getActivePotionEffects()) {
                    p.removePotionEffect(pe.getType());
                }
                Etat.base(p, Attribute.JUMP_STRENGTH, 0.42);
                p.setFireTicks(0);
                pl.teleporter(p, l);
                hotbar(c);
                p.getInventory().setHeldItemSlot(c.primaire != null ? 0 : 1);
                vie(c);
            } else {
                c.x = l.getX();
                c.z = l.getZ();
                c.yaw = l.getYaw();
                c.pitch = 0;
                c.chemin.clear();
                c.cible = null;
                c.but = null;
                c.accroupi = false;
                apparaitreBot(c);
            }
        }
        // le Noyau à un attaquant au hasard
        List<Combattant> att = new ArrayList<>();
        for (Combattant c : tous) {
            if (c.equipe == attaque) {
                att.add(c);
            }
        }
        if (!att.isEmpty()) {
            Combattant porteur = att.get(rnd.nextInt(att.size()));
            porteur.porteNoyau = true;
            if (porteur.p != null) {
                hotbar(porteur);
            }
        }
        bots.debutManche();
        for (Combattant c : tous) {
            if (c.p == null) {
                continue;
            }
            boolean a = c.equipe == attaque;
            Txt.title(c.p, "<#E6EDF3><b>MANCHE " + manche + "</b>",
                    a ? "<#E0484A><b>ATTAQUE</b></#E0484A> <#9AA4B2>· pose le Noyau sur A ou B" : "<#7CFF8A><b>DÉFENSE</b></#7CFF8A> <#9AA4B2>· protège les sites", 2200);
            c.p.playSound(c.p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
            if (c.porteNoyau) {
                Txt.send(c.p, "<#E0B864>Tu portes le Noyau.</#E0B864> Sur un site : prends-le en main (case 8) et reste accroupi 3,5 s.");
            }
            Txt.send(c.p, "Phase d'achat : " + Txt.cr(c.credits) + " · émeraude (case 9) ou <#7CFF8A>/breche boutique</#7CFF8A>.");
        }
    }

    private void debutCombat() {
        phase = Phase.COMBAT;
        reste = pl.getConfig().getInt("partie.manche-secondes", 100) * 20;
        carte.barrieres(false);
        for (Combattant c : tous) {
            if (c.p != null) {
                c.p.closeInventory();
                c.p.getInventory().setItem(8, null);
                c.p.playSound(c.p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
                Txt.title(c.p, "<gradient:#3A7FFF:#E0B864><b>GO !</b></gradient>", "", 700);
            }
        }
        bots.debutCombat();
    }

    void tick() {
        t++;
        if (phase == Phase.FIN) {
            return;
        }
        reste--;
        switch (phase) {
            case ATTENTE -> {
                if (t % 20 == 0) {
                    affichage();
                }
                if (reste <= 0) {
                    demarrer();
                }
                return;
            }
            case ACHAT -> {
                gardeBase();
                if (reste <= 0) {
                    debutCombat();
                }
            }
            case COMBAT -> {
                if (reste <= 0) {
                    finManche(1 - attaque, "Temps écoulé : le Noyau n'a pas été posé.");
                    return;
                }
            }
            case NOYAU -> {
                noyauReste--;
                bips();
                if (noyauReste <= 0) {
                    explosion();
                    return;
                }
            }
            case FIN_MANCHE -> {
                if (reste <= 0) {
                    apresManche();
                }
                return;
            }
            default -> {
            }
        }
        for (Combattant c : new ArrayList<>(tous)) {
            effets(c);
            if (!c.vivant) {
                continue;
            }
            tir.tick(c);
            objectif(c);
        }
        pouvoirs.tick();
        bots.tick();
        ramasserNoyau();
        if (t % 2 == 0) {
            affichage();
        }
    }

    /** Pendant la phase d'achat, personne ne quitte sa base (les vitres le font déjà ; ceci rattrape les sauts). */
    private void gardeBase() {
        if (t % 5 != 0) {
            return;
        }
        for (Combattant c : tous) {
            if (c.p == null || !c.vivant) {
                continue;
            }
            Location l = c.p.getLocation();
            char k = plan.at(plan.col(l.getX()), plan.rang(l.getZ()));
            char base = c.equipe == attaque ? 't' : 'd';
            if (k != base && k != '=') {
                pl.teleporter(c.p, carte.base(base, tous.indexOf(c), rnd));
            }
        }
    }

    // ------------------------------------------------------------------ effets, soins, recharges

    private void effets(Combattant c) {
        if (c.soinFin > t && c.vivant) {
            c.hp = Math.min(100, c.hp + c.soinParTick);
            if (t % 8 == 0) {
                w.spawnParticle(Particle.HAPPY_VILLAGER, c.pieds(w).add(0, 1.2, 0), 3, 0.3, 0.5, 0.3, 0);
            }
            vie(c);
        }
        if (c.revele > t && c.m != null && t % 10 == 0) {
            c.m.setGlowing(true);
        } else if (c.m != null && c.revele <= t && c.m.isGlowing() && c.revele > t - 12) {
            c.m.setGlowing(false);
        }
        if (c.gel == t && c.p != null) {
            Etat.base(c.p, Attribute.JUMP_STRENGTH, 0.42);
        }
        for (int i = 0; i < 3; i++) {
            Agent.Pouvoir pv = c.agent.pouvoirs[i];
            if (pv.recharge() > 0 && c.charges[i] < pv.max() && (phase == Phase.COMBAT || phase == Phase.NOYAU)) {
                if (++c.rechargeT[i] >= pv.recharge() * 20) {
                    c.rechargeT[i] = 0;
                    c.charges[i]++;
                    if (c.p != null) {
                        hotbar(c);
                        Txt.bar(c.p, "<" + c.agent.couleur + ">" + pv.nom() + " rechargé !");
                        c.p.playSound(c.p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ pose et désamorçage

    private void objectif(Combattant c) {
        boolean attaquant = c.equipe == attaque;
        Location l = c.pieds(w);
        if (phase == Phase.COMBAT && attaquant && c.porteNoyau) {
            boolean enMain = c.p == null || c.p.getInventory().getHeldItemSlot() == 7;
            char s = plan.site(l.getX(), l.getZ());
            if (enMain && s != 0 && c.accroupi() && c.gel <= t) {
                if (c.pose == 0) {
                    c.poseX = l.getX();
                    c.poseZ = l.getZ();
                }
                if (c.dist2(c.poseX, c.poseZ) > 0.5) {
                    c.pose = 0;
                    return;
                }
                c.pose++;
                if (c.pose % 10 == 1) {
                    w.playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + c.pose / 80f);
                }
                if (c.p != null) {
                    Txt.bar(c.p, "<#E0B864>Pose du Noyau " + Txt.bar(c.pose / (double) POSE, 14, "#E0B864"));
                }
                if (c.pose >= POSE) {
                    planter(c, s);
                }
            } else {
                c.pose = 0;
            }
        } else if (phase == Phase.NOYAU && !attaquant) {
            double d2 = c.dist2(noyauX, noyauZ);
            if (d2 <= 2.4 * 2.4 && c.accroupi() && c.gel <= t) {
                c.pose++;
                if (c.pose % 10 == 1) {
                    w.playSound(l, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.6f + c.pose / 150f);
                }
                if (c.pose == DESAMORCE / 2) {
                    annoncerEquipe(c.equipe, "<#7CFF8A>" + c.nom + " a désamorcé la moitié du Noyau.");
                }
                if (c.p != null) {
                    Txt.bar(c.p, "<#7CFF8A>Désamorçage " + Txt.bar(c.pose / (double) DESAMORCE, 14, "#7CFF8A") + (c.pose >= DESAMORCE / 2 ? " <#9AA4B2>(moitié acquise)" : ""));
                }
                if (c.pose >= DESAMORCE) {
                    desamorcer(c);
                }
            } else if (c.pose > 0) {
                c.pose = c.pose >= DESAMORCE / 2 ? DESAMORCE / 2 : 0;
            }
        }
    }

    private void planter(Combattant c, char s) {
        c.porteNoyau = false;
        c.pose = 0;
        c.poses++;
        c.ult = Math.min(c.agent.pouvoirs[3].max(), c.ult + 1);
        noyauPose = true;
        poseCetteManche = true;
        noyauSite = s;
        Location l = c.pieds(w);
        noyauX = l.getX();
        noyauZ = l.getZ();
        phase = Phase.NOYAU;
        noyauReste = pl.getConfig().getInt("partie.noyau-secondes", 45) * 20;
        prochainBip = 0;
        if (c.p != null) {
            hotbar(c);
            c.p.getInventory().setHeldItemSlot(c.primaire != null ? 0 : 1);
        }
        noyauVue = w.spawn(new Location(w, noyauX, Plan.Y + 0.45, noyauZ), ItemDisplay.class, e -> {
            pl.mark(e);
            e.setItemStack(pl.objets().modele(Material.PAPER, "noyau"));
            e.setGlowing(true);
            e.setGlowColorOverride(Color.fromRGB(0xE0484A));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
        });
        w.playSound(l, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.8f);
        for (Combattant o : tous) {
            if (o.p != null) {
                boolean att = o.equipe == attaque;
                Txt.title(o.p, "<#E0484A><b>NOYAU POSÉ</b>", "<#E6EDF3>Site <b>" + Character.toUpperCase(s) + "</b> <#9AA4B2>· "
                        + (att ? "défends-le " + noyauReste / 20 + " s" : "désamorce-le (accroupi à côté, 7 s)"), 2200);
                o.p.playSound(o.p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
            }
        }
        annoncer(c.nomCouleur() + " <#9AA4B2>pose le Noyau sur le site</#9AA4B2> <#E6EDF3><b>" + Character.toUpperCase(s) + "</b></#E6EDF3>.");
        bots.noyauPose();
    }

    private void bips() {
        if (--prochainBip > 0) {
            return;
        }
        int s = noyauReste / 20;
        prochainBip = s > 20 ? 20 : s > 10 ? 10 : s > 5 ? 5 : 3;
        Location l = new Location(w, noyauX, Plan.Y + 0.6, noyauZ);
        w.playSound(l, Sound.BLOCK_NOTE_BLOCK_BIT, 1.6f, 1.9f);
        w.spawnParticle(Particle.DUST, l.clone().add(0, 0.6, 0), 6, 0.1, 0.4, 0.1, 0, new Particle.DustOptions(Color.fromRGB(0xE0484A), 1.4f));
        if (noyauVue != null && noyauVue.isValid()) {
            noyauVue.setRotation(noyauVue.getYaw() + 25, 0);
        }
    }

    private void desamorcer(Combattant c) {
        c.desamorcages++;
        c.pose = 0;
        c.ult = Math.min(c.agent.pouvoirs[3].max(), c.ult + 1);
        w.playSound(new Location(w, noyauX, Plan.Y, noyauZ), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.2f);
        annoncer(c.nomCouleur() + " <#7CFF8A>désamorce le Noyau !");
        finManche(c.equipe, c.nom + " a désamorcé le Noyau.");
    }

    private void explosion() {
        Location l = new Location(w, noyauX, Plan.Y + 1, noyauZ);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 4, 2, 1, 2, 0);
        w.spawnParticle(Particle.FLAME, l, 200, 6, 2, 6, 0.1);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
        w.playSound(l, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.7f);
        for (Combattant c : new ArrayList<>(tous)) {
            if (c.vivant && c.dist2(noyauX, noyauZ) <= RAYON_NOYAU * RAYON_NOYAU) {
                c.bouclier = 0;
                blesser(null, c, 999, "Noyau", false);
            }
        }
        retirerNoyau();
        finManche(attaque, "Le Noyau a explosé.");
    }

    void lacherNoyau(double x, double z) {
        int[] cell = plan.proche(plan.col(x), plan.rang(z), null);
        if (cell != null) {
            x = plan.wx(cell[0]);
            z = plan.wz(cell[1]);
        }
        noyauAuSol = true;
        noyauX = x;
        noyauZ = z;
        retirerNoyau();
        double fx = x, fz = z;
        noyauVue = w.spawn(new Location(w, x, Plan.Y + 0.3, z), ItemDisplay.class, e -> {
            pl.mark(e);
            e.setItemStack(pl.objets().modele(Material.PAPER, "noyau"));
            e.setGlowing(true);
            e.setGlowColorOverride(Color.fromRGB(0xE0B864));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        annoncerEquipe(attaque, "<#E0B864>Le Noyau est au sol !</#E0B864> <#9AA4B2>Marche dessus pour le reprendre.");
        bots.noyauLache(fx, fz);
    }

    private void ramasserNoyau() {
        if (!noyauAuSol || phase != Phase.COMBAT) {
            return;
        }
        if (noyauVue != null && noyauVue.isValid() && t % 2 == 0) {
            noyauVue.setRotation(noyauVue.getYaw() + 9, 0);
        }
        for (Combattant c : tous) {
            if (c.vivant && c.equipe == attaque && c.dist2(noyauX, noyauZ) < 1.4 * 1.4) {
                noyauAuSol = false;
                retirerNoyau();
                c.porteNoyau = true;
                if (c.p != null) {
                    hotbar(c);
                    c.p.playSound(c.p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
                }
                annoncerEquipe(attaque, c.nomCouleur() + " <#9AA4B2>reprend le Noyau.");
                return;
            }
        }
    }

    private void retirerNoyau() {
        if (noyauVue != null) {
            noyauVue.remove();
            noyauVue = null;
        }
    }

    // ------------------------------------------------------------------ dégâts et morts

    /** Inflige des dégâts (le bouclier absorbe les deux tiers). src null = l'environnement (Noyau). */
    void blesser(Combattant src, Combattant cible, double dmg, String arme, boolean tete) {
        if (!cible.vivant || dmg <= 0 || phase == Phase.FIN_MANCHE || phase == Phase.FIN || phase == Phase.ATTENTE) {
            return;
        }
        if (src != null && src.equipe == cible.equipe && src != cible) {
            return;
        }
        double abs = Math.min(cible.bouclier, dmg * 0.66);
        cible.bouclier -= abs;
        double vraie = Math.min(cible.hp, dmg - abs);
        cible.hp -= dmg - abs;
        if (src != null && src != cible) {
            src.degats += (int) Math.round(vraie + abs);
            cible.blessePar.merge(src, (int) Math.round(vraie + abs), Integer::sum);
            if (src.p != null) {
                src.p.playSound(src.p.getLocation(), tete ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.ENTITY_ARROW_HIT_PLAYER, tete ? 0.9f : 0.5f, tete ? 1.8f : 1.2f);
            }
            if (cible.bot()) {
                bots.touche(cible, src);
            }
        }
        if (cible.p != null) {
            cible.p.playHurtAnimation(0);
            cible.p.playSound(cible.p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1f);
        } else if (cible.m != null) {
            cible.m.playHurtAnimation(0);
        }
        if (cible.hp <= 0.01) {
            tuer(cible, src, arme, tete);
        } else {
            vie(cible);
        }
    }

    void tuer(Combattant c, Combattant src, String arme, boolean tete) {
        if (!c.vivant) {
            return;
        }
        c.vivant = false;
        c.hp = 0;
        c.morts++;
        c.pose = 0;
        c.soinFin = 0;
        Location l = c.pieds(w);
        c.mortX = l.getX();
        c.mortZ = l.getZ();
        c.ult = Math.min(c.agent.pouvoirs[3].max(), c.ult + 1);
        if (src != null && src != c) {
            src.kills++;
            if (tete) {
                src.tetes++;
            }
            src.credits = Math.min(9000, src.credits + 200);
            src.ult = Math.min(src.agent.pouvoirs[3].max(), src.ult + 1);
            if (src.p != null) {
                src.p.playSound(src.p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f);
                Txt.title(src.p, "", "<#E0484A>✖</#E0484A> <#E6EDF3>" + c.nom + (tete ? " <#E0B864>✦ tête" : ""), 900);
                hotbar(src);
            }
            for (Map.Entry<Combattant, Integer> e : c.blessePar.entrySet()) {
                if (e.getKey() != src && e.getKey().equipe == src.equipe && e.getValue() >= 25) {
                    e.getKey().assists++;
                }
            }
        }
        w.spawnParticle(Particle.DUST, l.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0, new Particle.DustOptions(Color.fromRGB(c.equipe == 0 ? 0x3A7FFF : 0xE0B864), 1.5f));
        w.playSound(l, Sound.ENTITY_PLAYER_DEATH, 0.8f, 1.1f);
        annoncer((src == null ? "<#E0484A>Noyau</#E0484A>" : src == c ? c.nomCouleur() : src.nomCouleur()) + " <#5A6270>[" + arme + (tete ? " <#E0B864>✦</#E0B864>" : "")
                + "]</#5A6270> " + c.nomCouleur());
        if (c.porteNoyau) {
            c.porteNoyau = false;
            if (phase == Phase.COMBAT) {
                lacherNoyau(l.getX(), l.getZ());
            }
        }
        pouvoirs.mort(c);
        if (c.p != null) {
            Player p = c.p;
            for (PotionEffect pe : p.getActivePotionEffects()) {
                p.removePotionEffect(pe.getType());
            }
            p.setGameMode(GameMode.SPECTATOR);
            pl.teleporter(p, l.clone().add(0, 2.5, 0));
            Txt.title(p, "<#E0484A><b>ÉLIMINÉ</b>", src == null || src == c ? "" : "<#E6EDF3>par " + src.nomCouleur() + " <#5A6270>(" + src.agent.nomCouleur() + "<#5A6270>)", 1800);
        } else {
            retirerBot(c);
        }
        verifierFin();
    }

    /** Ramène un allié tombé (Renaissance). */
    void ranimer(Combattant c) {
        if (c.vivant || (phase != Phase.COMBAT && phase != Phase.NOYAU)) {
            return;
        }
        c.vivant = true;
        c.hp = 100;
        c.pleinMunitions();
        Location l = new Location(w, c.mortX, Plan.Y, c.mortZ);
        if (c.p != null) {
            c.p.setGameMode(GameMode.ADVENTURE);
            pl.teleporter(c.p, l);
            hotbar(c);
            vie(c);
            Txt.title(c.p, "<#7CFF8A><b>RANIMÉ</b>", "", 1200);
        } else {
            c.x = c.mortX;
            c.z = c.mortZ;
            c.chemin.clear();
            apparaitreBot(c);
        }
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        w.playSound(l, Sound.ITEM_TOTEM_USE, 0.8f, 1.3f);
        annoncer(c.nomCouleur() + " <#7CFF8A>revient au combat !");
    }

    int vivants(int equipe) {
        int n = 0;
        for (Combattant c : tous) {
            if (c.vivant && c.equipe == equipe) {
                n++;
            }
        }
        return n;
    }

    void verifierFin() {
        if (phase != Phase.COMBAT && phase != Phase.NOYAU) {
            return;
        }
        int def = 1 - attaque;
        if (vivants(def) == 0) {
            finManche(attaque, "Défenseurs éliminés.");
        } else if (phase == Phase.COMBAT && vivants(attaque) == 0) {
            finManche(def, "Attaquants éliminés.");
        }
    }

    private void finManche(int gagnant, String raison) {
        if (phase == Phase.FIN_MANCHE || phase == Phase.FIN) {
            return;
        }
        phase = Phase.FIN_MANCHE;
        reste = 100;
        raisonFin = raison;
        score[gagnant]++;
        int perdant = 1 - gagnant;
        serieDefaites[gagnant] = 0;
        serieDefaites[perdant]++;
        int gainPerte = serieDefaites[perdant] >= 3 ? 2900 : serieDefaites[perdant] == 2 ? 2400 : 1900;
        for (Combattant c : tous) {
            int gain = c.equipe == gagnant ? 3000 : gainPerte;
            if (c.equipe == attaque && poseCetteManche) {
                gain += 300;
            }
            c.credits = Math.min(9000, c.credits + gain);
            c.pose = 0;
            if (c.p != null) {
                boolean g = c.equipe == gagnant;
                Txt.title(c.p, g ? "<#7CFF8A><b>MANCHE GAGNÉE</b>" : "<#E0484A><b>MANCHE PERDUE</b>",
                        "<#9AA4B2>" + raison + " <#5A6270>·</#5A6270> <#3A7FFF>" + score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + score[1], 2600);
                c.p.playSound(c.p.getLocation(), g ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
            }
        }
        annoncer("Manche " + manche + " pour " + Txt.equipe(gagnant) + " <#9AA4B2>(" + raison + ")</#9AA4B2> <#5A6270>·</#5A6270> <#3A7FFF>"
                + score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + score[1]);
        bots.finManche();
    }

    private void apresManche() {
        int g = score[0] >= victoire ? 0 : score[1] >= victoire ? 1 : -1;
        if (g >= 0) {
            finPartie(g);
            return;
        }
        if (manche == victoire - 1) {
            attaque = 1 - attaque;
            for (Combattant c : tous) {
                c.credits = 800;
                c.primaire = null;
                c.secondaire = Arme.ETINCELLE;
                c.bouclier = 0;
                c.vivant = true;
                for (int i = 0; i < 3; i++) {
                    c.charges[i] = 0;
                }
            }
            serieDefaites[0] = serieDefaites[1] = 0;
            annoncer("<#E0B864><b>MI-TEMPS</b></#E0B864> <#9AA4B2>· changement de côté : " + Txt.equipe(attaque) + " <#9AA4B2>attaque maintenant. Crédits remis à 800 ¤.");
        }
        debutManche();
    }

    private void finPartie(int gagnant) {
        phase = Phase.FIN;
        carte.barrieres(false);
        pouvoirs.nettoyer();
        retirerNoyau();
        for (Combattant c : tous) {
            c.score = c.kills * 150 + c.degats + c.assists * 50 + c.poses * 50 + c.desamorcages * 100;
        }
        List<Combattant> classement = new ArrayList<>(tous);
        classement.sort(Comparator.comparingInt((Combattant c) -> -c.score));
        Combattant mvp = classement.isEmpty() ? null : classement.get(0);
        annoncer("<#5A6270>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        annoncer("<b>" + Txt.equipe(gagnant) + " <#E6EDF3>REMPORTE LA PARTIE</#E6EDF3></b> <#3A7FFF>" + score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + score[1]
                + "</#E0B864> <#9AA4B2>sur " + plan.nom);
        for (int e = 0; e < 2; e++) {
            for (Combattant c : classement) {
                if (c.equipe != e) {
                    continue;
                }
                annoncer("  " + c.nomCouleur() + " <#5A6270>(" + c.agent.nomCouleur() + "<#5A6270>)</#5A6270> <#E6EDF3>" + c.kills + "/" + c.morts + "/" + c.assists
                        + "</#E6EDF3> <#5A6270>·</#5A6270> <#E0B864>" + Txt.n(c.score) + " pts</#E0B864>" + (c == mvp ? " <#E0B864><b>MVP</b>" : ""));
            }
        }
        annoncer("<#5A6270>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        for (Combattant c : tous) {
            if (c.p == null) {
                continue;
            }
            boolean g = c.equipe == gagnant;
            Txt.title(c.p, g ? "<gradient:#7CFF8A:#E0B864><b>VICTOIRE</b></gradient>" : "<#E0484A><b>DÉFAITE</b>",
                    "<#3A7FFF>" + score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + score[1] + "</#E0B864> <#5A6270>·</#5A6270> <#E6EDF3>" + Txt.n(c.score) + " pts", 3500);
            c.p.playSound(c.p.getLocation(), g ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.6f, g ? 1f : 1.6f);
            pl.profils().finPartie(c.p, c, g, rondesJouees());
        }
        pl.getServer().getScheduler().runTaskLater(pl, () -> arreter(null), 120L);
    }

    int rondesJouees() {
        return score[0] + score[1];
    }

    /** Fin (normale ou forcée) : bots retirés, joueurs rendus et ramenés dans la salle d'arcade. */
    void arreter(String raison) {
        if (arretee) {
            return;
        }
        arretee = true;
        if (essai) {
            pl.getLogger().info("[essai " + plan.id + "] fin (" + (raison == null ? "partie terminée" : raison) + ") : " + etat());
            carte.garderCharge(false);
        }
        phase = Phase.FIN;
        pouvoirs.nettoyer();
        retirerNoyau();
        carte.barrieres(false);
        carte.debloquer();
        for (Combattant c : new ArrayList<>(tous)) {
            if (c.p != null) {
                Player p = c.p;
                p.hideBossBar(barre);
                pl.rendre(p, etats.remove(p.getUniqueId()));
                pl.versSalle(p);
                if (raison != null) {
                    Txt.send(p, "<#9AA4B2>Partie arrêtée : " + raison + ".");
                } else {
                    p.sendMessage(Txt.mm(Txt.PREFIX).append(Txt.button("Rejouer", "#7CFF8A", "/breche rejouer", "Relancer une partie sur " + plan.nom))
                            .append(Txt.mm(" ")).append(Txt.button("Classement", "#E0B864", "/breche top", "Meilleurs scores de combat")));
                }
            } else {
                retirerBot(c);
            }
        }
        for (Player r : new ArrayList<>(renforts)) {
            r.hideBossBar(barre);
            pl.rendre(r, etats.remove(r.getUniqueId()));
            pl.versSalle(r);
        }
        renforts.clear();
        tous.clear();
        parEntite.clear();
        pl.jeux().fini(this);
    }

    // ------------------------------------------------------------------ bots (corps)

    void apparaitreBot(Combattant b) {
        retirerBot(b);
        b.m = w.spawn(new Location(w, b.x, Plan.Y, b.z, b.yaw, 0f), Mannequin.class, m -> {
            pl.mark(m);
            m.setAI(false);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setImmovable(true);
            m.customName(Txt.mm("<" + Txt.COUL[b.equipe] + ">" + b.nom + " <#5A6270>·</#5A6270> " + b.agent.nomCouleur()));
            m.setCustomNameVisible(true);
            try {
                m.setDescription(null);
            } catch (RuntimeException ignored) {
                m.setDescription(net.kyori.adventure.text.Component.empty());
            }
        });
        parEntite.put(b.m.getUniqueId(), b);
        pl.objets().habillerBot(b);
    }

    void retirerBot(Combattant b) {
        if (b.m != null) {
            parEntite.remove(b.m.getUniqueId());
            b.m.remove();
            b.m = null;
        }
    }

    // ------------------------------------------------------------------ affichage

    void vie(Combattant c) {
        if (c.p == null) {
            return;
        }
        Player p = c.p;
        double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(0.5, Math.min(max, c.hp / 100.0 * max)));
        Etat.base(p, Attribute.MAX_ABSORPTION, 20);
        p.setAbsorptionAmount(Math.max(0, Math.min(20, c.bouclier / 5.0)));
    }

    void hotbar(Combattant c) {
        if (c.p == null) {
            return;
        }
        PlayerInventory inv = c.p.getInventory();
        Objets o = pl.objets();
        inv.setItem(0, c.primaire == null ? null : o.arme(c.primaire));
        inv.setItem(1, c.secondaire == null ? null : o.arme(c.secondaire));
        inv.setItem(2, o.lame());
        for (int i = 0; i < 4; i++) {
            inv.setItem(3 + i, o.pouvoir(c, i));
        }
        inv.setItem(7, c.porteNoyau ? o.noyau() : null);
        inv.setItem(8, phase == Phase.ACHAT ? o.boutique() : null);
        for (int i = 9; i < 41; i++) {
            if (i < 36 || i == 40) {
                inv.setItem(i, null);
            }
        }
        pl.objets().habiller(c);
    }

    private void hotbarAttente(Combattant c) {
        PlayerInventory inv = c.p.getInventory();
        inv.clear();
        inv.setItem(4, pl.objets().choixAgent());
        inv.setItem(8, pl.objets().quitter());
        inv.setHeldItemSlot(4);
        pl.objets().habiller(c);
    }

    private String pastilles(int e) {
        StringBuilder sb = new StringBuilder();
        for (Combattant c : tous) {
            if (c.equipe == e) {
                sb.append(c.vivant ? "●" : "<#3A4250>●</#3A4250>");
            }
        }
        return sb.toString();
    }

    private void affichage() {
        String temps;
        float prog;
        String etat;
        switch (phase) {
            case ATTENTE -> {
                temps = Txt.chrono(reste);
                prog = Math.max(0, Math.min(1, reste / (float) (pl.getConfig().getInt("partie.attente-secondes", 30) * 20)));
                etat = "<#9AA4B2>ATTENTE · " + humains().size() + " joueur(s)";
            }
            case ACHAT -> {
                temps = Txt.chrono(reste);
                prog = Math.max(0, Math.min(1, reste / 600f));
                etat = "<#7CFF8A>ACHAT";
            }
            case NOYAU -> {
                temps = "<#E0484A>" + Txt.chrono(noyauReste) + "</#E0484A>";
                prog = Math.max(0, Math.min(1, noyauReste / (float) (pl.getConfig().getInt("partie.noyau-secondes", 45) * 20)));
                etat = "<#E0484A>NOYAU " + Character.toUpperCase(noyauSite);
            }
            default -> {
                temps = Txt.chrono(Math.max(0, reste));
                prog = Math.max(0, Math.min(1, reste / (float) (pl.getConfig().getInt("partie.manche-secondes", 100) * 20)));
                etat = phase == Phase.FIN_MANCHE ? "<#E0B864>FIN DE MANCHE" : "<#E6EDF3>MANCHE " + manche;
            }
        }
        barre.name(Txt.mm("<#3A7FFF>" + pastilles(0) + " <b>BLEU " + score[0] + "</b></#3A7FFF>   <#E6EDF3>" + temps + "</#E6EDF3>  " + etat
                + "   <#E0B864><b>" + score[1] + " OR</b> " + pastilles(1)));
        barre.progress(prog);
        barre.color(phase == Phase.NOYAU ? BossBar.Color.RED : phase == Phase.ACHAT ? BossBar.Color.GREEN : BossBar.Color.BLUE);
        if (phase == Phase.ATTENTE) {
            return;
        }
        for (Combattant c : tous) {
            if (c.p == null || !c.vivant || c.pose > 0 || c.p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Arme a = c.arme();
            String arme;
            if (a != null) {
                int k = a.place;
                arme = "<#E6EDF3>" + a.nom + " <b>" + c.chargeur[k] + "</b><#5A6270>/" + c.reserve[k] + "</#5A6270>" + (c.rechargeFin >= 0 ? " <#E0B864>⟳" : "");
            } else {
                int slot = c.p.getInventory().getHeldItemSlot();
                arme = slot == 2 ? "<#E6EDF3>Lame" : slot >= 3 && slot <= 6 ? "<" + c.agent.couleur + ">" + c.agent.pouvoirs[slot - 3].nom() + " <#9AA4B2>(clic droit)"
                        : slot == 7 && c.porteNoyau ? "<#E0B864>Noyau <#9AA4B2>(accroupi sur A/B)" : "";
            }
            StringBuilder pv = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                pv.append("<#9AA4B2>").append(c.agent.pouvoirs[i].touche()).append("</#9AA4B2><").append(c.charges[i] > 0 ? c.agent.couleur : "#3A4250").append(">")
                        .append("●".repeat(Math.max(1, c.charges[i]))).append("</").append(c.charges[i] > 0 ? c.agent.couleur : "#3A4250").append("> ");
            }
            int ultMax = c.agent.pouvoirs[3].max();
            pv.append("<#9AA4B2>X</#9AA4B2> ").append(c.ult >= ultMax ? "<#E0B864><b>PRÊT</b></#E0B864>" : "<#5A6270>" + c.ult + "/" + ultMax + "</#5A6270>");
            Txt.bar(c.p, "<#E0484A>❤ " + (int) Math.ceil(c.hp) + "</#E0484A>" + (c.bouclier > 0 ? " <#62CFF4>⛨ " + (int) Math.ceil(c.bouclier) + "</#62CFF4>" : "")
                    + " <#5A6270>│</#5A6270> " + arme + " <#5A6270>│</#5A6270> " + Txt.cr(c.credits) + " <#5A6270>│</#5A6270> " + pv);
        }
    }

    void annoncer(String msg) {
        if (essai) {
            pl.getLogger().info("[essai " + plan.id + "] " + Txt.MM.stripTags(msg));
        }
        for (Combattant c : tous) {
            if (c.p != null) {
                Txt.send(c.p, msg);
            }
        }
        for (Player r : renforts) {
            Txt.send(r, msg);
        }
    }

    void annoncerEquipe(int e, String msg) {
        for (Combattant c : tous) {
            if (c.p != null && c.equipe == e) {
                Txt.send(c.p, msg);
            }
        }
    }

    String etat() {
        return plan.nom + " · " + phase + " · manche " + manche + " · Bleu " + score[0] + " — " + score[1] + " Or · " + humains().size() + " joueur(s), "
                + (tous.size() - humains().size()) + " bot(s)";
    }

    Map<String, String> resume() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("carte", plan.nom);
        m.put("phase", phase.name());
        return m;
    }
}
