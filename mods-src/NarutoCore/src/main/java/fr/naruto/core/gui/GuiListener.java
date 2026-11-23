package fr.naruto.core.gui;

import fr.naruto.core.jutsu.Jutsu;
import fr.naruto.core.jutsu.JutsuItem;
import fr.naruto.core.jutsu.JutsuService;
import fr.naruto.core.ninja.Clan;
import fr.naruto.core.ninja.Element;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.text.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Gere les clics dans les menus NarutoCore. */
public final class GuiListener implements Listener {

    private final NinjaService ninjas;
    private final JutsuService jutsus;

    public GuiListener(NinjaService ninjas, JutsuService jutsus) {
        this.ninjas = ninjas;
        this.jutsus = jutsus;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NarutoHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String id = holder.actions().get(event.getRawSlot());
        if (id == null) return;

        Ninja ninja = ninjas.get(player.getUniqueId());
        if (ninja == null) return;

        switch (holder.type()) {
            case ELEMENT -> {
                ninja.setElement(Element.byId(id));
                ninjas.save(ninja);
                player.sendMessage(Text.msg("<#FFB347>Affinite <white>" + ninja.element().displayName() + " <#FFB347>choisie."));
                Menus.openClan(player);
            }
            case CLAN -> {
                ninja.setClan(Clan.byId(id));
                ninjas.save(ninja);
                ninjas.updateBar(player, ninja);
                player.closeInventory();
                player.sendMessage(Text.msg("<#FFB347>Clan <white>" + ninja.clan().displayName()
                        + " <#FFB347>(Kekkei Genkai : <white>" + ninja.clan().kekkeiGenkai() + "<#FFB347>). Ouvre <white>/jutsu <#FFB347>!"));
            }
            case JUTSU -> {
                Jutsu j = jutsus.get(id);
                if (j != null) {
                    player.getInventory().addItem(JutsuItem.create(j));
                    player.sendMessage(Text.msg("<#FFB347>Parchemin obtenu : <white>" + j.displayName()
                            + " <dark_gray>(clic droit pour lancer)"));
                }
            }
        }
    }
}
