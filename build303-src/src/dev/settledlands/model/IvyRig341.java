package dev.settledlands.model;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 341: the Fresh Animations / Touch'n Motion pose is offered to every Ivy rig, not two names.
 *
 * <p>The possessed player's body is retargeted from the player model's Fresh Animations pose only when
 * the actor model answers to {@code ivy} or {@code ivy_oleander}. The shipped models also include
 * {@code ivy_oleander_professions}, which is the same rig with the profession bones - every NPC with a
 * profession uses it - and it was not on that list. Such a body therefore fell back to the model's own
 * clips, where Fresh Animations and Touch'n Motion cannot appear at all: the pose Touch'n Motion works
 * out was never carried onto the bones, so the character never reached for a lever.</p>
 *
 * <p>This class answers "is this one of the Ivy rigs" by prefix instead of by two exact names, so the
 * profession variant is covered. The rig validation of the bridge still runs afterwards: a model that
 * merely starts with {@code ivy} but has no body/waist/limb bones is still refused, exactly as before.
 * Nothing else about the retarget changes.</p>
 */
public final class IvyRig341 {

    private static final Logger LOGGER = LoggerFactory.getLogger("SettledLands/IvyRig341");

    /** Prefix every shipped actor rig shares. */
    private static final String PREFIX = "ivy";

    /** Bounded report: one line per actor model, and never more than this many. */
    private static final int REPORT_LIMIT = 4;

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private IvyRig341() {
    }

    /** True for any Ivy rig, including the profession variant the exact-name check left out. */
    public static boolean isIvyRig(BbModel model) {
        return model != null && isIvyName(model.getName());
    }

    /** The name test on its own, so it can be checked without a loaded model. */
    public static boolean isIvyName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    /** Reports the first few actor models the retarget gate is asked about, so a live log says which. */
    public static void note(BbModel model, boolean accepted) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String name = model == null ? "?" : String.valueOf(model.getName());
        if (!REPORTED.add(name)) return;
        LOGGER.warn("actor rig '{}' offered to the Fresh Animations / Touch'n Motion retarget: {}",
                name, accepted ? "accepted" : "inherited");
    }
}
