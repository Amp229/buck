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

package com.facebook.buck.apple.toolchain;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.toolchain.Toolchain;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.util.types.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** A collection of provisioning profiles. */
@BuckStyleValue
public abstract class ProvisioningProfileStore implements AddsToRuleKey, Toolchain {
  public static final Optional<ImmutableMap<String, NSObject>> MATCH_ANY_ENTITLEMENT =
      Optional.empty();
  public static final Optional<ImmutableList<CodeSignIdentity>> MATCH_ANY_IDENTITY =
      Optional.empty();

  public static final String DEFAULT_NAME = "apple-provisioning-profiles";

  private static final Logger LOG = Logger.get(ProvisioningProfileStore.class);

  // For those keys let the tooling decide if code signing should fail or succeed (every other key
  // mismatch results in provisioning profile being skipped).
  private static final ImmutableSet<String> IGNORE_MISMATCH_ENTITLEMENTS_KEYS =
      ImmutableSet.of(
          "keychain-access-groups",
          "application-identifier",
          "com.apple.developer.associated-domains",
          "com.apple.developer.icloud-container-development-container-identifiers",
          "com.apple.developer.icloud-container-environment",
          "com.apple.developer.icloud-container-identifiers",
          "com.apple.developer.icloud-services",
          "com.apple.developer.ubiquity-container-identifiers",
          "com.apple.developer.ubiquity-kvstore-identifier");

  public abstract Supplier<ImmutableList<ProvisioningProfileMetadata>>
      getProvisioningProfilesSupplier();

  @AddToRuleKey
  public ImmutableList<ProvisioningProfileMetadata> getProvisioningProfiles() {
    return getProvisioningProfilesSupplier().get();
  }

  private static boolean matchesOrArrayIsSubsetOf(
      String entitlementName,
      @Nullable NSObject expectedEntitlementsPlistValue,
      @Nullable NSObject actualProvisioningProfileValue,
      ApplePlatform platform) {
    if (expectedEntitlementsPlistValue == null) {
      return (actualProvisioningProfileValue == null);
    }

    if (actualProvisioningProfileValue == null
        && platform.getType().isDesktop()
        && entitlementName.startsWith("com.apple.security")) {
      // For macOS apps, including Catalyst, the provisioning profile would _not_ have entries for
      // the sandbox entitlements, so any value matches.
      return true;
    }

    if (expectedEntitlementsPlistValue instanceof NSArray
        && actualProvisioningProfileValue instanceof NSArray) {
      List<NSObject> lhsList = Arrays.asList(((NSArray) expectedEntitlementsPlistValue).getArray());
      List<NSObject> rhsList = Arrays.asList(((NSArray) actualProvisioningProfileValue).getArray());
      return rhsList.containsAll(lhsList);
    }

    return expectedEntitlementsPlistValue.equals(actualProvisioningProfileValue);
  }

  private String getStringFromNSObject(@Nullable NSObject obj) {
    if (obj == null) {
      return "(not set)" + System.lineSeparator();
    } else if (obj instanceof NSArray) {
      return ((NSArray) obj).toASCIIPropertyList();
    } else if (obj instanceof NSDictionary) {
      return ((NSDictionary) obj).toASCIIPropertyList();
    } else {
      return obj.toString() + System.lineSeparator();
    }
  }

  // If multiple valid ones, find the one which matches the most specifically.  I.e.,
  // XXXXXXXXXX.com.example.* will match over XXXXXXXXXX.* for com.example.TestApp
  public Optional<ProvisioningProfileMetadata> getBestProvisioningProfile(
      String bundleID,
      ApplePlatform platform,
      Optional<ImmutableMap<String, NSObject>> entitlements,
      Optional<? extends Iterable<CodeSignIdentity>> identities,
      StringBuffer diagnosticsBuffer) {
    Optional<String> prefix =
        entitlements.flatMap(ProvisioningProfileMetadata::prefixFromEntitlements);
    ImmutableList.Builder<String> lines = ImmutableList.builder();

    int bestMatchLength = -1;
    Optional<ProvisioningProfileMetadata> bestMatch = Optional.empty();

    lines.add(String.format("Looking for a provisioning profile for bundle ID %s", bundleID));

    boolean atLeastOneBundleIdMatch = false;
    for (ProvisioningProfileMetadata profile : getProvisioningProfiles()) {
      Pair<String, String> appID = profile.getAppID();

      LOG.debug("Looking at provisioning profile " + profile.getUUID() + "," + appID);

      if (prefix.isPresent() && !prefix.get().equals(appID.getFirst())) {
        continue;
      }
      final String profileBundleID = appID.getSecond();
      int currentMatchLength = bundleMatchLength(bundleID, profileBundleID);
      if (currentMatchLength == -1) {
        LOG.debug(
            "Ignoring non-matching ID for profile "
                + profile.getUUID()
                + ".  Expected: "
                + profileBundleID
                + ", actual: "
                + bundleID);
        continue;
      }

      atLeastOneBundleIdMatch = true;
      if (!profile.getExpirationDate().after(new Date())) {
        String message =
            "Ignoring expired profile " + profile.getUUID() + ": " + profile.getExpirationDate();
        LOG.debug(message);
        lines.add(message);
        continue;
      }

      Optional<String> platformName = platform.getProvisioningProfileName();
      if (platformName.isPresent() && !profile.getPlatforms().contains(platformName.get())) {
        String message =
            "Ignoring incompatible platform "
                + platformName.get()
                + " for profile "
                + profile.getUUID();
        LOG.debug(message);
        lines.add(message);
        continue;
      }

      if (!checkEntitlementsMatch(entitlements, profile, platform, lines)) {
        continue;
      }

      if (!checkDeveloperCertificatesMatch(profile, identities, lines)) {
        continue;
      }

      if (currentMatchLength > bestMatchLength) {
        bestMatchLength = currentMatchLength;
        bestMatch = Optional.of(profile);
      }
    }

    if (!atLeastOneBundleIdMatch) {
      lines.add(
          String.format("No provisioning profile matching the bundle ID %s was found", bundleID));
    }

    LOG.debug("Found provisioning profile " + bestMatch);
    ImmutableList<String> diagnostics = lines.build();
    diagnosticsBuffer.append(Joiner.on("\n").join(diagnostics));
    return bestMatch;
  }

