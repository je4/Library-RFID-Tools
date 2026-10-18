# RFID Core (`rfid-core`)

The **RFID Core** (`rfid-core`) module provides the headless, foundational infrastructure for RFID hardware abstraction (FEIG electronic Gen3 SDK), the ISO 28560-2 library data model ("Finnish Data Model"), tag validation pipelines, and RESTful webservice dispatching.

---

## Key Features

1. **ISO 28560-2 / Finnish Data Model (`FinnishDataModel`)**:
   - High-throughput bitwise encoding and decoding of standard library RFID tags.
   - Parses Primary Item Identifiers (barcodes / signatures), Content Parameters, Type of Usage, Format Versions, and Optional Blocks (Set Information, Title / Call Number extensions).
2. **FEIG SDK Gen3 Hardware Integration (`org.objectspace.rfid.feig`)**:
   - Comprehensive abstraction wrapping FEIG SDK Gen3 (`fedm-java-api-7.1.0.jar`, `fedm-funit-java-api-1.1.0.jar`, `fedm-service-java-api-11.2.1.jar`).
   - Supports ISO 15693 High-Frequency (HF) readers via USB, Serial, and TCP/IP interfaces with automatic reconnection handling.
3. **Tag Scanning & Pipeline Processor (`TagScanProcessor`)**:
   - Decoupled event-driven scanning pipeline with configurable time-based debounce filters.
   - Synchronous or asynchronous fan-out to local GUI callbacks, SQL databases, and remote webservices.
4. **RESTful Webservice Dispatcher with HMAC-SHA256 JWT Authorization (`WebserviceDispatcher`)**:
   - Formulates dynamic HTTP GET/POST payloads with template URL placeholders (`{marker}`, `{session}`, `{uid}`, `{raw}`, `{itemid}`, `{clientid}`).
   - Secure HMAC-SHA256 JWT authorization token generation matching the Android NFC Reader protocol.

---

## Architecture & Data Flow

```mermaid
graph TD
    subgraph Hardware["RFID Hardware Layer"]
        Reader["FEIG Electronic RFID Reader<br/>(e.g. ID ISC.MR102 / LR1002 / PRH102)"]
        Tag["ISO 15693 RFID Tags<br/>(NXP I-Code SLIX, etc.)"]
        Tag -->|HF 13.56 MHz Air Interface| Reader
    end

    subgraph NativeSDK["FEIG Gen3 SDK Wrapper"]
        DLL["Native FEIG Drivers<br/>(lib/native/x64 or aarch64)"]
        FeigSDK["FeigRFID / ISO15693Feig<br/>(Reader Factory & SDK Adapter)"]
        Reader --> DLL
        DLL --> FeigSDK
    end

    subgraph CoreEngine["RFID Core Engine"]
        FDM["FinnishDataModel<br/>(ISO 28560-2 Decoder & Validator)"]
        TSP["TagScanProcessor<br/>• Raw Byte Extraction<br/>• Debounce Filter<br/>• Error Handling"]
        FeigSDK -->|Raw Tag Blocks| TSP
        TSP -->|Raw Bytes| FDM
    end

    subgraph Dispatching["Dispatching & Persistence Layer"]
        WSD["WebserviceDispatcher<br/>• UrlBuilder Template<br/>• JwtGenerator (HMAC-SHA256)<br/>• Java HttpClient"]
        DB["Database Connector<br/>(MySQL / MariaDB JDBC)"]
        CB["Application Callbacks<br/>(Desktop UI & Raspi Daemon)"]
        
        TSP -->|Parsed Media Item| WSD
        TSP -->|Parsed Media Item| DB
        TSP -->|Parsed Media Item| CB
    end

    subgraph RemoteEndpoints["Remote Services"]
        REST["Library Management API / ILS<br/>(REST Endpoint)"]
        SQL["Relational Database<br/>(Inventory Records)"]
        WSD -->|HTTP POST/GET with JWT| REST
        DB -->|SQL INSERT| SQL
    end
```

---

## Tag Processing & Webservice Dispatch Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Tag as RFID Tag (ISO 15693)
    participant Reader as FEIG RFID Reader
    participant TSP as TagScanProcessor
    participant FDM as FinnishDataModel
    participant WSD as WebserviceDispatcher
    participant Remote as Remote Webservice / ILS

    loop Continuous Inventory Scan Loop
        Reader->>Tag: Inventory & Read Multiple Blocks (0..11)
        Tag-->>Reader: UID + 48 bytes raw data
        Reader->>TSP: onTagScanned(uid, rawBytes)
        
        TSP->>TSP: Check Debounce Filter (e.g. 3000ms cooldown)
        alt Tag recently processed
            TSP->>TSP: Drop redundant scan
        else New Tag or Cooldown Expired
            TSP->>FDM: decode(rawBytes)
            FDM-->>TSP: Parsed FinnishDataModel (Item ID, Set Info)
            
            par Dispatch to Webservice
                TSP->>WSD: dispatchScan(marker, uid, rawHex, fdm)
                WSD->>WSD: Generate HMAC-SHA256 JWT Token
                WSD->>WSD: Build URL with placeholders ({itemid}, {uid})
                WSD->>Remote: HTTP POST /inventory (Payload + Bearer/JWT)
                Remote-->>WSD: 200 OK (Response Body)
            and Dispatch to Local Callbacks
                TSP->>TSP: Notify UI / Console listeners
            end
        end
    end
```

---

## ISO 28560-2 / Finnish Data Model Memory Layout

```mermaid
classDiagram
    class FinnishDataModel {
        +byte version
        +byte contentType
        +String primaryIdentifier
        +int partNumber
        +int totalParts
        +boolean isSet()
        +List~FinnishDataModelOptionalBlock~ optionalBlocks
        +byte[] encode()
        +static FinnishDataModel decode(byte[] data)
    }

    class FinnishDataModelOptionalBlock {
        +byte blockId
        +byte[] data
        +String getAsText()
    }

    FinnishDataModel "1" *-- "many" FinnishDataModelOptionalBlock : contains
```

---

## Usage Examples

### 1. Decoding ISO 28560-2 Tag Data

```java
import org.objectspace.rfid.FinnishDataModel;

byte[] tagBytes = getRawTagData(); // 48 bytes read from blocks 0..11
FinnishDataModel model = FinnishDataModel.decode(tagBytes);

if (model != null) {
    System.out.println("Item Identifier (Barcode): " + model.getPrimaryIdentifier());
    System.out.println("Part Number: " + model.getPartNumber() + " of " + model.getTotalParts());
    System.out.println("Content Type: " + model.getContentType());
}
```

---

### 2. Webservice Dispatching with Dynamic URL and JWT

```java
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;

WebserviceConfig config = new WebserviceConfig();
config.setActive(true);
config.setTargetUrl("https://inventory.example.org/api/v1/scan?clientid={clientid}");
config.setHttpMethod("POST");
config.setJwtKey("secret-hmac-key");
config.setClientId("station-01");

WebserviceDispatcher dispatcher = new WebserviceDispatcher(config);

WebserviceResponse response = dispatcher.dispatchScan(
        "shelf-A3",          // marker/location
        null,                // userText
        "Item 12345",        // nfcContent
        "E004015099887766",  // rawPayloadHex
        "E004015099887766",  // uid
        model,               // FinnishDataModel
        "session-987",       // session id
        "FEIG_MR102"         // deviceName
);

System.out.println("Dispatch status: " + response.getStatusCode());
System.out.println("Response payload: " + response.getBody());
```
