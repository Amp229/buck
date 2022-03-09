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

package com.facebook.buck.cli;

import com.facebook.buck.apple.AppleBundle;
import com.facebook.buck.apple.AppleDsym;
import com.facebook.buck.command.Build;
import com.facebook.buck.command.BuildExecutor;
import com.facebook.buck.command.config.BuildBuckConfig;
import com.facebook.buck.core.build.engine.delegate.LocalCachingBuildEngineDelegate;
import com.facebook.buck.core.build.engine.type.BuildType;
import com.facebook.buck.core.build.event.BuildEvent;
import com.facebook.buck.core.build.execution.context.ExecutionContext;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.PathWrapper;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetWithOutputs;
import com.facebook.buck.core.model.CustomHashedBuckOutLinking;
import com.facebook.buck.core.model.OutputLabel;
import com.facebook.buck.core.model.actiongraph.ActionGraphAndBuilder;
import com.facebook.buck.core.model.graph.ActionAndTargetGraphs;
import com.facebook.buck.core.model.impl.BuildPaths;
import com.facebook.buck.core.model.impl.HashedBuckOutLinkMode;
import com.facebook.buck.core.model.targetgraph.TargetGraphCreationResult;
import com.facebook.buck.core.rulekey.RuleKey;
import com.facebook.buck.core.rulekey.calculator.ParallelRuleKeyCalculator;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.attr.HasMultipleOutputs;
import com.facebook.buck.core.rules.transformer.impl.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventListener;
import com.facebook.buck.event.listener.FileSerializationOutputRuleDepsListener;
import com.facebook.buck.io.file.MostFiles;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.log.InvocationInfo;
import com.facebook.buck.log.thrift.ThriftRuleKeyLogger;
import com.facebook.buck.parser.spec.TargetNodeSpec;
import com.facebook.buck.remoteexecution.config.RemoteExecutionConfig;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.rules.keys.RuleKeyCacheRecycler;
import com.facebook.buck.rules.keys.RuleKeyCacheScope;
import com.facebook.buck.rules.keys.RuleKeyFieldLoader;
import com.facebook.buck.support.cli.config.AliasConfig;
import com.facebook.buck.util.CommandLineException;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.ListeningProcessExecutor;
import com.facebook.buck.util.concurrent.ExecutorPool;
import com.facebook.buck.util.concurrent.WeightedListeningExecutorService;
import com.facebook.buck.util.json.ObjectMappers;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** Base class for build command and build for IDE command. */
abstract class AbstractBuildCommand extends AbstractCommand {

  static final String BUCK_BINARY_STRING_ARG = "--buck-binary";
  private static final String KEEP_GOING_LONG_ARG = "--keep-going";
  private static final String BUILD_REPORT_LONG_ARG = "--build-report";
  private static final String JUST_BUILD_LONG_ARG = "--just-build";
  private static final String DEEP_LONG_ARG = "--deep";
  private static final String OUT_LONG_ARG = "--out";
  private static final String POPULATE_CACHE_LONG_ARG = "--populate-cache";
  private static final String SHALLOW_LONG_ARG = "--shallow";
  private static final String REPORT_ABSOLUTE_PATHS = "--report-absolute-paths";
  private static final String SHOW_OUTPUT_LONG_ARG = "--show-output";
  private static final String SHOW_FULL_OUTPUT_LONG_ARG = "--show-full-output";
  private static final String SHOW_JSON_OUTPUT_LONG_ARG = "--show-json-output";
  private static final String SHOW_FULL_JSON_OUTPUT_LONG_ARG = "--show-full-json-output";
  private static final String SHOW_ALL_OUTPUTS_LONG_ARG = "--show-all-outputs";
  private static final String SHOW_ALL_OUTPUTS_FORMAT_LONG_ARG = "--show-all-outputs-format";
  private static final String SHOW_RULEKEY_LONG_ARG = "--show-rulekey";
  private static final String LOCAL_BUILD_LONG_ARG = "--local";
  private static final String RULEKEY_LOG_PATH_LONG_ARG = "--rulekeys-log-path";
  private static final String OUTPUT_RULE_DEPS_TO_FILE_ARG = "--output-rule-deps-to-file";
  private static final String ACTION_GRAPH_FILE_NAME = "action_graph.json";
  private static final String RULE_EXEC_TIME_FILE_NAME = "rule_exec_time.json";
  private final AtomicReference<Build> lastBuild = new AtomicReference<>(null);
  private final SettableFuture<ParallelRuleKeyCalculator<RuleKey>> localRuleKeyCalculator =
      SettableFuture.create();

  /** Enum with values for `--show-all-outputs` CLI parameter */
  protected enum ShowAllOutputsFormat {
    /* Format output as list with path relative to buck-out */
    LIST,

    /* Format output as JSON with path relative to buck-out */
    JSON,

    /* Format output as list with absolute paths */
    FULL_LIST,

    /* Format output as JSON with absolute paths */
    FULL_JSON,
  }

  @Argument protected List<String> arguments = new ArrayList<>();

  @Option(name = KEEP_GOING_LONG_ARG, usage = "Keep going when some targets can't be made.")
  private boolean keepGoing = false;

  @Option(name = BUILD_REPORT_LONG_ARG, usage = "File where build report will be written.")
  @Nullable
  private Path buildReport = null;

  @Nullable
  @Option(
      name = JUST_BUILD_LONG_ARG,
      usage = "For debugging, limits the build to a specific target in the action graph.",
      hidden = true)
  protected String justBuildTarget = null;

