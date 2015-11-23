/*******************************************************************************
 * Copyright 2015
 * Center for Information, Media and Technology (ZIMT)
 * HAWK University for Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
 *
 * This file is part of HAWK RFID Library Tools.
 * 
 * HAWK RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Diese Datei ist Teil von HAWK RFID Library Tools.
 *  
 * HAWK RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * veröffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber
 * OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License für weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.elatec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;

import com.fazecast.jSerialComm.SerialPort;

/**
 * @author Juergen Enge
 *
 */
public class ElatecRFID {

	private static final boolean DEBUG = false;

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	/** the constant 2^64 */
	public static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);
	/** the constant 2^32 */
	public static final BigInteger TWO_32 = BigInteger.ONE.shiftLeft(32);
	/** the constant 2^8 */
	public static final BigInteger TWO_8 = BigInteger.ONE.shiftLeft(8);

	public static final byte ERR_NONE = 0;
	public static final byte ERR_UNKNOWN_FUNCTION = 1;
	public static final byte ERR_MISSING_PARAMETER = 2;
	public static final byte ERR_UNUSED_PARAMETERS = 3;
	public static final byte ERR_INVALID_FUNCTION = 4;
	public static final byte ERR_PARSER = 5;

	public static final byte CRYPTO_ENV0 = 0;
	public static final byte CRYPTO_ENV1 = 1;
	public static final byte CRYPTO_ENV2 = 2;
	public static final byte CRYPTO_ENV3 = 3;
	public static final byte CRYPTO_ENV_CNT = 4;

	public static final byte CRYPTOMODE_AES128 = 0;
	public static final byte CRYPTOMODE_AES192 = 1;
	public static final byte CRYPTOMODE_AES256 = 2;
	public static final byte CRYPTOMODE_3DES = 3;
	public static final byte CRYPTOMODE_3K3DES = 4;
	public static final byte CRYPTOMODE_CBC_DES = 5;
	public static final byte CRYPTOMODE_CBC_DFN_DES = 6;
	public static final byte CRYPTOMODE_CBC_3DES = 7;
	public static final byte CRYPTOMODE_CBC_DFN_3DES = 8;
	public static final byte CRYPTOMODE_CBC_3K3DES = 9;
	public static final byte CRYPTOMODE_CBC_AES128 = 10;

	public static final byte DESF_FILETYPE_STDDATAFILE = 0;
	public static final byte DESF_FILETYPE_BACKUPDATAFILE = 1;
	public static final byte DESF_FILETYPE_VALUEFILE = 2;
	public static final byte DESF_FILETYPE_LINEARRECORDFILE = 3;
	public static final byte DESF_FILETYPE_CYCLICRECORDFILE = 4;

	public static final byte DESF_COMMSET_PLAIN = 0;
	public static final byte DESF_COMMSET_PLAIN_MACED = 1;
	public static final byte DESF_COMMSET_FULLY_ENC = 3;

	public static final byte DESF_AUTHMODE_COMPATIBLE = 0;
	public static final byte DESF_AUTHMODE_EV1 = 1;

	public static final byte DESF_KEYTYPE_3DES = 0;
	public static final byte DESF_KEYTYPE_3K3DES = 1;
	public static final byte DESF_KEYTYPE_AES = 2;

	public static final byte DESF_KEYLEN_3DES = 16;
	public static final byte DESF_KEYLEN_3K3DES = 24;
	public static final byte DESF_KEYLEN_AES = 16;

	/**
	 * 
	 */
	public ElatecRFID(AbstractConfiguration config) {
		this.config = config;

	}

	public void connect() throws Exception {
		String portString = config.getString("device.elatec.port", "Serial RFID Device");
		SerialPort[] ports = SerialPort.getCommPorts();
		for (SerialPort p : ports) {
			if (p.getDescriptivePortName().contains(portString)) {
				port = p;
			}
		}
		if( port == null ) throw new Exception( "could not find port " + portString );
		checkPort();
	}

	private boolean checkPort() {
		if (port.isOpen())
			return true;
		port.openPort();
		port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);
		return port.isOpen();
	}

	public byte[] ISO15693_ReadSingleBlock(int BlockNumber, byte BufferSize) throws Exception {
		byte[] result = callFunction((byte) 0x0D, (byte) 0x05, BlockNumber, BufferSize, 6);
		if (result[0] == 0)
			return null;
		int size = result[1];
		return Arrays.copyOfRange(result, 2, 2 + size);
	}

	public boolean ISO15693_WriteSingleBlock(int BlockNumber, byte[] data) throws Exception {
		byte[] result = callFunction((byte) 0x0D, (byte) 0x07, BlockNumber, data, 1);
		return (result[0] != 0);
	}
