# Совместимость API и байткода — Build 254

- **Целевая среда:** Minecraft `1.21.1`, NeoForge `21.1.x`, Java `21` (`major = 65`).
- **Разделение client/server (dedicated server safety):**
  - `dev.settledlands.npc.NpcDefaultModel254` не имеет ни одной ссылки на `net.minecraft.client.*` или `dev.settledlands.npc.client.*` (проверено статически в `gate_build254.py` и в изолированном серверном `ClassLoader` в `tests/jvm_ui_ivy254.py`).
  - Клиентская отрисовка попапов `NpcDropdown` (`Z = +500.0F` в пределах ортографической GUI-проекции `GameRenderer` `[1000..21000]` -> `Z ∈ [-1000..+1000]`) и распаковка `/assets/settledlands/dragon_default/ivy_oleander.bbmodel` вынесены в `dev.settledlands.npc.client.NpcDropdownTop254`.
- **Сохранённые сигнатуры `NpcStatsGraph` / `NpcStatsGraphUi`:**
  - Все публичные точки входа сборки 246 (`ensure`, `draw`, `height`, `centerY`, `mouseClicked`, `mouseScrolled`, `isNode`) сохранены 1-в-1, поэтому `NpcDialogueGraphScreen.class` и `NpcGraphCanvas.class` остались побайтно идентичными сборке 253.
- **Обратная совместимость NBT `NpcProfile` и `GuideNpc`:**
  - Новый `NpcProfile()` и NBT без ключа `"Texture"` получают `"ivy_oleander.bbmodel"`.
  - Явно сохранённый `Texture: ""` (выбор «Стандартная (Guide NPC)» в дропдауне) сохраняется как `""`.
  - `GuideTriggerNpc` и `GuideCorpseNpc` принудительно сохраняют начальный `DATA_TEXTURE = ""`.
