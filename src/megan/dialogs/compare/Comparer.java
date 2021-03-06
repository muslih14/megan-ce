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
package megan.dialogs.compare;

import jloda.util.*;
import jloda.util.parse.NexusStreamParser;
import megan.classification.Classification;
import megan.core.*;
import megan.parsers.blast.BlastMode;
import megan.viewer.MainViewer;
import megan.viewer.gui.NodeDrawer;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * comparison of multiple datasets
 * Daniel Huson, 3.2007
 */
public class Comparer {
    public enum COMPARISON_MODE {
        ABSOLUTE, RELATIVE;

        public static COMPARISON_MODE valueOfIgnoreCase(String name) {
            for (COMPARISON_MODE mode : values()) {
                if (mode.toString().equalsIgnoreCase(name))
                    return mode;
            }
            return RELATIVE; // is probably SUBSAMPLE, which is no longer supported
        }
    }

    final List<Director> dirs;
    int[] pid2pos;

    private COMPARISON_MODE mode = COMPARISON_MODE.ABSOLUTE;
    private boolean ignoreUnassigned = false;
    private boolean keep1 = false;

    /**
     * constructor
     */
    public Comparer() {
        dirs = new LinkedList<>();
    }

    /**
     * add a project to be compared
     *
     * @param dir
     */
    public void addDirector(Director dir) {
        dirs.add(dir);
    }

