package org.objectspace.wsbrokerclient.demo.wschat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.LoaderType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class WSChatConfigTest {

    @Test
    @DisplayName("Test loading wschat.xml configuration from project root")
    void testLoadWSChatXml() throws Exception {
        File xmlFile = new File("wschat.xml");
        if (!xmlFile.exists()) {
            xmlFile = new File("../wschat.xml");
        }
        assertThat(xmlFile).exists();

        WSChatConfig config = WSChatConfig.load(xmlFile);

        assertThat(config.getName()).isEqualTo("test01");
        assertThat(config.getWsbroker()).isEqualTo("https://localhost:8765");
        assertThat(config.getChannels()).containsExactly("all", "intern");
        assertThat(config.getTheme()).isEqualTo("system");
        assertThat(config.getWidth()).isEqualTo(1150);
        assertThat(config.getHeight()).isEqualTo(720);

        assertThat(config.getClientTls()).isNotNull();
        assertThat(config.getClientTls().getType()).isEqualTo(LoaderType.MINIVAULT);
        assertThat(config.getClientTls().getVault()).isNotNull();
        assertThat(config.getClientTls().getVault().getBaseUrl()).isEqualTo("https://localhost:8759/api/v1");
        assertThat(config.getClientTls().getVault().getParentToken()).isEqualTo("pa.22f09dfd1cbfbe9f.c17b29cb2b730533");
        assertThat(config.getClientTls().getVault().getTokenType()).isEqualTo("client_cert");
        assertThat(config.getClientTls().getVault().getUris()).contains("wsbroker:client:test01", "wsbroker:channel:all", "wsbroker:channel:intern");
    }

    @Test
    @DisplayName("Test loading WSChatConfig from XML InputStream")
    void testLoadFromInputStream() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <name>custom-client</name>
                    <wsbroker>wss://broker.example.com:9000</wsbroker>
                    <channels>
                        <channel>general</channel>
                        <channel>alerts</channel>
                    </channels>
                    <gui>
                        <theme>dark</theme>
                        <window>
                            <width>1280</width>
                            <height>800</height>
                        </window>
                    </gui>
                </configuration>
                """;

        WSChatConfig config = WSChatConfig.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(config.getName()).isEqualTo("custom-client");
        assertThat(config.getWsbroker()).isEqualTo("wss://broker.example.com:9000");
        assertThat(config.getChannels()).containsExactly("general", "alerts");
        assertThat(config.getTheme()).isEqualTo("dark");
        assertThat(config.getWidth()).isEqualTo(1280);
        assertThat(config.getHeight()).isEqualTo(800);
    }
}
