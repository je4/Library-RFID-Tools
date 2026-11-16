package de.feig.fedm;

import java.util.HashMap;
import java.util.Map;

public class TestConfig extends Config {
    public int transferResult = ErrorCode.Ok;
    public int fallbackWrites;
    public int readCompleteResult = ErrorCode.Ok;
    public int writeCompleteResult = ErrorCode.Ok;
    public final Map<String, Object> parameters = new HashMap<>();

    public TestConfig() {
        super(null);
    }

    @Override
    public int readCompleteConfiguration(boolean fromRam) {
        return readCompleteResult;
    }

    @Override
    public int writeCompleteConfiguration(boolean toRam) {
        return writeCompleteResult;
    }

    @Override
    public int setConfigPara(String param, boolean value) {
        parameters.put(param, value);
        return ErrorCode.Ok;
    }

    @Override
    public int setConfigPara(String param, byte value) {
        parameters.put(param, value);
        return ErrorCode.Ok;
    }

    @Override
    public int setConfigPara(String param, long value) {
        parameters.put(param, value);
        return ErrorCode.Ok;
    }

    @Override
    public int transferXmlFileToReaderCfg(String fileName) {
        return transferResult;
    }

    @Override
    public int deserializeFromFile(String fileName, CustomXmlData data) {
        return ErrorCode.Ok;
    }

    @Override
    public int writeConfigurationPage(long page, boolean persistent) {
        fallbackWrites++;
        return fallbackWrites == 1 ? ErrorCode.Ok : 130;
    }

    @Override
    public int applyConfiguration(boolean persistent) {
        return ErrorCode.Ok;
    }
}