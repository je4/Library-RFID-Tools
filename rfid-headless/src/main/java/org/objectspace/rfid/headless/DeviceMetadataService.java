package org.objectspace.rfid.headless;

import org.objectspace.rfid.headless.payload.DeviceMetadataPayload;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Service for querying reader hardware metadata and sending DeviceMetadataPayload responses.
 */
public class DeviceMetadataService {

	private static final Logger logger = LoggerFactory.getLogger(DeviceMetadataService.class);

	private final String clientName;

	public DeviceMetadataService(String clientName) {
		this.clientName = clientName;
	}

	public DeviceMetadataPayload queryDeviceMetadata(HeadlessReaderInstance inst, ISO15693Reader fallbackReader) {
		if (inst != null) {
			String devId = inst.getDeviceId();
			String devType = inst.getDeviceType();
			String initDesc = inst.getInitDescription();
			if (initDesc == null || initDesc.isBlank()) {
				initDesc = inst.getDeviceInfo();
			}
			return new DeviceMetadataPayload(devId, devType, initDesc, "", System.currentTimeMillis(), clientName, inst.getReaderId(), "ok", DeviceMetadataPayload.EVENT_RESPONSE);
		}

		String devId = (fallbackReader != null) ? fallbackReader.getDeviceId() : null;
		String devType = (fallbackReader != null) ? fallbackReader.getDeviceType() : null;
		String initDesc = (fallbackReader != null) ? fallbackReader.getInitDescription() : null;
		if (initDesc == null || initDesc.isBlank()) {
			initDesc = (fallbackReader != null) ? fallbackReader.getDeviceInfo() : null;
		}

		return new DeviceMetadataPayload(devId, devType, initDesc, "", System.currentTimeMillis(), clientName);
	}

	public CompletableFuture<Void> sendDeviceMetadata(String target, String refId, HeadlessReaderInstance inst,
													  WSBrokerClient client, boolean active, ISO15693Reader fallbackReader) {
		if (!active || client == null) {
			return CompletableFuture.completedFuture(null);
		}
		DeviceMetadataPayload payload = queryDeviceMetadata(inst, fallbackReader);
		Event<DeviceMetadataPayload> event = Event.createMessage(clientName, target, payload);
		if (payload.getSession() != null && !payload.getSession().isBlank()) {
			event.setSession(payload.getSession());
		}
		if (payload.getTimestamp() > 0) {
			event.setTime(java.time.Instant.ofEpochMilli(payload.getTimestamp()));
		}
		if (refId != null && !refId.isBlank()) {
			event.setRefId(refId);
		}
		event.setMessage(payload.getSummaryMessage());
		logger.info("[WSBroker] Sending device metadata response to '{}' (device: '{}', type: '{}', readerId: '{}', refId: '{}')",
				target, payload.getDeviceId(), payload.getDeviceType(), payload.getReaderId(), refId);
		return client.send(event);
	}
}
