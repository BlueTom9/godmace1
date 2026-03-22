package com.godmace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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
    private static final double LUNGE_POWER = 1.374;
    private static final double SPRINT_BOOST = 0.28;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onInitialize() {

        // ── /godmace command ──
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("godmace")
                    .requires(source -> {
                        if (!(source.getEntity() instanceof ServerPlayerEntity p)) return false;
                        return p.getPermissionLevel() >= 2;
                    })
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        ServerPlayerEntity player = source.getPlayer();
                        if (player == null) return 0;

                        try {
                            source.getServer().getCommandManager().getDispatcher().execute(
                                "give " + player.getName().getString() + " minecraft:mace[minecraft:unbreakable={value:1},minecraft:item_model='minecraft:item/mace/godmace',custom_name={\"text\":\"GOD MACE\",\"color\":\"yellow\",\"bold\":true,\"italic\":false},enchantments={\"minecraft:density\":5,\"minecraft:breach\":4,\"minecraft:wind_burst\":3,\"minecraft:mending\":1,\"minecraft:unbreaking\":3,\"minecraft:fire_aspect\":2}] 1",
                                source.getServer().getCommandSource()
                            );
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§cFailed to give God Mace!"), false);
                            return 0;
                        }

                        player.sendMessage(Text.literal("§6You have been given the §eGOD MACE§6!"), false);
                        return 1;
                    })
            );
        });

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
