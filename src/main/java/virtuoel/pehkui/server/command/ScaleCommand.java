package virtuoel.pehkui.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import virtuoel.pehkui.Pehkui;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.server.command.arguments.ScaleOperationArgumentType;
import virtuoel.pehkui.server.command.arguments.ScaleTypeArgumentType;

public class ScaleCommand
{
	public static void register(final CommandDispatcher<ServerCommandSource> commandDispatcher, final boolean dedicated)
	{
		commandDispatcher.register(
			CommandManager.literal("scale").requires(commandSource ->
			{
				return commandSource.hasPermissionLevel(2);
			})
			.then(CommandManager.argument("operation", ScaleOperationArgumentType.operation())
				.then(CommandManager.argument("scale_type", ScaleTypeArgumentType.scaleType())
					.then(CommandManager.argument("value", FloatArgumentType.floatArg())
						.then(CommandManager.argument("targets", EntityArgumentType.entities())
							.executes(context ->
							{
								final float scale = FloatArgumentType.getFloat(context, "value");
								final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
								
								for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
								{
									final ScaleData data = ScaleData.of(e, type);
									final ScaleOperationArgumentType.Operation operation = ScaleOperationArgumentType.getOperation(context, "operation");
									
									data.setTargetScale(operation.apply(data.getScale(), scale));
									data.markForSync();
								}
								
								return 1;
							})
						)
						.executes(context ->
						{
							final float scale = FloatArgumentType.getFloat(context, "value");
							final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
							
							final ScaleData data = ScaleData.of(context.getSource().getEntityOrThrow(), type);
							final ScaleOperationArgumentType.Operation operation = ScaleOperationArgumentType.getOperation(context, "operation");
							
							data.setTargetScale(operation.apply(data.getScale(), scale));
							data.markForSync();
							
							return 1;
						})
					)
				)
				.then(CommandManager.argument("value", FloatArgumentType.floatArg())
					.then(CommandManager.argument("targets", EntityArgumentType.entities())
						.executes(context ->
						{
							final float scale = FloatArgumentType.getFloat(context, "value");
							
							for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
							{
								final ScaleData data = ScaleData.of(e);
								final ScaleOperationArgumentType.Operation operation = ScaleOperationArgumentType.getOperation(context, "operation");
								
								data.setTargetScale(operation.apply(data.getScale(), scale));
								data.markForSync();
							}
							
							return 1;
						})
					)
					.executes(context ->
					{
						final float scale = FloatArgumentType.getFloat(context, "value");
						
						final ScaleData data = ScaleData.of(context.getSource().getEntityOrThrow());
						final ScaleOperationArgumentType.Operation operation = ScaleOperationArgumentType.getOperation(context, "operation");
						
						data.setTargetScale(operation.apply(data.getScale(), scale));
						data.markForSync();
						
						return 1;
					})
				)
			)
			.then(CommandManager.literal("get")
				.then(CommandManager.argument("scale_type", ScaleTypeArgumentType.scaleType())
					.then(CommandManager.argument("entity", EntityArgumentType.entity())
						.executes(context ->
						{
							final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
							final float scale = ScaleData.of(EntityArgumentType.getEntity(context, "entity"), type).getScale();
							context.getSource().sendFeedback(new LiteralText("Scale: " + scale), false);
							return 1;
						})
					)
					.executes(context ->
					{
						final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
						final float scale = ScaleData.of(context.getSource().getEntityOrThrow(), type).getScale();
						context.getSource().sendFeedback(new LiteralText("Scale: " + scale), false);
						return 1;
					})
				)
				.then(CommandManager.argument("entity", EntityArgumentType.entity())
					.executes(context ->
					{
						final float scale = ScaleData.of(EntityArgumentType.getEntity(context, "entity")).getScale();
						context.getSource().sendFeedback(new LiteralText("Scale: " + scale), false);
						return 1;
					})
				)
				.executes(context ->
				{
					final float scale = ScaleData.of(context.getSource().getEntityOrThrow()).getScale();
					context.getSource().sendFeedback(new LiteralText("Scale: " + scale), false);
					return 1;
				})
			)
			.then(CommandManager.literal("reset")
				.then(CommandManager.argument("scale_type", ScaleTypeArgumentType.scaleType())
					.then(CommandManager.argument("targets", EntityArgumentType.entities())
						.executes(context ->
						{
							for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
							{
								final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
								final ScaleData data = ScaleData.of(e, type);
								data.fromScale(ScaleData.IDENTITY);
								data.markForSync();
							}
							return 1;
						})
					)
					.executes(context ->
					{
						final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
						final ScaleData data = ScaleData.of(context.getSource().getEntityOrThrow(), type);
						data.fromScale(ScaleData.IDENTITY);
						data.markForSync();
						return 1;
					})
				)
				.then(CommandManager.argument("targets", EntityArgumentType.entities())
					.executes(context ->
					{
						for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
						{
							for (final ScaleType type : ScaleRegistries.SCALE_TYPES.values())
							{
								final ScaleData data = ScaleData.of(e, type);
								data.fromScale(ScaleData.IDENTITY);
								data.markForSync();
							}
						}
						
						return 1;
					})
				)
				.executes(context ->
				{
					for (final ScaleType type : ScaleRegistries.SCALE_TYPES.values())
					{
						final ScaleData data = ScaleData.of(context.getSource().getEntityOrThrow(), type);
						data.fromScale(ScaleData.IDENTITY);
						data.markForSync();
					}
					
					return 1;
				})
			)
			.then(CommandManager.literal("delay")
				.then(CommandManager.literal("set")
					.then(CommandManager.argument("scale_type", ScaleTypeArgumentType.scaleType())
						.then(CommandManager.argument("ticks", IntegerArgumentType.integer())
							.then(CommandManager.argument("targets", EntityArgumentType.entities())
								.executes(context ->
								{
									final int ticks = IntegerArgumentType.getInteger(context, "ticks");
									final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
									
									for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
									{
										final ScaleData data = ScaleData.of(e, type);
										
										data.setScaleTickDelay(ticks);
										data.markForSync();
									}
									
									return 1;
								})
							)
							.executes(context ->
							{
								final int ticks = IntegerArgumentType.getInteger(context, "ticks");
								final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
								
								final ScaleData data = ScaleData.of(context.getSource().getEntityOrThrow(), type);
								
								data.setScaleTickDelay(ticks);
								data.markForSync();
								
								return 1;
							})
						)
					)
					.then(CommandManager.argument("ticks", IntegerArgumentType.integer())
						.then(CommandManager.argument("targets", EntityArgumentType.entities())
							.executes(context ->
							{
								final int ticks = IntegerArgumentType.getInteger(context, "ticks");
								
								for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
								{
									final ScaleData data = ScaleData.of(e);
									
									data.setScaleTickDelay(ticks);
									data.markForSync();
								}
								
								return 1;
							})
						)
						.executes(context ->
						{
							final int ticks = IntegerArgumentType.getInteger(context, "ticks");
							
							final ScaleData data = ScaleData.of(context.getSource().getEntityOrThrow());
							
							data.setScaleTickDelay(ticks);
							data.markForSync();
							
							return 1;
						})
					)
				)
				.then(CommandManager.literal("get")
					.then(CommandManager.argument("scale_type", ScaleTypeArgumentType.scaleType())
						.then(CommandManager.argument("entity", EntityArgumentType.entity())
							.executes(context ->
							{
								final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
								final int ticks = ScaleData.of(EntityArgumentType.getEntity(context, "entity"), type).getScaleTickDelay();
								context.getSource().sendFeedback(new LiteralText("Delay: " + ticks + " ticks"), false);
								return 1;
							})
						)
						.executes(context ->
						{
							final ScaleType type = ScaleTypeArgumentType.getScaleTypeArgument(context, "scale_type");
							final int ticks = ScaleData.of(context.getSource().getEntityOrThrow(), type).getScaleTickDelay();
							context.getSource().sendFeedback(new LiteralText("Delay: " + ticks + " ticks"), false);
							return 1;
						})
					)
					.then(CommandManager.argument("entity", EntityArgumentType.entity())
						.executes(context ->
						{
							final int ticks = ScaleData.of(EntityArgumentType.getEntity(context, "entity")).getScaleTickDelay();
							context.getSource().sendFeedback(new LiteralText("Delay: " + ticks + " ticks"), false);
							return 1;
						})
					)
					.executes(context ->
					{
						final int ticks = ScaleData.of(context.getSource().getEntityOrThrow()).getScaleTickDelay();
						context.getSource().sendFeedback(new LiteralText("Delay: " + ticks + " ticks"), false);
						return 1;
					})
				)
			)
		);
		
		registerOldCommands(commandDispatcher);
	}
	
