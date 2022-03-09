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

package com.facebook.buck.intellij.ideabuck.autodeps;

import com.facebook.buck.intellij.ideabuck.api.BuckTarget;
import com.facebook.buck.intellij.ideabuck.api.BuckTargetLocator;
import com.facebook.buck.intellij.ideabuck.build.BuckCommand;
import com.facebook.buck.intellij.ideabuck.build.BuckJsonCommandHandler;
import com.facebook.buck.intellij.ideabuck.build.BuckJsonCommandHandler.Callback;
import com.facebook.buck.intellij.ideabuck.logging.EventLogger;
import com.facebook.buck.intellij.ideabuck.logging.Keys;
import com.facebook.buck.intellij.ideabuck.notification.BuckNotification;
import com.google.gson.JsonElement;
import com.intellij.notification.Notification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An intention that will attempt to add a dependency edge to both the Buck graph and the IntelliJ
 * module graph.
 */
public class BuckAddDependencyIntention extends AbstractBuckAddDependencyIntention {
  private static Logger LOGGER = Logger.getInstance(BuckAddDependencyIntention.class);

  private final VirtualFile importBuildFile;
  private final VirtualFile importSourceFile;
  private final Module importModule;

  // These methods are here to keep the method signatures the same
  @Nullable
  public static BuckAddDependencyIntention create(PsiReference reference, PsiClass psiClass) {
    return BuckAddDependencyIntentionFactory.createAddModuleDependencyIntention(
        reference, psiClass);
  }

  @Nullable
  public static BuckAddDependencyIntention create(
      PsiReference reference, PsiClass psiClass, BuckAddImportAction importAction) {
    return BuckAddDependencyIntentionFactory.createAddModuleDependencyIntention(
        reference, psiClass, importAction);
  }

  @Nullable
  public static BuckAddDependencyIntention create(
      PsiReference reference,
      VirtualFile importSourceFile,
      @Nullable PsiClass psiClass,
      BuckAddImportAction importAction) {
    return BuckAddDependencyIntentionFactory.createAddModuleDependencyIntention(
        reference,
        importSourceFile,
        psiClass,
        importAction,
        new BuckUpdateModelModuleDependencyAction());
  }

  @Nullable
  public static BuckAddDependencyIntention create(
      PsiReference reference,
      VirtualFile importSourceFile,
      @Nullable PsiClass psiClass,
      BuckAddImportAction importAction,
      BuckUpdateModelAction updateModelAction) {
    return BuckAddDependencyIntentionFactory.createAddModuleDependencyIntention(
        reference, importSourceFile, psiClass, importAction, updateModelAction);
  }

  BuckAddDependencyIntention(
      CommonAddDependencyDataWrapper wrapper,
      VirtualFile importSourceFile,
      VirtualFile importBuildFile,
      Module importModule,
      BuckTarget importSourceTarget) {
    super(wrapper);
    this.importBuildFile = importBuildFile;
    this.importSourceFile = importSourceFile;
    this.importSourceTarget = importSourceTarget;
    this.importModule = importModule;
    String message = "Add BUCK dependency on owner(" + importSourceTarget + ")";
    setText(message);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile)
      throws IncorrectOperationException {
    String msg = "Invoked for project " + project.getName() + " and file " + psiFile.getName();
    LOGGER.info(msg);
    super.invoke(project, editor, psiFile);
  }

