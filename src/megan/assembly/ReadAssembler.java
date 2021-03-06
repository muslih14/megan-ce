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

package megan.assembly;

import jloda.graph.*;
import jloda.util.*;
import malt.align.SimpleAligner4DNA;
import megan.core.Director;
import megan.data.IReadBlockIterator;
import megan.util.BoyerMoore;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * assembler for all reads assigned to a particular class
 * <p>
 * Daniel Huson, 5.2015
 */
public class ReadAssembler {
    private Graph overlapGraph;
    private NodeMap<String> node2ReadNameMap;
    private ReadData[] readId2ReadData;
    private Node[][] paths;
    private String label;
    private List<Pair<String, String>> contigs;
    private List<Integer>[] readId2ContainedReads;

    /**
     * constructor
     */
    public ReadAssembler() {
    }

    /**
     * build the overlap graph
     *
     * @param minOverlap
     * @param iterator
     * @param progress
     * @throws IOException
     * @throws CanceledException
     */
    public void computeOverlapGraph(String label, int minOverlap, IReadBlockIterator iterator, ProgressListener progress) throws IOException, CanceledException {
        this.label = label;
        final OverlapGraphBuilder overlapGraphBuilder = new OverlapGraphBuilder(minOverlap);
        overlapGraphBuilder.apply(iterator, progress);
        overlapGraph = overlapGraphBuilder.getOverlapGraph();
        readId2ReadData = overlapGraphBuilder.getReadId2ReadData();
        node2ReadNameMap = overlapGraphBuilder.getNode2ReadNameMap();
        readId2ContainedReads = overlapGraphBuilder.getReadId2ContainedReads();

        final PathExtractor pathExtractor = new PathExtractor(overlapGraph, readId2ContainedReads);
        pathExtractor.apply(progress);
    }

    /**
     * show the overlap graph
     *
     * @param dir
     * @param progress
     * @throws CanceledException
     */
    public void showOverlapGraph(Director dir, ProgressListener progress) throws CanceledException {
        final OverlapGraphViewer overlapGraphViewer = new OverlapGraphViewer(dir, overlapGraph, node2ReadNameMap, paths);
        overlapGraphViewer.apply(progress);
    }


    /**
     * write the overlap graph
     *
     * @param w
     * @return
     * @throws IOException
     * @throws CanceledException
     */
    public Pair<Integer, Integer> writeOverlapGraph(Writer w) throws IOException, CanceledException {
        final NodeArray<String> names = new NodeArray<>(overlapGraph);
        final NodeArray<String> sequences = new NodeArray<>(overlapGraph);
        for (Node v = overlapGraph.getFirstNode(); v != null; v = v.getNext()) {
            ReadData readData = readId2ReadData[(Integer) v.getInfo()];
            sequences.set(v, readData.getSegment());
            names.set(v, readData.getName());
        }
        final Map<String, NodeArray<?>> label2nodes = new TreeMap<>();
        label2nodes.put("label", names);
        label2nodes.put("sequence", sequences);

        final EdgeArray<Integer> overlap = new EdgeArray<>(overlapGraph);
        for (Edge e = overlapGraph.getFirstEdge(); e != null; e = e.getNext()) {
            overlap.set(e, (Integer) e.getInfo());
        }
        final Map<String, EdgeArray<?>> label2edges = new TreeMap<>();
        label2edges.put("label", null);
        label2edges.put("overlap", overlap);

        overlapGraph.writeGML(w, "Overlap graph generated by MEGAN6", true, label, 1, label2nodes, label2edges);

        return new Pair<>(this.overlapGraph.getNumberOfNodes(), this.overlapGraph.getNumberOfEdges());
    }

    /**
     * assemble all reads provided by the iterator using perfect overlaps of the given minimum length
     *
     * @param minReads
     * @param minCoverage
     * @param minLength
     * @param progress
     * @return number of contigs and singletons
     */
    public int computeContigs(int minReads, double minCoverage, int minLength, ProgressListener progress) throws IOException, CanceledException {
        final PathExtractor pathExtractor = new PathExtractor(overlapGraph, readId2ContainedReads);
        pathExtractor.apply(progress);
        paths = pathExtractor.getPaths();

        final ContigBuilder contigBuilder = new ContigBuilder(pathExtractor.getPaths(), readId2ContainedReads);
        contigBuilder.apply(readId2ReadData, minReads, minCoverage, minLength, progress);
        contigs = contigBuilder.getContigs();
        return contigBuilder.getCountContigs();
    }

