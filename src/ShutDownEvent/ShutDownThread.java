
package CoCubes.ShutDownEvent;

import CoCubes.Logging.LoggingFunctions;
import java.io.File;
import java.util.Date;

public class ShutDownThread extends Thread {
    @Override
    public void run() {
        LoggingFunctions.InsertLog("Closing Application at: " + new Date());
        File tracker = new File(LoggingFunctions.TrackerLogFile);
        if (tracker.exists()) tracker.delete();
    }
}