  @Option(
      name = DEEP_LONG_ARG,
      usage =
          "Perform a \"deep\" build, which makes the output of all transitive dependencies"
              + " available.",
      forbids = SHALLOW_LONG_ARG)
  private boolean deepBuild = false;

  @Option(
      name = POPULATE_CACHE_LONG_ARG,
      usage =
          "Performs a cache population, which makes the output of all unchanged "
              + "transitive dependencies available (if these outputs are available "
              + "in the remote cache). Does not build changed or unavailable dependencies locally.",
      forbids = {SHALLOW_LONG_ARG, DEEP_LONG_ARG})
  private boolean populateCacheOnly = false;

  @Option(
      name = SHALLOW_LONG_ARG,
      usage =
          "Perform a \"shallow\" build, which only makes the output of all explicitly listed"
              + " targets available.",
      forbids = DEEP_LONG_ARG)
  private boolean shallowBuild = false;

  @Option(
      name = REPORT_ABSOLUTE_PATHS,
      usage = "Reports errors using absolute paths to the source files instead of relative paths.")
  private boolean shouldReportAbsolutePaths = false;

  @Option(
      name = SHOW_OUTPUT_LONG_ARG,
      usage = "Print the path to the output for each of the built rules relative to the cell.")
  private boolean showOutput;

  @Option(name = OUT_LONG_ARG, usage = "Copies the output of the lone build target to this path.")
  @Nullable
  private Path outputPathForSingleBuildTarget;

  @Option(
      name = SHOW_FULL_OUTPUT_LONG_ARG,
      usage = "Print the absolute path to the output for each of the built rules.")
  private boolean showFullOutput;

  @Option(name = SHOW_JSON_OUTPUT_LONG_ARG, usage = "Show output in JSON format.")
  private boolean showJsonOutput;

  @Option(name = SHOW_FULL_JSON_OUTPUT_LONG_ARG, usage = "Show full output in JSON format.")
  private boolean showFullJsonOutput;

  @Option(name = SHOW_RULEKEY_LONG_ARG, usage = "Print the rulekey for each of the built rules.")
  private boolean showRuleKey;

  @Option(name = LOCAL_BUILD_LONG_ARG, usage = "Disable remote execution for this build.")
  private boolean forceDisableRemoteExecution = false;

  @Nullable
  @Option(
      name = BUCK_BINARY_STRING_ARG,
      usage = "Buck binary to use on a distributed build instead of the current git version.",
      hidden = true)
  private String buckBinary = null;

  @Nullable
  @Option(
      name = RULEKEY_LOG_PATH_LONG_ARG,
      usage = "If set, log a binary representation of rulekeys to this file.")
  private String ruleKeyLogPath = null;

  @Option(
      name = OUTPUT_RULE_DEPS_TO_FILE_ARG,
      usage = "Serialize rule dependencies and execution time to the log directory")
  private boolean outputRuleDeps = false;

  @Option(
      name = SHOW_ALL_OUTPUTS_FORMAT_LONG_ARG,
      usage =
          "Indicates the output format that should be used when using the show all outputs functionality (default: list).\n"
              + " list -  output paths are printed relative to the cell.\n"
              + " full_list - output paths are printed as absolute paths.\n"
              + " json - JSON format with relative paths\n"
              + " full_json - JSON format with absolute paths.\n",
      forbids = {
        SHOW_OUTPUT_LONG_ARG,
        SHOW_FULL_OUTPUT_LONG_ARG,
        SHOW_JSON_OUTPUT_LONG_ARG,
        SHOW_FULL_JSON_OUTPUT_LONG_ARG,
      })
  private ShowAllOutputsFormat showAllOutputsFormat = ShowAllOutputsFormat.LIST;

  @Option(
      name = SHOW_ALL_OUTPUTS_LONG_ARG,
      usage = "Print the paths to all the outputs for each of the built rules.",
      forbids = {
        SHOW_OUTPUT_LONG_ARG,
        SHOW_FULL_OUTPUT_LONG_ARG,
        SHOW_JSON_OUTPUT_LONG_ARG,
        SHOW_FULL_JSON_OUTPUT_LONG_ARG,
      })
  private boolean isShowAllOutputs = false;

  protected static ActionGraphAndBuilder createActionGraphAndResolver(
      CommandRunnerParams params,
      TargetGraphCreationResult targetGraphAndBuildTargets,
      Optional<ThriftRuleKeyLogger> ruleKeyLogger) {
    return params
        .getActionGraphProvider()
        .getActionGraph(
            new DefaultTargetNodeToBuildRuleTransformer(),
            targetGraphAndBuildTargets,
            ruleKeyLogger);
  }

  public List<String> getArguments() {
    return arguments;
  }

  /** Which build mode. */
  public Optional<BuildType> getBuildEngineMode() {
    Optional<BuildType> mode = Optional.empty();
    if (deepBuild) {
      mode = Optional.of(BuildType.DEEP);
    }
    if (populateCacheOnly) {
      mode = Optional.of(BuildType.POPULATE_FROM_REMOTE_CACHE);
    }
    if (shallowBuild) {
      mode = Optional.of(BuildType.SHALLOW);
    }
    return mode;
  }

  public boolean isKeepGoing() {
    return keepGoing;
  }

  protected boolean shouldReportAbsolutePaths() {
    return shouldReportAbsolutePaths;
  }

  public void setKeepGoing(boolean keepGoing) {
    this.keepGoing = keepGoing;
  }

