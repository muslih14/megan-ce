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
package megan.commands.show;

import jloda.gui.commands.ICommand;
import jloda.util.Basic;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.commands.CommandBase;
import megan.core.Document;
import megan.dialogs.parameters.ParametersDialog;
import megan.fx.NotificationsInSwing;
import megan.inspector.InspectorWindow;
import megan.viewer.MainViewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class ShowParametersDialogCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return "recompute [minSupportPercent=<number>] [minSupport=<number>] [minScore=<number>] [maxExpected=<number>] [minPercentIdentity=<number>] [topPercent=<number>]\n" +
                "\t[weightedLCA={false|true}] [weightedLCAPercent=<number>] [minComplexity=<number>] [pairedReads={false|true}] [useIdentityFilter={false|true}]\n" +
                "\t[fNames={" + Basic.toString(ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy(), "|") + "];";
    }

    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("recompute");

        if (np.peekMatchIgnoreCase("minSupportPercent")) {
            np.matchIgnoreCase("minSupportPercent=");
            getDoc().setMinSupportPercent((float) np.getDouble(0, 100));
        }
        if (np.peekMatchIgnoreCase("minSupport")) {
            np.matchIgnoreCase("minSupport=");
            getDoc().setMinSupport(np.getInt(1, Integer.MAX_VALUE));
        }
        if (np.peekMatchIgnoreCase("minScore")) {
            np.matchIgnoreCase("minScore=");
            getDoc().setMinScore((float) np.getDouble(0, Float.MAX_VALUE));
        }
        if (np.peekMatchIgnoreCase("maxExpected")) {
            np.matchIgnoreCase("maxExpected=");
            getDoc().setMaxExpected((float) np.getDouble(0, Float.MAX_VALUE));
        }
        if (np.peekMatchIgnoreCase("minPercentIdentity")) {
            np.matchIgnoreCase("minPercentIdentity=");
            getDoc().setMinPercentIdentity((float) np.getDouble(0, 100));
        }
        if (np.peekMatchIgnoreCase("topPercent")) {
            np.matchIgnoreCase("topPercent=");
            getDoc().setTopPercent((float) np.getDouble(0, Float.MAX_VALUE));
        }
        if (np.peekMatchIgnoreCase("weightedLCA")) {
            np.matchIgnoreCase("weightedLCA=");
            getDoc().setWeightedLCA(np.getBoolean());
        }
        if (np.peekMatchIgnoreCase("weightedLCAPercent")) {
            np.matchIgnoreCase("weightedLCAPercent=");
            getDoc().setWeightedLCAPercent((float) np.getDouble(1, 100));
        }
        if (np.peekMatchIgnoreCase("minComplexity")) {
            np.matchIgnoreCase("minComplexity=");
            getDoc().setMinComplexity((float) np.getDouble(-1.0, 1.0));
        }
        if (np.peekMatchIgnoreCase("pairedReads")) {
            np.matchIgnoreCase("pairedReads=");
            getDoc().setPairedReads(np.getBoolean());
        }
        if (np.peekMatchIgnoreCase("useIdentityFilter")) {
            np.matchIgnoreCase("useIdentityFilter=");
            getDoc().setUseIdentityFilter(np.getBoolean());
        }
        if (np.peekMatchIgnoreCase("fNames")) {
            getDoc().getActiveViewers().clear();
            getDoc().getActiveViewers().add(Classification.Taxonomy);
            np.matchIgnoreCase("fNames=");
            while (!np.peekMatchIgnoreCase(";"))
                getDoc().getActiveViewers().add(np.getWordMatchesRespectingCase(Basic.toString(ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy(), " ")));
        }
        np.matchIgnoreCase(";");

        final InspectorWindow inspectorWindow = (InspectorWindow) getDir().getViewerByClass(InspectorWindow.class);
        if (inspectorWindow != null && inspectorWindow.getDataTree().getRowCount() > 1) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    inspectorWindow.clear();
                }
            });
        }

        getDoc().processReadHits();
        getDoc().setDirty(true);
        if (getViewer() instanceof MainViewer)
            ((MainViewer) getViewer()).setDoReInduce(true);
        NotificationsInSwing.showInformation(String.format("Classified %,d reads", +getDoc().getNumberOfReads()));
    }

    public void actionPerformed(ActionEvent event) {
        final ParametersDialog dialog = new ParametersDialog(getViewer().getFrame(), getDir());
        if (dialog.apply()) {
            execute("recompute " + dialog.getParameterString() + ";");
        }
        dialog.dispose();
    }

    public boolean isApplicable() {
        Document doc = getDoc();
        return !doc.getMeganFile().isReadOnly() && doc.getMeganFile().hasDataConnector();
    }

    public String getName() {
        return "Change LCA Parameters...";
    }

    public String getDescription() {
        return "Rerun the LCA analysis with different parameters";
    }

    public boolean isCritical() {
        return true;
    }

    public KeyStroke getAcceleratorKey() {
        return KeyStroke.getKeyStroke(KeyEvent.VK_B, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("sun/toolbarButtonGraphics/general/Preferences16.gif");
    }
}


