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

package com.facebook.buck.android;

import static com.facebook.buck.util.concurrent.MostExecutors.newMultiThreadExecutor;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.facebook.buck.android.device.TargetDeviceOptions;
import com.facebook.buck.android.exopackage.AndroidDevice;
import com.facebook.buck.android.exopackage.AndroidDevicesHelper;
import com.facebook.buck.android.exopackage.AndroidIntent;
import com.facebook.buck.android.exopackage.ExopackageInfo;
import com.facebook.buck.android.exopackage.ExopackageInstaller;
import com.facebook.buck.android.exopackage.RealAndroidDevice;
import com.facebook.buck.core.build.execution.context.ExecutionContext;
import com.facebook.buck.core.exceptions.BuckUncheckedExecutionException;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.InstallEvent;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.event.StartActivityEvent;
import com.facebook.buck.event.UninstallEvent;
import com.facebook.buck.log.GlobalStateManager;
import com.facebook.buck.step.AdbOptions;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.MoreSuppliers;
import com.facebook.buck.util.Scope;
import com.facebook.buck.util.Threads;
import com.facebook.buck.util.concurrent.CommandThreadFactory;
import com.facebook.buck.util.concurrent.MostExecutors;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Helper for executing commands over ADB, especially for multiple devices. */
public class AdbHelper implements AndroidDevicesHelper {
  private static final Logger log = Logger.get(AdbHelper.class);
  private static final long ADB_CONNECT_TIMEOUT_MS = 5000;
  private static final long ADB_CONNECT_TIME_STEP_MS = ADB_CONNECT_TIMEOUT_MS / 10;

  /** Pattern that matches safe package names. (Must be a full string match). */
  public static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[\\w.-]+");

  private static Optional<Supplier<ImmutableList<AndroidDevice>>> devicesSupplierForTests =
      Optional.empty();

  /**
   * If this environment variable is set, the device with the specified serial number is targeted.
   * The -s option overrides this.
   */
  static final String SERIAL_NUMBER_ENV = "ANDROID_SERIAL";

  /**
   * The next port number to use for communicating with the agent on a device. This resets for every
   * instance of AdbHelper, but is incremented for every device on every call to adbCall().
   */
  private final AtomicInteger nextAgentPort;

  private final AdbOptions options;
  private final TargetDeviceOptions deviceOptions;
  private final Optional<String> adbExecutableHelper;
  private final Supplier<ExecutionContext> contextSupplier;
  private final boolean restartAdbOnFailure;
  // Caches the list of android devices for this execution
  private final Supplier<GetDevicesResult> devicesSupplier;
  private final boolean skipMetadataIfNoInstalls;
  private final boolean alwaysUseJavaAgent;
  private final boolean isZstdCompressionEnabled;
  private final AndroidInstallPrinter androidPrinter;

  @Nullable private ListeningExecutorService executorService = null;

  public AdbHelper(
      AdbOptions adbOptions,
      TargetDeviceOptions deviceOptions,
      Supplier<ExecutionContext> contextSupplier,
      AndroidInstallPrinter androidPrinter,
      Optional<String> adbExecutable,
      boolean restartAdbOnFailure,
      boolean skipMetadataIfNoInstalls,
      boolean alwaysUseJavaAgent,
      boolean isZstdCompressionEnabled,
      int agentPortBase) {
    this.options = adbOptions;
    this.deviceOptions = deviceOptions;
    this.contextSupplier = contextSupplier;
    this.restartAdbOnFailure = restartAdbOnFailure;
    this.devicesSupplier = MoreSuppliers.memoize(this::getDevicesImpl);
    this.androidPrinter = androidPrinter;
    this.adbExecutableHelper = adbExecutable;
    this.skipMetadataIfNoInstalls = skipMetadataIfNoInstalls;
    this.alwaysUseJavaAgent = alwaysUseJavaAgent;
    this.isZstdCompressionEnabled = isZstdCompressionEnabled;
    nextAgentPort = new AtomicInteger(agentPortBase);
  }

  @VisibleForTesting
  public static void setDevicesSupplierForTests(
      Optional<Supplier<ImmutableList<AndroidDevice>>> devicesSupplierForTests) {
    AdbHelper.devicesSupplierForTests = devicesSupplierForTests;
  }

