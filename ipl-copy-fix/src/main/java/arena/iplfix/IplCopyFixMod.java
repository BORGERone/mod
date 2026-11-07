package arena.iplfix;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(IplCopyFixMod.MOD_ID)
public class IplCopyFixMod {

    public static final String MOD_ID = "ipl_copy_fix";
    public static final Logger LOGGER = LoggerFactory.getLogger("ipl-copy-fix");

    public IplCopyFixMod() {
        LOGGER.info("arena build marker: ipl-copy-fix-1.0.0 (format tolerant IP x Iris framebuffer copies)");
    }
}
