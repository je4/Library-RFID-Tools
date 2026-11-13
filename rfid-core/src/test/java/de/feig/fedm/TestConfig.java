package de.feig.fedm;

public class TestConfig extends Config {
    public int transferResult = ErrorCode.Ok;
    public int fallbackWrites;

    public TestConfig() {
        super(null);
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