package connectiontest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ConnectionLogger {
    private FileOutputStream writer;
    private boolean initiated;

    public ConnectionLogger(String filename) {
        File logFile = new File(filename);
        if (logFile.exists())
            logFile.delete();
        try {
            logFile.createNewFile();
            writer = new FileOutputStream(logFile);
            initiated = true;
        } catch (IOException e) {
            initiated = false;
        }
    }

    public boolean isActive() {
        return initiated;
    }

    public void log() {
        // TODO: add logger
    }
}
