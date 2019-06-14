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

package org.apache.skywalking.oap.server.configuration.etcd;

import com.google.gson.Gson;
import java.net.URI;
import java.util.List;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import org.apache.skywalking.oap.server.cluster.plugin.etcd.ClusterModuleEtcdConfig;
import org.apache.skywalking.oap.server.cluster.plugin.etcd.EtcdCoordinator;
import org.apache.skywalking.oap.server.cluster.plugin.etcd.EtcdEndpoint;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Alan Lau
 */
public class ITClusterEtcdPluginTest {

    private ClusterModuleEtcdConfig etcdConfig;

    private EtcdClient client;

    private EtcdCoordinator coordinator;

    private Gson gson = new Gson();

    private Address remoteAddress = new Address("10.0.0.1", 1000, false);
    private Address selfRemoteAddress = new Address("10.0.0.2", 1001, true);

    private Address internalAddress = new Address("10.0.0.3", 1002, false);

    private static final String SERVICE_NAME = "my-service";

    @Before
    public void before() throws Exception {
        etcdConfig = new ClusterModuleEtcdConfig();
        etcdConfig.setServiceName(SERVICE_NAME);
        client = new EtcdClient(URI.create("http://127.0.0.1:2379"));
        coordinator = new EtcdCoordinator(etcdConfig, client);
    }

    @After
    public void after() throws Exception {
        client.close();
    }

    @Test
    public void registerRemote() throws Throwable {
        registerRemote(remoteAddress);
        clear(remoteAddress);
    }

    @Test
    public void registerSelfRemote() throws Throwable {
        registerRemote(selfRemoteAddress);
        clear(selfRemoteAddress);
    }

    @Test
    public void registerRemoteUsingInternal() throws Throwable {
        etcdConfig.setInternalComHost(internalAddress.getHost());
        etcdConfig.setInternalComPort(internalAddress.getPort());
        etcdConfig.setServiceName(SERVICE_NAME);
        registerRemote(internalAddress);
        clear(internalAddress);
    }

    @Test
    public void queryRemoteNodes() throws Throwable {
        registerRemote(selfRemoteAddress);
        List<RemoteInstance> remoteInstances = coordinator.queryRemoteNodes();
        assertEquals(1, remoteInstances.size());

        RemoteInstance selfInstance = remoteInstances.get(0);
        velidate(selfRemoteAddress, selfInstance);
        clear(selfRemoteAddress);
    }

    private void velidate(Address originArress, RemoteInstance instance) {
        Address instanceAddress = instance.getAddress();
        assertEquals(originArress.getHost(), instanceAddress.getHost());
        assertEquals(originArress.getPort(), instanceAddress.getPort());
    }

    private void registerRemote(Address address) {
        coordinator.registerRemote(new RemoteInstance(address));
        EtcdEndpoint endpoint = afterRegister();
        verifyRegistration(address, endpoint);
    }

    private EtcdEndpoint afterRegister() {
        List<RemoteInstance> list = coordinator.queryRemoteNodes();
        assertEquals(list.size(), 1L);
        return buildEndpoint(list.get(0));
    }

    private void clear(Address address) throws Throwable {
        String dir = new StringBuilder("/").append(SERVICE_NAME).append("/").append(address.getHost()).toString();
        EtcdResponsePromise promise = client.deleteDir(dir).dir().send();
        promise.get();
    }

    private void verifyRegistration(Address remoteAddress, EtcdEndpoint endpoint) {
        assertNotNull(endpoint);
        assertEquals(SERVICE_NAME, endpoint.getServiceName());
        assertEquals(remoteAddress.getHost(), endpoint.getHost());
        assertEquals(remoteAddress.getPort(), endpoint.getPort());
    }

    private EtcdEndpoint buildEndpoint(RemoteInstance instance) {
        Address address = instance.getAddress();
        EtcdEndpoint endpoint = new EtcdEndpoint.Builder().host(address.getHost()).port(address.getPort()).serviceName(SERVICE_NAME).build();
        return endpoint;
    }

}
