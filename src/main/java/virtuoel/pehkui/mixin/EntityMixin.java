package virtuoel.pehkui.mixin;

import java.util.Map;
import java.util.Map.Entry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import virtuoel.pehkui.Pehkui;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.server.command.DebugCommand;
import virtuoel.pehkui.util.PehkuiEntityExtensions;
import virtuoel.pehkui.util.ScaleCachingUtils;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(Entity.class)
public abstract class EntityMixin implements PehkuiEntityExtensions
{
	@Shadow boolean onGround;
	@Shadow boolean firstUpdate;
	
	private volatile Map<ScaleType, ScaleData> pehkui_scaleTypes = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
	private boolean pehkui_shouldSyncScales = false;
	private boolean pehkui_shouldIgnoreScaleNbt = false;
	private ScaleData[] pehkui_scaleCache = null;
	
	@Override
	public ScaleData pehkui_constructScaleData(ScaleType type)
	{
		return ScaleData.Builder.create().type(type).entity((Entity) (Object) this).build();
	}
	
	@Override
	public ScaleData pehkui_getScaleData(ScaleType type)
	{
		if (ScaleCachingUtils.ENABLE_CACHING)
		{
			ScaleData[] scaleCache = pehkui_scaleCache;
			
			if (scaleCache == null)
			{
				synchronized (this)
				{
					scaleCache = pehkui_scaleCache;
					
					if (scaleCache == null)
					{
						pehkui_scaleCache = scaleCache = new ScaleData[ScaleCachingUtils.CACHED.length];
					}
				}
			}
			
			final ScaleData cached = ScaleCachingUtils.getCachedData(scaleCache, type);
			
			if (cached != null)
			{
				return cached;
			}
		}
		
		final Map<ScaleType, ScaleData> scaleTypes = pehkui_getScales();
		
		ScaleData scaleData = scaleTypes.get(type);
		
		if (scaleData == null)
		{
			synchronized (scaleTypes)
			{
				if (!scaleTypes.containsKey(type))
				{
					scaleTypes.put(type, null);
					scaleTypes.put(type, scaleData = pehkui_constructScaleData(type));
					
					if (ScaleCachingUtils.ENABLE_CACHING)
					{
						ScaleCachingUtils.setCachedData(pehkui_scaleCache, type, scaleData);
					}
				}
				else
				{
					scaleData = scaleTypes.get(type);
				}
			}
		}
		
		return scaleData;
	}
	
	@Override
	public Map<ScaleType, ScaleData> pehkui_getScales()
	{
		Map<ScaleType, ScaleData> scaleTypes = pehkui_scaleTypes;
		
		if (scaleTypes == null)
		{
			synchronized (this)
			{
				scaleTypes = pehkui_scaleTypes;
				
				if (scaleTypes == null)
				{
					pehkui_scaleTypes = scaleTypes = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
					
					ScaleRegistries.SCALE_TYPES.values().forEach(this::pehkui_getScaleData);
				}
			}
		}
		
		return scaleTypes;
	}
	
	@Override
	public void pehkui_setShouldSyncScales(boolean sync)
	{
		pehkui_shouldSyncScales = sync;
	}
	
	@Override
	public boolean pehkui_shouldSyncScales()
	{
		return pehkui_shouldSyncScales;
	}
	
	@Override
	public boolean pehkui_shouldIgnoreScaleNbt()
	{
		return pehkui_shouldIgnoreScaleNbt;
	}
	
	@Override
	public void pehkui_setShouldIgnoreScaleNbt(boolean ignore)
	{
		pehkui_shouldIgnoreScaleNbt = ignore;
	}
	
	@Inject(at = @At("HEAD"), method = "readNbt")
	private void pehkui$readNbt(NbtCompound tag, CallbackInfo info)
	{
		pehkui_readScaleNbt(tag);
	}
	
	@Override
	public void pehkui_readScaleNbt(NbtCompound nbt)
	{
		if (pehkui_shouldIgnoreScaleNbt())
		{
			return;
		}
		
		if (nbt.contains(Pehkui.MOD_ID + ":scale_data_types", NbtType.COMPOUND) && !DebugCommand.unmarkEntityForScaleReset((Entity) (Object) this, nbt))
		{
			final NbtCompound typeData = nbt.getCompound(Pehkui.MOD_ID + ":scale_data_types");
			
			String key;
			ScaleData scaleData;
			for (Entry<Identifier, ScaleType> entry : ScaleRegistries.SCALE_TYPES.entrySet())
			{
				key = entry.getKey().toString();
				
				if (typeData.contains(key, NbtType.COMPOUND))
				{
					scaleData = pehkui_getScaleData(entry.getValue());
					scaleData.readNbt(typeData.getCompound(key));
				}
			}
		}
	}
	
