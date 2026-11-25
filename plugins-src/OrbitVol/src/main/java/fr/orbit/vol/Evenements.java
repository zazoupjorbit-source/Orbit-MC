package fr.orbit.vol;

import fr.orbit.vol.Catalogue.Mutation;
import fr.orbit.vol.Catalogue.Orbito;
import fr.orbit.vol.Catalogue.Rarete;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Événements tournants (un toutes les 10 minutes, en plus des météores) :
 * nuit dorée (revenus ×2), marchand noir (un Orbito rare à vendre, premier arrivé),
 * Orbito géant (gratuit, à ramener chez soi). Et l'annonce animée des Orbitos rares du tapis.
 */
final class Evenements implements Listener {

    enum Type { NUIT_DOREE, MARCHAND_NOIR, GEANT }

    static final long TOUTES_S = 10 * 60;

    final Jeu jeu;
    Type actif;
    long fin;
    long prochain;
    final BossBar barre = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    WanderingTrader marchand;
    Orbito offre;
    Mutation offreMutation;
    double offrePrix;
    Jeu.Spec geant;
    long duree;

    Evenements(Jeu jeu) {
        this.jeu = jeu;
        this.prochain = System.currentTimeMillis() + 5 * 60_000L;
    }

    double multRevenu() {
        return actif == Type.NUIT_DOREE ? 2 : 1;
    }

    void tic() {
        long now = System.currentTimeMillis();
        if (actif == null) {
            if (now >= prochain && jeu.meteoresFin == 0 && !jeu.profils.isEmpty()) lancer(Type.values()[ThreadLocalRandom.current().nextInt(Type.values().length)]);
            return;
        }
        if (now >= fin || (actif == Type.MARCHAND_NOIR && offre == null) || (actif == Type.GEANT && geant == null)) { arreter(); return; }
        barre.progress(Math.max(0f, Math.min(1f, (fin - now) / (float) duree)));
        if (actif == Type.NUIT_DOREE)
            for (Player p : jeu.w.getPlayers()) p.spawnParticle(Particle.DUST, p.getLocation().add(0, 2.4, 0), 3, 1.5, 0.6, 1.5, 0,
                    new Particle.DustOptions(Color.fromRGB(0xFFE08A), 1.4f));
        if (actif == Type.GEANT && geant != null && geant.etat == Jeu.Etat.LIBRE)
            jeu.w.spawnParticle(Particle.END_ROD, geant.mob.getLocation().add(0, 3, 0), 12, 0.3, 4, 0.3, 0.02);
        if (actif == Type.MARCHAND_NOIR && marchand != null && marchand.isValid())
            jeu.w.spawnParticle(Particle.WITCH, marchand.getLocation().add(0, 2.3, 0), 6, 0.3, 0.3, 0.3, 0);
    }

