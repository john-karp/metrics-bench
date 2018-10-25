package metricsbench.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class Misc {

    public static void printGarbageCollectionTime() {
        long collectionTime = 0;
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectionTime += garbageCollectorMXBean.getCollectionTime();
        }
        System.err.println(collectionTime);
    }

}