	@Deprecated
	private static void registerOldCommands(final CommandDispatcher<ServerCommandSource> commandDispatcher)
	{
		commandDispatcher.register(
			CommandManager.literal("setscale").requires(commandSource ->
			{
				return commandSource.hasPermissionLevel(2);
			})
			.then(CommandManager.argument("targets", EntityArgumentType.entities())
				.then(CommandManager.argument("scale", FloatArgumentType.floatArg())
					.executes(context ->
					{
						try
						{
							final float scale = FloatArgumentType.getFloat(context, "scale");
							for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
							{
								final ScaleData data = ScaleData.of(e);
								data.setTargetScale(scale);
								data.markForSync();
							}
						}
						catch (Exception e)
						{
							Pehkui.LOGGER.catching(e);
							throw e;
						}
						return 1;
					})
				)
			)
		);
		
		commandDispatcher.register(
			CommandManager.literal("setscaledelay").requires(commandSource ->
			{
				return commandSource.hasPermissionLevel(2);
			})
			.then(CommandManager.argument("targets", EntityArgumentType.entities())
				.then(CommandManager.argument("ticks", IntegerArgumentType.integer())
					.executes(context ->
					{
						try
						{
							final int ticks = IntegerArgumentType.getInteger(context, "ticks");
							for (final Entity e : EntityArgumentType.getEntities(context, "targets"))
							{
								final ScaleData data = ScaleData.of(e);
								data.setScaleTickDelay(ticks);
								data.markForSync();
							}
						}
						catch (Exception e)
						{
							Pehkui.LOGGER.catching(e);
							throw e;
						}
						return 1;
					})
				)
			)
		);
		
		commandDispatcher.register(
			CommandManager.literal("getscale").requires(commandSource ->
			{
				return commandSource.hasPermissionLevel(2);
			})
			.then(CommandManager.argument("entity", EntityArgumentType.entity())
				.executes(context ->
				{
					final float scale = ScaleData.of(EntityArgumentType.getEntity(context, "entity")).getScale();
					context.getSource().sendFeedback(new LiteralText("Scale: " + scale), false);
					return 1;
				})
			)
		);
		
		commandDispatcher.register(
			CommandManager.literal("getscaledelay").requires(commandSource ->
			{
				return commandSource.hasPermissionLevel(2);
			})
			.then(CommandManager.argument("entity", EntityArgumentType.entity())
				.executes(context ->
				{
					final int ticks = ScaleData.of(EntityArgumentType.getEntity(context, "entity")).getScaleTickDelay();
					context.getSource().sendFeedback(new LiteralText("Delay: " + ticks + " ticks"), false);
					return 1;
				})
			)
		);
	}
}