  /** Queries buck for targets that own the editSourceFile and the importSourceFile. */
  @Override
  protected void queryBuckForTargets(Editor editor, EventLogger buckEventLogger) {
    BuckTargetLocator buckTargetLocator = BuckTargetLocator.getInstance(project);
    String editPath = editSourceFile.getPath();
    String importPath = importSourceFile.getPath();
    BuckJsonCommandHandler<List<TargetMetadata>> handler =
        new BuckJsonCommandHandler<>(
            project,
            BuckCommand.QUERY,
            new Callback<List<TargetMetadata>>() {
              @Override
              public List<TargetMetadata> deserialize(JsonElement jsonElement) {
                return parseJson(jsonElement, buckTargetLocator);
              }

              @Override
              public void onSuccess(List<TargetMetadata> results, String stderr) {
                List<TargetMetadata> editTargets = new ArrayList<>();
                List<TargetMetadata> importTargets = new ArrayList<>();
                for (TargetMetadata targetMetadata : results) {
                  if (targetMetadata.contains(editSourceTarget)) {
                    editTargets.add(
                        TargetMetadataTransformer.transformEditedTarget(project, targetMetadata));
                  }
                  if (targetMetadata.contains(importSourceTarget)) {
                    importTargets.add(
                        TargetMetadataTransformer.transformImportedTarget(project, targetMetadata));
                  }
                }
                updateDependencies(editor, editTargets, importTargets, buckEventLogger);
              }

              @Override
              public void onFailure(
                  String stdout,
                  String stderr,
                  @Nullable Integer exitCode,
                  @Nullable Throwable throwable) {
                String message =
                    "Could not determine owners for "
                        + editSourceFile
                        + " and/or "
                        + importSourceFile;
                logFail(message, buckEventLogger);
                BuckNotification.getInstance(project).showWarningBalloon(message);
              }
            });
    handler
        .command()
        .addParameters(
            "owner(%s)",
            editPath, importPath, "--output-attributes=deps|srcs|visibility|resources");
    handler.runInCurrentThreadPostEnd(() -> {});
  }

  /**
   * Implementation of {@link
   * com.intellij.notification.NotificationListener#hyperlinkUpdate(Notification, HyperlinkEvent)}.
   */
  @Override
  protected void hyperlinkActivated(
      @NotNull Notification notification, @NotNull HyperlinkEvent event) {
    String href = event.getDescription();
    switch (href) {
      case "importBuildFile":
        FileEditorManager.getInstance(project).openFile(importBuildFile, true);
        break;
      case "importSourceFile":
        FileEditorManager.getInstance(project).openFile(importSourceFile, true);
        break;
      default:
        super.hyperlinkActivated(notification, event);
    }
  }

  private void updateDependencies(
      Editor editor,
      List<TargetMetadata> editTargets,
      List<TargetMetadata> importTargets,
      EventLogger buckEventLogger) {
    TargetMetadata editTargetMetadata =
        getTargetMetaDataFromList(
            editTargets,
            "<html><b>Add dependency failed</b>: Couldn't determine a Buck owner for <a href='editSourceFile'>"
                + editSourceTarget
                + "</a> in <a href='editBuildFile'>"
                + editBuildFile.getPath()
                + "</a>");
    if (editTargetMetadata == null) {
      logFail(
          "Could not determine Buck owner for edit source file " + editSourceTarget,
          buckEventLogger);
      return;
    }
    TargetMetadata importTargetMetadata =
        getTargetMetaDataFromList(
            importTargets,
            "<html><b>Add dependency failed</b>: Couldn't determine a Buck owner for <a href='importSourceFile'>"
                + importSourceTarget
                + "</a> in <a href='importBuildFile'>"
                + importBuildFile.getPath()
                + "</a></html>");
    if (importTargetMetadata == null) {
      logFail(
          "Could not determine Buck owner for import source file " + importSourceTarget,
          buckEventLogger);
      return;
    }
    editTarget = editTargetMetadata.target;
    importTarget = importTargetMetadata.target;

    if (!importTargetMetadata.isVisibleTo(editTarget)) {
      String message =
          "<html><b>Add dependency failed</b>: The target <a href='importTarget'>"
              + importTarget
              + "</a> is not visible to <a href='editTarget'>"
              + editTarget
              + "</a></html>";
      logFail(
          "Import target " + importTarget + " not visible to edit target " + editTarget,
          buckEventLogger);
      BuckNotification.getInstance(project).showErrorBalloon(message, this::hyperlinkActivated);
      return;
    }
    if (!tryToAddBuckDependency(editTargetMetadata, buckEventLogger)) {
      return;
    }

    // Update Module with the new dependency
    if (updateModelAction != null) {
      updateModelAction.updateModel(editModule, importModule, LOGGER);
    }

    buckEventLogger.withExtraData(getExtraLoggingData()).log();

    // Manually call add import action for the new dependency
    invokeAddImport(editor);
  }

  @Override
  Map<String, String> getExtraLoggingData() {
    Map<String, String> data = super.getExtraLoggingData();
    if (importModule != null) {
      data.put(Keys.MODULE, importModule.getName());
    }
    return data;
  }
}
