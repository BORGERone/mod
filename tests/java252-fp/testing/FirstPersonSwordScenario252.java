package testing;

import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.ModifierLayer;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import team.unnamed.mocha.MochaEngine;

import dev.settledlands.npc.PossessionClientSync;
import dev.settledlands.npc.client.PlayerBodyRenderer;
import net.dndats.hackersandslashers.impl.combat.mechanics.combo.BladeTracker;
import net.dndats.hackersandslashers.impl.combat.mechanics.combo.TrailSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

public class FirstPersonSwordScenario252 {
 // Exact netherite_sword values from /tmp/user-motion250/latest.log: modelScale=0.8500, baseLength=1.2019
 private static final float SWORD_REACH = 1.2019f * 0.8500f;
 private static final String[] ALL_PARTS = {
  "head", "hat", "body", "jacket",
  "right_arm", "right_sleeve", "left_arm", "left_sleeve",
  "right_leg", "right_pants", "left_leg", "left_pants"
 };
 private static final String[] HNS_LAYERS = {
  "pose_layer", "first_person_pose_layer", "pose_action_layer",
  "defense_layer", "action_layer", "attack_layer", "parkour_layer"
 };
 private static final int[] HNS_PRIORITIES = { 0, 1, 10, 50, 80, 100, 120 };

 private static final Field TRAIL_FRAME_FIELD;
 private static final Field IS_HITBOX_ACTIVE;
 private static final Field CUR_BASE_MAIN;
 private static final Field CUR_TIP_MAIN;
 private static final Field CUR_BASE_OFF;
 private static final Field CUR_TIP_OFF;
 private static final Method DIST_POINT_TO_AABB;

 static {
  try {
   TRAIL_FRAME_FIELD = TrailSystem.class.getDeclaredField("currentRenderFrame");
   TRAIL_FRAME_FIELD.setAccessible(true);
   IS_HITBOX_ACTIVE = BladeTracker.class.getDeclaredField("isHitboxActive");
   IS_HITBOX_ACTIVE.setAccessible(true);
   CUR_BASE_MAIN = BladeTracker.class.getDeclaredField("currentBaseMain");
   CUR_BASE_MAIN.setAccessible(true);
   CUR_TIP_MAIN = BladeTracker.class.getDeclaredField("currentTipMain");
   CUR_TIP_MAIN.setAccessible(true);
   CUR_BASE_OFF = BladeTracker.class.getDeclaredField("currentBaseOff");
   CUR_BASE_OFF.setAccessible(true);
   CUR_TIP_OFF = BladeTracker.class.getDeclaredField("currentTipOff");
   CUR_TIP_OFF.setAccessible(true);
   DIST_POINT_TO_AABB = BladeTracker.class.getDeclaredMethod("distancePointToAABB", Vector3f.class, AABB.class);
   DIST_POINT_TO_AABB.setAccessible(true);
  } catch (Exception e) {
   throw new ExceptionInInitializerError(e);
  }
 }

 private static void nextRenderFrame() throws Exception {
  TRAIL_FRAME_FIELD.setInt(null, TRAIL_FRAME_FIELD.getInt(null) + 1);
 }

 private static float distancePointToAabb(Vector3f pt, AABB box) throws Exception {
  return ((Double) DIST_POINT_TO_AABB.invoke(null, pt, box)).floatValue();
 }

 private static Object newPalInstance(Class<?> cls, Class<?>[] paramTypes, Object[] params) throws Exception {
  if (!java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
   Constructor<?> c = cls.getDeclaredConstructor(paramTypes);
   c.setAccessible(true);
   return c.newInstance(params);
  }
  javassist.util.proxy.ProxyFactory pf = new javassist.util.proxy.ProxyFactory();
  pf.setSuperclass(cls);
  return pf.create(paramTypes, params);
 }

 private static Object newPalPlayerModelMixin(EMFModelPartRoot root) throws Exception {
  Class<?> cls = Class.forName("com.zigythebird.playeranim.mixin.PlayerModelMixin");
  Object m = newPalInstance(cls, new Class<?>[]{ ModelPart.class, java.util.function.Function.class }, new Object[]{ root, null });
  setField(cls, m, "jacket", root.getChild("jacket"));
  setField(cls, m, "rightSleeve", root.getChild("right_sleeve"));
  setField(cls, m, "leftSleeve", root.getChild("left_sleeve"));
  setField(cls, m, "rightPants", root.getChild("right_pants"));
  setField(cls, m, "leftPants", root.getChild("left_pants"));
  return m;
 }

 private static void setField(Class<?> cls, Object target, String name, Object val) throws Exception {
  Field f = cls.getDeclaredField(name);
  f.setAccessible(true);
  f.set(target, val);
 }

