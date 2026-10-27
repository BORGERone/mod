package dev.settledlands.npc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.settledlands.revival.PurgatoryData;

/**
 * Build 217 «player-corpse»: смерть вселённого игрока оформляет труп как тело его НПС,
 * а после стокового воскрешения тела у маяка возрождения игрок сразу вселяется в него обратно.
 *
 * Механика:
 *  1) LivingDeathEvent (HIGHEST, до CorpseLifecycle): если игрок был вселён — запоминаем
 *     модель/имя тела в persistent-данные игрока (settledlands_body_*) и во временной карте.
 *  2) EntityJoinLevelEvent: только что созданный стоком GuideCorpseNpc этого игрока
 *     помечается (settledlands_player_body) и получает профиль с текстурой/именем НПС —
 *     труп выглядит как тело, которым управлял игрок. Дальше работает сток: труп
 *     неубиваем, укладывается в гроб, тащится поводком, у алтаря возрождает владельца.
 *  3) ServerTickEvent.Post: владелец вернулся к жизни (не в очереди чистилища) с меткой
 *     settledlands_body_pending → сразу восстанавливаем состояние вселения (ключи
 *     PossessionSystem), снимаем бестелесность, шлём possession_state на клиентов.
 *
 * Стоковая очередь/чистилище/телепорты НЕ трогаем; изумруды для тела игрока не требуются
 * (как и в стоковом reviveOwner).
 */
@EventBusSubscriber(modid = "settledlands")
public final class PlayerCorpsePossession {

    private PlayerCorpsePossession() {}

    // Маркер в persistent-данных ИГРОКА (переживает смерть через Clone-перенос ниже).
    private static final String PEND_KEY = "settledlands_body_pending";
    private static final String MODEL_KEY = "settledlands_body_model";
    private static final String NAME_KEY = "settledlands_body_name";

    // Маркер в persistent-данных ТРУПА (диагностика/совместимость сцен).
    private static final String CORPSE_MARK = "settledlands_player_body";

    // Ключ pending-буфера вещей тела (в persistent-данных игрока, переживает смерть).
    public static final String ITEMS_KEY = "settledlands_body_items";
    // Ключ хранилища вещей на САМОМ трупе-теле (у его сущности).
    public static final String CORPSE_ITEMS_KEY = "settledlands_corpse_items";

    // Ключи состояния вселения (владелец — PossessionSystem, build 216). Не менять!
    private static final String POSSESS_KEY = "settledlands_possess";
    private static final String POSSESS_MODEL = "settledlands_possess_model";
    private static final String POSSESS_NAME = "settledlands_possess_name";

    /** uuid игрока → параметры тела, между смертью и появлением трупа. */
    /** Радиус от маяка, в котором игрок считается «воскрешенным у маяка». */
    private static final double ALTAR_RADIUS_SQ = 5.0D * 5.0D;

    private static final Map<UUID, PendingBody> PENDING = new HashMap<>();

    /** Наблюдение за свежей смертью: если сток не оставил труп — создаём сами. */
    private static final List<DeadWatch> DEAD_WATCHES = new ArrayList<>();
    /** Фикс двух трупов (build 227): uuid владельца → uuid только что появившегося
     *  СТОКОВОГО трупа. Гарантия доверяет этой записи: если стоковый труп уже
     *  появился (даже в выгруженном чанке или уплыл от места смерти), второй не спавним. */
    private static final Map<UUID, UUID> STOCK_CORPSES = new HashMap<>();
    /** Сколько тиков максимум ждать загрузки чанка смерти, прежде чем снять гарантию. */
    private static final long UNLOADED_DEFER_MAX = 6000L;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Через сколько тиков после смерти проверять наличие трупа. */
    private static final long CORPSE_CHECK_DELAY = 40L;

    private record DeadWatch(UUID uuid, String name, String model, ServerLevel level,
                             Vec3 pos, float yRot, float yBodyRot, float yHeadRot,
                             boolean possessed, long checkAt) {}

    /** Контроль: дожил ли созданный нами труп 3 секунды (ловит тихие дискарды). */
    private record SurvivalCheck(ServerLevel level, UUID corpseId, String ownerName, long checkAt) {}
    private static final List<SurvivalCheck> SURVIVAL_CHECKS = new ArrayList<>();

