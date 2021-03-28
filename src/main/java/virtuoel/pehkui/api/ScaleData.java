package virtuoel.pehkui.api;

import java.util.Collection;
import java.util.Objects;
import java.util.SortedSet;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ScaleData
{
	public static final ScaleData IDENTITY = Builder.create().buildImmutable(1.0F);
	
	private float baseScale;
	private float prevBaseScale;
	private float initialScale;
	private float targetScale;
	private int scaleTicks;
	private int totalScaleTicks;
	
	private boolean shouldSync = false;
	
	private final ScaleType scaleType;
	
	@Nullable
	private final Entity entity;
	
	private final SortedSet<ScaleModifier> baseValueModifiers = new ObjectRBTreeSet<>();
	
	/**
	 * @see {@link ScaleType#getScaleData(Entity)}
	 * @see {@link ScaleData.Builder#create()}
	 */
	@ApiStatus.Internal
	protected ScaleData(ScaleType scaleType, @Nullable Entity entity)
	{
		this.scaleType = scaleType;
		this.entity = entity;
		
		final float defaultBaseScale = scaleType.getDefaultBaseScale();
		
		this.baseScale = defaultBaseScale;
		this.prevBaseScale = defaultBaseScale;
		this.initialScale = defaultBaseScale;
		this.targetScale = defaultBaseScale;
		this.scaleTicks = 0;
		this.totalScaleTicks = scaleType.getDefaultTickDelay();
		
		getBaseValueModifiers().addAll(getScaleType().getDefaultBaseValueModifiers());
	}
	
	/**
	 * Called at the start of {@link Entity#tick()}.
	 * <p>Pre and post tick callbacks are not invoked here. If calling this manually, be sure to invoke callbacks!
	 */
	public void tick()
	{
		final float currScale = getBaseScale();
		final float targetScale = getTargetScale();
		final int scaleTickDelay = getScaleTickDelay();
		
		if (currScale != targetScale)
		{
			this.prevBaseScale = currScale;
			if (this.scaleTicks >= scaleTickDelay)
			{
				this.initialScale = targetScale;
				this.scaleTicks = 0;
				setBaseScale(targetScale);
			}
			else
			{
				this.scaleTicks++;
				final float nextScale = currScale + ((targetScale - this.initialScale) / (float) scaleTickDelay);
				setBaseScale(nextScale);
			}
		}
		else if (this.prevBaseScale != currScale)
		{
			this.prevBaseScale = currScale;
		}
	}
	
	public ScaleType getScaleType()
	{
		return this.scaleType;
	}
	
	@Nullable
	public Entity getEntity()
	{
		return this.entity;
	}
	
	/**
	 * Returns a mutable sorted set of scale modifiers. This set already contains the default modifiers from the scale type.
	 * @return Set of scale modifiers sorted by priority
	 */
	public SortedSet<ScaleModifier> getBaseValueModifiers()
	{
		return baseValueModifiers;
	}
	
	/**
	 * Returns the given scale value with modifiers applied from the given collection.
	 * 
	 * @param value The scale value to be modified.
	 * @param modifiers A sorted collection of scale modifiers to apply to the given value.
	 * @param delta Tick delta for use with rendering. Use 1.0F if no delta is available.
	 * @return Scale with modifiers applied
	 */
	protected float computeScale(float value, Collection<ScaleModifier> modifiers, float delta)
	{
		for (final ScaleModifier m : modifiers)
		{
			value = m.modifyScale(this, value, delta);
		}
		
		return value;
	}
	
	/**
	 * Gets the scale without any modifiers applied
	 * 
	 * @return Scale without any modifiers applied
	 */
	public float getBaseScale()
	{
		return getBaseScale(1.0F);
	}
	
	/**
	 * Gets the scale without any modifiers applied
	 * 
	 * @param delta Tick delta for use with rendering. Use 1.0F if no delta is available.
	 * @return Scale without any modifiers applied
	 */
	public float getBaseScale(float delta)
	{
		return delta == 1.0F ? baseScale : MathHelper.lerp(delta, getPrevBaseScale(), baseScale);
	}
	
	/**
	 * Sets the scale to the given value, updates the previous scale, and notifies listeners
	 * 
	 * @param scale New scale value to be set
	 */
	public void setBaseScale(float scale)
	{
		this.prevBaseScale = getBaseScale();
		this.baseScale = scale;
		onUpdate();
	}
	
	/**
	 * Gets the scale with modifiers applied
	 * 
	 * @return Scale with modifiers applied
	 */
	public float getScale()
	{
		return getScale(1.0F);
	}
	
	/**
	 * Gets the scale with modifiers applied
	 * 
	 * @param delta Tick delta for use with rendering. Use 1.0F if no delta is available.
	 * @return Scale with modifiers applied
	 */
	public float getScale(float delta)
	{
		return computeScale(getBaseScale(delta), getBaseValueModifiers(), delta);
	}
	
	/**
	 * Helper for instant resizing that sets both the base scale and target scale.
	 * 
	 * @param scale New scale value to be set
	 */
	public void setScale(float scale)
	{
		setBaseScale(scale);
		setTargetScale(scale);
	}
	
	public float getInitialScale()
	{
		return this.initialScale;
	}
	
	public float getTargetScale()
	{
		return this.targetScale;
	}
	
	/**
	 * Sets a target scale. The base scale will be gradually changed to this over the amount of ticks specified by the scale tick delay.
	 * 
	 * @param targetScale The scale that the base scale should gradually change to
	 */
	public void setTargetScale(float targetScale)
	{
		this.initialScale = getBaseScale();
		this.targetScale = targetScale;
		this.scaleTicks = 0;
		markForSync(true);
	}
	
	/**
	 * Gets the amount of ticks it will take for the base scale to change to the target scale
	 * 
	 * @return Delay in ticks
	 */
	public int getScaleTickDelay()
	{
		return this.totalScaleTicks;
	}
	
	/**
	 * Sets the amount of ticks it will take for the base scale to change to the target scale
	 * 
	 * @param ticks Delay in ticks
	 */
	public void setScaleTickDelay(int ticks)
	{
		this.totalScaleTicks = ticks;
		markForSync(true);
	}
	
	/**
	 * Gets the last value that the base scale was set to with modifiers applied. Useful for linear interpolation.
	 * 
	 * @return Last value of the base scale with modifiers applied
	 */
	public float getPrevScale()
	{
		return computeScale(getPrevBaseScale(), getBaseValueModifiers(), 1.0F);
	}
	
	/**
	 * Gets the last value that the base scale was set to. Useful for linear interpolation.
	 * 
	 * @return Last value of the base scale
	 */
	public float getPrevBaseScale()
	{
		return this.prevBaseScale;
	}
	
	public void markForSync(boolean sync)
	{
		final Entity e = getEntity();
		
		if (e != null && e.world != null && !e.world.isClient)
		{
			this.shouldSync = sync;
		}
	}
	
	public boolean shouldSync()
	{
		return this.shouldSync;
	}
	
	public void onUpdate()
	{
		markForSync(true);
		getScaleType().getScaleChangedEvent().invoker().onEvent(this);
	}
	
	public PacketByteBuf toPacket(PacketByteBuf buffer)
	{
		final SortedSet<ScaleModifier> syncedModifiers = new ObjectRBTreeSet<>();
		
		syncedModifiers.addAll(getBaseValueModifiers());
		syncedModifiers.removeAll(getScaleType().getDefaultBaseValueModifiers());
		
		buffer.writeFloat(this.baseScale)
		.writeFloat(this.prevBaseScale)
		.writeFloat(this.initialScale)
		.writeFloat(this.targetScale)
		.writeInt(this.scaleTicks)
		.writeInt(this.totalScaleTicks)
		.writeInt(syncedModifiers.size());
		
		for (final ScaleModifier modifier : syncedModifiers)
		{
			buffer.writeIdentifier(ScaleRegistries.getId(ScaleRegistries.SCALE_MODIFIERS, modifier));
		}
		
		return buffer;
	}
	
	public void readNbt(CompoundTag tag)
	{
		final ScaleType type = getScaleType();
		
		this.baseScale = tag.contains("scale") ? tag.getFloat("scale") : type.getDefaultBaseScale();
		this.prevBaseScale = tag.contains("previous") ? tag.getFloat("previous") : this.baseScale;
		this.initialScale = tag.contains("initial") ? tag.getFloat("initial") : this.baseScale;
		this.targetScale = tag.contains("target") ? tag.getFloat("target") : this.baseScale;
		this.scaleTicks = tag.contains("ticks") ? tag.getInt("ticks") : 0;
		this.totalScaleTicks = tag.contains("total_ticks") ? tag.getInt("total_ticks") : type.getDefaultTickDelay();
		
		final SortedSet<ScaleModifier> baseValueModifiers = getBaseValueModifiers();
		
		baseValueModifiers.clear();
		
		baseValueModifiers.addAll(type.getDefaultBaseValueModifiers());
		
		if (tag.contains("baseValueModifiers"))
		{
			final ListTag modifiers = tag.getList("baseValueModifiers", NbtType.STRING);
			
			Identifier id;
			ScaleModifier modifier;
			for (int i = 0; i < modifiers.size(); i++)
			{
				id = Identifier.tryParse(modifiers.getString(i));
				modifier = ScaleRegistries.getEntry(ScaleRegistries.SCALE_MODIFIERS, id);
				
				if (modifier != null)
				{
					baseValueModifiers.add(modifier);
				}
			}
		}
		
		onUpdate();
	}
	
	public CompoundTag writeNbt(CompoundTag tag)
	{
		final ScaleType type = getScaleType();
		final float defaultBaseScale = type.getDefaultBaseScale();
		
		final float scale = getBaseScale();
		if (scale != defaultBaseScale)
		{
			tag.putFloat("scale", scale);
		}
		
		final float initial = getInitialScale();
		if (initial != defaultBaseScale)
		{
			tag.putFloat("initial", initial);
		}
		
		final float target = getTargetScale();
		if (target != defaultBaseScale)
		{
			tag.putFloat("target", target);
		}
		
		if (this.scaleTicks != 0)
		{
			tag.putInt("ticks", this.scaleTicks);
		}
		
		if (this.totalScaleTicks != type.getDefaultTickDelay())
		{
			tag.putInt("total_ticks", this.totalScaleTicks);
		}
		
		final SortedSet<ScaleModifier> savedModifiers = new ObjectRBTreeSet<>();
		
		savedModifiers.addAll(getBaseValueModifiers());
		savedModifiers.removeAll(getScaleType().getDefaultBaseValueModifiers());
		
		if (!savedModifiers.isEmpty())
		{
			final ListTag modifiers = new ListTag();
			
			for (ScaleModifier modifier : savedModifiers)
			{
				modifiers.add(NbtOps.INSTANCE.createString(ScaleRegistries.getId(ScaleRegistries.SCALE_MODIFIERS, modifier).toString()));
			}
			
			tag.put("baseValueModifiers", modifiers);
		}
		
		return tag;
	}
	
	public ScaleData resetScale()
	{
		return resetScale(true);
	}
	
	public ScaleData resetScale(boolean notifyListener)
	{
		final ScaleType type = getScaleType();
		final float defaultBaseScale = type.getDefaultBaseScale();
		
		this.baseScale = defaultBaseScale;
		this.prevBaseScale = defaultBaseScale;
		this.initialScale = defaultBaseScale;
		this.targetScale = defaultBaseScale;
		this.scaleTicks = 0;
		this.totalScaleTicks = type.getDefaultTickDelay();
		
		final SortedSet<ScaleModifier> baseValueModifiers = getBaseValueModifiers();
		
		baseValueModifiers.clear();
		baseValueModifiers.addAll(type.getDefaultBaseValueModifiers());
		
		if (notifyListener)
		{
			onUpdate();
		}
		
		return this;
	}
	
	public boolean isReset()
	{
		final ScaleType type = getScaleType();
		final float defaultBaseScale = type.getDefaultBaseScale();
		
		if (getBaseScale() != defaultBaseScale)
		{
			return false;
		}
		
		if (this.prevBaseScale != defaultBaseScale)
		{
			return false;
		}
		
		if (getInitialScale() != defaultBaseScale)
		{
			return false;
		}
		
		if (getTargetScale() != defaultBaseScale)
		{
			return false;
		}
		
		if (this.scaleTicks != 0)
		{
			return false;
		}
		
		if (getScaleTickDelay() != type.getDefaultTickDelay())
		{
			return false;
		}
		
		if (!getBaseValueModifiers().equals(getScaleType().getDefaultBaseValueModifiers()))
		{
			return false;
		}
		
		return true;
	}
	
	public ScaleData fromScale(ScaleData scaleData)
	{
		return fromScale(scaleData, true);
	}
	
	public ScaleData fromScale(ScaleData scaleData, boolean notifyListener)
	{
		this.baseScale = scaleData.getBaseScale();
		this.prevBaseScale = scaleData.getPrevBaseScale();
		this.initialScale = scaleData.getInitialScale();
		this.targetScale = scaleData.getTargetScale();
		this.scaleTicks = scaleData.scaleTicks;
		this.totalScaleTicks = scaleData.totalScaleTicks;
		
		if (notifyListener)
		{
			onUpdate();
		}
		
		return this;
	}
	
	/**
	 * Averages the values of the given scale data and sets its own values from them.
	 * 
	 * @param scaleData Single scale data
	 * @param scales Any additional scale data
	 * @return Itself
	 */
	public ScaleData averagedFromScales(ScaleData scaleData, ScaleData... scales)
	{
		float scale = scaleData.getBaseScale();
		float prevScale = scaleData.prevBaseScale;
		float fromScale = scaleData.getInitialScale();
		float toScale = scaleData.getTargetScale();
		int scaleTicks = scaleData.scaleTicks;
		int totalScaleTicks = scaleData.totalScaleTicks;
		
		for (final ScaleData data : scales)
		{
			scale += data.getBaseScale();
			prevScale += data.prevBaseScale;
			fromScale += data.getInitialScale();
			toScale += data.getTargetScale();
			scaleTicks += data.scaleTicks;
			totalScaleTicks += data.totalScaleTicks;
		}
		
		final float count = scales.length + 1;
		
		this.baseScale = scale / count;
		this.prevBaseScale = prevScale / count;
		this.initialScale = fromScale / count;
		this.targetScale = toScale / count;
		this.scaleTicks = Math.round(scaleTicks / count);
		this.totalScaleTicks = Math.round(totalScaleTicks / count);
		
		onUpdate();
		
		return this;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(initialScale, prevBaseScale, baseScale, scaleTicks, targetScale, totalScaleTicks);
	}
	
	@Override
	public boolean equals(final Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		
		if (!(obj instanceof ScaleData))
		{
			return false;
		}
		
		return equals((ScaleData) obj);
	}
	
	public boolean equals(final ScaleData other)
	{
		if (this == other)
		{
			return true;
		}
		
		return Float.floatToIntBits(baseScale) == Float.floatToIntBits(other.baseScale) &&
			Float.floatToIntBits(prevBaseScale) == Float.floatToIntBits(other.prevBaseScale) &&
			Float.floatToIntBits(initialScale) == Float.floatToIntBits(other.initialScale) &&
			Float.floatToIntBits(targetScale) == Float.floatToIntBits(other.targetScale) &&
			scaleTicks == other.scaleTicks &&
			totalScaleTicks == other.totalScaleTicks &&
			Float.floatToIntBits(getScale()) == Float.floatToIntBits(other.getScale());
	}
	
	public static class Builder
	{
		private Entity entity = null;
		private ScaleType type = ScaleType.INVALID;
		
		public static Builder create()
		{
			return new Builder();
		}
		
		private Builder()
		{
			
		}
		
		public Builder type(ScaleType type)
		{
			this.type = type == null ? ScaleType.INVALID : type;
			return this;
		}
		
		public Builder entity(@Nullable Entity entity)
		{
			this.entity = entity;
			return this;
		}
		
		public ImmutableScaleData buildImmutable(float value)
		{
			return new ImmutableScaleData(value, type, entity);
		}
		
		public ScaleData build()
		{
			return new ScaleData(type, entity);
		}
	}
	
	public static class ImmutableScaleData extends ScaleData
	{
		protected ImmutableScaleData(float scale, ScaleType scaleType, @Nullable Entity entity)
		{
			super(scaleType, entity);
		}
		
		@Override
		public void tick()
		{
			
		}
		
		@Override
		public float getScale(float delta)
		{
			return getBaseScale(delta);
		}
		
		@Override
		public void setBaseScale(float scale)
		{
			
		}
		
		@Override
		public float getPrevScale()
		{
			return getPrevBaseScale();
		}
		
		@Override
		public void setTargetScale(float targetScale)
		{
			
		}
		
		@Override
		public void setScaleTickDelay(int ticks)
		{
			
		}
		
		@Override
		public void markForSync(boolean sync)
		{
			
		}
		
		@Override
		public void onUpdate()
		{
			
		}
		
		@Override
		public void readNbt(CompoundTag tag)
		{
			
		}
		
		@Override
		public ScaleData resetScale(boolean notifyListener)
		{
			return this;
		}
		
		@Override
		public boolean isReset()
		{
			return true;
		}
		
		@Override
		public ScaleData fromScale(ScaleData scaleData, boolean notifyListener)
		{
			return this;
		}
	}
}
