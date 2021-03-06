/*
 *  Copyright (C) 2015 Daniel H. Huson
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
package megan.algorithms;

import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.data.ClassificationFullTree;
import megan.data.IMatchBlock;
import megan.data.IReadBlock;

import java.util.Arrays;
import java.util.BitSet;

/**
 * computes the assignment for a read, using the LCA algorithm that covers a given percentage of all matches
 * Daniel Huson, 7.2014
 */
public class AssignmentUsingLCA implements IAssignmentAlgorithm {
    private String[] addresses;

    private final BitSet toRemove;

    private final String cName;
    private final ClassificationFullTree tree;

    /**
     * constructor
     */
    public AssignmentUsingLCA(String cName) {
        this.cName = cName;
        tree = ClassificationManager.get(cName, true).getFullTree();
        addresses = new String[1000];
        toRemove = new BitSet();
    }

    /**
     * determine the taxon id of a read from its matches
     *
     * @param activeMatches
     * @param readBlock
     * @return taxon id
     */
    public int computeId(BitSet activeMatches, IReadBlock readBlock) {
        if (readBlock.getNumberOfMatches() == 0)
            return IdMapper.NOHITS_ID;

        // compute addresses of all hit taxa:
        if (activeMatches.cardinality() > 0) {

            boolean hasDisabledMatches = false;

            // collect the addresses of all non-disabled taxa:
            int numberOfAddresses = 0;
            for (int i = activeMatches.nextSetBit(0); i != -1; i = activeMatches.nextSetBit(i + 1)) {
                final IMatchBlock matchBlock = readBlock.getMatchBlock(i);
                final int id = matchBlock.getId(cName);
                if (id > 0) {
                    String address = tree.getAddress(id);
                    if (address != null) {
                        if (numberOfAddresses >= addresses.length) {
                            String[] tmp = new String[2 * addresses.length];
                            System.arraycopy(addresses, 0, tmp, 0, addresses.length);
                            addresses = tmp;
                        }
                        addresses[numberOfAddresses++] = address;
                    }
                } else
                    hasDisabledMatches = true;
            }

            // if there only matches to disabled taxa, then use them:
            if (numberOfAddresses == 0 && hasDisabledMatches) {
                for (int i = activeMatches.nextSetBit(0); i != -1; i = activeMatches.nextSetBit(i + 1)) {
                    final IMatchBlock matchBlock = readBlock.getMatchBlock(i);
                    final int id = matchBlock.getId(cName);
                    if (id > 0) {
                        String address = tree.getAddress(id);
                        if (address != null) {
                            if (numberOfAddresses >= addresses.length) {
                                String[] tmp = new String[2 * addresses.length];
                                System.arraycopy(addresses, 0, tmp, 0, addresses.length);
                                addresses = tmp;
                            }
                            addresses[numberOfAddresses++] = address;
                        }
                    }
                }
            }

            if (true) { // todo: fix this
                Arrays.sort(addresses, 0, numberOfAddresses);
                // determine nested addresses:
                toRemove.clear();
                for (int i = 0; i < numberOfAddresses - 1; i++) {
                    if (addresses[i + 1].startsWith(addresses[i]))
                        toRemove.set(i);
                }
                // remove them:
                if (toRemove.cardinality() > 0) {
                    int pos = 0;
                    for (int i = 0; i < numberOfAddresses; i++) {
                        if (!toRemove.get(i)) {
                            addresses[pos++] = addresses[i];
                        }
                    }
                    numberOfAddresses = pos;
                }
            }

            // compute LCA using addresses:
            if (numberOfAddresses > 0) {
                final String address = LCAAddressing.getCommonPrefix(addresses, numberOfAddresses);
                final int id = tree.getAddress2Id(address);
                if (id > 0) {
                    return id;
                }
            }
        }
        // although we had some hits, couldn't make an assignment
        return IdMapper.UNASSIGNED_ID;
    }
}

