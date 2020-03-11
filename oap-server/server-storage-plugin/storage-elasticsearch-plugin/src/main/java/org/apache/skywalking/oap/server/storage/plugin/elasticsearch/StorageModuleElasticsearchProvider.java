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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressInventoryCacheDAO;
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
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.IndexNameConverter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.MultipleFilesChangeMonitor;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.HistoryDeleteEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsInstaller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.EndpointInventoryCacheEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.NetworkAddressInventoryCacheEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.ServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.ServiceInventoryCacheEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockDAOImpl;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockInstaller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AggregationQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AlarmQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.LogQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetadataQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetricsQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileTaskLogEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileTaskQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileThreadSnapshotQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopNRecordsQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopologyQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TraceQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ttl.ElasticsearchStorageTTL;

public class StorageModuleElasticsearchProvider extends ModuleProvider {

    protected final StorageModuleElasticsearchConfig config;
    protected ElasticSearchClient elasticSearchClient;

    public StorageModuleElasticsearchProvider() {
        super();
        this.config = new StorageModuleElasticsearchConfig();
    }

    @Override
    public String name() {
        return "elasticsearch";
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
    public void prepare() throws ServiceNotProvidedException {
        if (!StringUtil.isEmpty(config.getNameSpace())) {
            config.setNameSpace(config.getNameSpace().toLowerCase());
        }
        if (config.getDayStep() > 1) {
            TimeSeriesUtils.setDAY_STEP(config.getDayStep());
        }

        if (!StringUtil.isEmpty(config.getSecretsManagementFile())) {
            MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
                10, readableContents -> {
                    final byte[] secretsFileContent = readableContents.get(0);
                    if (secretsFileContent == null) {
                        return;
                    }
                    Properties userAndPass = new Properties();
                    userAndPass.load(new ByteArrayInputStream(secretsFileContent));
                    config.setUser(userAndPass.getProperty("user"));
                    config.setPassword(userAndPass.getProperty("password"));

                    if (elasticSearchClient == null) {
                        //In the startup process, we just need to change the username/password
                    } else {
                        elasticSearchClient.connect();
                    }
                }, config.getSecretsManagementFile());
            /**
             * By leveraging the sync update check feature when startup.
             */
            monitor.start();
        }

        elasticSearchClient = new ElasticSearchClient(
            config.getClusterNodes(), config.getProtocol(), config.getTrustStorePath(), config
            .getTrustStorePass(), config.getUser(), config.getPassword(),
            indexNameConverters(config.getNameSpace(), config.isEnablePackedDownsampling())
        );

        this.registerServiceImplementation(
            IBatchDAO.class, new BatchProcessEsDAO(elasticSearchClient, config.getBulkActions(), config
                .getFlushInterval(), config.getConcurrentRequests()));
        this.registerServiceImplementation(StorageDAO.class, new StorageEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IRegisterLockDAO.class, new RegisterLockDAOImpl(elasticSearchClient));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new HistoryDeleteEsDAO(getManager(), elasticSearchClient,
                                                            new ElasticsearchStorageTTL(),
                                                            config.isEnablePackedDownsampling()
            ));

        this.registerServiceImplementation(
            IServiceInventoryCacheDAO.class, new ServiceInventoryCacheEsDAO(elasticSearchClient, config
                .getResultWindowMaxSize()));
        this.registerServiceImplementation(
            IServiceInstanceInventoryCacheDAO.class, new ServiceInstanceInventoryCacheDAO(elasticSearchClient, config
                .getResultWindowMaxSize()));
        this.registerServiceImplementation(
            IEndpointInventoryCacheDAO.class, new EndpointInventoryCacheEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            INetworkAddressInventoryCacheDAO.class, new NetworkAddressInventoryCacheEsDAO(elasticSearchClient, config
                .getResultWindowMaxSize()));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new MetricsQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            ITraceQueryDAO.class, new TraceQueryEsDAO(elasticSearchClient, config.getSegmentQueryMaxSize()));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new MetadataQueryEsDAO(elasticSearchClient, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new TopNRecordsQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(ILogQueryDAO.class, new LogQueryEsDAO(elasticSearchClient));

        this.registerServiceImplementation(
            IProfileTaskQueryDAO.class, new ProfileTaskQueryEsDAO(elasticSearchClient, config
                .getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new ProfileTaskLogEsDAO(elasticSearchClient, config
                .getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new ProfileThreadSnapshotQueryEsDAO(elasticSearchClient, config
                .getProfileTaskQueryMaxSize()));
    }

    @Override
    public void start() throws ModuleStartException {
        overrideCoreModuleTTLConfig();

        try {
            elasticSearchClient.connect();

            StorageEsInstaller installer = new StorageEsInstaller(getManager(), config);
            installer.install(elasticSearchClient);

            RegisterLockInstaller lockInstaller = new RegisterLockInstaller(elasticSearchClient);
            lockInstaller.install();
        } catch (StorageException | IOException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException | CertificateException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

    private void overrideCoreModuleTTLConfig() {
        ConfigService configService = getManager().find(CoreModule.NAME).provider().getService(ConfigService.class);
        configService.getDataTTLConfig().setRecordDataTTL(config.getRecordDataTTL());
        configService.getDataTTLConfig().setMinuteMetricsDataTTL(config.getMinuteMetricsDataTTL());
        configService.getDataTTLConfig().setHourMetricsDataTTL(config.getHourMetricsDataTTL());
        configService.getDataTTLConfig().setDayMetricsDataTTL(config.getDayMetricsDataTTL());
        configService.getDataTTLConfig().setMonthMetricsDataTTL(config.getMonthMetricsDataTTL());
    }

    public static List<IndexNameConverter> indexNameConverters(String namespace, boolean enablePackedDownsampling) {
        List<IndexNameConverter> converters = new ArrayList<>();

        if (enablePackedDownsampling) {
            // Packed downsampling converter.
            converters.add(new PackedDownsamplingConverter());
        }
        converters.add(new NamespaceConverter(namespace));
        return converters;
    }

    private static class PackedDownsamplingConverter implements IndexNameConverter {
        private final String[] removableSuffixes = new String[] {
            Const.ID_SPLIT + Downsampling.Day.getName(),
            Const.ID_SPLIT + Downsampling.Hour.getName()
        };
        private final Map<String, String> convertedIndexNames = new ConcurrentHashMap<>();

        public PackedDownsamplingConverter() {
        }

        @Override
        public String convert(final String indexName) {
            String convertedName = convertedIndexNames.get(indexName);
            if (convertedName != null) {
                return convertedName;
            }
            convertedName = indexName;
            for (final String removableSuffix : removableSuffixes) {
                String mayReplaced = indexName.replaceAll(removableSuffix, "");
                if (mayReplaced.length() != convertedName.length()) {
                    convertedName = mayReplaced;
                    break;
                }
            }
            convertedIndexNames.put(indexName, convertedName);
            return convertedName;
        }
    }

    private static class NamespaceConverter implements IndexNameConverter {
        private final String namespace;

        public NamespaceConverter(final String namespace) {
            this.namespace = namespace;
        }

        @Override
        public String convert(final String indexName) {
            if (StringUtil.isNotEmpty(namespace)) {
                return namespace + "_" + indexName;
            }

            return indexName;
        }
    }
}
