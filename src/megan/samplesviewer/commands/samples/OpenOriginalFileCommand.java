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
package megan.samplesviewer.commands.samples;

import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.core.SampleAttributeTable;
import megan.samplesviewer.SamplesViewer;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * * open original file
 * * Daniel Huson, 10.2015
 */
public class OpenOriginalFileCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return null;
    }

    /**
     * parses the given command and executes it
     *
     * @param np
     * @throws java.io.IOException
     */
    public void apply(NexusStreamParser np) throws Exception {
    }

    public void actionPerformed(ActionEvent event) {
        final SamplesViewer viewer = (SamplesViewer) getViewer();
        if (viewer.getDocument().getMeganFile().isMeganSummaryFile()) {
            if (viewer.getSamplesTable().getNumberOfSelectedSamples() > 0) {
                StringBuilder buf = new StringBuilder();
                for (String sample : viewer.getSamplesTable().getSelectedSamples()) {
                    Object source = viewer.getDocument().getSampleAttributeTable().get(sample, SampleAttributeTable.HiddenAttribute.Source.toString());
                    if (source != null) {
                        buf.append("open file='").append(source.toString()).append("';");
                    }
                }
                String command = buf.toString();
                if (command.length() > 0)
                    execute(command);
            }
        }
    }

    public boolean isApplicable() {
        if (getViewer() instanceof SamplesViewer) {
            final SamplesViewer viewer = (SamplesViewer) getViewer();
            if (viewer.getDocument().getMeganFile().isMeganSummaryFile()) {
                if (viewer.getSamplesTable().getNumberOfSelectedSamples() > 0) {
                    for (String sample : viewer.getSamplesTable().getSelectedSamples()) {
                        Object source = viewer.getDocument().getSampleAttributeTable().get(sample, SampleAttributeTable.HiddenAttribute.Source.toString());
                        if (source != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public String getName() {
        return "Open RMA File...";
    }

    public String getDescription() {
        return "Open the original RMA file";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("sun/toolbarButtonGraphics/general/Open16.gif");
    }

    public boolean isCritical() {
        return true;
    }

    public KeyStroke getAcceleratorKey() {
        return null;
    }

}
