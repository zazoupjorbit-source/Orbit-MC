package fr.orbit.bowl;

import net.kyori.adventure.text.Component;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une partie sur un poste de la salle. La classe de base s'occupe de tout ce qui est commun : file d'attente
 * (le créateur lance quand assez de joueurs ont rejoint), état des joueurs (rendu à la fin quoi qu'il arrive),
 * mises des défis (rendues si la partie s'arrête avant la fin), entités posées (retirées à la fin).
 */
public abstract class Activite {

    public enum Phase { ATTENTE, JEU, FIN }

    protected final OrbitBowl pl;
    protected final World w;
    public final Poste poste;
    public final Mode mode;
    public final long mise;
    protected final List<Player> joueurs = new ArrayList<>();
    private final Map<UUID, Etat> etats = new LinkedHashMap<>();
    private final Map<UUID, Long> misesPayees = new LinkedHashMap<>();
    private final List<Entity> entites = new ArrayList<>();
    protected Phase phase = Phase.ATTENTE;
    protected UUID hote;
    protected int t, attente;

    protected Activite(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        this.pl = pl;
        this.w = pl.monde();
        this.poste = poste;
        this.mode = mode;
        this.mise = mode.mise() ? mise : 0;
    }

    // ------------------------------------------------------------------ cycle de vie

    public final boolean finie() {
        return phase == Phase.FIN;
    }

    public Phase phase() {
        return phase;
    }

    public List<Player> joueurs() {
        return joueurs;
    }

    public boolean contient(Player p) {
        return etats.containsKey(p.getUniqueId());
    }

    /** Places encore libres pour un humain. */
    public boolean ouverte() {
        return phase == Phase.ATTENTE && joueurs.size() < mode.max();
    }

    /** Le créateur ouvre la partie (sa mise est déjà payée si c'est un défi). */
    void ouvrir(Player h) {
        hote = h.getUniqueId();
        ajouter(h);
        if (mode.max() == 1 || joueurs.size() >= mode.max()) {
            demarrer();
        } else {
            attente = attenteTicks();
            annoncerOuverture(h);
        }
    }

    private void annoncerOuverture(Player h) {
        String quoi = poste.jeu.grad("<b>" + poste.jeu.nom + "</b>") + " <#5A6270>·</#5A6270> " + mode.nom()
                + (mise > 0 ? " <#5A6270>·</#5A6270> mise " + Txt.oc(mise) : "");
        for (Player o : w.getPlayers()) {
            if (o.equals(h) || pl.activite(o) != null) {
                continue;
            }
            o.sendMessage(Txt.mm(Txt.PREFIX + "<#E6EDF3>" + h.getName() + " ouvre une partie : " + quoi + " (" + poste.nom + ") ")
                    .append(Txt.button("Rejoindre", poste.jeu.c1, "/bowl rejoindre " + poste.id, "Rejoindre la partie de " + h.getName()
                            + (mise > 0 ? "\n<#E0B864>Mise : " + Txt.n(mise) + " OC (le gagnant prend tout)" : ""))));
        }
        Txt.send(h, "Partie ouverte sur " + poste.nom + " : les joueurs de la salle sont prévenus.");
        boutonsHote(h);
    }

    void boutonsHote(Player h) {
        h.sendMessage(Txt.mm("  ").append(Txt.button("Lancer", "#7CFF8A", "/bowl lancer", "Lancer avec les joueurs présents ("
                        + mode.min() + " minimum)")).append(Txt.mm(" "))
                .append(Txt.button("Annuler", "#E0484A", "/bowl quitter", "Fermer la partie")));
    }

    /** Un joueur rejoint une partie ouverte (sa mise est déjà payée si c'est un défi). */
    void rejoindre(Player p) {
        ajouter(p);
        annoncer("<#7CFF8A>" + p.getName() + " rejoint la partie (" + joueurs.size() + "/" + mode.max() + ").");
        if (joueurs.size() >= mode.max()) {
            demarrer();
        }
    }

    private void ajouter(Player p) {
        etats.put(p.getUniqueId(), new Etat(p));
        if (mise > 0) {
            misesPayees.put(p.getUniqueId(), mise);
        }
        Etat.preparer(p);
        pl.entre(p, this);
        joueurs.add(p);
        equiper(p);
        p.getInventory().setItem(8, OrbitBowl.objetQuitter());
        accueillir(p);
    }

    /** Le créateur lance (ou la partie est pleine). */
    boolean demarrer() {
        if (phase != Phase.ATTENTE) {
            return false;
        }
        if (joueurs.size() < mode.min()) {
            return false;
        }
        phase = Phase.JEU;
        t = 0;
        commencer();
        return true;
    }

    final void tickInterne() {
        if (phase == Phase.ATTENTE) {
            if (--attente <= 0) {
                if (lancementAuto() && demarrer()) {
                    return;
                }
                annoncer("<#E0484A>Personne n'a rejoint à temps : partie annulée.");
                arreter();
                return;
            }
            if (attente % 400 == 0) {
                Player h = hoteJoueur();
                if (h != null) {
                    Txt.bar(h, "<#9AA4B2>En attente de joueurs… " + joueurs.size() + "/" + mode.max() + " · " + Txt.chrono(attente));
                }
            }
            return;
        }
        if (phase == Phase.JEU) {
            t++;
            tick();
        }
    }

    protected Player hoteJoueur() {
        for (Player p : joueurs) {
            if (p.getUniqueId().equals(hote)) {
                return p;
            }
        }
        return null;
    }

    /** Un joueur s'en va (bouton Quitter, déconnexion, téléportation ailleurs). */
    public final void abandon(Player p, boolean renvoyer) {
        if (!contient(p)) {
            return;
        }
        if (phase == Phase.ATTENTE) {
            boolean etaitHote = p.getUniqueId().equals(hote);
            sortir(p, renvoyer);
            Long m = misesPayees.remove(p.getUniqueId());
            if (m != null) {
                Caisse.crediter(p.getUniqueId(), m, poste.jeu.id, "mise rendue (partie quittée)", false);
            }
            if (etaitHote || joueurs.isEmpty()) {
                annoncer("<#E0484A>Le créateur a fermé la partie.");
                arreter();
            } else {
                annoncer("<#9AA4B2>" + p.getName() + " a quitté la partie.");
            }
            return;
        }
        depart(p);
        sortir(p, renvoyer);
        if (phase != Phase.FIN && joueurs.isEmpty()) {
            arreter();
        }
    }

    /**
     * Le jeu réagit au départ d'un joueur pendant la partie (avant qu'il ne soit retiré). Par défaut : en défi misé,
     * le dernier joueur restant gagne le pot.
     */
    protected void depart(Player p) {
        if (mise > 0 && joueurs.size() == 2) {
            Player autre = joueurs.get(0).equals(p) ? joueurs.get(1) : joueurs.get(0);
            annoncer("<#E0B864>" + p.getName() + " abandonne : " + autre.getName() + " remporte le défi.");
            regler(autre.getUniqueId(), "abandon de " + p.getName());
            terminerSansBilan();
        }
    }

    /** Retire un joueur de la partie et lui rend son état. */
    protected void sortir(Player p, boolean renvoyer) {
        Etat e = etats.remove(p.getUniqueId());
        joueurs.remove(p);
        pl.sort(p);
        if (e != null && p.isOnline()) {
            if (p.isInsideVehicle()) {
                p.leaveVehicle();
            }
            e.rendre(p);
            if (renvoyer) {
                pl.teleporter(p, retour());
            }
        }
        quitte(p);
    }

    /** Fin de partie normale : les joueurs restent là où ils sont (devant le poste), état rendu. */
    protected final void terminer() {
        if (phase == Phase.FIN) {
            return;
        }
        phase = Phase.FIN;
        for (Map.Entry<UUID, Long> e : misesPayees.entrySet()) {
            Caisse.crediter(e.getKey(), e.getValue(), poste.jeu.id, "mise rendue", false);
        }
        misesPayees.clear();
        for (Player p : new ArrayList<>(joueurs)) {
            sortir(p, false);
            if (p.isOnline() && p.getWorld().equals(w) && loin(p)) {
                pl.teleporter(p, retour());
            }
        }
        nettoyer();
    }

    private void terminerSansBilan() {
        terminer();
    }

    /** Arrêt brutal (serveur qui s'arrête, partie annulée) : mises rendues, joueurs renvoyés devant le poste. */
    public final void arreter() {
        if (phase == Phase.FIN) {
            return;
        }
        for (Map.Entry<UUID, Long> e : misesPayees.entrySet()) {
            Caisse.crediter(e.getKey(), e.getValue(), poste.jeu.id, "mise rendue (partie arrêtée)", false);
        }
        misesPayees.clear();
        phase = Phase.FIN;
        for (Player p : new ArrayList<>(joueurs)) {
            sortir(p, true);
        }
        nettoyer();
    }

    private void nettoyer() {
        try {
            fin();
        } catch (RuntimeException ex) {
            pl.getLogger().warning(poste.id + " : " + ex);
        }
        for (Entity e : entites) {
            e.remove();
        }
        entites.clear();
        pl.libere(this);
    }

    /** Paie le pot au gagnant (null = égalité : chacun récupère sa mise). */
    protected void regler(UUID gagnant, String pourquoi) {
        if (misesPayees.isEmpty()) {
            return;
        }
        long pot = 0;
        for (long m : misesPayees.values()) {
            pot += m;
        }
        if (gagnant == null) {
            for (Map.Entry<UUID, Long> e : misesPayees.entrySet()) {
                Caisse.crediter(e.getKey(), e.getValue(), poste.jeu.id, "égalité : mise rendue", false);
            }
        } else {
            Caisse.crediter(gagnant, pot, poste.jeu.id, pourquoi, true);
        }
        misesPayees.clear();
    }

    protected boolean loin(Player p) {
        return p.getLocation().distanceSquared(retour()) > 30 * 30;
    }

    /** Durée de la file d'attente (ticks). */
    protected int attenteTicks() {
        return pl.getConfig().getInt("attente.secondes", 90) * 20;
    }

    /** À la fin de l'attente : lancer avec les présents (et des bots) au lieu d'annuler. */
    protected boolean lancementAuto() {
        return false;
    }

    // ------------------------------------------------------------------ à écrire par chaque jeu

    /** Objets de la barre (l'emplacement 8 reçoit « Quitter »). */
    protected abstract void equiper(Player p);

    /** Message et placement à l'arrivée dans la file / la partie. */
    protected void accueillir(Player p) {
    }

    protected abstract void commencer();

    protected abstract void tick();

    /** Après la fin : retirer ce que le jeu a posé en dehors de {@link #garder}. */
    protected void fin() {
    }

    /** Le joueur vient d'être retiré (son état est rendu). */
    protected void quitte(Player p) {
    }

    /** Où l'on renvoie les joueurs : devant la console du poste, tourné vers elle. */
    public Location retour() {
        return Salle.devant(poste.console(w));
    }

    public void clic(Player p, boolean gauche) {
    }

    public void accroupi(Player p, boolean bas) {
    }

    public void touche(Player p, Input in) {
    }

    /** Joueur assis sur un siège de la partie (descente interdite). */
    public boolean assis(Player p) {
        return false;
    }

    /** Ligne d'état pour la console du poste et /bowl admin parties. */
    public String etat() {
        return joueurs.size() + " joueur(s)";
    }

    // ------------------------------------------------------------------ outils

    protected final void annoncer(String msg) {
        for (Player p : joueurs) {
            Txt.send(p, msg);
        }
    }

    protected final void titre(String haut, String bas, int ms) {
        for (Player p : joueurs) {
            Txt.title(p, haut, bas, ms);
        }
    }

    protected final void son(Location at, Sound s, float vol, float pitch) {
        w.playSound(at, s, vol, pitch);
    }

    protected final <T extends Entity> T garder(T e) {
        entites.add(e);
        return e;
    }

    protected final void lacher(Entity e) {
        if (e != null) {
            entites.remove(e);
            e.remove();
        }
    }

    public static String ip(Player p) {
        InetSocketAddress a = p.getAddress();
        return a == null || a.getAddress() == null ? "" : a.getAddress().getHostAddress();
    }

    /** Objet de la barre avec un modèle du pack (orbitbowl:modele), visible sans pack sous l'apparence de {@code base}. */
    public static ItemStack objet(Material base, String modele, String nom, String... lore) {
        ItemStack it = new ItemStack(base);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(nom));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Txt.mm("<#9AA4B2>" + s));
        }
        meta.lore(l);
        if (modele != null) {
            meta.setItemModel(new NamespacedKey("orbitbowl", modele));
        }
        it.setItemMeta(meta);
        return it;
    }

    /** ItemDisplay d'un modèle du pack (sprite face au joueur si {@code billboard}). */
    protected final ItemDisplay sprite(Location at, Material base, String modele, float taille, boolean billboard) {
        ItemStack it = objet(base, modele, "");
        return garder(w.spawn(at, ItemDisplay.class, d -> {
            OrbitBowl.marquer(d);
            d.setItemStack(it);
            d.setBillboard(billboard ? Display.Billboard.CENTER : Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(taille, taille, taille), new AxisAngle4f()));
        }));
    }

    protected final TextDisplay texte(Location at, String mm, float taille, boolean billboard) {
        return garder(Salle.texte(at, mm, taille, billboard));
    }

    /** Rotation autour d'un axe horizontal (bascule d'une quille, d'une cible…). */
    protected static Quaternionf bascule(double dirX, double dirZ, double angle) {
        Vector3f axe = new Vector3f((float) dirZ, 0f, (float) -dirX);
        if (axe.lengthSquared() < 1e-6) {
            axe.set(1, 0, 0);
        }
        axe.normalize();
        return new Quaternionf().fromAxisAngleRad(axe, (float) angle);
    }

    protected static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    /** Barre de charge pour l'action bar : 20 traits colorés. */
    protected static String jauge(double f, String couleur) {
        int n = (int) Math.round(clamp(f, 0, 1) * 20);
        return "<" + couleur + ">" + "|".repeat(n) + "</" + couleur + "><#3A3F4E>" + "|".repeat(20 - n) + "</#3A3F4E>";
    }

    /** Jauge qui monte et redescend (0 → 1 → 0) en {@code periode} ticks. */
    protected static double vaEtVient(int ticks, int periode) {
        double x = (ticks % periode) / (double) periode;
        return x < 0.5 ? x * 2 : 2 - x * 2;
    }
}
