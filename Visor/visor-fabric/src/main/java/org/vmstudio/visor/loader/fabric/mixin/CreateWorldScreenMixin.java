package org.vmstudio.visor.loader.fabric.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Quest (coah80/Visor 32c80265): flags a freshly created world for the Chunky preload (1.20.1 only, see WorldPreloader)
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    // require = 0: the preload only exists for 1.20.1, other targets keep a no-op hook
    @Inject(method = "onCreate", at = @At("HEAD"), require = 0)
    private void visor$prepareWorldPreload(CallbackInfo ci) {
        //? if <1.20.2 {
        /*org.vmstudio.visor.loader.fabric.WorldPreloader.markNewWorld();
        *///?}
    }
}
