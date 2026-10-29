#!/usr/bin/env python3
"""Real NpcPickCopy + NpcCopyPickPayload + NpcCopyEggFix253 + NpcContent + GuideNpc +
GuideTriggerNpc + GuideCorpseNpc + NpcProfile + NpcDialogueGraph + NpcStatsGraph on Java 21 (-Xverify:all).

Exercises:
1. Reproduction of the Build 252 freeze/crash from user's latest.log:
   buildCopyEgg(GuideNpc) wrote DataComponents.ENTITY_DATA without string "id" (tag type 8),
   causing ItemStack.save (CustomData.CODEC_WITH_ID) to throw IllegalStateException:
   'Missing id for entity in: {Guide:{sl_copy_src:...}}' and ServerPlayer save to throw
   ReportedException: Saving entity NBT.
2. Build 253 end-to-end verification:
   - NpcPickCopy.onKeyTriggered pick-block sends NpcCopyPickPayload(entityId)
   - NpcCopyPickPayload.CODEC encode/decode and register() playToServer handler
   - Non-OP permission check (hasPermissions(2) == false -> message.settledlands.cfg.noperm)
   - OP copy of configured GuideNpc (profile name/texture/scale/pose/aiMode + Build 246 STATS 14/13/12)
   - Full inventory fallback to ServerPlayer.drop(stack, false)
   - ItemStack.save & ServerPlayer.saveWithoutId pass CustomData.CODEC_WITH_ID cleanly
   - DeferredSpawnEggItem.getType(stack) + useOn spawns an independent GuideNpc copy via
     CustomData.loadInto -> GuideNpc.readAdditionalSaveData -> NpcProfile.save/load ->
     NpcDialogueGraph.save/load -> GuideNpc.applyProfile() with deep-copied graph & stats
   - GuideTriggerNpc copy produces triggerEgg + id='settledlands:guide_trigger' and spawns GuideTriggerNpc
   - GuideCorpseNpc pick safely produces spawnEgg + id='settledlands:guide' (never CoffinItem or corpse duplicate)
   - Unnamed GuideNpc falls back to EntityType description in CUSTOM_NAME
"""
import argparse, os, pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]

