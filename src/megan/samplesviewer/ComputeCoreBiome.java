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
package megan.samplesviewer;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.ProgressListener;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.core.ClassificationType;
import megan.core.DataTable;
import megan.core.Document;
import megan.viewer.TaxonomyData;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * computes the core or rare biome for a set of samples
 * Reads summarized by a node are used to decide whether to keep node, reads assigned to node are used as counts
 * Daniel Huson, 2.2013
 */
public class ComputeCoreBiome {
    /**
     * computes core biome for a given threshold
     *
     * @param asUpperBound                   if true, keep rare taxa in which the threshold is an upper bound
     * @param threshold                      number of samples that must contain a taxon so that it appears in the output, or max, if asUpperBound is true
     * @param srcDoc
     * @param tarClassification2class2counts
     * @param progressListener
     * @return sampleSize
     */
    public static int apply(Collection<String> samplesToUse, boolean asUpperBound, int threshold, Document srcDoc,
                            Map<String, Map<Integer, Integer[]>> tarClassification2class2counts, ProgressListener progressListener) {

        BitSet sampleIds = srcDoc.getDataTable().getSampleIds(samplesToUse);
        int size = 0;

        if (sampleIds.cardinality() > 0) {
            DataTable dataTable = srcDoc.getDataTable();
            for (String classificationName : dataTable.getClassification2Class2Counts().keySet()) {
                Map<Integer, Integer[]> srcClass2counts = srcDoc.getDataTable().getClass2Counts(classificationName);
                Node root;

                if (classificationName.equals(Classification.Taxonomy))
                    root = TaxonomyData.getTree().getRoot();
                else {
                    root = ClassificationManager.get(classificationName, true).getFullTree().getRoot();

                }
                Map<Integer, Integer[]> tarClass2counts = new HashMap<>();
                tarClassification2class2counts.put(classificationName, tarClass2counts);

                computeCoreBiomeRec(sampleIds, asUpperBound, srcDoc.getNumberOfSamples(), threshold, root, srcClass2counts, tarClass2counts);
                // System.err.println(classificationName + ": " + tarClassification2class2counts.size());
            }

            Map<Integer, Integer[]> taxId2counts = tarClassification2class2counts.get(ClassificationType.Taxonomy.toString());
            if (taxId2counts != null) {
                for (Integer taxId : taxId2counts.keySet()) {
                    if (taxId >= 0) {
                        Integer[] values = taxId2counts.get(taxId);
                        size += values[0];
                    }
                }
            }
            if (size == 0) {
                for (String classificationName : dataTable.getClassification2Class2Counts().keySet()) {
                    if (!classificationName.equals(ClassificationType.Taxonomy.toString())) {
                        Map<Integer, Integer[]> id2counts = tarClassification2class2counts.get(classificationName);
                        if (id2counts != null) {
                            for (Integer ids : id2counts.keySet()) {
                                Integer[] values = id2counts.get(ids);
                                if (ids >= 0)
                                    size += values[0];
                            }
                            if (size > 0)
                                break;
                        }
                    }
                }
            }
        }
        return size;
    }

    /**
     * recursively compute the core biome
     *
     * @param threshold
     * @param v
     * @param srcClass2counts
     * @param tarClass2counts
     */
    private static int[] computeCoreBiomeRec(BitSet sampleIds, boolean asUpperBound, int numberOfSamples, int threshold, Node v, Map<Integer, Integer[]> srcClass2counts, Map<Integer, Integer[]> tarClass2counts) {
        int[] summarized = new int[numberOfSamples];

        int classId = (Integer) v.getInfo();

        if (classId == -1 || classId == -2 || classId == -3)
            return summarized;  // ignore unassigned etc

        Integer[] countsV = srcClass2counts.get(classId);
        if (countsV != null) {
            for (int i = 0; i < countsV.length; i++) {
                if (countsV[i] != null && sampleIds.get(i))
                    summarized[i] = countsV[i];
            }
        }

        for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
            Node w = e.getTarget();
            int[] countsBelow = computeCoreBiomeRec(sampleIds, asUpperBound, numberOfSamples, threshold, w, srcClass2counts, tarClass2counts);
            for (int i = 0; i < numberOfSamples; i++) {
                if (sampleIds.get(i)) {
                    summarized[i] += countsBelow[i];
                }
            }
        }

        int numberOfSamplesWithClass = 0;
        int value = 0;
        for (int i = 0; i < numberOfSamples; i++) {
            if (summarized[i] > 0 && sampleIds.get(i)) {
                numberOfSamplesWithClass++;
                if (countsV != null && i < countsV.length && countsV[i] != null && sampleIds.get(i))
                    value += countsV[i];
            }
        }
        if (countsV != null && ((!asUpperBound && numberOfSamplesWithClass >= threshold) || (asUpperBound && numberOfSamplesWithClass <= threshold))) {
            tarClass2counts.put(classId, new Integer[]{value});
        }
        return summarized;
    }
}
