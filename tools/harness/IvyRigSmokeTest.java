import dev.settledlands.model.IvyRig341;

/**
 * Runs build 341's rig test against every actor model the mod ships: the two names the old exact
 * check knew, the profession variant it refused, and a model that only looks similar.
 */
public class IvyRigSmokeTest {

    static int failures = 0;

    static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("ok    " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    public static void main(String[] args) {
        check("no model is not a rig", !IvyRig341.isIvyName(null));
        check("ivy is a rig", IvyRig341.isIvyName("ivy"));
        check("ivy_oleander is a rig", IvyRig341.isIvyName("ivy_oleander"));
        check("ivy_oleander_professions is a rig", IvyRig341.isIvyName("ivy_oleander_professions"));
        check("the check ignores case", IvyRig341.isIvyName("IVY_OLEANDER_Professions"));
        check("the coffin is not a rig", !IvyRig341.isIvyName("corpse_coffin_v2"));
        check("a name that merely contains ivy is not a rig", !IvyRig341.isIvyName("my_ivy_copy"));
        check("an empty name is not a rig", !IvyRig341.isIvyName(""));
        check("a null model is not a rig", !IvyRig341.isIvyRig(null));

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }
}
