package dev.cloudframe.fabric.client;

import dev.cloudframe.fabric.quarry.controller.QuarryControllerScreen;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class CloudFrameFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(CloudFrameContent.getQuarryControllerScreenHandler(), QuarryControllerScreen::new);
    }
}
