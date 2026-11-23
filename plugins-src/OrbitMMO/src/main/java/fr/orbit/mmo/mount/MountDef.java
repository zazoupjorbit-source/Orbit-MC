package fr.orbit.mmo.mount;

import fr.orbit.mmo.classes.Faction;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Une monture de mounts.yml. */
public final class MountDef {

    public enum Anim { NONE, CORPS, PATTE, AILE_G, AILE_D, QUEUE }

    public record Part(String model, double x, double y, double z, Anim anim, double phase) {
    }

    /** Une façon de l'obtenir : toutes les conditions données doivent être vraies. */
    public record Source(String monster, String region, boolean boss, double chance) {

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (boss) {
                sb.append("Boss");
            } else if (monster != null) {
                sb.append("Monstre « ").append(monster).append(" »");
            } else {
                sb.append("Monstres");
            }
            if (region != null) {
                sb.append(" · région ").append(region);
            }
            double pct = chance * 100;
            sb.append(" · ").append(pct >= 1 ? String.format(java.util.Locale.FRANCE, "%.0f", pct)
                    : String.format(java.util.Locale.FRANCE, "%.1f", pct)).append(" %");
            return sb.toString();
        }
    }

    public final String id;
    public String name;
    public String hex = "#FFFFFF";
    public String rarity = "rare";
    public boolean flying;
    /** Monture aquatique (type: eau) : nage vite, plonge, lente à terre. */
    public boolean aquatic;
    public double swimSpeed = 0.6;
    public Faction faction;
    public Material icon = Material.SADDLE;
    public List<String> lore = List.of();
    public double speed = 0.4;
    public double flySpeed = 0.7;
    public double jump = 0.55;
    public float scale = 1.4f;
    public double seat = 1.75;
    public double width = 0.5;
    public final List<Source> sources = new ArrayList<>();
    public final List<Part> parts = new ArrayList<>();

    MountDef(String id) {
        this.id = id;
        this.name = id;
    }

    static MountDef read(String id, ConfigurationSection y) {
        MountDef d = new MountDef(id);
        d.name = y.getString("nom", id);
        d.hex = y.getString("couleur", d.hex);
        d.rarity = y.getString("rarete", d.rarity);
        d.flying = "vol".equalsIgnoreCase(y.getString("type", "sol"));
        d.aquatic = "eau".equalsIgnoreCase(y.getString("type", "sol"));
        d.swimSpeed = y.getDouble("vitesse-nage", d.swimSpeed);
        d.faction = Faction.byId(y.getString("faction"));
        Material m = Material.matchMaterial(y.getString("icone", "SADDLE"));
        d.icon = m == null ? Material.SADDLE : m;
        d.lore = y.getStringList("description");
        d.speed = y.getDouble("vitesse", d.speed);
        d.flySpeed = y.getDouble("vitesse-vol", d.flySpeed);
        d.jump = y.getDouble("saut", d.jump);
        d.scale = (float) y.getDouble("echelle", d.scale);
        d.seat = y.getDouble("selle", d.seat);
        d.width = y.getDouble("largeur", d.width);
        for (Map<?, ?> s : y.getMapList("sources")) {
            d.sources.add(new Source(str(s.get("monstre")), str(s.get("region")),
                    Boolean.TRUE.equals(s.get("boss")), num(s.get("chance"), 0)));
        }
        for (Map<?, ?> p : y.getMapList("pieces")) {
            String model = str(p.get("modele"));
            if (model == null) {
                continue;
            }
            Anim anim = Anim.NONE;
            String a = str(p.get("anim"));
            if (a != null) {
                try {
                    anim = Anim.valueOf(a.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // animation inconnue : pièce fixe
                }
            }
            d.parts.add(new Part(model, num(p.get("x"), 0), num(p.get("y"), 0), num(p.get("z"), 0), anim, num(p.get("phase"), 0)));
        }
        return d;
    }

    public String tag() {
        return "<" + hex + ">" + name + "</" + hex + ">";
    }

    public String typeTag() {
        return flying ? "<#7FD4FF>Volante" : aquatic ? "<#4FC3D9>Aquatique" : "<#C9CED6>Terrestre";
    }

    public String rarityTag() {
        return switch (rarity.toLowerCase(java.util.Locale.ROOT)) {
            case "commune", "commun" -> "<#D7DCE2>Commune</#D7DCE2>";
            case "rare" -> "<#7FB2FF>Rare</#7FB2FF>";
            case "epique", "épique" -> "<#B36BFF>Épique</#B36BFF>";
            case "legendaire", "légendaire" -> "<#FFB347>Légendaire</#FFB347>";
            default -> "<#5EE89A>Peu commune</#5EE89A>";
        };
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return o == null ? def : Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
