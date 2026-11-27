package org.vmstudio.visor.loader.fabric;

// Quest (coah80/Visor 32c80265): automatic Chunky preload of new worlds. Ported for the 1.20.1 Quest build only.
//? if <1.20.2 {
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class WorldPreloaderClient {
    private WorldPreloaderClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (WorldPreloader.isActive()
                    && client.level != null
                    && client.player != null
                    && !(client.screen instanceof WorldPreloaderScreen)) {
                client.setScreen(new WorldPreloaderScreen());
            }
        });
    }
}
*///?}
