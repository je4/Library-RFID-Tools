package org.objectspace.rfid.headless;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessReaderRegistryTest {

    @Test
    @DisplayName("Test registering and retrieving reader instances")
    void testRegisterAndGet() {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        HeadlessReaderConfig cfg = new HeadlessReaderConfig("gate-01");
        HeadlessReaderInstance inst = new HeadlessReaderInstance(cfg, null, null, null);

        registry.registerReaderInstance(inst);

        assertEquals(inst, registry.getReaderInstance("gate-01"));
        assertEquals(inst, registry.getReaderInstance("GATE-01"));
        assertNull(registry.getReaderInstance("unknown"));
        assertEquals(1, registry.getReaderInstances().size());
    }

    @Test
    @DisplayName("Test resolving target reader from event and requested ID")
    void testResolveTargetReader() {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        HeadlessReaderConfig cfg1 = new HeadlessReaderConfig("gate-01");
        HeadlessReaderConfig cfg2 = new HeadlessReaderConfig("return-02");
        HeadlessReaderInstance inst1 = new HeadlessReaderInstance(cfg1, null, null, null);
        HeadlessReaderInstance inst2 = new HeadlessReaderInstance(cfg2, null, null, null);

        registry.setReaderInstances(List.of(inst1, inst2));

        Event<String> evt = Event.createMessage("client-a", "return-02", "query");
        HeadlessReaderInstance resolved = registry.resolveTargetReader(evt, null, "hub", List.of("all"));
        assertNotNull(resolved);
        assertEquals("return-02", resolved.getReaderId());
    }

    @Test
    @DisplayName("Test fallback to default reader when no instances registered")
    void testFallbackReader() {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        assertNull(registry.resolveTargetReader(null, null, "hub", List.of()));

        HeadlessReaderConfig cfg = new HeadlessReaderConfig("hub");
        HeadlessReaderCallback cb = new HeadlessReaderCallback(null, null, 1000L);
        registry.setCallback(cb, "hub");

        HeadlessReaderInstance resolved = registry.resolveTargetReader(null, null, "hub", List.of());
        assertNotNull(resolved);
        assertEquals("hub", resolved.getReaderId());
    }
}
