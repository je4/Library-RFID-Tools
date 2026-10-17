# Certificate Manager (`certmanager`)

The **Certificate Manager** (`certmanager`) module provides automated, zero-downtime certificate lifecycle management, periodic renewal, and runtime dynamic hot-reloading for X.509 client and server certificates in Java.

It provides a robust Java implementation mirroring the Go PKI ecosystems [`minivaultclient`](https://go.ub.unibas.ch/cloud/minivaultclient) and [`certloader`](https://go.ub.unibas.ch/cloud/certloader).

---

## Key Features

1. **MiniVault PKI Integration (`MiniVaultClient` & `MiniVaultLoader`)**:
   - Automated token lifecycle management (`/auth/token/get`, `/auth/token/create`).
   - Automated issuance and background rotation of short-lived X.509 certificates (`/cert/create`).
   - Dynamic fetching and synchronization of trusted Root and Intermediate CA bundles (`/cert/ca/pem`).
   - Optimal renewal scheduling (automatically derived as $\frac{2}{3}$ of token and certificate TTLs).

2. **Zero-Downtime Dynamic Hot-Reloading (`DynamicKeyManager` & `DynamicTrustManager`)**:
   - Extends standard JSSE `X509ExtendedKeyManager` and `X509ExtendedTrustManager`.
   - Rotates active client/server key pairs and updates CA trust anchors in-flight without restarting the application or re-instantiating the `SSLContext`.
   - Automatic TLS session cache invalidation upon certificate updates to enforce fresh handshakes.

3. **Pluggable Loader Backends**:
   - **`MINIVAULT`**: Cloud PKI backend for automated mutual TLS (mTLS) with token and certificate rotation.
   - **`FILE`**: Local filesystem polling for `.crt`/`.pem` and `.key` file modifications.
   - **`ENV`**: In-memory loading from environment variables or static PEM strings.
   - **`DEV`**: Ephemeral in-memory self-signed certificate generation for rapid development and testing.

---

## Architecture Overview

```mermaid
graph TD
    subgraph Sources["Certificate Sources & Backends"]
        MV["MiniVault PKI Service<br/>(HTTP REST API)"]
        FS["Local Filesystem<br/>(File Watcher / Poller)"]
        ENV["Environment Variables<br/>(Static PEM Strings)"]
        DEV["In-Memory Dev Generator<br/>(BouncyCastle Self-Signed)"]
    end

    subgraph Loaders["CertLoader Subsystem"]
        MVL["MiniVaultLoader<br/>• Token Refresh Loop<br/>• Cert Renewal Loop<br/>• CA Sync"]
        FL["FileLoader<br/>• File Modification Poller"]
        EL["EnvLoader"]
        DL["DevLoader"]
    end

    subgraph DynamicJSSE["Dynamic JSSE Core"]
        DKM["DynamicKeyManager<br/>(X509ExtendedKeyManager)<br/>• AtomicReference&lt;KeyCertPair&gt;"]
        DTM["DynamicTrustManager<br/>(X509ExtendedTrustManager)<br/>• Dynamic CA Trust Anchors"]
        DSC["DynamicSSLContext<br/>• Session Cache Invalidation"]
    end

    subgraph Consumers["Application Clients & Servers"]
        HC["java.net.http.HttpClient<br/>(REST & Webservice Dispatcher)"]
        WS["Netty / gRPC / WebSockets"]
        SS["Raw TLS Sockets / Servers"]
    end

    MV --> MVL
    FS --> FL
    ENV --> EL
    DEV --> DL

    MVL --> DKM
    MVL --> DTM
    FL --> DKM
    FL --> DTM
    EL --> DKM
    EL --> DTM
    DL --> DKM
    DL --> DTM

    DKM --> DSC
    DTM --> DSC

    DSC --> HC
    DSC --> WS
    DSC --> SS
```

---

## Sequence Flows

### 1. MiniVault Token & Certificate Renewal Workflow

```mermaid
sequenceDiagram
    autonumber
    participant App as Application / CertLoader
    participant MV as MiniVault Server (/api/v1)
    participant DKM as DynamicKeyManager
    participant DTM as DynamicTrustManager
    participant DSC as DynamicSSLContext

    Note over App,MV: Initialization Phase
    App->>MV: GET /auth/token/get (Header: X-Vault-Token: parent_token)
    MV-->>App: 200 OK (Token metadata: MaxTTL, CertMaxTTL)
    App->>App: Compute intervals: TokenInterval & CertInterval (2/3 TTL)

    App->>MV: POST /auth/token/create (Type: client_cert, Policies)
    MV-->>App: 200 OK (Child Token: cc.xxxxxx)

    App->>MV: POST /cert/create (SAN URIs, DNS, IPs, TTL)
    MV-->>App: 200 OK (X.509 Cert + Private Key + CA)

    App->>MV: GET /cert/ca/pem
    MV-->>App: 200 OK (CA Bundle)

    App->>DKM: setKeyCertPair(newCertPair)
    App->>DTM: reload(caCertificates)
    App->>DSC: invalidateSessions()

    Note over App,MV: Background Periodic Renewal Loops
    loop Every CertInterval (e.g. 12h)
        App->>MV: POST /cert/create (using active Child Token)
        MV-->>App: 200 OK (Rotated Certificate & Key)
        App->>DKM: setKeyCertPair(rotatedPair)
        App->>DSC: invalidateSessions()
    end

    loop Every TokenInterval (e.g. 16h)
        App->>MV: POST /auth/token/create (using Parent Token)
        MV-->>App: 200 OK (Fresh Child Token)
        App->>App: Update active child token atomically
    end
```

### 2. Runtime Certificate Hot-Reloading

```mermaid
sequenceDiagram
    participant Worker as Background CertLoader
    participant DKM as DynamicKeyManager
    participant DTM as DynamicTrustManager
    participant DSC as DynamicSSLContext
    participant TLS as TLS Handshake Engine (Client/Server)

    Worker->>DKM: setKeyCertPair(newPair)
    Note over DKM: Atomic update via AtomicReference
    Worker->>DSC: invalidateSessions()
    Note over DSC: Clear client & server SSLSessionContext caches

    Note over TLS: Incoming / Outgoing TLS Connection
    TLS->>DKM: chooseClientAlias() / getCertificateChain() / getPrivateKey()
    DKM-->>TLS: Returns updated X.509 Certificate Chain and PrivateKey
    TLS->>DTM: checkServerTrusted() / checkClientTrusted()
    DTM-->>TLS: Validates against updated CA Trust Anchors
    Note over TLS: TLS 1.3 Handshake completes seamlessly with new certificate
```

---

## Usage Examples

### 1. Client-side mTLS with MiniVault

```java
import org.objectspace.certmanager.CertManager;
import org.objectspace.certmanager.config.*;
import org.objectspace.certmanager.loader.CertLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class MiniVaultClientExample {
    public static void main(String[] args) throws Exception {
        MiniVaultConfig vaultConfig = MiniVaultConfig.builder()
                .baseUrl("https://vault.example.com:8200")
                .parentToken("env:VAULT_PARENT_TOKEN") // or direct token string
                .tokenType("client_cert")
                .tokenPolicies(List.of("client-policy"))
                .certType("client_cert")
                .dnss(List.of("client.example.local"))
                .uris(List.of("spiffe://example.com/client"))
                .certTtl(Duration.ofHours(24))
                .certInterval(Duration.ofHours(12))
                .useSystemPool(true)
                .build();

        CertManagerConfig config = CertManagerConfig.builder()
                .type(LoaderType.MINIVAULT)
                .vault(vaultConfig)
                .tlsMinVersion("TLSv1.3")
                .build();

        // Starts background token & certificate refresh loops
        try (CertLoader loader = CertManager.createClientLoader(config)) {
            // Get pre-configured HttpClient backed by DynamicSSLContext
            HttpClient httpClient = loader.getHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://secure-service.example.local/api/resource"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
        }
    }
}
```

---

### 2. Filesystem Certificate Polling (`FILE`)

```java
FileConfig fileConfig = FileConfig.builder()
        .certFile("/etc/ssl/certs/client.crt")
        .keyFile("/etc/ssl/private/client.key")
        .interval(Duration.ofMinutes(1)) // polling frequency
        .build();

try (CertLoader loader = CertManager.createFileLoader(fileConfig)) {
    SSLContext sslContext = loader.getSSLContext();
    // Pass sslContext to Netty, Apache HttpClient, Spring WebClient, etc.
}
```

---

### 3. XML Configuration Loading (`inventory.xml`)

```java
// Loads configuration automatically from XML file (e.g. inventory.xml <clienttls> section)
File configFile = new File("inventory.xml");
CertManagerConfig config = CertManager.loadConfigFromFile(configFile);

try (CertLoader loader = CertManager.createClientLoader(config)) {
    HttpClient client = loader.getHttpClient();
    // Use client for TLS/mTLS requests
}
```

---

### 4. JSON Configuration Loading

```java
String jsonConfig = """
{
    "type": "MINIVAULT",
    "initialtimeout": "30s",
    "tlsminversion": "TLSv1.3",
    "minivault": {
        "baseurl": "https://vault.example.com:8200",
        "parenttoken": "env:VAULT_PARENT_TOKEN",
        "tokentype": "client_cert",
        "tokenpolicies": ["client-policy"],
        "certtype": "client_cert",
        "dnss": ["client.example.local"],
        "certttl": "24h",
        "certinterval": "12h",
        "usesystempool": true
    }
}
""";

CertManagerConfig config = CertManager.loadConfigFromJson(jsonConfig);
CertLoader loader = CertManager.createClientLoader(config);
```

---

## Package Structure

- **`org.objectspace.certmanager`**
  - `CertManager`: Unified entry point, factory methods, and JSON deserialization.
  - **`client`**: `MiniVaultClient` REST client for communication with MiniVault endpoints.
  - **`model`**: Data transfer objects (`Token`, `TokenType`, `TokenCreateRequest`, `CertCreateRequest`, `CertResponse`, `KeyCertPair`).
  - **`ssl`**: `DynamicKeyManager`, `DynamicTrustManager`, and `DynamicSSLContext` implementing runtime hot-reload.
  - **`loader`**: `CertLoader` contract and backends (`MiniVaultLoader`, `FileLoader`, `EnvLoader`, `DevLoader`).
  - **`config`**: Strongly-typed configuration POJOs with builder patterns.
  - **`util`**: `PemUtils` (PEM parser, CA generation, X.509 converters) and `DurationParser` (Go-style `"24h"`, `"1h30m"` & ISO duration parsing).
