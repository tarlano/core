/*
 * Copyright (c) 2014-2015 dCentralizedSystems, LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.dcentralized.core.common;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

/**
 * Document describing the <service>/stats REST API
 */
public class ServiceStats extends ServiceDocument {
    public static final String KIND = Utils.buildKind(ServiceStats.class);
    public static final String STAT_NAME_SUFFIX_PER_DAY = "PerDay";
    public static final String STAT_NAME_SUFFIX_PER_HOUR = "PerHour";

    public static class ServiceStatLogHistogram {
        /**
         * Each bin tracks a power of 10. Bin[0] tracks all values between 0 and 9, Bin[1] tracks
         * values between 10 and 99, Bin[2] tracks values between 100 and 999, and so forth
         */
        public long[] bins = new long[15];
    }

    /**
     * Data structure representing time series data
     * Users can specify the number of bins to be stored and the granularity of the bins
     * Any data to be stored will be normalized to an UTC boundary based on the the data granularity
     * If a bin already exists for that timestamp, the existing value is updated based on the
     * specified AggregationType
     * If the number of bins equals the configured number of bins, the oldest bin will be dropped
     * on any further insertion
     */
    public static class TimeSeriesStats {

        public static final int DEFAULT_ROUNDING_FACTOR = 10000000;

        public static class TimeBin {
            public Double avg;
            public Double var;
            public double count;
        }

        public static class ExtendedTimeBin extends TimeBin {
            public Double min;
            public Double max;
            public Double sum;
            public Double latest;
        }

        public enum AggregationType {
            AVG, MIN, MAX, SUM, LATEST
        }

        public static final EnumSet<AggregationType> AGG_AVG_INSTANCE = EnumSet
                .of(AggregationType.AVG);

        public static final EnumSet<AggregationType> AGG_SUM_INSTANCE = EnumSet
                .of(AggregationType.SUM);

        public static final EnumSet<AggregationType> AGG_ALL_INSTANCE = EnumSet
                .allOf(AggregationType.class);

        public SortedMap<Long, TimeBin> bins;
        public int numBins;
        public long binDurationMillis;
        public EnumSet<AggregationType> aggregationType;
        public int roundingFactor = DEFAULT_ROUNDING_FACTOR;

        public TimeSeriesStats() {
            // no-op
        }

        public TimeSeriesStats(int numBins, long binDurationMillis,
                EnumSet<AggregationType> aggregationType) {
            this.numBins = numBins;
            this.binDurationMillis = binDurationMillis;
            this.bins = new ConcurrentSkipListMap<>();
            this.aggregationType = aggregationType;
        }

        public void add(long timestampMicros, double value, double delta) {
            synchronized (this) {
                long binId = normalizeTimestamp(timestampMicros, this.binDurationMillis);
                TimeBin dataBin = null;
                dataBin = this.bins.get(binId);
                if (dataBin == null) {
                    if (this.bins.size() == this.numBins) {
                        if (this.bins.firstKey() > timestampMicros) {
                            // incoming data is too old; ignore
                            return;
                        }
                        // remove the oldest entry
                        this.bins.remove(this.bins.firstKey());
                    }
                    if (this.aggregationType.contains(AggregationType.SUM)
                            || this.aggregationType.contains(AggregationType.MAX)
                            || this.aggregationType.contains(AggregationType.MIN)
                            || this.aggregationType.contains(AggregationType.LATEST)) {
                        dataBin = new ExtendedTimeBin();
                    } else {
                        dataBin = new TimeBin();
                    }
                    this.bins.put(binId, dataBin);
                }
                if (this.aggregationType.contains(AggregationType.AVG)) {
                    if (dataBin.avg == null) {
                        dataBin.avg = value;
                        dataBin.var = 0.0;
                        dataBin.count = 1;
                    } else {
                        // Use Welford's algorithm for online computation of average and variance
                        // see https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
                        dataBin.count++;
                        double diff = value - dataBin.avg;
                        dataBin.avg += diff / dataBin.count;
                        double diffAfter = value - dataBin.avg;
                        dataBin.var += diff * diffAfter;
                    }
                }
                if (this.aggregationType.contains(AggregationType.SUM)) {
                    ExtendedTimeBin exBin = (ExtendedTimeBin) dataBin;
                    if (exBin.sum == null) {
                        exBin.sum = delta;
                    } else {
                        exBin.sum += delta;
                    }
                }
                if (this.aggregationType.contains(AggregationType.MAX)) {
                    ExtendedTimeBin exBin = (ExtendedTimeBin) dataBin;
                    if (exBin.max == null) {
                        exBin.max = value;
                    } else if (exBin.max < value) {
                        exBin.max = value;
                    }
                }
                if (this.aggregationType.contains(AggregationType.MIN)) {
                    ExtendedTimeBin exBin = (ExtendedTimeBin) dataBin;
                    if (exBin.min == null) {
                        exBin.min = value;
                    } else if (exBin.min > value) {
                        exBin.min = value;
                    }
                }
                if (this.aggregationType.contains(AggregationType.LATEST)) {
                    ExtendedTimeBin exBin = (ExtendedTimeBin) dataBin;
                    exBin.latest = value;
                }
            }
        }

        /**
         * This method normalizes the input timestamp at UTC time boundaries
         *  based on the bin size, effectively creating time series that are comparable to each other
         */
        public static long normalizeTimestamp(long timestampMicros, long binDurationMillis) {
            long timeMillis = TimeUnit.MICROSECONDS.toMillis(timestampMicros);
            timeMillis -= (timeMillis % binDurationMillis);
            return timeMillis;
        }

    }

    public static class ServiceStat {
        public static final String FIELD_NAME_NAME = "name";

        public static final String FIELD_NAME_VERSION = "version";

        public static final String FIELD_NAME_LAST_UPDATE_TIME_MICROS_UTC = "lastUpdateMicrosUtc";

        public static final String FIELD_NAME_LATEST_VALUE = "latestValue";

        public static final String FIELD_NAME_UNIT = "unit";

        /**
         * Name of the stat.
         */
        public String name;

        /**
         * Latest value of the stat.
         */
        public double latestValue;

        /**
         * The value accumulated over time for the stat.
         */
        public double accumulatedValue;

        /**
         * The stat document version.
         */
        public long version;

        /**
         * Time, in microseconds since UNIX epoch, the stat was received at the service.
         */
        public long lastUpdateMicrosUtc;

        /**
         * The unit of measurement associated with the stat.
         */
        public String unit;

        /**
         * Time, in microseconds since UNIX epoch, the data value was acquired at the source.
         */
        public Long sourceTimeMicrosUtc;

        /**
         * Source (provider) for this stat
         */
        public URI serviceReference;

        /**
         *  histogram of logarithmic time scale
         */
        public ServiceStatLogHistogram logHistogram;

        /**
         * time series data. If set,  the stat value is
         * maintained over time. Users can choose the number
         * of samples to maintain and the time window into
         * which each datapoint falls. If more than one entry
         * exists, aggregates (average, minimum, maximum; based
         * on user choice) are maintained
         */
        public TimeSeriesStats timeSeriesStats;
    }

    public String kind = KIND;

    public Map<String, ServiceStat> entries = new HashMap<>();

}