  @Override
  public ImmutableList<AndroidDevice> getDevices(boolean quiet) {
    GetDevicesResult result = devicesSupplier.get();
    if (!quiet && result.devices.size() > 1) {
      // Report if multiple devices are matching the filter.
      androidPrinter.printMessage("Found " + result.devices.size() + " matching devices.\n");
    }
    result.errorMessage.ifPresent(message -> androidPrinter.printError(message));
    return result.devices;
  }

  /**
   * Execute an {@link AdbDeviceCallable} for all matching devices. This functions performs device
   * filtering based on three possible arguments:
   *
   * <p>-e (emulator-only) - only emulators are passing the filter -d (device-only) - only real
   * devices are passing the filter -s (serial) - only device/emulator with specific serial number
   * are passing the filter
   *
   * <p>If more than one device matches the filter this function will fail unless multi-install mode
   * is enabled (-x). This flag is used as a marker that user understands that multiple devices will
   * be used to install the apk if needed.
   */
  @SuppressWarnings("PMD.EmptyCatchBlock")
  @Override
  public synchronized void adbCall(String description, AdbDeviceCallable func, boolean quiet)
      throws InterruptedException {
    List<AndroidDevice> devices;

    try (SimplePerfEvent.Scope ignored =
        SimplePerfEvent.scope(getBuckEventBus().isolated(), "set_up_adb_call")) {
      GetDevicesResult result = devicesSupplier.get();
      if (!quiet && result.devices.size() > 1) {
        // Report if multiple devices are matching the filter.
        androidPrinter.printMessage("Found " + result.devices.size() + " matching devices.\n");
      }
      if (result.errorMessage.isPresent()) {
        throw new HumanReadableException(result.errorMessage.get());
      } else if (result.devices.isEmpty()) {
        throw new HumanReadableException("Didn't find any attached Android devices/emulators.");
      }
      devices = result.devices;
    }

    // Start executions on all matching devices.
    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    for (AndroidDevice device : devices) {
      futures.add(
          getExecutorService()
              .submit(
                  () -> {
                    try (SimplePerfEvent.Scope ignored =
                        SimplePerfEvent.scope(
                            getBuckEventBus().isolated(),
                            SimplePerfEvent.PerfEventTitle.of("adbCall " + description),
                            "device_serial",
                            device.getSerialNumber())) {
                      return func.apply(device);
                    }
                  }));
    }

    // Wait for all executions to complete or fail.
    List<Boolean> results;
    try {
      results = Futures.allAsList(futures).get();
    } catch (ExecutionException ex) {
      throw new BuckUncheckedExecutionException(ex.getCause());
    } catch (InterruptedException e) {
      try {
        Futures.allAsList(futures).cancel(true);
      } catch (CancellationException ignored) {
        // Rethrow original InterruptedException instead.
      }
      Threads.interruptCurrentThread();
      throw e;
    }

    int successCount = 0;
    for (Boolean result : results) {
      if (result) {
        successCount++;
      }
    }
    int failureCount = results.size() - successCount;

    // Report results.
    if (successCount > 0 && !quiet) {
      androidPrinter.printSuccess(
          String.format("Successfully ran %s on %d device(s)", description, successCount));
    }

    if (failureCount != 0) {
      throw new HumanReadableException("Failed to %s on %d device(s).", description, failureCount);
    }
  }

  private synchronized ListeningExecutorService getExecutorService() {
    if (executorService != null) {
      return executorService;
    }
    int deviceCount;
    deviceCount = getDevices(true).size();
    int adbThreadCount = options.getAdbThreadCount();
    if (adbThreadCount <= 0) {
      adbThreadCount = deviceCount;
    }
    adbThreadCount = Math.min(deviceCount, adbThreadCount);
    executorService =
        listeningDecorator(
            newMultiThreadExecutor(
                new CommandThreadFactory(
                    getClass().getSimpleName(),
                    GlobalStateManager.singleton().getThreadToCommandRegister()),
                adbThreadCount));
    return executorService;
  }