    void lancer(Type t) {
        if (actif != null) arreter();
        actif = t;
        long now = System.currentTimeMillis();
        switch (t) {
            case NUIT_DOREE -> {
                duree = 180_000;
                barre.name(Txt.mm("<#FFE08A><b>NUIT DORÉE</b> <#E6EDF3>— tous les revenus <#7CFC9A>×2</#7CFC9A> !"));
                barre.color(BossBar.Color.YELLOW);
                annoncer("<#FFE08A><b>NUIT DORÉE</b>", "Tous les revenus ×2 pendant 3 minutes !", Sound.BLOCK_BEACON_ACTIVATE);
            }
            case MARCHAND_NOIR -> {
                duree = 120_000;
                double x = ThreadLocalRandom.current().nextDouble();
                Rarete r = x < 0.05 ? Rarete.SECRET : x < 0.30 ? Rarete.COSMIQUE : Rarete.MYTHIQUE;
                offre = Oeufs.auHasard(r);
                offreMutation = ThreadLocalRandom.current().nextDouble() < 0.25 ? Mutation.OR : Mutation.AUCUNE;
                offrePrix = offre.prix() * (double) offreMutation.mult * 1.25;
                Location l = new Location(jeu.w, 0.5, Arene.Y + 1, 7.5, 180, 0);
                marchand = jeu.w.spawn(l, WanderingTrader.class, m -> {
                    m.setAI(false);
                    m.setInvulnerable(true);
                    m.setPersistent(false);
                    m.setSilent(true);
                    m.setDespawnDelay(0);
                    m.customName(Txt.mm("<#A64DFF><b>MARCHAND NOIR</b>"));
                    m.setCustomNameVisible(true);
                    m.setGlowing(true);
                });
                barre.name(Txt.mm("<#A64DFF><b>MARCHAND NOIR</b> <#E6EDF3>— " + offre.nomComplet(offreMutation) + " <#E6EDF3>à vendre, premier arrivé !"));
                barre.color(BossBar.Color.PURPLE);
                annoncer("<#A64DFF><b>MARCHAND NOIR</b>", offre.nomComplet(offreMutation) + " <#E6EDF3>à vendre au centre : " + Txt.cr(offrePrix), Sound.ENTITY_WANDERING_TRADER_YES);
            }
            case GEANT -> {
                duree = 180_000;
                double x = ThreadLocalRandom.current().nextDouble();
                Rarete r = x < 0.15 ? Rarete.COSMIQUE : Rarete.MYTHIQUE;
                Orbito o = Oeufs.auHasard(r);
                geant = jeu.creer(o, Mutation.AUCUNE, new Location(jeu.w, 0.5, Arene.Y + 1, 6.5, 180, 0), Jeu.Etat.LIBRE);
                geant.geant = true;
                AttributeInstance a = geant.mob.getAttribute(Attribute.SCALE);
                if (a != null) a.setBaseValue(o.taille() * 2.2);
                geant.mob.setGlowing(true);
                geant.mob.customName(Txt.mm("<#FFE08A><b>GÉANT</b> " + o.nomComplet(Mutation.AUCUNE) + " <#E6EDF3>— gratuit, ramène-le chez toi !"));
                barre.name(Txt.mm("<#FFE08A><b>ORBITO GÉANT</b> <#E6EDF3>— " + o.nomComplet(Mutation.AUCUNE) + " <#E6EDF3>gratuit au centre !"));
                barre.color(BossBar.Color.GREEN);
                annoncer("<#FFE08A><b>ORBITO GÉANT !</b>", o.nomComplet(Mutation.AUCUNE) + " <#E6EDF3>gratuit au centre : le premier qui le ramène le garde !", Sound.ENTITY_ENDER_DRAGON_GROWL);
            }
        }
        fin = now + duree;
        for (Player p : jeu.w.getPlayers()) p.showBossBar(barre);
    }

    private void annoncer(String titre, String sous, Sound s) {
        for (Player p : jeu.w.getPlayers()) {
            p.showTitle(Title.title(Txt.mm(titre), Txt.mm("<#E6EDF3>" + sous), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
            Txt.sound(p, s, 1f);
        }
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + titre + " <#5A6270>— <#E6EDF3>" + sous));
    }

    void arreter() {
        if (actif == Type.NUIT_DOREE) Bukkit.broadcast(Txt.mm(Txt.PREFIX + "La nuit dorée est finie."));
        if (marchand != null) marchand.remove();
        marchand = null;
        if (offre != null && actif == Type.MARCHAND_NOIR) Bukkit.broadcast(Txt.mm(Txt.PREFIX + "Le marchand noir est reparti sans vendre."));
        offre = null;
        if (geant != null) {
            if (geant.etat == Jeu.Etat.PORTE) jeu.rendre(geant, true);
            if (geant.geant) jeu.supprimer(geant);
            geant = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barre);
        actif = null;
        prochain = System.currentTimeMillis() + TOUTES_S * 1000;
    }