/*
	public ElatecDESFireKeySettings DESFire_GetKeySettings(byte CryptoEnv) throws Exception {
		byte[] result = callFunction((byte) 0x0F, (byte) 0x05, CryptoEnv);
		if (result[0] == 0)
			return null;
		return new ElatecDESFireKeySettings(Arrays.copyOfRange(result, 1, result.length));
	}

	public byte[] DESFire_ReadData(byte CryptoEnv, byte fileID, int offset, byte length, byte commSet)
			throws Exception {
		if (DEBUG)
			System.out.println("DESFire_ReadData()");
		byte[] result = callFunction((byte) 0x0F, (byte) 0x08, CryptoEnv, fileID, offset, length, commSet);
		if (result[0] == 0)
			return null;
		return Arrays.copyOfRange(result, 1, result.length);
	}

	public ElatecDESFireFileSettings DESFire_GetFileSettings(byte CryptoEnv, byte fileID, long aid) throws Exception {
		if (DEBUG)
			System.out.println("DESFire_GetFileSettings()");

		byte[] result = callFunction((byte) 0x0F, (byte) 0x07, CryptoEnv, fileID);
		if (result[0] == 0)
			return null;
		System.out.println("Filesettings Size:" + result.length);
		return new ElatecDESFireFileSettings(aid, fileID, Arrays.copyOfRange(result, 1, result.length));
	}

	public byte[] DESFire_GetFileIDs(byte CryptoEnv, byte MaxFileIDCount) throws Exception {
		if (DEBUG)
			System.out.println("DESFire_GetFileIDs()");

		byte[] result = callFunction((byte) 0x0f, (byte) 0x06, CryptoEnv, MaxFileIDCount);
		if (result[0] == 0)
			return new byte[0];
		byte num = result[1];
		return Arrays.copyOfRange(result, 2, 2 + num);
	}

	public boolean DESFire_SelectApplication(byte CryptoEnv, long AID) throws Exception {
		if (DEBUG)
			System.out.println("DESFire_SelectApplication()");

		byte[] result = callFunction((byte) 0x0f, (byte) 0x03, CryptoEnv, AID);
		return result[0] > 0;
	}

	public long[] DESFire_GetApplicationIDs(byte CryptoEnv, byte MaxAIDCnt) throws Exception {
		if (DEBUG)
			System.out.println("DESFire_GetApplicationIDs()");

		byte[] result = callFunction((byte) 0x0f, (byte) 0x00, CryptoEnv, MaxAIDCnt);
		System.out.println(bytesToHex(result));
		if (result[0] == 0)
			return new long[0];
		byte num = result[1];
		long[] ret = new long[num];
		for (int i = 0; i < (int) num; i++) {
			ret[i] = LSBBytesToLong(Arrays.copyOfRange(result, i * 4 + 2, i * 4 + 2 + 4));
		}
		return ret;
	}

	public byte[] DESFire_GetCardUID(byte CryptoEnv, byte BufferSize) throws Exception {
		if (DEBUG)
			System.out.println("DESFire_GetCardUID()");

		byte[] ret = callFunction((byte) 0x0F, (byte) 0x16, CryptoEnv, BufferSize);
		if (ret[0] == 0)
			return new byte[0];
		byte length = ret[1];
		return Arrays.copyOfRange(ret, 2, 2 + length);
	}

	public boolean DESFire_Authenticate(byte CryptoEnv, byte KeyNoTag, byte[] Key, byte KeyType, byte Mode)
			throws Exception {
		if (DEBUG)
			System.out.println("DESFire_Authenticate()");

		byte[] ret = callFunction((byte) 0x0F, (byte) 0x04, CryptoEnv, KeyNoTag, Key, KeyType, Mode);
		return ret[0] > 0;
	}
*/
	public void reset() throws Exception {
		if (DEBUG)
			System.out.println("reset()");

		callFunction((byte) 0x00, (byte) 0x01, -1);
	}

	public String GetVersionString() throws Exception {
		if (DEBUG)
			System.out.println("GetVersionString()");

		byte MaxLen = 30;
		byte[] buffer = callFunction((byte) 0x00, (byte) 0x04, (byte) MaxLen, MaxLen+1);

		String ret = new String(buffer).trim();
		return ret;
	}

	public void SetTagTypes(long tagTypesLF, long tagTypesHF) throws Exception {
		if (DEBUG)
			System.out.println("SetTagTypes()");

		callFunction((byte) 0x05, (byte) 0x02, tagTypesLF, tagTypesHF, 0);
	}

	public ElatecTag SearchTag() throws Exception {
		return SearchTag((byte) 16);
	}

	public ElatecTag SearchTag(byte MaxIDBytes) throws Exception {
		if (DEBUG)
			System.out.println("SearchTag()");
		// Response: 00 01 82 40 08 E0 04 01 50 2D 42 10 E3
		byte[] data = callFunction((byte) 0x05, (byte) 0x00, MaxIDBytes, MaxIDBytes+5);
		if (data == null)
			return null;
		return new ElatecTag(data);
	}

	public String DESFire_GetVersion(byte cryptoEnv) throws Exception {
		if (DEBUG)
			System.out.println("DESFire_GetVersion()");

		byte[] buffer = callFunction((byte) 0x0F, (byte) 0x12, cryptoEnv);
		if (buffer == null)
			return null;
		return bytesToHex(buffer);
	}

	private byte[] getResult(int resultSize) throws Exception {
		if (!checkPort())
			throw new Exception(port.getDescriptivePortName() + "disconnected");

/*
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		int bytesAvailable = 0;

		// wait for data
		Thread.sleep(80);
		int count = 0;
		while ((bytesAvailable = port.bytesAvailable()) == 0) {
			Thread.sleep(20);
			count++;
			if (count > 100)
				break;
		}
		// read all
		// Thread.sleep(millis);
		while ((bytesAvailable = port.bytesAvailable()) > 0) {
			byte[] buf = new byte[bytesAvailable];
			int numBytes = port.readBytes(buf, bytesAvailable);
			buffer.put(buf);
		}
		
		buffer.flip();
		for (byte b : buffer.array()) {
*/
		String hexString = "";
		// 2 char per byte, execution level, result, carriage return
		byte[] buf = new byte[resultSize*2+2+1];
		int numBytes = port.readBytes(buf, resultSize*2+2+1);
		
		for (byte b : buf) {
//			if (b == 0x0D) {
//				break;
//			}
			hexString += (char) (b & 0xFF);
		}
		byte[] result = hexStringToByteArray(hexString.trim());

		if (result[0] != 0)
			throw new ElatecException(result[0]);

		if (result.length <= 1)
			return null;
		return Arrays.copyOfRange(result, 1, result.length);
	}

	private byte[] executeHexString(String command, int resultSize) throws Exception {
		if (!checkPort())
			throw new Exception(port.getDescriptivePortName() + "disconnected");
		byte[] hexBytes = command.getBytes();
		int numWrite = port.writeBytes(hexBytes, hexBytes.length);
		if (numWrite != hexBytes.length)
			throw new ElatecException((byte) 6);
		
		if( resultSize < 0 ) return null;
		return getResult(resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2 }) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, byte bParam, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2, bParam }) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, byte bParam1, byte bParam2, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2, bParam1, bParam2 }) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, byte bParam1, byte bParam2, byte[] bList, byte bParam3,
			byte bParam4, int resultSize) throws Exception {
		byte[] bytes1 = new byte[] { func1, func2, bParam1, bParam2, (byte) bList.length };
		byte[] bytes2 = new byte[bytes1.length + bList.length + 2];
		System.arraycopy(bytes1, 0, bytes2, 0, bytes1.length);
		System.arraycopy(bList, 0, bytes2, bytes1.length, bList.length);
		bytes2[bytes2.length - 2] = bParam3;
		bytes2[bytes2.length - 1] = bParam4;

		String hexString = bytesToHex(bytes2) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, int iParam, byte[] bList, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2 }) + intToLSBHex(iParam)
				+ bytesToHex(new byte[] { (byte) bList.length }) + bytesToHex(bList) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, byte bParam1, long lParam1, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2, bParam1 }) + longToLSBHex(lParam1) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, long lParam1, long lParam2, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2 }) + longToLSBHex(lParam1) + longToLSBHex(lParam2)
				+ (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, int iParam1, byte bParam1, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2 }) + intToLSBHex(iParam1)
				+ bytesToHex(new byte[] { bParam1 }) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	private byte[] callFunction(byte func1, byte func2, byte bParam1, byte bParam2, int iParam, byte bParam3,
			byte bParam4, int resultSize) throws Exception {
		String hexString = bytesToHex(new byte[] { func1, func2, bParam1, bParam2 }) + intToHex(iParam)
				+ bytesToHex(new byte[] { bParam3, bParam4 }) + (char) 0x0D;
		return executeHexString(hexString, resultSize);
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static String longToHex(long l) {
		BigInteger b = BigInteger.valueOf(l);
		if (b.signum() < 0)
			b = b.add(TWO_64);
		String str = b.toString(16);
		if (str.length() == 16)
			str = str.substring(8);
		while (str.length() < 8)
			str = "0" + str;
		return str;
	}

	public static String longToLSBHex(long l) {
		String str = longToHex(l);
		String ret = "";
		for (int i = 0; i < 4; i++) {
			ret += str.substring((3 - i) * 2, (3 - i) * 2 + 2);
		}
		return ret;
	}

	public static String intToHex(int i) {
		BigInteger b = BigInteger.valueOf(i);
		if (b.signum() < 0)
			b = b.add(TWO_64);
		String str = b.toString(16);
		if (str.length() == 16)
			str = str.substring(12);
		while (str.length() < 4)
			str = "0" + str;
		return str;
	}

	public static String intToLSBHex(int i) {
		String str = intToHex(i);
		String ret = "";
		for (int j = 0; j < 2; j++) {
			ret += str.substring((1 - j) * 2, (1 - j) * 2 + 2);
		}
		return ret;
	}

	public static long LSBHexToLong(String str) {
		String ret = "";
		for (int i = 0; i < 4; i++) {
			ret += str.substring((3 - i) * 2, (3 - i) * 2 + 2);
		}

		return (new BigInteger(ret, 16)).longValue();
	}

	public static long LSBBytesToLong(byte[] b) {
		return LSBHexToLong(bytesToHex(b));
	}

	public static int LSBHexToInt(String str) {
		String ret = "";
		for (int i = 0; i < 2; i++) {
			ret += str.substring((1 - i) * 2, (1 - i) * 2 + 2);
		}

		return (new BigInteger(ret, 16)).intValue();
	}

	public static int LSBBytesToInt(byte[] b) {
		return LSBHexToInt(bytesToHex(b));
	}

	public static long LSBHexToLong(byte[] b) {
		return LSBHexToLong(new String(b));
	}

	public static String hexDump(byte[] data, int bpl) {
		String ret = "";
		int p = 0;
		String asc = "";
		for (byte b : data) {
			if (p % bpl == 0) {
				if (asc.length() > 0) {
					ret += "   " + asc + "\n";
				}
				asc = "";
				p = 0;
			}
			ret += String.format("%02x ", b);
			if (b < 32)
				asc += ".";
			else
				asc += (char) b;
			p++;
		}
		while (p < bpl - 1) {
			ret += "   ";
			p++;
		}
		ret += "   " + asc + "\n";
		return ret;
	}

	private AbstractConfiguration config;
	private OutputStream out = null;
	private InputStream in = null;
	private SerialPort port = null;
}