  @Override
  public void installApk(
      SourcePathResolverAdapter pathResolver,
      HasInstallableApk hasInstallableApk,
      boolean installViaSd,
      boolean quiet)
      throws InterruptedException {
    InstallEvent.Started started = InstallEvent.started(hasInstallableApk.getBuildTarget());
    if (!quiet) {
      getBuckEventBus().post(started);
    }
    AtomicBoolean success = new AtomicBoolean();
    Set<AndroidDeviceInfo> deviceInfos = new HashSet<>();
    try (Scope ignored =
        () -> {
          if (!quiet) {
            ImmutableMap<String, String> deviceInfoMap = convertToMap(deviceInfos);
            getBuckEventBus()
                .post(
                    InstallEvent.finished(
                        started,
                        success.get(),
                        Optional.empty(),
                        Optional.of(
                            AdbHelper.tryToExtractPackageNameFromManifest(
                                pathResolver, hasInstallableApk.getApkInfo())),
                        deviceInfoMap,
                        AndroidDebugBridge.getSocketAddress() != null
                            ? Optional.of(AndroidDebugBridge.getSocketAddress().getPort())
                            : Optional.empty()));
          }
        }) {

      adbCall(
          "Get device info",
          (device) -> {
            try {
              String locale = getDeviceLocale(device);
              String abi = device.getProperty("ro.product.cpu.abi");
              String buildFingerprint = device.getProperty("ro.build.fingerprint");
              String dpi = getDeviceDpi(device);
              String sdk = device.getProperty("ro.build.version.sdk");
              boolean isEmulator = device.isEmulator();
              deviceInfos.add(
                  AndroidDeviceInfo.of(locale, abi, buildFingerprint, dpi, sdk, isEmulator));
            } catch (Exception e) {
              // Don't log.
            }
            return true;
          },
          true);

      Optional<ExopackageInfo> exopackageInfo = hasInstallableApk.getApkInfo().getExopackageInfo();
      if (exopackageInfo.isPresent()) {
        installApkExopackage(pathResolver, hasInstallableApk, quiet);
      } else {
        installApkDirectly(pathResolver, hasInstallableApk, installViaSd, quiet);
      }
      success.set(true);
    }
  }

  private static ImmutableMap<String, String> convertToMap(Set<AndroidDeviceInfo> infos) {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();

    map.put("install_device_locales", toCommaList(infos, i -> i.getLocale()));
    map.put("install_device_abis", toCommaList(infos, i -> i.getAbi()));
    map.put("install_device_build_fingerprint", toCommaList(infos, i -> i.getBuildFingerprint()));
    map.put("install_device_densities", toCommaList(infos, i -> i.getDensity().toString()));
    map.put("install_device_dpi", toCommaList(infos, i -> i.getDpi()));
    map.put("install_device_sdk", toCommaList(infos, i -> i.getSdk()));
    map.put("install_device_is_emulator", toCommaList(infos, i -> String.valueOf(i.isEmulator())));

    return map.build();
  }

  private static String toCommaList(
      Set<AndroidDeviceInfo> infos, Function<AndroidDeviceInfo, String> mapper) {
    return infos.stream().map(mapper).collect(Collectors.joining(","));
  }

  private static String getDeviceLocale(AndroidDevice device) throws Exception {
    // It's a bit tortuous to get the locale; there are 6 separate properties
    // we need to check to accurately record this.

    // First try "persist.sys" properties, which are the user's chosen language.
    String locale = device.getProperty("persist.sys.locale");
    // Try persist.sys.language + persist.sys.country
    if (Strings.isNullOrEmpty(locale)) {
      String language = device.getProperty("persist.sys.language");
      if (!Strings.isNullOrEmpty(language)) {
        String country = device.getProperty("persist.sys.country");
        if (!Strings.isNullOrEmpty(country)) {
          locale = language + "-" + country;
        }
      }
    }
    // Next try ro.product.locale properties which are the default system locale
    if (Strings.isNullOrEmpty(locale)) {
      locale = device.getProperty("ro.product.locale");
    }
    if (Strings.isNullOrEmpty(locale)) {
      String language = device.getProperty("ro.product.locale.language");
      String country = device.getProperty("ro.product.locale.region");

      // Default to en-US if all else fails
      if (Strings.isNullOrEmpty(language)) {
        language = "en";
      }
      if (Strings.isNullOrEmpty(country)) {
        country = "US-presumed";
      }
      locale = language = "-" + country;
    }
    return locale;
  }

