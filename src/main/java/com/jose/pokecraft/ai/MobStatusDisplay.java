package com.jose.pokecraft.ai;

import com.jose.pokecraft.network.ModeStatusPayload;
import com.jose.pokecraft.pokedex.MobStrengthManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Único punto que construye y envía los mensajes de estado de un aliado capturado al usar el palo.
 *
 * Todos van en DOS líneas independientes (ver ModeStatusOverlay en el cliente):
 *  - línea 1: el modo ("[pepito] ahora está siguiendo" / "[pepito] está protegiendo");
 *  - línea 2: el estado PVP/PVE, debajo del modo.
 * Como cada línea se construye por separado, cambiar el modo nunca borra el estado PVP/PVE y alternar PVP/PVE nunca
 * borra el texto del modo.
 *
 * También ofrece {@link #pvpLabel(boolean)}, que usan los tooltips de bolas y tarros ("Modo: siguiendo, PVE").
 */
public final class MobStatusDisplay {
    private MobStatusDisplay() {}

    /** "PVE" (verde) o "PVP" (rojo), traducible. */
    public static MutableComponent pvpLabel(boolean pvp) {
        return Component.translatable(pvp ? "pokecraft.pvp.pvp" : "pokecraft.pvp.pve")
                .withStyle(pvp ? ChatFormatting.RED : ChatFormatting.GREEN);
    }

    /** Segunda línea de estado: "Estado: PVE". */
    private static Component stateLine(boolean pvp) {
        return Component.translatable("message.pokecraft.pvp_state", pvpLabel(pvp)).withStyle(ChatFormatting.GRAY);
    }

    /** Nombre con el color de su categoría de fortaleza (respeta el nombre personalizado si lo tiene). */
    private static Component name(Mob mob) {
        return MobStrengthManager.coloredName(mob, mob.hasCustomName() ? mob.getCustomName() : mob.getDisplayName());
    }

    /** Nombre del modo actual (o "en modo venganza"). */
    private static Component modeName(Mob mob) {
        return MobOrderManager.isVengeance(mob)
                ? Component.translatable("pokecraft.order.vengeance")
                : MobOrderManager.getOrder(mob).displayName();
    }

    /** Tras cambiar el modo con el palo: "[X] ahora está siguiendo" + "Estado: PVE". */
    public static void showOrderChanged(ServerPlayer player, Mob mob, MobOrder order) {
        send(player,
                Component.translatable("message.pokecraft.order_changed", name(mob), order.displayName()),
                stateLine(MobOrderManager.isPvp(mob)));
    }

    /** Al apuntar con el palo (pulso único): "[X] está siguiendo" + "Estado: PVE". */
    public static void showLook(ServerPlayer player, Mob mob) {
        send(player,
                Component.translatable("message.pokecraft.order_look", name(mob), modeName(mob)),
                stateLine(MobOrderManager.isPvp(mob)));
    }

    /** Tras alternar PVP/PVE con clic izquierdo: el modo se mantiene y debajo aparece el estado nuevo. */
    public static void showPvpToggled(ServerPlayer player, Mob mob, boolean pvp) {
        send(player,
                Component.translatable("message.pokecraft.order_look", name(mob), modeName(mob)),
                Component.translatable("message.pokecraft.pvp_changed", pvpLabel(pvp)).withStyle(ChatFormatting.GRAY));
    }

    private static void send(ServerPlayer player, Component modeLine, Component stateLine) {
        PacketDistributor.sendToPlayer(player, new ModeStatusPayload(modeLine, stateLine));
    }
}