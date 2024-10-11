package com.github.agawronteam.changedtestsrunner.Services;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

@State(name = "RunnerService")
@Service(Service.Level.PROJECT)
public final class RunnerService implements PersistentStateComponent<RunnerService> {

    private RunnerServiceImpl runnerServiceImpl = new RunnerServiceImpl();

    @Override
    public @Nullable RunnerService getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RunnerService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public boolean isRunningTests() {
        return runnerServiceImpl.isRunningTests();
    }

    public void runRecentlyChangedTests(Project project) {
        runnerServiceImpl.runRecentlyChangedTests(project);
    }

    public void triggerSaveConfig(ActionEvent e) {
        setShouldSaveConfig(((JCheckBox) e.getSource()).isSelected());
    }

    public void registerResultsWindow(ResultsWindowFactory.TestResultsWindow testResultsWindow) {
        runnerServiceImpl.registerResultsWindow(testResultsWindow);
    }

    public boolean isShouldSaveConfig() {
        return runnerServiceImpl.isShouldSaveConfig();
    }

    public void setShouldSaveConfig(boolean shouldSaveConfig) {
        runnerServiceImpl.setShouldSaveConfig(shouldSaveConfig);
    }
}