  public boolean isRemoteExecutionForceDisabled() {
    return forceDisableRemoteExecution;
  }

  /** @return an absolute path or {@link Optional#empty()}. */
  public Optional<Path> getPathToBuildReport(BuckConfig buckConfig) {
    return Optional.ofNullable(
        buckConfig.resolvePathThatMayBeOutsideTheProjectFilesystem(buildReport));
  }

  @Override
  public ExitCode runWithoutHelp(CommandRunnerParams params) throws Exception {
    return runWithoutHelpInternal(params).getExitCode();
  }

  BuildRunResult runWithoutHelpInternal(CommandRunnerParams params) throws Exception {
    assertArguments(params);

    BuckEventBus buckEventBus = params.getBuckEventBus();
    if (outputRuleDeps) {
      FileSerializationOutputRuleDepsListener fileSerializationOutputRuleDepsListener =
          new FileSerializationOutputRuleDepsListener(
              getLogDirectoryPath(params).resolve(RULE_EXEC_TIME_FILE_NAME));
      buckEventBus.register(fileSerializationOutputRuleDepsListener);
    }

    try (CommandThreadManager pool =
            new CommandThreadManager("Build", getConcurrencyLimit(params.getBuckConfig()));
        BuildPrehook prehook = getPrehook(new ListeningProcessExecutor(), params)) {
      prehook.startPrehookScript();
      return run(params, pool, Function.identity(), ImmutableSet.of());
    }
  }

  private Path getLogDirectoryPath(CommandRunnerParams params) {
    InvocationInfo invocationInfo = params.getInvocationInfo().get();
    Path logDirectoryPath = invocationInfo.getLogDirectoryPath();
    ProjectFilesystem filesystem = params.getCells().getRootCell().getFilesystem();
    return filesystem.resolve(logDirectoryPath);
  }

  BuildPrehook getPrehook(ListeningProcessExecutor processExecutor, CommandRunnerParams params) {
    return new BuildPrehook(
        processExecutor,
        params.getCells().getRootCell(),
        params.getBuckEventBus(),
        params.getBuckConfig(),
        params.getEnvironment(),
        getArguments());
  }

  /** @throws CommandLineException if arguments provided are incorrect */
  protected void assertArguments(CommandRunnerParams params) {
    if (!getArguments().isEmpty()) {
      return;
    }
    String message =
        "Must specify at least one build target. See https://dev.buck.build/concept/build_target_pattern.html";
    ImmutableSet<String> aliases = AliasConfig.from(params.getBuckConfig()).getAliases().keySet();
    if (!aliases.isEmpty()) {
      // If there are aliases defined in .buckconfig, suggest that the user
      // build one of them. We show the user only the first 10 aliases.
      message +=
          String.format(
              "%nTry building one of the following targets:%n%s",
              Joiner.on(' ').join(Iterators.limit(aliases.iterator(), 10)));
    }
    throw new CommandLineException(message);
  }

  /** Run the build. */
  protected BuildRunResult run(
      CommandRunnerParams params,
      CommandThreadManager commandThreadManager,
      Function<ImmutableList<TargetNodeSpec>, ImmutableList<TargetNodeSpec>> targetNodeSpecEnhancer,
      ImmutableSet<String> additionalTargets)
      throws Exception {
    if (!additionalTargets.isEmpty()) {
      this.arguments.addAll(additionalTargets);
    }
    // check if keep-going was enabled in buck config and override --keep-going if necessary
    BuildBuckConfig buildBuckConfig = BuildBuckConfig.of(params.getBuckConfig());
    setKeepGoing(buildBuckConfig.getBuildKeepGoingEnabled() || isKeepGoing());

    BuildEvent.Started started = postBuildStartedEvent(params);
    BuildRunResult result =
        ImmutableBuildRunResult.ofImpl(ExitCode.BUILD_ERROR, ImmutableList.of());
    try {
      result = executeBuildAndProcessResult(params, commandThreadManager, targetNodeSpecEnhancer);
    } catch (ActionGraphCreationException e) {
      params.getConsole().printBuildFailure(e.getMessage());
      result = ImmutableBuildRunResult.ofImpl(ExitCode.PARSE_ERROR, ImmutableList.of());
    } finally {
      params.getBuckEventBus().post(BuildEvent.finished(started, result.getExitCode()));
    }

    return result;
  }

  private BuildEvent.Started postBuildStartedEvent(CommandRunnerParams params) {
    BuildEvent.Started started = BuildEvent.started(getArguments());
    params.getBuckEventBus().post(started);
    return started;
  }

  abstract GraphsAndBuildTargets createGraphsAndTargets(
      CommandRunnerParams params,
      ListeningExecutorService executorService,
      Function<ImmutableList<TargetNodeSpec>, ImmutableList<TargetNodeSpec>> targetNodeSpecEnhancer,
      Optional<ThriftRuleKeyLogger> ruleKeyLogger)
      throws ActionGraphCreationException, IOException, InterruptedException;

  protected void checkSingleBuildTargetSpecifiedForOutBuildMode(
      TargetGraphCreationResult targetGraphAndBuildTargets) {
    if (outputPathForSingleBuildTarget != null
        && targetGraphAndBuildTargets.getBuildTargets().size() != 1) {
      throw new CommandLineException(
          String.format(
              "When using %s you must specify exactly one build target, but you specified %s",
              OUT_LONG_ARG, targetGraphAndBuildTargets.getBuildTargets()));
    }
  }

