#!/usr/bin/env python3
"""Comprehensive verification gate for Build 443 (Custom Colored Books + Lectern & Chiseled Bookshelf
chest graphs, dungeon loot filtering, and slot-exact lock crack)."""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build436_check
import build438_check
import build439_check
import build440_check
import build442_check
import disasm
import invoke_kind_check
import patch_build443

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = tuple(dict.fromkeys(build440_check.LAYERS + (ROOT / "build303-src" / "stubs443-src",)))

TEST_SHIMS = dict(build440_check.TEST_SHIMS)

TEST_SHIMS["net/minecraft/network/chat/TextColor.java"] = """\
package net.minecraft.network.chat;

public final class TextColor {
    private final int value;

    private TextColor(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb & 0xFFFFFF);
    }
}
"""

TEST_SHIMS["net/minecraft/network/chat/Style.java"] = """\
package net.minecraft.network.chat;

import net.minecraft.ChatFormatting;

public class Style {
    public static final Style EMPTY = new Style(null, null, null, null, null, null);

    public final TextColor color;
    public final Boolean bold;
    public final Boolean italic;
    public final Boolean underlined;
    public final Boolean strikethrough;
    public final Boolean obfuscated;

    private Style(TextColor color, Boolean bold, Boolean italic,
            Boolean underlined, Boolean strikethrough, Boolean obfuscated) {
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    public TextColor getColor() { return color; }
    public boolean isBold() { return Boolean.TRUE.equals(bold); }
    public boolean isItalic() { return Boolean.TRUE.equals(italic); }
    public boolean isUnderlined() { return Boolean.TRUE.equals(underlined); }
    public boolean isStrikethrough() { return Boolean.TRUE.equals(strikethrough); }
    public boolean isObfuscated() { return Boolean.TRUE.equals(obfuscated); }

    public Style withColor(TextColor c) {
        return new Style(c, bold, italic, underlined, strikethrough, obfuscated);
    }

    public Style withColor(ChatFormatting f) {
        Integer col = f == null ? null : f.getColor();
        return withColor(col == null ? null : TextColor.fromRgb(col.intValue()));
    }

    public Style withColor(int rgb) {
        return withColor(TextColor.fromRgb(rgb));
    }

    public Style withBold(Boolean b) {
        return new Style(color, b, italic, underlined, strikethrough, obfuscated);
    }

    public Style withItalic(Boolean i) {
        return new Style(color, bold, i, underlined, strikethrough, obfuscated);
    }

    public Style withUnderlined(Boolean u) {
        return new Style(color, bold, italic, u, strikethrough, obfuscated);
    }

    public Style withStrikethrough(Boolean s) {
        return new Style(color, bold, italic, underlined, s, obfuscated);
    }

    public Style withObfuscated(Boolean o) {
        return new Style(color, bold, italic, underlined, strikethrough, o);
    }

    public Style applyTo(Style parent) {
        if (parent == null || parent == EMPTY) return this;
        return new Style(
                this.color != null ? this.color : parent.color,
                this.bold != null ? this.bold : parent.bold,
                this.italic != null ? this.italic : parent.italic,
                this.underlined != null ? this.underlined : parent.underlined,
                this.strikethrough != null ? this.strikethrough : parent.strikethrough,
                this.obfuscated != null ? this.obfuscated : parent.obfuscated);
    }
}
"""

