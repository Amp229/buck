/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.remoteexecution;

import build.bazel.remote.execution.v2.Platform;
import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.log.TraceInfoProvider;
import com.facebook.buck.remoteexecution.interfaces.MetadataProvider;
import com.facebook.buck.remoteexecution.proto.ActionHistoryInfo;
import com.facebook.buck.remoteexecution.proto.BuckInfo;
import com.facebook.buck.remoteexecution.proto.CasClientInfo;
import com.facebook.buck.remoteexecution.proto.ClientActionInfo;
import com.facebook.buck.remoteexecution.proto.ClientJobInfo;
import com.facebook.buck.remoteexecution.proto.CreatorInfo;
import com.facebook.buck.remoteexecution.proto.RESessionID;
import com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadata;
import com.facebook.buck.remoteexecution.proto.TraceInfo;
import com.facebook.buck.rules.keys.config.impl.BuckVersion;
import com.facebook.buck.util.environment.ExecutionEnvironment;
import com.facebook.buck.util.types.Pair;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Static class providing factory methods for instances of MetadataProviders. */
public class MetadataProviderFactory {

  private static final String DEFAULT_CLIENT_TYPE = "buck";
  private static final String RE_SESSION_ID_PREFIX = "reSessionID-";

  private MetadataProviderFactory() {
    // static class.
  }

  /** @return Metadata provider that always returns empty Metadata. */
  public static MetadataProvider emptyMetadataProvider() {
    return new MetadataProvider() {
      @Override
      public RemoteExecutionMetadata get() {
        return RemoteExecutionMetadata.newBuilder().build();
      }

      @Override
      public RemoteExecutionMetadata.Builder getBuilderForAction(
          String actionDigest, String ruleName, String ruleType) {
        return RemoteExecutionMetadata.newBuilder();
      }

      @Override
      public RemoteExecutionMetadata getForAction(
          String actionDigest, String ruleName, String ruleType) {
        return get();
      }
    };
  }

  /**
   * @return Metadata provider that provides minimal amount information that should be passed along
   *     remote execution requests.
   */
  public static MetadataProvider minimalMetadataProviderForBuild(
      BuildId buildId,
      String username,
      String repository,
      String scheduleType,
      String reSessionLabel,
      String tenantId,
      String useCaseId,
      String auxiliaryBuildTag,
      String projectPrefix,
      ExecutionEnvironment executionEnvironment) {
    return new MetadataProvider() {
      final RemoteExecutionMetadata metadata;

      {
        // TODO(msienkiewicz): Allow overriding RE Session ID, client type, username with config
        // flags/env vars.
        String reSessionIDRaw = RE_SESSION_ID_PREFIX + UUID.randomUUID();
        RESessionID reSessionID = RESessionID.newBuilder().setId(reSessionIDRaw).build();
        BuckInfo buckInfo =
            BuckInfo.newBuilder()
                .setBuildId(buildId.toString())
                .setAuxiliaryBuildTag(auxiliaryBuildTag)
                .setProjectPrefix(projectPrefix)
                .setVersion(BuckVersion.getVersion())
                .build();
        CreatorInfo creatorInfo =
            CreatorInfo.newBuilder()
                .setClientType(DEFAULT_CLIENT_TYPE)
                .setUsername(username)
                .build();
        CasClientInfo casClientInfo = CasClientInfo.newBuilder().setName("buck").build();
        ClientActionInfo clientActionInfo =
            ClientActionInfo.newBuilder()
                .setRepository(repository)
                .setScheduleType(scheduleType)
                .setReSessionLabel(reSessionLabel)
                .setTenantId(tenantId)
                .build();
        metadata =
            RemoteExecutionMetadata.newBuilder()
                .setReSessionId(reSessionID)
                .setBuckInfo(buckInfo)
                .setCreatorInfo(creatorInfo)
                .setCasClientInfo(casClientInfo)
                .setClientActionInfo(clientActionInfo)
                .setClientJobInfo(buildClientJobInfo(executionEnvironment))
                .setUseCaseId(useCaseId)
                .build();
      }

      @Override
      public RemoteExecutionMetadata get() {
        return metadata;
      }

      @Override
      public RemoteExecutionMetadata.Builder getBuilderForAction(
          String actionDigest, String ruleName, String ruleType) {
        // NOTE: Do NOT try to optimize this by storing the builder and applying updates to it
        // directly. This would require locking for the duration of update and copying into a fresh
        // RemoteExecutionMetadata.Builder object.
        RemoteExecutionMetadata.Builder builder = metadata.toBuilder();
        BuckInfo buckInfo =
            builder.getBuckInfo().toBuilder().setRuleName(ruleName).setRuleType(ruleType).build();
        builder.setBuckInfo(buckInfo);
        return builder;
      }

      @Override
      public RemoteExecutionMetadata getForAction(
          String actionDigest, String ruleName, String ruleType) {
        return getBuilderForAction(actionDigest, ruleName, ruleType).build();
      }
    };
  }

