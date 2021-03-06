/*
 *  Copyright (C) 2016 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.chart.commands;

import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.chart.data.IChartData;
import megan.chart.gui.ChartViewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class SortByAssignedUpCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return null;
    }

    public void apply(NexusStreamParser np) throws Exception {
    }

    public void actionPerformed(ActionEvent event) {
        executeImmediately("set sort=up;");
    }

    public boolean isApplicable() {
        ChartViewer viewer = (ChartViewer) getViewer();
        return viewer != null && (viewer.getActiveLabelsJList() == viewer.getSeriesList() || viewer.getChartData() instanceof IChartData);
    }

    public String getName() {
        return "Sort By Values (Up)";
    }

    public KeyStroke getAcceleratorKey() {
        return KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }

    public String getDescription() {
        return "Sort the list of entries by increasing number of assigned reads";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("SortNrHits16.gif");
    }

    public boolean isCritical() {
        return true;
    }
}

