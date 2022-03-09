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

import com.facebook.buck.cli.MainRunner.KnownRuleTypesFactoryFactory;
import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.module.BuckModuleManager;
import com.facebook.buck.core.module.impl.BuckModuleJarHashProvider;
import com.facebook.buck.core.module.impl.DefaultBuckModuleManager;
import com.facebook.buck.core.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.core.rules.knowntypes.DefaultKnownNativeRuleTypesFactory;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.support.bgtasks.BackgroundTaskManager;
import com.facebook.buck.support.state.BuckGlobalStateLifecycleManager;
import com.facebook.buck.util.Ansi;
import com.facebook.buck.util.AnsiEnvironmentChecking;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.Verbosity;
import com.facebook.buck.util.environment.CommandMode;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.shutdown.NonReentrantSystemExit;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import javax.annotation.Nullable;
import org.pf4j.PluginManager;

/**
 * The abstract entry point of Buck commands for both {@link MainWithoutNailgun} and {@link
 * MainWithNailgun}
 */
abstract class AbstractMain {

  private static final String BUCK_BUILD_ID_ENV_VAR = "BUCK_BUILD_ID";
  private static final Logger LOG = Logger.get(AbstractMain.class);
  private static PluginManager pluginManager;
  private static BuckModuleManager moduleManager;

  private static final NonReentrantSystemExit NON_REENTRANT_SYSTEM_EXIT =
      new NonReentrantSystemExit();

  static {
    pluginManager = BuckPluginManagerFactory.createPluginManager();
    moduleManager = new DefaultBuckModuleManager(pluginManager, new BuckModuleJarHashProvider());
  }

  protected final InputStream stdIn;

  protected final ImmutableMap<String, String> clientEnvironment;
  protected final Platform platform;
  private final Path projectRoot;
  private final @Nullable String rawClientPwd;

  private final Console defaultConsole;
  private final CommandMode commandMode;
  private final DaemonMode daemonMode;

  /**
   * The constructor with certain defaults for use by {@link MainWithNailgun} and {@link
   * MainWithoutNailgun}.
   *
   * <ul>
   *   <li>The default {@link Console} will be constructed from the streams
   *   <li>command mode is default to {@link CommandMode#RELEASE}
   *   <li>The repo root is set to the current running directory which is at the nearest .buckconfig
   * </ul>
   *
   * @param stdOut the output stream for which a {@link Console} is constructed
   * @param stdErr the error output stream for which a {@link Console} is constructed
   * @param stdIn the input stream
   * @param clientEnvironment the environment variable mapping for this command
   * @param platform the running platform
   * @param daemonMode whether this is daemon mode or not
   */
  protected AbstractMain(
      PrintStream stdOut,
      PrintStream stdErr,
      InputStream stdIn,
      ImmutableMap<String, String> clientEnvironment,
      Platform platform,
      DaemonMode daemonMode) {
    this(
        new Console(
            Verbosity.STANDARD_INFORMATION,
            stdOut,
            stdErr,
            new Ansi(
                AnsiEnvironmentChecking.environmentSupportsAnsiEscapes(
                    platform, clientEnvironment))),
        stdIn,
        clientEnvironment,
        platform,
        Paths.get("."),
        clientEnvironment.get("BUCK_CLIENT_PWD"),
        CommandMode.RELEASE,
        daemonMode);
  }

  /**
   * Constructor without certain defaults for testing, so that the console, repo root can be
   * overridden.
   *
   * @param console the console to use
   * @param stdIn the input stream
   * @param clientEnvironment the environment variable mapping for this command
   * @param platform the current platform
   * @param projectRoot the path to the root of the project being built, where the .buckconfig is.
   *     This can be relative like "." or absolute as it is later converted to a "real" path
   * @param commandMode the {@link CommandMode} of either {@link CommandMode#RELEASE} or {@link
   *     CommandMode#TEST}
   * @param daemonMode whether this is daemon mode or not
   */
  protected AbstractMain(
      Console console,
      InputStream stdIn,
      ImmutableMap<String, String> clientEnvironment,
      Platform platform,
      Path projectRoot,
      @Nullable String rawClientPwd,
      CommandMode commandMode,
      DaemonMode daemonMode) {
    this.stdIn = stdIn;

    this.clientEnvironment = clientEnvironment;
    this.platform = platform;
    this.projectRoot = projectRoot;
    this.rawClientPwd = rawClientPwd;
    this.daemonMode = daemonMode;
    this.commandMode = commandMode;
    this.defaultConsole = console;
  }

  /**
   * @return an initialized {@link MainRunner} for running the buck command, with all the base state
   *     setup.
   */
  protected MainRunner prepareMainRunner(
      BackgroundTaskManager bgTaskManager,
      BuckGlobalStateLifecycleManager buckGlobalStateLifecycleManager,
      CommandManager commandManager) {

    return new MainRunner(
        defaultConsole,
        stdIn,
        getKnownRuleTypesFactory(),
        getBuildId(),
        clientEnvironment,
        platform,
        projectRoot,
        rawClientPwd,
        moduleManager,
        bgTaskManager,
        commandMode,
        pluginManager,
        daemonMode,
        buckGlobalStateLifecycleManager,
        commandManager);
  }

  /** @return the {@link KnownRuleTypesFactoryFactory} for this command */
  protected KnownRuleTypesFactoryFactory getKnownRuleTypesFactory() {
    return DefaultKnownNativeRuleTypesFactory::new;
  }

  /**
   * @return the inferred {@link BuildId} from the environment variable or create a new random
   *     {@link BuildId}
   */
  protected BuildId getBuildId() {
    @Nullable String specifiedBuildId = clientEnvironment.get(BUCK_BUILD_ID_ENV_VAR);
    if (specifiedBuildId == null) {
      specifiedBuildId = UUID.randomUUID().toString();
    }
    return new BuildId(specifiedBuildId);
  }

  protected static void exitWithCode(Thread t, Throwable e) {
    ExitCode exitCode = ExitCode.FATAL_GENERIC;
    if (e instanceof OutOfMemoryError) {
      exitCode = ExitCode.FATAL_OOM;
    } else if (e instanceof IOException) {
      exitCode =
          e.getMessage().startsWith("No space left on device")
              ? ExitCode.FATAL_DISK_FULL
              : ExitCode.FATAL_IO;
    }

    // Do not log anything in case we do not have space on the disk
    if (exitCode != ExitCode.FATAL_DISK_FULL) {
      LOG.error(e, "Uncaught exception from thread %s", t);
    }

    NON_REENTRANT_SYSTEM_EXIT.shutdownSoon(exitCode.getCode());
  }
}