  private static ClientJobInfo buildClientJobInfo(ExecutionEnvironment executionEnvironment) {
    Optional<String> jobInstanceId = executionEnvironment.getenv("BUCK_JOB_INSTANCE_ID");
    Optional<String> jobGroupId = executionEnvironment.getenv("BUCK_JOB_GROUP_ID");
    Optional<String> jobDeploymentStage = executionEnvironment.getenv("BUCK_JOB_DEPLOYMENT_STAGE");
    Optional<String> jobTenant = executionEnvironment.getenv("BUCK_JOB_TENANT");

    return ClientJobInfo.newBuilder()
        .setInstanceId(jobInstanceId.orElse(""))
        .setGroupId(jobGroupId.orElse(""))
        .setDeploymentStage(jobDeploymentStage.orElse(""))
        .setClientSideTenant(jobTenant.orElse(""))
        .build();
  }

  /** Wraps the argument MetadataProvider return value with info about tracing. */
  public static MetadataProvider wrapWithTraceInfo(
      MetadataProvider metadataProvider, final TraceInfoProvider traceInfoProvider) {
    return new MetadataProvider() {
      @Override
      public RemoteExecutionMetadata get() {
        TraceInfo traceInfo =
            TraceInfo.newBuilder().setTraceId(traceInfoProvider.getTraceId()).build();
        return metadataProvider.get().toBuilder().setTraceInfo(traceInfo).build();
      }

      @Override
      public RemoteExecutionMetadata.Builder getBuilderForAction(
          String actionDigest, String ruleName, String ruleType) {
        TraceInfo traceInfo =
            TraceInfo.newBuilder()
                .setTraceId(traceInfoProvider.getTraceId())
                .setEdgeId(traceInfoProvider.getEdgeId(actionDigest))
                .build();
        return metadataProvider
            .getBuilderForAction(actionDigest, ruleName, ruleType)
            .setTraceInfo(traceInfo);
      }

      @Override
      public RemoteExecutionMetadata getForAction(
          String actionDigest, String ruleName, String ruleType) {
        return getBuilderForAction(actionDigest, ruleName, ruleType).build();
      }
    };
  }

  /** Wraps the argument MetadataProvider with worker requirements info */
  public static MetadataProvider wrapForRuleWithWorkerRequirements(
      MetadataProvider metadataProvider,
      Supplier<Pair<Platform, ActionHistoryInfo>> requirementsSupplier) {
    return new MetadataProvider() {
      @Override
      public RemoteExecutionMetadata get() {
        return metadataProvider
            .get()
            .toBuilder()
            .setPlatform(requirementsSupplier.get().getFirst())
            .setActionHistoryInfo(requirementsSupplier.get().getSecond())
            .build();
      }

      @Override
      public RemoteExecutionMetadata.Builder getBuilderForAction(
          String actionDigest, String ruleName, String ruleType) {
        return metadataProvider
            .getBuilderForAction(actionDigest, ruleName, ruleType)
            .setPlatform(requirementsSupplier.get().getFirst())
            .setActionHistoryInfo(requirementsSupplier.get().getSecond());
      }

      @Override
      public RemoteExecutionMetadata getForAction(
          String actionDigest, String ruleName, String ruleType) {
        return getBuilderForAction(actionDigest, ruleName, ruleType).build();
      }
    };
  }
}
