package org.vmstudio.visor.loader.fabric;


import net.fabricmc.api.ModInitializer;
//? if <1.20.2 {
/*import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
*///?}

public class VisorMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Quest (coah80/Visor 32c80265): automatic Chunky preload of new worlds, 1.20.1 only
        //? if <1.20.2 {
        /*WorldPreloader.initialize();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            WorldPreloaderClient.initialize();
        }
        *///?}
    }
}
