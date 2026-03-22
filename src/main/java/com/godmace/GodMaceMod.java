package com.godmace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
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
    // Exact Lunge III value from game data: base 0.458 + 0.458 per level above first
    // Level 3 = 0.458 + (2 * 0.458) = 1.374
    private static final double LUNGE_POWER = 1.374;
    // Sprint speed added on top like vanilla (player is always sprinting when lunging)
    private static final double SPRINT_BOOST = 0.28;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onInitialize() {

        // ── Action bar display ──
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                long now = server.getOverworld().getTime();
                UUID uuid = player.getUuid();

                boolean onCooldown = false;
                float remainingSec = 0f;

                if (cooldowns.containsKey(uuid)) {
                    long elapsed = now - cooldowns.get(uuid);
                    if (elapsed < COOLDOWN_TICKS) {
                        onCooldown = true;
                        remainingSec = (COOLDOWN_TICKS - elapsed) / 20f;
                    }
                }

                boolean holdingMace = isGodMace(player.getMainHandStack());

                if (onCooldown) {
                    player.sendMessage(
                        Text.literal("§6⚡ God Dash §c" + String.format("%.1f", remainingSec) + "s"),
                        true
                    );
                } else if (holdingMace) {
                    player.sendMessage(Text.literal("§6⚡ God Dash §aREADY"), true);
                }
            }
        });

        // ── Right click to dash ──
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
                    return ActionResult.FAIL;
                }
            }

            cooldowns.put(uuid, now);

            // Exact Lunge III: coordinate_scale [1,0,1] means X and Z of look vector
            // Y is untouched. Magnitude 1.374 added to existing velocity.
            // Extra sprint boost added since vanilla Lunge is always used while sprinting.
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d current = player.getVelocity();
            double totalPower = LUNGE_POWER + SPRINT_BOOST;

            player.setVelocity(
                current.x + look.x * totalPower,
                current.y,
                current.z + look.z * totalPower
            );
            player.velocityDirty = true;

            serverPlayer.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

            ServerWorld serverWorld = (ServerWorld) world;

            serverWorld.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(),
                25, 0.4, 0.4, 0.4, 0.05);

            serverWorld.spawnParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 0.5, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.1);

            serverWorld.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

            return ActionResult.SUCCESS;
        });
    }

    private boolean isGodMace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return GOD_MACE_MODEL.equals(model);
    }
}