OVERRIDES = {
    'net/minecraft/ReportedException.java': '''
package net.minecraft;
public class ReportedException extends RuntimeException {
    public ReportedException(String msg, Throwable cause) { super(msg, cause); }
}
''',
    'net/minecraft/resources/ResourceLocation.java': '''
package net.minecraft.resources;
import java.util.Objects;
public final class ResourceLocation {
    private final String ns, path;
    public ResourceLocation(String ns, String path) { this.ns = ns; this.path = path; }
    public static ResourceLocation fromNamespaceAndPath(String ns, String path) { return new ResourceLocation(ns, path); }
    public static ResourceLocation tryParse(String s) {
        if (s == null || s.isEmpty()) return null;
        int i = s.indexOf(':');
        return i < 0 ? new ResourceLocation("minecraft", s) : new ResourceLocation(s.substring(0, i), s.substring(i + 1));
    }
    public String getNamespace() { return ns; }
    public String getPath() { return path; }
    @Override public String toString() { return ns + ":" + path; }
    @Override public boolean equals(Object o) { return o instanceof ResourceLocation r && Objects.equals(ns, r.ns) && Objects.equals(path, r.path); }
    @Override public int hashCode() { return Objects.hash(ns, path); }
}
''',
    'net/minecraft/nbt/Tag.java': '''
package net.minecraft.nbt;
public interface Tag {
    byte getId();
    Tag copy();
}
''',
    'net/minecraft/nbt/StringTag.java': '''
package net.minecraft.nbt;
public final class StringTag implements Tag {
    public final String value;
    private StringTag(String v) { this.value = v == null ? "" : v; }
    public static StringTag valueOf(String v) { return new StringTag(v); }
    public String getAsString() { return value; }
    @Override public byte getId() { return 8; }
    @Override public Tag copy() { return new StringTag(value); }
    @Override public String toString() { return '"' + value + '"'; }
}
''',
    'net/minecraft/nbt/ListTag.java': '''
package net.minecraft.nbt;
import java.util.ArrayList;
public class ListTag extends ArrayList<Tag> implements Tag {
    @Override public byte getId() { return 9; }
    public CompoundTag getCompound(int i) {
        return i >= 0 && i < size() && get(i) instanceof CompoundTag c ? c : new CompoundTag();
    }
    public String getString(int i) {
        if (i < 0 || i >= size()) return "";
        Tag t = get(i);
        return t instanceof StringTag s ? s.value : "";
    }
    @Override public ListTag copy() {
        ListTag out = new ListTag();
        for (Tag t : this) out.add(t.copy());
        return out;
    }
}
''',
    'net/minecraft/nbt/CompoundTag.java': '''
package net.minecraft.nbt;
import java.util.*;
public class CompoundTag implements Tag {
    private static final class Scalar implements Tag {
        final byte type; final Object val;
        Scalar(byte type, Object val) { this.type = type; this.val = val; }
        @Override public byte getId() { return type; }
        @Override public Tag copy() {
            if (val instanceof int[] a) return new Scalar(type, a.clone());
            return new Scalar(type, val);
        }
        @Override public String toString() {
            if (val instanceof int[] a) return "[I;" + a[0] + "," + a[1] + "," + a[2] + "," + a[3] + "]";
            return String.valueOf(val);
        }
    }
    private final Map<String, Tag> tags = new LinkedHashMap<>();
    @Override public byte getId() { return 10; }
    public boolean isEmpty() { return tags.isEmpty(); }
    public Set<String> getAllKeys() { return tags.keySet(); }
    public void remove(String k) { tags.remove(k); }
    public Tag get(String k) { return tags.get(k); }
    public Tag put(String k, Tag v) { return v == null ? tags.remove(k) : tags.put(k, v); }
    public boolean contains(String k) { return tags.containsKey(k); }
    public boolean contains(String k, int type) {
        Tag t = tags.get(k);
        if (t == null) return false;
        int id = t.getId();
        if (id == type) return true;
        if (type == 99) return id >= 1 && id <= 6;
        return false;
    }
    public void putBoolean(String k, boolean v) { tags.put(k, new Scalar((byte) 1, (byte) (v ? 1 : 0))); }
    public void putByte(String k, byte v) { tags.put(k, new Scalar((byte) 1, v)); }
    public void putInt(String k, int v) { tags.put(k, new Scalar((byte) 3, v)); }
    public void putLong(String k, long v) { tags.put(k, new Scalar((byte) 4, v)); }
    public void putFloat(String k, float v) { tags.put(k, new Scalar((byte) 5, v)); }
    public void putDouble(String k, double v) { tags.put(k, new Scalar((byte) 6, v)); }
    public void putString(String k, String v) { tags.put(k, StringTag.valueOf(v)); }
    public void putIntArray(String k, int[] v) { tags.put(k, new Scalar((byte) 11, v == null ? new int[0] : v.clone())); }
    public void putUUID(String k, UUID u) {
        long msb = u.getMostSignificantBits(), lsb = u.getLeastSignificantBits();
        putIntArray(k, new int[]{(int)(msb >>> 32), (int)msb, (int)(lsb >>> 32), (int)lsb});
    }
    public boolean hasUUID(String k) { return contains(k, 11) && getIntArray(k).length == 4; }
    public UUID getUUID(String k) {
        int[] a = getIntArray(k);
        long msb = ((long)a[0] << 32) | (a[1] & 0xFFFFFFFFL);
        long lsb = ((long)a[2] << 32) | (a[3] & 0xFFFFFFFFL);
        return new UUID(msb, lsb);
    }
    public boolean getBoolean(String k) { return getInt(k) != 0; }
    public byte getByte(String k) { return (byte) getInt(k); }
    public int getInt(String k) {
        Tag t = tags.get(k);
        return t instanceof Scalar s && s.val instanceof Number n ? n.intValue() : 0;
    }
    public long getLong(String k) {
        Tag t = tags.get(k);
        return t instanceof Scalar s && s.val instanceof Number n ? n.longValue() : 0L;
    }
    public float getFloat(String k) {
        Tag t = tags.get(k);
        return t instanceof Scalar s && s.val instanceof Number n ? n.floatValue() : 0f;
    }
    public double getDouble(String k) {
        Tag t = tags.get(k);
        return t instanceof Scalar s && s.val instanceof Number n ? n.doubleValue() : 0.0;
    }
    public String getString(String k) {
        Tag t = tags.get(k);
        return t instanceof StringTag s ? s.value : "";
    }
    public int[] getIntArray(String k) {
        Tag t = tags.get(k);
        return t instanceof Scalar s && s.val instanceof int[] a ? a.clone() : new int[0];
    }
    public CompoundTag getCompound(String k) {
        Tag t = tags.get(k);
        return t instanceof CompoundTag c ? c : new CompoundTag();
    }
    public ListTag getList(String k, int elemType) {
        Tag t = tags.get(k);
        if (!(t instanceof ListTag l)) return new ListTag();
        if (l.isEmpty() || elemType == 0 || l.get(0).getId() == elemType) return l;
        return new ListTag();
    }
    @Override public CompoundTag copy() {
        CompoundTag out = new CompoundTag();
        for (var e : tags.entrySet()) out.tags.put(e.getKey(), e.getValue().copy());
        return out;
    }
    public CompoundTag merge(CompoundTag other) {
        for (String key : other.tags.keySet()) {
            Tag v = other.tags.get(key);
            if (v.getId() == 10 && contains(key, 10)) {
                getCompound(key).merge((CompoundTag) v);
            } else {
                tags.put(key, v.copy());
            }
        }
        return this;
    }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : tags.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.append('}').toString();
    }
}
''',
    'net/minecraft/core/component/DataComponentType.java': '''
package net.minecraft.core.component;
public interface DataComponentType<T> {}
''',
    'net/minecraft/core/component/DataComponents.java': '''
package net.minecraft.core.component;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
public final class DataComponents {
    public static final DataComponentType<CustomData> ENTITY_DATA = new DataComponentType<>() {};
    public static final DataComponentType<CustomData> CUSTOM_DATA = new DataComponentType<>() {};
    public static final DataComponentType<Component> CUSTOM_NAME = new DataComponentType<>() {};
}
''',
    'net/minecraft/world/item/component/CustomData.java': '''
package net.minecraft.world.item.component;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
public final class CustomData {
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    private final CompoundTag tag;
    private CustomData(CompoundTag tag) { this.tag = tag; }
    public static CustomData of(CompoundTag tag) { return new CustomData(tag.copy()); }
    public boolean isEmpty() { return tag.isEmpty(); }
    public CompoundTag copyTag() { return tag.copy(); }
    public CompoundTag getUnsafe() { return tag; }
    /** Exact validation of Minecraft 1.21.1 CustomData.CODEC_WITH_ID (CustomData.java:33-36). */
    public CompoundTag encodeWithIdOrThrow() {
        if (!tag.contains("id", 8)) {
            throw new IllegalStateException("Missing id for entity in: " + tag);
        }
        return tag.copy();
    }
    /** Exact logic of Minecraft 1.21.1 CustomData.loadInto(Entity) (CustomData.java:80-86). */
    public void loadInto(Entity entity) {
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        UUID uuid = entity.getUUID();
        saved.merge(this.tag);
        entity.load(saved);
        entity.setUUID(uuid);
    }
}
''',
    'net/minecraft/world/level/ItemLike.java': '''
package net.minecraft.world.level;
import net.minecraft.world.item.Item;
public interface ItemLike { Item asItem(); }
''',
    'net/minecraft/world/item/Item.java': '''
package net.minecraft.world.item;
import net.minecraft.world.level.ItemLike;
public class Item implements ItemLike {
    public static class Properties {
        public Properties stacksTo(int n) { return this; }
    }
    public Item() {}
    public Item(Properties p) {}
    @Override public Item asItem() { return this; }
}
''',
    'net/minecraft/world/item/SpawnEggItem.java': '''
package net.minecraft.world.item;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
public class SpawnEggItem extends Item {
    public static final Map<ResourceLocation, EntityType<?>> REGISTRY = new HashMap<>();
    protected EntityType<?> defaultType;
    public SpawnEggItem(EntityType<? extends Mob> type, int bg, int fg, Properties p) {
        super(p);
        this.defaultType = type;
    }
    protected EntityType<?> getDefaultType() { return defaultType; }
    /** Mirrors SpawnEggItem.getType(ItemStack) in MC 1.21.1 (SpawnEggItem.java:144-147). */
    public EntityType<?> getType(ItemStack stack) {
        CustomData cd = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        if (!cd.isEmpty()) {
            CompoundTag t = cd.getUnsafe();
            if (t.contains("id", 8)) {
                ResourceLocation rl = ResourceLocation.tryParse(t.getString("id"));
                EntityType<?> found = rl == null ? null : REGISTRY.get(rl);
                if (found != null) return found;
            }
        }
        return getDefaultType();
    }
    /** Mirrors SpawnEggItem.useOn -> EntityType.spawn -> appendDefaultStackConfig. */
    public Entity spawnFromEgg(ServerLevel level, ItemStack stack, Player player) {
        EntityType<?> type = getType(stack);
        return type.spawnFromStack(level, stack, player);
    }
}
''',
    'net/neoforged/neoforge/common/DeferredSpawnEggItem.java': '''
package net.neoforged.neoforge.common;
import java.util.function.Supplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.SpawnEggItem;
public class DeferredSpawnEggItem extends SpawnEggItem {
    private final Supplier<? extends EntityType<? extends Mob>> typeSupplier;
    public DeferredSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> sup, int bg, int fg, Properties p) {
        super(null, bg, fg, p);
        this.typeSupplier = sup;
    }
    @Override protected EntityType<?> getDefaultType() { return typeSupplier.get(); }
}
''',
    'net/minecraft/world/item/ItemStack.java': '''
package net.minecraft.world.item;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack((Item) null, 0);
    private final Item item;
    private int count;
    private final Map<DataComponentType<?>, Object> components = new IdentityHashMap<>();
    public ItemStack(ItemLike like) { this(like == null ? null : like.asItem(), 1); }
    public ItemStack(ItemLike like, int count) { this.item = like == null ? null : like.asItem(); this.count = count; }
    public Item getItem() { return item; }
    public boolean is(Item other) { return item != null && item == other; }
    public boolean isEmpty() { return item == null || count <= 0; }
    public int getCount() { return count; }
    @SuppressWarnings("unchecked")
    public <T> T set(DataComponentType<? super T> type, T val) {
        return (T) (val == null ? components.remove(type) : components.put(type, val));
    }
    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<? extends T> type) { return (T) components.get(type); }
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(DataComponentType<? extends T> type, T def) {
        Object v = components.get(type);
        return v == null ? def : (T) v;
    }
    public ItemStack copy() {
        if (isEmpty()) return EMPTY;
        ItemStack out = new ItemStack(item, count);
        out.components.putAll(components);
        return out;
    }
    /** Mirrors ItemStack.save(Provider, Tag) in MC 1.21.1 (ItemStack.java:408-412). */
    public Tag save(HolderLookup.Provider provider) {
        if (isEmpty()) throw new IllegalStateException("Cannot encode empty ItemStack");
        CompoundTag out = new CompoundTag();
        out.putInt("count", count);
        CustomData entityData = get(DataComponents.ENTITY_DATA);
        if (entityData != null) {
            CompoundTag encoded = entityData.encodeWithIdOrThrow();
            CompoundTag comps = new CompoundTag();
            comps.put("minecraft:entity_data", encoded);
            out.put("components", comps);
        }
        return out;
    }
    public static Optional<ItemStack> parse(HolderLookup.Provider p, Tag t) { return Optional.of(EMPTY); }
    public Tag saveOptional(HolderLookup.Provider p) { return isEmpty() ? new CompoundTag() : save(p); }
}
''',
    'net/minecraft/network/chat/Style.java': '''
package net.minecraft.network.chat;
public class Style {
    public int color;
    public Style withColor(int c) { this.color = c; return this; }
}
''',
    'net/minecraft/network/chat/Component.java': '''
package net.minecraft.network.chat;
import java.util.Arrays;
public interface Component {
    String getString();
    static MutableComponent literal(String s) { return new MutableComponent("literal:" + s, s); }
    static MutableComponent translatable(String k) { return new MutableComponent(k, k); }
    static MutableComponent translatable(String k, Object... args) {
        String[] rendered = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            rendered[i] = args[i] instanceof Component c ? c.getString() : String.valueOf(args[i]);
        }
        return new MutableComponent(k + Arrays.toString(rendered), k + ":" + String.join(",", rendered));
    }
}
''',
    'net/minecraft/network/chat/MutableComponent.java': '''
package net.minecraft.network.chat;
import java.util.function.UnaryOperator;
public class MutableComponent implements Component {
    public final String keyRepr;
    private final String text;
    public Style style = new Style();
    public MutableComponent(String keyRepr, String text) { this.keyRepr = keyRepr; this.text = text; }
    @Override public String getString() { return text; }
    public MutableComponent withStyle(UnaryOperator<Style> op) { style = op.apply(style); return this; }
}
''',
    'net/minecraft/network/syncher/EntityDataAccessor.java': '''
package net.minecraft.network.syncher;
public class EntityDataAccessor<T> {}
''',
    'net/minecraft/network/syncher/EntityDataSerializer.java': '''
package net.minecraft.network.syncher;
public interface EntityDataSerializer<T> {}
''',
    'net/minecraft/network/syncher/EntityDataSerializers.java': '''
package net.minecraft.network.syncher;
public final class EntityDataSerializers {
    public static final EntityDataSerializer<Boolean> BOOLEAN = new EntityDataSerializer<>() {};
    public static final EntityDataSerializer<Byte> BYTE = new EntityDataSerializer<>() {};
    public static final EntityDataSerializer<Integer> INT = new EntityDataSerializer<>() {};
    public static final EntityDataSerializer<Float> FLOAT = new EntityDataSerializer<>() {};
    public static final EntityDataSerializer<String> STRING = new EntityDataSerializer<>() {};
}
''',
    'net/minecraft/network/syncher/SynchedEntityData.java': '''
package net.minecraft.network.syncher;
import java.util.*;
public class SynchedEntityData {
    public static class Builder {
        public <T> Builder define(EntityDataAccessor<T> a, T def) { return this; }
    }
    private final Map<EntityDataAccessor<?>, Object> map = new IdentityHashMap<>();
    public static <T> EntityDataAccessor<T> defineId(Class<?> cls, EntityDataSerializer<T> ser) {
        return new EntityDataAccessor<>();
    }
    public <T> void set(EntityDataAccessor<T> a, T val) { map.put(a, val); }
    @SuppressWarnings("unchecked")
    public <T> T get(EntityDataAccessor<T> a) {
        Object v = map.get(a);
        return (T) (v == null ? Boolean.FALSE : v);
    }
}
''',
    'net/minecraft/core/Holder.java': '''
package net.minecraft.core;
public interface Holder<T> {
    default T value() { return null; }
    class Reference<T> implements Holder<T> {}
}
''',
    'net/minecraft/core/HolderLookup.java': '''
package net.minecraft.core;
public interface HolderLookup {
    interface Provider {}
}
''',
    'net/minecraft/core/RegistryAccess.java': '''
package net.minecraft.core;
public interface RegistryAccess extends HolderLookup.Provider {
    interface Frozen extends RegistryAccess {}
    Frozen EMPTY = new Frozen() {};
}
''',
    'net/minecraft/world/entity/MobCategory.java': '''
package net.minecraft.world.entity;
public enum MobCategory { CREATURE, MISC }
''',
    'net/minecraft/world/entity/EntityType.java': '''
package net.minecraft.world.entity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
public class EntityType<T extends Entity> {
    public interface EntityFactory<T extends Entity> { T create(EntityType<T> type, Level level); }
    public static class Builder<T extends Entity> {
        private final EntityFactory<T> factory;
        private Builder(EntityFactory<T> f) { this.factory = f; }
        public static <T extends Entity> Builder<T> of(EntityFactory<T> f, MobCategory c) { return new Builder<>(f); }
        public Builder<T> sized(float w, float h) { return this; }
        public Builder<T> eyeHeight(float h) { return this; }
        public Builder<T> clientTrackingRange(int r) { return this; }
        public EntityType<T> build(String key) {
            EntityType<T> t = new EntityType<>(factory, key);
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null) SpawnEggItem.REGISTRY.put(rl, t);
            return t;
        }
    }
    private final EntityFactory<T> factory;
    private final String idKey;
    public EntityType(EntityFactory<T> f, String idKey) { this.factory = f; this.idKey = idKey; }
    public String keyString() { return idKey; }
    public Component getDescription() { return Component.literal("entity." + idKey); }
    public T create(Level level) { return factory.create(this, level); }
    public T spawnFromStack(ServerLevel level, ItemStack stack, Player player) {
        T entity = create(level);
        Component cname = stack.get(DataComponents.CUSTOM_NAME);
        if (cname != null) entity.setCustomName(cname);
        CustomData cd = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        if (!cd.isEmpty()) cd.loadInto(entity);
        level.addEntity(entity);
        return entity;
    }
}
''',
    'net/minecraft/world/entity/ai/attributes/Attribute.java': '''
package net.minecraft.world.entity.ai.attributes;
public class Attribute {}
''',
    'net/minecraft/world/entity/ai/attributes/Attributes.java': '''
package net.minecraft.world.entity.ai.attributes;
import net.minecraft.core.Holder;
public final class Attributes {
    public static final Holder<Attribute> SCALE = new Holder.Reference<>();
    public static final Holder<Attribute> MAX_HEALTH = new Holder.Reference<>();
}
''',
    'net/minecraft/world/entity/ai/attributes/AttributeInstance.java': '''
package net.minecraft.world.entity.ai.attributes;
public class AttributeInstance {
    public double baseValue = 1.0;
    public void setBaseValue(double v) { this.baseValue = v; }
    public double getBaseValue() { return baseValue; }
}
''',
    'net/minecraft/world/entity/ai/navigation/PathNavigation.java': '''
package net.minecraft.world.entity.ai.navigation;
public class PathNavigation {
    public float multiplier = 1.0f;
    public void setMaxVisitedNodesMultiplier(float f) { this.multiplier = f; }
}
''',
    'net/minecraft/world/entity/Entity.java': '''
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
public class Entity implements IEntityExtension {
    private static int NEXT_ID = 100;
    private final EntityType<?> type;
    protected Level level;
    private UUID uuid = UUID.randomUUID();
    private int id = NEXT_ID++;
    public boolean alive = true;
    public boolean noGravity = false;
    private Component customName;
    private boolean customNameVisible;
    protected final SynchedEntityData entityData = new SynchedEntityData();
    private final CompoundTag persistentData = new CompoundTag();
    public Entity(EntityType<?> type, Level level) { this.type = type; this.level = level; }
    public EntityType<?> getType() { return type; }
    public Level level() { return level; }
    public UUID getUUID() { return uuid; }
    public void setUUID(UUID u) { this.uuid = u; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public boolean isAlive() { return alive; }
    public void setNoGravity(boolean v) { this.noGravity = v; }
    public void setCustomName(Component c) { this.customName = c; }
    public Component getCustomName() { return customName; }
    public void setCustomNameVisible(boolean v) { this.customNameVisible = v; }
    public boolean isCustomNameVisible() { return customNameVisible; }
    public void refreshDimensions() {}
    @Override public CompoundTag getPersistentData() { return persistentData; }
    protected void readAdditionalSaveData(CompoundTag tag) {}
    protected void addAdditionalSaveData(CompoundTag tag) {}
    public CompoundTag saveWithoutId(CompoundTag tag) {
        try {
            addAdditionalSaveData(tag);
            return tag;
        } catch (Throwable t) {
            throw new net.minecraft.ReportedException("Saving entity NBT", t);
        }
    }
    public void load(CompoundTag tag) { readAdditionalSaveData(tag); }
}
''',
    'net/minecraft/world/entity/LivingEntity.java': '''
package net.minecraft.world.entity;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
public class LivingEntity extends Entity {
    private final Map<Holder<Attribute>, AttributeInstance> attrs = new IdentityHashMap<>();
    public float health = 20f;
    public LivingEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        attrs.put(Attributes.SCALE, new AttributeInstance());
        attrs.put(Attributes.MAX_HEALTH, new AttributeInstance());
    }
    public AttributeInstance getAttribute(Holder<Attribute> h) { return attrs.get(h); }
    public void setHealth(float h) { this.health = h; }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
''',
    'net/minecraft/world/entity/Mob.java': '''
package net.minecraft.world.entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
public class Mob extends LivingEntity {
    protected int xpReward;
    private boolean persistenceRequired;
    private final PathNavigation nav = new PathNavigation();
    public Mob(EntityType<? extends Mob> type, Level level) { super(type, level); }
    public void setPersistenceRequired() { this.persistenceRequired = true; }
    public PathNavigation getNavigation() { return nav; }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
''',
    'net/minecraft/world/entity/PathfinderMob.java': '''
package net.minecraft.world.entity;
import net.minecraft.world.level.Level;
public class PathfinderMob extends Mob {
    protected PathfinderMob(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }
}
''',
    'net/minecraft/world/entity/item/ItemEntity.java': '''
package net.minecraft.world.entity.item;
import net.minecraft.world.item.ItemStack;
public class ItemEntity {
    public final ItemStack stack;
    public ItemEntity(ItemStack s) { this.stack = s; }
}
''',
    'net/minecraft/world/entity/player/Inventory.java': '''
package net.minecraft.world.entity.player;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
public class Inventory {
    public boolean full = false;
    public final List<ItemStack> items = new ArrayList<>();
    public boolean add(ItemStack s) {
        if (full) return false;
        items.add(s);
        return true;
    }
    public ListTag save(ListTag list) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                CompoundTag slot = (CompoundTag) s.save(RegistryAccess.EMPTY);
                slot.putByte("Slot", (byte) i);
                list.add(slot);
            }
        }
        return list;
    }
}
''',
    'net/minecraft/world/inventory/AbstractContainerMenu.java': '''
package net.minecraft.world.inventory;
public class AbstractContainerMenu {
    public int broadcasts = 0;
    public void broadcastChanges() { broadcasts++; }
}
''',
    'net/minecraft/world/entity/player/Player.java': '''
package net.minecraft.world.entity.player;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
public class Player extends LivingEntity {
    protected final Inventory inventory = new Inventory();
    public AbstractContainerMenu containerMenu = new AbstractContainerMenu();
    public Player(Level level) { super(null, level); }
    public Inventory getInventory() { return inventory; }
}
''',
    'net/minecraft/server/MinecraftServer.java': '''
package net.minecraft.server;
public class MinecraftServer {}
''',
    'net/minecraft/world/level/Level.java': '''
package net.minecraft.world.level;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
public class Level {
    protected final Map<Integer, Entity> byId = new HashMap<>();
    protected final Map<UUID, Entity> byUuid = new HashMap<>();
    public boolean isClientSide() { return false; }
    public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
    public int getMinBuildHeight() { return -64; }
    public int getMaxBuildHeight() { return 320; }
    public Entity getEntity(int id) { return byId.get(id); }
    public void addEntity(Entity e) {
        byId.put(e.getId(), e);
        byUuid.put(e.getUUID(), e);
    }
}
''',
    'net/minecraft/server/level/ServerLevel.java': '''
package net.minecraft.server.level;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class ServerLevel extends Level {
    private final MinecraftServer server = new MinecraftServer();
    public MinecraftServer getServer() { return server; }
    public Entity getEntity(UUID uuid) { return byUuid.get(uuid); }
}
''',
    'net/minecraft/server/level/ServerPlayer.java': '''
package net.minecraft.server.level;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
public class ServerPlayer extends Player {
    public int permLevel = 2;
    public final List<Component> messages = new ArrayList<>();
    public final List<ItemStack> dropped = new ArrayList<>();
    public ServerPlayer(ServerLevel level) { super(level); }
    public boolean hasPermissions(int p) { return permLevel >= p; }
    public void displayClientMessage(Component c, boolean actionBar) { messages.add(c); }
    public ItemEntity drop(ItemStack stack, boolean randomly) {
        dropped.add(stack);
        return new ItemEntity(stack);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", inventory.save(new ListTag()));
    }
}
''',
    'net/minecraft/network/FriendlyByteBuf.java': '''
package net.minecraft.network;
import java.util.*;
public class FriendlyByteBuf {
    private final List<Integer> ints = new ArrayList<>();
    private int idx = 0;
    public FriendlyByteBuf writeVarInt(int v) { ints.add(v); return this; }
    public int readVarInt() { return ints.get(idx++); }
}
''',
    'net/minecraft/network/RegistryFriendlyByteBuf.java': '''
package net.minecraft.network;
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {}
''',
    'net/minecraft/network/protocol/common/custom/CustomPacketPayload.java': '''
package net.minecraft.network.protocol.common.custom;
import net.minecraft.resources.ResourceLocation;
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
''',
    'net/minecraft/network/codec/StreamEncoder.java': '''
package net.minecraft.network.codec;
public interface StreamEncoder<B, V> { void encode(B buf, V val); }
''',
    'net/minecraft/network/codec/StreamDecoder.java': '''
package net.minecraft.network.codec;
public interface StreamDecoder<B, V> { V decode(B buf); }
''',
    'net/minecraft/network/codec/StreamCodec.java': '''
package net.minecraft.network.codec;
public interface StreamCodec<B, V> {
    void encode(B buf, V val);
    V decode(B buf);
    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> enc, StreamDecoder<B, V> dec) {
        return new StreamCodec<>() {
            @Override public void encode(B b, V v) { enc.encode(b, v); }
            @Override public V decode(B b) { return dec.decode(b); }
        };
    }
}
''',
    'net/neoforged/neoforge/network/handling/IPayloadContext.java': '''
package net.neoforged.neoforge.network.handling;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.player.Player;
public interface IPayloadContext {
    Player player();
    CompletableFuture<Void> enqueueWork(Runnable r);
}
''',
    'net/neoforged/neoforge/network/handling/IPayloadHandler.java': '''
package net.neoforged.neoforge.network.handling;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface IPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext ctx);
}
''',
    'net/neoforged/neoforge/network/registration/PayloadRegistrar.java': '''
package net.neoforged.neoforge.network.registration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
public class PayloadRegistrar {
    public boolean optional = false;
    public CustomPacketPayload.Type<?> serverType;
    public StreamCodec<?, ?> serverCodec;
    public IPayloadHandler<?> serverHandler;
    public PayloadRegistrar optional() { this.optional = true; return this; }
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(
        CustomPacketPayload.Type<T> t, StreamCodec<? super RegistryFriendlyByteBuf, T> c, IPayloadHandler<T> h
    ) {
        this.serverType = t; this.serverCodec = c; this.serverHandler = h;
        return this;
    }
}
''',
    'net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent.java': '''
package net.neoforged.neoforge.network.event;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
public class RegisterPayloadHandlersEvent {
    public final PayloadRegistrar reg = new PayloadRegistrar();
    public String version;
    public PayloadRegistrar registrar(String v) { this.version = v; return reg; }
}
''',
    'net/neoforged/neoforge/network/PacketDistributor.java': '''
package net.neoforged.neoforge.network;
import java.util.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class PacketDistributor {
    public static final List<CustomPacketPayload> SENT_TO_SERVER = new ArrayList<>();
    public static void sendToServer(CustomPacketPayload first, CustomPacketPayload... rest) {
        SENT_TO_SERVER.add(first);
        Collections.addAll(SENT_TO_SERVER, rest);
    }
}
''',
    'net/minecraft/world/phys/HitResult.java': '''
package net.minecraft.world.phys;
public abstract class HitResult {}
''',
    'net/minecraft/world/phys/EntityHitResult.java': '''
package net.minecraft.world.phys;
import net.minecraft.world.entity.Entity;
public class EntityHitResult extends HitResult {
    private final Entity entity;
    public EntityHitResult(Entity e) { this.entity = e; }
    public Entity getEntity() { return entity; }
}
''',
    'net/minecraft/client/gui/screens/Screen.java': '''
package net.minecraft.client.gui.screens;
public class Screen {}
''',
    'net/minecraft/client/Minecraft.java': '''
package net.minecraft.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.HitResult;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public Screen screen;
    public HitResult hitResult;
    public static Minecraft getInstance() { return INSTANCE; }
}
''',
    'net/neoforged/neoforge/client/event/InputEvent.java': '''
package net.neoforged.neoforge.client.event;
public class InputEvent {
    public static class InteractionKeyMappingTriggered {
        private final boolean pickBlock;
        public boolean canceled = false;
        public boolean swingHand = true;
        public InteractionKeyMappingTriggered(boolean pickBlock) { this.pickBlock = pickBlock; }
        public boolean isPickBlock() { return pickBlock; }
        public void setCanceled(boolean c) { this.canceled = c; }
        public void setSwingHand(boolean s) { this.swingHand = s; }
    }
}
''',
    'dev/settledlands/npc/NpcRelations.java': '''
package dev.settledlands.npc;
import java.util.*;
import net.minecraft.server.MinecraftServer;
public class NpcRelations {
    private static final NpcRelations INSTANCE = new NpcRelations();
    public int migrated = 0;
    public static NpcRelations get(MinecraftServer s) { return INSTANCE; }
    public void migrateNpc(UUID a, UUID b) { migrated++; }
    public void migrateQuestSlotsToIds(UUID u, List<String> ids) {}
}
''',
    'testing/NpcCopy253Scenario.java': '''
package testing;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.settledlands.npc.*;
import dev.settledlands.npc.client.NpcPickCopy;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.*;

public class NpcCopy253Scenario {
    static void ok(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        String mode = args[0];
        ServerLevel level = new ServerLevel();
        GuideNpc src = new GuideNpc(NpcContent.guide(), level);
        src.profile().name = "Проводник";
        src.profile().texture = "ivy_oleander";
        src.profile().scale = 125;
        src.profile().pose = 2;
        src.profile().aiMode = 1;
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = graph.add(0, 16, 24);
        graph.entry = line.id;
        graph.enabled = true;
        ok(NpcStatsGraph.ensure(graph), "ensure stats node");
        NpcDialogueGraph.Node stats = NpcStatsGraph.find(graph);
        stats.varValue = 14;
        stats.checkRadius = 13;
        stats.checkCooldown = 12;
        src.profile().dialogueGraph = graph;
        src.applyProfile();
        level.addEntity(src);

        if (mode.equals("reproduce-252")) {
            ItemStack broken = NpcCopyPickPayload.buildCopyEgg(src);
            CustomData cd = broken.get(DataComponents.ENTITY_DATA);
            ok(cd != null && !cd.getUnsafe().contains("id", 8), "252 egg unexpectedly had string id");
            try {
                broken.save(RegistryAccess.EMPTY);
                throw new AssertionError("252 ItemStack.save should fail with Missing id for entity");
            } catch (IllegalStateException ex) {
                ok(ex.getMessage().startsWith("Missing id for entity in: {Guide:{sl_copy_src:[I;"),
                    "unexpected 252 ItemStack.save error: " + ex.getMessage());
            }
            ServerPlayer player = new ServerPlayer(level);
            NpcCopyPickPayload.register(new RegisterPayloadHandlersEvent());
            player.getInventory().add(broken);
            try {
                player.saveWithoutId(new CompoundTag());
                throw new AssertionError("252 ServerPlayer.saveWithoutId should fail with ReportedException");
            } catch (ReportedException re) {
                ok("Saving entity NBT".equals(re.getMessage())
                    && re.getCause() instanceof IllegalStateException
                    && re.getCause().getMessage().contains("Missing id for entity in:"),
                    "unexpected 252 player save crash: " + re);
            }
            System.out.println("CONFIRMED build 252 NPC copy freeze/crash: Missing id for entity in DataComponents.ENTITY_DATA");
            return;
        }

        ok(mode.equals("verify-253"), "unknown mode: " + mode);

        // 1. Client pick-block triggers NpcCopyPickPayload(src.getId()) and cancels vanilla pick
        PacketDistributor.SENT_TO_SERVER.clear();
        Minecraft mc = Minecraft.getInstance();
        mc.screen = null;
        mc.hitResult = new EntityHitResult(src);
        InputEvent.InteractionKeyMappingTriggered nonPick = new InputEvent.InteractionKeyMappingTriggered(false);
        NpcPickCopy.onKeyTriggered(nonPick);
        ok(!nonPick.canceled && PacketDistributor.SENT_TO_SERVER.isEmpty(), "non-pick should be ignored");

        InputEvent.InteractionKeyMappingTriggered pick = new InputEvent.InteractionKeyMappingTriggered(true);
        NpcPickCopy.onKeyTriggered(pick);
        ok(pick.canceled && !pick.swingHand && PacketDistributor.SENT_TO_SERVER.size() == 1, "pick-block must send 1 packet");
        NpcCopyPickPayload sent = (NpcCopyPickPayload) PacketDistributor.SENT_TO_SERVER.get(0);
        ok(sent.entityId() == src.getId(), "wrong picked entityId");

        // 2. StreamCodec + PayloadRegistrar roundtrip
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf();
        NpcCopyPickPayload.CODEC.encode(buf, sent);
        NpcCopyPickPayload decoded = NpcCopyPickPayload.CODEC.decode(buf);
        ok(decoded.equals(sent) && decoded.type() == NpcCopyPickPayload.TYPE, "payload codec roundtrip");

        RegisterPayloadHandlersEvent regEvent = new RegisterPayloadHandlersEvent();
        NpcCopyPickPayload.register(regEvent);
        ok("1".equals(regEvent.version) && regEvent.reg.optional && regEvent.reg.serverType == NpcCopyPickPayload.TYPE, "registrar metadata");
        IPayloadHandler<CustomPacketPayload> handler = (IPayloadHandler<CustomPacketPayload>) regEvent.reg.serverHandler;

        // 3. Non-OP player denied cleanly without receiving an egg
        ServerPlayer nonOp = new ServerPlayer(level);
        nonOp.permLevel = 0;
        handler.handle(decoded, new IPayloadContext() {
            @Override public Player player() { return nonOp; }
            @Override public CompletableFuture<Void> enqueueWork(Runnable r) { r.run(); return CompletableFuture.completedFuture(null); }
        });
        ok(nonOp.getInventory().items.isEmpty() && nonOp.messages.size() == 1
            && nonOp.messages.get(0).getString().contains("message.settledlands.cfg.noperm"), "non-op permission check");

        // 4. OP player copies GuideNpc, saves inventory NBT cleanly, and places independent copy
        ServerPlayer op = new ServerPlayer(level);
        op.permLevel = 2;
        handler.handle(decoded, new IPayloadContext() {
            @Override public Player player() { return op; }
            @Override public CompletableFuture<Void> enqueueWork(Runnable r) { r.run(); return CompletableFuture.completedFuture(null); }
        });
        ok(op.getInventory().items.size() == 1 && op.containerMenu.broadcasts == 1, "egg added to OP inventory");
        ok(op.messages.size() == 1 && op.messages.get(0).getString().contains("message.settledlands.npc.copied")
            && op.messages.get(0).getString().contains("Проводник"), "copied feedback message");

        ItemStack egg = op.getInventory().items.get(0);
        ok(egg.is(NpcContent.spawnEgg()), "GuideNpc copy must use NpcContent.spawnEgg()");
        CustomData cd = egg.get(DataComponents.ENTITY_DATA);
        ok(cd != null && cd.getUnsafe().contains("id", 8), "ENTITY_DATA must contain TAG_STRING id");
        ok("settledlands:guide".equals(cd.getUnsafe().getString("id")), "GuideNpc id must be settledlands:guide");
        ok(src.getUUID().equals(cd.getUnsafe().getCompound("Guide").getUUID("sl_copy_src")), "Guide.sl_copy_src must match source UUID");
        Component customName = egg.get(DataComponents.CUSTOM_NAME);
        ok(customName instanceof MutableComponent mcName
            && mcName.keyRepr.startsWith("item.settledlands.guide_spawn_egg.copy")
            && customName.getString().contains("Проводник"), "copy egg custom name");

        // Client tooltip ItemStack.save + ServerPlayer NBT save MUST succeed without throwing!
        CompoundTag savedStack = (CompoundTag) egg.save(RegistryAccess.EMPTY);
        ok("settledlands:guide".equals(savedStack.getCompound("components").getCompound("minecraft:entity_data").getString("id")),
            "encoded item NBT must persist id=settledlands:guide");
        CompoundTag savedPlayer = op.saveWithoutId(new CompoundTag());
        ok(savedPlayer.getList("Inventory", 10).size() == 1, "ServerPlayer NBT save succeeded with copy egg in inventory");

        // Place copy egg via SpawnEggItem -> EntityType.spawnFromStack -> CustomData.loadInto -> GuideNpc.readAdditionalSaveData
        SpawnEggItem spawnEggItem = (SpawnEggItem) egg.getItem();
        ok(spawnEggItem.getType(egg) == NpcContent.guide(), "SpawnEggItem.getType must resolve NpcContent.guide()");
        Entity spawned = spawnEggItem.spawnFromEgg(level, egg, op);
        ok(spawned instanceof GuideNpc && !(spawned instanceof GuideTriggerNpc) && !(spawned instanceof GuideCorpseNpc), "spawned entity class");
        GuideNpc copy = (GuideNpc) spawned;
        ok(!copy.getUUID().equals(src.getUUID()), "spawned copy must retain its own distinct UUID");
        ok("Проводник".equals(copy.profile().name)
            && "ivy_oleander".equals(copy.profile().texture)
            && copy.profile().scale == 125
            && copy.profile().pose == 2
            && copy.profile().aiMode == 1, "copied profile attributes");
        ok(copy.getCustomName() != null && "literal:Проводник".equals(((MutableComponent) copy.getCustomName()).keyRepr),
            "applyProfile overwrote egg custom name with NPC profile name");
        ok(copy.profile().dialogueGraph != null && copy.profile().dialogueGraph != src.profile().dialogueGraph, "deep-copied dialogueGraph");
        ok(NpcStatsGraph.health(copy.profile().dialogueGraph) == 14
            && NpcStatsGraph.damage(copy.profile().dialogueGraph) == 13
            && NpcStatsGraph.speed(copy.profile().dialogueGraph) == 12, "copied Build 246 NPC characteristics 14/13/12");
        NpcStatsGraph.find(copy.profile().dialogueGraph).varValue = 20;
        ok(NpcStatsGraph.health(src.profile().dialogueGraph) == 14, "mutating copy stats must not mutate original NPC");

        // 5. Full inventory drops copy egg cleanly and dropped stack also saves cleanly
        op.getInventory().full = true;
        handler.handle(decoded, new IPayloadContext() {
            @Override public Player player() { return op; }
            @Override public CompletableFuture<Void> enqueueWork(Runnable r) { r.run(); return CompletableFuture.completedFuture(null); }
        });
        ok(op.dropped.size() == 1, "full inventory must drop copy egg");
        op.dropped.get(0).save(RegistryAccess.EMPTY);

        // 6. GuideTriggerNpc copy uses triggerEgg + settledlands:guide_trigger and spawns GuideTriggerNpc
        GuideTriggerNpc trig = new GuideTriggerNpc(NpcContent.guideTrigger(), level);
        trig.profile().name = "ТриггерСцены";
        level.addEntity(trig);
        ItemStack trigEgg = NpcCopyPickPayload.buildCopyEgg(trig);
        ok(trigEgg.is(NpcContent.triggerEgg()), "GuideTriggerNpc copy must use NpcContent.triggerEgg()");
        CustomData trigData = trigEgg.get(DataComponents.ENTITY_DATA);
        ok(trigData != null && trigData.getUnsafe().contains("id", 8)
            && "settledlands:guide_trigger".equals(trigData.getUnsafe().getString("id")),
            "GuideTriggerNpc id must be settledlands:guide_trigger");
        trigEgg.save(RegistryAccess.EMPTY);
        SpawnEggItem trigEggItem = (SpawnEggItem) trigEgg.getItem();
        ok(trigEggItem.getType(trigEgg) == NpcContent.guideTrigger(), "trigger egg resolves guideTrigger EntityType");
        Entity spawnedTrig = trigEggItem.spawnFromEgg(level, trigEgg, op);
        ok(spawnedTrig instanceof GuideTriggerNpc && "ТриггерСцены".equals(((GuideTriggerNpc) spawnedTrig).profile().name),
            "spawned GuideTriggerNpc copy");

        // 7. GuideCorpseNpc pick never produces CoffinItem or settledlands:guide_corpse
        GuideCorpseNpc corpse = new GuideCorpseNpc(NpcContent.guideCorpse(), level);
        corpse.profile().name = "";
        ItemStack safeEgg = NpcCopyPickPayload.buildCopyEgg(corpse);
        ok(safeEgg.is(NpcContent.spawnEgg()), "corpse fallback must use spawnEgg, never CoffinItem");
        ok("settledlands:guide".equals(safeEgg.get(DataComponents.ENTITY_DATA).getUnsafe().getString("id")),
            "corpse fallback must use settledlands:guide id");
        safeEgg.save(RegistryAccess.EMPTY);

        // 8. Unnamed GuideNpc falls back to EntityType description in CUSTOM_NAME and saves cleanly
        GuideNpc unnamed = new GuideNpc(NpcContent.guide(), level);
        unnamed.profile().name = "";
        ItemStack unnamedEgg = NpcCopyPickPayload.buildCopyEgg(unnamed);
        unnamedEgg.save(RegistryAccess.EMPTY);
        ok(unnamedEgg.get(DataComponents.CUSTOM_NAME).getString().contains("entity.settledlands:guide"),
            "unnamed NPC fallback description");

        System.out.println("PASS build 253 NPC copy: pick-block, codec, permissions, ENTITY_DATA id, ItemStack.save/player NBT save, independent GuideNpc+stats & GuideTriggerNpc spawn");
    }
}
''',
}