  private BuildRunResult executeBuildAndProcessResult(
      CommandRunnerParams params,
      CommandThreadManager commandThreadManager,
      Function<ImmutableList<TargetNodeSpec>, ImmutableList<TargetNodeSpec>> targetNodeSpecEnhancer)
      throws Exception {
    ExitCode exitCode;
    GraphsAndBuildTargets graphsAndBuildTargets;
    try (ThriftRuleKeyLogger ruleKeyLogger = createRuleKeyLogger().orElse(null)) {
      Optional<ThriftRuleKeyLogger> optionalRuleKeyLogger = Optional.ofNullable(ruleKeyLogger);
      graphsAndBuildTargets =
          createGraphsAndTargets(
              params,
              commandThreadManager.getListeningExecutorService(),
              targetNodeSpecEnhancer,
              optionalRuleKeyLogger);

      if (outputRuleDeps) {
        ActionGraphBuilder actionGraphBuilder =
            graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder().getActionGraphBuilder();
        ImmutableSet<BuildTarget> buildTargets = graphsAndBuildTargets.getBuildTargets();
        Path outputPath = getLogDirectoryPath(params).resolve(ACTION_GRAPH_FILE_NAME);
        new ActionGraphSerializer(actionGraphBuilder, buildTargets, outputPath).serialize();
      }

      try (RuleKeyCacheScope<RuleKey> ruleKeyCacheScope =
          getDefaultRuleKeyCacheScope(
              params, graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder())) {
        exitCode =
            executeLocalBuild(
                params,
                graphsAndBuildTargets,
                commandThreadManager.getWeightedListeningExecutorService(),
                optionalRuleKeyLogger,
                Optional.empty(),
                ruleKeyCacheScope,
                lastBuild);
        if (exitCode == ExitCode.SUCCESS) {
          exitCode = processSuccessfulBuild(params, graphsAndBuildTargets, ruleKeyCacheScope);
        }
      }
    }

    return ImmutableBuildRunResult.ofImpl(exitCode, graphsAndBuildTargets.getBuildTargets());
  }

  /**
   * Create a {@link ThriftRuleKeyLogger} depending on whether {@link BuildCommand#ruleKeyLogPath}
   * is set or not
   */
  private Optional<ThriftRuleKeyLogger> createRuleKeyLogger() throws IOException {
    if (ruleKeyLogPath == null) {
      return Optional.empty();
    } else {
      return Optional.of(ThriftRuleKeyLogger.create(Paths.get(ruleKeyLogPath)));
    }
  }

  ExitCode processSuccessfulBuild(
      CommandRunnerParams params,
      GraphsAndBuildTargets graphsAndBuildTargets,
      RuleKeyCacheScope<RuleKey> ruleKeyCacheScope)
      throws IOException {
    BuildBuckConfig buildBuckConfig = params.getBuckConfig().getView(BuildBuckConfig.class);
    if (buildBuckConfig.createBuildOutputSymLinksEnabled()) {
      symLinkBuildResults(params, graphsAndBuildTargets);
    }
    if (buildBuckConfig.shouldBuckOutIncludeTargetConfigHash()
        && !buildBuckConfig.getHashedBuckOutLinkMode().equals(HashedBuckOutLinkMode.NONE)) {
      linkBuildResultsToHashedBuckOut(params, graphsAndBuildTargets);
    }
    ActionAndTargetGraphs graphs = graphsAndBuildTargets.getGraphs();

    // Both showAllOutputs() and showOutput() paths check for rulekey, however, the code here is
    // meant to set the showOutput() path as the fallback in the case that neither --show-output nor
    // --show-all-outputs is explicitly stated but --show-rulekey is stated.
    if (isShowAllOutputs()) {
      showAllOutputs(params, graphsAndBuildTargets, ruleKeyCacheScope);
    } else if (isShowOutput()
        || isShowListOutput()
        || isShowJsonOutput()
        || isShowOutputsPathAbsolute()
        || isShowRuleKey()) {
      showOutputs(params, graphsAndBuildTargets, ruleKeyCacheScope);
    }
    if (outputPathForSingleBuildTarget != null) {
      BuildTargetWithOutputs loneTarget =
          Iterables.getOnlyElement(graphsAndBuildTargets.getBuildTargetWithOutputs());
      BuildRule rule =
          graphs
              .getActionGraphAndBuilder()
              .getActionGraphBuilder()
              .getRule(loneTarget.getBuildTarget());
      if (!rule.outputFileCanBeCopied()) {
        params
            .getConsole()
            .printErrorText(
                String.format(
                    "%s does not have an output that is compatible with `buck build --out`",
                    loneTarget));
        return ExitCode.BUILD_ERROR;
      } else {
        SourcePath output = getSourcePath(rule, loneTarget);
        ProjectFilesystem projectFilesystem = params.getCells().getRootCell().getFilesystem();
        SourcePathResolverAdapter pathResolver =
            graphs.getActionGraphAndBuilder().getActionGraphBuilder().getSourcePathResolver();

        AbsPath outputAbsPath = pathResolver.getAbsolutePath(output);
        if (projectFilesystem.isDirectory(outputAbsPath)) {
          if (projectFilesystem.isFile(outputPathForSingleBuildTarget)) {
            params
                .getConsole()
                .printErrorText(
                    "buck --out for targets outputting directory must be either nonexistent or a directory!");
            return ExitCode.BUILD_ERROR;
          }
          projectFilesystem.mkdirs(outputPathForSingleBuildTarget);
          projectFilesystem.copyFolder(outputAbsPath.getPath(), outputPathForSingleBuildTarget);
        } else {
          Path outputPath;
          if (Files.isDirectory(outputPathForSingleBuildTarget)) {
            Path outputDir = outputPathForSingleBuildTarget.normalize();
            Path outputFilename = outputAbsPath.getFileName();
            outputPath = outputDir.resolve(outputFilename);
          } else {
            outputPath = outputPathForSingleBuildTarget;
          }
          projectFilesystem.copyFile(outputAbsPath.getPath(), outputPath);
        }
      }
    }
    return ExitCode.SUCCESS;
  }

