package com.github.agawronteam.changedtestsrunner.ResultsWindow;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class TestResultsTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        JComponent c = (JComponent) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node.getUserObject() instanceof ResultsWindowFactory.TestJob) {
            var data = (ResultsWindowFactory.TestJob) node.getUserObject();
            var newComponent = new JLabel(data.status.getText() + " " + data.testClass);
            if(data.status.equals(ResultsWindowFactory.TestStatus.FAILED)){
                newComponent.setForeground(Color.red);
                newComponent.setOpaque(true);
            } else if (data.status.equals(ResultsWindowFactory.TestStatus.OK)){
                newComponent.setForeground(Color.green);
                newComponent.setOpaque(true);
            }
            return newComponent;
        }
        return c;
    }
}
