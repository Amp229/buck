/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)", date = "2017-11-02")
public class StreamLogs implements org.apache.thrift.TBase<StreamLogs, StreamLogs._Fields>, java.io.Serializable, Cloneable, Comparable<StreamLogs> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("StreamLogs");

  private static final org.apache.thrift.protocol.TField SLAVE_STREAM_FIELD_DESC = new org.apache.thrift.protocol.TField("slaveStream", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField LOG_LINE_BATCHES_FIELD_DESC = new org.apache.thrift.protocol.TField("logLineBatches", org.apache.thrift.protocol.TType.LIST, (short)2);
  private static final org.apache.thrift.protocol.TField ERROR_MESSAGE_FIELD_DESC = new org.apache.thrift.protocol.TField("errorMessage", org.apache.thrift.protocol.TType.STRING, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new StreamLogsStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new StreamLogsTupleSchemeFactory();

  public SlaveStream slaveStream; // optional
  public java.util.List<LogLineBatch> logLineBatches; // optional
  public java.lang.String errorMessage; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    SLAVE_STREAM((short)1, "slaveStream"),
    LOG_LINE_BATCHES((short)2, "logLineBatches"),
    ERROR_MESSAGE((short)3, "errorMessage");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // SLAVE_STREAM
          return SLAVE_STREAM;
        case 2: // LOG_LINE_BATCHES
          return LOG_LINE_BATCHES;
        case 3: // ERROR_MESSAGE
          return ERROR_MESSAGE;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final _Fields optionals[] = {_Fields.SLAVE_STREAM,_Fields.LOG_LINE_BATCHES,_Fields.ERROR_MESSAGE};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.SLAVE_STREAM, new org.apache.thrift.meta_data.FieldMetaData("slaveStream", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, SlaveStream.class)));
    tmpMap.put(_Fields.LOG_LINE_BATCHES, new org.apache.thrift.meta_data.FieldMetaData("logLineBatches", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, LogLineBatch.class))));
    tmpMap.put(_Fields.ERROR_MESSAGE, new org.apache.thrift.meta_data.FieldMetaData("errorMessage", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(StreamLogs.class, metaDataMap);
  }

  public StreamLogs() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public StreamLogs(StreamLogs other) {
    if (other.isSetSlaveStream()) {
      this.slaveStream = new SlaveStream(other.slaveStream);
    }
    if (other.isSetLogLineBatches()) {
      java.util.List<LogLineBatch> __this__logLineBatches = new java.util.ArrayList<LogLineBatch>(other.logLineBatches.size());
      for (LogLineBatch other_element : other.logLineBatches) {
        __this__logLineBatches.add(new LogLineBatch(other_element));
      }
      this.logLineBatches = __this__logLineBatches;
    }
    if (other.isSetErrorMessage()) {
      this.errorMessage = other.errorMessage;
    }
  }

  public StreamLogs deepCopy() {
    return new StreamLogs(this);
  }

  @Override
  public void clear() {
    this.slaveStream = null;
    this.logLineBatches = null;
    this.errorMessage = null;
  }

  public SlaveStream getSlaveStream() {
    return this.slaveStream;
  }

  public StreamLogs setSlaveStream(SlaveStream slaveStream) {
    this.slaveStream = slaveStream;
    return this;
  }

  public void unsetSlaveStream() {
    this.slaveStream = null;
  }

  /** Returns true if field slaveStream is set (has been assigned a value) and false otherwise */
  public boolean isSetSlaveStream() {
    return this.slaveStream != null;
  }

  public void setSlaveStreamIsSet(boolean value) {
    if (!value) {
      this.slaveStream = null;
    }
  }

  public int getLogLineBatchesSize() {
    return (this.logLineBatches == null) ? 0 : this.logLineBatches.size();
  }

  public java.util.Iterator<LogLineBatch> getLogLineBatchesIterator() {
    return (this.logLineBatches == null) ? null : this.logLineBatches.iterator();
  }

  public void addToLogLineBatches(LogLineBatch elem) {
    if (this.logLineBatches == null) {
      this.logLineBatches = new java.util.ArrayList<LogLineBatch>();
    }
    this.logLineBatches.add(elem);
  }

  public java.util.List<LogLineBatch> getLogLineBatches() {
    return this.logLineBatches;
  }

  public StreamLogs setLogLineBatches(java.util.List<LogLineBatch> logLineBatches) {
    this.logLineBatches = logLineBatches;
    return this;
  }

  public void unsetLogLineBatches() {
    this.logLineBatches = null;
  }

  /** Returns true if field logLineBatches is set (has been assigned a value) and false otherwise */
  public boolean isSetLogLineBatches() {
    return this.logLineBatches != null;
  }

  public void setLogLineBatchesIsSet(boolean value) {
    if (!value) {
      this.logLineBatches = null;
    }
  }

  public java.lang.String getErrorMessage() {
    return this.errorMessage;
  }

  public StreamLogs setErrorMessage(java.lang.String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public void unsetErrorMessage() {
    this.errorMessage = null;
  }

  /** Returns true if field errorMessage is set (has been assigned a value) and false otherwise */
  public boolean isSetErrorMessage() {
    return this.errorMessage != null;
  }

  public void setErrorMessageIsSet(boolean value) {
    if (!value) {
      this.errorMessage = null;
    }
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case SLAVE_STREAM:
      if (value == null) {
        unsetSlaveStream();
      } else {
        setSlaveStream((SlaveStream)value);
      }
      break;

    case LOG_LINE_BATCHES:
      if (value == null) {
        unsetLogLineBatches();
      } else {
        setLogLineBatches((java.util.List<LogLineBatch>)value);
      }
      break;

    case ERROR_MESSAGE:
      if (value == null) {
        unsetErrorMessage();
      } else {
        setErrorMessage((java.lang.String)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case SLAVE_STREAM:
      return getSlaveStream();

    case LOG_LINE_BATCHES:
      return getLogLineBatches();

    case ERROR_MESSAGE:
      return getErrorMessage();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case SLAVE_STREAM:
      return isSetSlaveStream();
    case LOG_LINE_BATCHES:
      return isSetLogLineBatches();
    case ERROR_MESSAGE:
      return isSetErrorMessage();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof StreamLogs)
      return this.equals((StreamLogs)that);
    return false;
  }

  public boolean equals(StreamLogs that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_slaveStream = true && this.isSetSlaveStream();
    boolean that_present_slaveStream = true && that.isSetSlaveStream();
    if (this_present_slaveStream || that_present_slaveStream) {
      if (!(this_present_slaveStream && that_present_slaveStream))
        return false;
      if (!this.slaveStream.equals(that.slaveStream))
        return false;
    }

    boolean this_present_logLineBatches = true && this.isSetLogLineBatches();
    boolean that_present_logLineBatches = true && that.isSetLogLineBatches();
    if (this_present_logLineBatches || that_present_logLineBatches) {
      if (!(this_present_logLineBatches && that_present_logLineBatches))
        return false;
      if (!this.logLineBatches.equals(that.logLineBatches))
        return false;
    }

    boolean this_present_errorMessage = true && this.isSetErrorMessage();
    boolean that_present_errorMessage = true && that.isSetErrorMessage();
    if (this_present_errorMessage || that_present_errorMessage) {
      if (!(this_present_errorMessage && that_present_errorMessage))
        return false;
      if (!this.errorMessage.equals(that.errorMessage))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetSlaveStream()) ? 131071 : 524287);
    if (isSetSlaveStream())
      hashCode = hashCode * 8191 + slaveStream.hashCode();

    hashCode = hashCode * 8191 + ((isSetLogLineBatches()) ? 131071 : 524287);
    if (isSetLogLineBatches())
      hashCode = hashCode * 8191 + logLineBatches.hashCode();

    hashCode = hashCode * 8191 + ((isSetErrorMessage()) ? 131071 : 524287);
    if (isSetErrorMessage())
      hashCode = hashCode * 8191 + errorMessage.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(StreamLogs other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetSlaveStream()).compareTo(other.isSetSlaveStream());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetSlaveStream()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.slaveStream, other.slaveStream);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetLogLineBatches()).compareTo(other.isSetLogLineBatches());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLogLineBatches()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.logLineBatches, other.logLineBatches);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetErrorMessage()).compareTo(other.isSetErrorMessage());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetErrorMessage()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.errorMessage, other.errorMessage);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("StreamLogs(");
    boolean first = true;

    if (isSetSlaveStream()) {
      sb.append("slaveStream:");
      if (this.slaveStream == null) {
        sb.append("null");
      } else {
        sb.append(this.slaveStream);
      }
      first = false;
    }
    if (isSetLogLineBatches()) {
      if (!first) sb.append(", ");
      sb.append("logLineBatches:");
      if (this.logLineBatches == null) {
        sb.append("null");
      } else {
        sb.append(this.logLineBatches);
      }
      first = false;
    }
    if (isSetErrorMessage()) {
      if (!first) sb.append(", ");
      sb.append("errorMessage:");
      if (this.errorMessage == null) {
        sb.append("null");
      } else {
        sb.append(this.errorMessage);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
    if (slaveStream != null) {
      slaveStream.validate();
    }
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class StreamLogsStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public StreamLogsStandardScheme getScheme() {
      return new StreamLogsStandardScheme();
    }
  }

  private static class StreamLogsStandardScheme extends org.apache.thrift.scheme.StandardScheme<StreamLogs> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, StreamLogs struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // SLAVE_STREAM
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.slaveStream = new SlaveStream();
              struct.slaveStream.read(iprot);
              struct.setSlaveStreamIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // LOG_LINE_BATCHES
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list8 = iprot.readListBegin();
                struct.logLineBatches = new java.util.ArrayList<LogLineBatch>(_list8.size);
                LogLineBatch _elem9;
                for (int _i10 = 0; _i10 < _list8.size; ++_i10)
                {
                  _elem9 = new LogLineBatch();
                  _elem9.read(iprot);
                  struct.logLineBatches.add(_elem9);
                }
                iprot.readListEnd();
              }
              struct.setLogLineBatchesIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // ERROR_MESSAGE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.errorMessage = iprot.readString();
              struct.setErrorMessageIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, StreamLogs struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.slaveStream != null) {
        if (struct.isSetSlaveStream()) {
          oprot.writeFieldBegin(SLAVE_STREAM_FIELD_DESC);
          struct.slaveStream.write(oprot);
          oprot.writeFieldEnd();
        }
      }
      if (struct.logLineBatches != null) {
        if (struct.isSetLogLineBatches()) {
          oprot.writeFieldBegin(LOG_LINE_BATCHES_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.logLineBatches.size()));
            for (LogLineBatch _iter11 : struct.logLineBatches)
            {
              _iter11.write(oprot);
            }
            oprot.writeListEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      if (struct.errorMessage != null) {
        if (struct.isSetErrorMessage()) {
          oprot.writeFieldBegin(ERROR_MESSAGE_FIELD_DESC);
          oprot.writeString(struct.errorMessage);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class StreamLogsTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public StreamLogsTupleScheme getScheme() {
      return new StreamLogsTupleScheme();
    }
  }

  private static class StreamLogsTupleScheme extends org.apache.thrift.scheme.TupleScheme<StreamLogs> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, StreamLogs struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetSlaveStream()) {
        optionals.set(0);
      }
      if (struct.isSetLogLineBatches()) {
        optionals.set(1);
      }
      if (struct.isSetErrorMessage()) {
        optionals.set(2);
      }
      oprot.writeBitSet(optionals, 3);
      if (struct.isSetSlaveStream()) {
        struct.slaveStream.write(oprot);
      }
      if (struct.isSetLogLineBatches()) {
        {
          oprot.writeI32(struct.logLineBatches.size());
          for (LogLineBatch _iter12 : struct.logLineBatches)
          {
            _iter12.write(oprot);
          }
        }
      }
      if (struct.isSetErrorMessage()) {
        oprot.writeString(struct.errorMessage);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, StreamLogs struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(3);
      if (incoming.get(0)) {
        struct.slaveStream = new SlaveStream();
        struct.slaveStream.read(iprot);
        struct.setSlaveStreamIsSet(true);
      }
      if (incoming.get(1)) {
        {
          org.apache.thrift.protocol.TList _list13 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.logLineBatches = new java.util.ArrayList<LogLineBatch>(_list13.size);
          LogLineBatch _elem14;
          for (int _i15 = 0; _i15 < _list13.size; ++_i15)
          {
            _elem14 = new LogLineBatch();
            _elem14.read(iprot);
            struct.logLineBatches.add(_elem14);
          }
        }
        struct.setLogLineBatchesIsSet(true);
      }
      if (incoming.get(2)) {
        struct.errorMessage = iprot.readString();
        struct.setErrorMessageIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

