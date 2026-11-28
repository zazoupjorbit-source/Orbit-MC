package fr.orbit.bowl;

import fr.orbit.bowl.baby.PartieBaby;
import fr.orbit.bowl.basket.PartieBasket;
import fr.orbit.bowl.bowling.PartieBowling;
import fr.orbit.bowl.fleches.PartieFleches;
import fr.orbit.bowl.golf.PartieGolf;
import fr.orbit.bowl.laser.PartieLaser;
import fr.orbit.bowl.tir.PartieTir;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tous les postes de la salle, leurs consoles cliquables (entités Interaction) et les menus du chat. */
public final class Postes {

    private final OrbitBowl pl;
    private final Map<String, Poste> parId = new LinkedHashMap<>();
    private final Map<UUID, Poste> consoles = new HashMap<>();
    private final Map<String, TextDisplay> etiquettes = new HashMap<>();

    Postes(OrbitBowl pl) {
        this.pl = pl;
        for (int i = 0; i < Salle.PISTES; i++) {
            String nom = "Piste " + (i + 1) + " · " + Salle.PLANETES[i].charAt(0) + Salle.PLANETES[i].substring(1).toLowerCase(java.util.Locale.ROOT);
            add(new Poste("piste" + (i + 1), nom, Jeu.BOWLING, i, Salle.pisteX(i), Salle.Y, 6.5, 0f));
        }
        for (int k = 0; k < 3; k++) {
            add(new Poste("cible" + (k + 1), "Cible " + (k + 1), Jeu.FLECHETTES, k, -29.5, Salle.Y, Salle.cibleZ(k), -90f));
        }
        for (int k = 0; k < 2; k++) {
            add(new Poste("baby" + (k + 1), "Table " + (k + 1), Jeu.BABY, k, -12.5, Salle.Y, Salle.babyZ0(k) + 2.5, -90f));
        }
        for (int k = 0; k < 3; k++) {
            add(new Poste("basket" + (k + 1), "Borne " + (k + 1), Jeu.BASKET, k, Salle.basketX(k), Salle.Y, 35.5, 180f));
        }
        for (int k = 0; k < 2; k++) {
            add(new Poste("tir" + (k + 1), "Stand " + (k + 1), Jeu.TIR, k, 23.5, Salle.Y, Salle.tirZ(k), 90f));
        }
        add(new Poste("golf", "Départ du trou 1", Jeu.GOLF, 0, -25.5, Salle.Y, 43.5, 180f));
        add(new Poste("laser", "Arène Laser Nova", Jeu.LASER, 0, 9.5, Salle.Y, 43.5, 180f));
    }

    private void add(Poste p) {
        parId.put(p.id, p);
    }

