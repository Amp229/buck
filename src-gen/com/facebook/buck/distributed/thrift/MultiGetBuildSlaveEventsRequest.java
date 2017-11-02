/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)", date = "2017-11-02")
public class MultiGetBuildSlaveEventsRequest implements org.apache.thrift.TBase<MultiGetBuildSlaveEventsRequest, MultiGetBuildSlaveEventsRequest._Fields>, java.io.Serializable, Cloneable, Comparable<MultiGetBuildSlaveEventsRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("MultiGetBuildSlaveEventsRequest");

  private static final org.apache.thrift.protocol.TField REQUESTS_FIELD_DESC = new org.apache.thrift.protocol.TField("requests", org.apache.thrift.protocol.TType.LIST, (short)1);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new MultiGetBuildSlaveEventsRequestStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new MultiGetBuildSlaveEventsRequestTupleSchemeFactory();

  public java.util.List<BuildSlaveEventsQuery> requests; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    REQUESTS((short)1, "requests");

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
        case 1: // REQUESTS
          return REQUESTS;
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
  private static final _Fields optionals[] = {_Fields.REQUESTS};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.REQUESTS, new org.apache.thrift.meta_data.FieldMetaData("requests", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, BuildSlaveEventsQuery.class))));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(MultiGetBuildSlaveEventsRequest.class, metaDataMap);
  }

  public MultiGetBuildSlaveEventsRequest() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public MultiGetBuildSlaveEventsRequest(MultiGetBuildSlaveEventsRequest other) {
    if (other.isSetRequests()) {
      java.util.List<BuildSlaveEventsQuery> __this__requests = new java.util.ArrayList<BuildSlaveEventsQuery>(other.requests.size());
      for (BuildSlaveEventsQuery other_element : other.requests) {
        __this__requests.add(new BuildSlaveEventsQuery(other_element));
      }
      this.requests = __this__requests;
    }
  }

  public MultiGetBuildSlaveEventsRequest deepCopy() {
    return new MultiGetBuildSlaveEventsRequest(this);
  }

  @Override
  public void clear() {
    this.requests = null;
  }

  public int getRequestsSize() {
    return (this.requests == null) ? 0 : this.requests.size();
  }

  public java.util.Iterator<BuildSlaveEventsQuery> getRequestsIterator() {
    return (this.requests == null) ? null : this.requests.iterator();
  }

  public void addToRequests(BuildSlaveEventsQuery elem) {
    if (this.requests == null) {
      this.requests = new java.util.ArrayList<BuildSlaveEventsQuery>();
    }
    this.requests.add(elem);
  }

  public java.util.List<BuildSlaveEventsQuery> getRequests() {
    return this.requests;
  }

  public MultiGetBuildSlaveEventsRequest setRequests(java.util.List<BuildSlaveEventsQuery> requests) {
    this.requests = requests;
    return this;
  }

  public void unsetRequests() {
    this.requests = null;
  }

  /** Returns true if field requests is set (has been assigned a value) and false otherwise */
  public boolean isSetRequests() {
    return this.requests != null;
  }

  public void setRequestsIsSet(boolean value) {
    if (!value) {
      this.requests = null;
    }
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case REQUESTS:
      if (value == null) {
        unsetRequests();
      } else {
        setRequests((java.util.List<BuildSlaveEventsQuery>)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case REQUESTS:
      return getRequests();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case REQUESTS:
      return isSetRequests();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof MultiGetBuildSlaveEventsRequest)
      return this.equals((MultiGetBuildSlaveEventsRequest)that);
    return false;
  }

  public boolean equals(MultiGetBuildSlaveEventsRequest that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_requests = true && this.isSetRequests();
    boolean that_present_requests = true && that.isSetRequests();
    if (this_present_requests || that_present_requests) {
      if (!(this_present_requests && that_present_requests))
        return false;
      if (!this.requests.equals(that.requests))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetRequests()) ? 131071 : 524287);
    if (isSetRequests())
      hashCode = hashCode * 8191 + requests.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(MultiGetBuildSlaveEventsRequest other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetRequests()).compareTo(other.isSetRequests());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetRequests()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.requests, other.requests);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("MultiGetBuildSlaveEventsRequest(");
    boolean first = true;

    if (isSetRequests()) {
      sb.append("requests:");
      if (this.requests == null) {
        sb.append("null");
      } else {
        sb.append(this.requests);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
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

  private static class MultiGetBuildSlaveEventsRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public MultiGetBuildSlaveEventsRequestStandardScheme getScheme() {
      return new MultiGetBuildSlaveEventsRequestStandardScheme();
    }
  }

  private static class MultiGetBuildSlaveEventsRequestStandardScheme extends org.apache.thrift.scheme.StandardScheme<MultiGetBuildSlaveEventsRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, MultiGetBuildSlaveEventsRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // REQUESTS
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list146 = iprot.readListBegin();
                struct.requests = new java.util.ArrayList<BuildSlaveEventsQuery>(_list146.size);
                BuildSlaveEventsQuery _elem147;
                for (int _i148 = 0; _i148 < _list146.size; ++_i148)
                {
                  _elem147 = new BuildSlaveEventsQuery();
                  _elem147.read(iprot);
                  struct.requests.add(_elem147);
                }
                iprot.readListEnd();
              }
              struct.setRequestsIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, MultiGetBuildSlaveEventsRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.requests != null) {
        if (struct.isSetRequests()) {
          oprot.writeFieldBegin(REQUESTS_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.requests.size()));
            for (BuildSlaveEventsQuery _iter149 : struct.requests)
            {
              _iter149.write(oprot);
            }
            oprot.writeListEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class MultiGetBuildSlaveEventsRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public MultiGetBuildSlaveEventsRequestTupleScheme getScheme() {
      return new MultiGetBuildSlaveEventsRequestTupleScheme();
    }
  }

  private static class MultiGetBuildSlaveEventsRequestTupleScheme extends org.apache.thrift.scheme.TupleScheme<MultiGetBuildSlaveEventsRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, MultiGetBuildSlaveEventsRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetRequests()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetRequests()) {
        {
          oprot.writeI32(struct.requests.size());
          for (BuildSlaveEventsQuery _iter150 : struct.requests)
          {
            _iter150.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, MultiGetBuildSlaveEventsRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        {
          org.apache.thrift.protocol.TList _list151 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.requests = new java.util.ArrayList<BuildSlaveEventsQuery>(_list151.size);
          BuildSlaveEventsQuery _elem152;
          for (int _i153 = 0; _i153 < _list151.size; ++_i153)
          {
            _elem152 = new BuildSlaveEventsQuery();
            _elem152.read(iprot);
            struct.requests.add(_elem152);
          }
        }
        struct.setRequestsIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

