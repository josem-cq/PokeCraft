package com.jose.pokecraft.ai;
 
import net.minecraft.network.chat.Component;
 
/**
 * Modos de comportamiento de un mob capturado/liberado.
 *
 * IMPORTANTE: el id (ordinal) se guarda en el NBT persistente bajo "MobOrderState".
 * Los tres primeros mantienen los ids antiguos (0 = seguir, 1 = quieto, 2 = proteger), así que los mobs
 * ya guardados en mundos existentes siguen funcionando. No reordenes los valores existentes; añade nuevos al final.
 * El orden del ciclo con el palo es el orden de declaración.
 */
public enum MobOrder {
    /** Sigue al dueño. No reacciona a nada. */
    FOLLOW("follow", true, false),
    /** Anclado en su sitio. No reacciona a nada. */
    STAY("stay", false, false),
    /** Sigue al dueño y defiende (a sí mismo y al dueño). */
    PROTECT("protect", true, true),
    /** Ataque: busca y ataca a cualquier cosa que no sea el dueño ni sus aliados. */
    ATTACK("attack", false, true),
    /** Libre: IA vanilla completa (sin seguir ni teletransportarse); solo evita al dueño y a sus aliados. */
    FREE("free", false, true);
 
    private final String key;
    private final boolean followsOwner;
    private final boolean allowsTargeting;
 
    MobOrder(String key, boolean followsOwner, boolean allowsTargeting) {
        this.key = key;
        this.followsOwner = followsOwner;
        this.allowsTargeting = allowsTargeting;
    }
 
    public boolean followsOwner() {
        return followsOwner;
    }
 
    public boolean allowsTargeting() {
        return allowsTargeting;
    }
 
    public MobOrder next() {
        MobOrder[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
 
    public Component displayName() {
        return Component.translatable("pokecraft.order." + key);
    }
 
    public static MobOrder byId(int id) {
        MobOrder[] all = values();
        return id >= 0 && id < all.length ? all[id] : FOLLOW;
    }
}
 