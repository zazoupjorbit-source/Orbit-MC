package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Échange d'Orbitos entre joueurs : A choisit un de ses Orbitos, puis un Orbito de B (ou « cadeau »),
 * B accepte ou refuse dans le chat (60 s). Tout est revérifié au moment d'accepter.
 */
final class Echange {

    record Offre(UUID de, UUID pour, int placeA, String cleA, int placeB, String cleB, long expire) {
    }

    final Jeu jeu;
    final Map<UUID, Offre> offres = new HashMap<>();   // par destinataire

    Echange(Jeu jeu) {
        this.jeu = jeu;
    }

    /** Étape 1 : choisir le partenaire. */
    void ouvrirJoueurs(Player p) {
        Gui g = new Gui(54, "<#62CFF4><b>Échanger</b> <#5A6270>· avec qui ?");
        int i = 0;
        for (Player o : jeu.w.getPlayers()) {
            if (o.equals(p) || !jeu.baseDe.containsKey(o.getUniqueId()) || i >= 45) continue;
            ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) tete.getItemMeta();
            m.setOwningPlayer(o);
            m.displayName(Txt.mm("<#E6EDF3><b>" + o.getName() + "</b>"));
            tete.setItemMeta(m);
            g.case_(i++, tete, x -> choisirMien(x, o));
        }
        if (i == 0) g.case_(22, Objets.objet(Material.BARRIER, 1, null, null, "<#FF4D6D>Personne d'autre n'a de base pour l'instant."), null);
        g.case_(45, Objets.retour(), x -> Objets.ouvrirMenu(x, jeu.profils.get(x.getUniqueId())));
        g.ouvrir(p);
    }

    private Gui grille(Player proprio, String titre, java.util.function.BiConsumer<Player, Integer> choix) {
        Gui g = new Gui(36, titre);
        Base b = jeu.baseDe.get(proprio.getUniqueId());
        Jeu.Spec[] tab = b == null ? null : jeu.poses.get(b.idx);
        if (tab != null) for (int i = 0; i < tab.length; i++) {
            Jeu.Spec s = tab[i];
            if (s == null || s.etat != Jeu.Etat.POSE) continue;
            final int place = i;
            g.case_(i, Objets.objet(Objets.oeuf(s.o), 1, null, "o_" + s.o.id(), s.o.nomComplet(s.m),
                    "<#5A6270>[" + s.o.rarete().etiquette() + "<#5A6270>] niv. " + s.niveau, "<#7CFC9A>+" + Txt.n(s.revenu()) + "/s"), x -> choix.accept(x, place));
        }
        return g;
    }

    /** Étape 2 : choisir ce qu'on donne. */
    void choisirMien(Player p, Player cible) {
        Gui g = grille(p, "<#62CFF4><b>Ce que tu donnes</b> <#5A6270>à " + cible.getName(), (x, place) -> choisirSien(x, cible, place));
        g.case_(27, Objets.retour(), this::ouvrirJoueurs);
        g.ouvrir(p);
    }

    /** Étape 3 : choisir ce qu'on veut en échange (ou rien). */
    void choisirSien(Player p, Player cible, int placeA) {
        if (!cible.isOnline()) { p.closeInventory(); return; }
        Gui g = grille(cible, "<#62CFF4><b>Ce que tu veux</b> <#5A6270>de " + cible.getName(), (x, placeB) -> proposer(x, cible, placeA, placeB));
        g.case_(31, Objets.objet(Material.CAKE, 1, null, null, "<#7CFC9A><b>Rien : c'est un cadeau</b>"), x -> proposer(x, cible, placeA, -1));
        g.case_(27, Objets.retour(), x -> choisirMien(x, cible));
        g.ouvrir(p);
    }

    private void proposer(Player p, Player cible, int placeA, int placeB) {
        p.closeInventory();
        Profil a = jeu.profils.get(p.getUniqueId()), b = jeu.profils.get(cible.getUniqueId());
        if (a == null || b == null || a.places[placeA] == null) return;
        String cleB = placeB >= 0 ? b.places[placeB] : null;
        if (placeB >= 0 && cleB == null) return;
        offres.put(cible.getUniqueId(), new Offre(p.getUniqueId(), cible.getUniqueId(), placeA, a.places[placeA], placeB, cleB, System.currentTimeMillis() + 60_000));
        String donne = nom(a.places[placeA]);
        String veut = cleB == null ? "<#7CFC9A>rien (cadeau)" : nom(cleB);
        Txt.send(p, "Proposition envoyée à <b>" + cible.getName() + "</b> : tu donnes " + donne + " <#E6EDF3>contre " + veut + "<#E6EDF3>. Elle expire dans 60 s.");
        Txt.send(cible, "<b>" + p.getName() + "</b> te propose " + donne + " <#E6EDF3>contre " + veut + "<#E6EDF3>.");
        cible.sendMessage(Component.text("   ")
                .append(Txt.mm("<#7CFC9A><b>[ACCEPTER]</b>").clickEvent(ClickEvent.runCommand("/vol echange accepter")))
                .append(Component.text("   "))
                .append(Txt.mm("<#FF4D6D><b>[REFUSER]</b>").clickEvent(ClickEvent.runCommand("/vol echange refuser"))));
        Txt.sound(cible, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f);
    }

    static String nom(String cle) {
        String[] t = cle.split("\\|");
        Catalogue.Orbito o = Catalogue.get(t[0]);
        Catalogue.Mutation m = t.length > 1 ? Catalogue.Mutation.valueOf(t[1]) : Catalogue.Mutation.AUCUNE;
        return o == null ? "?" : o.nomComplet(m) + (t.length > 2 ? " <#5A6270>niv. " + t[2] : "");
    }

    void repondre(Player b, boolean oui) {
        Offre o = offres.remove(b.getUniqueId());
        if (o == null || o.expire() < System.currentTimeMillis()) { Txt.send(b, "Aucune proposition en cours."); return; }
        Player a = Bukkit.getPlayer(o.de());
        if (!oui) {
            Txt.send(b, "Proposition refusée.");
            if (a != null) Txt.send(a, "<#FF4D6D>" + b.getName() + " a refusé ton échange.");
            return;
        }
        Profil pa = a == null ? null : jeu.profils.get(a.getUniqueId()), pb = jeu.profils.get(b.getUniqueId());
        if (pa == null || pb == null || jeu.baseDe.get(a.getUniqueId()) == null || jeu.baseDe.get(b.getUniqueId()) == null) {
            Txt.send(b, "L'échange n'est plus possible."); return;
        }
        Jeu.Spec sa = jeu.specA(a, o.placeA()), sb = o.placeB() >= 0 ? jeu.specA(b, o.placeB()) : null;
        boolean ok = o.cleA().equals(pa.places[o.placeA()]) && sa != null && sa.etat == Jeu.Etat.POSE
                && (o.placeB() < 0 || (o.cleB().equals(pb.places[o.placeB()]) && sb != null && sb.etat == Jeu.Etat.POSE));
        if (!ok) { Txt.send(b, "L'échange n'est plus possible (un Orbito a bougé)."); Txt.send(a, "<#FF4D6D>Échange annulé : un Orbito a bougé."); return; }
        int placeChezB = o.placeB() >= 0 ? o.placeB() : pb.placeLibre();
        if (placeChezB < 0) { Txt.send(b, "<#FF4D6D>Ta base est pleine."); Txt.send(a, "<#FF4D6D>La base de " + b.getName() + " est pleine."); return; }
        String cleA = o.cleA(), cleB = o.cleB();
        jeu.retirerDeBase(pa, sa);
        if (sb != null) jeu.retirerDeBase(pb, sb);
        String[] ta = cleA.split("\\|");
        jeu.reserver(pb, placeChezB, Catalogue.get(ta[0]), Catalogue.Mutation.valueOf(ta[1]), ta.length > 2 ? Integer.parseInt(ta[2]) : 1);
        jeu.apparaitre(b, placeChezB);
        if (cleB != null) {
            String[] tb = cleB.split("\\|");
            jeu.reserver(pa, o.placeA(), Catalogue.get(tb[0]), Catalogue.Mutation.valueOf(tb[1]), tb.length > 2 ? Integer.parseInt(tb[2]) : 1);
            jeu.apparaitre(a, o.placeA());
        }
        Jeu.Spec da = new Jeu.Spec(); da.o = Catalogue.get(ta[0]); da.m = Catalogue.Mutation.valueOf(ta[1]);
        jeu.decouverte(b, pb, da);
        if (cleB != null) {
            String[] tb = cleB.split("\\|");
            Jeu.Spec db = new Jeu.Spec(); db.o = Catalogue.get(tb[0]); db.m = Catalogue.Mutation.valueOf(tb[1]);
            jeu.decouverte(a, pa, db);
        }
        for (Player x : new Player[]{a, b}) {
            Txt.send(x, "<#7CFC9A><b>Échange fait !</b>");
            Txt.sound(x, Sound.ENTITY_VILLAGER_CELEBRATE, 1.2f);
            jeu.hud(x);
        }
    }
}