TEST_SHIMS["net/minecraft/network/chat/MutableComponent.java"] = """\
package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;

public class MutableComponent implements Component {
    private final String text;
    private Style style = Style.EMPTY;
    private final List<Component> siblings = new ArrayList<>();

    public MutableComponent(String s) {
        this.text = s == null ? "" : s;
    }

    @Override
    public String getString() {
        StringBuilder sb = new StringBuilder(text);
        for (Component sib : siblings) {
            sb.append(sib.getString());
        }
        return sb.toString();
    }

    @Override
    public Style getStyle() {
        return style;
    }

    @Override
    public List<Component> getSiblings() {
        return Collections.unmodifiableList(siblings);
    }

    public MutableComponent append(Component sibling) {
        if (sibling != null) siblings.add(sibling);
        return this;
    }

    public MutableComponent append(String s) {
        if (s != null && !s.isEmpty()) siblings.add(new MutableComponent(s));
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> updater) {
        if (updater != null) {
            Style next = updater.apply(this.style);
            if (next != null) this.style = next;
        }
        return this;
    }

    public MutableComponent withStyle(ChatFormatting formatting) {
        if (formatting != null) {
            if (formatting == ChatFormatting.RESET) {
                this.style = Style.EMPTY;
            } else if (formatting.isColor()) {
                this.style = this.style.withColor(formatting);
            } else if (formatting == ChatFormatting.BOLD) {
                this.style = this.style.withBold(Boolean.TRUE);
            } else if (formatting == ChatFormatting.ITALIC) {
                this.style = this.style.withItalic(Boolean.TRUE);
            } else if (formatting == ChatFormatting.UNDERLINE) {
                this.style = this.style.withUnderlined(Boolean.TRUE);
            } else if (formatting == ChatFormatting.STRIKETHROUGH) {
                this.style = this.style.withStrikethrough(Boolean.TRUE);
            } else if (formatting == ChatFormatting.OBFUSCATED) {
                this.style = this.style.withObfuscated(Boolean.TRUE);
            }
        }
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formattings) {
        if (formattings != null) {
            for (ChatFormatting f : formattings) withStyle(f);
        }
        return this;
    }

    @Override
    public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style base) {
        Style merged = this.style.applyTo(base);
        if (!text.isEmpty()) {
            Optional<T> res = consumer.accept(merged, text);
            if (res.isPresent()) return res;
        }
        for (Component sib : siblings) {
            Optional<T> res = sib.visit(consumer, merged);
            if (res.isPresent()) return res;
        }
        return Optional.empty();
    }
}
"""

TEST_SHIMS["net/minecraft/core/component/DataComponents.java"] = """\
package net.minecraft.core.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

public class DataComponents {
    public static final DataComponentType<Component> CUSTOM_NAME = new DataComponentType<>() {};
    public static final DataComponentType<WritableBookContent> WRITABLE_BOOK_CONTENT = new DataComponentType<>() {};
    public static final DataComponentType<WrittenBookContent> WRITTEN_BOOK_CONTENT = new DataComponentType<>() {};
}
"""

TEST_SHIMS["net/minecraft/tags/TagKey.java"] = """\
package net.minecraft.tags;

public final class TagKey<T> {
    public final String name;

    public TagKey(String name) {
        this.name = name;
    }
}
"""

TEST_SHIMS["net/minecraft/tags/ItemTags.java"] = """\
package net.minecraft.tags;

import net.minecraft.world.item.Item;

public final class ItemTags {
    public static final TagKey<Item> LECTERN_BOOKS = new TagKey<>("minecraft:lectern_books");
    public static final TagKey<Item> BOOKSHELF_BOOKS = new TagKey<>("minecraft:bookshelf_books");

    private ItemTags() {}
}
"""

TEST_SHIMS["net/minecraft/world/item/Items.java"] = """\
package net.minecraft.world.item;

public final class Items {
    public static final Item AIR = new Item();
    public static final Item BOOK = new Item();
    public static final Item WRITABLE_BOOK = new Item();
    public static final Item WRITTEN_BOOK = new Item();
    public static final Item ENCHANTED_BOOK = new Item();
    public static final Item KNOWLEDGE_BOOK = new Item();

    private Items() {}
}
"""

