// @generated
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: cd/resources/proto/kotlincd.proto

package com.facebook.buck.cd.model.kotlin;

@javax.annotation.Generated(value="protoc", comments="annotations:BuildKotlinCommandOrBuilder.java.pb.meta")
public interface BuildKotlinCommandOrBuilder extends
    // @@protoc_insertion_point(interface_extends:kotlincd.api.v1.BuildKotlinCommand)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>.kotlincd.api.v1.BaseCommandParams baseCommandParams = 1;</code>
   */
  boolean hasBaseCommandParams();
  /**
   * <code>.kotlincd.api.v1.BaseCommandParams baseCommandParams = 1;</code>
   */
  com.facebook.buck.cd.model.kotlin.BaseCommandParams getBaseCommandParams();
  /**
   * <code>.kotlincd.api.v1.BaseCommandParams baseCommandParams = 1;</code>
   */
  com.facebook.buck.cd.model.kotlin.BaseCommandParamsOrBuilder getBaseCommandParamsOrBuilder();

  /**
   * <code>.kotlincd.api.v1.LibraryJarCommand libraryJarCommand = 2;</code>
   */
  boolean hasLibraryJarCommand();
  /**
   * <code>.kotlincd.api.v1.LibraryJarCommand libraryJarCommand = 2;</code>
   */
  com.facebook.buck.cd.model.kotlin.LibraryJarCommand getLibraryJarCommand();
  /**
   * <code>.kotlincd.api.v1.LibraryJarCommand libraryJarCommand = 2;</code>
   */
  com.facebook.buck.cd.model.kotlin.LibraryJarCommandOrBuilder getLibraryJarCommandOrBuilder();

  /**
   * <code>.kotlincd.api.v1.AbiJarCommand abiJarCommand = 3;</code>
   */
  boolean hasAbiJarCommand();
  /**
   * <code>.kotlincd.api.v1.AbiJarCommand abiJarCommand = 3;</code>
   */
  com.facebook.buck.cd.model.kotlin.AbiJarCommand getAbiJarCommand();
  /**
   * <code>.kotlincd.api.v1.AbiJarCommand abiJarCommand = 3;</code>
   */
  com.facebook.buck.cd.model.kotlin.AbiJarCommandOrBuilder getAbiJarCommandOrBuilder();

  public com.facebook.buck.cd.model.kotlin.BuildKotlinCommand.CommandCase getCommandCase();
}