  private SourcePath getSourcePath(BuildRule rule, BuildTargetWithOutputs targetWithOutputs) {
    if (rule instanceof HasMultipleOutputs) {
      return Preconditions.checkNotNull(
          Iterables.getOnlyElement(
              ((HasMultipleOutputs) rule)
                  .getSourcePathToOutput(targetWithOutputs.getOutputLabel())),
          "%s specified a build target that does not have an output file: %s",
          OUT_LONG_ARG,
          targetWithOutputs);
    }
    return Preconditions.checkNotNull(
        rule.getSourcePathToOutput(),
        "%s specified a build target that does not have an output file: %s",
        OUT_LONG_ARG,
        targetWithOutputs);
  }

  private void linkBuildResultsToHashedBuckOut(
      CommandRunnerParams params, GraphsAndBuildTargets graphsAndBuildTargets)
      throws IllegalStateException, IOException {
    BuildBuckConfig buildBuckConfig = params.getBuckConfig().getView(BuildBuckConfig.class);
    if (!buildBuckConfig.shouldBuckOutIncludeTargetConfigHash()) {
      throw new IllegalStateException(
          "buckconfig buck_out_include_target_config_hash must be true to "
              + "hardlink build results to hashed buck-out!");
    }
    ActionGraphBuilder graphBuilder =
        graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder().getActionGraphBuilder();
    SourcePathResolverAdapter pathResolver = graphBuilder.getSourcePathResolver();

    for (BuildTargetWithOutputs buildTargetWithOutputs :
        graphsAndBuildTargets.getBuildTargetWithOutputs()) {
      BuildRule rule = graphBuilder.requireRule(buildTargetWithOutputs.getBuildTarget());
      // If this rule is opting out of target config hashing, then skip linking it.
      if (!rule.getProjectFilesystem()
          .getBuckPaths()
          .shouldIncludeTargetConfigHash(rule.getBuildTarget().getCellRelativeBasePath())) {
        continue;
      }
      if (rule instanceof HasMultipleOutputs) {
        for (OutputLabel outputLabel :
            PathUtils.getAncestorOutputsLabels(pathResolver, (HasMultipleOutputs) rule)) {
          linkRuleToHashedBuckOut(
              rule,
              pathResolver,
              buildBuckConfig.getBuckOutCompatLink(),
              outputLabel,
              buildBuckConfig.getHashedBuckOutLinkMode());
        }
      } else {
        linkRuleToHashedBuckOut(
            rule,
            pathResolver,
            buildBuckConfig.getBuckOutCompatLink(),
            buildTargetWithOutputs.getOutputLabel(),
            buildBuckConfig.getHashedBuckOutLinkMode());
      }
    }
  }

  private void linkRuleToHashedBuckOut(
      BuildRule rule,
      SourcePathResolverAdapter pathResolver,
      boolean buckOutCompatLink,
      OutputLabel outputLabel,
      HashedBuckOutLinkMode linkMode)
      throws IOException {
    Optional<AbsPath> outputPath =
        PathUtils.getUserFacingOutputPath(pathResolver, rule, buckOutCompatLink, outputLabel);
    if (!outputPath.isPresent()) {
      return;
    }
    AbsPath absolutePathWithHash = outputPath.get();
    Optional<Path> maybeAbsolutePathWithoutHash =
        BuildPaths.removeHashFrom(absolutePathWithHash.getPath(), rule.getBuildTarget());
    if (!maybeAbsolutePathWithoutHash.isPresent()) {
      // hash was not found, for example `export_file` rule outputs files in source directory, not
      // in buck-out
      // so we don't create any links
      return;
    }
    AbsPath absolutePathWithoutHash = AbsPath.of(maybeAbsolutePathWithoutHash.get());
    MostFiles.deleteRecursivelyIfExists(absolutePathWithoutHash);

    // If any of the components of the path we want to write exist as files, directory creation will
    // fail. Delete any such files (as long as they are within buck-out, we don't want to delete
    // random stuff on the machine).
    AbsPath expectedRoot =
        rule.getProjectFilesystem()
            .resolve(rule.getProjectFilesystem().getBuckPaths().getBuckOut());
    AbsPath parent = absolutePathWithoutHash.getParent();
    while (parent.startsWith(expectedRoot)) {
      if (Files.exists(parent.getPath()) && !Files.isDirectory(parent.getPath())) {
        Files.delete(parent.getPath());
      }
      parent = parent.getParent();
    }

    Files.createDirectories(absolutePathWithoutHash.getParent().getPath());

    // Support rule-specific output linking.
    if (rule instanceof CustomHashedBuckOutLinking) {
      // If this rule doesn't support hard-linking, downgrade to symlinking.
      if (linkMode == HashedBuckOutLinkMode.HARDLINK
          && !((CustomHashedBuckOutLinking) rule).supportsHashedBuckOutHardLinking()) {
        linkMode = HashedBuckOutLinkMode.SYMLINK;
      }
    }

    switch (linkMode) {
      case SYMLINK:
        Files.createSymbolicLink(absolutePathWithoutHash.getPath(), absolutePathWithHash.getPath());
        break;
      case HARDLINK:
        boolean isDirectory;
        try {
          isDirectory =
              Files.readAttributes(absolutePathWithHash.getPath(), BasicFileAttributes.class)
                  .isDirectory();
        } catch (NoSuchFileException e) {
          // Rule did not produce a file.
          // It should not be possible, but it happens.
          return;
        }
        if (isDirectory) {
          Files.createSymbolicLink(
              absolutePathWithoutHash.getPath(), absolutePathWithHash.getPath());
        } else {
          Files.createLink(absolutePathWithoutHash.getPath(), absolutePathWithHash.getPath());
        }
        break;
      case NONE:
        break;
    }
  }

