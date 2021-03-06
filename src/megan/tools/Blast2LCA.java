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

package megan.tools;

import jloda.util.*;
import megan.algorithms.ActiveMatches;
import megan.algorithms.KeggTopAssignment;
import megan.algorithms.TaxonPathAssignment;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.IdParser;
import megan.core.Document;
import megan.data.IReadBlock;
import megan.main.MeganProperties;
import megan.parsers.blast.BlastFileFormat;
import megan.parsers.blast.BlastMode;
import megan.rma6.BlastFileReadBlockIterator;
import megan.viewer.TaxonomicLevels;
import megan.viewer.TaxonomyData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Program that parses Blast input and computes a taxonomy classification and also a KEGG mapping, if desired
 * Daniel Huson, 3.2012
 */
public class Blast2LCA {
    /**
     * prepare DNA protein for pDNA
     *
     * @param args
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     */
    public static void main(String[] args) {
        try {
            ProgramProperties.setProgramName("Blast2LCA");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            long start = System.currentTimeMillis();
            (new Blast2LCA()).run(args);
            System.err.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + "s");
            System.exit(0);

        } catch (Exception ex) {
            Basic.caught(ex);
            System.exit(1);
        }
    }

    /**
     * run
     *
     * @param args
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    public void run(String[] args) throws UsageException, IOException, ClassNotFoundException, CanceledException {
        final ArgsOptions options = new ArgsOptions(args, this, "Applies the LCA alignment to reads and produce a taxonomic classification");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2016 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input");

        String blastFile = options.getOptionMandatory("-i", "input", "Input BLAST file", "foo.blast");
        String blastFormat = options.getOption("-f", "format", "BLAST format", BlastFileFormat.values(), BlastFileFormat.Unknown.toString());
        String blastMode = options.getOption("-m", "mode", "BLAST mode", BlastMode.values(), BlastMode.Unknown.toString());
        options.comment("Output");
        String outputFile = options.getOption("-o", "output", "Taxonomy output file", Basic.getFileBaseName(Basic.getFileNameWithoutZipOrGZipSuffix(blastFile)) + "-taxonomy.txt");
        String keggOutputFile = options.getOption("-ko", "keggOutput", "KEGG output file", Basic.getFileBaseName(Basic.getFileNameWithoutZipOrGZipSuffix(blastFile)) + "-kegg.txt");

        options.comment("Functional classification:");
        boolean doKegg = options.getOption("-k", "kegg", "Map reads to KEGG KOs?", false);

        options.comment("Output options:");
        boolean showRank = options.getOption("-sr", "showRanks", "Show taxonomic ranks", true);
        boolean useOfficialRanksOnly = options.getOption("-oro", "officialRanksOnly", "Report only taxa that have an official rank", true);
        boolean showTaxonIds = options.getOption("-tid", "showTaxIds", "Report taxon ids rather than taxon names", false);

        options.comment("Parameters");
        float minScore = options.getOption("-ms", "minScore", "Min score", Document.DEFAULT_MINSCORE);
        float maxExpected = options.getOption("-me", "maxExpected", "Max expected", 0.01f);
        float topPercent = options.getOption("-top", "topPercent", "Top percent", Document.DEFAULT_TOPPERCENT);
        float minPercentIdentity = options.getOption("-mid", "minPercentIdentity", "Min percent identity", Document.DEFAULT_MIN_PERCENT_IDENTITY);

        double minComplexity = 0; //options.getOption("-c","Minimum complexity (between 0 and 1)",0.0);

        int keggRanksToReport = options.getOption("-kr", "maxKeggPerRead", "Maximum number of KEGG assignments to report for a read", 4);
        boolean applyTopPercentFilterToKEGGAnalysis = options.getOption("+ktp", "applyTopPercentKegg", "Apply top percent filter in KEGG KO analysis", true);

        options.comment("Classification support:");

        final boolean parseTaxonNames = options.getOption("-tn", "parseTaxonNames", "Parse taxon names", true);
        final String gi2TaxaFile = options.getOption("-g2t", "gi2taxa", "GI-to-Taxonomy mapping file", "");
        final String acc2TaxaFile = options.getOption("-a2t", "acc2taxa", "Accession-to-Taxonomy mapping file", "");
        final String synonyms2TaxaFile = options.getOption("-s2t", "syn2taxa", "Synonyms-to-Taxonomy mapping file", "");

        final String gi2KeggFile = options.getOption("-g2kegg", "gi2kegg", "GI-to-KEGG mapping file", "");
        final String acc2KeggFile = options.getOption("-a2kegg", "acc2kegg", "Accession-to-KEGG mapping file", "");
        final String synonyms2KeggFile = options.getOption("-s2kegg", "syn2kegg", "Synonyms-to-KEGG mapping file", "");

        options.comment(ArgsOptions.OTHER);
        ProgramProperties.put(IdParser.PROPERTIES_FIRST_WORD_IS_ACCESSION, options.getOption("-fwa", "firstWordIsAccession", "First word in reference header is accession number", ProgramProperties.get(IdParser.PROPERTIES_FIRST_WORD_IS_ACCESSION, true)));
        ProgramProperties.put(IdParser.PROPERTIES_ACCESSION_TAGS, options.getOption("-atags", "accessionTags", "List of accession tags", ProgramProperties.get(IdParser.PROPERTIES_ACCESSION_TAGS, IdParser.ACCESSION_TAGS)));
        options.done();

        if (false)
        {
            final ClassLoader cl = ClassLoader.getSystemClassLoader();

            final URL[] urls = ((URLClassLoader) cl).getURLs();

            System.err.println("classpath:");
            for (URL url : urls) {
                System.err.println("\t" + url.getFile());
            }
        }


        final String propertiesFile;
        if (ProgramProperties.isMacOS())
            propertiesFile = System.getProperty("user.home") + "/Library/Preferences/Megan.def";
        else
            propertiesFile = System.getProperty("user.home") + File.separator + ".Megan.def";
        MeganProperties.initializeProperties(propertiesFile);

        ProgramProperties.get("oneMatchPerTaxon", false);

        if (blastFormat.equalsIgnoreCase(BlastFileFormat.Unknown.toString())) {
            blastFormat = BlastFileFormat.detectFormat(null, blastFile, true).toString();
        }
        if (blastMode.equalsIgnoreCase(BlastMode.Unknown.toString()))
            blastMode = BlastMode.detectMode(null, blastFile, false).toString();

        final IdMapper taxonIdMapper = ClassificationManager.get(Classification.Taxonomy, true).getIdMapper();
        // load taxonomy:
        {
            taxonIdMapper.setUseTextParsing(parseTaxonNames);
            if (gi2TaxaFile.length() > 0) {
                taxonIdMapper.loadMappingFile(gi2TaxaFile, IdMapper.MapType.GI, false, new ProgressPercentage());
            }
            if (acc2TaxaFile.length() > 0) {
                taxonIdMapper.loadMappingFile(acc2TaxaFile, IdMapper.MapType.Accession, false, new ProgressPercentage());
            }
            if (synonyms2TaxaFile.length() > 0) {
                taxonIdMapper.loadMappingFile(synonyms2TaxaFile, IdMapper.MapType.Synonyms, false, new ProgressPercentage());
            }
            final IdMapper keggMapper = ClassificationManager.get("KEGG", true).getIdMapper();
            if (doKegg) {
                if (gi2KeggFile.length() > 0) {
                    keggMapper.loadMappingFile(gi2KeggFile, IdMapper.MapType.GI, false, new ProgressPercentage());
                }
                if (acc2KeggFile.length() > 0) {
                    keggMapper.loadMappingFile(acc2KeggFile, IdMapper.MapType.Accession, false, new ProgressPercentage());
                }
                if (synonyms2KeggFile.length() > 0) {
                    keggMapper.loadMappingFile(synonyms2KeggFile, IdMapper.MapType.Synonyms, false, new ProgressPercentage());
                }
            }

            int totalIn = 0;
            int totalOut = 0;

            System.err.println("Reading file: " + blastFile);
            System.err.println("Writing file: " + outputFile);

            try (BlastFileReadBlockIterator it = new BlastFileReadBlockIterator(blastFile, null, BlastFileFormat.valueOfIgnoringCase(blastFormat), BlastMode.valueOfIgnoringCase(blastMode), new String[]{"Taxonomy", "KEGG"}, 100, false)) {
                final ProgressPercentage progressListener = new ProgressPercentage();
                progressListener.setMaximum(it.getMaximumProgress());

                try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile))) {
                    BufferedWriter keggW = null;
                    if (doKegg) {
                        keggW = new BufferedWriter(new FileWriter(keggOutputFile));
                        System.err.println("Writing file: " + keggOutputFile);
                    }
                    try {
                        while (it.hasNext()) {
                            IReadBlock readBlock = it.next();
                            totalIn++;

                            final BitSet activeMatchesForTaxa = new BitSet();
                            final BitSet activeMatchesForGenes = new BitSet();

                            boolean hasLowComplexity = readBlock.getComplexity() > 0 && readBlock.getComplexity() + 0.01 < minComplexity;

                            List<Pair<Integer, Float>> path;
                            if (hasLowComplexity) {
                                path = new LinkedList<>();
                                Pair<Integer, Float> pair = new Pair<>(IdMapper.LOW_COMPLEXITY_ID, 100f);
                                path.add(pair);
                            } else {
                                if (topPercent == 0)
                                    topPercent = 0.0001f;
                                ActiveMatches.compute(minScore, topPercent, maxExpected, minPercentIdentity, readBlock, Classification.Taxonomy, activeMatchesForTaxa);
                                ActiveMatches.compute(minScore, applyTopPercentFilterToKEGGAnalysis ? topPercent : 0, maxExpected, minPercentIdentity, readBlock, "KEGG", activeMatchesForGenes);

                                path = TaxonPathAssignment.computeTaxPath(activeMatchesForTaxa, readBlock);
                                if (doKegg) {
                                    keggW.write(readBlock.getReadName() + "; ;" + KeggTopAssignment.compute(activeMatchesForGenes, readBlock, keggRanksToReport) + "\n");
                                }
                            }

                            w.write(readBlock.getReadName() + "; ;");

                            for (Pair<Integer, Float> pair : path) {
                                final Integer taxId = pair.getFirst();
                                final String taxonName = (showTaxonIds ? "" + taxId : TaxonomyData.getName2IdMap().get(taxId));

                                if (taxonName.equals("root"))
                                    continue;

                                final int rank = TaxonomyData.getTaxonomicRank(taxId);

                                String rankName;
                                if (rank != 0) {
                                    rankName = TaxonomicLevels.getName(rank);
                                    if (rankName == null)
                                        rankName = "?";
                                } else {
                                    rankName = "?";
                                }
                                if (useOfficialRanksOnly && rankName.equals("?"))
                                    continue;

                                if (showRank && !rankName.equals("?")) {
                                    char letter = Character.toLowerCase(rankName.charAt(0));
                                    if (rank == 127) // domain
                                        letter = 'd';
                                    w.write(String.format("%c__%s; %d;", letter, taxonName, (int) (float) pair.getSecond()));
                                } else
                                    w.write(" " + taxonName + "; " + (int) (float) pair.getSecond() + ";");
                            }
                            w.write("\n");
                            totalOut++;
                            progressListener.setProgress(it.getProgress());
                        }
                    } finally {
                        if (doKegg)
                            keggW.close();
                    }
                }
                progressListener.close();
            }
            System.err.println(String.format("Reads in: %,11d", totalIn));
            System.err.println(String.format("Reads out:%,11d", totalOut));
        }
    }
}
