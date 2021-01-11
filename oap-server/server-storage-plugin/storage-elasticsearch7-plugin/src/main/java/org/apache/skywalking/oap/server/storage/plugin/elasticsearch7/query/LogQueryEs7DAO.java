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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.LogState;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.apm.util.StringUtil.isNotEmpty;

public class LogQueryEs7DAO extends EsDAO implements ILogQueryDAO {
    public LogQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public boolean supportQueryLogsByKeywords() {
        return true;
    }

    @Override
    public Logs queryLogs(String metricName,
                          final String serviceId,
                          final String serviceInstanceId,
                          final String endpointId,
                          final String endpointName,
                          final TraceScopeCondition relatedTrace,
                          final LogState state,
                          final int from,
                          final int limit,
                          final long startSecondTB,
                          final long endSecondTB,
                          final List<Tag> tags,
                          final List<String> keywordsOfContent,
                          final List<String> excludingKeywordsOfContent) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        if (startSecondTB != 0 && endSecondTB != 0) {
            mustQueryList.add(QueryBuilders.rangeQuery(Record.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }

        if (isNotEmpty(serviceId)) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(AbstractLogRecord.SERVICE_ID, serviceId));
        }
        if (isNotEmpty(serviceInstanceId)) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(AbstractLogRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (isNotEmpty(endpointId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(AbstractLogRecord.ENDPOINT_ID, endpointId));
        }
        if (isNotEmpty(endpointName)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(AbstractLogRecord.ENDPOINT_NAME);
            mustQueryList.add(QueryBuilders.matchPhraseQuery(matchCName, endpointName));
        }
        if (nonNull(relatedTrace)) {
            if (isNotEmpty(relatedTrace.getTraceId())) {
                boolQueryBuilder.must()
                                .add(QueryBuilders.termQuery(AbstractLogRecord.TRACE_ID, relatedTrace.getTraceId()));
            }
            if (isNotEmpty(relatedTrace.getSegmentId())) {
                boolQueryBuilder.must().add(
                    QueryBuilders.termQuery(AbstractLogRecord.TRACE_SEGMENT_ID, relatedTrace.getSegmentId()));
            }
            if (nonNull(relatedTrace.getSpanId())) {
                boolQueryBuilder.must().add(
                    QueryBuilders.termQuery(AbstractLogRecord.SPAN_ID, relatedTrace.getSpanId()));
            }
        }

        if (LogState.ERROR.equals(state)) {
            boolQueryBuilder.must()
                            .add(
                                QueryBuilders.termQuery(AbstractLogRecord.IS_ERROR, BooleanUtils.booleanToValue(true)));
        } else if (LogState.SUCCESS.equals(state)) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(
                                AbstractLogRecord.IS_ERROR,
                                BooleanUtils.booleanToValue(false)
                            ));
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            BoolQueryBuilder tagMatchQuery = QueryBuilders.boolQuery();
            tags.forEach(tag -> tagMatchQuery.must(QueryBuilders.termQuery(AbstractLogRecord.TAGS, tag.toString())));
            mustQueryList.add(tagMatchQuery);
        }

        if (CollectionUtils.isNotEmpty(keywordsOfContent)) {
            mustQueryList.add(
                QueryBuilders.matchPhraseQuery(
                    MatchCNameBuilder.INSTANCE.build(AbstractLogRecord.CONTENT),
                    String.join(Const.SPACE, keywordsOfContent)
                ));
        }

        if (CollectionUtils.isNotEmpty(excludingKeywordsOfContent)) {
            boolQueryBuilder.mustNot(QueryBuilders.matchPhraseQuery(
                MatchCNameBuilder.INSTANCE.build(AbstractLogRecord.CONTENT),
                String.join(Const.SPACE, excludingKeywordsOfContent)
            ));
        }

        sourceBuilder.size(limit);
        sourceBuilder.from(from);
        sourceBuilder.sort(AbstractLogRecord.TIMESTAMP);

        SearchResponse response = getClient().search(metricName, sourceBuilder);

        Logs logs = new Logs();
        logs.setTotal((int) response.getHits().getTotalHits().value);

        for (SearchHit searchHit : response.getHits().getHits()) {
            Log log = new Log();
            log.setServiceId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.SERVICE_ID));
            log.setServiceInstanceId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.SERVICE_INSTANCE_ID));
            log.setEndpointId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.ENDPOINT_ID));
            log.setEndpointName((String) searchHit.getSourceAsMap().get(AbstractLogRecord.ENDPOINT_NAME));
            log.setTraceId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.TRACE_ID));
            log.setTimestamp(searchHit.getSourceAsMap().get(AbstractLogRecord.TIMESTAMP).toString());
            log.setError(BooleanUtils.valueToBoolean(((Number) searchHit.getSourceAsMap()
                                                                        .get(AbstractLogRecord.IS_ERROR)).intValue()));
            log.setContentType(ContentType.instanceOf(((Number) searchHit.getSourceAsMap()
                                                                         .get(
                                                                             AbstractLogRecord.CONTENT_TYPE)).intValue()));
            log.setContent((String) searchHit.getSourceAsMap().get(AbstractLogRecord.CONTENT));
            String dataBinaryBase64 = (String) searchHit.getSourceAsMap().get(AbstractLogRecord.DATA_BINARY);
            if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                parserDataBinary(dataBinaryBase64, log.getTags());
            }
            logs.getLogs().add(log);
        }
        return logs;
    }
}