TEST_SHIMS["net/minecraft/world/item/ItemStack.java"] = """\
package net.minecraft.world.item;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;

public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();
    private final Item item;
    private int count;
    private final Map<DataComponentType<?>, Object> components = new IdentityHashMap<>();

    public ItemStack(net.minecraft.world.level.ItemLike item) { this(item, 1); }

    public ItemStack(net.minecraft.world.level.ItemLike item, int count) {
        this.item = item instanceof Item i ? i : null;
        this.count = count;
    }

    public ItemStack() { this.item = null; this.count = 0; }

    public boolean isEmpty() { return item == null || item == Items.AIR || count <= 0; }
    public Item getItem() { return item; }
    public boolean hasFoil() { return false; }
    public boolean is(Item other) { return item == other; }

    public boolean is(TagKey<Item> tag) {
        if (isEmpty() || tag == null) return false;
        if (tag == ItemTags.LECTERN_BOOKS) {
            return item == Items.WRITTEN_BOOK || item == Items.WRITABLE_BOOK;
        }
        if (tag == ItemTags.BOOKSHELF_BOOKS) {
            return item == Items.BOOK || item == Items.ENCHANTED_BOOK
                    || item == Items.WRITTEN_BOOK || item == Items.WRITABLE_BOOK
                    || item == Items.KNOWLEDGE_BOOK;
        }
        return false;
    }

    public ItemStack copy() {
        if (isEmpty()) return EMPTY;
        ItemStack out = new ItemStack(item, count);
        out.components.putAll(this.components);
        return out;
    }

    public ItemStack copyWithCount(int newCount) {
        if (isEmpty() || newCount <= 0) return EMPTY;
        ItemStack out = new ItemStack(item, newCount);
        out.components.putAll(this.components);
        return out;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public void shrink(int amount) { this.count = Math.max(0, this.count - amount); }
    public int getMaxStackSize() { return 64; }

    public Component getHoverName() {
        Component custom = get(DataComponents.CUSTOM_NAME);
        return custom != null ? custom : Component.literal(item == null ? "" : item.toString());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<? extends T> type) {
        return (T) components.get(type);
    }

    @SuppressWarnings("unchecked")
    public <T> T set(DataComponentType<? super T> type, T value) {
        if (value == null) return (T) components.remove(type);
        return (T) components.put(type, value);
    }
}
"""

TEST_SHIMS["net/minecraft/world/entity/player/Inventory.java"] = """\
package net.minecraft.world.entity.player;

import java.util.Arrays;
import net.minecraft.world.item.ItemStack;

public class Inventory {
    private final ItemStack[] slots = new ItemStack[36];

    public Inventory() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    public int getContainerSize() {
        return slots.length;
    }

    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= slots.length) return ItemStack.EMPTY;
        return slots[slot];
    }

    public boolean add(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || slots[i].isEmpty()) {
                slots[i] = stack.copy();
                stack.setCount(0);
                return true;
            }
        }
        return false;
    }
}
"""

TEST_SHIMS["net/minecraft/server/level/ServerPlayer.java"] = """\
package net.minecraft.server.level;

import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class ServerPlayer extends Player {
    public float fallDistance;
    private final Inventory inv = new Inventory();

    public ServerPlayer() {
        this.inventoryMenu = new InventoryMenu();
        this.containerMenu = this.inventoryMenu;
    }

    @Override public boolean isSpectator() { return false; }
    @Override public boolean hasPermissions(int level) { return true; }
    @Override public Inventory getInventory() { return inv; }

    public MinecraftServer getServer() { return null; }
    public ServerLevel serverLevel() { return null; }
    @Override public void clearFire() {}
    public void setDeltaMovement(Vec3 movement) {}
    @Override public float getYRot() { return 0.0F; }
    public float getXRot() { return 0.0F; }
    public boolean teleportTo(ServerLevel level, double x, double y, double z,
            Set<?> relatives, float yaw, float pitch) {
        return false;
    }
    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) { return null; }
    public ItemEntity drop(ItemStack stack, boolean dropAround) { return null; }
}
"""

TEST_SHIMS["net/minecraft/world/level/block/state/properties/BooleanProperty.java"] = """\
package net.minecraft.world.level.block.state.properties;

public class BooleanProperty extends Property<Boolean> {
    public final String name;

    public BooleanProperty() {
        this("bool");
    }

    public BooleanProperty(String name) {
        this.name = name;
    }
}
"""

