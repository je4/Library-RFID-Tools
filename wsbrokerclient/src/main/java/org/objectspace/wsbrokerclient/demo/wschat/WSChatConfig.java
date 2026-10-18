package org.objectspace.wsbrokerclient.demo.wschat;

import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.DevConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.certmanager.config.XmlConfigParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration model for WSChat Native GUI application.
 * Parses wschat.xml documents with &lt;name&gt;, &lt;wsbroker&gt;, &lt;channels&gt;, &lt;clienttls&gt;, and &lt;gui&gt; sections.
 */
public class WSChatConfig {

    private static final Logger log = LoggerFactory.getLogger(WSChatConfig.class);

    private String name = "test01";
    private String wsbroker = "https://localhost:8765";
    private List<String> channels = new ArrayList<>(List.of("all", "intern"));
    private CertManagerConfig clientTls;
    private String theme = "system";
    private int posX = 100;
    private int posY = 100;
    private int width = 1150;
    private int height = 720;
    private String configFilePath;

    public WSChatConfig() {
        // Fallback default dev TLS config
        this.clientTls = new CertManagerConfig();
        this.clientTls.setType(LoaderType.DEV);
        this.clientTls.setDev(new DevConfig());
    }

    /**
     * Loads WSChat configuration from XML file path.
     */
    public static WSChatConfig load(String filePath) throws Exception {
        File file = new File(filePath != null && !filePath.isBlank() ? filePath : "wschat.xml");
        if (!file.exists()) {
            // Check in parent, subfolder, or resources
            File candidate = new File("../" + file.getName());
            if (candidate.exists()) {
                file = candidate;
            } else {
                File subCandidate = new File("wsbrokerclient/" + file.getName());
                if (subCandidate.exists()) {
                    file = subCandidate;
                }
            }
        }
        if (!file.exists()) {
            log.warn("Configuration file not found: {}, using default configuration", file.getAbsolutePath());
            WSChatConfig defaultConfig = new WSChatConfig();
            defaultConfig.setConfigFilePath(file.getAbsolutePath());
            return defaultConfig;
        }

        return load(file);
    }

    /**
     * Loads WSChat configuration from XML file.
     */
    public static WSChatConfig load(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);

        WSChatConfig config = parseDocument(doc);
        config.setConfigFilePath(file.getAbsolutePath());
        return config;
    }

    /**
     * Loads WSChat configuration from an InputStream.
     */
    public static WSChatConfig load(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);

        return parseDocument(doc);
    }

    private static WSChatConfig parseDocument(Document doc) {
        WSChatConfig config = new WSChatConfig();
        Element root = doc.getDocumentElement();

        // 1. Name
        String nameVal = getDirectChildText(root, "name");
        if (nameVal != null && !nameVal.isBlank()) {
            config.setName(nameVal.trim());
        }

        // 2. WSBroker Endpoint
        String wsbrokerVal = getDirectChildText(root, "wsbroker", "endpoint", "url");
        if (wsbrokerVal != null && !wsbrokerVal.isBlank()) {
            config.setWsbroker(wsbrokerVal.trim());
        }

        // 3. Channels
        Element channelsElem = getDirectChildElement(root, "channels");
        if (channelsElem != null) {
            List<String> list = new ArrayList<>();
            NodeList children = channelsElem.getElementsByTagName("channel");
            for (int i = 0; i < children.getLength(); i++) {
                String ch = children.item(i).getTextContent();
                if (ch != null && !ch.isBlank()) {
                    list.add(ch.trim());
                }
            }
            if (!list.isEmpty()) {
                config.setChannels(list);
            }
        }

        // 4. ClientTLS (via certmanager XmlConfigParser)
        Element clientTlsElem = getDirectChildElement(root, "clienttls", "client_tls", "tls");
        if (clientTlsElem != null) {
            try {
                CertManagerConfig tlsCfg = XmlConfigParser.parseElement(clientTlsElem);
                config.setClientTls(tlsCfg);
            } catch (Exception e) {
                log.error("Failed to parse <clienttls> block from XML: {}", e.getMessage(), e);
            }
        }

        // 5. GUI & Window
        Element guiElem = getDirectChildElement(root, "gui", "inventory");
        if (guiElem != null) {
            String themeVal = getDirectChildText(guiElem, "theme", "ui");
            if (themeVal != null && !themeVal.isBlank()) {
                config.setTheme(themeVal.trim().toLowerCase());
            }

            Element windowElem = getDirectChildElement(guiElem, "window");
            if (windowElem != null) {
                config.setPosX(getIntChild(windowElem, "posx", config.getPosX()));
                config.setPosY(getIntChild(windowElem, "posy", config.getPosY()));
                config.setWidth(getIntChild(windowElem, "width", config.getWidth()));
                config.setHeight(getIntChild(windowElem, "height", config.getHeight()));
            }
        }

        return config;
    }

    private static String getDirectChildText(Element parent, String... tagNames) {
        Element child = getDirectChildElement(parent, tagNames);
        return child != null ? child.getTextContent() : null;
    }

    private static Element getDirectChildElement(Element parent, String... tagNames) {
        if (parent == null || tagNames == null) return null;
        for (String tagName : tagNames) {
            NodeList nl = parent.getElementsByTagName(tagName);
            if (nl.getLength() > 0) {
                return (Element) nl.item(0);
            }
        }
        return null;
    }

    private static int getIntChild(Element parent, String tagName, int defaultVal) {
        String txt = getDirectChildText(parent, tagName);
        if (txt != null && !txt.isBlank()) {
            try {
                return Integer.parseInt(txt.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    // --- Getters & Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWsbroker() {
        return wsbroker;
    }

    public void setWsbroker(String wsbroker) {
        this.wsbroker = wsbroker;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public CertManagerConfig getClientTls() {
        return clientTls;
    }

    public void setClientTls(CertManagerConfig clientTls) {
        this.clientTls = clientTls;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getPosX() {
        return posX;
    }

    public void setPosX(int posX) {
        this.posX = posX;
    }

    public int getPosY() {
        return posY;
    }

    public void setPosY(int posY) {
        this.posY = posY;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public void setConfigFilePath(String configFilePath) {
        this.configFilePath = configFilePath;
    }
}
