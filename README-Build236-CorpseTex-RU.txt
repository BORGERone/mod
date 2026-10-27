SettledLands 0.8.0 — build 236 «corpse-tex»
===========================================
Что исправлено: труп больше не «прозрачный». Имя модели трупа (DATA_TEXTURE)
теперь записывается ДО того, как что-либо может упасть:
  * в начале GuideNpc.applyProfile()  (из profile.texture, null -> "");
  * в самом конце PlayerCorpsePossession.dressBodyCorpse() — из PendingBody.model().
Раньше запись была последней строкой метода, вызываемого внутри catch(Throwable):
любое исключение оставляло поле пустым, миксин рендера отменял отрисовку, и труп
не рисовался вообще.

Диагностика (пишется в latest.log, сервер и клиент):
  [SettledLands][corpse] model=<имя модели> tex=<то, что реально в DATA_TEXTURE>
  [SettledLands][probe] client.corpse.tex | .model | .anim | .coffin
  [SettledLands][probe] client.modelsDir | client.models

Что сделать: поставить jar, добить моба / умереть в теле NPC, дождаться трупа,
выйти из игры, залить latest.log в репозиторий (origin/main).

Проверки перед выдачей (все пройдены):
  validate_jar.py   232 — ПРОЙДЕНЫ (калибровка), 236 — ПРОЙДЕНЫ
                    регресс: 230/233/234 обязаны падать — падают
  jvm_verify.py     GuideNpc, PlayerCorpsePossession — верификация настоящей JVM OK
  selftest_probe.py JVM-САМОТЕСТ OK
  check_mixin_class OK
