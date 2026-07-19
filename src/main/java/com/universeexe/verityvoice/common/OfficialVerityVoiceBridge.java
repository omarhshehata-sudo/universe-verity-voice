package com.universeexe.verityvoice.common;

import com.universeexe.verity.data.VerityPlayerData;
import com.universeexe.verity.entity.VerityBoxEntity;
import com.universeexe.verity.entity.VerityDemonEntity;
import com.universeexe.verity.entity.VerityEntity;
import com.universeexe.verity.registry.VerityEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges to universe_verity entity types. Never uses display-name detection.
 */
public final class OfficialVerityVoiceBridge {
    private OfficialVerityVoiceBridge() {
    }

    public static boolean isNormalVerity(Entity entity) {
        return entity instanceof VerityEntity && !(entity instanceof VerityDemonEntity);
    }

    public static boolean isDemonVerity(Entity entity) {
        return entity instanceof VerityDemonEntity
                || (entity != null && entity.getType() == VerityEntities.VERITY_DEMON.get());
    }

    public static boolean isUnopenedBox(Entity entity) {
        return entity instanceof VerityBoxEntity;
    }

    public static boolean isIntroductionComplete(Player player) {
        return VerityPlayerData.isVerityRevealed(player) || VerityPlayerData.isRevealCompleted(player);
    }

    public static boolean isVerityAvailableForConversation(Entity verity) {
        if (verity == null || !verity.isAlive() || verity.isRemoved()) {
            return false;
        }
        if (isDemonVerity(verity) || isUnopenedBox(verity)) {
            return false;
        }
        return isNormalVerity(verity);
    }

    public static Optional<Entity> findAssociatedVerity(ServerPlayer player) {
        Optional<UUID> uuid = VerityPlayerData.getVerityUuid(player);
        if (uuid.isEmpty() || !(player.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        Entity entity = serverLevel.getEntity(uuid.get());
        if (isVerityAvailableForConversation(entity)) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    public static Optional<Entity> findNearestNormalVerity(Player player, double range) {
        if (player.level() == null) {
            return Optional.empty();
        }
        AABB box = player.getBoundingBox().inflate(range);
        List<VerityEntity> list = player.level().getEntitiesOfClass(
                VerityEntity.class,
                box,
                OfficialVerityVoiceBridge::isVerityAvailableForConversation
        );
        return list.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .map(Entity.class::cast);
    }

    public static boolean belongsToPlayer(Entity verity, Player player) {
        if (!(verity instanceof VerityEntity ve) || player == null) {
            return false;
        }
        UUID owner = ve.getOwnerUUID();
        if (owner == null) {
            // Fall back to player data association when ownership was not synched yet.
            return VerityPlayerData.getVerityUuid(player).map(id -> id.equals(verity.getUUID())).orElse(false);
        }
        return owner.equals(player.getUUID());
    }
}
