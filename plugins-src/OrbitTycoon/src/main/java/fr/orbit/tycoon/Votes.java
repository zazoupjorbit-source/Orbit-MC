package fr.orbit.tycoon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Réception des votes (protocole « Votifier », v1 RSA et v2 jeton) : les sites de vote se connectent
 * sur le port votes.port et annoncent « tel joueur a voté ». À chaque vote : +1 Vote Party et la Clé Vote.
 * Joueur hors ligne (ou sur un autre serveur du réseau) ou inventaire plein : la clé est mise en attente
 * dans votes-attente.yml et donnée à sa prochaine connexion au Tycoon. Rien n'est jamais perdu.
 *
 * Fichiers générés au premier démarrage (secrets, ignorés par git) dans plugins/OrbitTycoon/votes/ :
 *   public.key / private.key (v1 : la clé publique se colle sur le site de vote)
 *   jetons.yml (v2 : jeton « default » à coller sur le site de vote)
 */
final class Votes implements Listener {
    private static final short MAGIE_V2 = 0x733A;

    private final OrbitTycoon plugin;
    private final File dossier;
    private final File fichierAttente;
    private final Map<String, Integer> attente = new HashMap<>();
    private final Map<String, String> jetons = new HashMap<>();
    private PrivateKey clePrivee;
    private ServerSocket serveur;

    Votes(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.dossier = new File(plugin.getDataFolder(), "votes");
        this.fichierAttente = new File(plugin.getDataFolder(), "votes-attente.yml");
    }

    // ------------------------------------------------------------------ démarrage / arrêt
    void demarrer() {
        chargerAttente();
        if (!plugin.getConfig().getBoolean("votes.actif", true)) return;
        try {
            chargerCles();
            chargerJetons();
        } catch (Exception e) {
            plugin.getLogger().severe("Votes : clés/jetons illisibles (" + e.getMessage() + "), réception désactivée.");
            return;
        }
        String hote = plugin.getConfig().getString("votes.hote", "0.0.0.0");
        int port = plugin.getConfig().getInt("votes.port", 8192);
        try {
            serveur = new ServerSocket();
            serveur.setReuseAddress(true);
            serveur.bind(new InetSocketAddress(hote, port));
        } catch (IOException e) {
            plugin.getLogger().severe("Votes : impossible d'écouter sur " + hote + ":" + port + " (" + e.getMessage() + ").");
            serveur = null;
            return;
        }
        Thread.ofVirtual().name("OrbitTycoon-Votes").start(this::boucle);
        plugin.getLogger().info("Votes : réception active sur le port " + port + " (Votifier v1 + v2).");
    }

    void arreter() {
        if (serveur != null) {
            try { serveur.close(); } catch (IOException ignored) { }
            serveur = null;
        }
    }

    private void boucle() {
        ServerSocket s = serveur;
        while (s != null && !s.isClosed()) {
            try {
                Socket c = s.accept();
                Thread.ofVirtual().start(() -> traiter(c));
            } catch (IOException e) {
                if (!s.isClosed()) plugin.getLogger().warning("Votes : erreur d'écoute (" + e.getMessage() + ")");
            }
        }
    }

