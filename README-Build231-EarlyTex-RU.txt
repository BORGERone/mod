SETTLED LANDS — BUILD 231 «earlytex»
===================================
Дата: 27.10.2026
Файл: settledlands-0.8.0-build231-earlytex.jar
Версия в меню модов: 0.8.0-build231-earlytex
База: build 229 «sablesweep» (build 230 ОТОЗВАН и в игру не идёт)

ЧТО ИСПРАВЛЕНО
--------------
Симптом: труп своего тела рисуется коричневой заглушкой (встроенная GuideModel +
guide_npc.png) вместо bbmodel'а.

Причина (подтверждена по байткоду):
* GuideRenderer.renderCorpse() берёт имя модели ТОЛЬКО из синхронизируемых данных:
  GuideNpc.texturePath() -> entityData(DATA_TEXTURE). NBT (`/data merge ... Texture`)
  не читается вообще — поэтому команда и не помогала.
* GuideNpc.applyProfile() пишет DATA_TEXTURE предпоследним оператором
  (offset 186..211 из 236), то есть ПОСЛЕ setCustomName, сжатия/масштаба,
  refreshDimensions() и setPoseIndex().
* applyProfile() вызывается из setProfile() и из
  PlayerCorpsePossession.dressBodyCorpse() — оба вызова внутри
  `catch (Throwable ignored)`. Любое исключение выше строки 186 = DATA_TEXTURE
  остаётся пустой строкой = BbModelManager.getModel("") = null = заглушка.
  В логе при этом НИ ОДНОЙ строки — поэтому баг и был «молчаливым».

Фикс build 231:
* Добавлен НОВЫЙ класс dev/settledlands/mixin/EarlyCorpseTextureMixin —
  миксин на GuideNpc, @Inject в HEAD applyProfile():
      String t = Objects.toString(this.profile().texture, "");
      this.entityData.set(GuideNpc.DATA_TEXTURE, t);
      System.out.print("[SettledLands] corpse texture pre-set: ");
      System.out.println(t);
  Теперь имя модели уходит в синхронизацию ДО того, как остальной метод
  успеет бросить исключение.
* Строка EarlyCorpseTextureMixin добавлена в settledlands.mixins.json.
* Больше НИЧЕГО не изменено: 350 из 352 записей jar'а побайтово идентичны 229.
  Байткод существующих классов (в т.ч. GuideNpc) не тронут — именно поэтому
  этот билд не может повторить краш build 230.

ЧТО СМОТРЕТЬ В ЛОГЕ
-------------------
После загрузки и появления трупа в latest.log будет строка:

    [SettledLands] corpse texture pre-set: <имя модели>

* Если там `ivy_oleander` (или другое имя bbmodel) — модель должна появиться.
  Если модель всё равно коричневая при непустом имени, значит имя не совпадает
  с файлом в .minecraft/settledlands/models (тогда пришлите эту строку —
  следующий фикс будет по ней, уже без гаданий).
* Если там пусто (`corpse texture pre-set:`) — значит профиль трупа вообще
  не заполнен (путь dressBodyCorpse пропущен), и лечится это в другом месте.

УСТАНОВКА
---------
1. Удалить из папки mods ВСЕ settledlands-*.jar, включая копии вида
   `settledlands-0.8.0-build230-modelfix(1).jar`.
2. Положить только settledlands-0.8.0-build231-earlytex.jar.
3. Проверить целостность (обязательно, «Сохранить ссылку как…», не «открыть страницу»):
       certutil -hashfile settledlands-0.8.0-build231-earlytex.jar SHA256
   Ожидается: c1ca5ebf9d642bbd671af1f2ee88db8dfd1299e1ed63ac9f676582d9e5373db3
   Размер: 1 538 081 байт.

ОТКАТ
-----
build 229 «sablesweep» — рабочий, но с этим дефектом:
settledlands-0.8.0-build229-sablesweep.jar
sha256 874c97ead4d9f93f5649f5a37749c52fe6f1cfec57f427365040dabf64837c76 (1 536 587 B)

НИЧЕГО ИЗ ПРОШЛОГО НЕ ОТКАЧЕНО
------------------------------
Труп/гроб как в 226+: 1 HP у трупа, гроб с полным HP, оба неубиваемы, leash к гробу.
Алтарь: NPC-гробы приоритетны, 9 изумрудов, сам-вышедший не ресается.
Watchdog 229 (±49999 вместо ±3.0E7) на месте.
