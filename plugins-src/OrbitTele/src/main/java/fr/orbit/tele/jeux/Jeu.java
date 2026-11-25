package fr.orbit.tele.jeux;

import java.awt.Graphics2D;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Un jeu de la console : dessiné sur une image de 640 × 384 (mise à l'échelle de l'écran ensuite), 30 images par
 * seconde, pour un ou deux joueurs. Aucun appel au serveur ici : la console tourne sur son propre fil.
 */
public abstract class Jeu {

    public static final int W = 640, H = 384;

    /** Qui joue : un ou deux joueurs assis, leurs noms, leurs identifiants, et le dossier où un jeu peut sauvegarder. */
    public record Contexte(int joueurs, String[] noms, String[] ids, File dossier) {
        public boolean deux() {
            return joueurs == 2;
        }
    }

    /** @param records faux pour un jeu en duel pur (pas de classement) */
    public record Fiche(String id, String titre, String resume, String aDeux, String touches, boolean records, Function<Contexte, Jeu> creer) {
    }

    public static final List<Fiche> LISTE = List.of(
            new Fiche("tv_astro", "ASTROBLOCS", "Empile les blocs, vide les lignes.", "duel en écran scindé", "Q D déplacer · Z tourner · S descendre · Espace poser", true, Astroblocs::new),
            new Fiche("tv_defense", "DÉFENSE ORBITALE", "Repousse les vagues de drones.", "à deux, en équipe", "Q D déplacer · Espace tirer", true, Defense::new),
            new Fiche("tv_rush", "ORBIT RUSH", "Cours, saute, glisse, ramasse.", "duel en écran scindé", "Q D voies · Espace sauter · S glisser", true, Rush::new),
            new Fiche("tv_nebuleuse", "NÉBULEUSE", "Le serpent d'étoiles qui grandit.", "duel sur la même grille", "Z Q S D diriger", true, Serpent::new),
            new Fiche("tv_palet", "PALET ORBITAL", "Palet sur glace, premier à 7.", "duel (seul : contre ORBI-7)", "Z Q S D déplacer ton maillet", false, Palet::new),
            new Fiche("tv_nova", "CHARGE NOVA", "Pose tes charges, piège l'autre.", "duel (seul : contre ORBI-7)", "Z Q S D bouger · Espace poser une charge", false, Nova::new),
            new Fiche("tv_bastion", "BASTION ORBITAL", "Bâtis ta base, attaque les autres.", "duel : chacun attaque la base de l'autre", "Z Q S D curseur · Espace valider · Maj retour / attaquer", true, Bastion::new));

    protected final Contexte ctx;
    protected final Random alea = new Random();
    /** Noms affichés (un jeu peut mettre un robot à la place du second). */
    public final String[] noms;
    public int score;
    public boolean fini;
    /** Vainqueur d'un duel : 1 ou 2, 0 sinon. */
    public int gagnant;
    /** Faux quand la partie ne compte pas pour les records (duel). */
    public boolean record = true;
    /** Vrai pour un jeu sans fin dont le score est publié au fil de l'eau. */
    public boolean recordContinu;
    /** Résumé de fin de partie (« 7 – 4 »), facultatif. */
    public String bilan;
    protected long tics;
    public Consumer<String> sons = s -> {
    };

    protected Jeu(Contexte ctx) {
        this.ctx = ctx;
        this.noms = new String[]{ctx.noms()[0], ctx.deux() ? ctx.noms()[1] : "ORBI-7"};
    }

    protected void son(String nom) {
        sons.accept(nom);
    }

    /** Une image de jeu (30 par seconde). {@code b} ne bouge pas quand il n'y a qu'un joueur. */
    public final void avancer(Manette a, Manette b) {
        tics++;
        tic(a, b);
    }

    protected abstract void tic(Manette a, Manette b);

    public abstract void dessiner(Graphics2D g);
}