    /** Le géant a été ramené dans une base : il devient un Orbito normal. */
    void geantPris(Player p, Jeu.Spec s) {
        s.geant = false;
        AttributeInstance a = s.mob.getAttribute(Attribute.SCALE);
        if (a != null) a.setBaseValue(s.o.taille());
        s.mob.setGlowing(s.m != Mutation.AUCUNE);
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + p.getName() + "</b> a ramené l'Orbito géant " + s.o.nomComplet(s.m) + " <#E6EDF3>chez lui !"));
        geant = null;
    }

    void montrer(Player p) {
        if (actif != null) p.showBossBar(barre);
    }

    // ------------------------------------------------------------------ marchand noir

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClicMarchand(PlayerInteractEntityEvent e) {
        if (marchand == null || !e.getRightClicked().equals(marchand)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (offre == null) return;
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        Gui g = new Gui(27, "<#A64DFF><b>Marchand noir</b>");
        g.case_(13, Oeufs.carte(offre, offreMutation), null);
        g.case_(11, Objets.objet(Material.LIME_CONCRETE, 1, null, "oui", "<#7CFC9A><b>Acheter</b>", Txt.cr(offrePrix),
                pr.credits >= offrePrix ? "<#7CFC9A>Tu peux te l'offrir !" : "<#FF4D6D>Il te manque " + Txt.cr(offrePrix - pr.credits)), this::acheter);
        g.case_(15, Objets.objet(Material.RED_CONCRETE, 1, null, "non", "<#FF4D6D><b>Non merci</b>"), Player::closeInventory);
        g.ouvrir(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDegat(EntityDamageEvent e) {
        if (marchand != null && e.getEntity().equals(marchand)) e.setCancelled(true);
    }

    private void acheter(Player p) {
        p.closeInventory();
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (offre == null || pr == null) { Txt.send(p, "Trop tard, il a déjà été vendu !"); return; }
        if (jeu.baseDe.get(p.getUniqueId()) == null) { Txt.send(p, "Il te faut une base."); return; }
        int place = pr.placeLibre();
        if (place < 0) { Txt.send(p, "<#FF4D6D>Ta base est pleine."); return; }
        if (pr.credits < offrePrix) { Txt.send(p, "Il te manque " + Txt.cr(offrePrix - pr.credits) + "."); return; }
        pr.credits -= offrePrix;
        Orbito o = offre;
        Mutation m = offreMutation;
        offre = null;
        jeu.reserver(pr, place, o, m, 1);
        jeu.apparaitre(p, place);
        Oeufs.reveler(jeu, p, pr, o, m, "vient du marchand noir");
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + p.getName() + "</b> a acheté " + o.nomComplet(m) + " <#E6EDF3>au marchand noir !"));
    }

    // ------------------------------------------------------------------ tapis

    /** Arrivée d'un Orbito rare sur le tapis : colonne de lumière, feu d'artifice, annonce. */
    void arriveeRare(Jeu.Spec s) {
        Rarete r = s.o.rarete();
        if (r.ordinal() < Rarete.LEGENDAIRE.ordinal() && s.m.mult < 5) return;
        Color c = switch (r) {
            case MYTHIQUE -> Color.fromRGB(0xFF4D6D);
            case COSMIQUE -> Color.fromRGB(0x62CFF4);
            case SECRET -> Color.fromRGB(0xFFFFFF);
            default -> Color.fromRGB(0xE0B864);
        };
        Location l = s.mob.getLocation();
        Firework f = jeu.w.spawn(l.clone().add(0, 1.5, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(r.ordinal() >= Rarete.MYTHIQUE.ordinal() ? FireworkEffect.Type.STAR : FireworkEffect.Type.BURST)
                    .withColor(c).withFade(Color.WHITE).flicker(true).trail(true).build());
            fw.setFireworkMeta(meta);
            fw.setPersistent(false);
        });
        Bukkit.getScheduler().runTaskLater(jeu.plugin, f::detonate, 2);
        for (int k = 0; k < 12; k++) {
            final int i = k;
            Bukkit.getScheduler().runTaskLater(jeu.plugin, () -> {
                if (!s.mob.isValid()) return;
                Location m = s.mob.getLocation();
                for (double y = 0; y < 14; y += 0.5)
                    jeu.w.spawnParticle(Particle.DUST, m.clone().add(0, y, 0), 1, 0.05, 0, 0.05, 0, new Particle.DustOptions(c, 1.6f));
                if (i == 0) jeu.w.spawnParticle(Particle.TOTEM_OF_UNDYING, m.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
            }, k * 5L);
        }
        if (r.ordinal() >= Rarete.MYTHIQUE.ordinal() && r != Rarete.SECRET)
            for (Player p : jeu.w.getPlayers())
                p.showTitle(Title.title(Component.empty(), Txt.mm(r.couleur + "<b>" + r.nom.toUpperCase() + "</b> <#E6EDF3>sur le tapis : " + s.o.nomComplet(s.m)),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(400))));
    }
}
