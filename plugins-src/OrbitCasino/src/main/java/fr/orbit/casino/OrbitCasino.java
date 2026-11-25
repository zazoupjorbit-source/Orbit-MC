package fr.orbit.casino;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OrbitCasino : Orbit Coins de jeu (partout sur le réseau), boutique de cosmétiques et titres, et sur le lobby
 * le casino caché de La Station. Aucun achat réel : les OC se gagnent seulement en jouant.
 */
public final class OrbitCasino extends JavaPlugin implements TabCompleter {

    private Db db;
    private Wallet wallet;
    private Shop shop;
    private Casino casino;
    private fr.orbit.casino.arcade.Arcade arcade;

    public Db db() {
        return db;
    }

    public Wallet wallet() {
        return wallet;
    }

    public Shop shop() {
        return shop;
    }

    public Casino casino() {
        return casino;
    }

    private fr.orbit.casino.hud.CasinoPack pack;
    private fr.orbit.casino.hud.Screens screens;

    /** Écrans de table HD (null hors du lobby ou sans pack). */
    public fr.orbit.casino.hud.Screens screens() {
        return screens;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Holo.init(this);
        db = new Db(this);
        db.connect(Db.locate(this, getConfig().getString("base.fichier", "auto")));
        wallet = new Wallet(this, db);
        shop = new Shop(this, wallet);
        shop.load();
        getServer().getPluginManager().registerEvents(wallet, this);
        getServer().getPluginManager().registerEvents(shop, this);
        getServer().getPluginManager().registerEvents(new Menu.Clicks(this), this);
        wallet.loadOnline();
        wallet.startPlaytime();
        for (String c : new String[]{"orbitcoins", "table", "casino", "arcade", "cj"}) {
            var cmd = getCommand(c);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
        // le casino attend que les mondes des autres plugins (la station) soient créés
        Bukkit.getScheduler().runTask(this, () -> {
            String mode = getConfig().getString("casino.actif", "auto");
            boolean on = mode.equalsIgnoreCase("true")
                    || (mode.equalsIgnoreCase("auto") && Bukkit.getWorld(getConfig().getString("casino.entree.monde", "station")) != null);
            if (!on) {
                getLogger().info("Casino inactif sur ce serveur (Orbit Coins, boutique et titres seulement).");
                return;
            }
            if (!db.available()) {
                getLogger().severe("Casino coupé : pas de base.");
                return;
            }
            wallet.refundStranded();
            // cosmétiques payés en OC : seulement sur le lobby (les modes, dont les montures du MMO, gardent leurs règles)
            if (Bukkit.getPluginManager().getPlugin("OrbitDevCore") != null && !getConfig().getBoolean("boutique.debloquer-tout", false)) {
                CasinoAccess.register(this, shop);
            }
            // écran de table HD : pack ajouté par-dessus celui du lobby, servi en local
            fr.orbit.casino.hud.Hud.load(this);
            pack = new fr.orbit.casino.hud.CasinoPack(this);
            getServer().getPluginManager().registerEvents(pack, this);
            pack.enable();
            screens = new fr.orbit.casino.hud.Screens(this, pack);
            getServer().getPluginManager().registerEvents(screens, this);
            casino = new Casino(this);
            getServer().getPluginManager().registerEvents(casino, this);
            casino.start();
            if (casino.world() != null) {
                arcade = new fr.orbit.casino.arcade.Arcade(this, casino.world());
                getServer().getPluginManager().registerEvents(arcade, this);
                getServer().getPluginManager().registerEvents(arcade.runner(), this);
                arcade.start();
                getLogger().info("Arcade cachée ouverte (ORBIT RUSH, Vole mon Orbito, casino).");
            }
            getLogger().info(String.format(Locale.ROOT, "Casino ouvert. Machines à sous : %.1f %% redistribués (hors gros lot).",
                    casino.slots().rtp() * 100));
        });
    }

    @Override
    public void onDisable() {
        if (arcade != null) {
            arcade.stop();
        }
        if (screens != null) {
            screens.shutdown();
        }
        if (casino != null) {
            casino.stop();
        }
        if (pack != null) {
            pack.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    public void showTop(Player p) {
        wallet.top(list -> {
            Txt.send(p, "<#E0B864><b>Les plus riches en Orbit Coins</b>");
            int i = 1;
            for (Wallet.Rank r : list) {
                p.sendMessage(Txt.mm("  <#5A6270>" + i++ + ".</#5A6270> <#E6EDF3>" + r.nom() + "</#E6EDF3> " + Txt.oc(r.solde())));
            }
        });
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName();
        if (name.equals("cj")) {
            if (!(sender instanceof Player p)) {
                return true;
            }
            if (casino == null || casino.jeux() == null) {
                Txt.send(p, "<#E0484A>Il n'y a pas de casino sur ce serveur.");
            } else {
                casino.jeux().command(p, args);
            }
            return true;
        }
        if (name.equals("arcade")) {
            if (arcade == null) {
                Txt.send(sender, "<#E0484A>Il n'y a pas d'arcade sur ce serveur.");
            } else {
                arcade.command(sender, args);
            }
            return true;
        }
        if (name.equals("orbitcoins")) {
            if (!(sender instanceof Player p)) {
                Txt.send(sender, "Commande réservée aux joueurs.");
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "boutique", "shop" -> {
                    if (casino == null) {
                        Txt.send(p, "<#9AA4B2>La boutique des cosmétiques du lobby est au casino de La Station.");
                    } else {
                        shop.open(p);
                    }
                }
                case "titres", "titre" -> shop.openTitles(p);
                case "top", "classement" -> showTop(p);
                case "regles", "règles", "regle", "guide", "aide" -> {
                    String what = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                    Guide.open(this, p, Guide.chapter(what));
                }
                default -> {
                    long b = wallet.balance(p);
                    Txt.send(p, "Solde : " + (b < 0 ? "<#E0484A>indisponible (base injoignable)" : Txt.oc(b))
                            + " <#5A6270>·</#5A6270> <#9AA4B2>ration de " + Txt.n(getConfig().getLong("portefeuille.ration")) + " OC par jour, "
                            + Txt.n(getConfig().getLong("portefeuille.temps-de-jeu.gain")) + " OC toutes les "
                            + getConfig().getInt("portefeuille.temps-de-jeu.minutes") + " min de jeu.");
                    p.sendMessage(Txt.mm("  ").append(Txt.button("Boutique", "#E0B864", "/oc boutique", "Cosmétiques et titres"))
                            .append(Txt.mm(" ")).append(Txt.button("Titres", "#C79BFF", "/oc titres", "Ton titre de chat"))
                            .append(Txt.mm(" ")).append(Txt.button("Classement", "#62CFF4", "/oc top", "Les 10 plus riches")));
                }
            }
            return true;
        }
        if (name.equals("table")) {
            if (!(sender instanceof Player p)) {
                return true;
            }
            if (casino == null) {
                Txt.send(p, "<#E0484A>Il n'y a pas de casino sur ce serveur.");
                return true;
            }
            casino.tableCommand(p, args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT), args.length > 1 ? args[1] : null);
            return true;
        }
        // /casino (admin)
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "donner", "retirer" -> {
                if (args.length < 3) {
                    Txt.send(sender, "/casino " + sub + " <pseudo> <montant>");
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    Txt.send(sender, "<#E0484A>Montant invalide.");
                    return true;
                }
                long signed = sub.equals("donner") ? amount : -amount;
                wallet.adjust(args[1], signed, s -> Txt.send(sender, s == Long.MIN_VALUE ? "<#E0484A>Compte introuvable : " + args[1]
                        : args[1] + " : " + (signed >= 0 ? "+" : "") + Txt.n(signed) + " OC, solde " + Txt.oc(s)));
            }
            case "solde" -> {
                if (args.length < 2) {
                    Txt.send(sender, "/casino solde <pseudo>");
                    return true;
                }
                wallet.adjust(args[1], 0, s -> Txt.send(sender, s == Long.MIN_VALUE ? "<#E0484A>Compte introuvable." : args[1] + " : " + Txt.oc(s)));
            }
            case "recharger" -> {
                reloadConfig();
                shop.load();
                Txt.send(sender, "Réglages et catalogue rechargés (les jeux gardent leurs réglages jusqu'au redémarrage).");
            }
            case "generer", "tp", "bouton" -> {
                if (casino == null) {
                    Txt.send(sender, "<#E0484A>Le casino n'est pas actif sur ce serveur.");
                    return true;
                }
                switch (sub) {
                    case "generer" -> {
                        Txt.send(sender, "Salle reconstruite : " + Txt.n(casino.rebuild()) + " blocs, tables et PNJ remis.");
                        if (arcade != null) {
                            arcade.placeButtons(); // le bouton de l'arcade est dans le mur du casino
                            arcade.spawnAll();
                        }
                    }
                    case "tp" -> {
                        if (sender instanceof Player p) {
                            casino.enter(p);
                        }
                    }
                    default -> {
                        if (args.length > 1 && args[1].equalsIgnoreCase("poser")) {
                            Txt.send(sender, "Entrée secrète : " + casino.placeButton() + ".");
                        } else if (sender instanceof Player p) {
                            Txt.send(sender, "Entrée secrète : " + casino.setEntrance(p) + ".");
                        } else {
                            Txt.send(sender, "/casino bouton poser (console) ou /casino bouton en visant un bloc.");
                        }
                    }
                }
            }
            default -> Txt.send(sender, "/casino <generer|tp|bouton [poser]|donner|retirer|solde|recharger>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("arcade")) {
            return arcade == null ? List.of() : arcade.tab(sender, args);
        }
        List<String> options = switch (command.getName()) {
            case "orbitcoins" -> args.length == 1 ? List.of("boutique", "titres", "top", "regles")
                    : args.length == 2 && args[0].equalsIgnoreCase("regles") ? List.of("blackjack", "poker", "mains", "roulette",
                    "crash", "mines", "plinko", "roue", "billard", "jukebox") : List.of();
            case "cj" -> args.length == 1 ? List.of("crash", "mines", "roue", "jukebox", "billard")
                    : args.length == 2 && args[0].equalsIgnoreCase("billard") ? List.of("rejoindre", "quitter")
                    : args.length == 2 && args[0].equalsIgnoreCase("crash") ? List.of("miser", "encaisser", "plus", "auto") : List.of();
            case "table" -> args.length == 1 ? List.of("miser", "tirer", "rester", "doubler", "separer", "parole", "suivre",
                    "relancer", "tapis", "coucher", "quitter", "ecran") : List.of();
            default -> args.length == 1 ? List.of("generer", "tp", "bouton", "donner", "retirer", "solde", "recharger")
                    : args.length == 2 && (args[0].equalsIgnoreCase("donner") || args[0].equalsIgnoreCase("retirer")
                    || args[0].equalsIgnoreCase("solde")) ? null : List.of();
        };
        if (options == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }
}
