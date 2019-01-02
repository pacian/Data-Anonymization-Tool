/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2016 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.risk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.deidentifier.arx.DataHandleInternal;
import org.deidentifier.arx.common.WrappedBoolean;
import org.deidentifier.arx.common.WrappedInteger;
import org.deidentifier.arx.exceptions.ComputationInterruptedException;
import org.deidentifier.arx.risk.msu.SUDA2;
import org.deidentifier.arx.risk.msu.SUDA2ProgressListener;
import org.deidentifier.arx.risk.msu.SUDA2Statistics;

/**
 * A risk model based on MSUs in the data set
 * @author Fabian Prasser
 */
public class RiskModelMSU {

    /** Progress stuff */
    private final WrappedInteger progress;
    /** Progress stuff */
    private final WrappedBoolean stop;
    /** Maximal size of keys considered */
    private final int            maxKeyLength;
    /** The number of keys */
    private final long           numKeys;
    /** The average size of keys */
    private final double         averageKeySize;
    /** Contributions of each column */
    private final double[]       columnContributions;
    /** Distribution of sizes of keys */
    private final double[]       columnAverageKeySize;
    /** Contributions of each column */
    private final double[]       sizeDistribution;
    /** Attributes */
    private final String[]       attributes;

    /**
     * Creates a new instance
     * @param handle
     * @param identifiers
     * @param stop 
     * @param progress 
     * @param maxKeyLength
     */
    RiskModelMSU(DataHandleInternal handle, 
                 Set<String> identifiers, 
                 WrappedInteger progress, 
                 WrappedBoolean stop,
                 int maxKeyLength) {

        // Store
        this.stop = stop;
        this.progress = progress;
        
        // Add all attributes, if none were specified
        if (identifiers == null || identifiers.isEmpty()) {
            identifiers = new HashSet<String>();
            for (int column = 0; column < handle.getNumColumns(); column++) {
                identifiers.add(handle.getAttributeName(column));
            }
        }
        
        // Build column array
        int[] columns = getColumns(handle, identifiers);
        attributes = getAttributes(handle, columns);
        
        // Update progress
        progress.value = 10;
        checkInterrupt();
        
        // Do something
        SUDA2 suda2 = new SUDA2(handle.getDataArray(columns).getArray());
        suda2.setProgressListener(new SUDA2ProgressListener() {
            @Override
            public void update(double progress) {
                RiskModelMSU.this.progress.value = 10 + (int)(progress * 90d);
            }
        });
        suda2.setStopFlag(stop);
        SUDA2Statistics result = suda2.getStatistics(maxKeyLength);
        this.maxKeyLength = result.getMaxKeyLength();
        this.numKeys = result.getNumKeys();
        this.columnContributions = result.getColumnKeyContributions();
        this.sizeDistribution = result.getKeySizeDistribution();
        this.columnAverageKeySize = result.getColumnAverageKeySize();
        this.averageKeySize = result.getAverageKeySize();
    }
    
    /**
     * Returns the attributes addressed by the statistics
     * @return
     */
    public String[] getAttributes() {
        return attributes;
    }

    /**
     * Returns the average key size
     * @return
     */
    public double getAverageKeySize() {
        return averageKeySize;
    }

    /**
     * Returns the average key size per column
     * @return 
     */
    public double[] getColumnAverageKeySize() {
        return columnAverageKeySize;
    }

    /**
     * Returns the contribution of each columns to the total SUDA score.
     * Calculated as described in: IHSN - STATISTICAL DISCLOSURE CONTROL FOR MICRODATA: A PRACTICE GUIDE
     * @return the columnKeyContributions
     */
    public double[] getColumnKeyContributions() {
        return columnContributions;
    }

    /**
     * Returns the distribution of sizes of keys found
     * @return
     */
    public double[] getKeySizeDistribution() {
        return sizeDistribution;
    }

    /**
     * Returns the maximal length of keys searched for
     * @return
     */
    public int getMaxKeyLength() {
        return maxKeyLength;
    }
    
    /**
     * Returns the number of keys found
     * @return
     */
    public long getNumKeys() {
        return numKeys;
    }

    /**
     * Checks for interrupts
     */
    private void checkInterrupt() {
        if (stop.value) { throw new ComputationInterruptedException(); }
    }

    /**
     * Returns the column array
     * @param handle
     * @param columns
     * @return
     */
    private String[] getAttributes(DataHandleInternal handle, int[] columns) {
        String[] result = new String[columns.length];
        int index = 0;
        for (int column : columns) {
            result[index++] = handle.getAttributeName(column);
        }
        return result;
    }

    /**
     * Returns the column array
     * @param handle
     * @param identifiers
     * @return The columns in ascending order
     */
    private int[] getColumns(DataHandleInternal handle, Set<String> identifiers) {
        int[] result = new int[identifiers.size()];
        int index = 0;
        for (String attribute : identifiers) {
            int column = handle.getColumnIndexOf(attribute);
            if (column == -1) {
                throw new IllegalArgumentException("Unknown attribute '" + attribute+"'");
            }
            result[index++] = column;
        }
        Arrays.sort(result);
        return result;
    }
}
