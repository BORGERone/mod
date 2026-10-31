package dev.settledlands.npc;

/** Server-safe profession selection and model-path mapping for the Ivy profession skins. */
public final class NpcProfessionSkin264 {
    public static final String BASE_MODEL = "ivy_oleander_professions.bbmodel";
    private static final String[][] TEXTURES = {
        {"", ""},
        {"ribak.png", "ribak_fem.png", "rybak_jp.png", ""},
        {"pastuh.png", "pastuh_fem.png", "pastuh_jp.png", "pastuh_jp_fem.png"},
        {"straj.png", "straj_fem.png", "strazh_jp.png", "strazh_jp_fem.png"},
        {"comandir.png", "comandir_fem.png", "komandir_jp.png", "komandir_jp_fem.png"},
        {"krestonosec.png", "krestonosec_fem.png", "", ""},
        {"fermer.png", "Fermer_fem.png", "", ""},
        {"kuznik.png", "", "", ""},
        {"torgovec.png", "torgovec_fem.png", "", ""},
        {"selanin.png", "selanin_fem.png", "", ""},
        {"mag.png", "", "", ""},
        {"", "celitel_fem.png", "", "celitel_jp_fem.png"},
        {"shahter.png", "shahter_fem.png", "", ""},
        {"lesorub.png", "lesorub_fem.png", "", ""},
        {"ohotnik.png", "ohotnik_fem.png", "ohotnik_jp.png", ""},
        {"traktirshik.png", "traktirshik_fem.png", "", ""},
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
        if (profile == null || !"ivy_oleander.bbmodel".equals(profile.texture) || profile.dialogueGraph == null) return null;
        NpcDialogueGraph.Node n = NpcStatsGraph.find(profile.dialogueGraph);
        if (n == null) return null;
        return modelName(NpcStatsGraph.profession(n), NpcStatsGraph.male(n), NpcStatsGraph.japanese(n));
    }

}
