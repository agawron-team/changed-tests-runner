package com.github.agawronteam.changedtestsrunner.Services;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@State(name = "RunnerService")
@Service(Service.Level.PROJECT)
public final class RunnerService implements PersistentStateComponent<RunnerService> {

    public boolean shouldSaveConfig = false;

    @Override
    public @Nullable RunnerService getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RunnerService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void runRecentlyChangedTests(Project project, ResultsWindowFactory.TestResultsWindow resultsWindow) {
        var changedFiles = getUncommittedChanges(project);
        var changedTestFiles = changedFiles.stream().filter(file ->
                file.getFileType().getName().toLowerCase().equals("java")).toList();

        var runConfigurations = new LinkedList<RunnerAndConfigurationSettings>();
        var runManager = RunManagerEx.getInstanceEx(project);

        for (var virtualFile : changedTestFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile == null) {
                continue;
            }
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            PsiClass[] javaFileClasses = psiJavaFile.getClasses();

            for (PsiClass javaFileClass : javaFileClasses) {
                if (isJUnitClass(javaFileClass)) {
                    var runConfig = getRunnerAndConfigurationSettings(javaFileClass, runManager, JUnitConfigurationType.getInstance());
                    runConfigurations.add(runConfig);
                }
            }
        }

        for (var runConfig : runConfigurations) {
            ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder
                    .createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runConfig);

            if (builder != null) {
                if (shouldSaveConfig) {
                    runManager.addConfiguration(runConfig, false);
                }

                ExecutionManager.getInstance(project).restartRunProfile(builder.build());

                project.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
                    @Override
                    public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                        var junitConfig = (JUnitConfiguration) env.getRunnerAndConfigurationSettings().getConfiguration();
                        System.out.println("Process started " + executorId);
                        if (resultsWindow != null) {
                            resultsWindow.testStarted(junitConfig.getModules()[0].getName(), junitConfig.getActionName());
                        }
                        //check if started process is needed one
                    }

                    @Override
                    public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
                        System.out.println("Process " + executorId + " terminated with code " + exitCode);
                        var junitConfig = (JUnitConfiguration) env.getRunnerAndConfigurationSettings().getConfiguration();
                        //check if terminated process is needed one
                        resultsWindow.testFinished(junitConfig.getModules()[0].getName(), junitConfig.getActionName(), exitCode);
                    }
                });
            }
        }
    }

    private boolean isJUnitClass(PsiClass psiClass) {
        // TODO: find a better way of finding out if a class is a JUnit test
        return Arrays.stream(psiClass.getAllMethods()).anyMatch(method -> method.getModifierList().toString().contains("@Test"));
    }

    private @NotNull List<VirtualFile> getUncommittedChanges(Project project) {
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        return changeListManager.getAffectedFiles();
    }

    private static @NotNull RunnerAndConfigurationSettings getRunnerAndConfigurationSettings(PsiClass javaFileClass, RunManager runManager, ConfigurationType configType) {
        var runnerAndConfigurationSettings = runManager.createConfiguration(javaFileClass.getName(), configType.getConfigurationFactories()[0]);
        var junitConfig = (JUnitConfiguration) runnerAndConfigurationSettings.getConfiguration();
        junitConfig.setMainClass(javaFileClass);
        return runnerAndConfigurationSettings;
    }

    public void triggerSaveConfig(ActionEvent e) {
        shouldSaveConfig = ((JCheckBox) e.getSource()).isSelected();
    }
}