TEST_SHIMS["net/minecraft/world/level/block/state/BlockState.java"] = """\
package net.minecraft.world.level.block.state;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState {
    private final Block block;
    private final boolean air;
    private final Map<Property<?>, Comparable<?>> values;

    public BlockState() { this(null, false); }

    public BlockState(Block block, boolean air) {
        this.block = block;
        this.air = air;
        this.values = new HashMap<>();
    }

    private BlockState(Block block, boolean air, Map<Property<?>, Comparable<?>> values) {
        this.block = block;
        this.air = air;
        this.values = values;
    }

    public Block getBlock() { return block; }

    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        return (T) values.get(property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object setValue(Property property, Comparable value) {
        Map<Property<?>, Comparable<?>> next = new HashMap<>(this.values);
        if (property != null) next.put(property, value);
        return new BlockState(block, air, next);
    }

    public boolean is(Block other) { return block == other; }
    public boolean isAir() { return air; }
    public boolean hasProperty(Property<?> property) { return property != null; }

    public net.minecraft.world.MenuProvider getMenuProvider(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        return null;
    }
}
"""

TEST_SHIMS["net/minecraft/world/level/block/LecternBlock.java"] = """\
package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LecternBlock extends Block {
    public static final BooleanProperty HAS_BOOK = new BooleanProperty("has_book");
    public static int RESET_CALLS = 0;

    public static void resetBookState(Entity entity, Level level, BlockPos pos,
            BlockState state, boolean hasBook) {
        RESET_CALLS++;
        if (level != null && pos != null && state != null) {
            BlockState next = (BlockState) state.setValue(HAS_BOOK, Boolean.valueOf(hasBook));
            level.setBlock(pos, next, 3);
        }
    }
}
"""

TEST_SHIMS["net/minecraft/world/level/block/entity/LecternBlockEntity.java"] = """\
package net.minecraft.world.level.block.entity;

import net.minecraft.world.item.ItemStack;

public class LecternBlockEntity extends BlockEntity {
    private ItemStack book = ItemStack.EMPTY;

    public boolean hasBook() {
        return book != null && !book.isEmpty();
    }

    public ItemStack getBook() {
        return book == null ? ItemStack.EMPTY : book;
    }

    public void setBook(ItemStack stack) {
        this.book = stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
"""

TEST_SHIMS["net/minecraft/world/level/block/ChiseledBookShelfBlock.java"] = """\
package net.minecraft.world.level.block;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class ChiseledBookShelfBlock extends Block {
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES =
            Collections.unmodifiableList(Arrays.asList(
                    new BooleanProperty("slot_0_occupied"),
                    new BooleanProperty("slot_1_occupied"),
                    new BooleanProperty("slot_2_occupied"),
                    new BooleanProperty("slot_3_occupied"),
                    new BooleanProperty("slot_4_occupied"),
                    new BooleanProperty("slot_5_occupied")));
}
"""

TEST_SHIMS["net/minecraft/world/level/block/entity/ChiseledBookShelfBlockEntity.java"] = """\
package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class ChiseledBookShelfBlockEntity extends BlockEntity implements Container {
    private final ItemStack[] slots = new ItemStack[6];

    public ChiseledBookShelfBlockEntity() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    @Override public int getContainerSize() { return slots.length; }

    @Override public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= slots.length) return ItemStack.EMPTY;
        return slots[slot];
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slots.length) return;
        slots[slot] = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override public int getMaxStackSize() { return 1; }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack != null && stack.is(ItemTags.BOOKSHELF_BOOKS);
    }
}
"""


def fail(msg):
    print("build443_check: FAIL - " + msg)
    return 1


