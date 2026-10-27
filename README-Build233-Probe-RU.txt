SETTLED LANDS — BUILD 233 «probe» (диагностический)
===================================================
Дата: 27.10.2026
Файл: settledlands-0.8.0-build233-probe.jar
Версия в меню модов: 0.8.0-build233-probe
База: build 232 «earlytex2»

ЧТО ДОКАЗАЛ ЛОГ 232
-------------------
Миксин применился и на СЕРВЕРЕ имя модели теперь выставляется верно:
    [SettledLands] corpse texture pre-set: ivy_oleander.bbmodel
    [SettledLands] corpse texture pre-set: provodnik.bbmodel
    player death at (41, 64, -255) possessed=true — corpse watch scheduled
    3 body stacks written into own corpse
Значит на серверной стороне всё правильно, и дефект живёт на КЛИЕНТЕ.

ВТОРОЙ УЧАСТНИК (найден в байткоде 229)
---------------------------------------
GuideCorpseFreezeMixin вставлен в HEAD GuideRenderer.renderCorpse и делает:
    if (corpse.isCoffin()) return;
    String anim = corpse.getCustomAnimName();
    if (anim == null || !anim.contains("die")) { ci.cancel(); return; }   // ничего не рисуем
    BbModel m = BbModelManager.getModel(corpse.texturePath());
    if (m == null) { ci.cancel(); return; }                               // ничего не рисуем
    ci.cancel(); m.render(corpse, ...);
То есть ванильный рендер (и его коричневый fallback) вообще отключён для тел,
и всё зависит от getModel(texturePath()) на клиенте.

ЧТО ДЕЛАЕТ 233
--------------
НИЧЕГО не меняет в игре. Добавлены два класса:

1. dev/settledlands/diag/TexProbe — печатает строку только когда значение
   по тегу ИЗМЕНИЛОСЬ (никакого спама на каждый кадр).
2. dev/settledlands/mixin/CorpseRenderProbeMixin — @Inject в HEAD renderCorpse,
   только чтение. В лог попадут строки:

   [SettledLands][probe] client.corpse.coffin = false
   [SettledLands][probe] client.corpse.anim = die
   [SettledLands][probe] client.corpse.tex = ...
   [SettledLands][probe] client.corpse.model = BbModel   (или NULL)
   [SettledLands][probe] client.modelsDir = ...
   [SettledLands][probe] client.models = [имена файлов, которые видит клиент]

ЧТО СДЕЛАТЬ
-----------
1. Положить в mods только этот jar.
2. Зайти в мир, умереть вселённым (как в прошлый раз) и посмотреть на тело.
3. Прислать latest.log.

По этим шести строкам сразу видно:
* client.corpse.tex пустой, а client.corpse.model = NULL  -> имя не доехало до клиента
  (лечится на серверной стороне синхронизации);
* tex непустой, а model = NULL                            -> файл модели не найден
  (смотрим client.models: чего не хватает в settledlands/models);
* model = BbModel, а тело всё равно невидимое              -> дело в BbModel.render
  (масштаб/пивот/кости), это уже другая правка.

sha256: 0592d61c4ffe5888f93ca17fdf139e5982a63d060ce606c13c1d7b742a661260
размер: 1 540 690 B
Отозваны: 230 (правка байткода), 231 (кодировка @Mixin). 232 рабочий, но баг не закрывает.
