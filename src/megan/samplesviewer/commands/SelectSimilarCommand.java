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
package megan.samplesviewer.commands;

import javafx.collections.ObservableList;
import javafx.scene.control.TablePosition;
import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.parse.NexusStreamParser;
import megan.samplesviewer.SamplesViewer;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * * selection command
 * * Daniel Huson, 11.2010
 */
public class SelectSimilarCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return null;
    }

    public void apply(NexusStreamParser np) throws Exception {
    }


    public void actionPerformed(ActionEvent event) {
        final SamplesViewer viewer = (SamplesViewer) getViewer();

        final ObservableList selectedCells = viewer.getSamplesTable().getSpreadsheetView().getSelectionModel().getSelectedCells();
        if (selectedCells.size() == 1) {
            Object obj = selectedCells.get(0);
            int row = ((TablePosition) obj).getRow();
            int col = ((TablePosition) obj).getColumn();
            final String attribute = viewer.getSamplesTable().getDataGrid().getColumnName(col);
            final String value = viewer.getSamplesTable().getDataGrid().getValue(row, col);
            executeImmediately("select similar name='" + attribute + "' value='" + value + "';");
        }
    }

    public boolean isApplicable() {
        return getViewer() instanceof SamplesViewer && ((SamplesViewer) getViewer()).getSamplesTable().getNumberOfSelectedCols() == 1;
    }

    public String getName() {
        return "Select Similar";
    }

    public String getDescription() {
        return "Deselect all";
    }

    public ImageIcon getIcon() {
        return null;
    }

    public boolean isCritical() {
        return true;
    }

    public KeyStroke getAcceleratorKey() {
        return null;
    }
}
