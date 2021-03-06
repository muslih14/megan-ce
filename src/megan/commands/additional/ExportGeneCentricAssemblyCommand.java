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
package megan.commands.additional;

import jloda.gui.ChooseFileDialog;
import jloda.gui.commands.ICommand;
import jloda.util.*;
import jloda.util.parse.NexusStreamParser;
import megan.alignment.AlignmentViewer;
import megan.assembly.ReadAssembler;
import megan.assembly.alignment.AlignmentAssembler;
import megan.commands.CommandBase;
import megan.core.Director;
import megan.core.Document;
import megan.data.IReadBlockIterator;
import megan.data.ReadBlockIteratorMaxCount;
import megan.fx.NotificationsInSwing;
import megan.parsers.blast.BlastMode;
import megan.viewer.ViewerBase;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

/**
 * assemble all reads associated with a selected node
 * Daniel Huson, 5.2015
 */
public class ExportGeneCentricAssemblyCommand extends CommandBase implements ICommand {

    public String getSyntax() {
        return "export assembly file=<name> [minOverlap=<number>] [minReads=<number>] [minLength=<number>] [minAvCoverage=<number>] [maxPercentIdentity=<number>] [maxNumberOfReads=<number>] [showGraph={false|true}];";
    }

    // nt minReads, double minCoverage, int minLength,
    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("export assembly");

        np.matchIgnoreCase("file=");
        final String outputFile = np.getWordFileNamePunctuation();

        final int minOverlap;
        if (np.peekMatchIgnoreCase("minOverlap")) {
            np.matchIgnoreCase("minOverlap=");
            minOverlap = np.getInt(1, 1000000);
        } else
            minOverlap = 20;

        final int minReads;
        if (np.peekMatchIgnoreCase("minReads")) {
            np.matchIgnoreCase("minReads=");
            minReads = np.getInt(1, 1000000);
        } else minReads = 2;


        final int minLength;
        if (np.peekMatchIgnoreCase("minLength")) {
            np.matchIgnoreCase("minLength=");
            minLength = np.getInt(1, 1000000);
        } else
            minLength = 0;

        final float minAvCoverage;
        if (np.peekMatchIgnoreCase("minAvCoverage")) {
            np.matchIgnoreCase("minAvCoverage=");
            minAvCoverage = (float) np.getDouble(0, 1000000);
        } else
            minAvCoverage = 0;

        float maxPercentIdentity = 100;
        if (np.peekMatchIgnoreCase("maxPercentIdentity")) {
            np.matchIgnoreCase("maxPercentIdentity=");
            maxPercentIdentity = (float) np.getDouble(0, 100);
        }

        int maxNumberOfReads = -1;
        if (np.peekMatchIgnoreCase("maxNumberOfReads")) {
            np.matchIgnoreCase("maxNumberOfReads=");
            maxNumberOfReads = np.getInt(-1, Integer.MAX_VALUE);
        }
        boolean showGraph = false;
        if (np.peekMatchIgnoreCase("showGraph")) {
            np.matchIgnoreCase("showGraph=");
            showGraph = np.getBoolean();
        }

        np.matchIgnoreCase(";");

        final Director dir = getDir();
        final Document doc = dir.getDocument();
        final ProgressListener progress = (ProgramProperties.isUseGUI() ? doc.getProgressListener() : new ProgressPercentage());

        progress.setTasks("Gene-centric assembly", "Initializing");

        String message = "";

