#Library RFID Tools

![TagHandle](https://raw.githubusercontent.com/je4/Library-RFID-Tools/master/screenshot/TagHandle-01.png)

These tools support ISO 15693 compatible RFID tags which are written according to the "Finnish Data Model" (http://www.kansalliskirjasto.fi/attachments/5kSvIrHoj/5kXbVnVS7/Files/CurrentFile/RFID-DataModel-FI-20051124.pdf, the basis for ISO 28560-2)  

The following additional libraries/SDKs are needed to use these tools under the Windows Operating System:
* FEIG SDK for JAVA (v5.6.3, located in `ID_ISC.SDK.Java.Win-V5.6.3`, for FEIG ISC.MR102-USB support) 
  * fecom.dll
  * fefu.dll
  * feisc.dll
  * fetcl.dll
  * fetcp.dll
  * feusb.dll
  * OBIDISC4J.dll
  * OBIDISC4J.jar
  * OBIDISC4J_API.jar
* javacv (https://github.com/bytedeco/javacv)
* apache commons (configuration2, collections4, lang3)
* SLF4J / Logback (Logging)
* SWT: The Standard Widget Toolkit (https://www.eclipse.org/swt/)
* Opal Project (https://github.com/lcaron/opal, for RangeSlider)
* MySQL Connector/J (https://dev.mysql.com/downloads/connector/j/)

## Build and Modernization
The project has been modernized:
* Maven build system configured with Java 17/21 compatibility.
* FEIG SDK v5.6.3 integration (x64 native support).
* Deprecated Elatec support removed.
* Modern logging and ISO 28560 / Finnish Data Model unit tests.

##Tools
###Inventory
The Inventory Tool is used to mass-read RFIDs and write the contents into a SQL database system.
![Inventory](https://raw.githubusercontent.com/je4/Library-RFID-Tools/master/screenshot/Inventory-01.png)
###TagHandle
The TagHandle Tool is used to provide an alternative to Biblioteca/Nedap RFID tag read/write tool. It is capable of creating a picture of the book while writing the RFID tag. To use this only a webcam is needed.

  