    // ------------------------------------------------------------------ protocole
    private void traiter(Socket c) {
        try (c) {
            c.setSoTimeout(5000);
            String defi = Long.toHexString(new SecureRandom().nextLong());
            OutputStream out = c.getOutputStream();
            out.write(("VOTIFIER 2 " + defi + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = c.getInputStream();
            DataInputStream din = new DataInputStream(in);
            byte[] debut = din.readNBytes(2);
            if (debut.length < 2) return;
            short magie = (short) (((debut[0] & 0xFF) << 8) | (debut[1] & 0xFF));
            if (magie == MAGIE_V2) {
                String reponse;
                try {
                    int taille = din.readUnsignedShort();
                    String json = new String(din.readNBytes(taille), StandardCharsets.UTF_8);
                    voteV2(json, defi);
                    reponse = "{\"status\":\"ok\"}";
                } catch (Exception e) {
                    plugin.getLogger().warning("Votes : vote v2 refusé de " + c.getInetAddress().getHostAddress() + " (" + e.getMessage() + ")");
                    reponse = "{\"status\":\"error\",\"cause\":\"refus\",\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
                }
                out.write((reponse + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } else {
                byte[] bloc = new byte[256];
                bloc[0] = debut[0];
                bloc[1] = debut[1];
                din.readFully(bloc, 2, 254);
                voteV1(bloc);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Votes : connexion refusée (" + e.getMessage() + ")");
        }
    }

    private void voteV1(byte[] bloc) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, clePrivee);
        String[] l = new String(rsa.doFinal(bloc), StandardCharsets.UTF_8).split("\n");
        if (l.length < 3 || !l[0].equals("VOTE")) throw new IllegalArgumentException("bloc v1 invalide");
        recu(l[1], l[2]);
    }

    private void voteV2(String json, String defi) throws Exception {
        JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
        String contenu = msg.get("payload").getAsString();
        byte[] signature = Base64.getDecoder().decode(msg.get("signature").getAsString());
        JsonObject vote = JsonParser.parseString(contenu).getAsJsonObject();
        String site = vote.get("serviceName").getAsString();
        String jeton = jetons.getOrDefault(site, jetons.get("default"));
        if (jeton == null) throw new IllegalArgumentException("aucun jeton pour " + site);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jeton.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        if (!MessageDigest.isEqual(signature, mac.doFinal(contenu.getBytes(StandardCharsets.UTF_8))))
            throw new IllegalArgumentException("signature invalide");
        if (!vote.has("challenge") || !defi.equals(vote.get("challenge").getAsString()))
            throw new IllegalArgumentException("défi invalide");
        recu(site, vote.get("username").getAsString());
    }

    /** Vote validé (thread réseau) : on repasse sur le thread du serveur. */
    private void recu(String site, String pseudo) {
        String p = pseudo.trim();
        if (!p.matches("[A-Za-z0-9_.]{1,16}")) {
            plugin.getLogger().warning("Votes : pseudo invalide reçu de " + site + " : « " + pseudo + " »");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> voter(p, site));
    }

    // ------------------------------------------------------------------ récompense
    /** Un vote de « pseudo » : Vote Party +1 et clé(s) donnée(s) tout de suite ou mise(s) en attente. */
    void voter(String pseudo, String site) {
        int nombre = Math.max(1, plugin.getConfig().getInt("votes.nombre", 1));
        Player j = Bukkit.getPlayerExact(pseudo);
        String nom = j != null ? j.getName() : pseudo;
        attente.merge(nom.toLowerCase(Locale.ROOT), nombre, Integer::sum);
        plugin.getLogger().info("Vote de " + nom + " sur " + site + " (+" + nombre + " clé).");
        if (j != null) donnerAttente(j);
        else sauverAttente();
        if (plugin.getConfig().getBoolean("votes.annonce", true)) {
            String cle = MiniMessageEchap.e(plugin.coffres().nom(numeroCle()));
            for (Player p : Bukkit.getOnlinePlayers())
                OrbitTycoon.msg(p, "<#E0B864>" + MiniMessageEchap.e(nom) + " <gray>a voté sur <white>" + MiniMessageEchap.e(site)
                        + "<gray> et reçoit une <#C77DFF>Clé " + cle + "<gray> ! <#62CFF4><click:run_command:/vote>/vote</click>");
        }
        plugin.voteParty().ajouter(1);
    }

    private int numeroCle() { return Math.max(0, Math.min(5, plugin.getConfig().getInt("votes.cle", 1) - 1)); }

    /** Donne au joueur les clés en attente ; ce qui ne rentre pas dans l'inventaire reste en attente. */
    void donnerAttente(Player j) {
        String id = j.getName().toLowerCase(Locale.ROOT);
        int n = attente.getOrDefault(id, 0);
        if (n <= 0) return;
        int reste = 0;
        for (ItemStack r : j.getInventory().addItem(plugin.coffres().cle(numeroCle(), n)).values()) reste += r.getAmount();
        int donnees = n - reste;
        if (reste > 0) attente.put(id, reste);
        else attente.remove(id);
        sauverAttente();
        String cle = MiniMessageEchap.e(plugin.coffres().nom(numeroCle()));
        if (donnees > 0) {
            OrbitTycoon.msg(j, "<#C77DFF>+" + donnees + " Clé " + cle + "<gray> dans ton inventaire. Merci pour ton vote !");
            j.playSound(j.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        if (reste > 0)
            OrbitTycoon.msg(j, "<red>Inventaire plein : <white>" + reste + "<red> Clé " + cle
                    + " en attente (rien n'est perdu). Fais de la place puis reconnecte-toi pour les recevoir.");
    }

    int enAttente(String pseudo) { return attente.getOrDefault(pseudo.toLowerCase(Locale.ROOT), 0); }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player j = e.getPlayer();
        if (enAttente(j.getName()) > 0)
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (j.isOnline()) donnerAttente(j); }, 60L);
    }

    // ------------------------------------------------------------------ fichiers
    private void chargerAttente() {
        attente.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierAttente);
        for (String k : y.getKeys(false)) if (y.getInt(k) > 0) attente.put(k.toLowerCase(Locale.ROOT), y.getInt(k));
    }

    private void sauverAttente() {
        YamlConfiguration y = new YamlConfiguration();
        attente.forEach(y::set);
        try { y.save(fichierAttente); }
        catch (IOException e) { plugin.getLogger().warning("Votes : impossible d'enregistrer votes-attente.yml (" + e.getMessage() + ")"); }
    }

    private void chargerCles() throws Exception {
        File pub = new File(dossier, "public.key"), priv = new File(dossier, "private.key");
        if (!pub.exists() || !priv.exists()) {
            dossier.mkdirs();
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            Files.writeString(pub.toPath(), Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            Files.writeString(priv.toPath(), Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
            plugin.getLogger().info("Votes : nouvelle paire de clés RSA créée dans votes/ (public.key à donner aux sites de vote v1).");
        }
        byte[] b = Base64.getDecoder().decode(Files.readString(priv.toPath()).trim());
        clePrivee = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(b));
        KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(Files.readString(pub.toPath()).trim())));
    }

    private void chargerJetons() throws IOException {
        File f = new File(dossier, "jetons.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.isString("default")) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            y.set("default", Base64.getUrlEncoder().withoutPadding().encodeToString(b));
            dossier.mkdirs();
            y.save(f);
            plugin.getLogger().info("Votes : jeton v2 « default » créé dans votes/jetons.yml.");
        }
        jetons.clear();
        for (String k : y.getKeys(false)) jetons.put(k, y.getString(k));
    }

    /** Échappe les balises MiniMessage d'un texte venu de l'extérieur (pseudo, nom de site). */
    private static final class MiniMessageEchap {
        static String e(String s) { return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(s); }
    }
}
