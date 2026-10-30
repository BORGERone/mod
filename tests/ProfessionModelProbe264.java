import dev.settledlands.npc.NpcProfessionSkin264;
import dev.settledlands.npc.client.NpcProfessionModel264;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exercises the actual JAR helper against a temporary model directory. */
public final class ProfessionModelProbe264 {
    private static final Pattern FIRST_TEXTURE_SOURCE = Pattern.compile(
            "\\\"textures\\\"\\s*:\\s*\\[\\s*\\{.*?\\\"source\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"",
            Pattern.DOTALL);

    public static void main(String[] args) throws Exception {
        for (int profession = 1; profession <= 16; profession++) {
            for (int male = 0; male <= 1; male++) {
                for (int japanese = 0; japanese <= 1; japanese++) {
                    String modelName = NpcProfessionSkin264.modelName(profession, male == 1, japanese == 1);
                    File file = NpcProfessionModel264.materialize(modelName);
                    if (file == null || !file.isFile()) throw new AssertionError("missing " + modelName);
                    String json = Files.readString(file.toPath());
                    Matcher source = FIRST_TEXTURE_SOURCE.matcher(json);
                    if (!source.find()) throw new AssertionError("texture source missing in " + modelName);
                    Matcher allSources = Pattern.compile("\\\"source\\\"\\s*:").matcher(json);
                    int sourceCount = 0;
                    while (allSources.find()) sourceCount++;
                    if (sourceCount != 1 || Files.size(file.toPath()) > 3_100_000L) {
                        throw new AssertionError("variant retained unrelated textures: " + modelName + " sourceCount=" + sourceCount + " size=" + Files.size(file.toPath()));
                    }
                    byte[] hash = MessageDigest.getInstance("SHA-256")
                            .digest(source.group(1).getBytes(StandardCharsets.UTF_8));
                    StringBuilder hex = new StringBuilder();
                    for (byte b : hash) hex.append(String.format("%02x", b));
                    System.out.println(profession + "," + male + "," + japanese + ","
                            + NpcProfessionSkin264.textureName(profession, male == 1, japanese == 1) + "," + hex);
                    Files.delete(file.toPath());
                }
            }
        }
    }
}
