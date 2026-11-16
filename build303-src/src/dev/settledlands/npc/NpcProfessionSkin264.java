package dev.settledlands.npc;

/**
 * Server-safe profession selection and model-path mapping for the Ivy profession skins.
 *
 * <p>Build 286: the bundled profession model was replaced with the current Blockbench export,
 * which carries 63 textures instead of 41. The female Japanese fisherman texture
 * ({@code rybak_jp_fem.png}) exists in that export, so the female + Japanese fisherman combination
 * now resolves to a real texture instead of silently falling back to the male one. The same export
 * adds Japanese pairs for fermer, celitel, shahter, lesorub, ohotnik and traktirshik, plus female
 * mage and female blacksmith skins; every name below exists in
 * {@code assets/settledlands/dragon_default/ivy_oleander_professions.bbmodel}.</p>
 */
public final class NpcProfessionSkin264 {
    public static final String BASE_MODEL = "ivy_oleander_professions.bbmodel";
    private static final String[][] TEXTURES = {
        {"", ""},
        {"ribak.png", "ribak_fem.png", "rybak_jp.png", "rybak_jp_fem.png"},
        {"pastuh.png", "pastuh_fem.png", "pastuh_jp.png", "pastuh_jp_fem.png"},
        {"straj.png", "straj_fem.png", "strazh_jp.png", "strazh_jp_fem.png"},
        {"comandir.png", "comandir_fem.png", "komandir_jp.png", "komandir_jp_fem.png"},
        {"krestonosec.png", "krestonosec_fem.png", "", ""},
        {"fermer.png", "Fermer_fem.png", "fermer_jp.png", "fermer_jp_fem.png"},
        {"kuznik.png", "kuznik_fem.png", "kuznec_jp.png", "kuznec_jp_fem.png"},
        {"torgovec.png", "torgovec_fem.png", "", ""},
        {"selanin.png", "selanin_fem.png", "", ""},
        {"mag.png", "mag_fem.png", "", ""},
        {"celitel.png", "celitel_fem.png", "celitel_jp.png", "celitel_jp_fem.png"},
        {"shahter.png", "shahter_fem.png", "shahter_jp.png", "shahter_jp_fem.png"},
        {"lesorub.png", "lesorub_fem.png", "lesorub_jp.png", "lesorub_jp_fem.png"},
        {"ohotnik.png", "ohotnik_fem.png", "ohotnik_jp.png", "ohotnik_jp_fem.png"},
        {"traktirshik.png", "traktirshik_fem.png", "traktirshik_jp.png", "traktirshik_jp_fem.png"},
        {"volshebnik.png", "volshebnik_fem.png", "", ""}
    };

    private NpcProfessionSkin264() {}

    public static String textureName(int profession, boolean male, boolean japanese) {
        if (profession <= 0 || profession >= TEXTURES.length) return "ivy_oleander.png";
        String[] variants = TEXTURES[profession];
        String chosen = "";
        if (japanese) {
            chosen = variants[male ? 2 : 3];
            if (chosen.isEmpty()) chosen = variants[male ? 3 : 2];
        }
        if (chosen.isEmpty()) {
            chosen = variants[male ? 0 : 1];
            if (chosen.isEmpty()) chosen = variants[male ? 1 : 0];
        }
        return chosen.isEmpty() ? "ivy_oleander.png" : chosen;
    }

    public static String modelName(int profession, boolean male, boolean japanese) {
        if (profession <= NpcStatsGraph.PROFESSION_NONE || profession > NpcStatsGraph.PROFESSION_COUNT) return null;
        return "ivy_oleander_prof264_p" + profession + "_m" + (male ? 1 : 0)
                + "_j" + (japanese ? 1 : 0) + ".bbmodel";
    }

    public static String modelName(NpcProfile profile) {
        if (profile == null || profile.dialogueGraph == null) return null;
        NpcDialogueGraph.Node n = NpcStatsGraph.find(profile.dialogueGraph);
        if (n == null) return null;
        return modelName(NpcStatsGraph.profession(n), NpcStatsGraph.male(n), NpcStatsGraph.japanese(n));
    }

}