  private void symLinkBuildRuleResult(
      SourcePathResolverAdapter pathResolver,
      BuckConfig buckConfig,
      AbsPath lastOutputDirPath,
      BuildRule rule,
      OutputLabel outputLabel)
      throws IOException {
    Optional<AbsPath> outputPath =
        PathUtils.getUserFacingOutputPath(
            pathResolver,
            rule,
            buckConfig.getView(BuildBuckConfig.class).getBuckOutCompatLink(),
            outputLabel);
    if (outputPath.isPresent()) {
      Path absolutePath = outputPath.get().getPath();
      RelPath destPath;
      try {
        destPath = lastOutputDirPath.relativize(absolutePath);
      } catch (IllegalArgumentException e) {
        // Troubleshooting a potential issue with windows relativizing things
        String msg =
            String.format(
                "Could not relativize %s to %s: %s",
                absolutePath, lastOutputDirPath, e.getMessage());
        throw new IllegalArgumentException(msg, e);
      }
      AbsPath linkPath = lastOutputDirPath.resolve(absolutePath.getFileName());
      // Don't overwrite existing symlink in case there are duplicate names.
      if (!Files.exists(linkPath.getPath(), LinkOption.NOFOLLOW_LINKS)) {
        ProjectFilesystem projectFilesystem = rule.getProjectFilesystem();
        projectFilesystem.createSymLink(linkPath, destPath.getPath(), false);
      }
    }
  }

  private void symLinkBuildResults(
      CommandRunnerParams params, GraphsAndBuildTargets graphsAndBuildTargets) throws IOException {
    // Clean up last buck-out/last.
    ProjectFilesystem filesystem = params.getCells().getRootCell().getFilesystem();
    AbsPath lastOutputDirPath =
        filesystem.getRootPath().resolve(filesystem.getBuckPaths().getLastOutputDir());
    MostFiles.deleteRecursivelyIfExists(lastOutputDirPath);
    Files.createDirectories(lastOutputDirPath.getPath());

    ActionGraphBuilder graphBuilder =
        graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder().getActionGraphBuilder();
    SourcePathResolverAdapter pathResolver = graphBuilder.getSourcePathResolver();

    for (BuildTargetWithOutputs targetWithOutputs :
        graphsAndBuildTargets.getBuildTargetWithOutputs()) {
      BuildRule rule = graphBuilder.requireRule(targetWithOutputs.getBuildTarget());
      // If it's an apple bundle, we'd like to also link the dSYM file over here.
      if (rule instanceof AppleBundle) {
        AppleBundle bundle = (AppleBundle) rule;
        Optional<AppleDsym> dsym = bundle.getAppleDsym();
        if (dsym.isPresent()) {
          symLinkBuildRuleResult(
              pathResolver,
              params.getBuckConfig(),
              lastOutputDirPath,
              dsym.get(),
              targetWithOutputs.getOutputLabel());
        }
      }
      symLinkBuildRuleResult(
          pathResolver,
          params.getBuckConfig(),
          lastOutputDirPath,
          rule,
          targetWithOutputs.getOutputLabel());
    }
  }

  private Optional<DefaultRuleKeyFactory> checkAndReturnRuleKeyFactory(
      CommandRunnerParams params,
      ActionGraphBuilder actionGraphBuilder,
      RuleKeyCacheScope<RuleKey> ruleKeyCacheScope) {
    Optional<DefaultRuleKeyFactory> ruleKeyFactory = Optional.empty();
    if (isShowRuleKey()) {
      RuleKeyFieldLoader fieldLoader = new RuleKeyFieldLoader(params.getRuleKeyConfiguration());
      ruleKeyFactory =
          Optional.of(
              new DefaultRuleKeyFactory(
                  fieldLoader,
                  params.getFileHashCache(),
                  actionGraphBuilder,
                  ruleKeyCacheScope.getCache(),
                  Optional.empty()));
    }
    return ruleKeyFactory;
  }

  private Optional<Path> getOutputPath(
      BuildRule rule,
      OutputLabel outputLabel,
      SourcePathResolverAdapter sourcePathResolver,
      boolean buckOutCompatLink,
      ProjectFilesystem fileSystem) {
    return PathUtils.getUserFacingOutputPath(
            sourcePathResolver, rule, buckOutCompatLink, outputLabel)
        .map(path -> isShowOutputsPathAbsolute() ? path : fileSystem.relativize(path))
        .map(PathWrapper::getPath);
  }

