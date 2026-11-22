package fr.orbit.extraction.gun;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.gun.GunRegistry.AmmoDef;
import fr.orbit.extraction.gun.GunRegistry.GrenadeDef;
import fr.orbit.extraction.gun.GunRegistry.GunDef;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Création et lecture des items d'armes et de munitions. */
public final class GunItems {

    private static final String MODEL_NS = "orbitguns:";

    private final GunRegistry reg;
    private final MagStore mags;

    public GunItems(GunRegistry reg, MagStore mags) {
        this.reg = reg;
        this.mags = mags;
    }

    public ItemStack gun(GunDef g, boolean fullMag) {
        ItemStack it = new ItemStack(g.material());
        String uid = UUID.randomUUID().toString();
        AmmoDef a = reg.ammo(g.ammo());
        it.editMeta(m -> {
            m.displayName(Text.item(g.name()));
            List<String> lore = new ArrayList<>(g.lore());
            lore.add("");
            lore.add("<#8A919B>Munition : " + (a == null ? g.ammo() : a.name()));
            lore.add("<#8A919B>Chargeur : <#D7DCE2>" + g.mag() + "</#D7DCE2>  •  Dégâts : <#D7DCE2>"
                    + (g.explosive() ? fmt(g.blastDamage() / 2) + " ❤ (zone " + fmt(g.blastRadius()) + ")"
                    : fmt(g.damage() / 2) + " ❤" + (g.pellets() > 1 ? " x" + g.pellets() : "")) + "</#D7DCE2>"
                    + (g.auto() ? "  •  <#E67E22>Auto" : ""));
            lore.add("<#5A6270>Clic droit : tirer  •  Clic gauche : viser  •  F : recharger");
            m.lore(Text.lore(lore));
            m.getPersistentDataContainer().set(Keys.GUN, PersistentDataType.STRING, g.id());
            m.getPersistentDataContainer().set(Keys.GUN_UID, PersistentDataType.STRING, uid);
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        });
        model(it, g.id());
        mags.set(uid, fullMag ? g.mag() : 0);
        return it;
    }

    public ItemStack grenade(GrenadeDef d, int amount) {
        ItemStack it = new ItemStack(d.material(), Math.max(1, Math.min(amount, d.material().getMaxStackSize())));
        it.editMeta(m -> {
            m.displayName(Text.item(d.name()));
            m.lore(Text.lore(List.of(
                    "<#8A919B>Dégâts : <#D7DCE2>" + fmt(d.damage() / 2) + " ❤</#D7DCE2> (zone " + fmt(d.radius()) + " blocs)",
                    "<#C0392B>Blesse aussi les joueurs — toi compris.",
                    "<#5A6270>Clic droit : lancer")));
            m.getPersistentDataContainer().set(Keys.GRENADE, PersistentDataType.STRING, d.id());
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        model(it, d.id());
        return it;
    }

    public GrenadeDef grenadeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return reg.grenade(it.getItemMeta().getPersistentDataContainer().get(Keys.GRENADE, PersistentDataType.STRING));
    }

    public ItemStack ammo(AmmoDef a, int amount) {
        ItemStack it = new ItemStack(a.material(), Math.max(1, Math.min(amount, a.material().getMaxStackSize())));
        it.editMeta(m -> {
            m.displayName(Text.item(a.name()));
            m.lore(Text.lore(List.of("<#5A6270>Munitions")));
            m.getPersistentDataContainer().set(Keys.AMMO, PersistentDataType.STRING, a.id());
        });
        model(it, AMMO_MODEL + a.id());
        return it;
    }

    /** Munitions créées avant l'arrivée de leur modèle : on le leur ajoute. Renvoie vrai si la pile a été modifiée. */
    public boolean ensureAmmoModel(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return false;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(Keys.AMMO, PersistentDataType.STRING);
        if (id == null) {
            return false;
        }
        CustomModelData cmd = it.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd != null && cmd.strings().contains(MODEL_NS + AMMO_MODEL + id)) {
            return false;
        }
        model(it, AMMO_MODEL + id);
        return true;
    }

    /** Préfixe des modèles de munitions dans le pack (« orbitguns:ammo_9mm »). */
    private static final String AMMO_MODEL = "ammo_";

    /** Ajoute n munitions dans l'inventaire (plusieurs piles si besoin), le surplus tombe au sol. */
    public void giveAmmo(Player p, AmmoDef a, int n) {
        int stack = a.material().getMaxStackSize();
        while (n > 0) {
            int k = Math.min(n, stack);
            give(p, ammo(a, k));
            n -= k;
        }
    }

    public static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    /** Modèle 3D du pack : choisi par la donnée de modèle « orbitguns:<id> » ; sans le pack, l'objet garde son apparence normale. */
    private static void model(ItemStack it, String id) {
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(MODEL_NS + id).build());
    }

    /** Arme créée avant l'arrivée du pack : on lui ajoute son modèle. Renvoie vrai si l'objet a été modifié. */
    public boolean ensureModel(ItemStack it, GunDef g) {
        CustomModelData cmd = it.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd != null && cmd.strings().contains(MODEL_NS + g.id())) {
            return false;
        }
        model(it, g.id());
        return true;
    }

    public GunDef gunOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return reg.gun(it.getItemMeta().getPersistentDataContainer().get(Keys.GUN, PersistentDataType.STRING));
    }

    public String uidOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(Keys.GUN_UID, PersistentDataType.STRING);
    }

    public String ammoOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(Keys.AMMO, PersistentDataType.STRING);
    }

    /** Munitions de ce type dans l'inventaire principal (hors armure et seconde main). */
    public int reserve(Player p, String ammoId) {
        PlayerInventory inv = p.getInventory();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (ammoId.equals(ammoOf(it))) {
                total += it.getAmount();
            }
        }
        return total;
    }

    /** Retire jusqu'à n munitions de l'inventaire ; renvoie le nombre réellement retiré. */
    public int takeAmmo(Player p, String ammoId, int n) {
        PlayerInventory inv = p.getInventory();
        int taken = 0;
        for (int i = 0; i < 36 && taken < n; i++) {
            ItemStack it = inv.getItem(i);
            if (!ammoId.equals(ammoOf(it))) {
                continue;
            }
            int k = Math.min(n - taken, it.getAmount());
            taken += k;
            if (k >= it.getAmount()) {
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - k);
                inv.setItem(i, it);
            }
        }
        return taken;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',');
    }
}