    /**
     * compute a comparison
     *
     * @param result
     * @param progressListener
     * @throws CanceledException
     */
    public void computeComparison(SampleAttributeTable sampleAttributeTable, final DataTable result, final ProgressListener progressListener) throws CanceledException {
        progressListener.setTasks("Computing comparison", "Initialization");
        progressListener.setMaximum(-1);

        System.err.println("Computing comparison: ");
        pid2pos = setupPid2Pos();

        result.setCreator(ProgramProperties.getProgramName());
        result.setCreationDate((new Date()).toString());


        final String[] names = new String[dirs.size()];
        final Long[] uids = new Long[dirs.size()];
        final Integer[] sizes = new Integer[dirs.size()];
        final BlastMode[] blastModes = new BlastMode[dirs.size()];

        // lock all unlocked projects involved in the comparison
        final List<Director> myLocked = new LinkedList<>();
        final Map<String, Object> sample2source = new HashMap<>();
        final ArrayList<String> sampleOrder = new ArrayList<>(dirs.size());
        for (final Director dir : dirs) {
            if (!dir.isLocked()) {
                dir.notifyLockInput();
                myLocked.add(dir);
            }
            int pos = pid2pos[dir.getID()];
            names[pos] = getUniqueName(names, pos, Basic.getFileBaseName(dir.getDocument().getTitle()));
            sizes[pos] = (int) dir.getDocument().getNumberOfReads();
            blastModes[pos] = dir.getDocument().getBlastMode();
            if (dir.getDocument().getSampleAttributeTable().getNumberOfSamples() == 1) {
                String oSample = dir.getDocument().getSampleAttributeTable().getSampleSet().iterator().next();
                Map<String, Object> attributes2value = dir.getDocument().getSampleAttributeTable().getAttributesToValues(oSample);
                sampleAttributeTable.addSample(names[pos], attributes2value, false, true);
            }
            try {
                uids[pos] = dir.getDocument().getMeganFile().getDataConnector().getUId();
            } catch (Exception e) {
                uids[pos] = 0L;
            }
            sample2source.put(names[pos], dir.getDocument().getMeganFile().getFileName());
            sampleOrder.add(names[pos]);
        }

        sampleAttributeTable.addAttribute(SampleAttributeTable.HiddenAttribute.Source.toString(), sample2source, true, true);

        final boolean useRelative = (getMode() == COMPARISON_MODE.RELATIVE);

        long calculateNewSampleSize = 0;
        if (useRelative) {
            for (Director dir : dirs) {
                final Document doc = dir.getDocument();
                final MainViewer mainViewer = dir.getMainViewer();
                long numberOfReads = isIgnoreUnassigned() ? mainViewer.getTotalAssignedReads() : doc.getNumberOfReads();
                if (calculateNewSampleSize == 0 || numberOfReads < calculateNewSampleSize)
                    calculateNewSampleSize = numberOfReads;
            }
                System.err.println("Normalizing to: " + calculateNewSampleSize + " reads per sample");
        }
        final long newSampleSize = calculateNewSampleSize;

        String parameters = "mode=" + getMode();
        if (useRelative)
            parameters += " normalizedTo=" + newSampleSize;
        if (isIgnoreUnassigned())
            parameters += " ignoreUnassigned=true";
        result.setParameters(parameters);

        progressListener.setMaximum(dirs.size());
        progressListener.setProgress(0);

        final int numberOfThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        final ArrayBlockingQueue<Director> inputQueue = new ArrayBlockingQueue<>(100 * numberOfThreads);
        final ExecutorService executor = Executors.newCachedThreadPool();

        final Counter submittedJobs = new Counter();
        final Counter completedJobs = new Counter();

        final long[] readCountPerThread = new long[numberOfThreads];

        final Single<Integer> progressListenerThread = new Single<>(-1); // make sure we are only moving progresslistener in one thread
        final ProgressSilent progressSilent = new ProgressSilent();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNumber = i;
            executor.execute(new Runnable() {
                public void run() {
                    while (true) {
                        long readCount = 0;

                        try {
                            final Director dir = inputQueue.take();
                            final Document doc = dir.getDocument();
                            final int pos = pid2pos[dir.getID()];
                            readCount = 0;

                            final DataTable table = dir.getDocument().getDataTable();

                            int numberOfReads;

                            if (isIgnoreUnassigned()) {
                                numberOfReads = (int) doc.getNumberOfReads();
                                Map<Integer, Integer[]> classificationBlock = table.getClassification2Class2Counts().get(ClassificationType.Taxonomy.toString());
                                if (classificationBlock != null) {
                                    for (Integer key : classificationBlock.keySet()) {
                                        if (key < 0)
                                            numberOfReads -= classificationBlock.get(key)[0];
                                    }
                                }
                            } else {
                                numberOfReads = (int) doc.getNumberOfReads();
                            }

                            ProgressListener progress;
                            synchronized (progressListenerThread) {
                                if (progressListenerThread.get() == -1) {
                                    progress = progressListener;
                                    progressListenerThread.set(threadNumber);
                                } else
                                    progress = progressSilent;
                            }

                            for (String classificationName : table.getClassification2Class2Counts().keySet()) {
                                boolean isTaxonomy = classificationName.equals(ClassificationType.Taxonomy.toString());

                                Map<Integer, Integer[]> class2countsSrc = table.getClass2Counts(classificationName);
                                Map<Integer, Integer[]> class2countsTarget = result.getClass2Counts(classificationName);
                                if (class2countsTarget == null) {
                                    synchronized (result) {
                                        class2countsTarget = result.getClass2Counts(classificationName);
                                        if (class2countsTarget == null) {
                                            class2countsTarget = new HashMap<>();
                                            result.getClassification2Class2Counts().put(classificationName, class2countsTarget);
                                        }
                                    }
                                }

                                final double factor = numberOfReads > 0 ? (double) newSampleSize / (double) numberOfReads : 1;

                                for (Integer classId : class2countsSrc.keySet()) {
                                    // todo: here we assume that the nohits id is the same for all classifications...
                                    if (!isIgnoreUnassigned() || classId > 0) {
                                        Integer[] countsTarget = class2countsTarget.get(classId);
                                        if (countsTarget == null) {
                                            synchronized (result) {
                                                countsTarget = class2countsTarget.get(classId);
                                                if (countsTarget == null) {
                                                    countsTarget = new Integer[dirs.size()];
                                                    for (int i = 0; i < countsTarget.length; i++)
                                                        countsTarget[i] = 0;
                                                    class2countsTarget.put(classId, countsTarget);
                                                }
                                            }
                                        }
                                        final Integer count = Basic.getSum(class2countsSrc.get(classId));
                                        if (count == null || count == 0)
                                            countsTarget[pos] = 0;
                                        else if (useRelative) {
                                            countsTarget[pos] = (int) Math.round(count * factor);
                                            if (countsTarget[pos] == 0 && isKeep1())
                                                countsTarget[pos] = 1;
                                        }
                                        else
                                            countsTarget[pos] = count;
                                        if (isTaxonomy)
                                            readCount += countsTarget[pos];
                                    }
                                }
                            }
                            sizes[pos] = (int) readCount;
                            progress.incrementProgress();
                        } catch (InterruptedException e) {
                            // Basic.caught(e);
                            return;    // this is the case when all jobs have been completed
                        } catch (Exception ex) {
                            Basic.caught(ex);
                        } finally {
                            synchronized (progressListenerThread) {
                                if (progressListenerThread.get() == threadNumber)
                                    progressListenerThread.set(-1);
                            }
                            readCountPerThread[threadNumber] += readCount;
                            completedJobs.increment();
                        }
                    }
                }
            });
        }

        progressListener.setTasks("Computing comparison", "Using " + mode.toString().toLowerCase() + " mode");
        progressListener.setProgress(0);
        progressListener.setMaximum(dirs.size());
        for (Director dir : dirs) {
            inputQueue.add(dir);
            submittedJobs.increment();
        }

