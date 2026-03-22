package com.godmace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GodMaceMod implements ModInitializer {

    public static final String MOD_ID = "godmace";

    private static final ResourceLocation GOD_MACE_MODEL =
        ResourceLocation.fromNamespaceAndPath("minecraft", "item/mace/godmace");
    private static final long COOLDOWN_TICKS = 300L;
    private static final double DASH_HORIZONTAL = 2.8;
    private static final double DASH_VERTICAL = 0.4;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide())
                return InteractionResultHolder.pass(player.getItemInHand(hand));

            ItemStack stack = player.getItemInHand(hand);
            if (!isGodMace(stack))
                return InteractionResultHolder.pass(stack);

            ServerPlayer serverPlayer = (ServerPlayer) player;
            long now = world.getGameTime();
            UUID uuid = player.getUUID();

            if (cooldowns.containsKey(uuid)) {
                long elapsed = now - cooldowns.get(uuid);
                if (elapsed < COOLDOWN_TICKS) {
                    float remainingSec = (COOLDOWN_TICKS - elapsed) / 20f;
                    serverPlayer.displayClientMessage(
                        Component.literal("§cDash on cooldown! §e" + String.format("%.1f", remainingSec) + "s"),
                        true
                    );
                    return InteractionResultHolder.fail(stack);
                }
            }

            cooldowns.put(uuid, now);

            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(
                look.x * DASH_HORIZONTAL,
                Math.max(look.y * DASH_HORIZONTAL, DASH_VERTICAL),
                look.z * DASH_HORIZONTAL
            );
            player.hurtMarked = true;

            ServerLevel level = (ServerLevel) world;

            level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(),
                20, 0.4, 0.4, 0.4, 0.05);

            level.sendParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 0.5, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.1);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);

            serverPlayer.displayClientMessage(
                Component.literal("§6⚡ God Dash!"), true);

            return InteractionResultHolder.success(stack);
        });
    }

    private boolean isGodMace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var model = stack.get(DataComponents.ITEM_MODEL);
        return model != null && GOD_MACE_MODEL.equals(model);
    }
}
