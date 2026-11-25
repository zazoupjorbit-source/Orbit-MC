package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import fr.orbit.vol.Catalogue.Mutation;
import fr.orbit.vol.Catalogue.Orbito;
import fr.orbit.vol.Catalogue.Rarete;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Œufs mystères (payés en crédits du jeu, jamais en argent réel) avec roulette animée,
 * et fusion : 3 Orbitos identiques → 1 Orbito de la rareté au-dessus.
 */
final class Oeufs {

    /** Poids par rareté (ordre de {@link Rarete}). */
    record Oeuf(String id, String nom, long prix, double[] poids, String description) {
    }

    static final List<Oeuf> OEUFS = List.of(
            new Oeuf("oeuf_stellaire", "<#62CFF4><b>Œuf stellaire</b>", 2_500,
                    new double[]{50, 35, 13, 2, 0, 0, 0}, "Commun 50 % · Rare 35 % · Épique 13 % · Légendaire 2 %"),
            new Oeuf("oeuf_nebuleuse", "<#A64DFF><b>Œuf nébuleuse</b>", 27_500,
                    new double[]{0, 40, 40, 17, 3, 0, 0}, "Rare 40 % · Épique 40 % · Légendaire 17 % · Mythique 3 %"),
            new Oeuf("oeuf_cosmique", "<gradient:#E0B864:#62CFF4><b>Œuf cosmique</b>", 750_000,
                    new double[]{0, 0, 0, 60, 32, 7.5, 0.5}, "Légendaire 60 % · Mythique 32 % · Cosmique 7,5 % · Secret 0,5 %"));

    private Oeufs() {
    }

