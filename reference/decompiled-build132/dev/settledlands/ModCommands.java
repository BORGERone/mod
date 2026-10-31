package dev.settledlands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.settledlands.ritual.RitualManager;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class ModCommands {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, WorldEvents events) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                "cleansing"
                                             )
                                             .requires(s -> s.hasPermission(2)))
                                          .then(
                                             Commands.literal("info")
                                                .executes(
                                                   c -> {
                                                      CommandSourceStack s = (CommandSourceStack)c.getSource();
                                                      ServerPlayer p = s.getPlayerOrException();
                                                      TerritoryData d = TerritoryData.get(p.serverLevel());
                                                      long k = Cell.at(p.blockPosition());
                                                      long now = ActivityClock.get(s.getServer()).seconds;
                                                      s.sendSuccess(
                                                         () -> Component.literal(
                                                            String.format(
                                                               Locale.ROOT,
                                                               "Settled Lands | ячейка %s | зачистка %.2f/%d | закрепление %d/%d | обжитость %d/%d с | постоянная: %s | часы: %d с",
                                                               BlockPos.of(k).toShortString(),
                                                               d.score(k, now),
                                                               Settings.TEMP_MAX.get(),
                                                               d.kills(k),
                                                               Settings.PERM_MAX.get(),
                                                               d.habitation(k),
                                                               Settings.HABITATION.get(),
                                                               d.permanent.contains(k),
                                                               now
                                                            )
                                                         ),
                                                         false
                                                      );
                                                      return 1;
                                                   }
                                                )
                                          ))
                                       .then(
                                          Commands.literal("ritual")
                                             .executes(
                                                c -> {
                                                   RitualManager.State ritual = RitualManager.state();
                                                   ((CommandSourceStack)c.getSource())
                                                      .sendSuccess(
                                                         () -> Component.literal(
                                                            ritual == null
                                                               ? "Активного ритуала нет. Нужны zombieRituals=true, mobGriefing=true и зомби в ячейке освящённого баннера."
                                                               : "Ритуал: "
                                                                  + (ritual.age() < 40 ? "cult" : "cultIDE")
                                                                  + " | до завершения "
                                                                  + Math.max(0.0, (140 - ritual.age()) / 20.0)
                                                                  + " с | баннер "
                                                                  + ritual.target().toShortString()
                                                                  + " | "
                                                                  + ritual.level().dimension().location()
                                                         ),
                                                         false
                                                      );
                                                   return ritual == null ? 0 : 1;
                                                }
                                             )
                                       ))
                                    .then(Commands.literal("version").executes(c -> {
                                       ((CommandSourceStack)c.getSource())
                                          .sendSuccess(() -> Diagnostics.report(((CommandSourceStack)c.getSource()).getServer()), false);
                                       return 1;
                                    })))
                                 .then(
                                    Commands.literal("ward")
                                       .executes(
                                          c -> {
                                             CommandSourceStack s = (CommandSourceStack)c.getSource();
                                             long key = Cell.at(BlockPos.containing(s.getPosition()));
                                             int count = Sanctity.count(s.getLevel(), key);
                                             int fire = Sanctity.countFire(s.getLevel(), key);
                                             s.sendSuccess(
                                                () -> Component.literal(
                                                   "Святость I: "
                                                      + (count > 0 ? "активна" : "нет")
                                                      + " | баннеров: "
                                                      + count
                                                      + " (из них II с горением: "
                                                      + fire
                                                      + ") | ячейка "
                                                      + BlockPos.of(key).toShortString()
                                                ),
                                                false
                                             );
                                             return count;
                                          }
                                       )
                                 ))
                              .then(
                                 Commands.literal("stats")
                                    .executes(
                                       c -> {
                                          TerritoryData d = TerritoryData.get(((CommandSourceStack)c.getSource()).getLevel());
                                          ((CommandSourceStack)c.getSource())
                                             .sendSuccess(
                                                () -> Component.literal(
                                                   "Измерение: изменяемых ячеек "
                                                      + d.zones.size()
                                                      + ", постоянных "
                                                      + d.permanent.size()
                                                      + ", бытовых блоков "
                                                      + d.blockCount()
                                                      + ", отклонено спавнов с запуска "
                                                      + d.deniedSpawns
                                                ),
                                                false
                                             );
                                          return 1;
                                       }
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("debug")
                                       .then(Commands.literal("on").executes(c -> debug((CommandSourceStack)c.getSource(), events, 32))))
                                    .then(Commands.literal("off").executes(c -> {
                                       ServerPlayer p = ((CommandSourceStack)c.getSource()).getPlayerOrException();
                                       events.debugging.remove(p.getUUID());
                                       DebugPayload.clear(p);
                                       return 1;
                                    })))
                                 .then(
                                    Commands.literal("radius")
                                       .then(
                                          Commands.argument("blocks", IntegerArgumentType.integer(16, 64))
                                             .executes(c -> debug((CommandSourceStack)c.getSource(), events, IntegerArgumentType.getInteger(c, "blocks")))
                                       )
                                 )
                           ))
                        .then(
                           Commands.literal("set")
                              .then(
                                 Commands.argument("temporary", IntegerArgumentType.integer(0, 100000))
                                    .then(
                                       Commands.argument("progress", IntegerArgumentType.integer(0, 1000000))
                                          .executes(
                                             c -> {
                                                ServerPlayer p = ((CommandSourceStack)c.getSource()).getPlayerOrException();
                                                TerritoryData d = TerritoryData.get(p.serverLevel());
                                                d.force(
                                                   Cell.at(p.blockPosition()),
                                                   Math.min((Integer)Settings.TEMP_MAX.get(), IntegerArgumentType.getInteger(c, "temporary")),
                                                   Math.min((Integer)Settings.PERM_MAX.get(), IntegerArgumentType.getInteger(c, "progress")),
                                                   false,
                                                   ActivityClock.get(p.server).seconds
                                                );
                                                ((CommandSourceStack)c.getSource())
                                                   .sendSuccess(() -> Component.literal("Прогресс текущей ячейки изменён."), true);
                                                return 1;
                                             }
                                          )
                                    )
                              )
                        ))
                     .then(Commands.literal("permanent").then(Commands.literal("confirm").executes(c -> {
                        ServerPlayer p = ((CommandSourceStack)c.getSource()).getPlayerOrException();
                        TerritoryData.get(p.serverLevel()).force(Cell.at(p.blockPosition()), 0.0, 0, true, ActivityClock.get(p.server).seconds);
                        ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("Текущая ячейка закреплена навсегда."), true);
                        return 1;
                     }))))
                  .then(
                     Commands.literal("reset")
                        .then(
                           Commands.literal("confirm")
                              .executes(
                                 c -> {
                                    ServerPlayer p = ((CommandSourceStack)c.getSource()).getPlayerOrException();
                                    TerritoryData.get(p.serverLevel()).reset(Cell.at(p.blockPosition()));
                                    ((CommandSourceStack)c.getSource())
                                       .sendSuccess(() -> Component.literal("Зачистка и закрепление текущей ячейки сброшены. Обустройство сохранено."), true);
                                    return 1;
                                 }
                              )
                        )
                  ))
               .then(
                  Commands.literal("register")
                     .executes(
                        c -> {
                           ServerPlayer p = ((CommandSourceStack)c.getSource()).getPlayerOrException();
                           HitResult hit = p.pick(8.0, 0.0F, false);
                           if (hit instanceof BlockHitResult b && hit.getType() == Type.BLOCK) {
                              int category = Household.category(p.serverLevel().getBlockState(b.getBlockPos()));
                              if (category == 0) {
                                 ((CommandSourceStack)c.getSource())
                                    .sendFailure(Component.literal("Посмотри на кровать, сундук, бочку, верстак, печь или костёр."));
                                 return 0;
                              } else {
                                 TerritoryData.get(p.serverLevel()).register(b.getBlockPos(), category, ActivityClock.get(p.server).seconds);
                                 ((CommandSourceStack)c.getSource())
                                    .sendSuccess(() -> Component.literal("Бытовой блок зарегистрирован. Команда подходит для старых построек."), true);
                                 return 1;
                              }
                           } else {
                              return 0;
                           }
                        }
                     )
               ))
            .then(
               Commands.literal("origin")
                  .then(
                     Commands.argument("entity", EntityArgument.entity())
                        .executes(
                           c -> {
                              Entity entity = EntityArgument.getEntity(c, "entity");
                              CompoundTag tag = entity.getPersistentData();
                              ((CommandSourceStack)c.getSource())
                                 .sendSuccess(
                                    () -> Component.literal(
                                       tag.contains("settledlands_origin")
                                          ? tag.getCompound("settledlands_origin").toString()
                                          : "Нет записанного происхождения."
                                    ),
                                    false
                                 );
                              return 1;
                           }
                        )
                  )
            )
      );
   }

   private static int debug(CommandSourceStack s, WorldEvents events, int radius) throws CommandSyntaxException {
      ServerPlayer p = s.getPlayerOrException();
      if (!DebugPayload.available(p)) {
         s.sendFailure(Component.literal("Для подсветки установи этот же JAR на клиент. Текстовые команды доступны без него."));
         return 0;
      } else {
         events.debugging.put(p.getUUID(), radius);
         DebugPayload.send(p, radius, ActivityClock.get(s.getServer()).seconds);
         s.sendSuccess(
            () -> Component.literal("Подсветка включена: " + radius + " блоков, максимум 512 ячеек. T — зачистка, P — закрепление, H — обжитость."), false
         );
         return 1;
      }
   }

   private ModCommands() {
   }
}
