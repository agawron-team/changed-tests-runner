package com.github.agawronteam.changedtestsrunner.ResultsWindow;


import com.github.agawronteam.changedtestsrunner.Services.RunnerService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.HashMap;

public class ResultsWindowFactory implements ToolWindowFactory {

    public enum TestStatus {
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

    public ResultsWindowFactory() {
        //thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
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
        HashMap<String, HashMap<String, DefaultMutableTreeNode>> modulesWithTests = new HashMap<>();
        HashMap<String, DefaultMutableTreeNode> moduleNodes = new HashMap<>();

        public TestResultsWindow(Project project) {
            this.project = project;
        }

        boolean checkIfAllTestsFinished() {
            for (var module : modulesWithTests.values()) {
                for (var test : module.values()) {
                    if (test.getUserObject().toString().startsWith(TestStatus.QUEUED.getText())
                            || test.getUserObject().toString().startsWith(TestStatus.RUNNING.getText())) {
                        return false;
                    }
                }
            }
            return true;
        }

        public JBPanel<JBPanel<?>> getContent() {
            service = project.getService(RunnerService.class);
            panel = new JBPanel<>();
            panel.setLayout(new VerticalFlowLayout(true, false));

            runChangedTestsButton = new JButton("Run changed tests");

            var thisIsIt = this;
            runChangedTestsButton.addActionListener(e -> {
                var backgroundTask = new Task.Backgroundable(project, "Running recently changed tests...") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        panel.remove(treeResults);
                        prepareTree(panel);
                        modulesWithTests.clear();
                        moduleNodes.clear();
                        runChangedTestsButton.setEnabled(false);
                        panel.validate();
                        panel.repaint();
                        ApplicationManager.getApplication().runReadAction(() -> service.runRecentlyChangedTests(project, thisIsIt));
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
            return panel;
        }

        private void prepareTree(JPanel panel) {
            root = new DefaultMutableTreeNode("Test results");
            treeResults = new Tree(root);
            panel.add(treeResults);
        }

        public void addTest(String module, String testClass) {
            if (!moduleNodes.containsKey(module)) {
                var moduleNode = new DefaultMutableTreeNode(module);
                moduleNodes.put(module, moduleNode);
                root.add(moduleNode);
                modulesWithTests.put(module, new HashMap<>());
            }
            if (!modulesWithTests.get(module).containsKey(testClass)) {
                var testNode = new DefaultMutableTreeNode("Queued " + testClass);
                modulesWithTests.get(module).put(testClass, testNode);
                moduleNodes.get(module).add(testNode);
            }

            panel.validate();
            panel.repaint();
        }

        public void expandAll() {
            for (var module : moduleNodes.values()) {
                treeResults.expandPath(new TreePath(module.getPath()));
            }
        }

        public void updateTest(String module, String testClass, TestStatus testStatus) {
            if (!moduleNodes.containsKey(module)) {
                System.out.println("Module " + module + " not found");
                return;
            }
            if (!modulesWithTests.get(module).containsKey(testClass)) {
                System.out.println("Test " + testClass + " not found");
                return;
            }
            modulesWithTests.get(module).get(testClass).setUserObject(testStatus.getText() + " " + testClass);
            if (checkIfAllTestsFinished()) {
                runChangedTestsButton.setEnabled(true);
            }
            panel.validate();
            panel.repaint();
        }
    }
}
