package insane96mcp.progressivebosses;

import insane96mcp.progressivebosses.commands.DifficultyCommand;
import insane96mcp.progressivebosses.setup.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("progressivebosses")
public class ProgressiveBosses {

    public static final String MOD_ID = "progressivebosses";
    public static final String RESOURCE_PREFIX = MOD_ID + ":";

    public static final Logger LOGGER = LogManager.getLogger();

    public ProgressiveBosses() {

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
    }

    @SubscribeEvent
    public void serverStarting(FMLServerStartingEvent event) {
        DifficultyCommand.register(event.getCommandDispatcher());
    }
}
