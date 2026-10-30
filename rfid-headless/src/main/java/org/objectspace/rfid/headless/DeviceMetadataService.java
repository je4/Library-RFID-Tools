package org.objectspace.rfid.headless;

import org.objectspace.rfid.headless.payload.DeviceMetadataData;
import org.objectspace.rfid.headless.payload.RfidPayload;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Service for querying reader hardware metadata and sending DeviceMetadataData responses.
 */
public class DeviceMetadataService {

	private static final Logger logger = LoggerFactory.getLogger(DeviceMetadataService.class);

	private final String clientName;

	public DeviceMetadataService(String clientName) {
		this.clientName = clientName;
	}

	public DeviceMetadataData queryDeviceMetadata(HeadlessReaderInstance inst, ISO15693Reader fallbackReader) {
		if (inst != null) {
			String devId = inst.getDeviceId();
			String devType = inst.getDeviceType();
			String initDesc = inst.getInitDescription();
			if (initDesc == null || initDesc.isBlank()) {
				initDesc = inst.getDeviceInfo();
			}
			return new DeviceMetadataData("ok", devId, devType, initDesc, inst.getReaderId());
		}

		String devId = (fallbackReader != null) ? fallbackReader.getDeviceId() : null;
		String devType = (fallbackReader != null) ? fallbackReader.getDeviceType() : null;
		String initDesc = (fallbackReader != null) ? fallbackReader.getInitDescription() : null;
		if (initDesc == null || initDesc.isBlank()) {
			initDesc = (fallbackReader != null) ? fallbackReader.getDeviceInfo() : null;
		}

		return new DeviceMetadataData("ok", devId, devType, initDesc, null);
	}

	public CompletableFuture<Void> sendDeviceMetadata(String target, String refId, HeadlessReaderInstance inst,
													  WSBrokerClient client, boolean active, ISO15693Reader fallbackReader) {
		if (!active || client == null) {
			return CompletableFuture.completedFuture(null);
		}
		DeviceMetadataData data = queryDeviceMetadata(inst, fallbackReader);
		RfidPayload<DeviceMetadataData> payload = RfidPayload.of(DeviceMetadataData.EVENT_RESPONSE, data);
		Event<RfidPayload<DeviceMetadataData>> event = Event.createMessage(clientName, target, payload);
		if (refId != null && !refId.isBlank()) {
			event.setRefId(refId);
		}
		event.setMessage(data.getSummaryMessage());
		logger.info("[WSBroker] Sending device metadata response to '{}' (device: '{}', type: '{}', readerId: '{}', refId: '{}')",
				target, data.deviceId(), data.deviceType(), data.readerId(), refId);
		return client.send(event);
	}
}
