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
package megan.commands.format;

import javafx.application.Platform;
import javafx.scene.control.ChoiceDialog;
import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.ProgramProperties;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.core.Director;
import megan.core.Document;
import megan.core.SampleAttributeTable;
import megan.samplesviewer.SamplesViewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Optional;

/**
 * * color by command
 * * Daniel Huson, 9.2105
 */
public class ColorSamplesByCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return "colorBy attribute=<name>;";
    }

    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("colorBy attribute=");
        String attribute = np.getWordRespectCase();
        Document doc = ((Director) getDir()).getDocument();

        java.util.Collection<String> samples;
        if (getViewer() instanceof SamplesViewer) {
            samples = ((SamplesViewer) getViewer()).getSamplesTable().getSelectedSamples();
        } else
            samples = doc.getSampleAttributeTable().getSampleSet();

        ProgramProperties.put("SetByAttribute", attribute);

        if (doc.getChartColorManager().isColorByPosition()) {
            final ArrayList<String> states = new ArrayList<>();
            for (String sample : samples) {
                Object value = doc.getSampleAttributeTable().get(sample, attribute);
                if (value != null && !value.equals("NA"))
                    states.add(value.toString());
            }
            doc.getChartColorManager().setAttributeStateColorPositions(attribute, states);
        }

        if (attribute.equals(SampleAttributeTable.SAMPLE_ID)) {
            for (String sample : samples) {
                doc.getSampleAttributeTable().putSampleColor(sample, null);
            }
        } else {
            for (String sample : samples) {
                Object value = doc.getSampleAttributeTable().get(sample, attribute);
                final Color color = doc.getChartColorManager().getAttributeStateColor(attribute, value.toString());
                doc.getSampleAttributeTable().putSampleColor(sample, color);
            }
        }
        doc.setDirty(true);
    }

    public void actionPerformed(ActionEvent event) {
        final Document doc = ((Director) getDir()).getDocument();
        final java.util.List<String> attributes = doc.getSampleAttributeTable().getUnhiddenAttributes();

        if (attributes.size() > 0) {
            final JFrame frame = getViewer().getFrame();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    String defaultChoice = ProgramProperties.get("SetByAttribute", "");

                    if (!attributes.contains(defaultChoice))
                        defaultChoice = attributes.get(0);

                    ChoiceDialog<String> dialog = new ChoiceDialog<>(defaultChoice, attributes);
                    dialog.setTitle("MEGAN6 " + getViewer().getClassName() + " choice");
                    dialog.setHeaderText("Select attribute to color by");
                    dialog.setContentText("Choose attribute:");

                    if (frame != null) {
                        dialog.setX(frame.getX() + (frame.getWidth() - 200) / 2);
                        dialog.setY(frame.getY() + (frame.getHeight() - 200) / 2);
                    }

                    final Optional<String> result = dialog.showAndWait();
                    if (result.isPresent()) {
                        final String choice = result.get();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                execute("colorBy attribute='" + choice + "';");
                            }
                        });
                    }
                }
            });
        }
    }

    public boolean isApplicable() {
        Document doc = ((Director) getDir()).getDocument();
        return doc.getSampleAttributeTable().getNumberOfUnhiddenAttributes() > 0;
    }

    public static final String NAME = "Color Samples By Attribute";

    public String getName() {
        return NAME;
    }

    public String getDescription() {
        return "Color samples by selected attribute";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("YellowSquare16.gif");
    }

    public boolean isCritical() {
        return true;
    }

    public KeyStroke getAcceleratorKey() {
        return null;
    }

}