        if (getViewer() instanceof AlignmentViewer) {
            final AlignmentViewer viewer = (AlignmentViewer) getViewer();
            final AlignmentAssembler alignmentAssembler = new AlignmentAssembler();
            alignmentAssembler.computeOverlapGraph(minOverlap, viewer.getAlignment(), progress);
            int count = alignmentAssembler.computeContigs(0, minReads, minAvCoverage, minLength, false, progress);

            System.err.println(String.format("Number of contigs:%9d", count));

            count = ReadAssembler.removeContainedContigs(progress, maxPercentIdentity, alignmentAssembler.getContigs());
            System.err.println(String.format("Remaining contigs:%9d", count));

            try (Writer w = new BufferedWriter(new FileWriter(outputFile))) {
                alignmentAssembler.writeContigs(w, progress);
                System.err.println("Contigs written to: " + outputFile);
                message += "Wrote " + count + " contigs\n";
            }
            if (showGraph)
                alignmentAssembler.showOverlapGraph(dir, dir.getDocument().getProgressListener());
        } else {
            final ViewerBase viewer = (ViewerBase) getViewer();
            if (viewer.getSelectedIds().size() > 0) {
                ReadAssembler readAssembler = new ReadAssembler();

                final IReadBlockIterator it0 = doc.getConnector().getReadsIteratorForListOfClassIds(viewer.getClassName(), viewer.getSelectedIds(), 0, 10, true, true);
                try (IReadBlockIterator it = (maxNumberOfReads > 0 ? new ReadBlockIteratorMaxCount(it0, maxNumberOfReads) : it0)) {
                    final String label = viewer.getClassName() + ". Id(s): " + Basic.toString(viewer.getSelectedIds(), ", ");
                    readAssembler.computeOverlapGraph(label, minOverlap, it, progress);
                    int count = readAssembler.computeContigs(minReads, minAvCoverage, minLength, progress);

                    System.err.println(String.format("Number of contigs:%9d", count));

                    count = ReadAssembler.removeContainedContigs(progress, maxPercentIdentity, readAssembler.getContigs());
                    System.err.println(String.format("Remaining contigs:%9d", count));

                    if (ProgramProperties.get("verbose-assembly", false)) {
                        for (Pair<String, String> contig : readAssembler.getContigs()) {
                            System.err.println(contig.getFirst());
                        }
                    }

                    try (Writer w = new BufferedWriter(new FileWriter(outputFile))) {
                        readAssembler.writeContigs(w, progress);
                        System.err.println("Contigs written to: " + outputFile);
                        message += "Wrote " + count + " contigs\n";
                    }
                    if (showGraph)
                        readAssembler.showOverlapGraph(dir, progress);
                }
            } else
                message = "Nothing selected";
        }
        if (message.length() > 0)
            NotificationsInSwing.showInformation(getViewer().getFrame(), message);
    }

    public void actionPerformed(final ActionEvent event) {
        final Single<Boolean> askedToOverwrite = new Single<>(false);
        final Document doc = getDir().getDocument();
        final String classificationName = getViewer().getClassName();

        final JDialog dialog = new JDialog(getViewer().getFrame());
        dialog.setModal(true);
        dialog.setLocationRelativeTo(getViewer().getFrame());
        dialog.setSize(800, 350);
        dialog.getContentPane().setLayout(new BorderLayout());
        final JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());

        dialog.getContentPane().add(mainPanel, BorderLayout.CENTER);

        dialog.setTitle("Assembly for " + classificationName + " - " + ProgramProperties.getProgramName());

        final JPanel middlePanel = new JPanel();
        middlePanel.setLayout(new BoxLayout(middlePanel, BoxLayout.Y_AXIS));

        {
            final String message;
            if (getViewer() instanceof AlignmentViewer)
                message = "Run 'gene-centric assembly' on current alignment";
            else
                message = "Run 'gene-centric assembly' on selected node";

            final JPanel messagePanel = newSingleLine(new JLabel(message), Box.createHorizontalGlue());
            messagePanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            middlePanel.add(messagePanel);
        }

        File lastOpenFile = ProgramProperties.getFile("AssemblyFile");
        String fileName = Basic.replaceFileSuffix(doc.getMeganFile().getName(), "");
        String addOn = null;
        if (getViewer() instanceof AlignmentViewer) {
            addOn = Basic.toCleanName(((AlignmentViewer) getViewer()).getAlignment().getName()).replaceAll("[_]+", "_");
        } else if (getViewer() instanceof ViewerBase) {
            addOn = getViewer().getClassName().toLowerCase();
            final Set<String> labels = new HashSet<>();
            labels.addAll(((ViewerBase) getViewer()).getSelectedNodeLabels(false));
            if (labels.size() == 1)
                addOn += "-" + Basic.toCleanName(labels.iterator().next()).replaceAll("[_]+", "_");
        }
        if (addOn != null) {
            final File file = new File(fileName);
            final String name = file.getName();
            fileName = Basic.getFilePath(file.getParent(), Basic.replaceFileSuffix(name, "-" + addOn));
        }

        if (lastOpenFile != null) {
            fileName = new File(lastOpenFile.getParent(), fileName).getPath();
        }
        fileName += "-contigs.fasta";

        final JTextField outfile = new JTextField();
        outfile.setText(fileName);

        final JPanel m1 = new JPanel();
        m1.setLayout(new BorderLayout());
        m1.setBorder(BorderFactory.createEmptyBorder(2, 4, 20, 4));
        m1.add(new JLabel("Output file:"), BorderLayout.WEST);
        m1.add(outfile, BorderLayout.CENTER);
        outfile.setToolTipText("Set file to save to");
        m1.add(new JButton(new AbstractAction("Browse...") {
            public void actionPerformed(ActionEvent actionEvent) {
                File file = ChooseFileDialog.chooseFileToSave(getViewer().getFrame(), new File(outfile.getText()), new FastaFileFilter(), new FastaFileFilter(), event, "Save assembly file", ".fasta");
                if (file != null) {
                    askedToOverwrite.set(true);
                    outfile.setText(file.getPath());
                }
            }
        }), BorderLayout.EAST);
        middlePanel.add(m1);


        final JPanel parametersPanel = new JPanel();
        parametersPanel.setLayout(new GridLayout(7, 4));
        mainPanel.add(parametersPanel, BorderLayout.CENTER);

        final JTextField minOverlapTextField = new JTextField(6);
        minOverlapTextField.setMaximumSize(new Dimension(100, 20));
        minOverlapTextField.setText("" + ProgramProperties.get("AssemblyMinOverlap", 20));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), new JLabel("Min overlap: ")));
        parametersPanel.add(newSingleLine(minOverlapTextField, Box.createHorizontalGlue()));
        minOverlapTextField.setToolTipText("Minimum length of exact overlap between two reads");

        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), Box.createHorizontalGlue()));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), Box.createHorizontalGlue()));

        final JTextField minReadsTextField = new JTextField(6);
        minReadsTextField.setMaximumSize(new Dimension(100, 20));
        minReadsTextField.setText("" + ProgramProperties.get("AssemblyMinReads", 5));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), new JLabel("Minimum reads: ")));
        parametersPanel.add(newSingleLine(minReadsTextField, Box.createHorizontalGlue()));
        minReadsTextField.setToolTipText("Minimum number of reads in a contig");

        final JTextField minLengthTextField = new JTextField(6);
        minLengthTextField.setMaximumSize(new Dimension(100, 20));
        minLengthTextField.setText("" + ProgramProperties.get("AssemblyMinLength", 200));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), new JLabel("Minimum length: ")));
        parametersPanel.add(newSingleLine(minLengthTextField, Box.createHorizontalGlue()));
        minLengthTextField.setToolTipText("Minimum contig length");

        final JTextField minAvCoverageTextField = new JTextField(6);
        minAvCoverageTextField.setMaximumSize(new Dimension(100, 20));
        minAvCoverageTextField.setText("" + ProgramProperties.get("AssemblyMinAvCoverage", 2));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), new JLabel("Min average coverage: ")));
        parametersPanel.add(newSingleLine(minAvCoverageTextField, Box.createHorizontalGlue()));
        minAvCoverageTextField.setToolTipText("Minimum average coverage of a contig");

        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), Box.createHorizontalGlue()));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), Box.createHorizontalGlue()));

        final JTextField maxPercentIdentityTextField = new JTextField(6);
        maxPercentIdentityTextField.setMaximumSize(new Dimension(100, 20));
        maxPercentIdentityTextField.setText("" + ProgramProperties.get("AssemblyMaxPercentIdentity", 98));
        parametersPanel.add(newSingleLine(Box.createHorizontalGlue(), new JLabel("Max percent identity: ")));
        parametersPanel.add(newSingleLine(maxPercentIdentityTextField, Box.createHorizontalGlue()));
        maxPercentIdentityTextField.setToolTipText("Maximum percent identity to consider two contigs different");

        middlePanel.add(parametersPanel);
        mainPanel.add(middlePanel, BorderLayout.NORTH);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setBorder(BorderFactory.createEtchedBorder());
        bottomPanel.setLayout(new BorderLayout());

        JPanel b1 = new JPanel();

        b1.add(new JButton(new AbstractAction("Cancel") {
            public void actionPerformed(ActionEvent actionEvent) {
                dialog.setVisible(false);
            }
        }));

        b1.setLayout(new BoxLayout(b1, BoxLayout.X_AXIS));
        final JButton applyButton = new JButton(new AbstractAction("Apply") {
            public void actionPerformed(ActionEvent actionEvent) {

                String fileName = (new File(outfile.getText().trim())).getPath();

                if (fileName.length() > 0) {
                    if (Basic.getSuffix(fileName) == null)
                        fileName = Basic.replaceFileSuffix(fileName, ".fasta");
                    if (!askedToOverwrite.get() && (new File(fileName)).exists()) {
                        switch (JOptionPane.showConfirmDialog(getViewer().getFrame(), "File already exists, do you want to replace it?", "File exists", JOptionPane.YES_NO_CANCEL_OPTION)) {
                            case JOptionPane.CANCEL_OPTION: // close and abort
                                dialog.setVisible(false);
                                return;
                            case JOptionPane.NO_OPTION: // don't close
                                return;
                            default: // close and continue
                        }
                    }
                    dialog.setVisible(false);

                    ProgramProperties.put("AssemblyFile", fileName);
                    ProgramProperties.put("AssemblyMinOverlap", minOverlapTextField.getText());
                    ProgramProperties.put("AssemblyMinReads", minReadsTextField.getText());
                    ProgramProperties.put("AssemblyMinLength", minLengthTextField.getText());
                    ProgramProperties.put("AssemblyMinAvCoverage", minAvCoverageTextField.getText());
                    ProgramProperties.put("AssemblyMaxPercentIdentity", maxPercentIdentityTextField.getText());

                    final String command = "export assembly file='" + fileName + "'"
                            + " minOverlap=" + minOverlapTextField.getText()
                            + " minReads=" + minReadsTextField.getText()
                            + " minLength=" + minLengthTextField.getText()
                            + " minAvCoverage=" + minAvCoverageTextField.getText()
                            + " maxPercentIdentity=" + maxPercentIdentityTextField.getText()
                            + " showGraph=false;";

                    execute(command);
                }
            }
        });
        b1.add(applyButton);
        dialog.getRootPane().setDefaultButton(applyButton);

        bottomPanel.add(b1, BorderLayout.EAST);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(mainPanel, BorderLayout.CENTER);
        dialog.validate();

        outfile.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyButton.setEnabled(outfile.getText().trim().length() > 0);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                insertUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                insertUpdate(e);
            }
        });

        dialog.setVisible(true);
    }

    private static JPanel newSingleLine(Component left, Component right) {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(left);
        panel.add(right);
        return panel;
    }

    public boolean isApplicable() {
        final Document doc = getDir().getDocument();
        return (getViewer() instanceof AlignmentViewer && ((AlignmentViewer) getViewer()).getAlignment().getLength() > 0) ||
                (getViewer() instanceof ViewerBase && ((ViewerBase) getViewer()).getNumberSelectedNodes() > 0 && doc.getMeganFile().hasDataConnector() && doc.getBlastMode().equals(BlastMode.BlastX));
    }

    public String getName() {
        return "Gene-Centric Assembly...";
    }

    public String getDescription() {
        return "Compute and export 'gene-centric' assembly of reads for all selected nodes.\n" +
                "This is experimental code, we are currently developing this method and will describe it in a future paper";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("sun/toolbarButtonGraphics/general/Export16.gif");
    }

    public boolean isCritical() {
        return true;
    }


}
