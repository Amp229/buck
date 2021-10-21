// @generated
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: remoteexecution/proto/metadata.proto

package com.facebook.buck.remoteexecution.proto;

/**
 * Protobuf type {@code facebook.remote_execution.CasClientInfo}
 */
@javax.annotation.Generated(value="protoc", comments="annotations:CasClientInfo.java.pb.meta")
public  final class CasClientInfo extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:facebook.remote_execution.CasClientInfo)
    CasClientInfoOrBuilder {
private static final long serialVersionUID = 0L;
  // Use CasClientInfo.newBuilder() to construct.
  private CasClientInfo(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private CasClientInfo() {
    name_ = "";
    jobId_ = "";
    streamingMode_ = 0;
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private CasClientInfo(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields =
        com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 10: {
            java.lang.String s = input.readStringRequireUtf8();

            name_ = s;
            break;
          }
          case 18: {
            java.lang.String s = input.readStringRequireUtf8();

            jobId_ = s;
            break;
          }
          case 24: {
            int rawValue = input.readEnum();

            streamingMode_ = rawValue;
            break;
          }
          case 34: {
            com.facebook.buck.remoteexecution.proto.ManifoldBucket.Builder subBuilder = null;
            if (manifoldBucket_ != null) {
              subBuilder = manifoldBucket_.toBuilder();
            }
            manifoldBucket_ = input.readMessage(com.facebook.buck.remoteexecution.proto.ManifoldBucket.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(manifoldBucket_);
              manifoldBucket_ = subBuilder.buildPartial();
            }

            break;
          }
          default: {
            if (!parseUnknownField(
                input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadataProto.internal_static_facebook_remote_execution_CasClientInfo_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadataProto.internal_static_facebook_remote_execution_CasClientInfo_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.facebook.buck.remoteexecution.proto.CasClientInfo.class, com.facebook.buck.remoteexecution.proto.CasClientInfo.Builder.class);
  }

  /**
   * Protobuf enum {@code facebook.remote_execution.CasClientInfo.CasStreamingMode}
   */
  public enum CasStreamingMode
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>BUCKETS = 0;</code>
     */
    BUCKETS(0),
    /**
     * <code>HTTP = 1;</code>
     */
    HTTP(1),
    UNRECOGNIZED(-1),
    ;

    /**
     * <code>BUCKETS = 0;</code>
     */
    public static final int BUCKETS_VALUE = 0;
    /**
     * <code>HTTP = 1;</code>
     */
    public static final int HTTP_VALUE = 1;


    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new java.lang.IllegalArgumentException(
            "Can't get the number of an unknown enum value.");
      }
      return value;
    }

    /**
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static CasStreamingMode valueOf(int value) {
      return forNumber(value);
    }

    public static CasStreamingMode forNumber(int value) {
      switch (value) {
        case 0: return BUCKETS;
        case 1: return HTTP;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<CasStreamingMode>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        CasStreamingMode> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<CasStreamingMode>() {
            public CasStreamingMode findValueByNumber(int number) {
              return CasStreamingMode.forNumber(number);
            }
          };

    public final com.google.protobuf.Descriptors.EnumValueDescriptor
        getValueDescriptor() {
      return getDescriptor().getValues().get(ordinal());
    }
    public final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptorForType() {
      return getDescriptor();
    }
    public static final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptor() {
      return com.facebook.buck.remoteexecution.proto.CasClientInfo.getDescriptor().getEnumTypes().get(0);
    }

    private static final CasStreamingMode[] VALUES = values();

    public static CasStreamingMode valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      if (desc.getIndex() == -1) {
        return UNRECOGNIZED;
      }
      return VALUES[desc.getIndex()];
    }

    private final int value;

    private CasStreamingMode(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:facebook.remote_execution.CasClientInfo.CasStreamingMode)
  }

  public static final int NAME_FIELD_NUMBER = 1;
  private volatile java.lang.Object name_;
  /**
   * <pre>
   * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
   * </pre>
   *
   * <code>string name = 1;</code>
   */
  public java.lang.String getName() {
    java.lang.Object ref = name_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      name_ = s;
      return s;
    }
  }
  /**
   * <pre>
   * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
   * </pre>
   *
   * <code>string name = 1;</code>
   */
  public com.google.protobuf.ByteString
      getNameBytes() {
    java.lang.Object ref = name_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      name_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int JOB_ID_FIELD_NUMBER = 2;
  private volatile java.lang.Object jobId_;
  /**
   * <pre>
   * This is the execution id from the worker or sandcastle id
   * </pre>
   *
   * <code>string job_id = 2;</code>
   */
  public java.lang.String getJobId() {
    java.lang.Object ref = jobId_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      jobId_ = s;
      return s;
    }
  }
  /**
   * <pre>
   * This is the execution id from the worker or sandcastle id
   * </pre>
   *
   * <code>string job_id = 2;</code>
   */
  public com.google.protobuf.ByteString
      getJobIdBytes() {
    java.lang.Object ref = jobId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      jobId_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int STREAMING_MODE_FIELD_NUMBER = 3;
  private int streamingMode_;
  /**
   * <pre>
   * Passed from the client to decide which streaming mode to use
   * </pre>
   *
   * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
   */
  public int getStreamingModeValue() {
    return streamingMode_;
  }
  /**
   * <pre>
   * Passed from the client to decide which streaming mode to use
   * </pre>
   *
   * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
   */
  public com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode getStreamingMode() {
    @SuppressWarnings("deprecation")
    com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode result = com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode.valueOf(streamingMode_);
    return result == null ? com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode.UNRECOGNIZED : result;
  }

  public static final int MANIFOLD_BUCKET_FIELD_NUMBER = 4;
  private com.facebook.buck.remoteexecution.proto.ManifoldBucket manifoldBucket_;
  /**
   * <pre>
   * This allows overriding the manifold bucket
   * </pre>
   *
   * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
   */
  public boolean hasManifoldBucket() {
    return manifoldBucket_ != null;
  }
  /**
   * <pre>
   * This allows overriding the manifold bucket
   * </pre>
   *
   * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
   */
  public com.facebook.buck.remoteexecution.proto.ManifoldBucket getManifoldBucket() {
    return manifoldBucket_ == null ? com.facebook.buck.remoteexecution.proto.ManifoldBucket.getDefaultInstance() : manifoldBucket_;
  }
  /**
   * <pre>
   * This allows overriding the manifold bucket
   * </pre>
   *
   * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
   */
  public com.facebook.buck.remoteexecution.proto.ManifoldBucketOrBuilder getManifoldBucketOrBuilder() {
    return getManifoldBucket();
  }

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (!getNameBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, name_);
    }
    if (!getJobIdBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, jobId_);
    }
    if (streamingMode_ != com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode.BUCKETS.getNumber()) {
      output.writeEnum(3, streamingMode_);
    }
    if (manifoldBucket_ != null) {
      output.writeMessage(4, getManifoldBucket());
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getNameBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, name_);
    }
    if (!getJobIdBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, jobId_);
    }
    if (streamingMode_ != com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode.BUCKETS.getNumber()) {
      size += com.google.protobuf.CodedOutputStream
        .computeEnumSize(3, streamingMode_);
    }
    if (manifoldBucket_ != null) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(4, getManifoldBucket());
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof com.facebook.buck.remoteexecution.proto.CasClientInfo)) {
      return super.equals(obj);
    }
    com.facebook.buck.remoteexecution.proto.CasClientInfo other = (com.facebook.buck.remoteexecution.proto.CasClientInfo) obj;

    if (!getName()
        .equals(other.getName())) return false;
    if (!getJobId()
        .equals(other.getJobId())) return false;
    if (streamingMode_ != other.streamingMode_) return false;
    if (hasManifoldBucket() != other.hasManifoldBucket()) return false;
    if (hasManifoldBucket()) {
      if (!getManifoldBucket()
          .equals(other.getManifoldBucket())) return false;
    }
    if (!unknownFields.equals(other.unknownFields)) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    hash = (37 * hash) + NAME_FIELD_NUMBER;
    hash = (53 * hash) + getName().hashCode();
    hash = (37 * hash) + JOB_ID_FIELD_NUMBER;
    hash = (53 * hash) + getJobId().hashCode();
    hash = (37 * hash) + STREAMING_MODE_FIELD_NUMBER;
    hash = (53 * hash) + streamingMode_;
    if (hasManifoldBucket()) {
      hash = (37 * hash) + MANIFOLD_BUCKET_FIELD_NUMBER;
      hash = (53 * hash) + getManifoldBucket().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.facebook.buck.remoteexecution.proto.CasClientInfo parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.facebook.buck.remoteexecution.proto.CasClientInfo prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code facebook.remote_execution.CasClientInfo}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:facebook.remote_execution.CasClientInfo)
      com.facebook.buck.remoteexecution.proto.CasClientInfoOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadataProto.internal_static_facebook_remote_execution_CasClientInfo_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadataProto.internal_static_facebook_remote_execution_CasClientInfo_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.facebook.buck.remoteexecution.proto.CasClientInfo.class, com.facebook.buck.remoteexecution.proto.CasClientInfo.Builder.class);
    }

    // Construct using com.facebook.buck.remoteexecution.proto.CasClientInfo.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      name_ = "";

      jobId_ = "";

      streamingMode_ = 0;

      if (manifoldBucketBuilder_ == null) {
        manifoldBucket_ = null;
      } else {
        manifoldBucket_ = null;
        manifoldBucketBuilder_ = null;
      }
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadataProto.internal_static_facebook_remote_execution_CasClientInfo_descriptor;
    }

    @java.lang.Override
    public com.facebook.buck.remoteexecution.proto.CasClientInfo getDefaultInstanceForType() {
      return com.facebook.buck.remoteexecution.proto.CasClientInfo.getDefaultInstance();
    }

    @java.lang.Override
    public com.facebook.buck.remoteexecution.proto.CasClientInfo build() {
      com.facebook.buck.remoteexecution.proto.CasClientInfo result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.facebook.buck.remoteexecution.proto.CasClientInfo buildPartial() {
      com.facebook.buck.remoteexecution.proto.CasClientInfo result = new com.facebook.buck.remoteexecution.proto.CasClientInfo(this);
      result.name_ = name_;
      result.jobId_ = jobId_;
      result.streamingMode_ = streamingMode_;
      if (manifoldBucketBuilder_ == null) {
        result.manifoldBucket_ = manifoldBucket_;
      } else {
        result.manifoldBucket_ = manifoldBucketBuilder_.build();
      }
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }
    @java.lang.Override
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return super.setField(field, value);
    }
    @java.lang.Override
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }
    @java.lang.Override
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }
    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }
    @java.lang.Override
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }
    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.facebook.buck.remoteexecution.proto.CasClientInfo) {
        return mergeFrom((com.facebook.buck.remoteexecution.proto.CasClientInfo)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.facebook.buck.remoteexecution.proto.CasClientInfo other) {
      if (other == com.facebook.buck.remoteexecution.proto.CasClientInfo.getDefaultInstance()) return this;
      if (!other.getName().isEmpty()) {
        name_ = other.name_;
        onChanged();
      }
      if (!other.getJobId().isEmpty()) {
        jobId_ = other.jobId_;
        onChanged();
      }
      if (other.streamingMode_ != 0) {
        setStreamingModeValue(other.getStreamingModeValue());
      }
      if (other.hasManifoldBucket()) {
        mergeManifoldBucket(other.getManifoldBucket());
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      com.facebook.buck.remoteexecution.proto.CasClientInfo parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.facebook.buck.remoteexecution.proto.CasClientInfo) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object name_ = "";
    /**
     * <pre>
     * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
     * </pre>
     *
     * <code>string name = 1;</code>
     */
    public java.lang.String getName() {
      java.lang.Object ref = name_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        name_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
     * </pre>
     *
     * <code>string name = 1;</code>
     */
    public com.google.protobuf.ByteString
        getNameBytes() {
      java.lang.Object ref = name_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        name_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
     * </pre>
     *
     * <code>string name = 1;</code>
     */
    public Builder setName(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      name_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
     * </pre>
     *
     * <code>string name = 1;</code>
     */
    public Builder clearName() {
      
      name_ = getDefaultInstance().getName();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Name of the tool reaching the CAS, eg, buck, worker, engine, ...
     * </pre>
     *
     * <code>string name = 1;</code>
     */
    public Builder setNameBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      name_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object jobId_ = "";
    /**
     * <pre>
     * This is the execution id from the worker or sandcastle id
     * </pre>
     *
     * <code>string job_id = 2;</code>
     */
    public java.lang.String getJobId() {
      java.lang.Object ref = jobId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        jobId_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * This is the execution id from the worker or sandcastle id
     * </pre>
     *
     * <code>string job_id = 2;</code>
     */
    public com.google.protobuf.ByteString
        getJobIdBytes() {
      java.lang.Object ref = jobId_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        jobId_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * This is the execution id from the worker or sandcastle id
     * </pre>
     *
     * <code>string job_id = 2;</code>
     */
    public Builder setJobId(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      jobId_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * This is the execution id from the worker or sandcastle id
     * </pre>
     *
     * <code>string job_id = 2;</code>
     */
    public Builder clearJobId() {
      
      jobId_ = getDefaultInstance().getJobId();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * This is the execution id from the worker or sandcastle id
     * </pre>
     *
     * <code>string job_id = 2;</code>
     */
    public Builder setJobIdBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      jobId_ = value;
      onChanged();
      return this;
    }

    private int streamingMode_ = 0;
    /**
     * <pre>
     * Passed from the client to decide which streaming mode to use
     * </pre>
     *
     * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
     */
    public int getStreamingModeValue() {
      return streamingMode_;
    }
    /**
     * <pre>
     * Passed from the client to decide which streaming mode to use
     * </pre>
     *
     * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
     */
    public Builder setStreamingModeValue(int value) {
      streamingMode_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Passed from the client to decide which streaming mode to use
     * </pre>
     *
     * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
     */
    public com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode getStreamingMode() {
      @SuppressWarnings("deprecation")
      com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode result = com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode.valueOf(streamingMode_);
      return result == null ? com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode.UNRECOGNIZED : result;
    }
    /**
     * <pre>
     * Passed from the client to decide which streaming mode to use
     * </pre>
     *
     * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
     */
    public Builder setStreamingMode(com.facebook.buck.remoteexecution.proto.CasClientInfo.CasStreamingMode value) {
      if (value == null) {
        throw new NullPointerException();
      }
      
      streamingMode_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Passed from the client to decide which streaming mode to use
     * </pre>
     *
     * <code>.facebook.remote_execution.CasClientInfo.CasStreamingMode streaming_mode = 3;</code>
     */
    public Builder clearStreamingMode() {
      
      streamingMode_ = 0;
      onChanged();
      return this;
    }

    private com.facebook.buck.remoteexecution.proto.ManifoldBucket manifoldBucket_;
    private com.google.protobuf.SingleFieldBuilderV3<
        com.facebook.buck.remoteexecution.proto.ManifoldBucket, com.facebook.buck.remoteexecution.proto.ManifoldBucket.Builder, com.facebook.buck.remoteexecution.proto.ManifoldBucketOrBuilder> manifoldBucketBuilder_;
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public boolean hasManifoldBucket() {
      return manifoldBucketBuilder_ != null || manifoldBucket_ != null;
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public com.facebook.buck.remoteexecution.proto.ManifoldBucket getManifoldBucket() {
      if (manifoldBucketBuilder_ == null) {
        return manifoldBucket_ == null ? com.facebook.buck.remoteexecution.proto.ManifoldBucket.getDefaultInstance() : manifoldBucket_;
      } else {
        return manifoldBucketBuilder_.getMessage();
      }
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public Builder setManifoldBucket(com.facebook.buck.remoteexecution.proto.ManifoldBucket value) {
      if (manifoldBucketBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        manifoldBucket_ = value;
        onChanged();
      } else {
        manifoldBucketBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public Builder setManifoldBucket(
        com.facebook.buck.remoteexecution.proto.ManifoldBucket.Builder builderForValue) {
      if (manifoldBucketBuilder_ == null) {
        manifoldBucket_ = builderForValue.build();
        onChanged();
      } else {
        manifoldBucketBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public Builder mergeManifoldBucket(com.facebook.buck.remoteexecution.proto.ManifoldBucket value) {
      if (manifoldBucketBuilder_ == null) {
        if (manifoldBucket_ != null) {
          manifoldBucket_ =
            com.facebook.buck.remoteexecution.proto.ManifoldBucket.newBuilder(manifoldBucket_).mergeFrom(value).buildPartial();
        } else {
          manifoldBucket_ = value;
        }
        onChanged();
      } else {
        manifoldBucketBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public Builder clearManifoldBucket() {
      if (manifoldBucketBuilder_ == null) {
        manifoldBucket_ = null;
        onChanged();
      } else {
        manifoldBucket_ = null;
        manifoldBucketBuilder_ = null;
      }

      return this;
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public com.facebook.buck.remoteexecution.proto.ManifoldBucket.Builder getManifoldBucketBuilder() {
      
      onChanged();
      return getManifoldBucketFieldBuilder().getBuilder();
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    public com.facebook.buck.remoteexecution.proto.ManifoldBucketOrBuilder getManifoldBucketOrBuilder() {
      if (manifoldBucketBuilder_ != null) {
        return manifoldBucketBuilder_.getMessageOrBuilder();
      } else {
        return manifoldBucket_ == null ?
            com.facebook.buck.remoteexecution.proto.ManifoldBucket.getDefaultInstance() : manifoldBucket_;
      }
    }
    /**
     * <pre>
     * This allows overriding the manifold bucket
     * </pre>
     *
     * <code>.facebook.remote_execution.ManifoldBucket manifold_bucket = 4;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        com.facebook.buck.remoteexecution.proto.ManifoldBucket, com.facebook.buck.remoteexecution.proto.ManifoldBucket.Builder, com.facebook.buck.remoteexecution.proto.ManifoldBucketOrBuilder> 
        getManifoldBucketFieldBuilder() {
      if (manifoldBucketBuilder_ == null) {
        manifoldBucketBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            com.facebook.buck.remoteexecution.proto.ManifoldBucket, com.facebook.buck.remoteexecution.proto.ManifoldBucket.Builder, com.facebook.buck.remoteexecution.proto.ManifoldBucketOrBuilder>(
                getManifoldBucket(),
                getParentForChildren(),
                isClean());
        manifoldBucket_ = null;
      }
      return manifoldBucketBuilder_;
    }
    @java.lang.Override
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }


    // @@protoc_insertion_point(builder_scope:facebook.remote_execution.CasClientInfo)
  }

  // @@protoc_insertion_point(class_scope:facebook.remote_execution.CasClientInfo)
  private static final com.facebook.buck.remoteexecution.proto.CasClientInfo DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.facebook.buck.remoteexecution.proto.CasClientInfo();
  }

  public static com.facebook.buck.remoteexecution.proto.CasClientInfo getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<CasClientInfo>
      PARSER = new com.google.protobuf.AbstractParser<CasClientInfo>() {
    @java.lang.Override
    public CasClientInfo parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new CasClientInfo(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<CasClientInfo> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<CasClientInfo> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.facebook.buck.remoteexecution.proto.CasClientInfo getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