        // wait until all jobs are done
        while (submittedJobs.get() > completedJobs.get()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Basic.caught(e);
            }
        }
        executor.shutdownNow();

        // if we have a taxonomy classification, then use it to get exact values:
        if (result.getClassification2Class2Counts().keySet().contains(Classification.Taxonomy)) {
            Map<Integer, Integer[]> class2counts = result.getClass2Counts(Classification.Taxonomy);
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = 0;
            }
            for (Integer[] counts : class2counts.values()) {
                for (int i = 0; i < counts.length; i++)
                    sizes[i] += counts[i];
            }
        }

        result.setSamples(names, uids, sizes, blastModes);
        sampleAttributeTable.removeAttribute(SampleAttributeTable.HiddenAttribute.Label.toString());

        final long totalNumberOfReads = Basic.getSum(readCountPerThread);

        for (String classificationName : result.getClassification2Class2Counts().keySet()) {
            result.setNodeStyle(classificationName, NodeDrawer.Style.PieChart.toString());
        }

        // unlock all  projects involved in the comparison
        for (final Director dir : myLocked) {
            dir.notifyUnlockInput();
        }

        if (useRelative) {
            System.err.println(" " + totalNumberOfReads + " normalized relative reads");
        } else {
            System.err.println(" " + totalNumberOfReads);
        }

        result.setTotalReads((int) totalNumberOfReads);
    }

    /**
     * modifies given name so that it does not match any of names[0],..,names[pos-1]
     *
     * @param names
     * @param pos
     * @param name
     * @return name or new name
     */
    private String getUniqueName(String[] names, int pos, String name) {
        boolean ok = false;
        int count = 0;
        String newName = name;
        while (!ok && count < 1000) {
            ok = true;
            for (int i = 0; ok && i < pos; i++) {
                if (newName.equalsIgnoreCase(names[i]))
                    ok = false;
            }
            if (!ok)
                newName = name + "." + (++count);
        }
        return newName;
    }

    /**
     * setup pid 2 position mapping
     */
    private int[] setupPid2Pos() {
        int maxId = 0;
        for (final Director dir : dirs) {
            int pid = dir.getID();
            if (pid > maxId)
                maxId = pid;
        }
        int[] pid2pos = new int[maxId + 1];
        int dirCount = 0;
        for (final Director dir : dirs) {
            int pid = dir.getID();
            pid2pos[pid] = dirCount++;
        }
        return pid2pos;
    }

    /**
     * gets the algorithm string
     *
     * @return algorithm string
     */
    public String getAlgorithm() {
        return "compare";
    }

    /**
     * Convenience method: gets the mode encoded in the parameter string
     *
     * @param parameterString
     * @return mode
     */
    static public COMPARISON_MODE parseMode(String parameterString) {
        try {
            if (parameterString != null) {
                NexusStreamParser np = new NexusStreamParser(new StringReader(parameterString));
                while (np.peekNextToken() != NexusStreamParser.TT_EOF) {
                    if (np.peekMatchIgnoreCase("mode=")) {
                        np.matchIgnoreCase("mode=");
                        return COMPARISON_MODE.valueOfIgnoreCase(np.getWordRespectCase());
                    } else np.getWordRespectCase(); // skip
                }
            }
        } catch (Exception ex) {
        }
        return COMPARISON_MODE.ABSOLUTE;
    }

    /**
     * Convenience method: gets the normalization number encoded in the parameter string
     *
     * @param parameterString
     * @return number of reads normalized by
     */
    public static int parseNormalizedTo(String parameterString) {
        try {
            if (parameterString != null) {
                NexusStreamParser np = new NexusStreamParser(new StringReader(parameterString));
                while (np.peekNextToken() != NexusStreamParser.TT_EOF) {
                    if (np.peekMatchIgnoreCase("normalizedTo=")) {
                        np.matchIgnoreCase("normalizedTo=");
                        return np.getInt();
                    }
                    // for backward compatibility:
                    if (np.peekMatchIgnoreCase("normalized_to=")) {
                        np.matchIgnoreCase("normalized_to=");
                        return np.getInt();
                    }
                    np.getWordRespectCase();
                }
            }
        } catch (Exception ex) {
        }
        return 0;
    }

    /**
     * set the comparison mode
     *
     * @param mode
     */
    public void setMode(COMPARISON_MODE mode) {
        this.mode = mode;
    }

    public void setMode(String modeName) {
        setMode(COMPARISON_MODE.valueOfIgnoreCase(modeName));
    }

    /**
     * gets the comparison mode
     *
     * @return mode
     */
    public COMPARISON_MODE getMode() {
        return mode;
    }

    public boolean isIgnoreUnassigned() {
        return ignoreUnassigned;
    }

    public void setIgnoreUnassigned(boolean ignoreUnassigned) {
        this.ignoreUnassigned = ignoreUnassigned;
    }

    public List<Director> getDirs() {
        return dirs;
    }

    public boolean isKeep1() {
        return keep1;
    }

    public void setKeep1(boolean keep1) {
        this.keep1 = keep1;
    }
}
