package grpc.server.services;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PrismGrpcLogger {

    private static PrismGrpcLogger instance;
    private final Logger logger;

    private PrismGrpcLogger() {
        this.logger = Logger.getLogger(PrismGrpcLogger.class.getName());

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                SimpleDateFormat logTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
                return logTime.format(new Date(record.getMillis())) + " - " +
                        record.getLevel() + " - " +
                        record.getMessage() + "\n";
            }
        });

        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    public static synchronized PrismGrpcLogger getLogger() {
        if (instance == null) {
            instance = new PrismGrpcLogger();
        }
        return instance;
    }

    public void info(String msg) {
        logger.log(Level.INFO, msg);
    }

    public void warning(String msg) {
        logger.log(Level.WARNING, msg);
    }

    public void severe(String msg) {
        logger.log(Level.SEVERE, msg);
    }

}
