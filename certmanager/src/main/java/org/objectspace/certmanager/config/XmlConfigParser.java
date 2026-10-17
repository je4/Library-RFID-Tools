package org.objectspace.certmanager.config;

import org.objectspace.certmanager.util.DurationParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses {@link CertManagerConfig} from XML documents (such as inventory.xml or standalone clienttls XML).
 */
public final class XmlConfigParser {

    private XmlConfigParser() {
    }

    /**
     * Parses a {@link CertManagerConfig} from XML string.
     */
    public static CertManagerConfig parse(String xmlContent) throws Exception {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("XML content is null or empty");
        }
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
        return parse(doc);
    }

    /**
     * Parses a {@link CertManagerConfig} from an XML file.
     */
    public static CertManagerConfig parse(File xmlFile) throws Exception {
        if (xmlFile == null || !xmlFile.exists()) {
            throw new FileNotFoundException("XML file not found: " + xmlFile);
        }
        try (InputStream is = Files.newInputStream(xmlFile.toPath())) {
            return parse(is);
        }
    }

    /**
     * Parses a {@link CertManagerConfig} from an InputStream.
     */
    public static CertManagerConfig parse(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream is null");
        }
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(inputStream));
        return parse(doc);
    }

    /**
     * Parses a {@link CertManagerConfig} from a parsed DOM Document.
     */
    public static CertManagerConfig parse(Document doc) {
        if (doc == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        Element root = doc.getDocumentElement();
        Element clientTlsElem = findElement(root, "clienttls", "client_tls", "tls", "clientTLS");
        if (clientTlsElem == null) {
            // Check if root itself is clienttls
            String rootName = root.getNodeName().toLowerCase(Locale.ROOT);
            if (rootName.equals("clienttls") || rootName.equals("client_tls") || rootName.equals("tls")) {
                clientTlsElem = root;
            }
        }

        if (clientTlsElem == null) {
            throw new IllegalArgumentException("No <clienttls> or <tls> configuration block found in XML");
        }

        return parseElement(clientTlsElem);
    }

    /**
     * Parses a {@link CertManagerConfig} from a DOM &lt;clienttls&gt; Element.
     */
    public static CertManagerConfig parseElement(Element element) {
        CertManagerConfig config = new CertManagerConfig();

        String typeStr = getChildText(element, "type");
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                config.setType(LoaderType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                config.setType(LoaderType.MINIVAULT);
            }
        }

        String initialTimeoutStr = getChildText(element, "initialtimeout", "initial_timeout", "initialTimeout");
        if (initialTimeoutStr != null && !initialTimeoutStr.isBlank()) {
            config.setInitialTimeout(DurationParser.parse(initialTimeoutStr));
        }

        String tlsMinVersion = getChildText(element, "tlsminversion", "tls_min_version", "tlsMinVersion");
        if (tlsMinVersion != null && !tlsMinVersion.isBlank()) {
            config.setTlsMinVersion(tlsMinVersion.trim());
        }

        String tlsMaxVersion = getChildText(element, "tlsmaxversion", "tls_max_version", "tlsMaxVersion");
        if (tlsMaxVersion != null && !tlsMaxVersion.isBlank()) {
            config.setTlsMaxVersion(tlsMaxVersion.trim());
        }

        String mutualStr = getChildText(element, "mutual");
        if (mutualStr != null && !mutualStr.isBlank()) {
            config.setMutual(Boolean.parseBoolean(mutualStr.trim()));
        }

        // 1. MiniVault section
        Element mvElem = findElement(element, "minivault", "vault", "miniVault");
        if (mvElem != null) {
            MiniVaultConfig mvConfig = new MiniVaultConfig();
            mvConfig.setBaseUrl(getChildText(mvElem, "baseurl", "baseUrl", "base_url"));
            mvConfig.setParentToken(getChildText(mvElem, "parenttoken", "parentToken", "parent_token"));

            String tokenType = getChildText(mvElem, "tokentype", "tokenType", "token_type");
            if (tokenType != null && !tokenType.isBlank()) {
                mvConfig.setTokenType(tokenType.trim());
            }

            mvConfig.setTokenPolicies(collectList(mvElem, "tokenpolicies", "tokenPolicies", "token_policies", "policy", "policies"));

            String tokenInterval = getChildText(mvElem, "tokeninterval", "tokenInterval", "token_interval");
            if (tokenInterval != null && !tokenInterval.isBlank()) {
                mvConfig.setTokenInterval(DurationParser.parse(tokenInterval));
            }

            String tokenTtl = getChildText(mvElem, "tokenttl", "tokenTTL", "tokenTtl", "token_ttl");
            if (tokenTtl != null && !tokenTtl.isBlank()) {
                mvConfig.setTokenTtl(DurationParser.parse(tokenTtl));
            }

            String certType = getChildText(mvElem, "certtype", "certType", "cert_type");
            if (certType != null && !certType.isBlank()) {
                mvConfig.setCertType(certType.trim());
            }

            mvConfig.setUris(collectList(mvElem, "uris", "uri"));
            mvConfig.setDnss(collectList(mvElem, "dnss", "dnsnames", "dnsNames", "dns", "name"));
            mvConfig.setIps(collectList(mvElem, "ips", "ip"));

            String certInterval = getChildText(mvElem, "certinterval", "certInterval", "cert_interval");
            if (certInterval != null && !certInterval.isBlank()) {
                mvConfig.setCertInterval(DurationParser.parse(certInterval));
            }

            String certTtl = getChildText(mvElem, "certttl", "certTTL", "certTtl", "cert_ttl");
            if (certTtl != null && !certTtl.isBlank()) {
                mvConfig.setCertTtl(DurationParser.parse(certTtl));
            }

            mvConfig.setCa(collectList(mvElem, "ca", "path", "file", "cert"));

            String useSysPool = getChildText(mvElem, "usesystempool", "useSystemPool", "use_system_pool");
            if (useSysPool != null && !useSysPool.isBlank()) {
                mvConfig.setUseSystemPool(Boolean.parseBoolean(useSysPool.trim()));
            }

            config.setVault(mvConfig);
        }

        // 2. File section
        Element fileElem = findElement(element, "file");
        if (fileElem != null) {
            FileConfig fileConfig = new FileConfig();
            fileConfig.setCertFile(getChildText(fileElem, "certfile", "certFile", "cert_file", "cert"));
            fileConfig.setKeyFile(getChildText(fileElem, "keyfile", "keyFile", "key_file", "key"));
            fileConfig.setCa(collectList(fileElem, "ca", "cafile", "caFile", "ca_file", "path", "file"));

            String interval = getChildText(fileElem, "interval");
            if (interval != null && !interval.isBlank()) {
                fileConfig.setInterval(DurationParser.parse(interval));
            }

            String useSysPool = getChildText(fileElem, "usesystempool", "useSystemPool", "use_system_pool");
            if (useSysPool != null && !useSysPool.isBlank()) {
                fileConfig.setUseSystemPool(Boolean.parseBoolean(useSysPool.trim()));
            }

            config.setFile(fileConfig);
        }

        // 3. Env section
        Element envElem = findElement(element, "env");
        if (envElem != null) {
            EnvConfig envConfig = new EnvConfig();
            envConfig.setCertContent(getChildText(envElem, "cert", "certcontent", "certContent"));
            envConfig.setKeyContent(getChildText(envElem, "key", "keycontent", "keyContent"));
            envConfig.setCertEnv(getChildText(envElem, "certenv", "certEnv", "cert_env"));
            envConfig.setKeyEnv(getChildText(envElem, "keyenv", "keyEnv", "key_env"));
            envConfig.setCaEnv(getChildText(envElem, "caenv", "caEnv", "ca_env"));
            envConfig.setCaContent(collectList(envElem, "ca", "caContent", "path", "file"));

            String useSysPool = getChildText(envElem, "usesystempool", "useSystemPool", "use_system_pool");
            if (useSysPool != null && !useSysPool.isBlank()) {
                envConfig.setUseSystemPool(Boolean.parseBoolean(useSysPool.trim()));
            }

            config.setEnv(envConfig);
        }

        // 4. Dev section
        Element devElem = findElement(element, "dev");
        if (devElem != null) {
            DevConfig devConfig = new DevConfig();
            String commonName = getChildText(devElem, "commonname", "commonName", "common_name", "certname", "certName", "cert_name", "cn");
            if (commonName != null && !commonName.isBlank()) {
                devConfig.setCommonName(commonName.trim());
            }

            List<String> uris = collectList(devElem, "uris", "uri");
            if (!uris.isEmpty()) {
                devConfig.setUris(uris);
            }
            List<String> dnss = collectList(devElem, "dnss", "dnsnames", "dnsNames", "dns", "name");
            if (!dnss.isEmpty()) {
                devConfig.setDnss(dnss);
            }
            List<String> ips = collectList(devElem, "ips", "ip");
            if (!ips.isEmpty()) {
                devConfig.setIps(ips);
            }

            String validity = getChildText(devElem, "validity");
            if (validity != null && !validity.isBlank()) {
                devConfig.setValidity(DurationParser.parse(validity));
            }

            String interval = getChildText(devElem, "interval");
            if (interval != null && !interval.isBlank()) {
                devConfig.setInterval(DurationParser.parse(interval));
            }

            config.setDev(devConfig);
        }

        return config;
    }

    private static Element findElement(Element parent, String... names) {
        if (parent == null || names == null) {
            return null;
        }
        for (String name : names) {
            NodeList list = parent.getElementsByTagName(name);
            if (list.getLength() > 0 && list.item(0) instanceof Element elem) {
                return elem;
            }
        }
        return null;
    }

    private static String getChildText(Element parent, String... names) {
        if (parent == null || names == null) {
            return null;
        }
        for (String name : names) {
            NodeList list = parent.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if (node instanceof Element elem && elem.getNodeName().equalsIgnoreCase(name)) {
                    String text = elem.getTextContent();
                    return text != null ? text.trim() : "";
                }
            }
        }
        return null;
    }

    private static List<String> collectList(Element parent, String containerName, String... childItemNames) {
        List<String> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }

        Element container = findElement(parent, containerName);
        Element target = container != null ? container : parent;

        NodeList children = target.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child) {
                String childTag = child.getNodeName();
                boolean matches = false;
                if (childItemNames != null && childItemNames.length > 0) {
                    for (String itemTag : childItemNames) {
                        if (childTag.equalsIgnoreCase(itemTag)) {
                            matches = true;
                            break;
                        }
                    }
                } else {
                    matches = true;
                }

                if (matches) {
                    String val = child.getTextContent();
                    if (val != null && !val.isBlank()) {
                        result.add(val.trim());
                    }
                }
            }
        }

        // If direct text in container or comma-separated
        if (result.isEmpty() && container != null) {
            String direct = container.getTextContent();
            if (direct != null && !direct.isBlank() && !direct.contains("\n") && !direct.contains("\r")) {
                if (direct.contains(",")) {
                    for (String part : direct.split(",")) {
                        if (!part.isBlank()) {
                            result.add(part.trim());
                        }
                    }
                } else {
                    result.add(direct.trim());
                }
            }
        }

        return result;
    }

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        return factory;
    }
}
