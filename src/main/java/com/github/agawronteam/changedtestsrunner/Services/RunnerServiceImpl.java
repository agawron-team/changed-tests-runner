package com.github.agawronteam.changedtestsrunner.Services;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.github.agawronteam.changedtestsrunner.TestJobConfig;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class RunnerServiceImpl {

    private boolean shouldSaveConfig = false;
    private ResultsWindowFactory.TestResultsWindow testResultsWindow;
    private HashMap<UUID, Boolean> testJobsActive = new HashMap<>();
    private HashMap<Project, Boolean> subscribedProjects = new HashMap<>();
    private boolean isPreparingExecution = false;

    protected RunManager getRunManagerInstance(Project project) {
        return RunManager.getInstance(project);
    }

    protected ChangeListManager getChangeListManagerInstance(Project project) {
        return ChangeListManager.getInstance(project);
    }

    public boolean isRunningTests() {
        return testJobsActive.values().stream().anyMatch(Boolean::booleanValue) || isPreparingExecution;
    }

    public PsiManager getPsiManagerInstance(Project project) {
        return PsiManager.getInstance(project);
    }

    public JUnitConfigurationType getJUnitConfigurationTypeInstance() {
        return JUnitConfigurationType.getInstance();
    }

    public ExecutionEnvironmentBuilder getExecutionEnvironmentBuilder(RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
        return ExecutionEnvironmentBuilder
                .createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runnerAndConfigurationSettings);
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

        var runManager = getRunManagerInstance(project);

        var testJobConfigs = getRunConfigurationsForChangedFiles(project, changedTestFiles, runManager);

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

    public void registerResultsWindow(ResultsWindowFactory.TestResultsWindow testResultsWindow) {
        this.testResultsWindow = testResultsWindow;
    }

    public boolean isShouldSaveConfig() {
        return shouldSaveConfig;
    }

    public void setShouldSaveConfig(boolean shouldSaveConfig) {
        this.shouldSaveConfig = shouldSaveConfig;
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
                if (!testJobsActive.containsKey(testId)) {
                    return;
                }
                testJobsActive.put(testId, true);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.QUEUED);
            }

            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                System.out.println("Process started: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                if (!testJobsActive.containsKey(testId)) {
                    return;
                }
                testJobsActive.put(testId, true);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.RUNNING);
            }

            @Override
            public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
                System.out.println("Process not started: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                if (!testJobsActive.containsKey(testId)) {
                    return;
                }
                testJobsActive.put(testId, false);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.FAILED);
            }

            @Override
            public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
                System.out.println("Process finished: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                if (!testJobsActive.containsKey(testId)) {
                    return;
                }
                testJobsActive.put(testId, false);
                testResultsWindow.updateTest(testId,
                        exitCode == 0 ? ResultsWindowFactory.TestStatus.OK : ResultsWindowFactory.TestStatus.FAILED);
            }
        });
    }

    private void executeConfigurations(Project project, List<TestJobConfig> testJobConfigs, RunManager runManager) {
        for (var testJobConfig : testJobConfigs) {
            var runConfig = testJobConfig.runConfig;
            ExecutionEnvironmentBuilder builder = getExecutionEnvironmentBuilder(runConfig);

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

    private List<TestJobConfig> getRunConfigurationsForChangedFiles(Project project, List<VirtualFile> changedTestFiles, RunManager runManager) {
        var testJobConfigs = new LinkedList<TestJobConfig>();
        for (var virtualFile : changedTestFiles) {
            PsiFile psiFile = getPsiManagerInstance(project).findFile(virtualFile);
            if (psiFile == null) {
                continue;
            }
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            PsiClass[] javaFileClasses = psiJavaFile.getClasses();

            for (PsiClass javaFileClass : javaFileClasses) {
                if (isJUnitClass(javaFileClass)) {
                    var testJobConfig = getTestJobConfigs(javaFileClass, runManager, getJUnitConfigurationTypeInstance(), virtualFile);
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
        ChangeListManager changeListManager = getChangeListManagerInstance(project);
        return changeListManager.getAffectedFiles();
    }

    private static @NotNull TestJobConfig getTestJobConfigs(PsiClass javaFileClass, RunManager runManager, ConfigurationType configType, VirtualFile virtualFile) {
        var configFactory = configType.getConfigurationFactories()[0];
        var runnerAndConfigurationSettings = runManager.createConfiguration(javaFileClass.getName(), configFactory);
        var junitConfig = (JUnitConfiguration) runnerAndConfigurationSettings.getConfiguration();
        junitConfig.setMainClass(javaFileClass);
        return new TestJobConfig(getUUID(runnerAndConfigurationSettings.getUniqueID()), junitConfig.getModules()[0].getName(), junitConfig.getActionName(), virtualFile, runnerAndConfigurationSettings);
    }
}
