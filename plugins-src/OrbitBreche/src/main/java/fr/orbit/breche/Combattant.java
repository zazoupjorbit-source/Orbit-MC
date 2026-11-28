package fr.orbit.breche;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/** Un combattant d'une partie : un joueur ou un bot (mannequin déplacé par le serveur). */
final class Combattant {

    final Player p;
    final String nom;
    Mannequin m;
    int equipe;
    Agent agent;

    // ---- manche
    double hp = 100, bouclier;
    boolean vivant = true;
    double mortX, mortZ;
    Arme primaire, secondaire = Arme.ETINCELLE;
    final int[] chargeur = new int[2], reserve = new int[2];
    int credits = 800;
    final int[] charges = new int[4], rechargeT = new int[4];
    int ult;
    boolean porteNoyau;
    int pose;            // progression de la pose / du désamorçage (ticks)
    double poseX, poseZ;
    final Map<Combattant, Integer> blessePar = new HashMap<>();
    final Map<String, Object> achats = new HashMap<>();

    // ---- stats de la partie
    int kills, morts, assists, degats, tetes, poses, desamorcages, score;

    // ---- tir (joueur et bot)
    int prochainTir, gachette = -1, rechargeFin = -1, rechargePlace, dernierTir, dernierClic = -100, dernierSlotArme;
    double recul;

    // ---- effets
    int aveugle, lent, revele, gel, commotion, myope, soinFin;
    double soinParTick;

    // ---- bot
    double x, z;
    float yaw, pitch;
    LinkedList<int[]> chemin = new LinkedList<>();
    Combattant cible;
    Object cibleTourelle;
    int reaction, reflexion, attente, prochainPouvoir, bouge;
    int[] but;
    char siteBut;
    double connuX = Double.NaN, connuZ = Double.NaN;
    int connuJusqu;
    boolean accroupi;
    int niveau = 2;

    Combattant(Player p, String nom) {
        this.p = p;
        this.nom = nom;
    }

    boolean bot() {
        return p == null;
    }

    Entity corps() {
        return p != null ? p : m;
    }

    Location pieds(World w) {
        return p != null ? p.getLocation() : new Location(w, x, Plan.Y, z, yaw, pitch);
    }

    Location oeil(World w) {
        if (p != null) {
            return p.getEyeLocation();
        }
        return new Location(w, x, Plan.Y + (accroupi ? 1.27 : 1.62), z, yaw, pitch);
    }

    Vector regard(World w) {
        return oeil(w).getDirection();
    }

    double px() {
        return p != null ? p.getLocation().getX() : x;
    }

    double pz() {
        return p != null ? p.getLocation().getZ() : z;
    }

    double dist2(double ox, double oz) {
        double dx = px() - ox, dz = pz() - oz;
        return dx * dx + dz * dz;
    }

    boolean accroupi() {
        return p != null ? p.isSneaking() : accroupi;
    }

    boolean bouge() {
        if (p != null) {
            Vector v = p.getVelocity();
            return v.getX() * v.getX() + v.getZ() * v.getZ() > 0.003 || !p.isOnGround();
        }
        return bouge > 0;
    }

    /** Arme tenue : celle de la main (joueur), la meilleure disponible (bot). Null pour la lame ou un pouvoir. */
    Arme arme() {
        if (p != null) {
            int slot = p.getInventory().getHeldItemSlot();
            return slot == 0 ? primaire : slot == 1 ? secondaire : null;
        }
        return primaire != null ? primaire : secondaire;
    }

    int place(Arme a) {
        return a == null ? -1 : a.place;
    }

    String nomCouleur() {
        return "<" + Txt.COUL[equipe] + ">" + nom + "</" + Txt.COUL[equipe] + ">";
    }

    void pleinMunitions() {
        if (primaire != null) {
            chargeur[0] = primaire.chargeur;
            reserve[0] = primaire.reserve;
        }
        if (secondaire != null) {
            chargeur[1] = secondaire.chargeur;
            reserve[1] = secondaire.reserve;
        }
    }
}
