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

        // Force creative tab registration (some Fabric versions require client-side call)
        if (CloudFrameContent.CLOUD_FRAME_ITEM_GROUP != null) {
            // No-op: accessing the group forces registration in some environments
            Object ignore = CloudFrameContent.CLOUD_FRAME_ITEM_GROUP.getDisplayName();
        }

        // Log loaded recipes for diagnostics (client side)
        try {
            // This is a diagnostic: will only work if Minecraft client is initialized enough
            var recipeManagerField = Class.forName("net.minecraft.client.MinecraftClient").getDeclaredField("recipeManager");
            recipeManagerField.setAccessible(true);
            Object recipeManager = recipeManagerField.get(Class.forName("net.minecraft.client.MinecraftClient").getMethod("getInstance").invoke(null));
            var recipes = (Iterable<?>) recipeManager.getClass().getMethod("values").invoke(recipeManager);
            int cloudframeCount = 0;
            for (Object recipe : recipes) {
                var getId = recipe.getClass().getMethod("getId");
                Object id = getId.invoke(recipe);
                if (id != null && id.toString().startsWith("cloudframe:")) {
                    cloudframeCount++;
                }
            }
            System.out.println("[CloudFrame] Loaded CloudFrame recipes (client): " + cloudframeCount);
        } catch (Throwable t) {
            System.out.println("[CloudFrame] Could not enumerate recipes (client): " + t);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof QuarryControllerScreen screen) {
                screen.onClientTick();
            }
        });
    }
}
