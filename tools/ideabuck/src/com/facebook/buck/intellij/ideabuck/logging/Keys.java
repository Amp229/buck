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

package com.facebook.buck.intellij.ideabuck.logging;

public class Keys {

  private Keys() {}
  // Event types
  public static String MENU_ITEM = "menu.item";
  public static String FILE_QUICKFIX = "file.quickfix";

  // Extra data keys
  public static String BUCK_FILE = "buck_file";
  public static String ERROR = "error_message";
  public static String TARGET_TO_ADD = "target_to_add";
  public static String TARGET = "target";
  public static String CLASS_NAME = "class_name";
  public static String EDIT_TARGET = "edit_target";
  public static String IMPORT_TARGET = "import_target";
  public static String MODULE = "module";
  public static String LIBRARY = "library";
}
