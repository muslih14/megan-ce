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
package megan.util;

import jloda.graph.Node;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import megan.viewer.ClassificationViewer;
import megan.viewer.MainViewer;
import megan.viewer.ViewerBase;

import java.io.IOException;

/**
 * compute different diversity indices
 * Daniel Huson, 4.2012
 */
public class DiversityIndex {
    public static final String SHANNON = "Shannon";
    public static final String SIMPSON_RECIPROCAL = "SimpsonReciprocal";

    private static final double LOG2 = Math.log(2);

    /**
     * compute the Shannon-Weaver diversity index in bits
     *
     * @param progressListener
     * @return index in bits
     */
    public static String computeShannonWeaver(ViewerBase viewer, ProgressListener progressListener) throws IOException, CanceledException {
        if (viewer instanceof MainViewer)
            return toString(computeShannonWeaver((MainViewer) viewer, progressListener));
        else if (viewer instanceof ClassificationViewer)
            return toString(computeShannonWeaver((ClassificationViewer) viewer, progressListener));
        else
            return null;
    }

    /**
     * compute the Shannon-Weaver diversity index in bits
     *
     * @param progressListener
     * @return index in bits
     */
    private static double[] computeShannonWeaver(MainViewer mainViewer, ProgressListener progressListener) throws IOException, CanceledException {

        progressListener.setMaximum(2 * mainViewer.getSelectedNodes().size());
        progressListener.setProgress(0);
        int numberOfDatasets = mainViewer.getDir().getDocument().getNumberOfSamples();
        double[] total = new double[numberOfDatasets];

        for (Node v : mainViewer.getSelectedNodes()) {
            int[] summarized = mainViewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++)
                total[i] += summarized[i];
            progressListener.incrementProgress();
        }

        double[] result = new double[numberOfDatasets];
        for (int i = 0; i < result.length; i++)
            result[0] = 0d;

        for (Node v : mainViewer.getSelectedNodes()) {
            int[] summarized = mainViewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++) {
                if (summarized[i] > 0) {
                    double p = summarized[i] / total[i];
                    result[i] += p * Math.log(p) / LOG2;
                }
            }
            progressListener.incrementProgress();
        }
        for (int i = 0; i < result.length; i++)
            result[i] = -result[i];
        return result;
    }

    /**
     * compute the Shannon-Weaver diversity index in bits
     *
     * @param progressListener
     * @return index in bits
     */
    private static double[] computeShannonWeaver(ClassificationViewer viewer, ProgressListener progressListener) throws IOException, CanceledException {

        progressListener.setMaximum(2 * viewer.getSelectedNodes().size());
        progressListener.setProgress(0);
        int numberOfDatasets = viewer.getDocument().getNumberOfSamples();
        double[] total = new double[numberOfDatasets];

        for (Node v : viewer.getSelectedNodes()) {
            int[] summarized = viewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++)
                total[i] += summarized[i];
            progressListener.incrementProgress();
        }

        double[] result = new double[numberOfDatasets];
        for (int i = 0; i < result.length; i++)
            result[0] = 0d;

        for (Node v : viewer.getSelectedNodes()) {
            int[] summarized = viewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++) {
                if (summarized[i] > 0) {
                    double p = summarized[i] / total[i];
                    result[i] += p * Math.log(p) / LOG2;
                }
            }
            progressListener.incrementProgress();
        }
        for (int i = 0; i < result.length; i++)
            result[i] = -result[i];
        return result;
    }

    /**
     * compute the Shannon-Weaver diversity index in bits
     *
     * @param progressListener
     * @return index in bits
     */
    public static String computeSimpsonReciprocal(ViewerBase viewer, ProgressListener progressListener) throws IOException, CanceledException {
        if (viewer instanceof MainViewer)
            return toString(computeSimpsonReciprocal((MainViewer) viewer, progressListener));
        else if (viewer instanceof ClassificationViewer)
            return toString(computeSimpsonReciprocal((ClassificationViewer) viewer, progressListener));
        else
            return null;
    }

    /**
     * compute the Shannon-Weaver diversity index in bits
     *
     * @param progressListener
     * @return index in bits
     */
    private static double[] computeSimpsonReciprocal(MainViewer mainViewer, ProgressListener progressListener) throws IOException, CanceledException {

        progressListener.setMaximum(2 * mainViewer.getSelectedNodes().size());
        progressListener.setProgress(0);
        int numberOfDatasets = mainViewer.getDir().getDocument().getNumberOfSamples();
        double[] total = new double[numberOfDatasets];

        for (Node v : mainViewer.getSelectedNodes()) {
            int[] summarized = mainViewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++)
                total[i] += summarized[i];
            progressListener.incrementProgress();
        }
        double[] result = new double[numberOfDatasets];
        for (Node v : mainViewer.getSelectedNodes()) {
            int[] summarized = mainViewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++) {
                double p = summarized[i] / total[i];
                result[i] += p * p;
            }
            progressListener.incrementProgress();
        }
        for (int i = 0; i < result.length; i++)
            result[i] = result[i] > 0 ? 1.0 / result[i] : 0;
        return result;
    }

    /**
     * compute the Shannon-Weaver diversity index in bits
     *
     * @param progressListener
     * @return index in bits
     */
    private static double[] computeSimpsonReciprocal(ClassificationViewer viewer, ProgressListener progressListener) throws IOException, CanceledException {

        progressListener.setMaximum(2 * viewer.getSelectedNodes().size());
        progressListener.setProgress(0);
        int numberOfDatasets = viewer.getDocument().getNumberOfSamples();
        double[] total = new double[numberOfDatasets];

        for (Node v : viewer.getSelectedNodes()) {
            int[] summarized = viewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++)
                total[i] += summarized[i];
            progressListener.incrementProgress();
        }
        double[] result = new double[numberOfDatasets];
        for (Node v : viewer.getSelectedNodes()) {
            int[] summarized = viewer.getNodeData(v).getSummarized();
            for (int i = 0; i < summarized.length; i++) {
                double p = summarized[i] / total[i];
                result[i] += p * p;
            }
            progressListener.incrementProgress();
        }
        for (int i = 0; i < result.length; i++)
            result[i] = result[i] > 0 ? 1.0 / result[i] : 0;
        return result;
    }

    /**
     * write numbers as string
     *
     * @param values
     * @return string
     */
    private static String toString(double[] values) {
        StringBuilder buf = new StringBuilder();

        boolean first = true;
        for (double value : values) {
            if (first)
                first = false;
            else
                buf.append(", ");
            buf.append(String.format("%.3f", value + 0.00001));
        }
        return buf.toString();
    }
}