 private static Map<String, Animation> loadAllHnsAnimations(Path hnsJar) throws Exception {
  Map<String, Animation> out = new TreeMap<>();
  try (ZipFile zf = new ZipFile(hnsJar.toFile())) {
   Enumeration<? extends ZipEntry> entries = zf.entries();
   while (entries.hasMoreElements()) {
    ZipEntry ze = entries.nextElement();
    String n = ze.getName();
    if (n.startsWith("assets/hackersandslashers/player_animations/") && n.endsWith(".json")) {
     String shortName = n.substring(n.lastIndexOf('/') + 1);
     try (java.io.InputStream in = zf.getInputStream(ze)) {
      Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(in);
      if (loaded == null || loaded.isEmpty()) throw new AssertionError("No animations in " + shortName);
      Animation a = loaded.values().iterator().next();
      if (a == null) throw new AssertionError("Null animation for " + shortName);
      out.put(shortName, a);
     }
    }
   }
  }
  return out;
 }

 private static final class FrameCapture {
  final Map<String, float[]> partPose = new LinkedHashMap<>();
  final Map<String, Boolean> partVisible = new LinkedHashMap<>();
  final Map<String, Boolean> partSetByAnim = new LinkedHashMap<>();
  final Vector3f mainBase = new Vector3f();
  final Vector3f mainTip = new Vector3f();
  final Vector3f offBase = new Vector3f();
  final Vector3f offTip = new Vector3f();
 }

 private static final class Rig {
  final LocalPlayer player;
  final EMFModelPartRoot root;
  final PlayerRenderer renderer;
  final Object palModelMixin;
  final Object palRendererMixin;
  final Object palItemLayerMixin;
  final Method setDefaultBeforeRender;
  final Method setupPlayerAnimation;
  final Method translateToHand;
  final Method modifyRenderState;
  final Method applyBodyTransforms;
  final Method changeItemLocation;
  final Method changeItemRotationAndScale;
  final Map<String, ModifierLayer<PlayerAnimationController>> layers = new LinkedHashMap<>();

  Rig(boolean slim) throws Exception {
   player = new LocalPlayer();
   player.slim = slim;
   player.main = new ItemStack();
   player.off = new ItemStack();
   player.px = 0.0; player.py = 0.0; player.pz = 0.0;
   player.eyeHeight = 1.62f;
   player.initPal();

   for (int i = 0; i < HNS_LAYERS.length; i++) {
    String ln = HNS_LAYERS[i];
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("hackersandslashers", ln);
    FirstPersonMode fpm = (ln.equals("pose_layer") || ln.equals("parkour_layer"))
     ? FirstPersonMode.NONE
     : FirstPersonMode.THIRD_PERSON_MODEL;
    FirstPersonConfiguration cfg = new FirstPersonConfiguration()
     .setShowRightArm(true).setShowLeftArm(true)
     .setShowRightItem(true).setShowLeftItem(true);
    PlayerAnimationController ctrl = new PlayerAnimationController(
     player,
     (c, state, setter) -> PlayState.STOP,
     c -> MochaEngine.createStandard(c)
    );
    ctrl.setFirstPersonMode(fpm);
    ctrl.setFirstPersonConfiguration(cfg);
    ModifierLayer<PlayerAnimationController> ml = new ModifierLayer<>(ctrl);
    player.modAnimations.put(id, ml);
    player.animManager.addAnimLayer(HNS_PRIORITIES[i], ml);
    layers.put(ln, ml);
   }

   root = (EMFModelPartRoot) RealEmfFactory.make(slim);
   root.isMainModel = true;
   PlayerModel<net.minecraft.client.player.AbstractClientPlayer> pm = new PlayerModel<>(root, slim);
   renderer = new PlayerRenderer(pm);

   palModelMixin = newPalPlayerModelMixin(root);
   Class<?> pmmCls = palModelMixin.getClass();
   setDefaultBeforeRender = pmmCls.getDeclaredMethod("setDefaultBeforeRender",
    Entity.class, float.class, float.class, float.class, float.class, float.class, CallbackInfo.class);
   setDefaultBeforeRender.setAccessible(true);
   setupPlayerAnimation = pmmCls.getDeclaredMethod("setupPlayerAnimation",
    Entity.class, float.class, float.class, float.class, float.class, float.class, CallbackInfo.class);
   setupPlayerAnimation.setAccessible(true);
   translateToHand = pmmCls.getDeclaredMethod("translateToHand", ModelPart.class, PoseStack.class);
   translateToHand.setAccessible(true);

   Class<?> prmCls = Class.forName("com.zigythebird.playeranim.mixin.PlayerRendererMixin");
   palRendererMixin = newPalInstance(prmCls, new Class<?>[0], new Object[0]);
   modifyRenderState = prmCls.getDeclaredMethod("modifyRenderState",
    net.minecraft.client.player.AbstractClientPlayer.class, float.class, float.class,
    PoseStack.class, MultiBufferSource.class, int.class, CallbackInfo.class);
   modifyRenderState.setAccessible(true);
   applyBodyTransforms = prmCls.getDeclaredMethod("applyBodyTransforms",
    net.minecraft.client.player.AbstractClientPlayer.class, PoseStack.class,
    float.class, float.class, float.class, float.class, CallbackInfo.class);
   applyBodyTransforms.setAccessible(true);

   Class<?> ihlCls = Class.forName("com.zigythebird.playeranim.mixin.ItemInHandLayerMixin");
   palItemLayerMixin = newPalInstance(ihlCls, new Class<?>[0], new Object[0]);
   changeItemLocation = ihlCls.getDeclaredMethod("changeItemLocation",
    LivingEntity.class, ItemStack.class, ItemDisplayContext.class, HumanoidArm.class,
    PoseStack.class, MultiBufferSource.class, int.class, CallbackInfo.class);
   changeItemLocation.setAccessible(true);
   changeItemRotationAndScale = ihlCls.getDeclaredMethod("changeItemRotationAndScale",
    LivingEntity.class, ItemStack.class, ItemDisplayContext.class, HumanoidArm.class,
    PoseStack.class, MultiBufferSource.class, int.class, CallbackInfo.class);
   changeItemRotationAndScale.setAccessible(true);
  }