  private void addOutputForJson(
      Map<String, String> sortedJsonOutputs,
      BuildTargetWithOutputs targetWithOutputs,
      Optional<Path> outputPath) {
    sortedJsonOutputs.put(
        targetWithOutputs.toString(), outputPath.map(Object::toString).orElse(""));
  }

  private String addOutputForList(
      BuildRule rule,
      Optional<DefaultRuleKeyFactory> ruleKeyFactory,
      BuildTargetWithOutputs targetWithOutputs,
      Optional<Path> outputPath) {
    return String.format(
        "%s%s%s%n",
        targetWithOutputs,
        isShowRuleKey() ? " " + ruleKeyFactory.get().build(rule) : "",
        isShowListOutput() ? getOutputPathToShow(outputPath) : "");
  }

  private Set<BuildTargetWithOutputs> computeTargetsToPrintForShowAllOutputs(
      Set<BuildTargetWithOutputs> targetsWithOutputs, ActionGraphBuilder graphBuilder) {
    ImmutableSet.Builder<BuildTargetWithOutputs> buildTargetsToCompute =
        new ImmutableSet.Builder<>();
    for (BuildTargetWithOutputs targetWithOutputs : targetsWithOutputs) {
      BuildTarget buildTarget = targetWithOutputs.getBuildTarget();
      BuildRule buildRule = graphBuilder.requireRule(buildTarget);
      if (buildRule instanceof HasMultipleOutputs
          && targetWithOutputs.getOutputLabel().isDefault()) {
        HasMultipleOutputs multipleOutputsRule = (HasMultipleOutputs) buildRule;
        for (OutputLabel label : multipleOutputsRule.getOutputLabels()) {
          buildTargetsToCompute.add(BuildTargetWithOutputs.of(buildTarget, label));
        }
      } else {
        buildTargetsToCompute.add(targetWithOutputs);
      }
    }
    return buildTargetsToCompute.build();
  }

  private void printTargets(
      Set<BuildTargetWithOutputs> buildTargetsToPrint,
      CommandRunnerParams params,
      ActionGraphBuilder graphBuilder,
      Optional<DefaultRuleKeyFactory> ruleKeyFactory)
      throws IOException {
    TreeMap<String, String> sortedJsonOutputs = new TreeMap<>();
    PrintStream stdOut = params.getConsole().getStdOut();

    for (BuildTargetWithOutputs buildTargetToCompute : buildTargetsToPrint) {
      BuildRule buildRule = graphBuilder.requireRule(buildTargetToCompute.getBuildTarget());
      stdOut.flush();
      Optional<Path> outputPath =
          getOutputPath(
              buildRule,
              buildTargetToCompute.getOutputLabel(),
              graphBuilder.getSourcePathResolver(),
              params.getBuckConfig().getView(BuildBuckConfig.class).getBuckOutCompatLink(),
              params.getCells().getRootCell().getFilesystem());
      if (isShowJsonOutput()) {
        addOutputForJson(sortedJsonOutputs, buildTargetToCompute, outputPath);
      } else {
        stdOut.print(addOutputForList(buildRule, ruleKeyFactory, buildTargetToCompute, outputPath));
      }
    }
    if (isShowJsonOutput()) {
      // Print the build rule information as JSON.
      StringWriter stringWriter = new StringWriter();
      ObjectMappers.WRITER.withDefaultPrettyPrinter().writeValue(stringWriter, sortedJsonOutputs);
      stdOut.println(stringWriter.toString());
    }
  }

  private void showOutputs(
      CommandRunnerParams params,
      GraphsAndBuildTargets graphsAndBuildTargets,
      RuleKeyCacheScope<RuleKey> ruleKeyCacheScope)
      throws IOException {
    ActionGraphBuilder graphBuilder =
        graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder().getActionGraphBuilder();
    Optional<DefaultRuleKeyFactory> ruleKeyFactory =
        checkAndReturnRuleKeyFactory(params, graphBuilder, ruleKeyCacheScope);
    printTargets(
        graphsAndBuildTargets.getBuildTargetWithOutputs(), params, graphBuilder, ruleKeyFactory);
  }

  private void showAllOutputs(
      CommandRunnerParams params,
      GraphsAndBuildTargets graphsAndBuildTargets,
      RuleKeyCacheScope<RuleKey> ruleKeyCacheScope)
      throws IOException {
    ActionGraphBuilder graphBuilder =
        graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder().getActionGraphBuilder();
    Optional<DefaultRuleKeyFactory> ruleKeyFactory =
        checkAndReturnRuleKeyFactory(params, graphBuilder, ruleKeyCacheScope);
    Set<BuildTargetWithOutputs> buildTargetsToPrint =
        computeTargetsToPrintForShowAllOutputs(
            graphsAndBuildTargets.getBuildTargetWithOutputs(), graphBuilder);
    printTargets(buildTargetsToPrint, params, graphBuilder, ruleKeyFactory);
  }

  private boolean isShowRuleKey() {
    return showRuleKey;
  }

  private boolean isShowListOutput() {
    return showOutput
        || showFullOutput
        || (isShowAllOutputs()
            && (showAllOutputsFormat.equals(ShowAllOutputsFormat.LIST)
                || showAllOutputsFormat.equals(ShowAllOutputsFormat.FULL_LIST)));
  }

  private boolean isShowAllOutputs() {
    return isShowAllOutputs;
  }

  private boolean isShowOutput() {
    return showOutput;
  }

