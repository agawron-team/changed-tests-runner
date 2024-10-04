package com.github.agawronteam.changedtestsrunner.Actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.UUID;

public class RunChangedTestsBashAction extends AnAction {

    private String copyToTempDirectory(String resourceName) throws IOException {
        Path tempFile = Files.createTempFile("script_" + UUID.randomUUID().toString(), ".sh");
        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            Files.copy(resourceStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile.toString();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Using the event, evaluate the context,
        // and enable or disable the action.
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        // Using the event, implement an action.
        // For example, create and show a dialog.
        // run all unit tests for a currently opened file
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            return;
        }
        String[] env = {"PATH=/bin:/usr/bin/"};
        String cmd = null;
        try {
            cmd = copyToTempDirectory("run_tests.sh");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var workingDir = file.getParent().getPath();
        try {
            Process p = Runtime.getRuntime().exec("sh " + cmd + " --verbose", env, new File(workingDir));


            // Output stream is the input to the subprocess
            var outputStream = p.getOutputStream();
            if (outputStream != null) {
                outputStream.close();
            }

// Input stream is the normal output of the subprocess
            InputStream inputStream = p.getInputStream();
            if (inputStream != null) {
                // You can use the input stream to log your output here.
                printOutput(inputStream);
            }

// Error stream is the error output of the subprocess
            InputStream errorStream = p.getErrorStream();
            if (errorStream != null) {
                // You can use the input stream to log your error output here.
                printOutput(errorStream);
                errorStream.close();
            }
            p.waitFor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Running all unit tests for the currently opened file");



// Remove the temp file from disk
        new File(cmd).delete();

        Task.Backgroundable backgroundTask = new Task.Backgroundable(project, "Running tests for recent changes", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                getChangedFiles(project);
            }
        };
        ProgressManager.getInstance().run(backgroundTask);
    }

    private GitLineHandler createGitStatusCommand(Project project) {
        var handler = new GitLineHandler(project, new File(project.getBasePath()), GitCommand.STATUS);
        handler.setStderrSuppressed(true);
        handler.addParameters("--porcelain");
        handler.endOptions();
        return handler;
    }

    private void getChangedFiles(Project project) {
        var command = createGitStatusCommand(project);
        System.out.println("Running command: " + command);
        var result = Git.getInstance().runCommand(command);

        if (result.success()) {

            System.out.println("Result: " + result.getOutputAsJoinedString());
        } else {
            System.err.println("Error running command " + command + ": " + result.getErrorOutputAsJoinedString());
        }
    }

    private static void printOutput(InputStream inputStream) throws IOException {
        Scanner s = new Scanner(inputStream);
        StringBuilder text = new StringBuilder();
        while (s.hasNextLine()) {
            var line = s.nextLine();
            text.append(line);
            text.append("\n");
            System.out.println(line);
        }
        s.close();
        inputStream.close();
    }

    // Override getActionUpdateThread() when you target 2022.3 or later!

}