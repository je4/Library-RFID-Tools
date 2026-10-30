package org.objectspace.rfid.headless;

import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;

/**
 * Reusable mock implementation of ISO15693Reader for unit testing.
 */
public class MockISO15693Reader implements ISO15693Reader {

    private String deviceId = "52E101";
    private String deviceType = "ISC.MR102-USB";
    private String deviceInfo = "FEIG Electronic GmbH MR102";
    private boolean connected = true;

    public MockISO15693Reader() {
    }

    public MockISO15693Reader(String deviceId, String deviceType, String deviceInfo) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.deviceInfo = deviceInfo;
    }

    @Override
    public void connect() throws Exception {
    }

    @Override
    public void init() throws Exception {
    }

    @Override
    public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    @Override
    public boolean checkConnection() {
        return connected;
    }

    @Override
    public String getDeviceInfo() {
        return deviceInfo;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getDeviceType() {
        return deviceType;
    }

    @Override
    public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
    }

    @Override
    public void writeAfi(String uid, byte afi) throws Exception {
    }

    @Override
    public void close() throws Exception {
    }
}
