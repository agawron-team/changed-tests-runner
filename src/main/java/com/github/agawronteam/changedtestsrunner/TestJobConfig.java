package com.github.agawronteam.changedtestsrunner;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.UUID;

public class TestJobConfig {
    public UUID id;
    public String module;
    public String testClass;
    public ResultsWindowFactory.TestStatus status;
    public VirtualFile virtualFile;
    public RunnerAndConfigurationSettings runConfig;
    public TestJobConfig(UUID id, String module, String testClass, VirtualFile virtualFile, RunnerAndConfigurationSettings runConfig) {
        this.id = id;
        this.module = module;
        this.testClass = testClass;
        this.status = ResultsWindowFactory.TestStatus.QUEUED;
        this.virtualFile = virtualFile;
        this.runConfig = runConfig;
    }
}
