package de.danoeh.antennapod.net.sync.serviceinterface;

import de.danoeh.antennapod.model.feed.FeedMedia;

public abstract class PodHopperPositionSync {
    private static PodHopperPositionSync instance;

    public static PodHopperPositionSync getInstance() {
        return instance;
    }

    public static void setInstance(PodHopperPositionSync instance) {
        PodHopperPositionSync.instance = instance;
    }

    public abstract void pushPosition(FeedMedia media, boolean immediate);

    public abstract void pullLatestPositions();
}
