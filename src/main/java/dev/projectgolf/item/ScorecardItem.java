package dev.projectgolf.item;

import dev.projectgolf.network.ScorecardPayload;
import dev.projectgolf.round.ScorecardData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** A permanent physical copy of one Project Golf round. */
public final class ScorecardItem extends Item {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu");

    public ScorecardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(Item item, ScorecardData data) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data.save()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(data.course() + " Scorecard"));
        return stack;
    }

    public static Optional<ScorecardData> read(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return Optional.empty();
        CompoundTag tag = custom.copyTag();
        if (!tag.contains("Course") || !tag.hasUUID("RoundId")) return Optional.empty();
        return Optional.of(ScorecardData.load(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            read(stack).ifPresent(data -> PacketDistributor.sendToPlayer(serverPlayer, new ScorecardPayload(data.save())));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        read(stack).ifPresent(data -> {
            tooltip.add(Component.literal(data.playerName() + " - " + data.course()).withStyle(ChatFormatting.GOLD));
            String result = data.completed() ? "Completed round"
                    : ("EXITED".equals(data.finishReason()) ? "Exited round" : "Partial round");
            tooltip.add(Component.literal(result + " - " + data.holes().size() + " hole(s)").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(data.totalStrokes() + " strokes  " + formatRelative(data.relativeToPar()))
                    .withStyle(ChatFormatting.WHITE));
            if (data.endedAt() > 0L) {
                String date = DATE.format(Instant.ofEpochMilli(data.endedAt()).atZone(ZoneId.systemDefault()));
                tooltip.add(Component.literal(date).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("tooltip.projectgolf.scorecard.open").withStyle(ChatFormatting.DARK_GREEN));
        });
    }

    private static String formatRelative(int value) {
        if (value == 0) return "E";
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
