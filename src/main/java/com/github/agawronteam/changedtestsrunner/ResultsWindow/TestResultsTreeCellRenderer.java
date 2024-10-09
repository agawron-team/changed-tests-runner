package com.github.agawronteam.changedtestsrunner.ResultsWindow;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class TestResultsTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        JComponent c = (JComponent) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (leaf && row != 0) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            var data = (String) node.getUserObject();
            if(data.contains(ResultsWindowFactory.TestStatus.FAILED.getText())){
                c.setForeground(Color.red);
                c.setOpaque(true);
            } else if (data.contains(ResultsWindowFactory.TestStatus.OK.getText())){
                c.setForeground(Color.green);
                c.setOpaque(true);
            }
        }
        return c;
    }
}
