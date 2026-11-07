package com.jose.pokecraft.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class MobJarBlockItem extends BlockItem {
    public MobJarBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag entityTag = tag.contains("CapturedEntity") ? tag.getCompound("CapturedEntity") : tag;

            if (entityTag.contains("id")) {
                tooltipComponents.add(Component.literal("§8-----------------"));

                // 1. Nombre legible del mob o nombre personalizado
                Component mobName = Component.literal("Desconocido");
                EntityType<?> rawType = null;

                if (entityTag.contains("CustomName", 8)) {
                    try {
                        mobName = Component.Serializer.fromJson(entityTag.getString("CustomName"), context.registries());
                    } catch (Exception e) {
                        mobName = Component.literal(entityTag.getString("CustomName"));
                    }
                }
                if (entityTag.contains("id", 8)) {
                    ResourceLocation loc = ResourceLocation.tryParse(entityTag.getString("id"));
                    if (loc != null) {
                        rawType = BuiltInRegistries.ENTITY_TYPE.get(loc);
                        if (rawType != null && !entityTag.contains("CustomName", 8)) {
                            mobName = rawType.getDescription();
                        }
                    }
                }

                MutableComponent header = Component.literal("§aContiene: §f").append(mobName);
                tooltipComponents.add(header);

                // 2. Poké Ball que contiene al mob
                Component ballName = Component.literal("Poké Ball");
                if (entityTag.contains("PokeballRegistryName", 8)) {
                    ResourceLocation ballLoc = ResourceLocation.tryParse(entityTag.getString("PokeballRegistryName"));
                    if (ballLoc != null) {
                        Item ballItem = BuiltInRegistries.ITEM.get(ballLoc);
                        if (ballItem != null) {
                            ballName = ballItem.getDescription();
                        }
                    }
                }
                tooltipComponents.add(Component.literal("§7Bola: §f").append(ballName));

                // Lista de efectos activos
                ListTag effectsList = null;
                if (entityTag.contains("active_effects", 9)) {
                    effectsList = entityTag.getList("active_effects", 10);
                } else if (entityTag.contains("ActiveEffects", 9)) {
                    effectsList = entityTag.getList("ActiveEffects", 10);
                }

                // 3. Salud actual / Salud total real del mob (Dinámica con Health Boost y Absorption)
                float health = entityTag.contains("Health", 99) ? entityTag.getFloat("Health") : 20f;
                float maxHealth = 20f;

                if (rawType != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) rawType;
                        if (DefaultAttributes.hasSupplier(livingType)) {
                            maxHealth = (float) DefaultAttributes.getSupplier(livingType).getValue(Attributes.MAX_HEALTH);
                        }
                    } catch (Exception ignored) {}
                }

                if (entityTag.contains("Attributes", 9) || entityTag.contains("attributes", 9)) {
                    ListTag attributesList = entityTag.contains("Attributes", 9) ? entityTag.getList("Attributes", 10) : entityTag.getList("attributes", 10);
                    for (int i = 0; i < attributesList.size(); i++) {
                        CompoundTag attrTag = attributesList.getCompound(i);
                        String attrId = attrTag.getString("id");
                        if ("minecraft:generic.max_health".equals(attrId) || "generic.max_health".equals(attrId)) {
                            if (attrTag.contains("base", 99)) {
                                maxHealth = attrTag.getFloat("base");
                            }
                            if (attrTag.contains("Modifiers", 9) || attrTag.contains("modifiers", 9)) {
                                ListTag modList = attrTag.contains("Modifiers", 9) ? attrTag.getList("Modifiers", 10) : attrTag.getList("modifiers", 10);
                                for (int m = 0; m < modList.size(); m++) {
                                    CompoundTag modTag = modList.getCompound(m);
                                    if (modTag.contains("amount", 99)) {
                                        maxHealth += modTag.getFloat("amount");
                                    }
                                }
                            }
                        }
                    }
                }

                float absorptionAmount = 0f;
                if (effectsList != null) {
                    for (int i = 0; i < effectsList.size(); i++) {
                        CompoundTag effectTag = effectsList.getCompound(i);
                        String effectId = effectTag.getString("id");
                        int amplifier = effectTag.contains("amplifier", 99) ? effectTag.getInt("amplifier") : 0;
                        
                        if ("minecraft:health_boost".equals(effectId)) {
                            maxHealth += 4.0f * (amplifier + 1);
                        } else if ("minecraft:absorption".equals(effectId)) {
                            absorptionAmount += 4.0f * (amplifier + 1);
                        }
                    }
                }

                String healthStr = "§7Salud: §f" + (int)Math.ceil(health) + " / " + (int)Math.ceil(maxHealth);
                if (absorptionAmount > 0f) {
                    healthStr += " (+" + (int)absorptionAmount + " 💛)";
                }
                healthStr += " §c❤";
                tooltipComponents.add(Component.literal(healthStr));

                // 4. Armadura (Natural + Modificadores NBT + Equipamiento dinámico de cualquier mod)
                float armor = 0f;
                if (rawType != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) rawType;
                        if (DefaultAttributes.hasSupplier(livingType)) {
                            armor = (float) DefaultAttributes.getSupplier(livingType).getValue(Attributes.ARMOR);
                        }
                    } catch (Exception ignored) {}
                }

                if (entityTag.contains("Attributes", 9) || entityTag.contains("attributes", 9)) {
                    ListTag attributesList = entityTag.contains("Attributes", 9) ? entityTag.getList("Attributes", 10) : entityTag.getList("attributes", 10);
                    for (int i = 0; i < attributesList.size(); i++) {
                        CompoundTag attrTag = attributesList.getCompound(i);
                        String attrId = attrTag.getString("id");
                        if ("minecraft:generic.armor".equals(attrId) || "generic.armor".equals(attrId)) {
                            if (attrTag.contains("base", 99)) {
                                armor = attrTag.getFloat("base");
                            }
                            if (attrTag.contains("Modifiers", 9) || attrTag.contains("modifiers", 9)) {
                                ListTag modList = attrTag.contains("Modifiers", 9) ? attrTag.getList("Modifiers", 10) : attrTag.getList("modifiers", 10);
                                for (int m = 0; m < modList.size(); m++) {
                                    CompoundTag modTag = modList.getCompound(m);
                                    if (modTag.contains("amount", 99)) {
                                        armor += modTag.getFloat("amount");
                                    }
                                }
                            }
                        }
                    }
                }

                if (entityTag.contains("ArmorItems", 9)) {
                    ListTag armorItems = entityTag.getList("ArmorItems", 10);
                    for (int i = 0; i < armorItems.size(); i++) {
                        CompoundTag itemTag = armorItems.getCompound(i);
                        if (itemTag.contains("id", 8)) {
                            ResourceLocation itemLoc = ResourceLocation.tryParse(itemTag.getString("id"));
                            if (itemLoc != null) {
                                Item armorItem = BuiltInRegistries.ITEM.get(itemLoc);
                                if (armorItem != null) {
                                    ItemAttributeModifiers modifiers = armorItem.getDefaultAttributeModifiers();
                                    if (modifiers != null) {
                                        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                                            if (entry.attribute().is(Attributes.ARMOR)) {
                                                armor += (float) entry.modifier().amount();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (armor > 0f) {
                    tooltipComponents.add(Component.literal("§7Armadura: §f" + (int)armor + " 🛡"));
                }

                // 5. Daño de Ataque (Base + Modificadores NBT + Arma equipada + Pociones de Fuerza/Debilidad)
                double attackDamage = 1.0;
                if (rawType != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) rawType;
                        if (DefaultAttributes.hasSupplier(livingType) && DefaultAttributes.getSupplier(livingType).hasAttribute(Attributes.ATTACK_DAMAGE)) {
                            attackDamage = DefaultAttributes.getSupplier(livingType).getValue(Attributes.ATTACK_DAMAGE);
                        }
                    } catch (Exception ignored) {}
                }

                if (entityTag.contains("Attributes", 9) || entityTag.contains("attributes", 9)) {
                    ListTag attributesList = entityTag.contains("Attributes", 9) ? entityTag.getList("Attributes", 10) : entityTag.getList("attributes", 10);
                    for (int i = 0; i < attributesList.size(); i++) {
                        CompoundTag attrTag = attributesList.getCompound(i);
                        String attrId = attrTag.getString("id");
                        if ("minecraft:generic.attack_damage".equals(attrId) || "generic.attack_damage".equals(attrId)) {
                            if (attrTag.contains("base", 99)) {
                                attackDamage = attrTag.getDouble("base");
                            }
                            if (attrTag.contains("Modifiers", 9) || attrTag.contains("modifiers", 9)) {
                                ListTag modList = attrTag.contains("Modifiers", 9) ? attrTag.getList("Modifiers", 10) : attrTag.getList("modifiers", 10);
                                for (int m = 0; m < modList.size(); m++) {
                                    CompoundTag modTag = modList.getCompound(m);
                                    if (modTag.contains("amount", 99)) {
                                        attackDamage += modTag.getDouble("amount");
                                    }
                                }
                            }
                        }
                    }
                }

                if (entityTag.contains("HandItems", 9)) {
                    ListTag handItems = entityTag.getList("HandItems", 10);
                    if (!handItems.isEmpty()) {
                        CompoundTag mainHandItemTag = handItems.getCompound(0);
                        if (mainHandItemTag.contains("id", 8)) {
                            ResourceLocation itemLoc = ResourceLocation.tryParse(mainHandItemTag.getString("id"));
                            if (itemLoc != null) {
                                Item weaponItem = BuiltInRegistries.ITEM.get(itemLoc);
                                if (weaponItem != null) {
                                    ItemAttributeModifiers modifiers = weaponItem.getDefaultAttributeModifiers();
                                    if (modifiers != null) {
                                        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                                            if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                                                attackDamage += entry.modifier().amount();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (effectsList != null) {
                    for (int i = 0; i < effectsList.size(); i++) {
                        CompoundTag effectTag = effectsList.getCompound(i);
                        String effectId = effectTag.getString("id");
                        int amplifier = effectTag.contains("amplifier", 99) ? effectTag.getInt("amplifier") : 0;
                        
                        if ("minecraft:strength".equals(effectId)) {
                            attackDamage += 3.0 * (amplifier + 1);
                        } else if ("minecraft:weakness".equals(effectId)) {
                            attackDamage = Math.max(0.0, attackDamage - 4.0 * (amplifier + 1));
                        }
                    }
                }

                if (attackDamage > 0.0) {
                    tooltipComponents.add(Component.literal("§7Daño: §f" + String.format("%.1f", attackDamage) + " ⚔"));
                }

                // 6. Efectos activos con nombre localizado
                if (effectsList != null && !effectsList.isEmpty()) {
                    tooltipComponents.add(Component.literal("§7Efectos activos:"));
                    for (int i = 0; i < effectsList.size(); i++) {
                        CompoundTag effectTag = effectsList.getCompound(i);
                        String effectId = effectTag.getString("id");
                        int amplifier = effectTag.contains("amplifier", 99) ? effectTag.getInt("amplifier") : 0;
                        
                        Component effectName = Component.literal(effectId);
                        ResourceLocation effectLoc = ResourceLocation.tryParse(effectId);
                        if (effectLoc != null) {
                            MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effectLoc);
                            if (mobEffect != null) {
                                effectName = Component.translatable(mobEffect.getDescriptionId());
                            }
                        }

                        tooltipComponents.add(Component.literal(" §7- ").append(effectName).append(" (Nivel " + (amplifier + 1) + ")"));
                    }
                }

                // 7. Al final en gris el ID técnico
                if (entityTag.contains("id", 8)) {
                    tooltipComponents.add(Component.literal("§7ID: " + entityTag.getString("id")));
                }
            }
        }
    }
}