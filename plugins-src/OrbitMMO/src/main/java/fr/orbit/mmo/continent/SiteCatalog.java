package fr.orbit.mmo.continent;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.orbit.mmo.continent.ContinentGenerator.NONE;

/**
 * La liste des sites du continent, tirée de la carte (villages, ville, donjons) et de sites.yml (hameaux, cratères,
 * règles par région). Les camps et les ruines sont placés par calcul : terrain sec et plat, loin des routes et des
 * lieux. Leur emplacement est ensuite figé dans sites-places.yml, pour qu'un changement de règle ne déplace jamais un
 * site déjà construit.
 */
final class SiteCatalog {

    record Theme(String id, String name, List<String> mobs) {
    }

    record RegionRule(String id, String style, int camps, int ruins, List<String> themes, List<String> fauna, List<String> haunts) {
    }

    private static final Pattern FLOOR = Pattern.compile("\\s*\\(étage (\\d+)\\)\\s*$");
    private static final String[][] RUINS = {{"tour", "Tour effondrée"}, {"cercle", "Cercle de pierres"}, {"chapelle", "Chapelle en ruine"}};

    final Map<String, SiteDesign.Site> sites = new LinkedHashMap<>();
    final Map<String, RegionRule> regions = new TreeMap<>();
    final Map<String, Theme> themes = new LinkedHashMap<>();
    final List<String> notes = new ArrayList<>();
    /** Emplacements calculés à ce chargement, à figer : id → type, x, z, thème. */
    final Map<String, String[]> computed = new LinkedHashMap<>();
    final int budgetMs;
    final int perPlayer, cap, perCamp, perRuin, levelSpan, guardReturn;

    private final ContinentData data;
    private final ContinentGenerator gen;
    private final ContinentGenerator.Column col = new ContinentGenerator.Column();
    private final ContinentData.RoadHit hit = new ContinentData.RoadHit();

    private SiteCatalog(ContinentData data, ContinentGenerator gen, ConfigurationSection cfg) {
        this.data = data;
        this.gen = gen;
        budgetMs = Math.max(2, Math.min(40, cfg.getInt("budget-ms", 12)));
        perPlayer = Math.max(0, cfg.getInt("faune.par-joueur", 5));
        cap = Math.max(0, cfg.getInt("faune.plafond", 160));
        perCamp = Math.max(0, cfg.getInt("faune.par-camp", 5));
        perRuin = Math.max(0, cfg.getInt("faune.par-ruine", 3));
        levelSpan = Math.max(100, cfg.getInt("faune.distance-niveau", 700));
        guardReturn = Math.max(2, cfg.getInt("faune.retour-gardien", 30));
    }

    /**
     * @param cfg    sites.yml
     * @param frozen sites-places.yml (emplacements déjà figés), ou null
     */
    static SiteCatalog load(ContinentData data, ContinentGenerator gen, ConfigurationSection cfg, ConfigurationSection frozen) {
        SiteCatalog c = new SiteCatalog(data, gen, cfg);
        c.rules(cfg);
        c.places(cfg);
        c.hamlets(cfg.getConfigurationSection("hameaux"));
        c.craters(cfg.getConfigurationSection("crateres"), frozen);
        c.scatter(frozen);
        c.overlaps();
        return c;
    }

