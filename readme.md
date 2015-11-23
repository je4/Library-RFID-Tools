#Library RFID Tools

![TagHandle](https://raw.githubusercontent.com/je4/Library-RFID-Tools/master/screenshot/TagHandle-01.png)

These tools support ISO 15693 compatible RFID tags which are written according to the "Finnish Data Model" (http://www.kansalliskirjasto.fi/attachments/5kSvIrHoj/5kXbVnVS7/Files/CurrentFile/RFID-DataModel-FI-20051124.pdf, the basis for ISO 28560-2)  

The following additional libraries are needed to use these tools under the Windows Operating System
* FEIG SDK for JAVA (http://www.feig.de, for FEIG ISC.MR102-USB support) 
  * fecom.dll
  * fefu.dll
  * feisc.dll
  * fetcl.dll
  * fetcp.dll
  * feusb.dll
  * OBIDISC4J.dll
  * OBIDISC4J.jar
* javacv (https://github.com/bytedeco/javacv)
* apache commons (https://commons.apache.org/)
  * commons-beanutils-1.9.2.jar
  * commons-collections4-4.0.jar
  * commons-configuration2-2.0-beta1.jar
  * commons-lang3-3.4.jar
  * commons-logging-1.2.jar
* SWT: The Standard Widget Toolkit (https://www.eclipse.org/swt/)
* jSerialComm (https://github.com/Fazecast/jSerialComm, for Elatec TWN4 Support)
  * jSerialComm-1.3.9.jar
* MySQL Connector (https://dev.mysql.com/downloads/connector/j/, if you want to use MySQL, configurable)
  * mysql-connector-java-5.1.3.6-bin.jar

##Tools
###Inventory
The Inventory Tool is used to mass-read RFIDs and write the contents into a SQL database system.
###TagHandle
The TagHandle Tool is used to provide an alternative to Biblioteca/Nedap RFID tag read/write tool. It is capable of creating a picture of the book while writing the RFID tag. To use this only a webcam is needed.

  