  void playOnLayer(String layerName, Animation anim, int tick, float partialTick) {
   for (ModifierLayer<PlayerAnimationController> ml : layers.values()) {
    ml.getAnimation().stop();
   }
   PlayerAnimationController ctrl = layers.get(layerName).getAnimation();
   ctrl.triggerAnimation(anim);
   player.tickCount = 0;
   player.animManager.setTickDelta(0.0f);
   player.animProcessor.handleAnimations(0.0f, true);
   for (int t = 1; t <= tick; t++) {
    player.tickCount = t;
    player.animProcessor.handleAnimations(0.0f, true);
   }
   player.animManager.setTickDelta(partialTick);
   player.animProcessor.handleAnimations(partialTick, false);
  }

  FrameCapture renderFirstPersonPass(float partialTick, boolean runEmf) throws Exception {
   Minecraft mc = Minecraft.getInstance();
   (player.slim ? mc.dispatcher.slimRenderer : mc.dispatcher.renderer).model = renderer.getModel();
   PoseStack poseStack = new PoseStack();
   CallbackInfo ci = new CallbackInfo();

   boolean fpPass = mc.options.getCameraType().isFirstPerson()
    && mc.getCameraEntity() == player
    && player.animManager.getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL;
   FirstPersonMode.setFirstPersonPass(fpPass);
   try {
    RenderPlayerEvent.Pre pre = new RenderPlayerEvent.Pre(player, poseStack, null, partialTick);
    PlayerBodyRenderer.onRenderPlayerPre(pre);
    if (fpPass && pre.isCanceled()) {
     throw new AssertionError("PlayerBodyRenderer canceled first-person render pass!");
    }

    modifyRenderState.invoke(palRendererMixin, player, 0f, partialTick, poseStack, null, 0xF000F0, ci);

    Vector3f camPos = new Vector3f((float) player.px, (float) player.py + player.eyeHeight, (float) player.pz);
    poseStack.pushPose();
    poseStack.translate((float) player.px - camPos.x, (float) player.py - camPos.y, (float) player.pz - camPos.z);
    float bodyYaw = player.yBodyRot;
    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
    applyBodyTransforms.invoke(palRendererMixin, player, poseStack, (float) player.tickCount + partialTick, bodyYaw, partialTick, 1.0f, ci);
    poseStack.scale(-1.0f, -1.0f, 1.0f);
    poseStack.scale(0.9375f, 0.9375f, 0.9375f);
    poseStack.translate(0.0f, -1.501f, 0.0f);

    for (String pName : ALL_PARTS) {
     ModelPart mp = root.getChild(pName);
     mp.visible = true;
     mp.skipDraw = false;
     if (mp instanceof EMFModelPart em) em.isSetByAnimation = false;
    }
    setDefaultBeforeRender.invoke(palModelMixin, player, 0f, 0f, (float) player.tickCount + partialTick, 0f, player.pitch, ci);
    if (player.crouching) {
     root.getChild("body").xRot = 0.5f;
     root.getChild("right_arm").xRot += 0.4f;
     root.getChild("left_arm").xRot += 0.4f;
     root.getChild("head").y = 4.2f;
     root.getChild("body").y = 3.2f;
    }
    setupPlayerAnimation.invoke(palModelMixin, player, 0f, 0f, (float) player.tickCount + partialTick, 0f, player.pitch, ci);

    if (runEmf) {
     EMFState.FIRST_PERSON = false;
     EMFState.ENTITY = player;
     EMFState.isMainPhase = true;
     EMFState.isLayerPhase = false;
     traben.entity_model_features.EMFManager.getInstance().entityRenderCount++;
     root.animate();
    }

    FrameCapture cap = new FrameCapture();
    recordTree(root, "", cap);

    IS_HITBOX_ACTIVE.setBoolean(null, true);
    nextRenderFrame();
    captureHandBlade(poseStack, camPos, false, ci, cap.mainBase, cap.mainTip);
    nextRenderFrame();
    captureHandBlade(poseStack, camPos, true, ci, cap.offBase, cap.offTip);

    poseStack.popPose();
    return cap;
   } finally {
    FirstPersonMode.setFirstPersonPass(false);
   }
  }

