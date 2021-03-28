package virtuoel.pehkui.mixin.compat116minus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SlimeEntity;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(SlimeEntity.class)
public class SlimeEntityMixin
{
	@ModifyArg(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
	private Entity removeSpawnEntityProxy(Entity entity)
	{
		ScaleUtils.loadScale(entity, (Entity) (Object) this);
		
		return entity;
	}
	
	@ModifyConstant(method = "remove", constant = @Constant(floatValue = 4.0F), remap = false)
	private float removeModifyHorizontalOffset(float value)
	{
		final float scale = ScaleUtils.getWidthScale((Entity) (Object) this);
		
		if (scale != 1.0F)
		{
			return value / scale;
		}
		
		return value;
	}
	
	@ModifyConstant(method = "remove", constant = @Constant(doubleValue = 0.5D), remap = false)
	private double removeModifyVerticalOffset(double value)
	{
		final float scale = ScaleUtils.getHeightScale((Entity) (Object) this);
		
		if (scale != 1.0F)
		{
			return value * scale;
		}
		
		return value;
	}
}
