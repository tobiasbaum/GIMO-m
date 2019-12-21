package de.unihannover.reviews.mining.interaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;

import de.unihannover.reviews.mining.common.Blackboard.RecordsAndRemarks;
import de.unihannover.reviews.mining.common.ChangePartId;
import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.RecordScheme;
import de.unihannover.reviews.mining.common.Util;

public class SimulationProxy implements AutoCloseable {

    private static final class StrategySeedMap {
        private final Map<String, Map<Integer, Double>> data = new LinkedHashMap<>();

        public void put(String strategy, int seed, double value) {
            Map<Integer, Double> stratMap = this.data.get(strategy);
            if (stratMap == null) {
                stratMap = new LinkedHashMap<>();
                this.data.put(strategy, stratMap);
            }
            stratMap.put(seed, value);
        }

        public Set<Integer> getSeeds() {
            return this.data.values().iterator().next().keySet();
        }

        public Set<String> getStrategies() {
            return this.data.keySet();
        }

        public double getMaxPerSeed(Integer seed) {
            double max = Double.MIN_VALUE;
            for (final Map<Integer, Double> m : this.data.values()) {
                final double val = m.get(seed);
                max = Math.max(max, val);
            }
            return max;
        }

        public double get(String strategy, int seed) {
            return this.data.get(strategy).get(seed);
        }

        public Map<String, Double> trimmedMeansPerStrategy() {
            final Map<String, Double> ret = new LinkedHashMap<>();
            for (final Entry<String, Map<Integer, Double>> e : this.data.entrySet()) {
                ret.put(e.getKey(), Util.trimmedMeanDbl(new ArrayList<>(e.getValue().values())));
            }
            return ret;
        }
    }

    public static interface SimulationResult {

        public abstract List<String> getSettingNames();

        public abstract String getSettingValue(String name);

        public abstract double getDiffToBest(String strategy);

    }

    public static final class SimulationResultAdapter implements SimulationResult {

        private final RecordsAndRemarks rr;
        private final Record record;

        private SimulationResultAdapter(Record record2, RecordsAndRemarks rr2) {
            this.record = record2;
            this.rr = rr2;
        }

        public static SimulationResult forRecord(Record record, RecordsAndRemarks rr) {
            return new SimulationResultAdapter(record, rr);
        }

        @Override
        public double getDiffToBest(String strategy) {
            return this.rr.getResultData().getDiffToBest(this.record.getId(), strategy);
        }

        @Override
        public List<String> getSettingNames() {
            final List<String> settings = new ArrayList<>();
            final RecordScheme scheme = this.rr.getRecords().getScheme();
            for (int i = 0; i < scheme.getAllColumnCount(); i++) {
                final String name = scheme.getName(i);
                if (name.equals("id") || name.startsWith("largeStratFor.") || name.startsWith("smallStratFor.")) {
                    continue;
                }
                settings.add(name);
            }
            return settings;
        }

        @Override
        public String getSettingValue(String name) {
            final RecordScheme scheme = this.rr.getRecords().getScheme();
            final int i = scheme.getAbsIndex(name);
            if (scheme.isNumeric(i)) {
                return Double.toString(this.record.getValueDbl(scheme.toNumericIndex(i)));
            } else {
                return this.record.getValueStr(scheme.toStringIndex(i));
            }
        }

    }


    public static final class OnDemandSimulationResult implements SimulationResult {

        private final Map<String, String> settings;
        private final StrategySeedMap relData;

        public OnDemandSimulationResult(Map<String, String> settings, StrategySeedMap data) {
            this.settings = settings;

            this.relData = new StrategySeedMap();
            for (final Integer seed : data.getSeeds()) {
                final double max = data.getMaxPerSeed(seed);
                for (final String strategy : data.getStrategies()) {
                    this.relData.put(strategy, seed, data.get(strategy, seed) / max);
                }
            }
        }

        @Override
        public List<String> getSettingNames() {
            return new ArrayList<>(this.settings.keySet());
        }

        @Override
        public String getSettingValue(String name) {
            return this.settings.get(name);
        }

        @Override
        public double getDiffToBest(String strategy) {
            final Map<String, Double> trimmedMeans = this.relData.trimmedMeansPerStrategy();
            double max = Double.MIN_VALUE;
            for (final Double d : trimmedMeans.values()) {
                max = Math.max(max, d);
            }
            return max - trimmedMeans.get(strategy);
        }

    }


    private final Map<Map<String, String>, ChangePartId> ids = new HashMap<>();
    private final Map<ChangePartId, Map<String, String>> params = new HashMap<>();
    private final Map<ChangePartId, SimulationResult> results = new HashMap<>();

    private final Connection connection;
    private final Session session;
    private final MessageProducer workProducer;
    private final MessageConsumer resultConsumer;

