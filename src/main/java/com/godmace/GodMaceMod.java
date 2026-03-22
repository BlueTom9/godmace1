package com.godmace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GodMaceMod implements ModInitializer {

    public static final String MOD_ID = "godmace";

    private static final Identifier GOD_MACE_MODEL = Identifier.of("minecraft", "item/mace/godmace");
    private static final long COOLDOWN_TICKS = 300L;
    private static final double DASH_HORIZONTAL = 2.8;
    private static final double DASH_VERTICAL = 0.4;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onInitialize() {

        UseItemCallback.EVENT.register((player, world, hand) -> {

            if (world.isClient()) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);

            if (!isGodMace(stack)) return ActionResult.PASS;

            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            long now = world.getTime();
            UUID uuid = player.getUuid();

            if (cooldowns.containsKey(uuid)) {
                long elapsed = now - cooldowns.get(uuid);
                if (elapsed < COOLDOWN_TICKS) {
                    long remainingTicks = COOLDOWN_TICKS - elapsed;
                    float remainingSec = remainingTicks / 20f;
                    serverPlayer.sendMessage(
                        Text.literal("§cDash on cooldown! §e" + String.format("%.1f", remainingSec) + "s"),
                        true
                    );
                    return ActionResult.FAIL;
                }
            }

            cooldowns.put(uuid, now);

            Vec3d look = player.getRotationVec(1.0f);
            double vx = look.x * DASH_HORIZONTAL;
            double vy = Math.max(look.y * DASH_HORIZONTAL, DASH_VERTICAL);
            double vz = look.z * DASH_HORIZONTAL;
            player.setVelocity(vx, vy, vz);
            player.velocityModified = true;

            ServerWorld serverWorld = (ServerWorld) world;

            serverWorld.spawnParticles(
                ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(),
                25, 0.4, 0.4, 0.4, 0.05
            );

            serverWorld.spawnParticles(
                ParticleTypes.SWEEP_ATTACK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                5, 0.3, 0.3, 0.3, 0.0
            );

            serverWorld.spawnParticles(
                ParticleTypes.GUST,
                player.getX(), player.getY() + 0.8, player.getZ(),
                3, 0.2, 0.2, 0.2, 0.0
            );

            serverWorld.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_BREEZE_WIND_BURST,
                SoundCategory.PLAYERS,
                1.0f, 1.1f
            );

            serverWorld.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ELYTRA_FLYING,
                SoundCategory.PLAYERS,
                0.8f, 1.4f
            );

            serverPlayer.sendMessage(Text.literal("§6⚡ God Dash!"), true);

            return ActionResult.SUCCESS;
        });
    }

    private boolean isGodMace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return GOD_MACE_MODEL.equals(model);
    }
}