    static Rarete tirer(double[] poids) {
        double total = 0;
        for (double x : poids) total += x;
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < poids.length; i++) {
            r -= poids[i];
            if (r < 0) return Rarete.values()[i];
        }
        return Rarete.COMMUN;
    }

    static List<Orbito> deRarete(Rarete r) {
        List<Orbito> l = new ArrayList<>();
        for (Orbito o : Catalogue.TOUS.values()) if (o.rarete() == r) l.add(o);
        return l;
    }

    static Orbito auHasard(Rarete r) {
        List<Orbito> l = deRarete(r);
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    static ItemStack carte(Orbito o, Mutation m) {
        return Objets.objet(Objets.oeuf(o), 1, null, "o_" + o.id(), o.nomComplet(m), "<#5A6270>[" + o.rarete().etiquette() + "<#5A6270>]");
    }

    // ------------------------------------------------------------------ menu

    static void ouvrir(Jeu jeu, Player p) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        Gui g = new Gui(27, "<#E0B864><b>Œufs mystères</b> <#5A6270>· " + Txt.cr(pr.credits));
        int[] cases = {11, 13, 15};
        for (int i = 0; i < OEUFS.size(); i++) {
            Oeuf o = OEUFS.get(i);
            g.case_(cases[i], Objets.objet(Material.TURTLE_EGG, 1, null, o.id(), o.nom(),
                    "<gray>Un Orbito au hasard, posé dans ta base.", "<#8A96A8>" + o.description(),
                    "<gray>Les mutations peuvent tomber (×2 pendant les météores).", "",
                    "<#E6EDF3>Prix : " + Txt.cr(o.prix()) + (pr.credits >= o.prix() ? " <#7CFC9A>✔" : " <#FF4D6D>✘")), x -> acheter(jeu, x, o));
        }
        g.case_(22, Objets.objet(Material.ANVIL, 1, null, "fusion", "<#FF7A1A><b>Fusion</b>",
                "<gray>3 Orbitos identiques dans ta base", "<gray>= 1 Orbito de la rareté au-dessus !",
                "<gray>Clique un de tes Orbitos → « Fusionner »."), null);
        g.case_(18, Objets.retour(), x -> Objets.ouvrirMenu(x, jeu.profils.get(x.getUniqueId())));
        g.ouvrir(p);
    }

    static void acheter(Jeu jeu, Player p, Oeuf oeuf) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        Base b = jeu.baseDe.get(p.getUniqueId());
        if (pr == null || b == null) { Txt.send(p, "Il te faut une base."); return; }
        int place = pr.placeLibre();
        if (place < 0) { Txt.send(p, "<#FF4D6D>Ta base est pleine.</#FF4D6D> Fais de la place avant d'ouvrir un œuf."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
        if (pr.credits < oeuf.prix()) { Txt.send(p, "Il te manque " + Txt.cr(oeuf.prix() - pr.credits) + "."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
        pr.credits -= oeuf.prix();
        Rarete r = tirer(oeuf.poids());
        Orbito o = auHasard(r);
        Mutation m = Catalogue.tirerMutation(jeu.meteoresFin > System.currentTimeMillis());
        jeu.reserver(pr, place, o, m, 1);
        List<Orbito> pool = new ArrayList<>();
        for (int i = 0; i < oeuf.poids().length; i++) if (oeuf.poids()[i] > 0) pool.addAll(deRarete(Rarete.values()[i]));
        roulette(jeu, p, oeuf.nom(), pool, o, m, () -> {
            jeu.apparaitre(p, place);
            reveler(jeu, p, pr, o, m, "a éclos d'un " + oeuf.nom().replaceAll("<[^>]*>", ""));
            Quetes.avancer(jeu, p, pr, Quetes.Type.ACHAT, 1);
            if (o.rarete().ordinal() >= Rarete.EPIQUE.ordinal()) Quetes.avancer(jeu, p, pr, Quetes.Type.ACHAT_RARE, 1);
            if (m != Mutation.AUCUNE) Quetes.avancer(jeu, p, pr, Quetes.Type.MUTATION, 1);
        });
    }

    static void reveler(Jeu jeu, Player p, Profil pr, Orbito o, Mutation m, String verbe) {
        Txt.send(p, o.nomComplet(m) + " <#5A6270>[" + o.rarete().etiquette() + "<#5A6270>] <#E6EDF3>" + verbe + " et rejoint ta base !");
        Jeu.Spec s = new Jeu.Spec();
        s.o = o;
        s.m = m;
        jeu.decouverte(p, pr, s);
        if (o.rarete().ordinal() >= Rarete.MYTHIQUE.ordinal())
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<b>" + p.getName() + "</b> obtient " + o.nomComplet(m) + " <#5A6270>[" + o.rarete().etiquette() + "<#5A6270>] <#E6EDF3>!"));
        jeu.hud(p);
    }

    /** Roulette : la rangée du milieu défile en ralentissant et s'arrête sur le gagnant (case 13). */
    static void roulette(Jeu jeu, Player p, String titre, List<Orbito> pool, Orbito gagnant, Mutation m, Runnable fin) {
        Gui g = new Gui(27, titre);
        g.verrouille = true;
        ItemStack vitre = Objets.objet(Material.BLACK_STAINED_GLASS_PANE, 1, null, null, " ");
        ItemStack fleche = Objets.objet(Material.YELLOW_STAINED_GLASS_PANE, 1, null, null, "<#E0B864>▼");
        for (int i = 0; i < 9; i++) { g.case_(i, i == 4 ? fleche : vitre, null); g.case_(18 + i, i == 4 ? fleche : vitre, null); }
        int etapes = 26;
        List<Orbito> suite = new ArrayList<>();
        for (int i = 0; i < etapes + 9; i++) suite.add(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
        suite.set(etapes - 1 + 4, gagnant);
        boolean[] fini = {false};
        Runnable terminer = () -> {
            if (fini[0]) return;
            fini[0] = true;
            fin.run();
        };
        g.aLaFermeture = terminer;
        g.ouvrir(p);
        long delai = 0;
        for (int k = 0; k < etapes; k++) {
            final int pas = k;
            delai += 1 + (long) (k * k / 45.0);
            Bukkit.getScheduler().runTaskLater(jeu.plugin, () -> {
                if (fini[0] || !p.isOnline()) return;
                for (int i = 0; i < 9; i++) {
                    Orbito o = suite.get(pas + i);
                    ItemStack it = carte(o, o == gagnant && pas == etapes - 1 && i == 4 ? m : Mutation.AUCUNE);
                    Objets.habiller(it, jeu.pack.charge(p));
                    g.getInventory().setItem(9 + i, it);
                }
                Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f + pas * 0.02f);
                if (pas == etapes - 1) {
                    Txt.sound(p, gagnant.rarete().ordinal() >= Rarete.LEGENDAIRE.ordinal() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                    Bukkit.getScheduler().runTaskLater(jeu.plugin, () -> {
                        if (!fini[0] && p.getOpenInventory().getTopInventory().getHolder() == g) p.closeInventory();
                        terminer.run();
                    }, 40);
                }
            }, delai);
        }
    }

    // ------------------------------------------------------------------ fusion

    static List<Jeu.Spec> identiques(Jeu jeu, Player p, Jeu.Spec s) {
        List<Jeu.Spec> l = new ArrayList<>();
        Base b = jeu.baseDe.get(p.getUniqueId());
        Jeu.Spec[] tab = b == null ? null : jeu.poses.get(b.idx);
        if (tab == null) return l;
        for (Jeu.Spec x : tab) if (x != null && x.etat == Jeu.Etat.POSE && x.o == s.o) l.add(x);
        return l;
    }

    static void fusionner(Jeu jeu, Player p, Jeu.Spec s) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null || s.etat != Jeu.Etat.POSE || !p.getUniqueId().equals(s.proprio)) return;
        if (s.o.rarete() == Rarete.SECRET) { Txt.send(p, "Les Secrets ne se fusionnent pas : ils sont déjà au sommet."); return; }
        List<Jeu.Spec> l = identiques(jeu, p, s);
        if (l.size() < 3) { Txt.send(p, "Il te faut <b>3</b> " + s.o.nomComplet(s.m) + " <#E6EDF3>dans ta base (tu en as " + l.size() + ")."); return; }
        l.remove(s);
        l.sort((a, b) -> Integer.compare(a.niveau * 10 + a.m.mult, b.niveau * 10 + b.m.mult));
        List<Jeu.Spec> trois = new ArrayList<>(List.of(s, l.get(0), l.get(1)));
        Mutation meilleure = Mutation.AUCUNE;
        for (Jeu.Spec x : trois) if (x.m.mult > meilleure.mult) meilleure = x.m;
        int place = s.place;
        for (Jeu.Spec x : trois) jeu.retirerDeBase(pr, x);
        Rarete suivante = Rarete.values()[s.o.rarete().ordinal() + 1];
        Orbito o = auHasard(suivante);
        jeu.reserver(pr, place, o, meilleure, 1);
        final Mutation mm = meilleure;
        p.closeInventory();
        Txt.sound(p, Sound.BLOCK_ANVIL_USE, 0.8f);
        roulette(jeu, p, "<#FF7A1A><b>Fusion…</b>", deRarete(suivante), o, mm, () -> {
            jeu.apparaitre(p, place);
            pr.titres.add("fusion");
            reveler(jeu, p, pr, o, mm, "naît de la fusion");
        });
    }
}