  private static String getDeviceDpi(AndroidDevice device) throws Exception {
    // First, try getting it from a property
    String dpi = device.getProperty("ro.sf.lcd_density");
    if (Strings.isNullOrEmpty(dpi)) {
      // Try getting it from the WindowManager
      dpi = device.getProperty("density");
    }
    return dpi;
  }

  @Override
  public void startActivity(
      SourcePathResolverAdapter pathResolver,
      HasInstallableApk hasInstallableApk,
      @Nullable String activity,
      @Nullable String intentUri,
      boolean waitForDebugger)
      throws IOException {

    // Might need the package name and activities from the AndroidManifest.
    AbsPath pathToManifest =
        pathResolver.getAbsolutePath(hasInstallableApk.getApkInfo().getManifestPath());
    AndroidManifestReader reader = DefaultAndroidManifestReader.forPath(pathToManifest.getPath());

    final AndroidIntent intent;
    final String intentTargetNiceName;
    if (intentUri != null) {
      intent =
          new AndroidIntent(
              null, AndroidIntent.ACTION_VIEW, null, intentUri, null, waitForDebugger);
      intentTargetNiceName = intentUri;
    } else {
      if (activity == null) {
        // Get list of activities that show up in the launcher.
        List<String> launcherActivities = reader.getLauncherActivities();

        // Sanity check.
        if (launcherActivities.isEmpty()) {
          throw new HumanReadableException("No launchable activities found.");
        } else if (launcherActivities.size() > 1) {
          throw new HumanReadableException("Default activity is ambiguous.");
        }

        // Construct a component for the '-n' argument of 'adb shell am start'.
        activity = reader.getPackage() + "/" + launcherActivities.get(0);
      } else if (!activity.contains("/")) {
        // If no package name was provided, assume the one in the manifest.
        activity = reader.getPackage() + "/" + activity;
      }

      //  0x10200000 is FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | FLAG_ACTIVITY_NEW_TASK; the
      // constant values are public ABI.  This way of invoking "am start" makes buck install -r
      // act just like the launcher, avoiding activity duplication on subsequent
      // launcher starts.
      intent =
          new AndroidIntent(
              activity,
              AndroidIntent.ACTION_MAIN,
              AndroidIntent.CATEGORY_LAUNCHER,
              null,
              "0x10200000",
              waitForDebugger);
      intentTargetNiceName = activity;
    }

    androidPrinter.printMessage(String.format("Starting activity %s...", intentTargetNiceName));

    StartActivityEvent.Started started =
        StartActivityEvent.started(hasInstallableApk.getBuildTarget(), intentTargetNiceName);
    getBuckEventBus().post(started);
    try {
      adbCallOrThrow(
          "start activity",
          (device) -> {
            ((RealAndroidDevice) device).deviceStartIntent(intent);
            return true;
          },
          false);
      getBuckEventBus().post(StartActivityEvent.finished(started, true));
    } catch (Exception e) {
      getBuckEventBus().post(StartActivityEvent.finished(started, false));
    }
  }

  /**
   * Uninstall apk from all matching devices.
   *
   * @see #installApk(SourcePathResolverAdapter, HasInstallableApk, boolean, boolean)
   */
  @Override
  public void uninstallApp(String packageName, boolean shouldKeepUserData)
      throws InterruptedException {
    Preconditions.checkArgument(AdbHelper.PACKAGE_NAME_PATTERN.matcher(packageName).matches());

    UninstallEvent.Started started = UninstallEvent.started(packageName);
    getBuckEventBus().post(started);
    try {
      adbCall(
          "uninstall apk",
          (device) -> {
            ((RealAndroidDevice) device).uninstallApkFromDevice(packageName, shouldKeepUserData);
            return true;
          },
          false);
    } catch (RuntimeException e) {
      getBuckEventBus().post(UninstallEvent.finished(started, false));
      throw e;
    }
    getBuckEventBus().post(UninstallEvent.finished(started, true));
  }

