/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ttl;

import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.storage.ttl.DayTTLCalculator;
import org.apache.skywalking.oap.server.core.storage.ttl.MonthTTLCalculator;
import org.apache.skywalking.oap.server.core.storage.ttl.StorageTTL;
import org.apache.skywalking.oap.server.core.storage.ttl.TTLCalculator;

public class ElasticsearchStorageTTL implements StorageTTL {

    @Override
    public TTLCalculator metricsCalculator(DownSampling downsampling) {
        switch (downsampling) {
            case Month:
                return new MonthTTLCalculator();
            case Hour:
                return new EsHourTTLCalculator();
            case Minute:
                return new EsMinuteTTLCalculator();
            default:
                return new DayTTLCalculator();
        }
    }

    @Override
    public TTLCalculator recordCalculator() {
        return new EsRecordTTLCalculator();
    }
}
