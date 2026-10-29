# Аудит совместимости API — Build 252 (первое лицо HnS + PAL + EMF / FA / Core / TnM)

## 1. Симптом пользователя после проверки build 251 в игре

По итогам игровой проверки **build 251** пользователь подтвердил, что вид со стороны (3-е лицо), ползание и сохранение модели вселённого NPC при перезаходе исправлены, но осталась проблема при виде **от первого лица** с установленными **Hackers and Slashers (`2.0-beta2.5`) + Touch’n Motion (`1.0.4`)**:
- взмахи меча от первого лица идут вниз под игрока вместо направления прицела спереди;
- из-за этого реальный хитбокс меча (`BladeTracker` в HnS привязан к матрицам рендера оружия в руках на кадре 1-го лица) не дотягивается до моба перед игроком и атака не наносит урон.

## 2. Точная причина в конвейере рендера 1-го лица

1. При активном слое HnS с `FirstPersonMode.THIRD_PERSON_MODEL` (`attack_layer`, `action_layer`, `defense_layer`, `pose_action_layer`, `first_person_pose_layer`) `PlayerAnimationLibrary` (`1.1.6+mc.1.21.1`) выполняет проход 1-го лица (`FirstPersonMode.isFirstPersonPass() == true`) через обычный `PlayerRenderer.render(...)`.
2. В `PlayerBodyRenderer.onRenderPlayerPre` в `build 251` для `mc.options.getCameraType().isFirstPerson()` срабатывает ранний `return;` без отмены события — поэтому рендер 1-го лица идёт через ванильный `PlayerRenderer` с JEM-корнем `EMFModelPartRoot` (`Fresh Animations: Player Extension`), как у обычного, так и у вселённого в Иви игрока.
3. `PlayerModelMixin.setupPlayerAnimation` (инъекция PAL на `RETURN` `PlayerModel.setupAnim(Entity, ...)`) записывает углы/позиции удара HnS в 12 частей `PlayerModel` и скрывает всё кроме рук (`rightArm`, `leftArm`, `rightSleeve`, `leftSleeve`).
4. Сразу после возврата из `setupAnim` и до `PlayerItemInHandLayer.renderArmWithItem` EMF вызывает `EMFModelPartRoot.animate()`:
   - выражения JPM ресурспака (`FA+Player`) перезаписывают `right_arm`/`left_arm` в покоящуюся позу (`xRot ≈ 0` вместо боевого замаха вперед);
   - `EMFCompatAnimationHook` (`emf_compat_core 2.3.2`) и `TouchNMotionHook` (`touch_n_motion 1.0.4`) дополнительно модифицируют модель.
5. Затем `PlayerItemInHandLayer` строит мировую матрицу меча от уже перезаписанной руки `rightArm.translateAndRotate(poseStack)` и передаёт `base`/`tip` в `net.dndats.hackersandslashers.client.combat.BladeTracker.updateBladePositions(...)`. На стенде с реальным байткодом PAL 1.1.6 + HnS beta2.5 + EMF 3.3.12 + Core 2.3.2 + TnM 1.0.4 это численно воспроизведено на `build 251`: по зомби прямо перед игроком (`Z = [1.45, 2.25]`) чистый PAL даёт `pureMinDist = 0.0` (попадание), а `build 251` — `brokenMinDist = 0.8523814`, `maxBrokenTipZ = 0.905772` (меч уходит вниз под ноги и не достаёт до цели).

## 3. Архитектура решения в Build 252

База: точный релиз `settledlands-0.8.0-build251-crawl-hns.jar` (`a3be663ea42de11e815b67b710e589bb92dd2752eb6fbd0d5408fdba3c9d256a`). Все классы 251 (включая `BbModel`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `GuideRenderer`) оставлены **байт-в-байт без изменений**.

Единственная точечная правка существующего класса:
- `dev/settledlands/npc/client/PlayerBodyRenderer.onRenderPlayerPre(RenderPlayerEvent$Pre)V` — на смещении `0` (до ранних выходов `isSpectator()`, `isInvisible()`, `modelKey.isEmpty()` и `isFirstPerson()`) добавлен вызов `FirstPersonSwordBridge252.onRenderPlayerPre(event)`. Событие не отменяется; остальной метод `onRenderPlayerPre` не изменён.

