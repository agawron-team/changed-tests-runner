package com.github.agawronteam.changedtestsrunner.Actions;

import com.github.agawronteam.changedtestsrunner.MyLogger;
import com.github.agawronteam.changedtestsrunner.Services.RunnerService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class RunChangedTestsAction extends AnAction {

    private Logger log = MyLogger.getLogger();

    public RunChangedTestsAction() {
        super("Run changed tests");
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Using the event, evaluate the context,
        // and enable or disable the action.
        Project project = event.getProject();
        var service = project.getService(RunnerService.class);
        if (service.isRunningTests()) {
            event.getPresentation().setEnabled(false);
        } else {
            event.getPresentation().setEnabled(true);
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        var service = project.getService(RunnerService.class);
        ToolWindowManager.getInstance(project).getToolWindow("Test Results").show(() -> {
        });

        var backgroundTask = new Task.Backgroundable(project, "Running recently changed tests...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ApplicationManager.getApplication().runReadAction(() -> service.runRecentlyChangedTests(project));
            }
        };
        backgroundTask.queue();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}