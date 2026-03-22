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
    private static final double DASH_HORIZONTAL = 2.8;
    private static final double DASH_VERTICAL = 0.4;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onInitialize() {

        // ── Always show action bar while holding god mace ──
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ItemStack stack = player.getMainHandStack();
                if (!isGodMace(stack)) continue;

                long now = server.getOverworld().getTime();
                UUID uuid = player.getUuid();

                if (cooldowns.containsKey(uuid)) {
                    long elapsed = now - cooldowns.get(uuid);
                    if (elapsed < COOLDOWN_TICKS) {
                        float remainingSec = (COOLDOWN_TICKS - elapsed) / 20f;
                        player.sendMessage(
                            Text.literal("§6⚡ God Dash §c" + String.format("%.1f", remainingSec) + "s"),
                            true
                        );
                        continue;
                    }
                }
                // Ready!
                player.sendMessage(Text.literal("§6⚡ God Dash §aREADY"), true);
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

            // Dash
            Vec3d look = player.getRotationVec(1.0f);
            player.setVelocity(
                look.x * DASH_HORIZONTAL,
                Math.max(look.y * DASH_HORIZONTAL, DASH_VERTICAL),
                look.z * DASH_HORIZONTAL
            );
            player.velocityDirty = true;

            // Send velocity to client so it actually moves
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
