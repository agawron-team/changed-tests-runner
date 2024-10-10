package com.github.agawronteam.changedtestsrunner.ResultsWindow;


import com.github.agawronteam.changedtestsrunner.Services.RunnerService;
import com.github.agawronteam.changedtestsrunner.TestJobConfig;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.UUID;

public class ResultsWindowFactory implements ToolWindowFactory {

    public enum TestStatus {
        CHANGED("Changed"),
        QUEUED("Queued"),
        RUNNING("Running"),
        OK("OK"),
        FAILED("Failed");

        private String text;

        TestStatus(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
    }

    @Override
    public @Nullable Object isApplicableAsync(@NotNull Project project, @NotNull Continuation<? super Boolean> $completion) {
        // TODO: check if the project is a Java project and contains junit files
        return ToolWindowFactory.super.isApplicableAsync(project, $completion);
    }

    public ResultsWindowFactory() {
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        var myToolWindow = new TestResultsWindow(project);
        var content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), "Test Results", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return true;
    }

    public class TestResultsWindow {

        private RunnerService service;
        private Project project;
        private DefaultMutableTreeNode root;
        private Tree treeResults;
        private JButton runChangedTestsButton;
        JBPanel<JBPanel<?>> panel;
        HashMap<String, DefaultMutableTreeNode> moduleNodes = new HashMap<>();
        HashMap<UUID, DefaultMutableTreeNode> testNodes = new HashMap<>();
        HashMap<UUID, TestJobConfig> testJobs = new HashMap<>();

        public TestResultsWindow(Project project) {
            this.project = project;
        }

        public JScrollPane getContent() {
            service = project.getService(RunnerService.class);
            service.registerResultsWindow(this);
            panel = new JBPanel<>();
            panel.setLayout(new VerticalFlowLayout(true, false));
            JScrollPane scroller = new JScrollPane(panel);

            runChangedTestsButton = new JButton("Run changed tests");

            runChangedTestsButton.addActionListener(e -> {
                var backgroundTask = new Task.Backgroundable(project, "Running recently changed tests...") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        ApplicationManager.getApplication().runReadAction(() -> service.runRecentlyChangedTests(project));
                    }
                };
                backgroundTask.queue();
            });
            panel.add(runChangedTestsButton);

            var checkbox = new JCheckBox("Save run configurations");
            checkbox.setSelected(service.shouldSaveConfig);
            checkbox.addActionListener(e -> service.triggerSaveConfig(e));
            panel.add(checkbox);

            prepareTree(panel);
            return scroller;
        }

        private void prepareTree(JPanel panel) {
            root = new DefaultMutableTreeNode("Test results");
            treeResults = new Tree(root);
            treeResults.setCellRenderer(new TestResultsTreeCellRenderer());

            MouseListener ml = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    int selRow = treeResults.getRowForLocation(e.getX(), e.getY());
                    TreePath selPath = treeResults.getPathForLocation(e.getX(), e.getY());

                    if (selPath == null) {
                        return;
                    }
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) (selPath.getLastPathComponent());

                    if(selRow > 0 && selectedNode.isLeaf()) {
                        var selectedValue = (TestJobConfig) selectedNode.getUserObject();
                        if(e.getClickCount() == 2) {
                            System.out.println("Clicked row " + selRow + " value: " + selectedValue.testClass);
                            FileEditorManager.getInstance(project).openTextEditor(
                                    new OpenFileDescriptor(
                                            project,
                                            selectedValue.virtualFile
                                    ),
                                    true // request focus to editor
                            );
                        }
                    }
                }
            };
            treeResults.addMouseListener(ml);

            panel.add(treeResults);
        }

        public void addTest(UUID id, TestJobConfig testJobConfig) {
            if (!moduleNodes.containsKey(testJobConfig.module)) {
                var moduleNode = new DefaultMutableTreeNode(testJobConfig.module);
                moduleNodes.put(testJobConfig.module, moduleNode);
                root.add(moduleNode);
            }
            if (!testNodes.containsKey(id)) {
                var testNode = new DefaultMutableTreeNode(testJobConfig);
                testNodes.put(id, testNode);
                moduleNodes.get(testJobConfig.module).add(testNode);
                testJobs.put(id, testJobConfig);
            }
            panel.validate();
            panel.repaint();
        }

        public void expandAll() {
            for (var module : moduleNodes.values()) {
                treeResults.expandPath(new TreePath(module.getPath()));
            }
        }

        public void updateTest(UUID id, TestStatus testStatus) {
            disableIfRunning();
            if (!testJobs.containsKey(id)) {
                System.out.println("Test job " + id + " not found");
                return;
            }
            testJobs.get(id).status = testStatus;

            panel.validate();
            panel.repaint();
        }

        public void disableIfRunning() {
            if (!service.isRunningTests()) {
                runChangedTestsButton.setEnabled(true);
            } else {
                runChangedTestsButton.setEnabled(false);
            }
        }

        public void reset() {
            panel.remove(treeResults);
            prepareTree(panel);
            testNodes.clear();
            moduleNodes.clear();
            testJobs.clear();
            disableIfRunning();
            panel.validate();
            panel.repaint();
        }
    }
}