def _cp_class_refs(class_bytes):
    p = disasm.parse_class(class_bytes)
    cp = p["cp"]
    refs = set()
    for i in range(1, len(cp)):
        entry = cp[i]
        if entry and entry[0] == 7:
            name = disasm.cls_name(cp, i)
            if name:
                refs.add(name)
    return refs


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = []
    problems.extend(build442_check.check_structural(jar, base_jar))

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        required = (
            "dev/settledlands/chest/CustomBook443.class",
            "dev/settledlands/chest/CustomBook443$BookDef.class",
            "dev/settledlands/chest/CustomBook443$ColorDef.class",
            "dev/settledlands/chest/CustomBook443$Span.class",
            "dev/settledlands/chest/CustomBookManager443.class",
            "dev/settledlands/chest/CustomBookNet443.class",
            "dev/settledlands/chest/CustomBookNet443$Books.class",
            "dev/settledlands/chest/CustomBookNet443$BookQuery.class",
            "dev/settledlands/chest/CustomBookNet443$BookData.class",
            "dev/settledlands/chest/CustomBookNet443$BookSave.class",
            "dev/settledlands/npc/client/LootPoolBookEditorScreen443.class",
        )
        for cls in required:
            if cls not in names:
                problems.append("missing class: " + cls)

        # Lang checks: all 43 new RU/EN keys present & no problematic '<=' glyph anywhere
        for lp in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            raw = zf.read(lp).decode("utf-8")
            if "\u2264" in raw:
                problems.append(f"{lp} contains glyph-overlapping '<=' symbol")
            table = json.loads(raw)
            expected = patch_build443.LANG_SET_RU if lp.endswith("ru_ru.json") else patch_build443.LANG_SET_EN
            for key in expected:
                if not table.get(key):
                    problems.append(f"{lp} missing or empty key: {key}")

        # Anti-blur invariant on LootPoolBookEditorScreen443
        scr = zf.read("dev/settledlands/npc/client/LootPoolBookEditorScreen443.class")
        parsed_scr = disasm.parse_class(scr)
        cp_scr = parsed_scr["cp"]
        has_bg = any((cp_scr[ni][1].decode() if isinstance(cp_scr[ni][1], bytes) else cp_scr[ni][1]) == "renderBackground"
                     for _a, ni, _d, _at in parsed_scr["methods"])
        if not has_bg:
            problems.append("LootPoolBookEditorScreen443 must override renderBackground to disable vanilla blur")
        if "net/minecraft/client/gui/screens/Screen.renderBackground" in build438_check.method_calls(scr, "renderBackground"):
            problems.append("LootPoolBookEditorScreen443#renderBackground calls super.renderBackground (would trigger blur)")
        if b"setTooltipForNextRenderPass" not in scr:
            problems.append("LootPoolBookEditorScreen443 should use Screen#setTooltipForNextRenderPass for custom tips")

        # Dedicated server safety: common classes must never reference client GUI classes
        forbidden_client = (
            "net/minecraft/client/Minecraft",
            "net/minecraft/client/gui/screens/Screen",
            "net/minecraft/client/gui/GuiGraphics",
            "dev/settledlands/npc/client/LootPoolBookEditorScreen443",
            "dev/settledlands/npc/client/LootPoolEditorScreen440",
            "dev/settledlands/npc/client/LootPoolItemPickerScreen440",
            "dev/settledlands/npc/client/LootPoolGearButton440",
            "dev/settledlands/npc/client/LootPoolCard436",
        )
        for common_cls in (
            "dev/settledlands/chest/CustomBook443.class",
            "dev/settledlands/chest/CustomBookManager443.class",
            "dev/settledlands/chest/CustomBookNet443.class",
            "dev/settledlands/chest/LootPool436.class",
            "dev/settledlands/chest/LootPoolManager436.class",
            "dev/settledlands/chest/ChestLootNet436.class",
            "dev/settledlands/chest/ChestGraph396.class",
            "dev/settledlands/chest/ChestGraph396Events.class",
        ):
            refs = _cp_class_refs(zf.read(common_cls))
            for bad in forbidden_client:
                if bad in refs:
                    problems.append(f"{common_cls} references client class {bad}")

        # Zero synthetic lambdas across Build 436-443 mixins & single-hook chest mixins
        for mixin_cls in (
            "dev/settledlands/mixin/NpcChestOpen396Mixin.class",
            "dev/settledlands/mixin/NpcChestPalette396Mixin.class",
            "dev/settledlands/mixin/LootPoolWiringMixin436.class",
            "dev/settledlands/mixin/LootPoolActionsMixin436.class",
            "dev/settledlands/mixin/LootPoolGraphUiMixin436.class",
            "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
            "dev/settledlands/mixin/LootPoolDropdownMixin440.class",
            "dev/settledlands/mixin/GuideTriggerSilentMixin441.class",
        ):
            parsed = disasm.parse_class(zf.read(mixin_cls))
            cp = parsed["cp"]
            for _acc, ni, _di, _attrs in parsed["methods"]:
                mname = cp[ni][1]
                if mname.startswith("lambda$"):
                    problems.append(f"{mixin_cls} declares synthetic lambda: {mname}")

        # Wiring checks
        if b"CustomBookNet443" not in zf.read("dev/settledlands/chest/ChestLootNet436.class"):
            problems.append("ChestLootNet436 does not register CustomBookNet443 payloads")
        if b"hasPermissions" not in zf.read("dev/settledlands/chest/CustomBookNet443.class"):
            problems.append("CustomBookNet443 missing OP permission check")
        cg = zf.read("dev/settledlands/chest/ChestGraph396.class")
        if b"LecternBlock" not in cg or b"ChiseledBookShelfBlock" not in cg:
            problems.append("ChestGraph396 missing LecternBlock / ChiseledBookShelfBlock")
        lpm = zf.read("dev/settledlands/chest/LootPoolManager436.class")
        if b"LecternBlockEntity" not in lpm or b"ChiseledBookShelfBlockEntity" not in lpm:
            problems.append("LootPoolManager436 missing LecternBlockEntity / ChiseledBookShelfBlockEntity")
        if b"captureClientHit" not in zf.read("dev/settledlands/chest/ChestNet397.class"):
            problems.append("ChestNet397 missing ChestOpen401.captureClientHit call")

    return problems


