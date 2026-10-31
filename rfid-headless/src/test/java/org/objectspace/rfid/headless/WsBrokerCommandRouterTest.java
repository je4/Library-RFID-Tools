package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class WsBrokerCommandRouterTest {

    private static JsonNode payloadData(Event<?> evt) {
        return evt.getDataNode();
    }

    @Test
    @DisplayName("Test dispatcher handles rfid:write_tag command with all parameters and token")
    void testWriteTagCommandSuccess() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("all"),
                null,
                mockClient
        );

        AtomicBoolean writeTagCalled = new AtomicBoolean(false);
        AtomicInteger writeAfiCalled = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                assertEquals("E004015099887766", uid);
                assertEquals("3012345678", model.getPrimaryItemId());
                assertEquals("CH-000001-1", model.getISIL());
                assertEquals("CH", model.getCountryOfOwnerLib());
                assertEquals(2, model.getPartsInItem());
                assertEquals(1, model.getPartNumber());
                if (afi != null) {
                    assertEquals((byte) 0x07, afi.byteValue());
                    writeAfiCalled.incrementAndGet();
                }
                writeTagCalled.set(true);
            }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                assertEquals("E004015099887766", uid);
                assertEquals((byte) 0x07, afi);
                writeAfiCalled.incrementAndGet();
            }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        Event writeMsg = Event.of("rfid/write_tag", "web-ui", "rfid-station-1", Map.of(
                "uid", "E004015099887766",
                "itemId", "3012345678",
                "isil", "CH-000001-1",
                "country", "CH",
                "parts", 2,
                "partno", 1,
                "afi", "07"
        ));

        dispatcher.handleIncomingMessage(writeMsg);

        // Wait for async write future to dispatch response
        Thread.sleep(400);

        assertTrue(writeTagCalled.get(), "Reader.writeTag should have been invoked");
        assertEquals(1, sentEvents.size(), "Should have received write_tag response event");

        Event reply = sentEvents.get(0);
        assertEquals("rfid-station-1", reply.getSource());
        assertEquals("web-ui", reply.getTarget());
        assertEquals("rfid/write_tag_response", reply.getType());
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals("E004015099887766", payloadData(reply).get("uid").asText());
        assertEquals("3012345678", payloadData(reply).get("itemId").asText());
        assertEquals("CH-000001-1", payloadData(reply).get("isil").asText());
        assertEquals("CH", payloadData(reply).get("country").asText());
        assertEquals(2, payloadData(reply).get("parts").asInt());
        assertEquals(1, payloadData(reply).get("partNo").asInt());
        assertEquals("07", payloadData(reply).get("afi").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher handles rfid:write_afi command")
    void testWriteAfiCommandSuccess() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("all"),
                null,
                mockClient
        );

        AtomicInteger writeAfiInvocations = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                assertEquals("E004015099887766", uid);
                assertEquals((byte) 0xC2, afi);
                writeAfiInvocations.incrementAndGet();
            }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        // 1. Test rfid:write_afi with hex string
        Event afiMsg1 = Event.of("rfid/write_afi", "web-ui", "rfid-station-1", Map.of(
                "uid", "E004015099887766",
                "afi", "0xC2"
        ));

        dispatcher.handleIncomingMessage(afiMsg1);
        Thread.sleep(400);

        assertEquals(1, writeAfiInvocations.get());
        assertEquals(1, sentEvents.size());

        Event reply1 = sentEvents.get(0);
        assertEquals("rfid-station-1", reply1.getSource());
        assertEquals("web-ui", reply1.getTarget());
        assertEquals("rfid/write_afi_response", reply1.getType());
        assertEquals("ok", payloadData(reply1).get("status").asText());
        assertEquals("E004015099887766", payloadData(reply1).get("uid").asText());
        assertEquals("C2", payloadData(reply1).get("afi").asText());

        // 2. Test rfid:write_afi with decimal number
        sentEvents.clear();
        Event afiMsg2 = Event.of("rfid/write_afi", "web-ui", "rfid-station-1", Map.of(
                "uid", "E004015099887766",
                "afi", 194 // decimal 194 == 0xC2
        ));

        dispatcher.handleIncomingMessage(afiMsg2);
        Thread.sleep(400);

        assertEquals(2, writeAfiInvocations.get());
        assertEquals(1, sentEvents.size());

        Event reply2 = sentEvents.get(0);
        assertEquals("rfid/write_afi_response", reply2.getType());
        assertEquals("ok", payloadData(reply2).get("status").asText());
        assertEquals("C2", payloadData(reply2).get("afi").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher write error reporting when reader throws exception")
    void testWriteErrorHandling() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("all"),
                null,
                mockClient
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                throw new Exception("Tag write collision or transponder not in field");
            }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                throw new Exception("Tag AFI lock bit set");
            }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        // 1. Error on writeTag
        Event writeMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "E004015099887766",
                "itemId", "3012345678"
        ));

        dispatcher.handleIncomingMessage(writeMsg);
        Thread.sleep(400);

        assertEquals(1, sentEvents.size());
        Event reply1 = sentEvents.get(0);
        assertEquals("rfid:write_tag_response", reply1.getDataNode().get("event").asText());
        assertEquals("error", payloadData(reply1).get("status").asText());
        assertTrue(payloadData(reply1).get("error").asText().contains("Tag write collision"));

        // 2. Error on writeAfi
        sentEvents.clear();
        Event afiMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_afi",
                "uid", "E004015099887766",
                "afi", "07"
        ));

        dispatcher.handleIncomingMessage(afiMsg);
        Thread.sleep(400);

        assertEquals(1, sentEvents.size());
        Event reply2 = sentEvents.get(0);
        assertEquals("rfid:write_afi_response", reply2.getDataNode().get("event").asText());
        assertEquals("error", payloadData(reply2).get("status").asText());
        assertTrue(payloadData(reply2).get("error").asText().contains("AFI lock bit set"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-station broadcast write_tag is executed only by the station with the active tag")
    void testBroadcastWriteTagFilteredByPresenceMultiStation() throws Exception {
        List<Event> st1Events = new CopyOnWriteArrayList<>();
        List<Event> st2Events = new CopyOnWriteArrayList<>();
        List<Event> st3Events = new CopyOnWriteArrayList<>();

        WSClientConfig cfg1 = new WSClientConfig();
        cfg1.setClientName("rfid-station-1");
        cfg1.setEndpoint("https://localhost:8765");

        WSClientConfig cfg2 = new WSClientConfig();
        cfg2.setClientName("rfid-station-2");
        cfg2.setEndpoint("https://localhost:8765");

        WSClientConfig cfg3 = new WSClientConfig();
        cfg3.setClientName("rfid-station-3");
        cfg3.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher d1 = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg1) {
                    @Override public CompletableFuture<Void> send(Event e) { st1Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d2 = new HeadlessWsBrokerDispatcher("rfid-station-2", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg2) {
                    @Override public CompletableFuture<Void> send(Event e) { st2Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d3 = new HeadlessWsBrokerDispatcher("rfid-station-3", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg3) {
                    @Override public CompletableFuture<Void> send(Event e) { st3Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean st1Written = new AtomicBoolean(false);
        AtomicBoolean st2Written = new AtomicBoolean(false);
        AtomicBoolean st3Written = new AtomicBoolean(false);

        d1.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st1Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        d2.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-2"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st2Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        d3.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-3"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st3Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, d1, 3000L, 2000L);
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, d2, 3000L, 2000L);
        HeadlessReaderCallback cb3 = new HeadlessReaderCallback(null, d3, 3000L, 2000L);

        d1.setCallback(cb1);
        d2.setCallback(cb2);
        d3.setCallback(cb3);

        // Station 1 detects UID-1111
        cb1.doIt(1, 1, "NXP", "SLIX", "UID-1111", new FinnishDataModel().getBlock(32), 4);
        // Station 2 detects UID-2222
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-2222", new FinnishDataModel().getBlock(32), 4);
        // Station 3 has empty antenna field (no tags)

        st1Events.clear();
        st2Events.clear();
        st3Events.clear();

        // Broadcast write_tag command for UID-1111 published to channel 'all'
        Event broadcastEvent = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", "UID-1111",
                "itemId", "BOOK-1111",
                "isil", "CH-001",
                "country", "CH"
        ));

        // Broadcast reaches all 3 stations
        d1.handleIncomingMessage(broadcastEvent);
        d2.handleIncomingMessage(broadcastEvent);
        d3.handleIncomingMessage(broadcastEvent);

        Thread.sleep(800);

        // Station 1 must have executed write and replied
        assertTrue(st1Written.get(), "Station 1 holding UID-1111 should execute writeTag");
        assertEquals(2, st1Events.size(), "Station 1 should send tags_cleared and write_tag_response");
        Event st1Cleared = st1Events.stream().filter(e -> "rfid:tags_cleared".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        Event st1Reply = st1Events.stream().filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(st1Cleared, "Station 1 should send tags_cleared event after write operation");
        assertNotNull(st1Reply, "Station 1 should send write_tag_response");
        assertEquals("ok", payloadData(st1Reply).get("status").asText());

        // Stations 2 and 3 must silently ignore the broadcast
        assertFalse(st2Written.get(), "Station 2 (holding UID-2222) should ignore broadcast for UID-1111");
        assertEquals(0, st2Events.size(), "Station 2 should not send any response");

        assertFalse(st3Written.get(), "Station 3 (empty) should ignore broadcast for UID-1111");
        assertEquals(0, st3Events.size(), "Station 3 should not send any response");

        d1.close();
        d2.close();
        d3.close();
    }

    @Test
    @DisplayName("Test multi-station broadcast write_afi is executed only by the station with the active tag")
    void testBroadcastWriteAfiFilteredByPresence() throws Exception {
        List<Event> st1Events = new CopyOnWriteArrayList<>();
        List<Event> st2Events = new CopyOnWriteArrayList<>();

        WSClientConfig cfg1 = new WSClientConfig();
        cfg1.setClientName("rfid-station-1");
        cfg1.setEndpoint("https://localhost:8765");

        WSClientConfig cfg2 = new WSClientConfig();
        cfg2.setClientName("rfid-station-2");
        cfg2.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher d1 = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg1) {
                    @Override public CompletableFuture<Void> send(Event e) { st1Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d2 = new HeadlessWsBrokerDispatcher("rfid-station-2", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg2) {
                    @Override public CompletableFuture<Void> send(Event e) { st2Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean st1AfiWritten = new AtomicBoolean(false);
        AtomicBoolean st2AfiWritten = new AtomicBoolean(false);

        d1.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String u, FinnishDataModel m, Byte a) {}
            @Override public void writeAfi(String u, byte a) { st1AfiWritten.set(true); }
            @Override public void close() {}
        });

        d2.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-2"; }
            @Override public void writeTag(String u, FinnishDataModel m, Byte a) {}
            @Override public void writeAfi(String u, byte a) { st2AfiWritten.set(true); }
            @Override public void close() {}
        });

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, d1, 3000L, 2000L);
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, d2, 3000L, 2000L);

        d1.setCallback(cb1);
        d2.setCallback(cb2);

        cb1.doIt(1, 1, "NXP", "SLIX", "UID-AAAA", new FinnishDataModel().getBlock(32), 4);
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-BBBB", new FinnishDataModel().getBlock(32), 4);

        st1Events.clear();
        st2Events.clear();

        // Broadcast write_afi command for UID-BBBB
        Event broadcastAfi = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_afi",
                "uid", "UID-BBBB",
                "afi", "07"
        ));

        d1.handleIncomingMessage(broadcastAfi);
        d2.handleIncomingMessage(broadcastAfi);

        Thread.sleep(800);

        assertFalse(st1AfiWritten.get(), "Station 1 should ignore write_afi for UID-BBBB");
        assertEquals(0, st1Events.size());

        assertTrue(st2AfiWritten.get(), "Station 2 holding UID-BBBB should execute write_afi");
        assertEquals(2, st2Events.size(), "Station 2 should send tags_cleared and write_afi_response");
        Event st2Cleared = st2Events.stream().filter(e -> "rfid:tags_cleared".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        Event st2Reply = st2Events.stream().filter(e -> "rfid:write_afi_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(st2Cleared, "Station 2 should send tags_cleared event after write_afi operation");
        assertNotNull(st2Reply, "Station 2 should send write_afi_response");
        assertEquals("ok", payloadData(st2Reply).get("status").asText());
        assertEquals("UID-BBBB", payloadData(st2Reply).get("uid").asText());

        d1.close();
        d2.close();
    }

    @Test
    @DisplayName("Test direct unicast write command for missing tag returns explicit error")
    void testDirectWriteTagNonPresentReturnsExplicitError() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg) {
                    @Override public CompletableFuture<Void> send(Event e) { sentEvents.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean readerWriteInvoked = new AtomicBoolean(false);
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String u, FinnishDataModel m, Byte a) { readerWriteInvoked.set(true); }
            @Override public void writeAfi(String u, byte a) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        // Station has UID-PRESENT on field
        callback.doIt(1, 1, "NXP", "SLIX", "UID-PRESENT", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Direct command explicitly addressed to rfid-station-1 for missing tag UID-ABSENT
        Event directMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "UID-ABSENT",
                "itemId", "BOOK-ABSENT"
        ));

        dispatcher.handleIncomingMessage(directMsg);
        Thread.sleep(200);

        assertFalse(readerWriteInvoked.get(), "Reader hardware should not be touched for absent tag in direct mode");
        assertEquals(1, sentEvents.size(), "Direct message should receive explicit error response");

        Event reply = sentEvents.get(0);
        assertEquals("rfid:write_tag_response", reply.getDataNode().get("event").asText());
        assertEquals("error", payloadData(reply).get("status").asText());
        assertTrue(payloadData(reply).get("error").asText().contains("not present on reader station"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test wildcard broadcast write_tag executes when exactly 1 tag is present, ignores when ambiguous")
    void testWildcardBroadcastWriteTagSingleVsMultipleTags() throws Exception {
        List<Event> st1Events = new CopyOnWriteArrayList<>();
        List<Event> st2Events = new CopyOnWriteArrayList<>();

        WSClientConfig cfg1 = new WSClientConfig();
        cfg1.setClientName("rfid-station-1");
        cfg1.setEndpoint("https://localhost:8765");

        WSClientConfig cfg2 = new WSClientConfig();
        cfg2.setClientName("rfid-station-2");
        cfg2.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher d1 = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg1) {
                    @Override public CompletableFuture<Void> send(Event e) { st1Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d2 = new HeadlessWsBrokerDispatcher("rfid-station-2", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg2) {
                    @Override public CompletableFuture<Void> send(Event e) { st2Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean st1Written = new AtomicBoolean(false);
        AtomicBoolean st2Written = new AtomicBoolean(false);

        d1.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) {
                assertEquals("UID-SOLO", uid);
                st1Written.set(true);
            }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        d2.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-2"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st2Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, d1, 3000L, 2000L);
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, d2, 3000L, 2000L);

        d1.setCallback(cb1);
        d2.setCallback(cb2);

        // Station 1: exactly 1 tag
        cb1.doIt(1, 1, "NXP", "SLIX", "UID-SOLO", new FinnishDataModel().getBlock(32), 4);

        // Station 2: multiple (2) tags
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-MULTI-1", new FinnishDataModel().getBlock(32), 4);
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-MULTI-2", new FinnishDataModel().getBlock(32), 4);

        st1Events.clear();
        st2Events.clear();

        // Broadcast wildcard write command (empty uid)
        Event wildcardEvent = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", "",
                "itemId", "WILDCARD-ITEM"
        ));

        d1.handleIncomingMessage(wildcardEvent);
        d2.handleIncomingMessage(wildcardEvent);

        Thread.sleep(400);

        assertTrue(st1Written.get(), "Station 1 with single tag should execute wildcard write");
        assertEquals(2, st1Events.size(), "Station 1 should send tags_cleared and write_tag_response");
        Event st1Cleared = st1Events.stream().filter(e -> "rfid:tags_cleared".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        Event st1Reply = st1Events.stream().filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(st1Cleared, "Station 1 should send tags_cleared notification");
        assertNotNull(st1Reply, "Station 1 should send write_tag_response");
        assertEquals("UID-SOLO", payloadData(st1Reply).get("uid").asText());

        assertFalse(st2Written.get(), "Station 2 with multiple tags should ignore wildcard write");
        assertEquals(0, st2Events.size());

        d1.close();
        d2.close();
    }

    @Test
    @DisplayName("Test back-to-back chained write operations succeed without waiting for scan loop")
    void testBackToBackChainedWriteOperations() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg) {
                    @Override public CompletableFuture<Void> send(Event e) { sentEvents.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean writeTagDone = new AtomicBoolean(false);
        AtomicBoolean writeAfiDone = new AtomicBoolean(false);

        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { writeTagDone.set(true); }
            @Override public void writeAfi(String uid, byte afi) { writeAfiDone.set(true); }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        String uid = "UID-CHAIN-123";
        callback.doIt(1, 1, "NXP", "SLIX", uid, new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // 1. First broadcast write_tag
        Event writeTagEvt = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", uid,
                "itemId", "CHAIN-ITEM"
        ));
        dispatcher.handleIncomingMessage(writeTagEvt);

        Thread.sleep(400);
        assertTrue(writeTagDone.get(), "First write_tag should succeed");
        assertEquals(2, sentEvents.size(), "Should have received tags_cleared and write_tag_response");
        Event cleared1 = sentEvents.stream().filter(e -> "rfid:tags_cleared".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        Event reply1 = sentEvents.stream().filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(cleared1, "tags_cleared event must be sent after write_tag");
        assertNotNull(reply1, "write_tag_response must be sent");

        // Background scanner detects written tag on antenna
        callback.doIt(1, 1, "NXP", "SLIX", uid, new FinnishDataModel().getBlock(32), 4);

        // 2. Immediate second broadcast write_afi for same tag
        Event writeAfiEvt = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_afi",
                "uid", uid,
                "afi", "07"
        ));
        dispatcher.handleIncomingMessage(writeAfiEvt);

        Thread.sleep(400);
        assertTrue(writeAfiDone.get(), "Chained write_afi should immediately succeed on same tag");
        Event reply2 = sentEvents.stream().filter(e -> "rfid:write_afi_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(reply2, "write_afi_response must be sent");
        assertEquals("ok", payloadData(reply2).get("status").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test RefID correlation across metadata queries, tag write, AFI write and error replies")
    void testRefIdCorrelationInDispatcher() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg) {
                    @Override public CompletableFuture<Void> send(Event e) { sentEvents.add(e); return CompletableFuture.completedFuture(null); }
                });

        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceId() { return "DEV-999"; }
            @Override public String getDeviceType() { return "OBID-LRU1002"; }
            @Override public String getInitDescription() { return "Feig LRU1002 Initialized"; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) {}
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        String tagUid = "UID-CORRELATION-TEST";
        callback.doIt(1, 1, "NXP", "SLIX", tagUid, new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // 1. Device metadata command with specific ID
        Event metaCmd = Event.createMessage("dashboard", "rfid-station-1", Map.of("event", "rfid:get_device_metadata"));
        metaCmd.setId("meta-query-id-111111111111111");
        dispatcher.handleIncomingMessage(metaCmd);

        Thread.sleep(200);
        assertEquals(1, sentEvents.size());
        Event metaResp = sentEvents.get(0);
        assertEquals("meta-query-id-111111111111111", metaResp.getRefId(), "Response event must correlate with request ID via refId");
        assertEquals("rfid:device_metadata_response", metaResp.getDataNode().get("event").asText());
        assertEquals("DEV-999", payloadData(metaResp).get("deviceId").asText());

        // 2. Write Tag command with specific ID
        sentEvents.clear();
        Event writeTagCmd = Event.createMessage("dashboard", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", tagUid,
                "itemId", "ITEM-CORR"
        ));
        writeTagCmd.setId("write-tag-id-2222222222222222");
        dispatcher.handleIncomingMessage(writeTagCmd);

        Thread.sleep(300);
        Event writeTagResp = sentEvents.stream().filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(writeTagResp, "write_tag_response must be present");
        assertEquals("write-tag-id-2222222222222222", writeTagResp.getRefId());
        assertEquals("rfid:write_tag_response", writeTagResp.getDataNode().get("event").asText());
        assertEquals("ok", payloadData(writeTagResp).get("status").asText());

        // Background scanner detects written tag again on antenna
        callback.doIt(1, 1, "NXP", "SLIX", tagUid, new FinnishDataModel().getBlock(32), 4);

        // 3. Write AFI command with specific ID
        sentEvents.clear();
        Event writeAfiCmd = Event.createMessage("dashboard", "all", Map.of(
                "event", "rfid:write_afi",
                "uid", tagUid,
                "afi", "07"
        ));
        writeAfiCmd.setId("write-afi-id-3333333333333333");
        dispatcher.handleIncomingMessage(writeAfiCmd);

        Thread.sleep(300);
        Event writeAfiResp = sentEvents.stream().filter(e -> "rfid:write_afi_response".equals(e.getDataNode().path("event").asText())).findFirst().orElse(null);
        assertNotNull(writeAfiResp, "write_afi_response must be present");
        assertEquals("write-afi-id-3333333333333333", writeAfiResp.getRefId());
        assertEquals("rfid:write_afi_response", writeAfiResp.getDataNode().get("event").asText());
        assertEquals("ok", payloadData(writeAfiResp).get("status").asText());

        // 4. Direct write command error reply when tag not present
        sentEvents.clear();
        Event directMissingWrite = Event.createMessage("dashboard", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "NON-EXISTENT-UID",
                "itemId", "ITEM-FAIL"
        ));
        directMissingWrite.setId("error-req-id-44444444444444");
        dispatcher.handleIncomingMessage(directMissingWrite);

        Thread.sleep(200);
        assertEquals(1, sentEvents.size());
        Event errResp = sentEvents.get(0);
        assertEquals("error-req-id-44444444444444", errResp.getRefId());
        assertEquals("error", payloadData(errResp).get("status").asText());

        // 5. handleMetadataQuery via CLIENT_INFO / non-message query
        sentEvents.clear();
        Event infoQuery = Event.builder()
                .id("info-query-id-55555555555555")
                .source("external-service")
                .target("rfid-station-1")
                .type(org.objectspace.wsbrokerclient.event.EventType.CLIENT_INFO)
                .build();
        dispatcher.handleMetadataQuery(infoQuery);

        Thread.sleep(200);
        assertEquals(1, sentEvents.size());
        Event infoResp = sentEvents.get(0);
        assertEquals("info-query-id-55555555555555", infoResp.getRefId());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-reader command routing for metadata queries")
    void testMultiReaderCommandRoutingAndMetadata() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-hub-01");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-hub-01", "https://localhost:8765", List.of("all"), null, mockClient
        );

        HeadlessReaderConfig cfg1 = new HeadlessReaderConfig("gate-01", false, "52E101");
        HeadlessReaderConfig cfg2 = new HeadlessReaderConfig("return-02", true, "52E102");

        ISO15693Reader reader1 = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceId() { return "52E101"; }
            @Override public String getDeviceType() { return "ISC.MR102-USB"; }
            @Override public String getDeviceInfo() { return "FEIG Gate 1"; }
            @Override public void writeTag(String uid, FinnishDataModel model, Byte afi) {}
            @Override public void close() {}
        };

        ISO15693Reader reader2 = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceId() { return "52E102"; }
            @Override public String getDeviceType() { return "ISC.PRH101-USB"; }
            @Override public String getDeviceInfo() { return "FEIG Return 2"; }
            @Override public void writeTag(String uid, FinnishDataModel model, Byte afi) {}
            @Override public void close() {}
        };

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, dispatcher, 3000L, "gate-01");
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, dispatcher, 3000L, "return-02");

        HeadlessReaderScanner sc1 = new HeadlessReaderScanner(reader1, cb1, 12, 300, "gate-01");
        HeadlessReaderScanner sc2 = new HeadlessReaderScanner(reader2, cb2, 12, 300, "return-02");

        HeadlessReaderInstance inst1 = new HeadlessReaderInstance(cfg1, reader1, cb1, sc1);
        HeadlessReaderInstance inst2 = new HeadlessReaderInstance(cfg2, reader2, cb2, sc2);

        dispatcher.setReaderInstances(List.of(inst1, inst2));

        // 1. Query device metadata for gate-01
        Event queryGate = Event.createMessage("ui-client", "rfid-hub-01", Map.of(
                "event", "rfid:get_device_metadata",
                "reader_id", "gate-01"
        ));
        dispatcher.handleIncomingMessage(queryGate);

        Event replyGate = sentEvents.stream().filter(e -> "52E101".equals(payloadData(e).path("deviceId").asText())).findFirst().orElseThrow();
        assertEquals("rfid:device_metadata_response", replyGate.getDataNode().get("event").asText());
        assertEquals("52E101", payloadData(replyGate).get("deviceId").asText());
        assertEquals("ISC.MR102-USB", payloadData(replyGate).get("deviceType").asText());

        // 2. Query device metadata for return-02
        Event queryReturn = Event.createMessage("ui-client", "rfid-hub-01", Map.of(
                "event", "rfid:get_device_metadata",
                "reader_id", "return-02"
        ));
        dispatcher.handleIncomingMessage(queryReturn);

        Event replyReturn = sentEvents.stream().filter(e -> "52E102".equals(payloadData(e).path("deviceId").asText())).findFirst().orElseThrow();
        assertEquals("rfid:device_metadata_response", replyReturn.getDataNode().get("event").asText());
        assertEquals("52E102", payloadData(replyReturn).get("deviceId").asText());
        assertEquals("ISC.PRH101-USB", payloadData(replyReturn).get("deviceType").asText());

        // 3. Query non-existent reader
        Event queryUnknown = Event.createMessage("ui-client", "rfid-hub-01", Map.of(
                "event", "rfid:get_device_metadata",
                "reader_id", "non-existent-reader"
        ));
        dispatcher.handleIncomingMessage(queryUnknown);

        Event replyUnknown = sentEvents.stream().filter(e -> "error".equals(payloadData(e).path("status").asText())).findFirst().orElseThrow();
        assertEquals("error", payloadData(replyUnknown).get("status").asText());
        assertTrue(payloadData(replyUnknown).get("initDescription").asText().contains("not found"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-reader read-only enforcement on write_tag and write_afi commands")
    void testMultiReaderReadOnlyRejectionOnWriteCommands() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-hub-01");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-hub-01", "https://localhost:8765", List.of("all"), null, mockClient
        );

        HeadlessReaderConfig cfgRw = new HeadlessReaderConfig("reader-rw", false, "52E101");
        HeadlessReaderConfig cfgRo = new HeadlessReaderConfig("reader-ro", true, "52E102");

        List<String> writtenTags = new CopyOnWriteArrayList<>();
        List<String> writtenAfis = new CopyOnWriteArrayList<>();

        ISO15693Reader readerRw = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceId() { return "52E101"; }
            @Override public String getDeviceType() { return "MR102"; }
            @Override public String getDeviceInfo() { return "RW Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) {
                writtenTags.add(uid + ":" + model.getPrimaryItemId());
            }
            @Override
            public void writeAfi(String uid, byte afi) {
                writtenAfis.add(uid + ":" + afi);
            }
            @Override public void close() {}
        };

        ISO15693Reader readerRo = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceId() { return "52E102"; }
            @Override public String getDeviceType() { return "MR102"; }
            @Override public String getDeviceInfo() { return "RO Reader"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) {
                fail("Write tag should NEVER be invoked on a read-only reader!");
            }
            @Override
            public void writeAfi(String uid, byte afi) {
                fail("Write AFI should NEVER be invoked on a read-only reader!");
            }
            @Override public void close() {}
        };

        HeadlessReaderCallback cbRw = new HeadlessReaderCallback(null, dispatcher, 3000L, "reader-rw");
        HeadlessReaderCallback cbRo = new HeadlessReaderCallback(null, dispatcher, 3000L, "reader-ro");

        HeadlessReaderScanner scRw = new HeadlessReaderScanner(readerRw, cbRw, 12, 300, "reader-rw");
        HeadlessReaderScanner scRo = new HeadlessReaderScanner(readerRo, cbRo, 12, 300, "reader-ro");

        HeadlessReaderInstance instRw = new HeadlessReaderInstance(cfgRw, readerRw, cbRw, scRw);
        HeadlessReaderInstance instRo = new HeadlessReaderInstance(cfgRo, readerRo, cbRo, scRo);

        dispatcher.setReaderInstances(List.of(instRw, instRo));

        // Place tags in presence
        cbRw.doIt(1, 1, "NXP", "SLIX", "E00401-RW-1", new FinnishDataModel().getBlock(32), 4);
        cbRo.doIt(1, 1, "NXP", "SLIX", "E00401-RO-1", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // 1. Attempt write_tag on READ-ONLY reader -> MUST REJECT
        Event writeRoMsg = Event.createMessage("ui", "rfid-hub-01", Map.of(
                "event", "rfid:write_tag",
                "reader_id", "reader-ro",
                "uid", "E00401-RO-1",
                "itemId", "NEW-BOOK-RO"
        ));
        dispatcher.handleIncomingMessage(writeRoMsg);

        Event replyWriteRo = sentEvents.stream().filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText())).findFirst().orElseThrow();
        assertEquals("error", payloadData(replyWriteRo).get("status").asText());
        assertTrue(payloadData(replyWriteRo).get("error").asText().contains("read-only"));
        assertTrue(writtenTags.isEmpty());

        // 2. Attempt write_afi on READ-ONLY reader -> MUST REJECT
        sentEvents.clear();
        Event afiRoMsg = Event.createMessage("ui", "rfid-hub-01", Map.of(
                "event", "rfid:write_afi",
                "reader_id", "reader-ro",
                "uid", "E00401-RO-1",
                "afi", "07"
        ));
        dispatcher.handleIncomingMessage(afiRoMsg);

        Event replyAfiRo = sentEvents.stream().filter(e -> "rfid:write_afi_response".equals(e.getDataNode().path("event").asText())).findFirst().orElseThrow();
        assertEquals("error", payloadData(replyAfiRo).get("status").asText());
        assertTrue(payloadData(replyAfiRo).get("error").asText().contains("read-only"));
        assertTrue(writtenAfis.isEmpty());

        // 3. Write tag on R/W reader -> MUST SUCCEED
        sentEvents.clear();
        Event writeRwMsg = Event.createMessage("ui", "rfid-hub-01", Map.of(
                "event", "rfid:write_tag",
                "reader_id", "reader-rw",
                "uid", "E00401-RW-1",
                "itemId", "NEW-BOOK-RW"
        ));
        dispatcher.handleIncomingMessage(writeRwMsg);
        Thread.sleep(400);

        Event replyWriteRw = sentEvents.stream().filter(e -> "rfid:write_tag_response".equals(e.getDataNode().path("event").asText())).findFirst().orElseThrow();
        assertEquals("ok", payloadData(replyWriteRw).get("status").asText());
        assertEquals(1, writtenTags.size());
        assertEquals("E00401-RW-1:NEW-BOOK-RW", writtenTags.get(0));

        // 4. Write AFI on R/W reader -> MUST SUCCEED
        // Place tag back in presence after previous write_tag emptied callback presence
        cbRw.doIt(1, 1, "NXP", "SLIX", "E00401-RW-1", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        Event afiRwMsg = Event.createMessage("ui", "rfid-hub-01", Map.of(
                "event", "rfid:write_afi",
                "reader_id", "reader-rw",
                "uid", "E00401-RW-1",
                "afi", "C2"
        ));
        dispatcher.handleIncomingMessage(afiRwMsg);
        Thread.sleep(400);

        Event replyAfiRw = sentEvents.stream().filter(e -> "rfid:write_afi_response".equals(e.getDataNode().path("event").asText())).findFirst().orElseThrow();
        assertEquals("ok", payloadData(replyAfiRw).get("status").asText());
        assertEquals(1, writtenAfis.size());
        assertEquals("E00401-RW-1:-62", writtenAfis.get(0));

        dispatcher.close();
    }
}
