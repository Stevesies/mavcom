/**
 * Generated class : msg_param_ext_request_read
 * DO NOT MODIFY!
 **/
package org.mavlink.messages.lquac;
import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.IMAVLinkCRC;
import org.mavlink.MAVLinkCRC;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.mavlink.io.LittleEndianDataInputStream;
import org.mavlink.io.LittleEndianDataOutputStream;
/**
 * Class msg_param_ext_request_read
 * Request to read the value of a parameter with either the param_id string id or param_index. PARAM_EXT_VALUE or PARAM_EXT_VALUE_TRIMMED should be emitted in response (see field: trimmed).
 **/
public class msg_param_ext_request_read extends MAVLinkMessage {
  public static final int MAVLINK_MSG_ID_PARAM_EXT_REQUEST_READ = 320;
  private static final long serialVersionUID = MAVLINK_MSG_ID_PARAM_EXT_REQUEST_READ;
  public msg_param_ext_request_read() {
    this(1,1);
}
  public msg_param_ext_request_read(int sysId, int componentId) {
    messageType = MAVLINK_MSG_ID_PARAM_EXT_REQUEST_READ;
    this.sysId = sysId;
    this.componentId = componentId;
    payload_length = 21;
}

  /**
   * Parameter index. Set to -1 to use the Parameter ID field as identifier (else param_id will be ignored)
   */
  public int param_index;
  /**
   * System ID
   */
  public int target_system;
  /**
   * Component ID
   */
  public int target_component;
  /**
   * Parameter id, terminated by NULL if the length is less than 16 human-readable chars and WITHOUT null termination (NULL) byte if the length is exactly 16 chars - applications have to provide 16+1 bytes storage if the ID is stored as string
   */
  public char[] param_id = new char[16];
  public void setParam_id(String tmp) {
    int len = Math.min(tmp.length(), 16);
    for (int i=0; i<len; i++) {
      param_id[i] = tmp.charAt(i);
    }
    for (int i=len; i<16; i++) {
      param_id[i] = 0;
    }
  }
  public String getParam_id() {
    String result="";
    for (int i=0; i<16; i++) {
      if (param_id[i] != 0) result=result+param_id[i]; else break;
    }
    return result;
  }
  /**
   * Request _TRIMMED variants of PARAM_EXT_ messages. Set to 1 if _TRIMMED message variants are supported, and 0 otherwise. This signals the recipient that _TRIMMED messages are supported by the sender (and should be used if supported by the recipient).
   */
  public int trimmed;
/**
 * Decode message with raw data
 */
public void decode(LittleEndianDataInputStream dis) throws IOException {
  param_index = (int)dis.readShort();
  target_system = (int)dis.readUnsignedByte()&0x00FF;
  target_component = (int)dis.readUnsignedByte()&0x00FF;
  for (int i=0; i<16; i++) {
    param_id[i] = (char)dis.readByte();
  }
  trimmed = (int)dis.readUnsignedByte()&0x00FF;
}
/**
 * Encode message with raw data and other informations
 */
public byte[] encode() throws IOException {
  byte[] buffer = new byte[12+21];
   LittleEndianDataOutputStream dos = new LittleEndianDataOutputStream(new ByteArrayOutputStream());
  dos.writeByte((byte)0xFD);
  dos.writeByte(payload_length & 0x00FF);
  dos.writeByte(incompat & 0x00FF);
  dos.writeByte(compat & 0x00FF);
  dos.writeByte(packet & 0x00FF);
  dos.writeByte(sysId & 0x00FF);
  dos.writeByte(componentId & 0x00FF);
  dos.writeByte(messageType & 0x00FF);
  dos.writeByte((messageType >> 8) & 0x00FF);
  dos.writeByte((messageType >> 16) & 0x00FF);
  dos.writeShort(param_index&0x00FFFF);
  dos.writeByte(target_system&0x00FF);
  dos.writeByte(target_component&0x00FF);
  for (int i=0; i<16; i++) {
    dos.writeByte(param_id[i]);
  }
  dos.writeByte(trimmed&0x00FF);
  dos.flush();
  byte[] tmp = dos.toByteArray();
  for (int b=0; b<tmp.length; b++) buffer[b]=tmp[b];
  int crc = MAVLinkCRC.crc_calculate_encode(buffer, 21);
  crc = MAVLinkCRC.crc_accumulate((byte) IMAVLinkCRC.MAVLINK_MESSAGE_CRCS[messageType], crc);
  byte crcl = (byte) (crc & 0x00FF);
  byte crch = (byte) ((crc >> 8) & 0x00FF);
  buffer[31] = crcl;
  buffer[32] = crch;
  dos.close();
  return buffer;
}
public String toString() {
return "MAVLINK_MSG_ID_PARAM_EXT_REQUEST_READ : " +   "  param_index="+param_index
+  "  target_system="+target_system
+  "  target_component="+target_component
+  "  param_id="+getParam_id()
+  "  trimmed="+trimmed
;}
}
