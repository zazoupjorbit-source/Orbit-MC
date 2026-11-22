package fr.orbit.extraction.gun;

import fr.orbit.extraction.gun.GunRegistry.AmmoDef;
import fr.orbit.extraction.gun.GunRegistry.GunDef;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Objets décrits en texte (kit, butin, /extraction give) :
 * arme:&lt;id&gt; · munition:&lt;id&gt;:&lt;n|a-b&gt; · grenade:&lt;id&gt;:&lt;n|a-b&gt; · soin:&lt;id&gt;:&lt;n|a-b&gt; ·
 * vehicule:&lt;jerrican|reparation&gt;:&lt;n|a-b&gt; · armure:&lt;tenue&gt;[:&lt;casque|plastron|jambieres|bottes&gt;]
 * (sans pièce : la tenue complète) · objet:&lt;MATERIAU&gt;:&lt;n|a-b&gt;.
 */
public final class ItemSpecs {

    private final GunRegistry reg;
    private final GunItems items;
    private final Random random = new Random();
    private fr.orbit.extraction.loot.Meds meds;

    /** Objets de soin (« soin:bandage:2 ») : branchés après coup, le service a besoin du plugin. */
    public void meds(fr.orbit.extraction.loot.Meds meds) {
        this.meds = meds;
    }

    private fr.orbit.extraction.vehicle.Vehicles vehicles;

    /** Outils de véhicule (« vehicule:jerrican:1 », « vehicule:reparation:1 »). */
    public void vehicles(fr.orbit.extraction.vehicle.Vehicles vehicles) {
        this.vehicles = vehicles;
    }

    private fr.orbit.extraction.armor.Armors armors;

    /** Tenues (« armure:emeute:casque », « armure:emeute » pour les quatre pièces). */
    public void armors(fr.orbit.extraction.armor.Armors armors) {
        this.armors = armors;
    }

    public ItemSpecs(GunRegistry reg, GunItems items) {
        this.reg = reg;
        this.items = items;
    }

    /** Liste vide si la description est invalide. Plusieurs piles si la quantité dépasse une pile. */
    public List<ItemStack> create(String spec, boolean fullMag) {
        List<ItemStack> out = new ArrayList<>();
        if (spec == null) {
            return out;
        }
        String[] p = spec.trim().split(":");
        String kind = p[0].toLowerCase();
        switch (kind) {
            case "arme", "gun" -> {
                GunDef g = p.length > 1 ? reg.gun(p[1]) : null;
                if (g != null) {
                    out.add(items.gun(g, fullMag));
                }
            }
            case "munition", "ammo" -> {
                AmmoDef a = p.length > 1 ? reg.ammo(p[1]) : null;
                if (a != null) {
                    split(out, amount(p.length > 2 ? p[2] : "1"), a.material().getMaxStackSize(), n -> items.ammo(a, n));
                }
            }
            case "grenade" -> {
                GunRegistry.GrenadeDef d = p.length > 1 ? reg.grenade(p[1]) : null;
                if (d != null) {
                    split(out, amount(p.length > 2 ? p[2] : "1"), d.material().getMaxStackSize(), n -> items.grenade(d, n));
                }
            }
            case "soin", "med" -> {
                fr.orbit.extraction.loot.Meds.MedDef d = meds != null && p.length > 1 ? meds.def(p[1]) : null;
                if (d != null) {
                    split(out, amount(p.length > 2 ? p[2] : "1"), d.material().getMaxStackSize(), n -> meds.item(d, n));
                }
            }
            case "vehicule", "vehicle" -> {
                if (vehicles != null && p.length > 1 && vehicles.tool(p[1], 1) != null) {
                    String id = p[1];
                    split(out, amount(p.length > 2 ? p[2] : "1"), vehicles.toolStack(), n -> vehicles.tool(id, n));
                }
            }
            case "armure", "armor", "tenue" -> {
                fr.orbit.extraction.armor.Armors.SetDef s = armors != null && p.length > 1 ? armors.def(p[1]) : null;
                fr.orbit.extraction.armor.Armors.Piece piece = p.length > 2 ? fr.orbit.extraction.armor.Armors.Piece.of(p[2]) : null;
                if (s != null && piece != null) {
                    ItemStack it = armors.item(s, piece);
                    if (it != null) {
                        out.add(it);
                    }
                } else if (s != null && (p.length == 2 || p[2].matches("\\d+"))) {
                    out.addAll(armors.items(s));
                }
            }
            case "objet", "item" -> {
                Material m = p.length > 1 ? Material.matchMaterial(p[1]) : null;
                if (m != null && m.isItem()) {
                    split(out, amount(p.length > 2 ? p[2] : "1"), m.getMaxStackSize(), n -> new ItemStack(m, n));
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static void split(List<ItemStack> out, int total, int stack, java.util.function.IntFunction<ItemStack> make) {
        while (total > 0) {
            int k = Math.min(total, Math.max(1, stack));
            out.add(make.apply(k));
            total -= k;
        }
    }

    private int amount(String s) {
        try {
            int dash = s.indexOf('-');
            if (dash > 0) {
                int a = Integer.parseInt(s.substring(0, dash));
                int b = Integer.parseInt(s.substring(dash + 1));
                return a + random.nextInt(Math.max(1, b - a + 1));
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
