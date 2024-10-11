package com.github.agawronteam.changedtestsrunner;

import com.intellij.openapi.diagnostic.Logger;

import static com.github.agawronteam.changedtestsrunner.Constants.LOGGER_NAME;


public class MyLogger {
    private static final Logger logger = Logger.getInstance(LOGGER_NAME);
    public static Logger getLogger() {
        return logger;
    }
}
