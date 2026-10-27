SETTLED LANDS — BUILD 234 «probe» (исправленная диагностика)
============================================================
Дата: 27.10.2026
Файл: settledlands-0.8.0-build234-probe.jar
Версия: 0.8.0-build234-probe
База: build 232 «earlytex2»

ПОЧЕМУ УПАЛ 233 (найдено в логе юзера)
--------------------------------------
    An error occurred scanning file Mod file: ...build233-probe.jar
    java.lang.ArrayIndexOutOfBoundsException: Index 79 out of bounds for length 79
      at org.objectweb.asm.ClassReader.readUtf
      at net.neoforged.fml.loading.modfiles.ModFile.scanFile
Причина: в моём классе TexProbe имя файла-исходника добавлялось в пул констант
ПОСЛЕ сериализации пула, из-за чего атрибут SourceFile ссылался на индекс 79 при
пуле длиной 79. NeoForge читает ASM-ом КАЖДЫЙ класс в jar ещё на сканировании,
поэтому такой дефект = падение до загрузки модов.

ЧТО СДЕЛАНО, ЧТОБЫ ЭТО НЕ ПОВТОРЯЛОСЬ
--------------------------------------
* генератор исправлен: пул констант собирается полностью, потом сериализуется;
* написан строгий валидатор tools/validate_jar.py — проверяет ВЕСЬ jar:
  пул констант, каждый операнд байткода, все атрибуты (Code, StackMapTable,
  LineNumberTable, аннотации, SourceFile, Signature, Nest*, InnerClasses,
  BootstrapMethods), версии, наличие миксинов, форму @Mixin;
* валидатор откалиброван: на рабочем 232 проходит (307 классов, ноль ложных
  срабатываний), на сломанном 233 падает ровно на SourceFile #79;
* правила записаны в AI-GUIDE-SettledLands.md (шлюз проверок перед выдачей).

ЧТО ДЕЛАЕТ 234
--------------
Ничего не меняет в игре. Два новых класса, только чтение:
1. dev/settledlands/diag/TexProbe — печатает строку только при СМЕНЕ значения
   (никакого спама на каждый кадр).
2. dev/settledlands/mixin/CorpseRenderProbeMixin — @Inject HEAD renderCorpse.

Ожидаемые строки в latest.log:
    [SettledLands][probe] client.corpse.coffin = false
    [SettledLands][probe] client.corpse.anim = die
    [SettledLands][probe] client.corpse.tex = ...
    [SettledLands][probe] client.corpse.model = BbModel | NULL
    [SettledLands][probe] client.modelsDir = ...
    [SettledLands][probe] client.models = [что реально видит клиент]

Как читать:
* tex пустой + model=NULL  -> имя модели не доезжает до клиента (чиним синхронизацию);
* tex непустой + model=NULL -> файл модели не найден (смотрим client.models);
* model=BbModel, а тело невидимое -> дело в самом BbModel.render (пивоты/кости/масштаб).

УСТАНОВКА
---------
В mods оставить только этот jar. Проверка:
    certutil -hashfile settledlands-0.8.0-build234-probe.jar SHA256
    ожидается f83b8c69178176ecfbd3a3136e2328f25cbf80af791a818f0ab200a7fd6039b7
    размер 1 540 699 байт
Качать «Сохранить ссылку как…».

ОТОЗВАНЫ: 230 (правка байткода), 231 (кодировка @Mixin), 233 (пул констант).
Актуальная рабочая база без зонда — 232.