    /** Deux sites dont les emprises se recouvrent se réécrivent l'un l'autre : on le signale. */
    private void overlaps() {
        List<SiteDesign.Site> all = new ArrayList<>(sites.values());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                SiteDesign.Site a = all.get(i), b = all.get(j);
                double d = Math.hypot(a.x() - b.x(), a.z() - b.z());
                // + 2 : marge du plan autour de l'emprise.
                int reach = a.outer() + b.outer() + 4;
                if (d < reach) {
                    notes.add(a.id() + " et " + b.id() + " se recouvrent de " + (int) Math.ceil(reach - d)
                            + " blocs : réduire le « raccord » de l'un des deux dans sites.yml");
                }
            }
        }
    }

    SiteDesign.Site site(String id) {
        return sites.get(id);
    }

    // ------------------------------------------------------------------ Règles
    private void rules(ConfigurationSection cfg) {
        ConfigurationSection ts = cfg.getConfigurationSection("themes");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                themes.put(id, new Theme(id, ts.getString(id + ".nom", id), ts.getStringList(id + ".monstres")));
            }
        }
        ConfigurationSection rs = cfg.getConfigurationSection("regions");
        for (ContinentData.Region r : data.regions) {
            String k = r.id();
            String style = rs == null ? null : rs.getString(k + ".style");
            if (style == null) {
                style = r.faction().equals("ombre") ? "ombre" : (r.faction().equals("lumiere") ? "lumiere" : "neutre");
            }
            regions.put(k, new RegionRule(k, style, rs == null ? 0 : rs.getInt(k + ".camps", 0), rs == null ? 0 : rs.getInt(k + ".ruines", 0),
                    rs == null ? List.of() : rs.getStringList(k + ".themes"), rs == null ? List.of() : rs.getStringList(k + ".faune"),
                    rs == null ? List.of() : rs.getStringList(k + ".hantes")));
        }
    }

    private String styleAt(int x, int z, String fallback) {
        ContinentData.Region r = data.region(x, z);
        RegionRule rule = r == null ? null : regions.get(r.id());
        return rule == null ? fallback : rule.style();
    }

    private static long seed(String id) {
        return (id.hashCode() & 0x7FFFFFFFL) % 100_000 + 1;
    }

    /** Sites ajoutés à la carte (hameaux, cratères) : les camps et les ruines s'en tiennent à l'écart. */
    private final List<SiteDesign.Site> extras = new ArrayList<>();

    private void add(SiteDesign.Site s) {
        if (sites.containsKey(s.id())) {
            notes.add("site en double ignoré : " + s.id());
            return;
        }
        sites.put(s.id(), s);
        if (s.kind().equals("crateres") || (s.kind().equals("village") && data.placeAt(s.x(), s.z()) == null)) {
            extras.add(s);
        }
    }

    // ------------------------------------------------------------------ Lieux de la carte
    private void places(ConfigurationSection cfg) {
        ConfigurationSection vs = cfg.getConfigurationSection("villages"), ds = cfg.getConfigurationSection("donjons");
        for (ContinentData.Place p : data.places) {
            ContinentData.Region r = data.region(p.x(), p.z());
            int lo = r == null ? 1 : r.levelMin(), hi = r == null ? 1 : r.levelMax();
            double aim = Math.atan2(-p.z(), -p.x());
            if (p.kind().equals("village") || p.kind().equals("ville")) {
                String id = ContinentData.plain(p.name());
                int radius = get(vs, id, "rayon", 36);
                radius = Math.max(20, Math.min(radius, (int) Math.round(p.safe() * 0.55)));
                List<String> services = vs != null && vs.isList(id + ".services") ? vs.getStringList(id + ".services")
                        : (vs == null ? List.of("auberge", "forge", "alchimie") : vs.getStringList("defaut.services"));
                String style = vs == null ? null : vs.getString(id + ".style");
                add(new SiteDesign.Site(id, "village", p.name(), p.faction(), p.x(), p.z(), radius, style != null ? style : styleAt(p.x(), p.z(), "neutre"),
                        "", seed(id), p.safe(), 0, "", aim, get(vs, id, "raccord", 0), services, List.of(), lo, hi));
            } else if (p.kind().equals("donjon")) {
                Matcher m = FLOOR.matcher(p.name());
                int floor = 0;
                String name = p.name();
                if (m.find()) {
                    floor = Integer.parseInt(m.group(1));
                    name = name.substring(0, m.start());
                }
                String id = ContinentData.plain(name);
                String style = ds == null ? null : ds.getString(id + ".style");
                if (ds != null && ds.contains(id + ".etage")) {
                    floor = ds.getInt(id + ".etage");
                }
                add(new SiteDesign.Site(id, "donjon", name, p.faction(), p.x(), p.z(), Math.max(10, Math.min(20, get(ds, id, "rayon", 13))),
                        style != null ? style : styleAt(p.x(), p.z(), "neutre"), "", seed(id), 0, floor, "", aim, get(ds, id, "raccord", 0),
                        List.of(), List.of(), lo, hi));
            }
        }
    }

    private static int get(ConfigurationSection s, String id, String key, int fallback) {
        if (s == null) {
            return fallback;
        }
        return s.getInt(id + "." + key, s.getInt("defaut." + key, fallback));
    }

    private void hamlets(ConfigurationSection hs) {
        if (hs == null) {
            return;
        }
        for (String id : hs.getKeys(false)) {
            ConfigurationSection h = hs.getConfigurationSection(id);
            if (h == null || !h.contains("x") || !h.contains("z")) {
                notes.add("hameau « " + id + " » ignoré : x et z manquent");
                continue;
            }
            int x = h.getInt("x"), z = h.getInt("z");
            ContinentData.Place clash = data.placeAt(x, z);
            if (clash != null) {
                notes.add("hameau « " + id + " » ignoré : il tombe dans la zone sûre de " + clash.name());
                continue;
            }
            ContinentData.Region r = data.region(x, z);
            int radius = Math.max(20, Math.min(60, h.getInt("rayon", 30)));
            add(new SiteDesign.Site(id, "village", h.getString("nom", id), h.getString("faction", r == null ? "neutre" : r.faction()), x, z, radius,
                    h.getString("style", styleAt(x, z, "neutre")), "", seed(id), Math.max(radius + 10, h.getInt("sur", radius * 2)), 0,
                    h.getString("quete", ""), Math.atan2(-z, -x), h.getInt("raccord", 0), h.getStringList("services"), List.of(),
                    r == null ? 1 : r.levelMin(), r == null ? 1 : r.levelMax()));
        }
    }

    // ------------------------------------------------------------------ Terrain
    /** Dénivelé du terrain dans un rayon donné, ou -1 s'il y a de l'eau, de la lave, une plage ou une route. */
    private int relief(int x, int z, int radius, int roadMargin) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int ring = 0; ring <= 2; ring++) {
            double rr = radius * ring / 2.0;
            int n = ring == 0 ? 1 : 8 * ring;
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n;
                int px = x + (int) Math.round(Math.cos(a) * rr), pz = z + (int) Math.round(Math.sin(a) * rr);
                if (!data.inside(px, pz)) {
                    return -1;
                }
                int biome = data.biome(px, pz);
                if (biome == ContinentData.OCEAN || biome == ContinentData.WATER || biome == ContinentData.BEACH) {
                    return -1;
                }
                gen.column(px, pz, false, hit, col);
                if (col.fluid != NONE || data.road(px, pz, roadMargin, hit)) {
                    return -1;
                }
                lo = Math.min(lo, col.top);
                hi = Math.max(hi, col.top);
            }
        }
        return hi - lo;
    }

    /** Assez loin de tous les lieux de la carte et des sites déjà inscrits. */
    private boolean apart(int x, int z, int margin) {
        for (ContinentData.Place p : data.places) {
            if (Math.hypot(x - p.x(), z - p.z()) < Math.max(p.safe(), 40) + margin) {
                return false;
            }
        }
        for (SiteDesign.Site s : extras) {
            if (Math.hypot(x - s.x(), z - s.z()) < Math.max(s.safe(), s.outer()) + margin) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Cratères
    private void craters(ConfigurationSection cs, ConfigurationSection frozen) {
        if (cs == null) {
            return;
        }
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            SiteDesign.Site near = c == null ? null : sites.get(c.getString("pres", ""));
            if (c == null || near == null) {
                notes.add("cratères « " + id + " » ignorés : le hameau « pres » est inconnu");
                continue;
            }
            int radius = Math.max(24, Math.min(48, c.getInt("rayon", 32)));
            int x, z;
            if (frozen != null && frozen.contains(id + ".x")) {
                x = frozen.getInt(id + ".x");
                z = frozen.getInt(id + ".z");
            } else {
                List<Integer> range = c.getIntegerList("distance");
                int d0 = range.size() == 2 ? range.get(0) : 130, d1 = range.size() == 2 ? range.get(1) : 190;
                int best = Integer.MAX_VALUE, bx = 0, bz = 0;
                for (int d = d0; d <= d1; d += 10) {
                    for (int deg = 0; deg < 360; deg += 10) {
                        int px = near.x() + (int) Math.round(Math.cos(Math.toRadians(deg)) * d), pz = near.z() + (int) Math.round(Math.sin(Math.toRadians(deg)) * d);
                        int rel = relief(px, pz, radius + 6, 14);
                        if (rel < 0 || rel >= best || !apart(px, pz, radius + 20)) {
                            continue;
                        }
                        best = rel;
                        bx = px;
                        bz = pz;
                    }
                }
                if (best == Integer.MAX_VALUE) {
                    notes.add("cratères « " + id + " » : aucun terrain sec et plat près de " + near.name());
                    continue;
                }
                x = bx;
                z = bz;
                computed.put(id, new String[]{"crateres", String.valueOf(x), String.valueOf(z), ""});
            }
            ContinentData.Region r = data.region(x, z);
            add(new SiteDesign.Site(id, "crateres", c.getString("nom", id), c.getString("faction", near.faction()), x, z, radius,
                    c.getString("style", styleAt(x, z, "lumiere")), "", seed(id), 0, 0, c.getString("quete", ""),
                    Math.atan2(near.z() - z, near.x() - x), 0, List.of(), List.of(), r == null ? 1 : r.levelMin(), r == null ? 1 : r.levelMax()));
        }
    }

    // ------------------------------------------------------------------ Camps et ruines
    private void scatter(ConfigurationSection frozen) {
        boolean known = false;
        if (frozen != null) {
            for (String id : frozen.getKeys(false)) {
                String type = frozen.getString(id + ".type", "");
                if (type.equals("camp") || type.equals("ruine")) {
                    known = true;
                    small(id, type, frozen.getInt(id + ".x"), frozen.getInt(id + ".z"), frozen.getString(id + ".theme", ""));
                }
            }
        }
        if (known) {
            return;
        }
        int half = data.size / 2, cell = 260;
        Map<String, List<int[]>> byRegion = new TreeMap<>();
        for (int gz = -half + cell / 2; gz < half; gz += cell) {
            for (int gx = -half + cell / 2; gx < half; gx += cell) {
                for (int attempt = 0; attempt < 6; attempt++) {
                    int x = gx + (int) ((ContinentGenerator.rnd(gx, gz, 900 + attempt * 2) - 0.5) * 190);
                    int z = gz + (int) ((ContinentGenerator.rnd(gx, gz, 901 + attempt * 2) - 0.5) * 190);
                    ContinentData.Region r = data.region(x, z);
                    if (r == null || !regions.containsKey(r.id()) || Math.abs(x) > half - 60 || Math.abs(z) > half - 60) {
                        continue;
                    }
                    int rel = relief(x, z, 14, 20);
                    if (rel < 0 || rel > 3 || !apart(x, z, 90)) {
                        continue;
                    }
                    byRegion.computeIfAbsent(r.id(), k -> new ArrayList<>()).add(new int[]{x, z});
                    break;
                }
            }
        }
        List<int[]> taken = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : byRegion.entrySet()) {
            RegionRule rule = regions.get(e.getKey());
            List<int[]> list = e.getValue();
            list.sort(Comparator.comparingDouble(p -> ContinentGenerator.rnd(p[0], p[1], 950)));
            int camps = 0, ruins = 0, n = 0;
            String low = e.getKey().toLowerCase(java.util.Locale.ROOT);
            for (int[] p : list) {
                if (camps >= rule.camps() && ruins >= rule.ruins()) {
                    break;
                }
                boolean free = true;
                for (int[] t : taken) {
                    if (Math.hypot(p[0] - t[0], p[1] - t[1]) < 220) {
                        free = false;
                        break;
                    }
                }
                if (!free) {
                    continue;
                }
                taken.add(p);
                // Une ruine toutes les trois places, tant qu'il en reste à poser.
                boolean ruin = ruins < rule.ruins() && (camps >= rule.camps() || rule.themes().isEmpty() || n % 3 == 2);
                n++;
                String id, theme;
                if (ruin) {
                    theme = RUINS[ruins % RUINS.length][0];
                    id = "ruine-" + low + (++ruins);
                } else {
                    theme = rule.themes().get(camps % rule.themes().size());
                    id = "camp-" + low + (++camps);
                }
                computed.put(id, new String[]{ruin ? "ruine" : "camp", String.valueOf(p[0]), String.valueOf(p[1]), theme});
                small(id, ruin ? "ruine" : "camp", p[0], p[1], theme);
            }
            if (camps < rule.camps() || ruins < rule.ruins()) {
                notes.add("région " + e.getKey() + " : " + camps + " camps sur " + rule.camps() + " et " + ruins + " ruines sur " + rule.ruins()
                        + " (pas assez de terrain plat et sec)");
            }
        }
    }

    private void small(String id, String type, int x, int z, String theme) {
        ContinentData.Region r = data.region(x, z);
        RegionRule rule = r == null ? null : regions.get(r.id());
        String name;
        List<String> mobs;
        if (type.equals("ruine")) {
            name = RUINS[0][1];
            for (String[] ru : RUINS) {
                if (ru[0].equals(theme)) {
                    name = ru[1];
                }
            }
            mobs = rule == null ? List.of() : rule.haunts();
        } else {
            Theme th = themes.get(theme);
            name = th == null ? "Camp" : th.name();
            mobs = th == null ? List.of() : th.mobs();
        }
        add(new SiteDesign.Site(id, type, name, r == null ? "neutre" : r.faction(), x, z, type.equals("ruine") ? 11 : 12,
                rule == null ? "neutre" : rule.style(), theme, seed(id), 0, 0, "", 0, 0, List.of(), mobs, r == null ? 1 : r.levelMin(),
                r == null ? 1 : r.levelMax()));
    }
}