  private boolean isShowOutputsPathAbsolute() {
    return showFullOutput
        || showFullJsonOutput
        || (isShowAllOutputs()
            && (showAllOutputsFormat.equals(ShowAllOutputsFormat.FULL_JSON)
                || showAllOutputsFormat.equals(ShowAllOutputsFormat.FULL_LIST)));
  }

  private boolean isShowJsonOutput() {
    return showJsonOutput
        || showFullJsonOutput
        || (isShowAllOutputs()
            && (showAllOutputsFormat.equals(ShowAllOutputsFormat.JSON)
                || showAllOutputsFormat.equals(ShowAllOutputsFormat.FULL_JSON)));
  }

  private String getOutputPathToShow(Optional<Path> path) {
    return path.map(Objects::toString)
        .filter(Predicates.not(String::isEmpty))
        .map(s -> " " + s)
        .orElse("");
  }

  private ExitCode executeLocalBuild(
      CommandRunnerParams params,
      GraphsAndBuildTargets graphsAndBuildTargets,
      WeightedListeningExecutorService executor,
      Optional<ThriftRuleKeyLogger> ruleKeyLogger,
      Optional<CountDownLatch> initializeBuildLatch,
      RuleKeyCacheScope<RuleKey> ruleKeyCacheScope,
      AtomicReference<Build> buildReference)
      throws Exception {

    ActionGraphAndBuilder actionGraphAndBuilder =
        graphsAndBuildTargets.getGraphs().getActionGraphAndBuilder();
    boolean remoteExecutionAutoEnabled =
        params
            .getBuckConfig()
            .getView(RemoteExecutionConfig.class)
            .isRemoteExecutionAutoEnabled(
                params.getBuildEnvironmentDescription().getUser(), getArguments());
    BuildExecutor builder =
        new BuildExecutor(
            params.createBuilderArgs(),
            getExecutionContext(),
            actionGraphAndBuilder,
            new LocalCachingBuildEngineDelegate(params.getFileHashCache()),
            executor,
            isKeepGoing(),
            ruleKeyCacheScope,
            getBuildEngineMode(),
            ruleKeyLogger,
            params.getMetadataProvider(),
            params.getTargetConfigurationSerializer(),
            remoteExecutionAutoEnabled,
            isRemoteExecutionForceDisabled());
    // TODO(buck_team): use try-with-resources instead
    try {
      buildReference.set(builder.getBuild());
      localRuleKeyCalculator.set(builder.getCachingBuildEngine().getRuleKeyCalculator());

      // Signal to other threads that lastBuild has now been set.
      initializeBuildLatch.ifPresent(CountDownLatch::countDown);

      Iterable<BuildTarget> targets =
          FluentIterable.concat(
              graphsAndBuildTargets.getBuildTargets(),
              getAdditionalTargetsToBuild(graphsAndBuildTargets));

      return builder.buildTargets(targets, getPathToBuildReport(params.getBuckConfig()));
    } finally {
      builder.shutdown();
    }
  }

  private RuleKeyCacheScope<RuleKey> getDefaultRuleKeyCacheScope(
      CommandRunnerParams params, ActionGraphAndBuilder actionGraphAndBuilder) {
    return getDefaultRuleKeyCacheScope(
        params,
        new RuleKeyCacheRecycler.SettingsAffectingCache(
            params.getBuckConfig().getView(BuildBuckConfig.class).getKeySeed(),
            actionGraphAndBuilder.getActionGraph()));
  }

  @Override
  protected ExecutionContext.Builder getExecutionContextBuilder(CommandRunnerParams params) {
    return super.getExecutionContextBuilder(params)
        .setShouldReportAbsolutePaths(shouldReportAbsolutePaths());
  }

  @SuppressWarnings("unused")
  protected Iterable<BuildTarget> getAdditionalTargetsToBuild(
      GraphsAndBuildTargets graphsAndBuildTargets) {
    return ImmutableList.of();
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public boolean isSourceControlStatsGatheringEnabled() {
    return true;
  }

  Build getBuild() {
    return Objects.requireNonNull(lastBuild.get());
  }

  @Override
  public String getShortDescription() {
    return "builds the specified target";
  }

  @Override
  public Iterable<BuckEventListener> getEventListeners(
      Map<ExecutorPool, ListeningExecutorService> executorPool,
      ScheduledExecutorService scheduledExecutorService) {
    return ImmutableList.of();
  }

  @Override
  public boolean performsBuild() {
    return true;
  }

  /** Data class for graph and targets. */
  @BuckStyleValue
  interface GraphsAndBuildTargets {

    ActionAndTargetGraphs getGraphs();

    ImmutableSet<BuildTargetWithOutputs> getBuildTargetWithOutputs();

    @Value.Lazy
    default ImmutableSet<BuildTarget> getBuildTargets() {
      ImmutableSet.Builder<BuildTarget> builder =
          ImmutableSet.builderWithExpectedSize(getBuildTargetWithOutputs().size());
      getBuildTargetWithOutputs()
          .forEach(targetWithOutputs -> builder.add(targetWithOutputs.getBuildTarget()));
      return builder.build();
    }
  }

  /** Result of the build. */
  @BuckStyleValue
  interface BuildRunResult {

    ExitCode getExitCode();

    ImmutableSet<BuildTarget> getBuildTargets();
  }

  /** An exception thrown when action graph creation failed. */
  public static class ActionGraphCreationException extends Exception {

    public ActionGraphCreationException(String message) {
      super(message);
    }
  }
}
