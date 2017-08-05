package org.skywalking.apm.collector.agentstream.worker.noderef.summary.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.define.NodeRefTable;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.define.NodeRefSumTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class NodeRefSumEsDAO extends EsDAO implements INodeRefSumDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        GetResponse getResponse = getClient().prepareGet(NodeRefSumTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Data data = dataDefine.build(id);
            Map<String, Object> source = getResponse.getSource();
            data.setDataLong(0, (Long)source.get(NodeRefSumTable.COLUMN_ONE_SECOND_LESS));
            data.setDataLong(1, (Long)source.get(NodeRefSumTable.COLUMN_THREE_SECOND_LESS));
            data.setDataLong(2, (Long)source.get(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS));
            data.setDataLong(3, (Long)source.get(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER));
            data.setDataLong(4, (Long)source.get(NodeRefSumTable.COLUMN_ERROR));
            data.setDataLong(5, (Long)source.get(NodeRefSumTable.COLUMN_SUMMARY));
            data.setDataLong(6, (Long)source.get(NodeRefSumTable.COLUMN_TIME_BUCKET));
            data.setDataString(1, (String)source.get(NodeRefSumTable.COLUMN_AGG));
            return data;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, data.getDataLong(0));
        source.put(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, data.getDataLong(1));
        source.put(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, data.getDataLong(2));
        source.put(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, data.getDataLong(3));
        source.put(NodeRefSumTable.COLUMN_ERROR, data.getDataLong(4));
        source.put(NodeRefSumTable.COLUMN_SUMMARY, data.getDataLong(5));
        source.put(NodeRefSumTable.COLUMN_AGG, data.getDataString(1));
        source.put(NodeRefSumTable.COLUMN_TIME_BUCKET, data.getDataLong(6));

        return getClient().prepareIndex(NodeRefSumTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, data.getDataLong(0));
        source.put(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, data.getDataLong(1));
        source.put(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, data.getDataLong(2));
        source.put(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, data.getDataLong(3));
        source.put(NodeRefSumTable.COLUMN_ERROR, data.getDataLong(4));
        source.put(NodeRefSumTable.COLUMN_SUMMARY, data.getDataLong(5));
        source.put(NodeRefSumTable.COLUMN_AGG, data.getDataString(1));
        source.put(NodeRefSumTable.COLUMN_TIME_BUCKET, data.getDataLong(6));

        return getClient().prepareUpdate(NodeRefTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