def compile_and_run_all_probes(jar: pathlib.Path):
    jar = jar.resolve()
    java = build439_check.find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        shims_out = td / "shims_out"
        joml_out = td / "joml_out"
        compat436_out = td / "compat436_out"
        modern_diegesis_out = td / "modern_diegesis_out"
        legacy_diegesis_out = td / "legacy_diegesis_out"
        probe_out = td / "probe_out"
        legacy_probe_out = td / "legacy_probe_out"
        nodiesis_out = td / "nodiesis_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, probe_out, legacy_probe_out, nodiesis_out, work_dir):
            d.mkdir(parents=True, exist_ok=True)

        for layer in LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise AssertionError("stubs compile failed:\n" + r1.stdout + r1.stderr)

        build439_check._compile_map(java, ecj, TEST_SHIMS, f"{stubs_out}:{jar}", shims_out)
        build439_check._compile_map(java, ecj, build439_check.JOML_SHIMS,
                                    f"{shims_out}:{jar}:{stubs_out}", joml_out)

        compat436_only = {k: v for k, v in build436_check.COMPAT_SOURCES.items()
                          if k != "Build436CompatProbe.java"}
        build439_check._compile_map(java, ecj, compat436_only,
                                    f"{shims_out}:{jar}:{stubs_out}", compat436_out)
        build439_check._compile_map(java, ecj, build439_check.DIEGESIS_MODERN_SHIMS,
                                    f"{joml_out}:{shims_out}:{jar}:{stubs_out}", modern_diegesis_out)
        build439_check._compile_map(java, ecj, build439_check.DIEGESIS_LEGACY_SHIMS,
                                    f"{joml_out}:{shims_out}:{jar}:{stubs_out}", legacy_diegesis_out)

        probes = [
            ("ChestGraph396Probe", ROOT / "tools" / "harness" / "ChestGraph396Probe.java"),
            ("ChestHack397Probe", ROOT / "tools" / "harness" / "ChestHack397Probe.java"),
            ("ChestClose402Probe", ROOT / "tools" / "harness" / "ChestClose402Probe.java"),
            ("Build436Probe", ROOT / "tools" / "harness" / "Build436Probe.java"),
            ("Build437Probe", ROOT / "tools" / "harness" / "Build437Probe.java"),
            ("Build438Probe", ROOT / "tools" / "harness" / "Build438Probe.java"),
            ("Build440Probe", ROOT / "tools" / "harness" / "Build440Probe.java"),
            ("Build441Probe", ROOT / "tools" / "harness" / "Build441Probe.java"),
            ("Build442Probe", ROOT / "tools" / "harness" / "Build442Probe.java"),
            ("Build443Probe", ROOT / "tools" / "harness" / "Build443Probe.java"),
        ]
        r_main = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{compat436_out}:{jar}:{stubs_out}", "-d", str(probe_out)]
            + [str(p) for _, p in probes],
            capture_output=True, text=True)
        if r_main.returncode != 0:
            raise AssertionError("Probes compile failed:\n" + r_main.stdout + r_main.stderr)

        for main_cls, _ in probes:
            r_run = subprocess.run(
                [java, "-cp", f"{probe_out}:{shims_out}:{compat436_out}:{jar}:{stubs_out}", main_cls],
                cwd=str(work_dir), capture_output=True, text=True)
            if r_run.returncode != 0:
                raise AssertionError(f"{main_cls} failed (rc={r_run.returncode}):\n{r_run.stdout}\n{r_run.stderr}")
            sys.stdout.write(r_run.stdout)

        # Build439Probe (modern Diegesis) + Build439LegacyProbe + Zero-Diegesis check
        r_439 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{modern_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "-d", str(probe_out), str(ROOT / "tools" / "harness" / "Build439Probe.java")],
            capture_output=True, text=True)
        if r_439.returncode != 0:
            raise AssertionError("Build439Probe compile failed:\n" + r_439.stdout + r_439.stderr)
        run_439 = subprocess.run(
            [java, "-cp", f"{probe_out}:{modern_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "Build439Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_439.returncode != 0:
            raise AssertionError(f"Build439Probe failed (rc={run_439.returncode}):\n{run_439.stdout}\n{run_439.stderr}")
        sys.stdout.write(run_439.stdout)

        r_leg = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{legacy_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "-d", str(legacy_probe_out), str(ROOT / "tools" / "harness" / "Build439LegacyProbe.java")],
            capture_output=True, text=True)
        if r_leg.returncode != 0:
            raise AssertionError("Build439LegacyProbe compile failed:\n" + r_leg.stdout + r_leg.stderr)
        run_leg = subprocess.run(
            [java, "-cp", f"{legacy_probe_out}:{legacy_diegesis_out}:{joml_out}:{shims_out}:{jar}:{stubs_out}",
             "Build439LegacyProbe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_leg.returncode != 0:
            raise AssertionError(f"Build439LegacyProbe failed (rc={run_leg.returncode}):\n{run_leg.stdout}\n{run_leg.stderr}")
        sys.stdout.write(run_leg.stdout)

        build439_check._compile_map(java, ecj,
                                    {"Build439NoDiegesisProbe.java": build439_check.NO_DIEGESIS_PROBE_SRC},
                                    f"{shims_out}:{jar}:{stubs_out}", nodiesis_out)
        run_nd = subprocess.run(
            [java, "-cp", f"{nodiesis_out}:{shims_out}:{jar}:{stubs_out}", "Build439NoDiegesisProbe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_nd.returncode != 0:
            raise AssertionError(f"Build439NoDiegesisProbe failed (rc={run_nd.returncode}):\n{run_nd.stdout}\n{run_nd.stderr}")
        sys.stdout.write(run_nd.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: build443_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    base442 = ROOT / "settledlands-0.8.0-build442-rarity-chance-crate-fix.jar"
    if base442.is_file():
        rc = invoke_kind_check.main([str(jar), "--base", str(base442)])
        if rc != 0:
            return fail("invoke_kind_check failed")

    problems = check_structural(jar, base442 if base442.is_file() else None)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        compile_and_run_all_probes(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build443_check: PASS (%s verified)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