def make_copy_fixture(java: pathlib.Path, jar: pathlib.Path, tmp: pathlib.Path):
    fake = tmp / 'auto'
    fake.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, TMPDIR=str(fake))
    targets = [
        'dev/settledlands/npc/NpcCopyPickPayload#buildCopyEgg(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/client/NpcPickCopy#onKeyTriggered(net.neoforged.neoforge.client.event.InputEvent$InteractionKeyMappingTriggered)',
        'dev/settledlands/npc/NpcContent#spawnEgg',
        'dev/settledlands/npc/GuideNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/IvyCombatAi#launchFireball',
        'dev/settledlands/npc/GuideTriggerNpc#isInvisible',
        'dev/settledlands/npc/GuideCorpseNpc#isCoffin',
        'dev/settledlands/npc/CorpseRecovery#syncHealth(dev.settledlands.npc.GuideCorpseNpc)',
    ]
    r = subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        env=env, capture_output=True, text=True,
    )
    assert r.returncode == 0 and 'ИТОГ: OK' in r.stdout, (r.stdout, r.stderr)
    autos = list(fake.glob('jvmverify-*'))
    assert len(autos) == 1
    autostub = autos[0]
    for n in ('java', 'javax', 'jdk', 'sun', 'dev'):
        shutil.rmtree(autostub / n, ignore_errors=True)

    src_dir = tmp / 'src'
    out = tmp / 'out'
    out.mkdir()
    sources = []
    for rel, code in OVERRIDES.items():
        p = src_dir / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(code.strip() + '\n', encoding='utf-8')
        sources.append(p)
        # Remove autostub class of the same path so ECJ compiles strictly against our override
        cls_stub = autostub / rel.replace('.java', '.class')
        if cls_stub.exists():
            cls_stub.unlink()

    cp = ':'.join(map(str, [jar, autostub]))
    subprocess.run(
        [str(java), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'),
         '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', cp, '-d', str(out), *map(str, sorted(sources))],
        check=True,
    )
    return out, autostub


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--base-jar', type=pathlib.Path)
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()

    with tempfile.TemporaryDirectory(prefix='npccopy253-') as d:
        tmp = pathlib.Path(d)
        out, autostub = make_copy_fixture(java, jar, tmp)

        if a.base_jar is not None:
            base_jar = a.base_jar.resolve()
            cp_base = ':'.join(map(str, [out, base_jar, autostub]))
            r = subprocess.run([str(java), '-Xverify:all', '-cp', cp_base, 'testing.NpcCopy253Scenario', 'reproduce-252'], capture_output=True, text=True)
            assert r.returncode == 0 and 'CONFIRMED build 252 NPC copy freeze/crash' in r.stdout, (r.stdout, r.stderr)
            print(r.stdout.strip(), flush=True)

        cp = ':'.join(map(str, [out, jar, autostub]))
        r = subprocess.run([str(java), '-Xverify:all', '-cp', cp, 'testing.NpcCopy253Scenario', 'verify-253'], capture_output=True, text=True)
        assert r.returncode == 0 and 'PASS build 253 NPC copy:' in r.stdout, (r.stdout, r.stderr)
        print(r.stdout.strip(), flush=True)


if __name__ == '__main__':
    main()
