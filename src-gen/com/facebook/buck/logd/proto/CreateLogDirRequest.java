// @generated
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: logd/proto/logdservice.proto

package com.facebook.buck.logd.proto;

/**
 * <pre>
 * Request for [LogdService.CreateLogDir]
 * </pre>
 *
 * Protobuf type {@code logd.v1.CreateLogDirRequest}
 */
@javax.annotation.Generated(value="protoc", comments="annotations:CreateLogDirRequest.java.pb.meta")
public  final class CreateLogDirRequest extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:logd.v1.CreateLogDirRequest)
    CreateLogDirRequestOrBuilder {
private static final long serialVersionUID = 0L;
  // Use CreateLogDirRequest.newBuilder() to construct.
  private CreateLogDirRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private CreateLogDirRequest() {
    buildId_ = "";
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private CreateLogDirRequest(
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

            buildId_ = s;
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
    return com.facebook.buck.logd.proto.LogdServiceOuterFile.internal_static_logd_v1_CreateLogDirRequest_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.facebook.buck.logd.proto.LogdServiceOuterFile.internal_static_logd_v1_CreateLogDirRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.facebook.buck.logd.proto.CreateLogDirRequest.class, com.facebook.buck.logd.proto.CreateLogDirRequest.Builder.class);
  }

  public static final int BUILDID_FIELD_NUMBER = 1;
  private volatile java.lang.Object buildId_;
  /**
   * <pre>
   * a buck command's uuid
   * </pre>
   *
   * <code>string buildId = 1;</code>
   */
  public java.lang.String getBuildId() {
    java.lang.Object ref = buildId_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      buildId_ = s;
      return s;
    }
  }
  /**
   * <pre>
   * a buck command's uuid
   * </pre>
   *
   * <code>string buildId = 1;</code>
   */
  public com.google.protobuf.ByteString
      getBuildIdBytes() {
    java.lang.Object ref = buildId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      buildId_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
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
    if (!getBuildIdBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, buildId_);
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getBuildIdBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, buildId_);
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
    if (!(obj instanceof com.facebook.buck.logd.proto.CreateLogDirRequest)) {
      return super.equals(obj);
    }
    com.facebook.buck.logd.proto.CreateLogDirRequest other = (com.facebook.buck.logd.proto.CreateLogDirRequest) obj;

    if (!getBuildId()
        .equals(other.getBuildId())) return false;
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
    hash = (37 * hash) + BUILDID_FIELD_NUMBER;
    hash = (53 * hash) + getBuildId().hashCode();
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.facebook.buck.logd.proto.CreateLogDirRequest parseFrom(
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
  public static Builder newBuilder(com.facebook.buck.logd.proto.CreateLogDirRequest prototype) {
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
   * <pre>
   * Request for [LogdService.CreateLogDir]
   * </pre>
   *
   * Protobuf type {@code logd.v1.CreateLogDirRequest}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:logd.v1.CreateLogDirRequest)
      com.facebook.buck.logd.proto.CreateLogDirRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.facebook.buck.logd.proto.LogdServiceOuterFile.internal_static_logd_v1_CreateLogDirRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.facebook.buck.logd.proto.LogdServiceOuterFile.internal_static_logd_v1_CreateLogDirRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.facebook.buck.logd.proto.CreateLogDirRequest.class, com.facebook.buck.logd.proto.CreateLogDirRequest.Builder.class);
    }

    // Construct using com.facebook.buck.logd.proto.CreateLogDirRequest.newBuilder()
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
      buildId_ = "";

      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.facebook.buck.logd.proto.LogdServiceOuterFile.internal_static_logd_v1_CreateLogDirRequest_descriptor;
    }

    @java.lang.Override
    public com.facebook.buck.logd.proto.CreateLogDirRequest getDefaultInstanceForType() {
      return com.facebook.buck.logd.proto.CreateLogDirRequest.getDefaultInstance();
    }

    @java.lang.Override
    public com.facebook.buck.logd.proto.CreateLogDirRequest build() {
      com.facebook.buck.logd.proto.CreateLogDirRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.facebook.buck.logd.proto.CreateLogDirRequest buildPartial() {
      com.facebook.buck.logd.proto.CreateLogDirRequest result = new com.facebook.buck.logd.proto.CreateLogDirRequest(this);
      result.buildId_ = buildId_;
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
      if (other instanceof com.facebook.buck.logd.proto.CreateLogDirRequest) {
        return mergeFrom((com.facebook.buck.logd.proto.CreateLogDirRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.facebook.buck.logd.proto.CreateLogDirRequest other) {
      if (other == com.facebook.buck.logd.proto.CreateLogDirRequest.getDefaultInstance()) return this;
      if (!other.getBuildId().isEmpty()) {
        buildId_ = other.buildId_;
        onChanged();
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
      com.facebook.buck.logd.proto.CreateLogDirRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.facebook.buck.logd.proto.CreateLogDirRequest) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object buildId_ = "";
    /**
     * <pre>
     * a buck command's uuid
     * </pre>
     *
     * <code>string buildId = 1;</code>
     */
    public java.lang.String getBuildId() {
      java.lang.Object ref = buildId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        buildId_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * a buck command's uuid
     * </pre>
     *
     * <code>string buildId = 1;</code>
     */
    public com.google.protobuf.ByteString
        getBuildIdBytes() {
      java.lang.Object ref = buildId_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        buildId_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * a buck command's uuid
     * </pre>
     *
     * <code>string buildId = 1;</code>
     */
    public Builder setBuildId(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      buildId_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * a buck command's uuid
     * </pre>
     *
     * <code>string buildId = 1;</code>
     */
    public Builder clearBuildId() {
      
      buildId_ = getDefaultInstance().getBuildId();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * a buck command's uuid
     * </pre>
     *
     * <code>string buildId = 1;</code>
     */
    public Builder setBuildIdBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      buildId_ = value;
      onChanged();
      return this;
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


    // @@protoc_insertion_point(builder_scope:logd.v1.CreateLogDirRequest)
  }

  // @@protoc_insertion_point(class_scope:logd.v1.CreateLogDirRequest)
  private static final com.facebook.buck.logd.proto.CreateLogDirRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.facebook.buck.logd.proto.CreateLogDirRequest();
  }

  public static com.facebook.buck.logd.proto.CreateLogDirRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<CreateLogDirRequest>
      PARSER = new com.google.protobuf.AbstractParser<CreateLogDirRequest>() {
    @java.lang.Override
    public CreateLogDirRequest parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new CreateLogDirRequest(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<CreateLogDirRequest> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<CreateLogDirRequest> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.facebook.buck.logd.proto.CreateLogDirRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

