package virtuoel.pehkui.mixin.reach.compat115minus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(Item.class)
public class ItemMixin
{
	@ModifyConstant(method = "method_7872(Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_3959$class_242;)Lnet/minecraft/class_239;", constant = @Constant(doubleValue = 5.0D), remap = false)
	private static double raycastModifyMultiplier(double value, World world, PlayerEntity player, RaycastContext.FluidHandling fluidHandling)
	{
		final float scale = ScaleUtils.getReachScale(player);
		
		if (scale != 1.0F)
		{
			return value * scale;
		}
		
		return value;
	}
}
