# API-аудит экспериментального моста 248

## Точная область
Игрок `AbstractClientPlayer`, внешний вид от третьего лица через `BbModel.renderActor`, модель с именем `ivy` или `ivy_oleander` и ожидаемыми уникальными костями `root/body/waist/head/rightarm/leftarm/rightleg/leftleg`. NPC, первое лицо, переименованная Иви и другие риги не заявлены. Пользователь просил также NPC — эта часть не выполнена.

## Touch'n Motion
Проверялся внешний NeoForge JAR 1.0.4 SHA-256 `3754909bf0d12445b18a60de315619734c1229bcc621f88f315ccf076701ab56`, SHA-1 `b6fba9ca4d73d2e1eb4968fb48cb6eb0fcadca36`, загруженный из `Eldodoron/PEAK-dev` commit `5d6be28b42e0c9fe95cde184ddeb7e6fde9615f6`; pack-index содержит тот же SHA-1. Это не доказательство идентичности JAR пользователя. TnM — GPL-3.0; его JAR в архивы SettledLands не включён.

Байт-кодом проверены `ModelSpace.before(LivingEntity,PoseStack,float,Consumer)`, `FootGrounding.recordAnimated(UUID,Function)`, `DrawnEntities.inWorld(Entity)`, `ParCoolActivity.active(AbstractClientPlayer)`, `WholeBody.held(AbstractClientPlayer)` и приватный статический `TouchNMotionHook.applyAll(UUID,Function,boolean,boolean)`. Последний — не публичный API: версия проверяется точно, используется `trySetAccessible`, при отказе — обычный рендер и диагностика. Автоматический Java module открыт в изолированном тесте, но конкретный загрузчик игры не проверен.

## Порядок и математика
Wrapper создаёт вложенный scope и вызывает исходный рендер. `ModelSpace.before` идёт перед родным `setupAnimRaw`, `recordAnimated` и `applyAll` — после него и перед телом/предметами. Шесть именованных виртуальных `ModelPart` отражают полную исходную цепочку родителей. После TnM преобразование рассчитывается обратно parent-first; исходные кости не изменяются до проверки всех матриц. Незавершаемая цепь, NaN, отражение/непредставимый shear или исключение вызывают откат добавленного PoseStack-кадра. Это не гарантирует отката внутренних состояний самого TnM после исключения. Сам исходный рендер при своей ошибке продолжает выбрасывать исключение.

Операции копируют Minecraft 1.21.1 `ModelPart.translateAndRotate`: `translate`, затем `PoseStack.mulPose(new Quaternionf().rotationZYX(z,y,x))`, затем scale. JOML 1.10.5 — официальная библиотека Minecraft 1.21.1; его MIT-исходник и лицензия сохранены только для тестов, классы не включены в mod JAR. 1200 детерминированных поз с близкими к ±90° углами проверяют матрицы, но не визуальную корректность.

На Java 21 исполнялись настоящие TnM `ModelSpace`/`applyAll`/`applyAllParts`; отдельными сценариями — реальные `FootGrounding`/`FootStep` и `TorsoLean`/`PelvisFollow` с искусственными состояниями и заглушенными миром, часами и другими подсистемами. EMF/ETF-текущий контекст проверялся с test doubles. Ничто из этого не является запуском Minecraft/NeoForge или подтверждением работы в сборке пользователя.

## Источники дескрипторов
- TnM upstream: <https://github.com/victorkozhokin/emf-compat> (открытые исходники могут отличаться от проверенного JAR 1.0.4).
- EMF 3.3.9 state: <https://github.com/Traben-0/Entity_Model_Features/blob/v3.3.9/src/main/java/traben/entity_model_features/models/animation/state/EMFState.java>.
- ETF 7.2.4 render scope: <https://github.com/Traben-0/Entity_Texture_Features/blob/v7.2.4/src/main/java/traben/entity_texture_features/mixin/mixins/entity/misc/MixinEntityRenderDispatcher.java>.
- Minecraft 1.21 `ModelPart`: <https://github.com/Yeet-Masta/MCP-1.21/blob/main/src/main/java/net/minecraft/client/model/geom/ModelPart.java>.
- Minecraft 1.21.1 / JOML 1.10.5 metadata: <https://github.com/PrismLauncher/meta-launcher/blob/master/net.minecraft/1.21.1.json>.