  public static String tryToExtractPackageNameFromManifest(
      SourcePathResolverAdapter pathResolver, HasInstallableApk.ApkInfo apkInfo) {
    AbsPath pathToManifest = pathResolver.getAbsolutePath(apkInfo.getManifestPath());
    return tryToExtractPackageNameFromManifest(pathToManifest.getPath());
  }

  static String tryToExtractPackageNameFromManifest(Path pathToManifest) {
    // Note that the file may not exist if AndroidManifest.xml is a generated file
    // and the rule has not been built yet.
    if (!Files.isRegularFile(pathToManifest)) {
      throw new HumanReadableException(
          "Manifest file %s does not exist, so could not extract package name.", pathToManifest);
    }

    try {
      return DefaultAndroidManifestReader.forPath(pathToManifest).getPackage();
    } catch (IOException e) {
      throw new HumanReadableException("Could not extract package name from %s", pathToManifest);
    }
  }

  private BuckEventBus getBuckEventBus() {
    return contextSupplier.get().getBuckEventBus();
  }

  /**
   * Returns list of devices that pass the filter. If there is an invalid combination or no devices
   * are left after filtering this function prints an error and returns null.
   */
  @Nullable
  @VisibleForTesting
  List<IDevice> filterDevices(IDevice[] allDevices) {
    if (allDevices.length == 0) {
      androidPrinter.printError("No devices are found.");
      return null;
    }

    List<IDevice> devices = new ArrayList<>();
    Optional<Boolean> emulatorsOnly = Optional.empty();
    if (deviceOptions.isEmulatorsOnlyModeEnabled() && options.isMultiInstallModeEnabled()) {
      emulatorsOnly = Optional.empty();
    } else if (deviceOptions.isEmulatorsOnlyModeEnabled()) {
      emulatorsOnly = Optional.of(true);
    } else if (deviceOptions.isRealDevicesOnlyModeEnabled()) {
      emulatorsOnly = Optional.of(false);
    }

    int onlineDevices = 0;
    for (IDevice device : allDevices) {
      boolean passed = false;
      if (device.isOnline()) {
        onlineDevices++;

        boolean serialMatches = true;
        if (deviceOptions.getSerialNumber().isPresent()) {
          serialMatches = device.getSerialNumber().equals(deviceOptions.getSerialNumber().get());
        } else if (getEnvironment().containsKey(SERIAL_NUMBER_ENV)) {
          serialMatches = device.getSerialNumber().equals(getEnvironment().get(SERIAL_NUMBER_ENV));
        }

        // Only devices of specific type are accepted:
        // either real devices only or emulators only.
        // All online devices match.
        boolean deviceTypeMatches =
            emulatorsOnly
                .map(isEmulatorOnly -> (isEmulatorOnly == createDevice(device).isEmulator()))
                .orElse(true);
        passed = serialMatches && deviceTypeMatches;
      }

      if (passed) {
        devices.add(device);
      }
    }

    // Filtered out all devices.
    if (onlineDevices == 0) {
      androidPrinter.printError("No devices are found.");
      return null;
    }

    if (devices.isEmpty()) {
      androidPrinter.printError(
          String.format(
              "Found %d connected device(s), but none of them matches specified filter.",
              onlineDevices));
      return null;
    }

    return devices;
  }

  private ImmutableMap<String, String> getEnvironment() {
    return contextSupplier.get().getEnvironment();
  }

  private RealAndroidDevice createDevice(IDevice device) {
    return new RealAndroidDevice(
        getBuckEventBus(),
        device,
        getConsole(),
        getApkFilePathFromProperties().orElse(null),
        nextAgentPort.getAndIncrement(),
        alwaysUseJavaAgent,
        isZstdCompressionEnabled);
  }

  @VisibleForTesting
  AndroidDebugBridgeFacade createAdb() {
    return new AndroidDebugBridgeFacadeImpl(getAdbExecutable());
  }