  private static Integer bundleMatchLength(String expectedBundleId, String bundleIdPattern) {
    if (bundleIdPattern.endsWith("*")) {
      // Chop the ending * if wildcard.
      String patternAsteriskPrefix = bundleIdPattern.substring(0, bundleIdPattern.length() - 1);
      if (expectedBundleId.startsWith(patternAsteriskPrefix)) {
        return patternAsteriskPrefix.length();
      }
    } else if (expectedBundleId.equals(bundleIdPattern)) {
      return bundleIdPattern.length();
    }
    return -1;
  }

  // Match against other keys of the entitlements.  Otherwise, we could potentially select
  // a profile that doesn't have all the needed entitlements, causing a error when
  // installing to device.
  //
  // For example: get-task-allow, aps-environment, etc.
  private boolean checkEntitlementsMatch(
      Optional<ImmutableMap<String, NSObject>> expectedEntitlements,
      ProvisioningProfileMetadata profile,
      ApplePlatform platform,
      ImmutableList.Builder<String> diagnosticsBuilder) {

    if (!expectedEntitlements.isPresent()) {
      return true;
    }

    boolean result = true;

    ImmutableMap<String, NSObject> entitlementsDict = expectedEntitlements.get();
    ImmutableMap<String, NSObject> profileEntitlements = profile.getEntitlements();
    for (Entry<String, NSObject> entry : entitlementsDict.entrySet()) {
      NSObject profileEntitlement = profileEntitlements.get(entry.getKey());
      if (!IGNORE_MISMATCH_ENTITLEMENTS_KEYS.contains(entry.getKey())
          && !matchesOrArrayIsSubsetOf(
              entry.getKey(), entry.getValue(), profileEntitlement, platform)) {
        result = false;
        String profileEntitlementString = getStringFromNSObject(profileEntitlement);
        String entryValueString = getStringFromNSObject(entry.getValue());
        String message =
            "Profile "
                + profile.getProfilePath().getFileName()
                + " ("
                + profile.getUUID()
                + ") with bundleID "
                + profile.getAppID().getSecond()
                + " correctly matches. However there is a mismatched entitlement "
                + entry.getKey()
                + ";"
                + System.lineSeparator()
                + "value in provisioning profile is: "
                + profileEntitlementString
                + "but expected value from entitlements file: "
                + entryValueString;
        LOG.debug(message);
        diagnosticsBuilder.add(message);
      }
    }
    return result;
  }

  private boolean checkDeveloperCertificatesMatch(
      ProvisioningProfileMetadata profile,
      Optional<? extends Iterable<CodeSignIdentity>> identities,
      ImmutableList.Builder<String> diagnosticsBuilder) {
    // Reject any certificate which we know we can't sign with the supplied identities.
    ImmutableSet<HashCode> validFingerprints = profile.getDeveloperCertificateFingerprints();
    if (!identities.isPresent() || validFingerprints.isEmpty()) {
      return true;
    }

    for (CodeSignIdentity identity : identities.get()) {
      Optional<HashCode> fingerprint = identity.getFingerprint();
      if (fingerprint.isPresent() && validFingerprints.contains(fingerprint.get())) {
        return true;
      }
    }

    String message =
        "Ignoring profile "
            + profile.getUUID()
            + " because it can't be signed with any valid identity in the current keychain.";
    LOG.debug(message);
    diagnosticsBuilder.add(message);
    return false;
  }

  public static ProvisioningProfileStore empty() {
    return ProvisioningProfileStore.of(Suppliers.ofInstance(ImmutableList.of()));
  }

  @Override
  public String getName() {
    return DEFAULT_NAME;
  }

  public static ProvisioningProfileStore of(
      Supplier<ImmutableList<ProvisioningProfileMetadata>> provisioningProfilesSupplier) {
    return ImmutableProvisioningProfileStore.ofImpl(provisioningProfilesSupplier);
  }
}