    public Poste get(String id) {
        return id == null ? null : parId.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public List<Poste> tous() {
        return new ArrayList<>(parId.values());
    }

    // ------------------------------------------------------------------ consoles

    void poser(World w) {
        for (Poste p : parId.values()) {
            Location at = p.console(w);
            Interaction i = w.spawn(at, Interaction.class, e -> {
                OrbitBowl.marquer(e);
                e.setInteractionWidth(1.3f);
                e.setInteractionHeight(2.1f);
                e.setResponsive(true);
            });
            consoles.put(i.getUniqueId(), p);
            TextDisplay td = Salle.texte(at.clone().add(0, 2.35, 0), "", 0.7f, true);
            etiquettes.put(p.id, td);
            rafraichir(p);
        }
    }

    void retirer() {
        consoles.clear();
        etiquettes.clear();
    }

    public Poste console(Entity e) {
        return consoles.get(e.getUniqueId());
    }

    /** Texte au-dessus de la console : libre / en attente / en cours. */
    public void rafraichir(Poste p) {
        TextDisplay td = etiquettes.get(p.id);
        if (td == null || !td.isValid()) {
            return;
        }
        String etat;
        if (p.libre()) {
            etat = "<#7CFF8A>● libre</#7CFF8A> <#9AA4B2>· clic droit pour jouer";
        } else if (p.activite.phase() == Activite.Phase.ATTENTE) {
            etat = "<#E0B864>● on attend des joueurs</#E0B864> <#9AA4B2>· clic pour rejoindre";
        } else {
            etat = "<#E0484A>● en jeu</#E0484A> <#9AA4B2>· " + p.activite.etat();
        }
        td.text(Txt.mm(p.jeu.grad("<b>" + p.nom.toUpperCase(java.util.Locale.ROOT) + "</b>") + "\n" + etat));
    }

    public void rafraichirTout() {
        for (Poste p : parId.values()) {
            rafraichir(p);
        }
    }

    // ------------------------------------------------------------------ menus

    /** Clic sur une console. */
    public void menu(Player p, Poste poste) {
        if (pl.activite(p) != null) {
            Txt.send(p, "<#9AA4B2>Tu es déjà dans une partie. " + "<#5A6270>(/bowl quitter)");
            return;
        }
        if (!poste.libre()) {
            Activite a = poste.activite;
            if (a.ouverte()) {
                Txt.send(p, poste.jeu.grad("<b>" + poste.nom + "</b>") + " : " + a.mode.nom() + " ouverte ("
                        + a.joueurs().size() + "/" + a.mode.max() + ")" + (a.mise > 0 ? " · mise " + Txt.oc(a.mise) : ""));
                p.sendMessage(Txt.mm("  ").append(Txt.button("Rejoindre", poste.jeu.c1, "/bowl rejoindre " + poste.id,
                        "Rejoindre la partie" + (a.mise > 0 ? "\n<#E0B864>Mise : " + Txt.n(a.mise) + " OC" : ""))));
            } else {
                Txt.send(p, "<#E0484A>" + poste.nom + " est occupé" + (poste.jeu == Jeu.BOWLING ? "e" : "") + " <#9AA4B2>(" + a.etat() + ").");
            }
            return;
        }
        p.sendMessage(Txt.mm(" "));
        Txt.send(p, poste.jeu.titre() + " <#5A6270>·</#5A6270> " + poste.nom + " <#5A6270>·</#5A6270> <#9AA4B2>" + poste.jeu.description);
        Component ligne = Txt.mm("  ");
        for (Mode m : Mode.de(poste.jeu)) {
            if (m.mise()) {
                continue;
            }
            ligne = ligne.append(Txt.button(m.nom(), poste.jeu.c1, "/bowl jouer " + poste.id + " " + m.id(), m.aide())).append(Txt.mm(" "));
        }
        p.sendMessage(ligne);
        for (Mode m : Mode.de(poste.jeu)) {
            if (!m.mise()) {
                continue;
            }
            Component l = Txt.mm("  <#E0B864>" + m.nom() + " :</#E0B864> ");
            for (long s : Mode.MISES) {
                l = l.append(Txt.button(Txt.n(s) + " OC", "#E0B864", "/bowl jouer " + poste.id + " " + m.id() + " " + s,
                        m.aide() + "\n<#9AA4B2>Chacun mise " + Txt.n(s) + " OC, le gagnant prend " + Txt.n(2 * s) + " OC.\nAucune commission.")).append(Txt.mm(" "));
            }
            p.sendMessage(l);
        }
        Component bas = Txt.mm("  ").append(Txt.button("Règles", "#62CFF4", "/bowl regles " + poste.jeu.id, "Comment on joue"));
        if (poste.jeu.record != null) {
            bas = bas.append(Txt.mm(" ")).append(Txt.button("Records", "#E0B864", "/bowl top " + poste.jeu.id, "Les 10 meilleurs"));
        }
        p.sendMessage(bas);
    }

    /** Crée l'activité d'un poste. */
    public Activite creer(Poste poste, Mode m, long mise) {
        return switch (poste.jeu) {
            case BOWLING -> new PartieBowling(pl, poste, m, mise);
            case FLECHETTES -> new PartieFleches(pl, poste, m, mise);
            case TIR -> new PartieTir(pl, poste, m, mise);
            case BASKET -> new PartieBasket(pl, poste, m, mise);
            case GOLF -> new PartieGolf(pl, poste, m, mise);
            case BABY -> new PartieBaby(pl, poste, m, mise);
            case LASER -> new PartieLaser(pl, poste, m, mise);
        };
    }
}
