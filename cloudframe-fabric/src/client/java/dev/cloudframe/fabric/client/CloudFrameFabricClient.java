package dev.cloudframe.fabric.client;

import dev.cloudframe.fabric.quarry.controller.QuarryControllerScreen;
import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.pipes.filter.PipeFilterScreen;
import dev.cloudframe.fabric.content.trash.TrashCanScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class CloudFrameFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(CloudFrameContent.getQuarryControllerScreenHandler(), QuarryControllerScreen::new);
        HandledScreens.register(CloudFrameContent.getPipeFilterScreenHandler(), PipeFilterScreen::new);
        HandledScreens.register(CloudFrameContent.getTrashCanScreenHandler(), TrashCanScreen::new);
        PowerProbeClient.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof QuarryControllerScreen screen) {
                screen.onClientTick();
            }
        });
    }
}
