package dev.settledlands.npc.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Minimal bytecode-surface gate invoked from PlayerBodyRenderer when
 * {@code mc.player == player && mc.options.getCameraType().isFirstPerson()}.
 *
 * Intentionally avoids direct bytecode references to optional mod types or
 * non-core Minecraft fields so -Xverify:all passes even under minimal test stubs.
 */
public final class PlayerFirstPersonBodyGate256 {
    private static final int FULL_BRIGHT_GUI_LIGHT = 0x00F000F0;

    private static volatile boolean initialized;
    private static Method palIsFirstPersonPass;
    private static Method tr7zwIsRenderingPlayer;
    private static Object tr7zwApiInstance;
    private static Field emfIsInGui;
    private static Field mcScreenField;
    private static Method getDispatcherMethod;
    private static Method shouldRenderShadowMethod;
    private static Method isSleepingMethod;

    private PlayerFirstPersonBodyGate256() {}

    private static void ensureInitialized() {
        if (initialized) return;
        synchronized (PlayerFirstPersonBodyGate256.class) {
            if (initialized) return;
            ClassLoader loader = PlayerFirstPersonBodyGate256.class.getClassLoader();
            try {
                Class<?> fpMode = Class.forName("com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode", false, loader);
                palIsFirstPersonPass = fpMode.getMethod("isFirstPersonPass");
            } catch (Throwable ignored) {
            }
            try {
                Class<?> fpApi = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI", false, loader);
                tr7zwIsRenderingPlayer = fpApi.getMethod("isRenderingPlayer");
                try {
                    tr7zwApiInstance = fpApi.getMethod("getInstance").invoke(null);
                } catch (Throwable ignored2) {
                    tr7zwApiInstance = null;
                }
            } catch (Throwable ignored) {
            }
            try {
                Class<?> emfState = Class.forName("traben.entity_model_features.models.animation.state.EMFState", false, loader);
                emfIsInGui = emfState.getField("isInGui");
            } catch (Throwable ignored) {
            }
            try {
                mcScreenField = Minecraft.class.getField("screen");
            } catch (Throwable ignored) {
            }
            try {
                getDispatcherMethod = Minecraft.class.getMethod("getEntityRenderDispatcher");
                Class<?> dispType = getDispatcherMethod.getReturnType();
                shouldRenderShadowMethod = dispType.getMethod("shouldRenderShadow");
            } catch (Throwable ignored) {
            }
            try {
                isSleepingMethod = AbstractClientPlayer.class.getMethod("isSleeping");
            } catch (Throwable ignored) {
            }
            initialized = true;
        }
    }

    public static boolean blockFirstPersonRender(boolean isFirstPerson,
                                                 Minecraft mc,
                                                 AbstractClientPlayer player,
                                                 RenderPlayerEvent.Pre event) {
        if (!isFirstPerson) return false;
        return !shouldRenderBodyInFirstPerson(mc, player, event);
    }

    public static boolean shouldRenderBodyInFirstPerson(Minecraft mc,
                                                        AbstractClientPlayer player,
                                                        RenderPlayerEvent.Pre event) {
        if (mc == null || player == null || event == null) return false;
        // Never cancel or draw on null buffer probes (e.g. parkour/layer checks in world pass).
        if (event.getMultiBufferSource() == null) return false;

        ensureInitialized();

        // Never draw full 3rd-person NPC body over the camera during PAL / HnS 1st-person pass
        // or FirstPersonModel world pass.
        if (isFirstPersonWorldPass()) return false;

        // 1. EMF explicitly flags InventoryScreen / GUI entity rendering via EMFState.isInGui.
        if (isEmfGui()) return true;

        // 2. Open GUI screen (InventoryScreen, CreativeModeInventoryScreen, Curios, Horse, etc.).
        if (hasOpenScreen(mc)) return true;

        // 3. Sleeping in a bed while camera is in FIRST_PERSON.
        if (isPlayerSleeping(player)) return true;

        // 4. Detached HUD paperdoll renders disable dispatcher shadows and use full-bright GUI light + partialTick 1.0F.
        return isDetachedPaperdollPass(mc, event);
    }

    public static boolean isFirstPersonWorldPass() {
        ensureInitialized();
        if (palIsFirstPersonPass != null) {
            try {
                if (Boolean.TRUE.equals(palIsFirstPersonPass.invoke(null))) return true;
            } catch (Throwable ignored) {
            }
        }
        if (tr7zwIsRenderingPlayer != null) {
            try {
                Object res = tr7zwIsRenderingPlayer.invoke(tr7zwApiInstance);
                if (Boolean.TRUE.equals(res)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean isEmfGui() {
        if (emfIsInGui == null) return false;
        try {
            return emfIsInGui.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasOpenScreen(Minecraft mc) {
        if (mcScreenField == null) return false;
        try {
            return mcScreenField.get(mc) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPlayerSleeping(AbstractClientPlayer player) {
        if (isSleepingMethod == null) return false;
        try {
            return Boolean.TRUE.equals(isSleepingMethod.invoke(player));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isDetachedPaperdollPass(Minecraft mc, RenderPlayerEvent.Pre event) {
        if (getDispatcherMethod == null || shouldRenderShadowMethod == null) return false;
        if (event.getPackedLight() != FULL_BRIGHT_GUI_LIGHT) return false;
        if (Math.abs(event.getPartialTick() - 1.0f) > 1.0e-4f) return false;
        try {
            Object dispatcher = getDispatcherMethod.invoke(mc);
            if (dispatcher == null) return false;
            return Boolean.FALSE.equals(shouldRenderShadowMethod.invoke(dispatcher));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
