package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lien avec les plugins de mode, sans dépendance de compilation : un plugin qui veut apparaître dans le
 * catalogue et le livre ajoute à sa classe principale l'une ou l'autre de ces méthodes publiques :
 *
 * <pre>
 *   public Map&lt;String, List&lt;ItemStack&gt;&gt; orbitCatalogue()   // catégorie → objets (ordre gardé)
 *   public List&lt;String&gt; orbitStatsJoueur(Player p)              // lignes MiniMessage pour le livre
 * </pre>
 */
public final class Ponts {

    private final OrbitStaff plugin;

    public Ponts(OrbitStaff plugin) {
        this.plugin = plugin;
    }

    private static Method methode(Plugin p, String nom, Class<?>... params) {
        try {
            return p.getClass().getMethod(nom, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** « Plugin › catégorie » → objets, pour tous les plugins de ce serveur qui en fournissent. */
    @SuppressWarnings("unchecked")
    public Map<String, List<ItemStack>> catalogue() {
        Map<String, List<ItemStack>> out = new LinkedHashMap<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            Method m = p.isEnabled() ? methode(p, "orbitCatalogue") : null;
            if (m == null) {
                continue;
            }
            try {
                Object r = m.invoke(p);
                if (r instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        List<ItemStack> items = new ArrayList<>();
                        if (e.getValue() instanceof List<?> l) {
                            for (Object o : l) {
                                if (o instanceof ItemStack it && !it.isEmpty()) {
                                    items.add(it.clone());
                                }
                            }
                        }
                        if (!items.isEmpty()) {
                            out.put(p.getName() + " › " + e.getKey(), items);
                        }
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Catalogue de " + p.getName() + " illisible : " + t);
            }
        }
        return out;
    }

    /** Lignes de stats du mode (plugin → lignes) pour un joueur en ligne ici. */
    public Map<String, List<String>> stats(Player joueur) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            Method m = p.isEnabled() ? methode(p, "orbitStatsJoueur", Player.class) : null;
            if (m == null) {
                continue;
            }
            try {
                Object r = m.invoke(p, joueur);
                if (r instanceof List<?> l && !l.isEmpty()) {
                    List<String> lignes = new ArrayList<>();
                    for (Object o : l) {
                        lignes.add(String.valueOf(o));
                    }
                    out.put(p.getName(), lignes);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Stats de " + p.getName() + " illisibles : " + t);
            }
        }
        return out;
    }
}
