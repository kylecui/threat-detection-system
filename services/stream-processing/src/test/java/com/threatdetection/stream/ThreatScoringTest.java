package com.threatdetection.stream;

import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreatScoringTest {

    @Test
    void threatScoreCalculator_appliesExpectedTimeWeights() throws Exception {
        StreamProcessingJob.ThreatScoreCalculator calculator = new StreamProcessingJob.ThreatScoreCalculator();

        Tuple4<String, String, Double, Long> midnight = calculator.map(aggregationJsonAtHour(2, 1, 1, 1, 10));
        Tuple4<String, String, Double, Long> business = calculator.map(aggregationJsonAtHour(10, 1, 1, 1, 10));
        Tuple4<String, String, Double, Long> evening = calculator.map(aggregationJsonAtHour(18, 1, 1, 1, 10));
        Tuple4<String, String, Double, Long> night = calculator.map(aggregationJsonAtHour(22, 1, 1, 1, 10));

        assertEquals(12.0, midnight.f2, 0.0001);
        assertEquals(10.0, business.f2, 0.0001);
        assertEquals(9.0, evening.f2, 0.0001);
        assertEquals(8.0, night.f2, 0.0001);
    }

    @Test
    void threatScoreCalculator_appliesExpectedPortWeights() throws Exception {
        StreamProcessingJob.ThreatScoreCalculator calculator = new StreamProcessingJob.ThreatScoreCalculator();

        Tuple4<String, String, Double, Long> onePort = calculator.map(aggregationJsonAtHour(10, 1, 1, 1, 1));
        Tuple4<String, String, Double, Long> threePorts = calculator.map(aggregationJsonAtHour(10, 1, 3, 1, 1));
        Tuple4<String, String, Double, Long> tenPorts = calculator.map(aggregationJsonAtHour(10, 1, 10, 1, 1));
        Tuple4<String, String, Double, Long> twentyFivePorts = calculator.map(aggregationJsonAtHour(10, 1, 25, 1, 1));

        assertEquals(1.0, onePort.f2, 0.0001);
        assertEquals(3.6, threePorts.f2, 0.0001);
        assertEquals(15.0, tenPorts.f2, 0.0001);
        assertEquals(50.0, twentyFivePorts.f2, 0.0001);
    }

    @Test
    void threatLevelAggregator_mapsScoreBandsToExpectedLevels() throws Exception {
        StreamProcessingJob.ThreatScoreAggregator aggregator = new StreamProcessingJob.ThreatScoreAggregator();

        assertEquals("LOW", aggregateSingleScore(aggregator, 100.0));
        assertEquals("MEDIUM", aggregateSingleScore(aggregator, 200.0));
        assertEquals("HIGH", aggregateSingleScore(aggregator, 500.0));
        assertEquals("CRITICAL", aggregateSingleScore(aggregator, 1001.0));
        assertEquals("INFO", aggregateSingleScore(aggregator, 49.99));
    }

    private static String aggregateSingleScore(StreamProcessingJob.ThreatScoreAggregator aggregator, double score) throws Exception {
        ListCollector<String> out = new ListCollector<>();
        List<Tuple4<String, String, Double, Long>> input = List.of(
            Tuple4.of("cust-1:AA:BB", "cust-1", score, 1710050000000L)
        );

        WindowFunction<Tuple4<String, String, Double, Long>, String, String, TimeWindow> windowFunction = aggregator;
        windowFunction.apply("cust-1:AA:BB", new TimeWindow(0, 60_000), input, out);

        String payload = out.values.get(0);
        return extractJsonString(payload, "threatLevel");
    }

    private static String aggregationJsonAtHour(int hour, int uniqueIps, int uniquePorts, int uniqueDevices, int attackCount) {
        long timestamp = LocalDate.of(2026, 1, 1)
            .atTime(hour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

        return "{" +
            "\"customerId\":\"cust-1\"," +
            "\"attackMac\":\"AA:BB:CC\"," +
            "\"uniqueIps\":" + uniqueIps + "," +
            "\"uniquePorts\":" + uniquePorts + "," +
            "\"uniqueDevices\":" + uniqueDevices + "," +
            "\"attackCount\":" + attackCount + "," +
            "\"timestamp\":" + timestamp +
            "}";
    }

    private static String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private static class ListCollector<T> implements Collector<T> {
        private final List<T> values = new ArrayList<>();

        @Override
        public void collect(T record) {
            values.add(record);
        }

        @Override
        public void close() {
        }
    }
}
