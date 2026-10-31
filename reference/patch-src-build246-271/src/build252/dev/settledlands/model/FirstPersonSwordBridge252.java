package dev.settledlands.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Optional first-person Hackers 'n Slashers (2.0-beta2.5) + PAL (1.1.6+mc.1.21.1)
 * bridge over Entity Model Features (3.3.9) / Fresh Animations / Touch'n Motion (1.0.4).
 *
 * Keeps third-person Ivy rendering, unpossessed third-person rendering, and
 * ordinary first-person hand rendering untouched. Resolves optional mods lazily
 * so environments without ModList/EMF/PAL verify and run unmodified.
 */
public final class FirstPersonSwordBridge252 {
    private static boolean checked, disabled, warned;
    private static Method ensureHooks;

    private FirstPersonSwordBridge252() {}

    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (disabled || event == null) return;
        Player raw = event.getEntity();
        if (!(raw instanceof AbstractClientPlayer player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player != player || mc.options == null) return;
        CameraType cameraType = mc.options.getCameraType();
        if (cameraType == null || !cameraType.isFirstPerson()) return;
        if (!available()) return;
        try {
            ensureHooks.invoke(null);
        } catch (Throwable error) {
            disable(error);
        }
    }

    private static synchronized boolean available() {
        if (disabled) return false;
        if (checked) return ensureHooks != null;
        checked = true;
        ClassLoader loader = FirstPersonSwordBridge252.class.getClassLoader();
        Object mods;
        Method isLoaded;
        Method getContainer;
        Method getModInfo;
        Method getVersion;
        try {
            Class<?> modListType = Class.forName("net.neoforged.fml.ModList", false, loader);
            Class<?> modContainerType = Class.forName("net.neoforged.fml.ModContainer", false, loader);
            Class<?> modInfoType = Class.forName("net.neoforged.neoforgespi.language.IModInfo", false, loader);
            mods = modListType.getMethod("get").invoke(null);
            if (mods == null) return false;
            isLoaded = modListType.getMethod("isLoaded", String.class);
            getContainer = modListType.getMethod("getModContainerById", String.class);
            getModInfo = modContainerType.getMethod("getModInfo");
            getVersion = modInfoType.getMethod("getVersion");
        } catch (ClassNotFoundException | NoClassDefFoundError missing) {
            return false;
        } catch (Throwable error) {
            disable(error);
            return false;
        }
        try {
            if (!loaded(mods, isLoaded, "hackersandslashers")
                    || !loaded(mods, isLoaded, "player_animation_library")
                    || !loaded(mods, isLoaded, "entity_model_features")) {
                return false;
            }
            if (loaded(mods, isLoaded, "emf_compat_hackers_and_slashers")) {
                warn("Separate emf_compat_hackers_and_slashers addon detected; first-person HnS bridge is off.", null);
                return false;
            }
            String hns = versionOf(mods, getContainer, getModInfo, getVersion, "hackersandslashers");
            String pal = versionOf(mods, getContainer, getModInfo, getVersion, "player_animation_library");
            String emf = versionOf(mods, getContainer, getModInfo, getVersion, "entity_model_features");
            if (!"2.0-beta2.5".equals(hns)
                    || !"1.1.6+mc.1.21.1".equals(pal)
                    || (!"3.3.9".equals(emf) && !"3.3.12".equals(emf))) {
                warn("Unsupported HnS/PAL/EMF version (" + hns + "/" + pal + "/" + emf + "); first-person HnS bridge is off.", null);
                return false;
            }
            if (loaded(mods, isLoaded, "touch_n_motion")
                    && !"1.0.4".equals(versionOf(mods, getContainer, getModInfo, getVersion, "touch_n_motion"))) {
                warn("Unsupported touch_n_motion version; first-person HnS bridge is off.", null);
                return false;
            }
            if (loaded(mods, isLoaded, "emf_compat_core")
                    && !"2.3.2".equals(versionOf(mods, getContainer, getModInfo, getVersion, "emf_compat_core"))) {
                warn("Unsupported emf_compat_core version; first-person HnS bridge is off.", null);
                return false;
            }
            Class<?> access = Class.forName("dev.settledlands.model.FirstPersonPalEmfAccess252", true, loader);
            Method ensure = access.getMethod("ensureInstalled");
            if (!Boolean.TRUE.equals(ensure.invoke(null))) {
                warn("Could not install EMF first-person HnS hooks; first-person bridge is off.", null);
                return false;
            }
            ensureHooks = ensure;
            return true;
        } catch (Throwable error) {
            disable(error);
            return false;
        }
    }

    private static boolean loaded(Object mods, Method isLoaded, String id) throws Exception {
        return Boolean.TRUE.equals(isLoaded.invoke(mods, id));
    }

    private static String versionOf(Object mods, Method getContainer, Method getModInfo, Method getVersion, String id) throws Exception {
        Object optObj = getContainer.invoke(mods, id);
        if (!(optObj instanceof Optional<?> opt) || opt.isEmpty()) return "";
        Object container = opt.get();
        Object info = getModInfo.invoke(container);
        if (info == null) return "";
        Object version = getVersion.invoke(info);
        return version == null ? "" : String.valueOf(version);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
    }

    private static void warn(String message, Throwable error) {
        if (warned) return;
        warned = true;
        System.err.println("[SettledLands/Motion252] " + message);
        if (error != null) error.printStackTrace(System.err);
    }

    private static void disable(Throwable error) {
        disabled = true;
        warn("First-person HnS/PAL bridge failed and is disabled.", unwrap(error));
    }
}