  /**
   * Creates connection to adb and waits for this connection to be initialized and receive initial
   * list of devices.
   *
   * <p>The returned bridge is not guaranteed to be connected.
   */
  private boolean waitForConnection(AndroidDebugBridgeFacade adb) {
    if (!adb.connect()) {
      getConsole().printBuildFailure("Failed to connect to adb. Make sure adb server is running.");
      return false;
    }

    waitUntil(() -> adb.isInitialized(), ADB_CONNECT_TIMEOUT_MS, ADB_CONNECT_TIME_STEP_MS);
    return adb.isInitialized();
  }

  private static void waitUntil(Supplier<Boolean> condition, long timeoutMs, long stepTimeMs) {
    long start = System.currentTimeMillis();
    while (!condition.get()) {
      long timeLeft = start + timeoutMs - System.currentTimeMillis();
      if (timeLeft <= 0) {
        break;
      }
      try {
        Thread.sleep(stepTimeMs);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private String getAdbExecutable() {
    return this.adbExecutableHelper.orElseThrow(
        () -> new HumanReadableException("No AdbExectuable set"));
  }

  private static class GetDevicesResult {
    private final ImmutableList<AndroidDevice> devices;
    private final Optional<String> errorMessage;

    private GetDevicesResult(ImmutableList<AndroidDevice> devices, Optional<String> errorMessage) {
      this.devices = devices;
      this.errorMessage = errorMessage;
    }

    static GetDevicesResult createSuccess(ImmutableList<AndroidDevice> devices) {
      return new GetDevicesResult(devices, Optional.empty());
    }

    static GetDevicesResult createFailure(String message) {
      return new GetDevicesResult(ImmutableList.of(), Optional.of(message));
    }
  }

  private GetDevicesResult getDevicesImpl() {
    if (devicesSupplierForTests.isPresent()) {
      return GetDevicesResult.createSuccess(devicesSupplierForTests.get().get());
    }

    // Initialize adb connection.
    AndroidDebugBridgeFacade adb = createAdb();
    waitForConnection(adb);
    if (!adb.isConnected()) {
      // Try resetting state and reconnecting
      androidPrinter.printError("Unable to reconnect to existing server, starting a new one");
      adb.terminate();
      waitForConnection(adb);
    }
    if (!adb.isConnected()) {
      return GetDevicesResult.createFailure("Failed to create adb connection.");
    }

    // Build list of matching devices.
    List<IDevice> devices = filterDevices(adb.getDevices());
    // Found multiple devices but multi-install mode is not enabled.
    if (devices != null && devices.size() > 1 && !options.isMultiInstallModeEnabled()) {
      return GetDevicesResult.createFailure(
          String.format(
              "%d devices match specified device filter (1 expected).\n"
                  + "Either disconnect other devices or enable multi-install mode (%s).",
              devices.size(), AdbOptions.MULTI_INSTALL_MODE_SHORT_ARG));
    }

    if (devices == null && restartAdbOnFailure) {
      androidPrinter.printError("No devices found with adb, restarting adb-server.");
      adb.restart();
      devices = filterDevices(adb.getDevices());
    }
    if (devices == null && restartAdbOnFailure) {
      androidPrinter.printError(
          "No devices found with adb after restart, terminating and restarting adb-server.");
      adb.terminate();
      if (!waitForConnection(adb)) {
        return GetDevicesResult.createFailure("Failed to re-create adb connection.");
      }
      devices = filterDevices(adb.getDevices());
    }
    if (devices == null) {
      return GetDevicesResult.createSuccess(ImmutableList.of());
    }
    return GetDevicesResult.createSuccess(
        devices.stream().map(this::createDevice).collect(ImmutableList.toImmutableList()));
  }

  private Console getConsole() {
    return contextSupplier.get().getConsole();
  }

  private static Optional<Path> getApkFilePathFromProperties() {
    String apkFileName = System.getProperty("buck.android_agent_path");
    return Optional.ofNullable(apkFileName).map(Paths::get);
  }

  @Override
  public synchronized void close() {
    // getExecutorService() requires the context for lazy initialization, so explicitly check if it
    // has been initialized.
    if (executorService != null) {
      MostExecutors.shutdownOrThrow(
          executorService,
          10,
          TimeUnit.MINUTES,
          new RuntimeException("Failed to shutdown ExecutorService."));
      executorService = null;
    }
  }

  /** An exception that indicates that an executed command returned an unsuccessful exit code. */
  public static class CommandFailedException extends IOException {
    public final String command;
    public final int exitCode;
    public final String output;

    public CommandFailedException(String command, int exitCode, String output) {
      super("Command '" + command + "' failed with code " + exitCode + ".  Output:\n" + output);
      this.command = command;
      this.exitCode = exitCode;
      this.output = output;
    }
  }

  private void installApkExopackage(
      SourcePathResolverAdapter pathResolver, HasInstallableApk hasInstallableApk, boolean quiet)
      throws InterruptedException {
    adbCall(
        "install exopackage apk",
        device -> {
          new ExopackageInstaller(
                  pathResolver,
                  contextSupplier.get().getBuckEventBus(),
                  hasInstallableApk.getProjectFilesystem(),
                  tryToExtractPackageNameFromManifest(pathResolver, hasInstallableApk.getApkInfo()),
                  device,
                  skipMetadataIfNoInstalls)
              .doInstall(hasInstallableApk.getApkInfo());
          return true;
        },
        quiet);
  }

  private void installApkDirectly(
      SourcePathResolverAdapter pathResolver,
      HasInstallableApk hasInstallableApk,
      boolean installViaSd,
      boolean quiet)
      throws InterruptedException {
    File apk = pathResolver.getAbsolutePath(hasInstallableApk.getApkInfo().getApkPath()).toFile();
    adbCall(
        String.format("install apk %s", hasInstallableApk.getBuildTarget().toString()),
        (device) -> device.installApkOnDevice(apk, installViaSd, quiet),
        quiet);
  }

  /**
   * A facade for the AndroidDebugBridge which makes it easier to test logic in AdbHelper without
   * talking to a real adb.
   */
  @VisibleForTesting
  abstract static class AndroidDebugBridgeFacade {
    /** Initializes and connects the debug bridge. */
    boolean connect() {
      return false;
    }

    /** Returns true if the bridge is connected. */
    boolean isConnected() {
      return false;
    }

    /** Returns true if the bridge has an initial device list. */
    boolean hasInitialDeviceList() {
      return false;
    }

    /** Returns connected devices. */
    IDevice[] getDevices() {
      return new IDevice[0];
    }

    /** Restarts the adb server. */
    boolean restart() {
      return false;
    }

    /** Terminates adb and disconnects the bridge. */
    void terminate() {}

    /** Returns true if the bridge is initialized. */
    final boolean isInitialized() {
      return isConnected() && hasInitialDeviceList();
    }
  }

  private class AndroidDebugBridgeFacadeImpl extends AndroidDebugBridgeFacade {
    private final String adbExecutable;
    private @Nullable AndroidDebugBridge bridge;

    AndroidDebugBridgeFacadeImpl(String adbExecutable) {
      this.adbExecutable = adbExecutable;
    }

    @Override
    public boolean connect() {
      DdmPreferences.setTimeOut(options.getAdbTimeout());

      try {
        AndroidDebugBridge.init(/* clientSupport */ false);
      } catch (IllegalStateException ex) {
        // ADB was already initialized, we're fine, so just ignore.
      }

      log.debug("Using %s to create AndroidDebugBridge", adbExecutable);
      this.bridge = AndroidDebugBridge.createBridge(adbExecutable, false);
      return this.bridge != null;
    }

    @Override
    public boolean isConnected() {
      return bridge != null && bridge.isConnected();
    }

    @Override
    public boolean hasInitialDeviceList() {
      return bridge != null && bridge.hasInitialDeviceList();
    }

    @Override
    public IDevice[] getDevices() {
      if (bridge == null) {
        throw new IllegalStateException("Not connected");
      }
      return bridge.getDevices();
    }

    @Override
    public boolean restart() {
      if (bridge == null) {
        throw new IllegalStateException("Not connected");
      }
      return bridge.restart();
    }

    @Override
    public void terminate() {
      AndroidDebugBridge.disconnectBridge();
      AndroidDebugBridge.terminate();
    }
  }
}
