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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import org.apache.skywalking.oap.server.core.analysis.config.Config;
import org.apache.skywalking.oap.server.core.storage.IConfigDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author MrPro
 */
public class H2ConfigDAO extends H2SQLExecutor implements IConfigDAO {

    private JDBCHikariCPClient h2Client;
    private StorageBuilder<Config> storageBuilder;

    public H2ConfigDAO(JDBCHikariCPClient h2Client, StorageBuilder<Config> storageBuilder) {
        this.h2Client = h2Client;
        this.storageBuilder = storageBuilder;
    }

    @Override
    public void insert(Model model, Config config) throws IOException {
        try (Connection connection = h2Client.getConnection()) {
            SQLExecutor insertExecutor = getInsertExecutor(model.getName(), config, storageBuilder);
            insertExecutor.invoke(connection);
        } catch (IOException | SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
