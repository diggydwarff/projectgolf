package dev.projectgolf.item;

import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.round.GolfRoundManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GolfBallItem extends Item {
    public GolfBallItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static DyeColor color(ItemStack stack) {
        return stack.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE);
    }

    public static ItemStack coloredStack(DyeColor color) {
        ItemStack stack = new ItemStack(GolfItems.GOLF_BALL.get());
        stack.set(DataComponents.BASE_COLOR, color);
        return stack;
    }

    /** RGB used by the item color handler. Kept explicit so every variant matches vanilla dye hues. */
    public static int renderColor(ItemStack stack) {
        return switch (color(stack)) {
            case WHITE -> 0xF9FFFE;
            case ORANGE -> 0xF9801D;
            case MAGENTA -> 0xC74EBD;
            case LIGHT_BLUE -> 0x3AB3DA;
            case YELLOW -> 0xFED83D;
            case LIME -> 0x80C71F;
            case PINK -> 0xF38BAA;
            case GRAY -> 0x474F52;
            case LIGHT_GRAY -> 0x9D9D97;
            case CYAN -> 0x169C9C;
            case PURPLE -> 0x8932B8;
            case BLUE -> 0x3C44AA;
            case BROWN -> 0x835432;
            case GREEN -> 0x5E7C16;
            case RED -> 0xB02E26;
            case BLACK -> 0x1D1D21;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        DyeColor color = color(stack);
        return Component.translatable(
                "item.projectgolf.golf_ball.colored",
                Component.translatable("color.minecraft." + color.getName()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DyeColor color = color(stack);
        tooltip.add(Component.translatable(
                        "tooltip.projectgolf.golf_ball.color",
                        Component.translatable("color.minecraft." + color.getName()))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer owner = context.getPlayer() instanceof ServerPlayer player ? player : null;
        ItemStack placedStack = context.getItemInHand().copyWithCount(1);
        if (owner != null) {
            // Whatever color the player intentionally places becomes their preferred competitive
            // ball color for subsequent tees as well.
            GolfRoundManager.setPreferredBallColor(owner, color(placedStack));
        }

        if (owner != null && GolfRoundManager.hasActiveRound(owner) && !GolfRoundManager.hasTakenStroke(owner)) {
            GolfRoundManager.resetOpeningBallToTee(owner);
            owner.sendSystemMessage(Component.literal(
                    "Opening shot must start from this hole's Tee Marker. Ball reset to the tee."));
            return InteractionResult.CONSUME;
        }

        GolfBallEntity ball = GolfEntities.GOLF_BALL.get().create(level);
        if (ball == null) return InteractionResult.FAIL;

        Vec3 click = context.getClickLocation();
        ball.setPos(click.x, click.y + 0.14, click.z);
        ball.setItem(placedStack);

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
