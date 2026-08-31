package dev.projectgolf.item;

import dev.projectgolf.golf.ClubType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class GolfClubItem extends Item {
    private final ClubType club;

    public GolfClubItem(ClubType club, Properties properties) {
        super(properties.stacksTo(1));
        this.club = club;
    }

    public ClubType club() {
        return club;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(String.format("Max speed %.2f | Loft %.0f°", club.maxSpeed(), club.loftDegrees())));
        tooltip.add(Component.literal("Hold use: power. Release: lock. Press use: impact."));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
