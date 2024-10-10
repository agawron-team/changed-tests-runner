package com.github.agawronteam.changedtestsrunner.Services;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.github.agawronteam.changedtestsrunner.TestJobConfig;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@State(name = "RunnerService")
@Service(Service.Level.PROJECT)
public final class RunnerService implements PersistentStateComponent<RunnerService> {

    public boolean shouldSaveConfig = false;
    private ResultsWindowFactory.TestResultsWindow testResultsWindow;
    HashMap<UUID, Boolean> testJobsActive = new HashMap<>();
    HashMap<Project, Boolean> subscribedProjects = new HashMap<>();
    boolean isPreparingExecution = false;

    @Override
    public @Nullable RunnerService getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RunnerService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public boolean isRunningTests() {
        return testJobsActive.values().stream().anyMatch(Boolean::booleanValue) || isPreparingExecution;
    }

    public void runRecentlyChangedTests(Project project) {
        if (isRunningTests()) {
            return;
        }
        isPreparingExecution = true;
        testJobsActive.clear();
        var changedFiles = getUncommittedChanges(project);
        var changedTestFiles = changedFiles.stream().filter(file ->
                file.getFileType().getName().toLowerCase().equals("java")).toList();

        var runManager = RunManagerEx.getInstanceEx(project);

        var testJobConfigs = getRunConfigurationsFromChangedFiles(project, changedTestFiles, runManager);

        if (testJobConfigs.isEmpty()) {
            isPreparingExecution = false;
            testResultsWindow.reset("No tests to run");
            return;
        }
        // Clear the results window only if there are tests to run
        testResultsWindow.reset("Tests results");

        executeConfigurations(project, testJobConfigs, runManager);

        subscribeToExecutionEvents(project);
        testResultsWindow.expandAll();
        isPreparingExecution = false;
    }

    private void subscribeToExecutionEvents(Project project) {
        if (subscribedProjects.containsKey(project)) {
            return;
        }
        subscribedProjects.put(project, true);
        project.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStartScheduled(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
                System.out.println("Process start scheduled: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                testJobsActive.put(testId, true);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.QUEUED);
            }

            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                System.out.println("Process started: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                testJobsActive.put(testId, true);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.RUNNING);
            }

            @Override
            public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
                System.out.println("Process not started: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                testJobsActive.put(testId, false);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.FAILED);
            }

            @Override
            public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
                System.out.println("Process finished: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                testJobsActive.put(testId, false);
                testResultsWindow.updateTest(testId,
                        exitCode == 0 ? ResultsWindowFactory.TestStatus.OK : ResultsWindowFactory.TestStatus.FAILED);
            }
        });
    }

    private void executeConfigurations(Project project, List<TestJobConfig> testJobConfigs, RunManagerEx runManager) {
        for (var testJobConfig : testJobConfigs) {
            var runConfig = testJobConfig.runConfig;
            ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder
                    .createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runConfig);

            var testId = getUUID(runConfig.getUniqueID());
            testJobsActive.put(testId, true);
            testResultsWindow.addTest(testId, testJobConfig);

            if (builder != null) {
                if (shouldSaveConfig) {
                    runManager.addConfiguration(runConfig, false);
                }

                ExecutionManager.getInstance(project).restartRunProfile(builder.build());
            }
        }
    }

    private List<TestJobConfig> getRunConfigurationsFromChangedFiles(Project project, List<VirtualFile> changedTestFiles, RunManagerEx runManager) {
        var testJobConfigs = new LinkedList<TestJobConfig>();
        for (var virtualFile : changedTestFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile == null) {
                continue;
            }
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            PsiClass[] javaFileClasses = psiJavaFile.getClasses();

            for (PsiClass javaFileClass : javaFileClasses) {
                if (isJUnitClass(javaFileClass)) {
                    var testJobConfig = getTestJobConfigs(javaFileClass, runManager, JUnitConfigurationType.getInstance(), virtualFile);
                    testJobConfigs.add(testJobConfig);
                }
            }
        }
        return testJobConfigs;
    }

    static UUID getUUID(String name) {
        return UUID.nameUUIDFromBytes((name).getBytes());
    }

    private boolean isJUnitClass(PsiClass psiClass) {
        // TODO: find a better way of finding out if a class is a JUnit test
        return Arrays.stream(psiClass.getAllMethods()).anyMatch(method -> method.getModifierList().toString().contains("@Test"));
    }

    private @NotNull List<VirtualFile> getUncommittedChanges(Project project) {
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        return changeListManager.getAffectedFiles();
    }

    private static @NotNull TestJobConfig getTestJobConfigs(PsiClass javaFileClass, RunManager runManager, ConfigurationType configType, VirtualFile virtualFile) {
        var runnerAndConfigurationSettings = runManager.createConfiguration(javaFileClass.getName(), configType.getConfigurationFactories()[0]);
        var junitConfig = (JUnitConfiguration) runnerAndConfigurationSettings.getConfiguration();
        junitConfig.setMainClass(javaFileClass);
        return new TestJobConfig(getUUID(runnerAndConfigurationSettings.getUniqueID()), junitConfig.getModules()[0].getName(), junitConfig.getActionName(), virtualFile, runnerAndConfigurationSettings);
    }

    public void triggerSaveConfig(ActionEvent e) {
        shouldSaveConfig = ((JCheckBox) e.getSource()).isSelected();
    }

    public void registerResultsWindow(ResultsWindowFactory.TestResultsWindow testResultsWindow) {
        this.testResultsWindow = testResultsWindow;
    }
}