  private void captureHandBlade(
   PoseStack poseStack,
   Vector3f camPos,
   boolean isLeft,
   CallbackInfo ci,
   Vector3f outBase,
   Vector3f outTip
  ) throws Exception {
   poseStack.pushPose();
   ModelPart arm = root.getChild(isLeft ? "left_arm" : "right_arm");
   boolean doVanilla = (Boolean) translateToHand.invoke(palModelMixin, arm, poseStack);
   if (doVanilla) arm.translateAndRotate(poseStack);
   HumanoidArm hArm = isLeft ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
   ItemDisplayContext ctx = isLeft ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
   ItemStack stack = isLeft ? player.off : player.main;
   changeItemLocation.invoke(palItemLayerMixin, player, stack, ctx, hArm, poseStack, null, 0xF000F0, ci);
   poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
   poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
   poseStack.translate((float) (isLeft ? -1 : 1) / 16.0f, 0.125f, -0.625f);
   changeItemRotationAndScale.invoke(palItemLayerMixin, player, stack, ctx, hArm, poseStack, null, 0xF000F0, ci);

   Matrix4f m = poseStack.last().pose();
   Vector4f b4 = new Vector4f(0f, 0f, 0f, 1f).mul(m);
   Vector4f t4 = new Vector4f(0f, SWORD_REACH, 0f, 1f).mul(m);
   Vector3f base = new Vector3f(b4.x + camPos.x, b4.y + camPos.y, b4.z + camPos.z);
   Vector3f tip = new Vector3f(t4.x + camPos.x, t4.y + camPos.y, t4.z + camPos.z);
   BladeTracker.updateBladePositions(base, tip, isLeft);
   Vector3f trackedBase = (Vector3f) (isLeft ? CUR_BASE_OFF : CUR_BASE_MAIN).get(null);
   Vector3f trackedTip = (Vector3f) (isLeft ? CUR_TIP_OFF : CUR_TIP_MAIN).get(null);
   outBase.set(trackedBase);
   outTip.set(trackedTip);
   poseStack.popPose();
  }

  private static void recordTree(ModelPart part, String path, FrameCapture cap) {
   cap.partPose.put(path, new float[]{
    part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale
   });
   cap.partVisible.put(path, part.visible);
   if (part instanceof EMFModelPart em) {
    cap.partSetByAnim.put(path, em.isSetByAnimation);
   }
   for (Map.Entry<String, ModelPart> e : part.children.entrySet()) {
    recordTree(e.getValue(), path + "/" + e.getKey(), cap);
   }
  }
 }

 private static void assertCapturesMatch(String label, FrameCapture expected, FrameCapture actual) {
  if (!expected.partPose.keySet().equals(actual.partPose.keySet())) {
   throw new AssertionError(label + ": keyset mismatch");
  }
  for (String k : expected.partPose.keySet()) {
   float[] a = expected.partPose.get(k);
   float[] b = actual.partPose.get(k);
   for (int i = 0; i < a.length; i++) {
    if (Math.abs(a[i] - b[i]) > 1e-5f) {
     throw new AssertionError(label + " part " + k + " field[" + i + "] expected=" + a[i] + " actual=" + b[i]);
    }
   }
   if (!Objects.equals(expected.partVisible.get(k), actual.partVisible.get(k))) {
    throw new AssertionError(label + " part " + k + " visible mismatch: expected=" + expected.partVisible.get(k) + " actual=" + actual.partVisible.get(k));
   }
   if (!Objects.equals(expected.partSetByAnim.get(k), actual.partSetByAnim.get(k))) {
    throw new AssertionError(label + " part " + k + " isSetByAnimation mismatch: expected=" + expected.partSetByAnim.get(k) + " actual=" + actual.partSetByAnim.get(k));
   }
  }
  if (expected.mainBase.distance(actual.mainBase) > 1e-5f || expected.mainTip.distance(actual.mainTip) > 1e-5f) {
   throw new AssertionError(label + " main blade mismatch: expectedTip=" + expected.mainTip + " actualTip=" + actual.mainTip);
  }
  if (expected.offBase.distance(actual.offBase) > 1e-5f || expected.offTip.distance(actual.offTip) > 1e-5f) {
   throw new AssertionError(label + " off blade mismatch: expectedTip=" + expected.offTip + " actualTip=" + actual.offTip);
  }
 }

