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
 *
 */

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricGraph {

    private final ModuleManager moduleManager;

    public ApplicationMetricGraph(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        GraphManager.INSTANCE.createIfAbsent(GraphIdDefine.APPLICATION_METRIC_GRAPH_ID, ApplicationReferenceMetric.class)
            .addNode(new ApplicationMetricAggregationWorker.Factory(moduleManager).create(null))
            .addNext(new ApplicationMetricRemoteWorker.Factory(moduleManager, remoteSenderService, GraphIdDefine.APPLICATION_METRIC_GRAPH_ID).create(null))
            .addNext(new ApplicationMetricPersistenceWorker.Factory(moduleManager).create(null));
    }
}
