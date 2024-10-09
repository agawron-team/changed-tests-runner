package com.github.agawronteam.changedtestsrunner.Actions;

import com.github.agawronteam.changedtestsrunner.MyLogger;
import com.github.agawronteam.changedtestsrunner.Services.RunnerService;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RunChangedTestsAction extends AnAction {

    private Logger log = MyLogger.getLogger();

    public RunChangedTestsAction() {
        super("Run changed tests");
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Using the event, evaluate the context,
        // and enable or disable the action.
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        var service = project.getService(RunnerService.class);

        var backgroundTask = new Task.Backgroundable(project, "Running recently changed tests...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ApplicationManager.getApplication().runReadAction(() -> service.runRecentlyChangedTests(project, null));
            }
        };
        backgroundTask.queue();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private void updateBeforeTasks() {
                /*var lastConfiguration = (RunnerAndConfigurationSettings) runConfigurations.pop();
        var junitConfiguration = (JUnitConfiguration) lastConfiguration.getConfiguration();
        var beforeRunTasks = junitConfiguration.getBeforeRunTasks();
        MakeProjectStepBeforeRun.MakeProjectBeforeRunTask buildProjectTask =
                new MakeProjectStepBeforeRun.MakeProjectBeforeRunTask();
        beforeRunTasks.add(buildProjectTask);
        // chain configurations to work around the IntelliJ limitation of showing just few configurations
        for (var runConfig : runConfigurations) {
            var runOtherConfigTaskProvider = new RunConfigurationBeforeRunProvider(project);
            var beforeTask = runOtherConfigTaskProvider.createTask(runConfig.getConfiguration());
            beforeTask.getSettings().getConfiguration().setBeforeRunTasks(runConfig.getConfiguration().getBeforeRunTasks());

            beforeTask.setEnabled(true);
            beforeRunTasks.add(beforeTask);
            runManager.addConfiguration(runConfig, false);
        }

        runManager.addConfiguration(lastConfiguration, false);*/
        //junitConfiguration.setBeforeRunTasks(beforeRunTasks);
    }

}