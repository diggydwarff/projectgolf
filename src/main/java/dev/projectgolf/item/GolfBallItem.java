package dev.projectgolf.item;

import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.round.GolfRoundManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public class GolfBallItem extends Item {
    public GolfBallItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        GolfBallEntity ball = GolfEntities.GOLF_BALL.get().create(level);
        if (ball == null) return InteractionResult.FAIL;

        Vec3 click = context.getClickLocation();
        ball.setPos(click.x, click.y + 0.14, click.z);

        ServerPlayer owner = context.getPlayer() instanceof ServerPlayer player ? player : null;
        if (owner != null) {
            GolfRoundManager.removeActiveBall(owner);
            ball.setGolfOwner(owner.getUUID());
            ball.setLastSafePosition(ball.position());
        }

        if (!level.addFreshEntity(ball)) {
            return InteractionResult.FAIL;
        }
        if (owner != null) {
            GolfRoundManager.setActiveBall(owner, ball);
        }

        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