    private static Field ownerUuidField;
    private static boolean ownerFieldBroken;

    private record PendingBody(String model, String name, float yRot, float yBodyRot, float yHeadRot) {}

    private static String safe(String s) { return s == null ? "" : s; }

    /** Persistent NBT (NeoForge IEntityExtension). */
    private static CompoundTag data(Entity entity) {
        return ((IEntityExtension) entity).getPersistentData();
    }

    // ----- 1) смерть вселённого игрока -----

    /** До CorpseLifecycle: фиксируем, что игрок умер, будучи вселённым в тело НПС. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        boolean possessed = PossessionSystem.isPossessed(player);
        String model = possessed ? safe(PossessionSystem.possessModelKey(player)) : "";
        String name = possessed ? safe(PossessionSystem.possessName(player)) : "";
        if (possessed) {
            CompoundTag tag = data(player);
            tag.putBoolean(PEND_KEY, true);
            tag.putString(MODEL_KEY, model);
            tag.putString(NAME_KEY, name);
            PENDING.put(uuid, new PendingBody(model, name, player.getYRot(), player.yBodyRot, player.yHeadRot));
        }
        // Гарантия трупа: сток (CorpseLifecycle LOWEST) должен создать труп владельца,
        // но в сборках со множеством модов его могут съесть — через 2 секунды проверим сами.
        DEAD_WATCHES.add(new DeadWatch(uuid, player.getName().getString(), model,
                player.serverLevel(), player.position(), player.getYRot(), player.yBodyRot, player.yHeadRot,
                possessed, player.serverLevel().getGameTime() + CORPSE_CHECK_DELAY));
        LOGGER.info("[SettledLands] player death at ({}) possessed={} — corpse watch scheduled",
                player.blockPosition(), possessed);
        if (possessed) {
            player.displayClientMessage(Component.translatable(
                    "message.settledlands.possession.body_left_at",
                    player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ()), false);
        }
    }

    /** Перенос маркера через смерть (страховка поверх копирования persistent data). */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag from = data(event.getOriginal());
        CompoundTag to = data(event.getEntity());
        if (from.getBoolean(PEND_KEY)) {
            to.putBoolean(PEND_KEY, true);
            to.putString(MODEL_KEY, from.getString(MODEL_KEY));
            to.putString(NAME_KEY, from.getString(NAME_KEY));
        }
        if (from.contains(ITEMS_KEY)) {
            to.put(ITEMS_KEY, from.getList(ITEMS_KEY, Tag.TAG_COMPOUND).copy());
        }
    }

    /** Build 223: вещи вселённого игрока не разлетаются — остаются «в теле» (NBT игрока). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!PossessionSystem.isPossessed(player)) return;
        if (event.getDrops().isEmpty()) return;
        HolderLookup.Provider registries = player.level().registryAccess();
        ListTag list = new ListTag();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) list.add(stack.save(registries, new CompoundTag()));
        }
        event.getDrops().clear();
        // Буфер до момента появления трупа; дальше вещи поедут на САМ труп (см. movePendingItemsToCorpse).
        data(player).put(ITEMS_KEY, list);
        // Фикс (build 227): стоковый труп к этому моменту уже создан (CorpseLifecycle
        // на LivingDeathEvent LOWEST срабатывает раньше дропов) — переносим вещи на труп
        // сразу, чтобы они не оставались в буфере игрока и не возвращались в инвентарь.
        GuideCorpseNpc ownCorpse = findOwnerCorpse(player.serverLevel(), player.position(), player.getUUID());
        if (ownCorpse != null) {
            movePendingItemsToCorpse(player, ownCorpse);
        }
        LOGGER.info("[SettledLands] body items kept in corpse: {} stacks for {}", list.size(),
                player.getName().getString());
    }

    /** Возврат вещей тела (при авто-вселении или при отмене маркера без воскрешения). */
    private static void restoreBodyItems(ServerPlayer player) {
        CompoundTag tag = data(player);
        if (!tag.contains(ITEMS_KEY)) return;
        ListTag list = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        HolderLookup.Provider registries = player.level().registryAccess();
        int restored = 0;
        for (int i = 0; i < list.size(); i++) {
            try {
                ItemStack stack = ItemStack.parse(registries, list.getCompound(i))
                        .orElse(ItemStack.EMPTY);
                if (stack.isEmpty()) continue;
                restored++;
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            } catch (Throwable ignored) {}
        }
        tag.remove(ITEMS_KEY);
        if (restored > 0) {
            LOGGER.info("[SettledLands] body items restored: {} stacks for {}", restored,
                    player.getName().getString());
        }
    }

    /** Уборка временной карты при выходе игрока (NBT-маркер в игроке остаётся — он главный). */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    // ----- 2) труп выглядит как тело НПС -----

    @SubscribeEvent
    public static void onCorpseJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof GuideCorpseNpc corpse)) return;
        // Здесь интересует только труп, созданный в этот же тик (смерть игрока),
        // а не подгруженный из чанка старый труп.
        if (corpse.tickCount > 2) return;
        UUID owner = ownerUuid(corpse);
        if (owner == null) return;
        // Запоминаем СТОКОВЫЙ труп — гарантия (guaranteeCorpses) не будет плодить дубликат.
        STOCK_CORPSES.put(owner, corpse.getUUID());
        PendingBody pending = PENDING.remove(owner);
        if (pending == null) return;
        CompoundTag tag = data(corpse);
        tag.putBoolean(CORPSE_MARK, true);
        tag.putString("settledlands_player_body_model", pending.model());
        tag.putString("settledlands_player_body_name", pending.name());
        tag.putBoolean("settledlands_corpse_dressed", true);
        dressBodyCorpse(corpse, pending);
        if (event.getLevel().getServer() != null) {
            movePendingItemsToCorpse(event.getLevel().getServer().getPlayerList().getPlayer(owner), corpse);
        }
    }

    private static UUID ownerUuid(GuideCorpseNpc corpse) {
        if (ownerFieldBroken) return null;
        try {
            if (ownerUuidField == null) {
                ownerUuidField = GuideCorpseNpc.class.getDeclaredField("ownerUuid");
                ownerUuidField.setAccessible(true);
            }
            Object value = ownerUuidField.get(corpse);
            return value instanceof UUID ? (UUID) value : null;
        } catch (Throwable t) {
            ownerFieldBroken = true;
            return null;
        }
    }

    // ----- 3) авто-вселение после воскрешения у маяка -----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        guaranteeCorpses();
        healStuckIvyBodies(server);
        PurgatoryData data = PurgatoryData.getMaster(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CompoundTag tag = data(player);
            if (!tag.getBoolean(PEND_KEY)) continue;
            // Стоковый цикл чистилища ещё идёт: игрок на экране смерти или гуляет в очереди.
            if (!player.isAlive()) continue;
            if (data.isDeadInQueue(player.getUUID())) continue;
            // Дошли сюда → стоковое воскрешение тела у маяка уже отработало
            // (removeFromQueue + телепорт к алтарю в GuideCorpseNpc.reviveOwner).
            String model = tag.getString(MODEL_KEY);
            String name = tag.getString(NAME_KEY);
            // Стоковое воскрешение через маяк телепортирует игрока прямо на блок алтаря.
            // Если игрок жив и не в очереди чистилища, но рядом НИ ОДНОГО маяка нет —
            // значит он выбрался сам (через выход/другое) и маяк его не реал:
            // авто-вселение НЕ делаем, метку просто снимаем.
            if (!nearRevivalAltar(player, data)) {
                // Сам-выход: тело не воскрешено — вещи остаются записанными в теле
                // (вернутся только воскрешением из гроба у маяка).
                clearMarker(tag);
                continue;
            }
            clearMarker(tag);
            if (PossessionSystem.isPossessed(player)) {
                // Пока ждали тело, игрок вселился в кого-то другого — не перебиваем.
                player.displayClientMessage(
                        Component.translatable("message.settledlands.possession.body_revived_busy"), true);
                continue;
            }
            repossess(player, model, name);
        }
    }

    /** Возврат в тело: то же состояние, что выставляет PossessionSystem при вселении. */
    private static void repossess(ServerPlayer player, String model, String name) {
        CompoundTag tag = data(player);
        tag.putBoolean(POSSESS_KEY, true);
        tag.putString(POSSESS_MODEL, model);
        tag.putString(POSSESS_NAME, name);
        try {
            Holder<MobEffect> bodiless = PossessionSystem.bodiless(player);
            if (bodiless != null) player.removeEffect(bodiless);
        } catch (Throwable ignored) {
            // Эффекта может не быть в реестре на ранней загрузке — не критично.
        }
        restoreBodyItems(player);
        PossessionClientSync.push(player.getServer(), player.getUUID(), model, name);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.1F);
        player.displayClientMessage(
                Component.translatable("message.settledlands.possession.body_revived"), true);
    }

    /** Если через CORPSE_CHECK_DELAY тиков труп владельца так и не появился у места смерти — создаём. */
    private static void guaranteeCorpses() {
        if (DEAD_WATCHES.isEmpty()) return;
        Iterator<DeadWatch> it = DEAD_WATCHES.iterator();
        List<DeadWatch> deferred = null;
        while (it.hasNext()) {
            DeadWatch w = it.next();
            ServerLevel level = w.level();
            if (level.getGameTime() < w.checkAt()) continue;
            // (a) Стоковый труп уже зафиксирован при появлении — дубль не спавним.
            if (STOCK_CORPSES.remove(w.uuid()) != null) {
                it.remove();
                LOGGER.info("[SettledLands] stock corpse recorded for {} — no duplicate spawn", w.name());
                // Build 229: стоковый труп мог появиться РАНЬШЕ, чем в onPlayerDeath заполнился
                // PENDING (порядок событий между модами), — тогда onCorpseJoin выходит по
                // `pending == null` и труп остаётся «неодетым»: 60/60, без модели тела, без
                // анимации die, без invulnerable. Ровно это видно как «модельки трупа нет».
                // Доодеваем его здесь, один раз (метка settledlands_corpse_dressed).
                if (w.possessed()) {
                    GuideCorpseNpc stock = findOwnerCorpse(level, w.pos(), w.uuid());
                    if (stock != null) {
                        CompoundTag ctag = data(stock);
                        if (!ctag.getBoolean("settledlands_corpse_dressed")) {
                            ctag.putBoolean("settledlands_corpse_dressed", true);
                            ctag.putBoolean(CORPSE_MARK, true);
                            ctag.putString("settledlands_player_body_model", w.model());
                            ctag.putString("settledlands_player_body_name", w.name());
                            dressBodyCorpse(stock, new PendingBody(w.model(), w.name(),
                                    w.yRot(), w.yBodyRot(), w.yHeadRot()));
                            LOGGER.info("[SettledLands] stock corpse dressed late for {} (model='{}', hp={})",
                                    w.name(), w.model(), stock.getHealth());
                        }
                    } else {
                        LOGGER.warn("[SettledLands] stock corpse recorded but not found near {} for {}",
                                w.pos(), w.name());
                    }
                }
                continue;
            }
            // (b) Чанк смерти выгружен: поиск и спавн ненадёжны (отсюда брались два трупа) —
            // откладываем проверку до загрузки чанка, но не дольше UNLOADED_DEFER_MAX.
            if (!level.isLoaded(BlockPos.containing(w.pos()))) {
                it.remove();
                if (level.getGameTime() >= w.checkAt() + UNLOADED_DEFER_MAX) {
                    LOGGER.warn("[SettledLands] corpse guarantee abandoned for {} — death chunk still unloaded", w.name());
                } else {
                    if (deferred == null) deferred = new ArrayList<>();
                    deferred.add(new DeadWatch(w.uuid(), w.name(), w.model(), level, w.pos(),
                            w.yRot(), w.yBodyRot(), w.yHeadRot(), w.possessed(), level.getGameTime() + 40L));
                }
                continue;
            }
            it.remove();
            GuideCorpseNpc found = findOwnerCorpse(level, w.pos(), w.uuid());
            if (found != null) {
                LOGGER.info("[SettledLands] stock corpse ok for {}", w.name());
                continue;
            }
            try {
                GuideCorpseNpc corpse = (GuideCorpseNpc) NpcContent.guideCorpse().create(level);
                if (corpse == null) {
                    LOGGER.warn("[SettledLands] corpse entity type creation returned null for {}", w.name());
                    continue;
                }
                corpse.moveTo(w.pos().x, w.pos().y, w.pos().z, w.yRot(), 0.0F);
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(w.uuid());
                if (owner != null) {
                    corpse.setOwner(owner);
                }
                CompoundTag ctag = data(corpse);
                if (w.possessed()) {
                    ctag.putBoolean(CORPSE_MARK, true);
                    ctag.putString("settledlands_player_body_model", w.model());
                    ctag.putString("settledlands_player_body_name", w.name());
                }
                if (w.possessed()) {
                    dressBodyCorpse(corpse, new PendingBody(w.model(), w.name(), w.yRot(), w.yBodyRot(), w.yHeadRot()));
                } else {
                    try {
                        corpse.moveTo(w.pos().x, w.pos().y, w.pos().z, w.yRot(), 0.0F);
                        corpse.setYBodyRot(w.yBodyRot());
                        corpse.setYHeadRot(w.yHeadRot());
                        corpse.playCustomAnimation("die", false);
                        corpse.setHealth(1.0F);
                        corpse.setInvulnerable(true);
                    } catch (Throwable ignored) {}
                }
                level.addFreshEntity(corpse);
                if (w.possessed()) {
                    movePendingItemsToCorpse(owner, corpse);
                }
                try {
                    level.getChunkSource().broadcast(corpse,
                            new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(corpse));
                } catch (Throwable ignored) {}
                LOGGER.info("[SettledLands] stock corpse missing - spawned our own for {} (possessed={}) at {} id={}",
                        w.name(), w.possessed(), corpse.blockPosition(), corpse.getId());
                SURVIVAL_CHECKS.add(new SurvivalCheck(level, corpse.getUUID(), w.name(),
                        level.getGameTime() + 60L));
            } catch (Throwable t) {
                LOGGER.warn("[SettledLands] corpse guarantee failed for {}: {}", w.name(), t.toString());
            }
        }
        if (deferred != null) DEAD_WATCHES.addAll(deferred);
    }

    /** Build 228: лечение «застрявшей» сцены смерти тела НПС (IvyDeathScene).
     *  Сцена хранит старт в persistentData (settledlands_ivydie_t0) и сравнивает с
     *  entity.tickCount; но tickCount сбрасывается к 0 при ПЕРЕЗАГРУЗКЕ чанка, а t0
     *  остаётся в NBT → возраст отрицательный и конверсия (на 71 тик невидимость,
     *  на 72 труп + discard тела) не выполняется НИКОГДА: тело остаётся навсегда
     *  невидимым и живым (те самые «60/60 и невидимка» после выгрузки чанка смерти,
     *  пока игрок сидел в чистилище). Раз в секунду чиним: если рядом уже есть труп —
     *  тело просто утилизируем (чтобы не плодить второй труп), иначе перебазировем
     *  t0 так, чтобы конверсия завершилась немедленно. */
    private static long ivySweepCounter;

    private static void healStuckIvyBodies(MinecraftServer server) {
        if (++ivySweepCounter % 20L != 0L) return;
        for (ServerLevel level : server.getAllLevels()) {
            if (level == null || level.isClientSide()) continue;
            // Build 229: БЕЗ AABB. Прошлый вариант брал бокс ±3.0E7 блоков, а Sable 2.0.5
            // (SubLevelInclusiveLevelEntityGetter) отменяет любой EntityGetter-запрос с
            // box.getSize() > 100000.0 и пишет ERROR со стектрейсом — то есть фикс 228 не
            // находил НИ ОДНОЙ сущности и 1 раз/сек заливал лог. Перебираем сущности уровня
            // напрямую: getAllEntities() не проходит через проверку бокса и стоит один проход
            // раз в секунду.
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (!(entity instanceof GuideNpc npc)) continue;
                if (npc instanceof GuideCorpseNpc) continue;
                try {
                    CompoundTag tag = data(npc);
                    if (!tag.contains("settledlands_ivydie_t0")) continue;
                    int t0 = tag.getInt("settledlands_ivydie_t0");
                    if (t0 <= npc.tickCount) continue;   // сцена идёт нормально
                    boolean corpseNear = !level.getEntitiesOfClass(GuideCorpseNpc.class,
                            npc.getBoundingBox().inflate(4.0D, 3.0D, 4.0D)).isEmpty();
                    if (corpseNear) {
                        tag.remove("settledlands_ivydie_t0");
                        tag.remove("settledlands_ivydie_stage");
                        npc.discard();
                        LOGGER.warn("[SettledLands] stuck ivy body discarded (corpse already present) for {} at {}",
                                npc.getName().getString(), npc.blockPosition());
                    } else {
                        tag.putInt("settledlands_ivydie_t0", npc.tickCount - 72);
                        LOGGER.warn("[SettledLands] stuck ivy scene rebased for {} (t0={} > tickCount={}) — conversion completes now",
                                npc.getName().getString(), t0, npc.tickCount);
                    }
                } catch (Throwable t) {
                    // Один битый НПС не должен убивать весь проход (и не должен спамить лог).
                    if (ivySweepCounter % 600L == 0L) {
                        LOGGER.warn("[SettledLands] ivy sweep skip {}: {}", entity.getUUID(), t.toString());
                    }
                }
            }
        }
    }

    /** Вещи из pending-буфера игрока переносятся на ЕГО труп (и только на него). */
    private static void movePendingItemsToCorpse(ServerPlayer player, GuideCorpseNpc corpse) {
        if (player == null) return;
        CompoundTag pt = data(player);
        if (!pt.contains(ITEMS_KEY)) return;
        ListTag list = pt.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        if (!list.isEmpty()) {
            data(corpse).put(CORPSE_ITEMS_KEY, list.copy());
            LOGGER.info("[SettledLands] {} body stacks written into own corpse", list.size());
        }
        pt.remove(ITEMS_KEY);
    }

    /** Внешний вид трупа-тела = трубопровод сцен гибели NPC: повороты, профиль,
     *  имя, замороженная "die", 1 HP, invulnerable. */
    private static void dressBodyCorpse(GuideCorpseNpc corpse, PendingBody body) {
        try {
            corpse.setYRot(body.yRot());
            corpse.setYBodyRot(body.yBodyRot());
            corpse.setYHeadRot(body.yHeadRot());
        } catch (Throwable ignored) {}
        if (!body.model().isEmpty()) {
            try {
                NpcProfile profile = new NpcProfile();
                profile.name = body.name().isEmpty() ? corpse.getName().getString() : body.name();
                profile.texture = body.model();
                corpse.setProfile(profile);
                corpse.applyProfile();
            } catch (Throwable ignored) {
                // Косметика: не ломает спавн.
            }
        }
        if (!body.name().isEmpty()) {
            try {
                corpse.setCustomName(Component.literal(body.name()));
                corpse.setCustomNameVisible(true);
            } catch (Throwable ignored) {}
        }
        try {
            corpse.playCustomAnimation("die", false);
        } catch (Throwable ignored) {}
        try {
            corpse.setHealth(1.0F);
            corpse.setInvulnerable(true);
        } catch (Throwable ignored) {}
    }

    private static GuideCorpseNpc findOwnerCorpse(ServerLevel level, Vec3 around, UUID owner) {
        AABB box = new AABB(around.x - 4, around.y - 3, around.z - 4, around.x + 4, around.y + 3, around.z + 4);
        for (GuideCorpseNpc corpse : level.getEntitiesOfClass(GuideCorpseNpc.class, box)) {
            if (owner.equals(ownerUuid(corpse))) return corpse;
        }
        return null;
    }

    /** Игрок стоит рядом с каким-либо зарегистрированным маяком возрождения? */
    private static boolean nearRevivalAltar(ServerPlayer player, PurgatoryData data) {
        if (data.altars().isEmpty()) return false;
        double px = player.getX();
        double py = player.getY() - 1.0D;
        double pz = player.getZ();
        for (long packed : data.altars()) {
            BlockPos pos = BlockPos.of(packed);
            double dx = pos.getX() + 0.5D - px;
            double dy = pos.getY() - py;
            double dz = pos.getZ() + 0.5D - pz;
            if (dx * dx + dy * dy + dz * dz <= ALTAR_RADIUS_SQ) return true;
        }
        return false;
    }

    private static void clearMarker(CompoundTag tag) {
        tag.remove(PEND_KEY);
        tag.remove(MODEL_KEY);
        tag.remove(NAME_KEY);
    }
}
