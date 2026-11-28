package fr.orbit.breche;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menus de BRÈCHE : accueil (cartes, niveau des bots, taille des équipes), choix de l'agent, boutique de la phase d'achat. */
final class Menus implements Listener {

    enum Type { ACCUEIL, AGENTS, BOUTIQUE }

    static final class Ecran implements InventoryHolder {
        final Type type;
        final Partie partie;
        Inventory inv;

        Ecran(Type type, Partie partie) {
            this.type = type;
            this.partie = partie;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final OrbitBreche pl;
    private final Map<UUID, int[]> reglages = new HashMap<>();   // {niveau des bots, joueurs par équipe}

    Menus(OrbitBreche pl) {
        this.pl = pl;
    }

    int[] reglages(Player p) {
        return reglages.computeIfAbsent(p.getUniqueId(), k -> new int[]{pl.getConfig().getInt("partie.niveau-bots", 2), pl.getConfig().getInt("partie.par-equipe", 5)});
    }

    private static ItemStack item(Material m, String nom, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(nom));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Txt.mm(s.startsWith("<") ? s : "<#9AA4B2>" + s));
        }
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static Inventory creer(Ecran e, int taille, String titre) {
        e.inv = Bukkit.createInventory(e, taille, Txt.mm(titre));
        ItemStack vitre = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < taille; i++) {
            e.inv.setItem(i, vitre);
        }
        return e.inv;
    }

    // ------------------------------------------------------------------ accueil

    void ouvrirAccueil(Player p) {
        Ecran e = new Ecran(Type.ACCUEIL, null);
        Inventory inv = creer(e, 27, "<#3A7FFF><b>BRÈCHE</b></#3A7FFF> <#5A6270>·</#5A6270> <#E0B864>choisis ta carte");
        int[] r = reglages(p);
        int slot = 10;
        for (Plan plan : Plan.toutes()) {
            Partie en = pl.jeux().partie(plan.id);
            List<String> lore = new ArrayList<>();
            lore.add(plan.accroche);
            lore.add("");
            if (en == null) {
                lore.add("<#7CFF8A>Clic : lancer une partie");
                lore.add(r[1] + " contre " + r[1] + " · bots " + niveau(r[0]));
            } else if (en.phase == Partie.Phase.ATTENTE) {
                lore.add("<#E0B864>Partie en attente : clic pour la rejoindre");
                lore.add(en.humains().size() + " joueur(s) inscrit(s)");
            } else {
                lore.add("<#E0B864>Partie en cours : clic pour entrer en renfort");
                lore.add("<#3A7FFF>Bleu " + en.score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + en.score[1] + " Or");
            }
            inv.setItem(slot, item(plan.id.equals("helios") ? Material.SEA_LANTERN : Material.RED_SANDSTONE,
                    "<" + plan.couleur + "><b>" + plan.nom + "</b>", lore));
            slot += 2;
        }
        inv.setItem(14, item(Material.ENDER_EYE, "<#C79BFF><b>CARTE AU HASARD</b>", List.of("Une des deux cartes libres.", "", "<#7CFF8A>Clic : lancer")));
        inv.setItem(16, item(Material.WRITABLE_BOOK, "<#E6EDF3><b>Comment jouer</b>", List.of(
                "Bleu contre Or, premier à " + pl.getConfig().getInt("partie.manches-victoire", 4) + " manches.",
                "Attaque : pose le Noyau sur le site A ou B",
                "(case 8 en main, accroupi 3,5 s) et défends-le.",
                "Défense : empêche la pose ou désamorce",
                "(accroupi à côté du Noyau, 7 s).",
                "Achats à chaque manche : armes, bouclier, pouvoirs.",
                "Pouvoirs : cases 4 à 7 puis clic droit.",
                "Clic droit : tirer · F : recharger · lame en case 3.")));
        inv.setItem(20, item(Material.REDSTONE_TORCH, "<#E6EDF3>Niveau des bots : <#E0B864><b>" + niveau(r[0]) + "</b>", List.of("Clic : changer (Recrue, Agent, Élite)")));
        inv.setItem(22, item(Material.PLAYER_HEAD, "<#E6EDF3>Équipes : <#E0B864><b>" + r[1] + " contre " + r[1] + "</b>", List.of("Clic : changer (3 contre 3 ou 5 contre 5)")));
        inv.setItem(24, item(Material.GOLD_INGOT, "<#E0B864><b>Classement</b>", List.of("Meilleurs scores de combat.")));
        p.openInventory(inv);
    }

    static String niveau(int n) {
        return n <= 1 ? "Recrue" : n >= 3 ? "Élite" : "Agent";
    }

    // ------------------------------------------------------------------ agents

    void ouvrirAgents(Player p, Partie partie) {
        Ecran e = new Ecran(Type.AGENTS, partie);
        Inventory inv = creer(e, 27, "<#E0B864><b>Choisis ton agent</b>");
        Combattant moi = partie.de(p);
        int slot = 11;
        for (Agent a : Agent.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("<" + a.couleur + ">" + a.role);
            lore.add("<i>" + a.bio + "</i>");
            lore.add("");
            for (Agent.Pouvoir pv : a.pouvoirs) {
                lore.add("<#E6EDF3><b>" + pv.touche() + "</b> " + pv.nom() + (pv.ultime() ? " <#E0B864>(ultime " + pv.max() + " pts)" : pv.prix() > 0 ? " <#5A6270>" + pv.prix() + " ¤" : " <#7CFF8A>gratuit"));
                lore.add("  " + pv.desc());
            }
            lore.add("");
            boolean pris = moi != null && partie.agentPris(moi.equipe, a, moi);
            lore.add(moi != null && moi.agent == a ? "<#7CFF8A><b>Ton agent</b>" : pris ? "<#E0484A>Déjà pris dans ton équipe" : "<#7CFF8A>Clic : choisir");
            ItemStack it = item(Material.LEATHER_CHESTPLATE, "<" + a.couleur + "><b>" + a.nom.toUpperCase() + "</b>", lore);
            LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
            meta.setColor(org.bukkit.Color.fromRGB(a.rgb()));
            if (moi != null && moi.agent == a) {
                meta.setEnchantmentGlintOverride(true);
            }
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ boutique

    void ouvrirBoutique(Player p, Partie partie) {
        Combattant c = partie.de(p);
        if (c == null) {
            return;
        }
        if (partie.phase != Partie.Phase.ACHAT || !c.vivant) {
            Txt.bar(p, "<#9AA4B2>La boutique n'ouvre que pendant la phase d'achat.");
            return;
        }
        Ecran e = new Ecran(Type.BOUTIQUE, partie);
        creer(e, 54, "<#7CFF8A><b>Boutique</b></#7CFF8A> <#5A6270>·</#5A6270> <#E6EDF3>" + Txt.n(c.credits) + " ¤");
        remplirBoutique(e, c);
        p.openInventory(e.inv);
    }

    private void remplirBoutique(Ecran e, Combattant c) {
        Inventory inv = e.inv;
        inv.setItem(4, item(Material.GOLD_NUGGET, "<#7CFF8A><b>" + Txt.n(c.credits) + " ¤</b>", List.of("Clic gauche : acheter", "Clic droit : revendre un achat de cette manche")));
        armeCase(inv, 10, Arme.ETINCELLE, c);
        armeCase(inv, 11, Arme.FAUCON, c);
        armeCase(inv, 13, Arme.ESSAIM, c);
        armeCase(inv, 14, Arme.BRISE_ROC, c);
        armeCase(inv, 15, Arme.COMETE, c);
        armeCase(inv, 16, Arme.ZENITH, c);
        for (Arme.Bouclier b : Arme.Bouclier.values()) {
            boolean a = c.bouclier >= b.points;
            inv.setItem(b == Arme.Bouclier.LEGER ? 29 : 30, item(b == Arme.Bouclier.LEGER ? Material.IRON_CHESTPLATE : Material.DIAMOND_CHESTPLATE,
                    (a ? "<#7CFF8A>" : "<#E6EDF3>") + "<b>" + b.nom + "</b> <#5A6270>· " + prix(b.prix, c),
                    List.of("+" + b.points + " points de bouclier.", "Absorbe les deux tiers des dégâts.", a ? "<#7CFF8A>Équipé" : "")));
        }
        int slot = 32;
        for (int i = 0; i < 3; i++) {
            Agent.Pouvoir pv = c.agent.pouvoirs[i];
            if (pv.prix() <= 0 && pv.gratuit() > 0 && pv.max() <= pv.gratuit()) {
                inv.setItem(slot++, item(Material.LIME_DYE, "<" + c.agent.couleur + "><b>" + pv.touche() + " · " + pv.nom() + "</b> <#7CFF8A>gratuit",
                        List.of(pv.desc(), "Charges : " + c.charges[i] + "/" + pv.max())));
                continue;
            }
            ItemStack it = pl.objets().modele(Material.PAPER, "pouvoir_" + pv.id());
            ItemMeta m = it.getItemMeta();
            m.displayName(Txt.mm("<" + c.agent.couleur + "><b>" + pv.touche() + " · " + pv.nom() + "</b> <#5A6270>· " + prix(pv.prix(), c)));
            m.lore(List.of(Txt.mm("<#9AA4B2>" + pv.desc()), Txt.mm("<#E6EDF3>Charges : " + c.charges[i] + "/" + pv.max())));
            m.addItemFlags(ItemFlag.values());
            it.setItemMeta(m);
            it.setAmount(Math.max(1, c.charges[i]));
            inv.setItem(slot++, it);
        }
        inv.setItem(49, item(Material.BARRIER, "<#E0484A>Fermer", List.of()));
    }

    private static String prix(int prix, Combattant c) {
        return prix == 0 ? "<#7CFF8A>gratuit" : (c.credits >= prix ? "<#7CFF8A>" : "<#E0484A>") + Txt.n(prix) + " ¤";
    }

    private void armeCase(Inventory inv, int slot, Arme a, Combattant c) {
        boolean a1 = c.primaire == a || c.secondaire == a;
        ItemStack it = pl.objets().arme(a);
        ItemMeta m = it.getItemMeta();
        m.displayName(Txt.mm((a1 ? "<#7CFF8A>" : "<#E6EDF3>") + "<b>" + a.nom + "</b> <#5A6270>· " + prix(a.prix, c) + (a.primaire() ? " <#5A6270>(principale)" : " <#5A6270>(pistolet)")));
        List<Component> lore = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
        if (a1) {
            lore.add(Txt.mm("<#7CFF8A>Équipée"));
        }
        m.lore(lore);
        it.setItemMeta(m);
        inv.setItem(slot, it);
    }

    private void acheter(Player p, Ecran e, int slot, boolean revendre) {
        Partie partie = e.partie;
        Combattant c = partie.de(p);
        if (c == null || partie.phase != Partie.Phase.ACHAT || !c.vivant) {
            p.closeInventory();
            return;
        }
        Arme arme = switch (slot) {
            case 10 -> Arme.ETINCELLE;
            case 11 -> Arme.FAUCON;
            case 13 -> Arme.ESSAIM;
            case 14 -> Arme.BRISE_ROC;
            case 15 -> Arme.COMETE;
            case 16 -> Arme.ZENITH;
            default -> null;
        };
        boolean ok = false;
        String msg = null;
        if (arme != null) {
            String cle = "arme" + arme.place;
            if (revendre) {
                if ((arme.primaire() ? c.primaire : c.secondaire) == arme && c.achats.containsKey(cle)) {
                    Object avant = c.achats.remove(cle);
                    c.credits += arme.prix;
                    if (arme.primaire()) {
                        c.primaire = (Arme) avant;
                    } else {
                        c.secondaire = (Arme) avant;
                    }
                    ok = true;
                    msg = arme.nom + " revendue.";
                }
            } else if ((arme.primaire() ? c.primaire : c.secondaire) != arme) {
                if (c.credits < arme.prix) {
                    msg = "<#E0484A>Pas assez de crédits.";
                } else {
                    c.achats.putIfAbsent(cle, arme.primaire() ? c.primaire : c.secondaire);
                    c.credits -= arme.prix;
                    if (arme.primaire()) {
                        c.primaire = arme;
                    } else {
                        c.secondaire = arme;
                    }
                    ok = true;
                    msg = "Acheté : " + arme.nom;
                }
            }
            if (ok) {
                c.pleinMunitions();
            }
        } else if (slot == 29 || slot == 30) {
            Arme.Bouclier b = slot == 29 ? Arme.Bouclier.LEGER : Arme.Bouclier.LOURD;
            if (revendre) {
                if (c.achats.get("bouclier") instanceof Object[] avant && avant[1] == b) {
                    c.achats.remove("bouclier");
                    c.credits += b.prix;
                    c.bouclier = (Double) avant[0];
                    ok = true;
                    msg = b.nom + " revendu.";
                }
            } else if (c.bouclier < b.points) {
                if (c.credits < b.prix) {
                    msg = "<#E0484A>Pas assez de crédits.";
                } else {
                    if (c.achats.get("bouclier") instanceof Object[] avant) {
                        c.credits += ((Arme.Bouclier) avant[1]).prix;
                        c.bouclier = (Double) avant[0];
                    }
                    c.achats.put("bouclier", new Object[]{c.bouclier, b});
                    c.credits -= b.prix;
                    c.bouclier = b.points;
                    ok = true;
                    msg = "Acheté : " + b.nom;
                }
            }
        } else if (slot >= 32 && slot <= 34) {
            int i = slot - 32;
            Agent.Pouvoir pv = c.agent.pouvoirs[i];
            String cle = "pouvoir" + i;
            if (pv.prix() > 0) {
                if (revendre) {
                    int n = c.achats.get(cle) instanceof Integer k ? k : 0;
                    if (n > 0 && c.charges[i] > 0) {
                        c.achats.put(cle, n - 1);
                        c.charges[i]--;
                        c.credits += pv.prix();
                        ok = true;
                        msg = pv.nom() + " revendu.";
                    }
                } else if (c.charges[i] >= pv.max()) {
                    msg = "<#9AA4B2>" + pv.nom() + " déjà au maximum.";
                } else if (c.credits < pv.prix()) {
                    msg = "<#E0484A>Pas assez de crédits.";
                } else {
                    c.achats.merge(cle, 1, (x, y) -> (Integer) x + 1);
                    c.charges[i]++;
                    c.credits -= pv.prix();
                    ok = true;
                    msg = "Acheté : " + pv.nom();
                }
            }
        } else if (slot == 49) {
            p.closeInventory();
            return;
        }
        if (msg != null) {
            Txt.bar(p, (msg.startsWith("<") ? "" : "<#7CFF8A>") + msg);
        }
        p.playSound(p.getLocation(), ok ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_DISPENSER_FAIL, 0.7f, ok ? 1.4f : 1.6f);
        if (ok) {
            partie.vie(c);
            partie.hotbar(c);
            Bukkit.getScheduler().runTask(pl, () -> {
                if (partie.phase == Partie.Phase.ACHAT && p.isOnline()) {
                    ouvrirBoutique(p, partie);
                }
            });
        }
    }

    // ------------------------------------------------------------------ clics

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getView().getTopInventory().getHolder() instanceof Ecran e) || !(ev.getWhoClicked() instanceof Player p)) {
            return;
        }
        ev.setCancelled(true);
        if (ev.getClickedInventory() == null || ev.getClickedInventory() != ev.getView().getTopInventory()) {
            return;
        }
        int slot = ev.getRawSlot();
        switch (e.type) {
            case ACCUEIL -> {
                int[] r = reglages(p);
                switch (slot) {
                    case 10 -> lancer(p, "helios");
                    case 12 -> lancer(p, "vesta");
                    case 14 -> lancer(p, "hasard");
                    case 16 -> {
                    }
                    case 20 -> {
                        r[0] = r[0] % 3 + 1;
                        ouvrirAccueil(p);
                    }
                    case 22 -> {
                        r[1] = r[1] == 5 ? 3 : 5;
                        ouvrirAccueil(p);
                    }
                    case 24 -> {
                        p.closeInventory();
                        pl.profils().top(p);
                    }
                    default -> {
                    }
                }
            }
            case AGENTS -> {
                if (slot < 11 || slot >= 11 + Agent.values().length) {
                    return;
                }
                Agent a = Agent.values()[slot - 11];
                Combattant c = e.partie.de(p);
                if (c == null || e.partie.phase != Partie.Phase.ATTENTE) {
                    p.closeInventory();
                    return;
                }
                if (e.partie.agentPris(c.equipe, a, c)) {
                    p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.7f, 1.6f);
                    return;
                }
                c.agent = a;
                pl.objets().habiller(c);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                p.closeInventory();
                Txt.send(p, "Agent : " + a.nomCouleur() + " <#9AA4B2>(" + a.role + ") · C, Q, E en cases 4 à 6, ultime en case 7.");
                e.partie.annoncerEquipe(c.equipe, c.nomCouleur() + " <#9AA4B2>joue</#9AA4B2> " + a.nomCouleur() + ".");
            }
            case BOUTIQUE -> acheter(p, e, slot, ev.getClick() == ClickType.RIGHT || ev.getClick() == ClickType.SHIFT_RIGHT);
        }
    }

    private void lancer(Player p, String carte) {
        p.closeInventory();
        int[] r = reglages(p);
        pl.jeux().jouer(p, carte, r[0], r[1]);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent ev) {
        if (ev.getView().getTopInventory().getHolder() instanceof Ecran) {
            ev.setCancelled(true);
        }
    }
}
