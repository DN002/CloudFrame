package dev.cloudframe.cloudframe.core;

import dev.cloudframe.cloudframe.CloudFrame;
import dev.cloudframe.cloudframe.markers.MarkerManager;
import dev.cloudframe.cloudframe.quarry.QuarryManager;
import dev.cloudframe.cloudframe.tubes.ItemPacketManager;
import dev.cloudframe.cloudframe.tubes.TubeNetworkManager;

/**
 * Central place to register managers (quarry manager, tube manager, item packet manager, etc.)
 */
public class CloudFrameRegistry {

    private static CloudFrameEngine engine;
    private static MarkerManager markerManager;
    private static QuarryManager quarryManager;
    private static TubeNetworkManager tubeManager;
    private static ItemPacketManager packetManager;
    private static CloudFrame plugin;



    public static void init(CloudFrameEngine eng) {
        engine = eng;
        markerManager = new MarkerManager();
        quarryManager = new QuarryManager();
        tubeManager = new TubeNetworkManager();
        packetManager = new ItemPacketManager();
    }

    public static CloudFrameEngine engine() {
        return engine;
    }

    public static MarkerManager markers() {
        return markerManager;
    }

    public static QuarryManager quarries() {
        return quarryManager;
    }
    
    public static TubeNetworkManager tubes() {
    	return tubeManager;
    }
    
    public static ItemPacketManager packets() {
    	return packetManager;
    }

    public static void init(CloudFrame pl) {
    	plugin = pl;
    }
    
    public static CloudFrame plugin() {
    	return plugin;
    }
}
