package dev.projectgolf.block;

import dev.projectgolf.visual.GolfVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A flat putting-surface cup marker. The entity performs speed/radius capture logically.
 *
 * Keeping normal full-block collision is intentional: a fast putt that exceeds the capture
 * threshold must roll across the cup instead of falling into a physical one-block-deep cavity.
 */
public class GolfCupBlock extends Block {
    public GolfCupBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // The physical flag does most of the locating. Keep this intentionally sparse so a cup is
        // readable through foliage/terrain without becoming another particle cloud.
        if (random.nextInt(7) != 0) return;
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        level.addParticle(GolfVisualEffects.WHITE_DUST, x, pos.getY() + 2.05, z, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 2.35, z, 0.0, 0.0, 0.0);
        }
    }
}
