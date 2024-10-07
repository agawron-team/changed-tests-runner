package com.github.agawronteam.changedtestsrunner.Actions;

import com.github.agawronteam.changedtestsrunner.MyLogger;
import com.intellij.compiler.options.MakeProjectStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.task.ProjectTaskManager;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class RunChangedTestsAction extends AnAction {

    private Logger log = MyLogger.getLogger();

    //private final BackgroundTaskQueue myQueue;
    //private final Runnable backgroundExecutor;

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Using the event, evaluate the context,
        // and enable or disable the action.
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        //IdeDocumentHistory changedFilesHistory = IdeDocumentHistory.getInstance(project);

        //var changedFiles = changedFilesHistory.getChangedFiles();
        var changedFiles = getUncommittedChanges(event.getProject());
        var changedTestFiles = changedFiles.stream().filter(file ->
                file.getFileType().getName().toLowerCase().equals("java")).toList();

        var runConfigurations = new LinkedList<RunnerAndConfigurationSettings>();
        var runManager = RunManagerEx.getInstanceEx(project);
        var configs = runManager.getAllSettings();

        for (var virtualFile : changedTestFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile == null) {
                continue;
            }
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            PsiClass[] javaFileClasses = psiJavaFile.getClasses();

            for (PsiClass javaFileClass : javaFileClasses) {
                if (isJUnitClass(javaFileClass)) {
                    var configType = ConfigurationTypeUtil.findConfigurationType("JUnit");
                    var runConfig = getRunnerAndConfigurationSettings(javaFileClass, runManager, configType);
                    runConfigurations.add(runConfig);
                }
            }
        }
        if (runConfigurations.isEmpty()) {
            return;
        }

        /*var lastConfiguration = (RunnerAndConfigurationSettings) runConfigurations.pop();
        var beforeRunTasks = lastConfiguration.getConfiguration().getBeforeRunTasks();
        // RunProfileWithCompileBeforeLaunchOption
        // chain configurations to work around the IntelliJ limitation of showing just few configurations
        for (var runConfig : runConfigurations) {
            var buildProjectTask = new RunConfigurationBeforeRunProvider(project);
            var beforeTask = buildProjectTask.createTask(runConfig.getConfiguration());

            beforeTask.setEnabled(true);
            beforeRunTasks.add(beforeTask);
        }
        lastConfiguration.getConfiguration().setBeforeRunTasks(beforeRunTasks);*/

        for (var runConfig : runConfigurations) {
            ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder
                    .createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runConfig);

            if (builder != null) {
                //runManager.addConfiguration(lastConfiguration, false);
                ExecutionManager.getInstance(project).restartRunProfile(builder.build());
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

    private static void runTestsForClass(PsiClass javaFileClass, RunManager runManager, ConfigurationType configType, Project project) {
        var runnerAndConfigurationSettings = getRunnerAndConfigurationSettings(javaFileClass, runManager, configType);
        ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder
                .createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runnerAndConfigurationSettings);

        if (builder != null) {
            ExecutionManager.getInstance(project).restartRunProfile(builder.build());
        }
    }

    private static @NotNull RunnerAndConfigurationSettings getRunnerAndConfigurationSettings(PsiClass javaFileClass, RunManager runManager, ConfigurationType configType) {
        var runnerAndConfigurationSettings = runManager.createConfiguration(javaFileClass.getName(), configType.getConfigurationFactories()[0]);
        var junitConfig = (JUnitConfiguration) runnerAndConfigurationSettings.getConfiguration();
        junitConfig.setMainClass(javaFileClass);
        //junitConfig.setBeforeRunTasks(new ArrayList<>());
        return runnerAndConfigurationSettings;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}