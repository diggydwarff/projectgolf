package dev.projectgolf.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Portable golf-only storage. The bag deliberately uses the vanilla two-row chest menu so the
 * inventory interaction is familiar and reliable; the backing container rejects everything that
 * is not a Project Golf club or golf ball. Contents live directly on the ItemStack's vanilla
 * CONTAINER data component, so moving/dropping the bag preserves its inventory without a block
 * entity, capability, or custom packet protocol.
 */
public final class GolfBagItem extends Item {
    public static final int SLOTS = 18;

    public GolfBagItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean canStore(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof GolfClubItem || stack.getItem() instanceof GolfBallItem);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(bag);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, opener) -> new ChestMenu(
                            MenuType.GENERIC_9x2,
                            containerId,
                            playerInventory,
                            new BagContainer(bag),
                            2),
                    Component.translatable("container.projectgolf.golf_bag")));
        }
        return InteractionResultHolder.consume(bag);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        long occupied = contents.stream().filter(s -> !s.isEmpty()).count();
        long clubs = contents.stream().filter(s -> s.getItem() instanceof GolfClubItem).count();
        int balls = contents.stream()
                .filter(s -> s.getItem() instanceof GolfBallItem)
                .mapToInt(ItemStack::getCount)
                .sum();
        tooltip.add(Component.translatable("tooltip.projectgolf.golf_bag.contents", occupied, SLOTS)
                .withStyle(ChatFormatting.GRAY));
        if (occupied > 0) {
            tooltip.add(Component.translatable("tooltip.projectgolf.golf_bag.summary", clubs, balls)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.projectgolf.golf_bag.restriction")
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    private static final class BagContainer extends SimpleContainer {
        private final ItemStack bag;

        private BagContainer(ItemStack bag) {
            super(SLOTS);
            this.bag = bag;
            bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return canStore(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
        }
    }
}