    private int nextId;

    public SimulationProxy(
                    Connection connection2, Session session2, MessageProducer workProducer2, MessageConsumer resultConsumer2) {
        this.connection = connection2;
        this.session = session2;
        this.workProducer = workProducer2;
        this.resultConsumer = resultConsumer2;
    }

    public static SimulationProxy create(String mqUrl) throws JMSException {
        final ActiveMQConnectionFactory connFactory = new ActiveMQConnectionFactory(mqUrl);
        final ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(0);
        connFactory.setPrefetchPolicy(prefetchPolicy);
        final Connection connection = connFactory.createConnection();
        connection.start();
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageProducer workProducer = session.createProducer(session.createQueue("workQueue"));
        final MessageConsumer resultConsumer = session.createConsumer(session.createQueue("resultQueue"));

        return new SimulationProxy(connection, session, workProducer, resultConsumer);
    }

    @Override
    public void close() {
        try {
            this.workProducer.close();
            this.resultConsumer.close();
            this.connection.close();
        } catch (final JMSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Determines the result for the given parameters. Starts the simulation if the result
     * is not yet available. Returns null if there is no result yet.
     */
    public synchronized SimulationResult determineResult(RecordsAndRemarks records, Map<String, String> params) {
        this.checkForNewResults();
        ChangePartId id = this.ids.get(params);
        if (id != null) {
            final SimulationResult result = this.results.get(id);
            if (result != null) {
                return result;
            }
            return SimulationResultAdapter.forRecord(this.getRecordFor(records, id), records);
        }
        id = this.getIdFor(records, params);
        this.params.put(id, new LinkedHashMap<>(params));
        this.ids.put(this.params.get(id), id);
        final Record r = this.getRecordFor(records, id);
        if (r != null) {
            return SimulationResultAdapter.forRecord(r, records);
        }
        this.startSimulation(id, params);
        return null;
    }

    private void checkForNewResults() {
        Message m;
        try {
            while ((m = this.resultConsumer.receiveNoWait()) != null) {
                final TextMessage tm = (TextMessage) m;
                final StrategySeedMap data = new StrategySeedMap();
                try (BufferedReader r = new BufferedReader(new StringReader(tm.getText()))) {
                    String line = r.readLine();
                    while ((line = r.readLine()) != null) {
                        final String[] parts = line.split(";");
                        final int seed = Integer.parseInt(parts[1]);
                        final String strategy = parts[2].intern();
                        final double value = Double.parseDouble(parts[3]);
                        data.put(strategy, seed, value);
                    }
                }
                final ChangePartId id = new ChangePartId(Integer.parseInt(tm.getStringProperty("msgId")));
                this.results.put(id, new OnDemandSimulationResult(this.params.get(id), data));
            }
        } catch (JMSException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ChangePartId getIdFor(RecordsAndRemarks records, Map<String, String> params) {
        int maxId = this.nextId;
        final Predicate<Record> p = this.toEqualValuesPredicate(records.getRecords().getScheme(), params);
        for (final Record r : records.getRecords().getRecords()) {
            if (p.test(r)) {
                return r.getId();
            }
            maxId = Math.max(maxId, r.getId().getId());
        }
        this.nextId = maxId + 1;
        return new ChangePartId(maxId + 1);
    }

    private Predicate<Record> toEqualValuesPredicate(RecordScheme recordScheme, Map<String, String> params) {
        Predicate<Record> p = (Record x) -> true;
        for (final Entry<String, String> e : params.entrySet()) {
            final int idx = recordScheme.getAbsIndex(e.getKey());
            if (recordScheme.isNumeric(idx)) {
                final double val = Double.parseDouble(e.getValue());
                final int numIdx = recordScheme.toNumericIndex(idx);
                p = p.and((Record r) -> r.getValueDbl(numIdx) == val);
            } else {
                final String val = e.getValue();
                final int strIdx = recordScheme.toStringIndex(idx);
                p = p.and((Record r) -> r.getValueStr(strIdx).equals(val));
            }
        }
        return p;
    }

    private Record getRecordFor(RecordsAndRemarks records, ChangePartId id) {
        for (final Record r : records.getRecords().getRecords()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private void startSimulation(ChangePartId id, Map<String, String> params) {
        final StringBuilder paramCsv = new StringBuilder();
        paramCsv.append(String.join(";", params.keySet())).append('\n');
        paramCsv.append(String.join(";", params.values())).append('\n');

        try {
            final TextMessage msg = this.session.createTextMessage(paramCsv.toString());
            msg.setStringProperty("msgId", Integer.toString(id.getId()));
            this.workProducer.send(msg);
        } catch (final JMSException e) {
            throw new RuntimeException(e);
        }
    }

}
