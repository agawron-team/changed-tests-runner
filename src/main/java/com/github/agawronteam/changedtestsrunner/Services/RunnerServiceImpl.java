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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class RunnerServiceImpl {

    private boolean shouldSaveConfig = false;
    private boolean detectAffectedTests = false;
    private ResultsWindowFactory.TestResultsWindow testResultsWindow;
    private HashMap<UUID, Boolean> testJobsActive = new HashMap<>();
    private HashMap<UUID, ProcessHandler> activeProcessHandlers = new HashMap<>();
    private HashMap<Project, Boolean> subscribedProjects = new HashMap<>();
    private boolean isPreparingExecution = false;
    private volatile boolean isStopping = false;
    private Project currentProject = null;

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

    public void stopTests() {
        // Signal that we want to stop — queued jobs that haven't started yet will
        // be aborted in the processStartScheduled / processStarted listeners.
        // isStopping must remain true until all jobs have finished terminating;
        // it is cleared by checkAndResetStoppingState() once no active jobs remain.
        isStopping = true;
        isPreparingExecution = false;

        // Immediately grey out the stop button in the results window
        if (testResultsWindow != null) {
            testResultsWindow.onStopRequested();
        }

        // Destroy any process handlers we are directly tracking.
        for (ProcessHandler handler : activeProcessHandlers.values()) {
            if (!handler.isProcessTerminated()) {
                handler.destroyProcess();
            }
        }

        // Cancel all queued and running executions tracked by the IDE's ExecutionManager.
        // getRunningDescriptors() takes a Condition<RunnerAndConfigurationSettings> and returns
        // a list of RunContentDescriptor — each descriptor holds the ProcessHandler to destroy.
        if (currentProject != null) {
            ExecutionManager executionManager = ExecutionManager.getInstance(currentProject);
            executionManager.getRunningDescriptors((RunnerAndConfigurationSettings settings) -> {
                UUID testId = getUUID(settings.getUniqueID());
                return testJobsActive.containsKey(testId);
            }).forEach(descriptor -> {
                ProcessHandler handler = descriptor.getProcessHandler();
                if (handler != null && !handler.isProcessTerminated()) {
                    handler.destroyProcess();
                }
            });
        }

        // If there are no active jobs left at all (e.g. stop was clicked before anything started),
        // reset immediately.
        checkAndResetStoppingState();
    }

    /**
     * Resets the stopping state once all tracked jobs are no longer active.
     * Called after each job completes/cancels while isStopping is true.
     */
    private void checkAndResetStoppingState() {
        if (isStopping && !isPreparingExecution && testJobsActive.values().stream().noneMatch(Boolean::booleanValue)) {
            activeProcessHandlers.clear();
            testJobsActive.clear();
            isStopping = false;
            if (testResultsWindow != null) {
                testResultsWindow.onTestsFinished();
            }
        }
    }

    public void runRecentlyChangedTests(Project project) {
        if (isRunningTests()) {
            return;
        }
        isPreparingExecution = true;
        isStopping = false;
        currentProject = project;
        testJobsActive.clear();
        activeProcessHandlers.clear();
        var changedFiles = getUncommittedChanges(project);
        var changedSourceFiles = changedFiles.stream().filter(file -> {
            String fileTypeName = file.getFileType().getName().toLowerCase();
            return fileTypeName.equals("java") || fileTypeName.equals("kotlin");
        }).toList();

        var runManager = getRunManagerInstance(project);

        // Use a LinkedHashMap keyed by UUID to deduplicate configs from both paths
        var testJobConfigMap = new LinkedHashMap<UUID, TestJobConfig>();

        // 1. Direct changed test files
        for (var config : getRunConfigurationsForChangedFiles(project, changedSourceFiles, runManager)) {
            testJobConfigMap.put(config.id, config);
        }

        // 2. If "detect affected tests" is on, also find tests that reference changed production classes
        if (detectAffectedTests) {
            for (var config : getAffectedTestConfigurations(project, changedSourceFiles, runManager)) {
                testJobConfigMap.putIfAbsent(config.id, config);
            }
        }

        if (testJobConfigMap.isEmpty()) {
            isPreparingExecution = false;
            testResultsWindow.reset("No tests to run");
            return;
        }
        // Clear the results window only if there are tests to run
        testResultsWindow.reset("Tests results");

        executeConfigurations(project, new LinkedList<>(testJobConfigMap.values()), runManager);

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

    public boolean isDetectAffectedTests() {
        return detectAffectedTests;
    }

    public void setDetectAffectedTests(boolean detectAffectedTests) {
        this.detectAffectedTests = detectAffectedTests;
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
                if (isStopping) {
                    testJobsActive.put(testId, false);
                    testResultsWindow.updateTest(testId, ResultsWindowFactory.TestStatus.CANCELLED);
                    checkAndResetStoppingState();
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
                if (isStopping) {
                    handler.destroyProcess();
                    testJobsActive.put(testId, false);
                    testResultsWindow.updateTest(testId, ResultsWindowFactory.TestStatus.CANCELLED);
                    checkAndResetStoppingState();
                    return;
                }
                testJobsActive.put(testId, true);
                activeProcessHandlers.put(testId, handler);
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
                activeProcessHandlers.remove(testId);
                testResultsWindow.updateTest(testId,
                        ResultsWindowFactory.TestStatus.FAILED);
                checkAndResetStoppingState();
                // If all jobs are done notify the window
                if (!isRunningTests() && testResultsWindow != null) {
                    testResultsWindow.onTestsFinished();
                }
            }

            @Override
            public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
                System.out.println("Process finished: " + env.getRunnerAndConfigurationSettings().getConfiguration().getName());
                var testId = getUUID(env.getRunnerAndConfigurationSettings().getUniqueID());
                if (!testJobsActive.containsKey(testId)) {
                    return;
                }
                testJobsActive.put(testId, false);
                activeProcessHandlers.remove(testId);
                if (isStopping) {
                    testResultsWindow.updateTest(testId, ResultsWindowFactory.TestStatus.CANCELLED);
                    checkAndResetStoppingState();
                } else {
                    testResultsWindow.updateTest(testId,
                            exitCode == 0 ? ResultsWindowFactory.TestStatus.OK : ResultsWindowFactory.TestStatus.FAILED);
                    // If all jobs are done and we're not stopping, notify the window
                    if (!isRunningTests() && testResultsWindow != null) {
                        testResultsWindow.onTestsFinished();
                    }
                }
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

    /**
     * For each changed source file that is NOT itself a test class, finds all test classes
     * that directly reference (import or use) any of the changed classes.
     */
    private List<TestJobConfig> getAffectedTestConfigurations(Project project, List<VirtualFile> changedSourceFiles, RunManager runManager) {
        var testJobConfigs = new LinkedList<TestJobConfig>();
        var scope = GlobalSearchScope.projectScope(project);

        for (var virtualFile : changedSourceFiles) {
            PsiFile psiFile = getPsiManagerInstance(project).findFile(virtualFile);
            if (!(psiFile instanceof PsiJavaFile psiJavaFile)) {
                continue;
            }

            for (PsiClass changedClass : psiJavaFile.getClasses()) {
                // Skip if the changed class is itself a test — already covered by the direct path
                if (isJUnitClass(changedClass)) {
                    continue;
                }

                // Search for all references to this class in the project scope
                ReferencesSearch.search(changedClass, scope).forEach(reference -> {
                    PsiElement element = reference.getElement();
                    PsiFile referencingFile = element.getContainingFile();
                    if (!(referencingFile instanceof PsiJavaFile referencingJavaFile)) {
                        return true; // continue
                    }

                    for (PsiClass referencingClass : referencingJavaFile.getClasses()) {
                        if (isJUnitClass(referencingClass)) {
                            var config = getTestJobConfigs(
                                    referencingClass, runManager,
                                    getJUnitConfigurationTypeInstance(),
                                    referencingFile.getVirtualFile()
                            );
                            testJobConfigs.add(config);
                        }
                    }
                    return true; // continue iteration
                });
            }
        }
        return testJobConfigs;
    }

    static UUID getUUID(String name) {
        return UUID.nameUUIDFromBytes((name).getBytes());
    }

    private static final List<String> TEST_ANNOTATIONS = List.of(
            "org.junit.Test",                    // JUnit 4
            "org.junit.jupiter.api.Test",        // JUnit 5
            "org.junit.jupiter.params.ParameterizedTest", // JUnit 5 parameterized
            "org.junit.jupiter.api.RepeatedTest" // JUnit 5 repeated
    );

    private static final List<String> TEST_CLASS_ANNOTATIONS = List.of(
            "org.junit.runner.RunWith",          // JUnit 4 runner (e.g. suites)
            "org.junit.jupiter.api.extension.ExtendWith" // JUnit 5 extension
    );

    private boolean isJUnitClass(PsiClass psiClass) {
        // Check for test class-level annotations (e.g. @RunWith, @ExtendWith)
        if (TEST_CLASS_ANNOTATIONS.stream().anyMatch(psiClass::hasAnnotation)) {
            return true;
        }
        // Check for test method annotations (JUnit 4 @Test, JUnit 5 @Test, @ParameterizedTest, etc.)
        return Arrays.stream(psiClass.getAllMethods())
                .anyMatch(method -> TEST_ANNOTATIONS.stream().anyMatch(method::hasAnnotation));
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
        var modules = junitConfig.getModules();
        var moduleName = (modules != null && modules.length > 0) ? modules[0].getName() : "";
        return new TestJobConfig(getUUID(runnerAndConfigurationSettings.getUniqueID()), moduleName, junitConfig.getActionName(), virtualFile, runnerAndConfigurationSettings);
    }
}
