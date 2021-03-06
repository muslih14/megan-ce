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
package megan.viewer.commands.collapse;

import jloda.gui.commands.ICommand;
import jloda.util.Basic;
import jloda.util.parse.NexusStreamParser;
import megan.classification.ClassificationManager;
import megan.commands.CommandBase;
import megan.fx.NotificationsInSwing;
import megan.util.CallBack;
import megan.util.PopupChoice;
import megan.viewer.ClassificationViewer;
import megan.viewer.MainViewer;
import megan.viewer.TaxonomicLevels;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * collapse rank command
 * Daniel Huson, 9.2015
 */
public class CollapseByRankCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return "collapse rank={" + Basic.toString(TaxonomicLevels.getAllNames(), "|") + "}";
    }

    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("collapse rank=");
        final String rankName = np.getWordMatchesIgnoringCase(Basic.toString(TaxonomicLevels.getAllMajorRanks(), " "));
        Integer rank = TaxonomicLevels.getId(rankName);
        np.matchRespectCase(";");
        if (rank == 0)
            NotificationsInSwing.showError(getViewer().getFrame(), "Unknown rank: " + rankName);
        else {
            final ClassificationViewer classificationViewer = (ClassificationViewer) getViewer();
            classificationViewer.setCollapsedIds(ClassificationManager.get(classificationViewer.getClassName(), true).getFullTree().getNodeIdsAtGivenRank(rank, true));
            getDoc().setDirty(true);
            classificationViewer.updateTree();
        }
    }

    public void actionPerformed(ActionEvent event) {
        final String[] ranks = TaxonomicLevels.getAllMajorRanks().toArray(new String[TaxonomicLevels.getAllMajorRanks().size()]);

        String choice = null;
        if (getViewer() instanceof MainViewer) {
            choice = ((MainViewer) getViewer()).getPOWEREDBY();
        }

        PopupChoice<String> popupChoice = new PopupChoice<>(ranks, choice, new CallBack<String>() {
            @Override
            public void call(String choice) {
                execute("collapse rank='" + choice + "';select rank='" + choice + "';");
            }
        });
        popupChoice.showAtCurrentMouseLocation(getViewer().getFrame());
    }

    public boolean isCritical() {
        return true;
    }

    public boolean isApplicable() {
        final ClassificationViewer classificationViewer = (ClassificationViewer) getViewer();
        return ClassificationManager.hasTaxonomicRanks(classificationViewer.getClassName());
    }

    public String getName() {
        return "Rank...";
    }


    public String getDescription() {
        return "Collapse tree at specific rank";
    }

    public ImageIcon getIcon() {
        return null;
    }
}
