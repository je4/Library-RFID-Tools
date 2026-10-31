package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.WriteAfiResponseData;
import org.objectspace.rfid.headless.payload.WriteTagResponseData;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class WsBrokerBatchWriteTest {

    private static JsonNode payloadData(Event<?> evt) {
        return evt.getDataNode();
    }

    @Test
    @DisplayName("Test multi-UID batch write AFI when all transponders succeed")
    void testBatchWriteAfiAllSuccess() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> writtenUids = new CopyOnWriteArrayList<>();
        AtomicInteger exclusiveLocks = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                assertEquals((byte) 0x07, afi);
                writtenUids.add(uid);
            }
            @Override public void close() {}
        };

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, null, 100, 4) {
            @Override
            public <T> T executeExclusive(ScannerTask<T> task) throws Exception {
                exclusiveLocks.incrementAndGet();
                return super.executeExclusive(task);
            }
        };

        dispatcher.setReader(mockReader);
        dispatcher.setScanner(scanner);

        List<String> requestedUids = List.of("E004010001", "E004010002", "E004010003");
        WriteAfiResponseData resp = dispatcher.writeAfi(requestedUids, "07", "web-ui", "req-batch-all-ok", "ref-batch-001").join();

        assertEquals("ok", resp.status());
        assertTrue(resp.isSuccess());
        assertFalse(resp.isPartial());
        assertEquals("07", resp.afi());
        assertEquals(requestedUids, resp.uids());
        assertEquals(requestedUids, resp.successfulUids());
        assertTrue(resp.failedUids() == null || resp.failedUids().isEmpty());
        assertEquals(3, resp.results().size());
        assertTrue(resp.results().stream().allMatch(WriteAfiResponseData.TagAfiResult::isSuccess));
        assertEquals(3, writtenUids.size());
        assertEquals(1, exclusiveLocks.get(), "Batch write should acquire exclusive scanner lock exactly once for all UIDs");

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("ref-batch-001", reply.getRefId());
        assertEquals("rfid:write_afi_response", reply.getDataNode().get("event").asText());
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(3, payloadData(reply).get("uids").size());
        assertEquals(3, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-UID batch write AFI with partial failure reports exact successes and errors")
    void testBatchWriteAfiPartialFailure() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                if ("E00401-LOCKED".equals(uid)) {
                    throw new Exception("Tag AFI lock bit set (ISO error 0x12)");
                }
            }
            @Override public void close() {}
        };

        dispatcher.setReader(mockReader);

        List<String> requestedUids = List.of("E00401-OK1", "E00401-LOCKED", "E00401-OK2");
        WriteAfiResponseData resp = dispatcher.writeAfi(requestedUids, "0xC2", "web-ui", "req-batch-partial", "ref-partial-002").join();

        assertEquals("partial", resp.status());
        assertFalse(resp.isSuccess());
        assertTrue(resp.isPartial());
        assertEquals("C2", resp.afi());
        assertEquals(requestedUids, resp.uids());
        assertEquals(List.of("E00401-OK1", "E00401-OK2"), resp.successfulUids());
        assertNotNull(resp.failedUids());
        assertEquals(1, resp.failedUids().size());
        assertTrue(resp.failedUids().get("E00401-LOCKED").contains("Tag AFI lock bit set"));

        assertEquals(3, resp.results().size());
        assertTrue(resp.results().get(0).isSuccess());
        assertFalse(resp.results().get(1).isSuccess());
        assertEquals("E00401-LOCKED", resp.results().get(1).getUid());
        assertTrue(resp.results().get(1).getError().contains("Tag AFI lock bit set"));
        assertTrue(resp.results().get(2).isSuccess());

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("partial", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());
        assertTrue(payloadData(reply).get("failedUids").has("E00401-LOCKED"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-UID batch write AFI when all transponders fail")
    void testBatchWriteAfiAllFailure() throws Exception {
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, new WSBrokerClient(cfg)
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                throw new Exception("Tag not in field");
            }
            @Override public void close() {}
        };

        dispatcher.setReader(mockReader);

        List<String> requestedUids = List.of("UID-FAIL-1", "UID-FAIL-2");
        WriteAfiResponseData resp = dispatcher.writeAfi(requestedUids, "07", null, null).join();

        assertEquals("error", resp.status());
        assertFalse(resp.isSuccess());
        assertFalse(resp.isPartial());
        assertTrue(resp.successfulUids().isEmpty());
        assertEquals(2, resp.failedUids().size());
        assertTrue(resp.failedUids().get("UID-FAIL-1").contains("Tag not in field"));
        assertTrue(resp.failedUids().get("UID-FAIL-2").contains("Tag not in field"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_afi message with uids array triggers batch processing")
    void testIncomingMessageWithUidsArray() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                written.add(uid);
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-TAG-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-TAG-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with uids array
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_afi",
                "uids", List.of("E00401-TAG-1", "E00401-TAG-2"),
                "afi", "C2"
        ));

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(800);

        assertEquals(2, written.size());
        assertEquals(2, sentEvents.size(), "Should have dispatched tags_cleared and write_afi_response");
        Event reply = sentEvents.stream().filter(e -> "rfid:write_afi_response".equals(e.getDataNode().path("event").asText())).findFirst().orElseThrow();
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals("C2", payloadData(reply).get("afi").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-tag batch write when all transponders succeed")
    void testBatchWriteTagsAllSuccess() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> writtenUids = new CopyOnWriteArrayList<>();
        AtomicInteger exclusiveLocks = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                writtenUids.add(uid);
            }
            @Override public void close() {}
        };

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, null, 100, 4) {
            @Override
            public <T> T executeExclusive(ScannerTask<T> task) throws Exception {
                exclusiveLocks.incrementAndGet();
                return super.executeExclusive(task);
            }
        };

        dispatcher.setReader(mockReader);
        dispatcher.setScanner(scanner);

        List<TagWriteItem> items = List.of(
                new TagWriteItem("E004010001", "ITEM-1", "CH-001", "CH", 1, 1, 1, "07"),
                new TagWriteItem("E004010002", "ITEM-2", "CH-001", "CH", 1, 1, 1, "07"),
                new TagWriteItem("E004010003", "ITEM-3", "CH-001", "CH", 1, 1, 1, "C2")
        );

        WriteTagResponseData resp = dispatcher.writeTags(items, "web-ui", "req-batch-tags-ok", "ref-batch-tags-001").join();

        assertEquals("ok", resp.status());
        assertTrue(resp.isSuccess());
        assertFalse(resp.isPartial());
        assertEquals(List.of("E004010001", "E004010002", "E004010003"), resp.uids());
        assertEquals(List.of("E004010001", "E004010002", "E004010003"), resp.successfulUids());
        assertTrue(resp.failedUids() == null || resp.failedUids().isEmpty());
        assertEquals(3, resp.results().size());
        assertTrue(resp.results().stream().allMatch(WriteTagResponseData.TagWriteResult::isSuccess));
        assertEquals(3, writtenUids.size());
        assertEquals(1, exclusiveLocks.get(), "Batch tag write should acquire exclusive scanner lock exactly once for all tags");

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("ref-batch-tags-001", reply.getRefId());
        assertEquals("rfid:write_tag_response", reply.getDataNode().get("event").asText());
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(3, payloadData(reply).get("uids").size());
        assertEquals(3, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-tag batch write with partial failure reports exact successes and errors")
    void testBatchWriteTagsPartialFailure() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                if ("E00401-LOCKED".equals(uid)) {
                    throw new Exception("Transponder write lock error (ISO error 0x12)");
                }
            }
            @Override public void close() {}
        };

        dispatcher.setReader(mockReader);

        List<TagWriteItem> items = List.of(
                new TagWriteItem("E00401-OK1", "ITEM-1", "CH-001", "CH", "07"),
                new TagWriteItem("E00401-LOCKED", "ITEM-2", "CH-001", "CH", "07"),
                new TagWriteItem("E00401-OK2", "ITEM-3", "CH-001", "CH", "07")
        );

        WriteTagResponseData resp = dispatcher.writeTags(items, "web-ui", "req-batch-tags-partial", "ref-partial-002").join();

        assertEquals("partial", resp.status());
        assertFalse(resp.isSuccess());
        assertTrue(resp.isPartial());
        assertEquals(List.of("E00401-OK1", "E00401-LOCKED", "E00401-OK2"), resp.uids());
        assertEquals(List.of("E00401-OK1", "E00401-OK2"), resp.successfulUids());
        assertNotNull(resp.failedUids());
        assertEquals(1, resp.failedUids().size());
        assertTrue(resp.failedUids().get("E00401-LOCKED").contains("Transponder write lock error"));

        assertEquals(3, resp.results().size());
        assertTrue(resp.results().get(0).isSuccess());
        assertFalse(resp.results().get(1).isSuccess());
        assertEquals("E00401-LOCKED", resp.results().get(1).getUid());
        assertTrue(resp.results().get(1).getError().contains("Transponder write lock error"));
        assertTrue(resp.results().get(2).isSuccess());

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("partial", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());
        assertTrue(payloadData(reply).get("failedUids").has("E00401-LOCKED"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-tag batch write when all transponders fail")
    void testBatchWriteTagsAllFailure() throws Exception {
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, new WSBrokerClient(cfg)
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                throw new Exception("Tag not in RF field");
            }
            @Override public void close() {}
        };

        dispatcher.setReader(mockReader);

        List<TagWriteItem> items = List.of(
                new TagWriteItem("UID-FAIL-1", "ITEM-1"),
                new TagWriteItem("UID-FAIL-2", "ITEM-2")
        );

        WriteTagResponseData resp = dispatcher.writeTags(items, null, null).join();

        assertEquals("error", resp.status());
        assertFalse(resp.isSuccess());
        assertFalse(resp.isPartial());
        assertTrue(resp.successfulUids().isEmpty());
        assertEquals(2, resp.failedUids().size());
        assertTrue(resp.failedUids().get("UID-FAIL-1").contains("Tag not in RF field"));
        assertTrue(resp.failedUids().get("UID-FAIL-2").contains("Tag not in RF field"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_tag message with tags array triggers batch processing")
    void testIncomingMessageWithTagsArray() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                written.add(uid + ":" + model.getPrimaryItemId() + ":" + (afi != null ? afi : "null"));
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-TAG-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-TAG-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with tags array
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "tags", List.of(
                        Map.of("uid", "E00401-TAG-1", "itemId", "BOOK-1", "afi", "07"),
                        Map.of("uid", "E00401-TAG-2", "itemId", "BOOK-2", "afi", "C2")
                )
        ));

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(800);

        assertEquals(2, written.size());
        assertTrue(written.contains("E00401-TAG-1:BOOK-1:7"));
        assertTrue(written.contains("E00401-TAG-2:BOOK-2:-62")); // 0xC2 signed byte = -62
        Event reply = sentEvents.stream()
                .filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText()))
                .findFirst().orElseThrow();
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_tag message with uids array and shared metadata triggers batch processing")
    void testIncomingMessageWithUidsAndSharedData() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                written.add(uid + ":" + model.getPrimaryItemId() + ":" + model.getISIL() + ":" + (afi != null ? afi : "null"));
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-SHARED-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-SHARED-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with uids array and shared item data & AFI
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uids", List.of("E00401-SHARED-1", "E00401-SHARED-2"),
                "itemId", "SHARED-ITEM",
                "isil", "DE-101",
                "country", "DE",
                "afi", "07"
        ));

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(800);

        assertEquals(2, written.size());
        assertTrue(written.contains("E00401-SHARED-1:SHARED-ITEM:DE-101:7"));
        assertTrue(written.contains("E00401-SHARED-2:SHARED-ITEM:DE-101:7"));
        Event reply = sentEvents.stream()
                .filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText()))
                .findFirst().orElseThrow();
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_tag message with tags map triggers batch processing with per-UID data and AFI")
    void testIncomingMessageWithTagsMapPerUid() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                written.add(uid + ":" + model.getPrimaryItemId() + ":" + model.getCountryOfOwnerLib() + ":" + (afi != null ? afi : "null"));
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-MAP-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-MAP-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with map of UID -> tag data/AFI
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "tags", Map.of(
                        "E00401-MAP-1", Map.of("itemId", "ITEM-MAP-1", "country", "DE", "afi", "07"),
                        "E00401-MAP-2", Map.of("itemId", "ITEM-MAP-2", "country", "CH", "afi", "C2")
                )
        ));

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(800);

        assertEquals(2, written.size());
        assertTrue(written.contains("E00401-MAP-1:ITEM-MAP-1:DE:7"));
        assertTrue(written.contains("E00401-MAP-2:ITEM-MAP-2:CH:-62"));
        Event reply = sentEvents.stream()
                .filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText()))
                .findFirst().orElseThrow();
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_tag message with root-level map of UID to tag data")
    void testIncomingMessageWithRootLevelMapPerUid() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                written.add(uid + ":" + model.getPrimaryItemId() + ":" + model.getCountryOfOwnerLib() + ":" + (afi != null ? afi : "null"));
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-ROOT-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-ROOT-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with root-level map of UID -> tag data / AFI
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "E00401-ROOT-1", Map.of("itemId", "ITEM-ROOT-1", "country", "AT", "afi", "07"),
                "E00401-ROOT-2", Map.of("itemId", "ITEM-ROOT-2", "country", "CH", "afi", "01")
        ));

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(800);

        assertEquals(2, written.size());
        assertTrue(written.contains("E00401-ROOT-1:ITEM-ROOT-1:AT:7"));
        assertTrue(written.contains("E00401-ROOT-2:ITEM-ROOT-2:CH:1"));
        Event reply = sentEvents.stream()
                .filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText()))
                .findFirst().orElseThrow();
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_tag message with parallel arrays (uids, itemIds, afis)")
    void testIncomingMessageWithParallelArrays() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                written.add(uid + ":" + model.getPrimaryItemId() + ":" + model.getPartNumber() + "/" + model.getPartsInItem() + ":" + (afi != null ? afi : "null"));
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-PAR-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-PAR-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with parallel arrays
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uids", List.of("E00401-PAR-1", "E00401-PAR-2"),
                "itemIds", List.of("BOOK-PART-1", "BOOK-PART-2"),
                "afis", List.of("07", "C2")
        ));

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(800);

        assertEquals(2, written.size());
        assertTrue(written.contains("E00401-PAR-1:BOOK-PART-1:1/2:7"));
        assertTrue(written.contains("E00401-PAR-2:BOOK-PART-2:2/2:-62"));
        Event reply = sentEvents.stream()
                .filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText()))
                .findFirst().orElseThrow();
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals(2, payloadData(reply).get("successfulUids").size());

        dispatcher.close();
    }
}
