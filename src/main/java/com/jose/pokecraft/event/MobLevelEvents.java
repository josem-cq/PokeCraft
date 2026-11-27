package com.jose.pokecraft.event;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.MobOrderManager;
import com.jose.pokecraft.item.XpTreatItem;
import com.jose.pokecraft.level.MobLevelData;
import com.jose.pokecraft.level.MobLevelManager;
import com.jose.pokecraft.pokedex.CaptureRules;
import com.jose.pokecraft.pokedex.MobStrengthManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Eventos del sistema de niveles: reaplicar el nivel al cargar un aliado, XP por muertes cercanas, bola de reserva ante
 * un golpe letal, poción permanente y entrega de la golosina de XP.
 */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class MobLevelEvents {

    /** Los atributos y las metas no se reponen solos al cargar el mob (chunk, reinicio, liberación desde la bola). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob && MobOrderManager.isFriendly(mob)) {
            MobLevelManager.refresh(mob);
        }
    }

    /**
     * Bola de reserva. Prioridad ALTA y cancelando: la PokéDex y la XP (que no reciben eventos cancelados) no cuentan
     * una muerte que no ocurre. El tótem de la mano secundaria lo consume el motor ANTES de lanzar este evento, así que
     * si llega aquí es que no había tótem (o ya se gastó).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onReserveDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        if (MobLevelManager.tryReserveCapture(mob)) {
            event.setCanceled(true);
        }
    }

    /** XP por kills: prioridad mínima para contar solo muertes que de verdad ocurren. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKillXp(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        MobLevelManager.awardKillXp(dead, event.getSource().getEntity());
    }

    /** Renueva el efecto de la poción permanente cada 40 ticks (solo se mira el tickCount primero: es barato). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && mob.tickCount % 40 == 0
                && !mob.level().isClientSide() && MobOrderManager.isFriendly(mob)) {
            MobLevelManager.tickPotion(mob);
        }
    }

    /**
     * Clic derecho con la golosina de XP sobre un aliado propio: suma la XP de 1 nivel a su barra. Prioridad baja y
     * sin recibir eventos cancelados: Shift (recoger al mob) y las órdenes del palo se resuelven antes en ModEventHandlers.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFeedTreat(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof XpTreatItem)) return;

        LivingEntity target = CaptureRules.resolveTarget(event.getTarget());
        if (!(target instanceof Mob mob) || !MobOrderManager.isFriendly(mob) || MobOrderManager.isVengeance(mob)) return;
        Player player = event.getEntity();
        if (!player.getUUID().equals(MobOrderManager.getOwnerId(mob)) || player.isShiftKeyDown()) return;

        MobLevelData data = MobLevelManager.readLive(mob);
        double perLevel = MobLevelManager.xpPerLevel(mob.getType(), mob.level());
        Component name = MobStrengthManager.coloredName(mob, mob.hasCustomName() ? mob.getCustomName() : mob.getDisplayName());

        if (!data.giveLevelOfXp(perLevel)) {
            player.displayClientMessage(Component.translatable("message.pokecraft.xp_treat.max", name), true);
        } else {
            MobLevelManager.writeLive(mob, data);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY(0.7D), mob.getZ(), 8, 0.4D, 0.4D, 0.4D, 0.0D);
                level.playSound(null, mob.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.0F);
            }
            player.displayClientMessage(Component.translatable("message.pokecraft.xp_treat.fed", name, data.available), true);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
