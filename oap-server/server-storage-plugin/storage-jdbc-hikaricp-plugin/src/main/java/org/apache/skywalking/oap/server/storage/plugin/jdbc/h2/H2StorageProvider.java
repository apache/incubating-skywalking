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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2;

import java.util.Properties;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.ttl.GeneralStorageTTL;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2AggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2AlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2LogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressAliasCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2RegisterLockDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2RegisterLockInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TraceQueryDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H2 Storage provider is for demonstration and preview only. I will find that haven't implemented several interfaces,
 * because not necessary, and don't consider about performance very much.
 * <p>
 * If someone wants to implement SQL-style database as storage, please just refer the logic.
 */
public class H2StorageProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(H2StorageProvider.class);

    private H2StorageConfig config;
    private JDBCHikariCPClient h2Client;
    private H2RegisterLockDAO lockDAO;

    public H2StorageProvider() {
        config = new H2StorageConfig();
    }

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        Properties settings = new Properties();
        settings.setProperty("dataSourceClassName", config.getDriver());
        settings.setProperty("dataSource.url", config.getUrl());
        settings.setProperty("dataSource.user", config.getUser());
        settings.setProperty("dataSource.password", config.getPassword());
        h2Client = new JDBCHikariCPClient(settings);

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(h2Client));
        this.registerServiceImplementation(StorageDAO.class, new H2StorageDAO(h2Client));

        lockDAO = new H2RegisterLockDAO(h2Client);
        this.registerServiceImplementation(IRegisterLockDAO.class, lockDAO);

        this.registerServiceImplementation(IServiceInventoryCacheDAO.class, new H2ServiceInventoryCacheDAO(h2Client));
        this.registerServiceImplementation(
            IServiceInstanceInventoryCacheDAO.class, new H2ServiceInstanceInventoryCacheDAO(h2Client));
        this.registerServiceImplementation(
            INetworkAddressAliasCacheDAO.class, new H2NetworkAddressAliasCacheDAO(h2Client));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(h2Client));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new H2MetricsQueryDAO(h2Client));
        this.registerServiceImplementation(ITraceQueryDAO.class, new H2TraceQueryDAO(h2Client));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new H2MetadataQueryDAO(h2Client, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new H2AggregationQueryDAO(h2Client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new H2AlarmQueryDAO(h2Client));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(getManager(), h2Client, new GeneralStorageTTL()));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(h2Client));
        this.registerServiceImplementation(ILogQueryDAO.class, new H2LogQueryDAO(h2Client));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(h2Client));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(h2Client));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new H2ProfileThreadSnapshotQueryDAO(h2Client));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        try {
            h2Client.connect();

            H2TableInstaller installer = new H2TableInstaller(getManager());
            installer.install(h2Client);

            new H2RegisterLockInstaller().install(h2Client, lockDAO);
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
