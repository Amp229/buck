// @generated
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: remoteexecution/proto/metadata.proto

package com.facebook.buck.remoteexecution.proto;

@javax.annotation.Generated(value="protoc", comments="annotations:ClientJobInfoOrBuilder.java.pb.meta")
public interface ClientJobInfoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:facebook.remote_execution.ClientJobInfo)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Deployment stage of the job producing this action (e.g. prod).
   * </pre>
   *
   * <code>string deployment_stage = 1;</code>
   */
  java.lang.String getDeploymentStage();
  /**
   * <pre>
   * Deployment stage of the job producing this action (e.g. prod).
   * </pre>
   *
   * <code>string deployment_stage = 1;</code>
   */
  com.google.protobuf.ByteString
      getDeploymentStageBytes();

  /**
   * <pre>
   * Job identifier.
   * </pre>
   *
   * <code>string instance_id = 2;</code>
   */
  java.lang.String getInstanceId();
  /**
   * <pre>
   * Job identifier.
   * </pre>
   *
   * <code>string instance_id = 2;</code>
   */
  com.google.protobuf.ByteString
      getInstanceIdBytes();

  /**
   * <pre>
   * Job group identifier (aka nonce), if it's part of group of jobs.
   * </pre>
   *
   * <code>string group_id = 3;</code>
   */
  java.lang.String getGroupId();
  /**
   * <pre>
   * Job group identifier (aka nonce), if it's part of group of jobs.
   * </pre>
   *
   * <code>string group_id = 3;</code>
   */
  com.google.protobuf.ByteString
      getGroupIdBytes();

  /**
   * <pre>
   * Job tenant.
   * </pre>
   *
   * <code>string client_side_tenant = 4;</code>
   */
  java.lang.String getClientSideTenant();
  /**
   * <pre>
   * Job tenant.
   * </pre>
   *
   * <code>string client_side_tenant = 4;</code>
   */
  com.google.protobuf.ByteString
      getClientSideTenantBytes();
}
