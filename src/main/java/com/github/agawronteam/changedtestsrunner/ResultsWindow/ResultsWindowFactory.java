package com.github.agawronteam.changedtestsrunner.ResultsWindow;


import com.github.agawronteam.changedtestsrunner.Services.RunnerService;
import com.intellij.diagnostic.hprof.util.TreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ResultsWindowFactory implements ToolWindowFactory {

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

    public class TestResult {
        public TestResult(String testClass, String module) {
            this.testClass = testClass;
            this.module = module;
        }
        public String module;
        public String testClass;
        public int exitCode;
        public DefaultMutableTreeNode node;
    }


    public class TestResultsWindow {

        private RunnerService service;
        private Project project;
        JBPanel<JBPanel<?>> panel;
        HashMap<String, HashMap<String, Integer>> testResults = new HashMap<>();
        LinkedList<TestResult> treeNodes = new LinkedList<>();

        public TestResultsWindow(Project project) {
            this.project = project;
        }

        public JBPanel<JBPanel<?>> getContent() {
            panel = new JBPanel<>();
            panel.setLayout(new VerticalFlowLayout(true, false));
            preparePanel(panel);
            return panel;
        }

        private void preparePanel(JPanel panel) {
            service = project.getService(RunnerService.class);

            JButton runChangedTestsButton = new JButton("Run changed tests");

            var thisIsIt = this;
            runChangedTestsButton.addActionListener(e -> {
                var backgroundTask = new Task.Backgroundable(project, "Running recently changed tests...") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
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

            var root = new DefaultMutableTreeNode("Test results");
            treeNodes.clear();
            for (var module : testResults.keySet()) {
                var moduleNode = new DefaultMutableTreeNode(module);

                var result = new TestResult("empty", module);
                result.node = moduleNode;
                treeNodes.add(result);

                root.add(moduleNode);
                for (var testResult : testResults.get(module).entrySet()) {
                    var testClass = testResult.getKey();
                    var exitCode = testResult.getValue();
                    DefaultMutableTreeNode leaf;
                    if (exitCode == -1) {
                        leaf = new DefaultMutableTreeNode("Running " + testClass);
                    } else if (exitCode != 0) {
                        leaf = new DefaultMutableTreeNode("Failed " + testClass);
                    } else {
                        leaf = new DefaultMutableTreeNode("OK " + testClass);
                    }

                    moduleNode.add(leaf);
                }
            }
            var treeResults = new Tree(root);
            for (var node : treeNodes) {
                treeResults.expandPath(new TreePath(node.node.getPath()));
            }
            panel.add(treeResults);
        }

        public void testStarted(String module, String testClass) {
            //treeNodes.add(new TestResult(testClass, module));
            if (!testResults.containsKey(module)) {
                testResults.put(module, new HashMap<String, Integer>());
            }
            testResults.get(module).put(testClass, Integer.valueOf(-1));
            panel.removeAll();
            preparePanel(panel);
            panel.validate();
            panel.repaint();
        }

        public void testFinished(String module, String testClass, int exitCode) {
            if (!testResults.containsKey(module)) {
                System.out.println("Module " + module + " not found in test results");
                return;
            }
            testResults.get(module).put(testClass, Integer.valueOf(exitCode));
            panel.removeAll();
            preparePanel(panel);
            panel.validate();
            panel.repaint();
        }
    }
}