    /**
     * identify and removed contained contigs
     *
     * @param progress
     * @param maxPercentIdentityAllowForSeparateContigs
     * @param contigs                                   list of contigs
     * @return number of contigs removed
     * @throws CanceledException
     */
    public static int removeContainedContigs(final ProgressListener progress, final float maxPercentIdentityAllowForSeparateContigs, List<Pair<String, String>> contigs) throws CanceledException {
        progress.setSubtask("Removing contained contigs");
        progress.setMaximum(contigs.size());
        progress.setProgress(0);

        if (contigs.size() <= 1) {
            if (progress instanceof ProgressPercentage)
                ((ProgressPercentage) progress).reportTaskCompleted();
            return contigs.size();
        }

        final List<Pair<String, String>> sorted = new ArrayList<>(contigs.size());
        sorted.addAll(contigs);
        sorted.sort(new Comparator<Pair<String, String>>() {
            @Override
            public int compare(Pair<String, String> pair1, Pair<String, String> pair2) {
                if (pair1.getSecond().length() > pair2.getSecond().length())
                    return -1;
                else if (pair1.getSecond().length() < pair2.getSecond().length())
                    return 1;
                else
                    return pair1.getFirst().compareTo(pair2.getFirst());
            }
        });
        contigs.clear();

        final boolean verbose = ProgramProperties.get("verbose-assembly", false);

        final int numberOfThreads = Math.min(sorted.size(), Runtime.getRuntime().availableProcessors() - 1);
        final ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        final BitSet containedContigs = new BitSet();
        final CountDownLatch countDownLatch = new CountDownLatch(sorted.size());

        progress.setMaximum(sorted.size());
        progress.setProgress(0);
        for (int i0 = 0; i0 < sorted.size(); i0++) {
            final int i = i0;

            service.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (maxPercentIdentityAllowForSeparateContigs < 100) {
                            final byte[] iBytes = sorted.get(i).getSecond().getBytes();
                            final byte[] iBytesReverseComplemented = Basic.getReverseComplement(sorted.get(i).getSecond()).getBytes();

                            final SimpleAligner4DNA simpleAlignerDNA = new SimpleAligner4DNA();
                            simpleAlignerDNA.setMinPercentIdentity(maxPercentIdentityAllowForSeparateContigs);

                            for (int j = 0; j < i; j++) {
                                boolean gone;
                                synchronized (containedContigs) {
                                    gone = containedContigs.get(j);
                                }
                                if (!gone) {
                                    final byte[] jBytes = sorted.get(j).getSecond().getBytes(); // this is the longer sequence, does it contain the shorter one, iBytes?
                                    if (simpleAlignerDNA.isContained(iBytes, jBytes)) {
                                        if (verbose) {
                                            System.err.println(String.format("Removed contig '%s', is %6.2f%% contained in '%s'",
                                                    Basic.skipFirstWord(sorted.get(i).getFirst()), simpleAlignerDNA.getPercentIdentity(),
                                                    Basic.skipFirstWord(sorted.get(j).getFirst())));
                                            System.err.println(simpleAlignerDNA.getAlignmentString());
                                        }
                                        synchronized (containedContigs) {
                                            containedContigs.set(i);
                                        }
                                        return;
                                    } else if (simpleAlignerDNA.isContained(iBytesReverseComplemented, jBytes)) {
                                        if (verbose) {
                                            System.err.println(String.format("Removed contig '%s', is %6.2f%% contained in '%s'",
                                                    Basic.skipFirstWord(sorted.get(i).getFirst()), simpleAlignerDNA.getPercentIdentity(),
                                                    Basic.skipFirstWord(sorted.get(j).getFirst())));
                                            System.err.println(simpleAlignerDNA.getAlignmentString());
                                        }
                                        synchronized (containedContigs) {
                                            containedContigs.set(i);
                                        }
                                        return;
                                    }
                                }
                            }
                        } else { // 100% contained, no need to align..., just check whether contained
                            final String iSequence = sorted.get(i).getSecond();
                            final String iSequenceReverseComplemented = Basic.getReverseComplement(iSequence);

                            final BoyerMoore boyerMoore = new BoyerMoore(iSequence); // make Boyer Moore for query
                            final BoyerMoore boyerMooreReverseComplemented = new BoyerMoore(iSequenceReverseComplemented);
                            for (int j = 0; j < i; j++) {
                                final String jSequence = sorted.get(j).getSecond();
                                if (boyerMoore.search(jSequence) < jSequence.length() || boyerMooreReverseComplemented.search(jSequence) < jSequence.length()) {
                                    containedContigs.set(i);
                                    return;
                                }
                            }
                        }

                    } finally {
                        countDownLatch.countDown();
                        try {
                            progress.incrementProgress();
                        } catch (CanceledException e) {
                            service.shutdownNow();
                            while (countDownLatch.getCount() > 0)
                                countDownLatch.countDown();
                        }
                    }
                }
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
        service.shutdownNow();

        int count = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (!containedContigs.get(i)) {
                String iName = sorted.get(i).getFirst().trim();
                if (iName.contains("length="))
                    iName = iName.substring(iName.indexOf("length="));
                iName = String.format(">Contig-%06d %s", ++count, iName);
                contigs.add(new Pair<>(iName, sorted.get(i).getSecond()));
            }
        }
        if (progress instanceof ProgressPercentage)
            ((ProgressPercentage) progress).reportTaskCompleted();
        System.err.println(String.format("Removed contigs:  %9d", containedContigs.cardinality()));
        return count;
    }

    /**
     * write contigs
     *
     * @param w
     * @param progress
     * @throws CanceledException
     * @throws IOException
     */
    public void writeContigs(Writer w, ProgressListener progress) throws CanceledException, IOException {
        progress.setSubtask("Writing contigs");
        progress.setMaximum(contigs.size());
        progress.setProgress(0);
        for (Pair<String, String> pair : contigs) {
            w.write(pair.getFirst().trim());
            w.write("\n");
            w.write(pair.getSecond().trim());
            w.write("\n");
            progress.incrementProgress();
        }
        w.flush();
        if (progress instanceof ProgressPercentage)
            ((ProgressPercentage) progress).reportTaskCompleted();
    }

    public List<Pair<String, String>> getContigs() {
        return contigs;
    }
}