Новые изолированные классы (`src/build252/dev/settledlands/model/`):
1. `FirstPersonSwordBridge252` — без прямых байткодных ссылок на `ModList`, `EMF` или `PAL` (безопасная верификация при отсутствии необязательных модов). Через публичные интерфейсы `net.neoforged.fml.ModContainer` и `net.neoforged.neoforgespi.language.IModInfo` проверяет точные префиксы версий:
   - `hackersandslashers` (`2.0-beta2.5*`),
   - `player_animation_library` (`1.1.6*`),
   - `entity_model_features` (`3.3.*`),
   - отсутствие `emf_compat_hackers_and_slashers` (чтобы не дублировать сторонний аддон, если пользователь установит его отдельно).
2. `FirstPersonPalEmfAccess252` — удерживает пару хуков `PRE_HOOK` (строго индекс `0` в `EMFState.animationHooks`, до `EMFCompatAnimationHook`) и `POST_HOOK` (строго последний индекс `size - 1`, после `TouchNMotionHook` и любых поздних хуков).
   - Срабатывает **только** при одновременном выполнении всех условий: `FirstPersonMode.isFirstPersonPass() == true`, `mc.options.getCameraType().isFirstPerson()`, `cameraEntity == player`, `!state.isFirstPersonHand()`, `root.isMainModel`, `emf.emf$getEMFRootModel() == root` (приватный внеэкранный `EMFModelPartRoot` из `FreshEmfAccess` исключён), `animManager.isActive()`, `animManager.getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL`, и активен хотя бы один из 5 слоёв HnS `THIRD_PERSON_MODEL`.
   - `PRE_HOOK` снимает позу (`x, y, z, xRot, yRot, zRot, xScale, yScale, zScale, visible, skipDraw, isSetByAnimation`) корня `root` и 12 ванильных частей `PlayerModel` до вмешательства `EMFCompatAnimationHook` (включая `emfcompat$repairCrouchPose`), JPM ресурспака и `TouchNMotionHook`.
   - `POST_HOOK` восстанавливает сохранённую позу PAL на `root` и 12 ванильных частях, а для всех кастомных дочерних JEM-частей (`EMFModelPartCustom`, например `EMF_right_arm` / сгибы предплечья FA, которые PAL сам не сбрасывает после кадров 3-го лица `F5`) вызывает `custom.resetPose()` и сбрасывает `isSetByAnimation = false`. Стек снимков в `ThreadLocal` очищается даже при исключении внутри `EMFAnimationHandler.animate()`.

## 4. Что проверено на JVM 21 и пределы стенда

- `tests/jvm_fp_sword252.py` под `-Xverify:all` исполняет реальный байткод `PAL 1.1.6`, `HnS 2.0-beta2.5` (`BladeTracker`, `UniversalAnimLoader`, все 93 JSON и все 39 `hnsattack_*.json` × 5 кадров = 195 кадров), `EMF 3.3.12`, `EMF Compat Core 2.3.2` (`EMFCompatAnimationHook` + `EMFCompatConfig`) и `Touch’n Motion 1.0.4` (`TouchNMotionHook`):
  - подтверждает промах `build 251` по фронтальному AABB (`brokenMinDist = 0.8523814`) и полное совпадение `build 252` с чистым PAL (`< 1e-5f` по всем частям и векторам `BladeTracker` обеих рук, `frontHits = 14/14`);
  - проверяет все 5 слоёв `THIRD_PERSON_MODEL`, удар в приседе (`sneak-attack`), холодный старт невселённого и невидимого игрока без предварительного нажатия `F5`, `slim` + парное оружие, перестановку позднего стороннего хука, переход после 3-го лица (`F5`) и восстановление после исключения, а также негативные проверки (3-е лицо, freecam, обычная рука FA без активного удара, бумажная кукла инвентаря без `FirstPersonPass`, `parkour_layer`, приватный `EMFModelPartRoot`).
- `tests/mutants_fp252.py`: 10/10 валидных для верификатора мутантов `FirstPersonSwordBridge252` / `FirstPersonPalEmfAccess252` убиты.
- Ограничения: полноценный клиент Minecraft/NeoForge, реальные миксины в рантайме игры, фактический архив `FA+Player-v1.1.zip` пользователя и `EMF 3.3.9` (в стенде использован публичный `EMF 3.3.12`) визуально не запускались. Требуется финальная проверка ударов от первого лица непосредственно в игре. Touch’n Motion для самих сущностей NPC (`GuideNpc`) по-прежнему не реализован.