 private static float minSegmentDistanceToAabb(Vector3f base, Vector3f tip, AABB box) throws Exception {
  float best = Float.MAX_VALUE;
  Vector3f p = new Vector3f();
  for (int s = 0; s <= 16; s++) {
   float alpha = s / 16.0f;
   base.lerp(tip, alpha, p);
   float d = distancePointToAabb(p, box);
   if (d < best) best = d;
  }
  return best;
 }

 public static void main(String[] args) throws Exception {
  Path hnsJar = Path.of(args[0]);
  boolean expectBroken = args.length > 1 && "--expect-broken".equals(args[1]);

  Probe252.reset();
  Probe.events.clear();
  EMFState.resetDefaultHooks();

  Map<String, Animation> allAnims = loadAllHnsAnimations(hnsJar);
  List<String> attackNames = allAnims.keySet().stream()
   .filter(n -> n.startsWith("hnsattack_"))
   .sorted()
   .toList();
  if (attackNames.size() != 39) {
   throw new AssertionError("Expected 39 hnsattack_*.json in HnS jar, got " + attackNames.size());
  }

  strm.emfcompat.core.EMFCompatConfig.init(null);
  Minecraft mc = Minecraft.getInstance();
  Rig pureRig = new Rig(false);
  Rig emfRig = new Rig(false);

  mc.player = emfRig.player;
  mc.cameraEntity = emfRig.player;
  mc.options.setCameraType(CameraType.FIRST_PERSON);
  PossessionClientSync.CLIENT_STATES.put(emfRig.player.getUUID(), new PossessionClientSync.State("settledlands:ivy", "Ivy"));

  // Zombie standing directly in front of player (facing +Z, yBodyRot=0)
  AABB frontZombie = new AABB(-0.35, 0.0, 1.45, 0.35, 1.95, 2.25);

  if (expectBroken) {
   Animation swordSwing = allAnims.get("hnsattack_sword_right.json");
   if (swordSwing == null) swordSwing = allAnims.get(attackNames.get(0));
   float minPureDist = Float.MAX_VALUE;
   float minBrokenDist = Float.MAX_VALUE;
   float maxBrokenTipZ = -Float.MAX_VALUE;
   for (int step = 1; step <= 5; step++) {
    int tick = Math.max(0, Math.round((swordSwing.length() * step) / 6.0f));
    mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
    pureRig.playOnLayer("attack_layer", swordSwing, tick, 0.25f);
    FrameCapture pureCap = pureRig.renderFirstPersonPass(0.25f, false);
    minPureDist = Math.min(minPureDist, minSegmentDistanceToAabb(pureCap.mainBase, pureCap.mainTip, frontZombie));

    mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
    emfRig.playOnLayer("attack_layer", swordSwing, tick, 0.25f);
    FrameCapture brokenCap = emfRig.renderFirstPersonPass(0.25f, true);
    minBrokenDist = Math.min(minBrokenDist, minSegmentDistanceToAabb(brokenCap.mainBase, brokenCap.mainTip, frontZombie));
    maxBrokenTipZ = Math.max(maxBrokenTipZ, brokenCap.mainTip.z);
   }
   if (!(minPureDist == 0.0f && minBrokenDist > 0.35f)) {
    throw new AssertionError("Expected build 251 to miss front zombie while pure PAL hits: pureDist=" + minPureDist + " brokenDist=" + minBrokenDist + " maxBrokenTipZ=" + maxBrokenTipZ);
   }
   System.out.println("CONFIRMED build 251 bug: pureMinDist=" + minPureDist + " brokenMinDist=" + minBrokenDist + " maxBrokenTipZ=" + maxBrokenTipZ);
   return;
  }

  // === BUILD 252 VERIFICATION ===
  int sampledFrames = 0;
  int pureHits = 0;
  int emfHits = 0;
  for (String name : attackNames) {
   Animation anim = allAnims.get(name);
   boolean pureHitThis = false;
   boolean emfHitThis = false;
   for (int step = 1; step <= 5; step++) {
    int tick = Math.max(0, Math.round((anim.length() * step) / 6.0f));
    float pt = 0.15f * step;

    mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
    pureRig.playOnLayer("attack_layer", anim, tick, pt);
    FrameCapture pureCap = pureRig.renderFirstPersonPass(pt, false);

    mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
    emfRig.playOnLayer("attack_layer", anim, tick, pt);
    FrameCapture emfCap = emfRig.renderFirstPersonPass(pt, true);

    assertCapturesMatch("attack:" + name + "@" + tick, pureCap, emfCap);
    float pureD = Math.min(
     minSegmentDistanceToAabb(pureCap.mainBase, pureCap.mainTip, frontZombie),
     minSegmentDistanceToAabb(pureCap.offBase, pureCap.offTip, frontZombie)
    );
    float emfD = Math.min(
     minSegmentDistanceToAabb(emfCap.mainBase, emfCap.mainTip, frontZombie),
     minSegmentDistanceToAabb(emfCap.offBase, emfCap.offTip, frontZombie)
    );
    if (Math.abs(pureD - emfD) > 1e-5f) {
     throw new AssertionError("Hitbox distance mismatch on " + name + "@" + tick + ": pure=" + pureD + " emf=" + emfD);
    }
    if (pureD == 0.0f) pureHitThis = true;
    if (emfD == 0.0f) emfHitThis = true;
    sampledFrames++;
   }
   if (pureHitThis) pureHits++;
   if (emfHitThis) emfHits++;
  }
  if (sampledFrames != 195 || emfHits != pureHits || emfHits < 12) {
   throw new AssertionError("Unexpected attack sweep counts: sampled=" + sampledFrames + " pureHits=" + pureHits + " emfHits=" + emfHits);
  }

  // 2. Non-attack THIRD_PERSON_MODEL layers
  String[][] extraLayers = {
   { "action_layer", "bash.json" },
   { "defense_layer", "parry_one_handed_up.json" },
   { "pose_action_layer", "battleaxe_ultimate_stance.json" },
   { "first_person_pose_layer", "one_handed_sneak.json" }
  };
  for (String[] pair : extraLayers) {
   String layerName = pair[0];
   String file = allAnims.containsKey(pair[1]) ? pair[1] : allAnims.keySet().iterator().next();
   Animation anim = allAnims.get(file);
   int tick = Math.max(1, (int) (anim.length() * 0.4f));

   mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
   pureRig.playOnLayer(layerName, anim, tick, 0.5f);
   FrameCapture pureCap = pureRig.renderFirstPersonPass(0.5f, false);

   mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
   emfRig.playOnLayer(layerName, anim, tick, 0.5f);
   FrameCapture emfCap = emfRig.renderFirstPersonPass(0.5f, true);

   assertCapturesMatch("layer:" + layerName + ":" + file, pureCap, emfCap);
  }

  // 3. Sneaking first-person attack (real EMFCompatAnimationHook.emfcompat$repairCrouchPose active)
  Animation refSwing = allAnims.get("hnsattack_one_handed_katana_leftswing.json");
  if (refSwing == null) refSwing = allAnims.get(attackNames.get(0));
  pureRig.player.crouching = true;
  emfRig.player.crouching = true;
  Probe252.sneaking = true;
  try {
   mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
   pureRig.playOnLayer("attack_layer", refSwing, 5, 0.35f);
   FrameCapture pureSneak = pureRig.renderFirstPersonPass(0.35f, false);

   mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
   emfRig.playOnLayer("attack_layer", refSwing, 5, 0.35f);
   FrameCapture emfSneak = emfRig.renderFirstPersonPass(0.35f, true);

   assertCapturesMatch("sneak-attack", pureSneak, emfSneak);
  } finally {
   pureRig.player.crouching = false;
   emfRig.player.crouching = false;
   Probe252.sneaking = false;
  }

  // 4. Unpossessed player + invisible player (Shadow Form) + cold-start hooks (never pressed F5)
  EMFState.resetDefaultHooks();
  resetBridgeInstalledForFreshBootTest();
  PossessionClientSync.CLIENT_STATES.clear();
  emfRig.player.invisible = true;
  try {
   mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
   pureRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
   FrameCapture pureUnpossessed = pureRig.renderFirstPersonPass(0.4f, false);

   mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
   emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
   FrameCapture emfUnpossessed = emfRig.renderFirstPersonPass(0.4f, true);

   assertCapturesMatch("unpossessed-invisible-coldstart", pureUnpossessed, emfUnpossessed);
  } finally {
   emfRig.player.invisible = false;
  }

  // 5. Slim model + dual-wield
  Rig slimPure = new Rig(true);
  Rig slimEmf = new Rig(true);
  mc.player = slimPure.player; mc.cameraEntity = slimPure.player;
  slimPure.playOnLayer("attack_layer", refSwing, 5, 0.25f);
  FrameCapture slimPureCap = slimPure.renderFirstPersonPass(0.25f, false);

  mc.player = slimEmf.player; mc.cameraEntity = slimEmf.player;
  slimEmf.playOnLayer("attack_layer", refSwing, 5, 0.25f);
  FrameCapture slimEmfCap = slimEmf.renderFirstPersonPass(0.25f, true);
  assertCapturesMatch("slim-dualwield", slimPureCap, slimEmfCap);

  // 6. Late third-party hook registered AFTER our hooks gets re-bracketed on next render pass
  EMFAnimationApi.EMFAnimationHook lateHook = new EMFAnimationApi.EMFAnimationHook() {
   @Override
   public void onAnimationEnd(AnimationContext ctx, boolean cancelled) {
    if (!ctx.activeState().isFirstPersonHand() && ctx.animatingModelRoot().isMainModel) {
     ctx.animatingModelRoot().getChild("right_arm").xRot = 1.234f;
    }
   }
  };
  EMFAnimationApi.registerAnimationHook(lateHook);
  try {
   mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
   emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
   FrameCapture rebracketed = emfRig.renderFirstPersonPass(0.4f, true);
   mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
   pureRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
   FrameCapture pureForRebracket = pureRig.renderFirstPersonPass(0.4f, false);
   assertCapturesMatch("late-hook-rebracket", pureForRebracket, rebracketed);
  } finally {
   EMFState.animationHooks.remove(lateHook);
  }

  // 7. Non-interference negative checks:
  // 7a. Third-person camera (F5): FA + TnM MUST still mutate arm/pack normally (even if a mod leaves FirstPersonPass=true)
  mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
  mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
  emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
  FrameCapture thirdPersonCap = emfRig.renderFirstPersonPass(0.4f, true);
  float tpRightArmXRot = thirdPersonCap.partPose.get("/right_arm")[3];
  if (Math.abs(tpRightArmXRot - 0.04f) > 1e-4f) {
   throw new AssertionError("Expected 3rd-person camera not to trigger 1st-person snapshot, got xRot=" + tpRightArmXRot);
  }
  FirstPersonMode.setFirstPersonPass(true);
  try {
   EMFState.FIRST_PERSON = false;
   EMFState.ENTITY = emfRig.player;
   EMFState.isMainPhase = true;
   emfRig.root.getChild("right_arm").xRot = -1.42f;
   traben.entity_model_features.EMFManager.getInstance().entityRenderCount++;
   emfRig.root.animate();
   if (Math.abs(emfRig.root.getChild("right_arm").xRot - 0.04f) > 1e-4f) {
    throw new AssertionError("Expected 3rd-person camera with stale FirstPersonPass not to snapshot, got xRot=" + emfRig.root.getChild("right_arm").xRot);
   }
  } finally {
   FirstPersonMode.setFirstPersonPass(false);
  }
  mc.options.setCameraType(CameraType.FIRST_PERSON);

  // 7b. Camera attached to another entity (freecam / spectator camera != player): must NOT snapshot
  Entity spectatorTarget = new Entity();
  mc.cameraEntity = spectatorTarget;
  emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
  FirstPersonMode.setFirstPersonPass(true);
  try {
   EMFState.FIRST_PERSON = false;
   EMFState.ENTITY = emfRig.player;
   EMFState.isMainPhase = true;
   emfRig.root.getChild("right_arm").xRot = -1.5f;
   traben.entity_model_features.EMFManager.getInstance().entityRenderCount++;
   emfRig.root.animate();
   if (Math.abs(emfRig.root.getChild("right_arm").xRot - 0.04f) > 1e-4f) {
    throw new AssertionError("Freecam cameraEntity != player must not trigger 1st-person snapshot");
   }
  } finally {
   FirstPersonMode.setFirstPersonPass(false);
   mc.cameraEntity = emfRig.player;
  }

  // 7c. Ordinary vanilla/FA first-person hand (EMFState.FIRST_PERSON == true, even if FirstPersonPass were set)
  emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
  FirstPersonMode.setFirstPersonPass(true);
  try {
   EMFState.FIRST_PERSON = true;
   EMFState.ENTITY = emfRig.player;
   EMFState.isMainPhase = true;
   emfRig.root.getChild("right_arm").xRot = -0.99f;
   traben.entity_model_features.EMFManager.getInstance().entityRenderCount++;
   emfRig.root.animate();
   if (Math.abs(emfRig.root.getChild("right_arm").xRot - (-0.42f)) > 1e-4f) {
    throw new AssertionError("Ordinary FA first-person hand animation was overwritten: xRot=" + emfRig.root.getChild("right_arm").xRot);
   }
  } finally {
   EMFState.FIRST_PERSON = false;
   FirstPersonMode.setFirstPersonPass(false);
  }

  // 7c2. Inventory paperdoll / full-body render while cameraType == FIRST_PERSON (FirstPersonMode.isFirstPersonPass() == false)
  // Must NOT hijack EMF/TnM full-body animation!
  emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
  FirstPersonMode.setFirstPersonPass(false);
  EMFState.FIRST_PERSON = false;
  EMFState.ENTITY = emfRig.player;
  EMFState.isMainPhase = true;
  emfRig.root.getChild("right_arm").xRot = -1.61f;
  traben.entity_model_features.EMFManager.getInstance().entityRenderCount++;
  emfRig.root.animate();
  if (Math.abs(emfRig.root.getChild("right_arm").xRot - 0.04f) > 1e-4f) {
   throw new AssertionError("Non-FirstPersonPass full-body render (paperdoll) was hijacked: xRot=" + emfRig.root.getChild("right_arm").xRot);
  }

  // 7d. FirstPersonMode.NONE layer (parkour_layer) active in 1st person: must NOT snapshot
  Animation anyAnim = allAnims.values().iterator().next();
  emfRig.playOnLayer("parkour_layer", anyAnim, 2, 0.2f);
  FrameCapture parkourCap = emfRig.renderFirstPersonPass(0.2f, true);
  if (Math.abs(parkourCap.partPose.get("/right_arm")[3] - 0.04f) > 1e-4f) {
   throw new AssertionError("FirstPersonMode.NONE layer must not trigger 1st-person snapshot");
  }

  // 7e. Exception inside EMFAnimationHandler.animate() is caught by real EMFModelPartRoot,
  // invokes TAIL_HOOK.onAnimationEnd to restore pose and clean up ThreadLocal without leaking
  Rig errRig = new Rig(false);
  Probe252.throwInAnimate = true;
  try {
   mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
   pureRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
   FrameCapture errPure = pureRig.renderFirstPersonPass(0.4f, false);

   mc.player = errRig.player; mc.cameraEntity = errRig.player;
   errRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
   FrameCapture errRecovered = errRig.renderFirstPersonPass(0.4f, true);
   assertCapturesMatch("in-frame-exception-recovery", errPure, errRecovered);
  } finally {
   Probe252.throwInAnimate = false;
  }
  mc.player = pureRig.player; mc.cameraEntity = pureRig.player;
  pureRig.playOnLayer("attack_layer", refSwing, 5, 0.3f);
  FrameCapture postErrPure = pureRig.renderFirstPersonPass(0.3f, false);
  mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
  emfRig.playOnLayer("attack_layer", refSwing, 5, 0.3f);
  FrameCapture postErrEmf = emfRig.renderFirstPersonPass(0.3f, true);
  assertCapturesMatch("post-exception-recovery", postErrPure, postErrEmf);

  // 7f. Private off-screen EMFModelPartRoot (e.g. FreshEmfAccess private root, != renderer's root) must NOT be touched
  EMFModelPartRoot privateRoot = (EMFModelPartRoot) RealEmfFactory.make(false);
  privateRoot.isMainModel = true;
  mc.player = emfRig.player; mc.cameraEntity = emfRig.player;
  (emfRig.player.slim ? mc.dispatcher.slimRenderer : mc.dispatcher.renderer).model = emfRig.renderer.getModel();
  emfRig.playOnLayer("attack_layer", refSwing, 4, 0.4f);
  FirstPersonMode.setFirstPersonPass(true);
  try {
   EMFState.FIRST_PERSON = false;
   EMFState.ENTITY = emfRig.player;
   EMFState.isMainPhase = true;
   privateRoot.getChild("right_arm").xRot = -1.75f;
   traben.entity_model_features.EMFManager.getInstance().entityRenderCount++;
   privateRoot.animate();
   if (Math.abs(privateRoot.getChild("right_arm").xRot - 0.04f) > 1e-4f) {
    throw new AssertionError("Private non-renderer EMFModelPartRoot was unexpectedly snapshotted!");
   }
  } finally {
   FirstPersonMode.setFirstPersonPass(false);
  }

  // 7g. Verify external JAR provenance for EMF, Core, TnM, PAL, and HnS classes
  for (Class<?> extCls : new Class<?>[]{
   EMFModelPartRoot.class,
   strm.emfcompat.core.EMFCompatAnimationHook.class,
   strm.touchnmotion.TouchNMotionHook.class,
   Class.forName("com.zigythebird.playeranim.mixin.PlayerModelMixin"),
   BladeTracker.class
  }) {
   String loc = String.valueOf(extCls.getProtectionDomain().getCodeSource().getLocation());
   if (!loc.endsWith(".jar")) {
    throw new AssertionError("Expected external JAR code source for " + extCls.getName() + ", got " + loc);
   }
  }

  System.out.println("PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / "
   + sampledFrames + " frames, frontHits=" + emfHits + "/" + pureHits + ", 5 layers, sneak, unpossessed/invisible, slim+dualwield, negative checks");
 }

 private static void resetBridgeInstalledForFreshBootTest() throws Exception {
  Class<?> bridge = Class.forName("dev.settledlands.model.FirstPersonSwordBridge252");
  for (String fn : new String[]{ "checked", "disabled", "warned" }) {
   Field f = bridge.getDeclaredField(fn);
   f.setAccessible(true);
   f.setBoolean(null, false);
  }
  Field eh = bridge.getDeclaredField("ensureHooks");
  eh.setAccessible(true);
  eh.set(null, null);
 }
}