	@Inject(at = @At("HEAD"), method = "writeNbt")
	private void pehkui$writeNbt(NbtCompound tag, CallbackInfoReturnable<NbtCompound> info)
	{
		pehkui_writeScaleNbt(tag);
	}
	
	@Override
	public NbtCompound pehkui_writeScaleNbt(NbtCompound nbt)
	{
		if (pehkui_shouldIgnoreScaleNbt())
		{
			return nbt;
		}
		
		final NbtCompound typeData = new NbtCompound();
		
		NbtCompound compound;
		for (final ScaleData scaleData : pehkui_getScales().values())
		{
			if (scaleData != null)
			{
				compound = scaleData.writeNbt(new NbtCompound());
				
				if (compound.getSize() != 0)
				{
					typeData.put(ScaleRegistries.getId(ScaleRegistries.SCALE_TYPES, scaleData.getScaleType()).toString(), compound);
				}
			}
		}
		
		if (typeData.getSize() > 0)
		{
			nbt.put(Pehkui.MOD_ID + ":scale_data_types", typeData);
		}
		
		return nbt;
	}
	
	@Inject(at = @At("HEAD"), method = "tick")
	private void pehkui$tick(CallbackInfo info)
	{
		for (ScaleType type : ScaleRegistries.SCALE_TYPES.values())
		{
			ScaleUtils.tickScale(pehkui_getScaleData(type));
		}
	}
	
	@Inject(at = @At("RETURN"), method = "getDimensions", cancellable = true)
	private void pehkui$getDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> info)
	{
		final float widthScale = ScaleUtils.getBoundingBoxWidthScale((Entity) (Object) this);
		final float heightScale = ScaleUtils.getBoundingBoxHeightScale((Entity) (Object) this);
		
		if (widthScale != 1.0F || heightScale != 1.0F)
		{
			info.setReturnValue(info.getReturnValue().scaled(widthScale, heightScale));
		}
	}
	
	@Inject(at = @At("HEAD"), method = "onStartedTrackingBy")
	private void pehkui$onStartedTrackingBy(ServerPlayerEntity player, CallbackInfo info)
	{
		ScaleUtils.syncScalesOnTrackingStart((Entity) (Object) this, player.networkHandler::sendPacket);
	}
	
	@ModifyVariable(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;", at = @At(value = "STORE"))
	private ItemEntity pehkui$dropStack(ItemEntity entity)
	{
		ScaleUtils.setScaleOfDrop(entity, (Entity) (Object) this);
		return entity;
	}
	
	@ModifyConstant(method = "move", constant = @Constant(doubleValue = 1.0E-7D))
	private double pehkui$move$minVelocity(double value)
	{
		final float scale = ScaleUtils.getMotionScale((Entity) (Object) this);
		
		return scale < 1.0F ? scale * scale * value : value;
	}
	
	@ModifyArg(method = "move", index = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;adjustMovementForSneaking(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/MovementType;)Lnet/minecraft/util/math/Vec3d;"))
	private Vec3d pehkui$move$adjustMovementForSneaking(Vec3d movement, MovementType type)
	{
		if (type == MovementType.SELF || type == MovementType.PLAYER)
		{
			return movement.multiply(ScaleUtils.getMotionScale((Entity) (Object) this));
		}
		
		return movement;
	}
	
	@ModifyArgs(method = "pushAwayFrom", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
	private void pehkui$pushSelfAwayFrom$other(Args args, Entity other)
	{
		final float otherScale = ScaleUtils.getMotionScale(other);
		
		if (otherScale != 1.0F)
		{
			args.set(0, args.<Double>get(0) * otherScale);
			args.set(2, args.<Double>get(2) * otherScale);
		}
	}
	
	@ModifyArgs(method = "pushAwayFrom", at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
	private void pehkui$pushOtherAwayFrom$self(Args args, Entity other)
	{
		final float ownScale = ScaleUtils.getMotionScale((Entity) (Object) this);
		
		if (ownScale != 1.0F)
		{
			args.set(0, args.<Double>get(0) * ownScale);
			args.set(2, args.<Double>get(2) * ownScale);
		}
	}
	
	@Inject(at = @At("HEAD"), method = "spawnSprintingParticles", cancellable = true)
	private void pehkui$spawnSprintingParticles(CallbackInfo info)
	{
		if (ScaleUtils.getMotionScale((Entity) (Object) this) < 1.0F)
		{
			info.cancel();
		}
	}
	
	@Override
	public boolean pehkui_isFirstUpdate()
	{
		return this.firstUpdate;
	}
	
	@Override
	public boolean pehkui_getOnGround()
	{
		return this.onGround;
	}
	
	@Override
	public void pehkui_setOnGround(boolean onGround)
	{
		this.onGround = onGround;
	}
}
