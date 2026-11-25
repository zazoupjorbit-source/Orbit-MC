package fr.orbit.bornes;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Change le titre de l'écran ouvert sans le refermer (paquet d'ouverture renvoyé avec le même identifiant). */
public final class Retitle {

    private static boolean broken;

    private Retitle() {
    }

    public static boolean send(Player p, Component title) {
        if (broken) {
            return false;
        }
        try {
            ServerPlayer sp = ((CraftPlayer) p).getHandle();
            AbstractContainerMenu menu = sp.containerMenu;
            if (menu == sp.inventoryMenu) {
                return false;
            }
            sp.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), PaperAdventure.asVanilla(title)));
            menu.sendAllDataToRemote();
            return true;
        } catch (Throwable t) {
            broken = true; // version du serveur différente : on rouvre l'écran à la place
            return false;
        }
    }
}
