/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.JavaProjectTestCase

class VisualizationManagerTest : JavaProjectTestCase() {

  fun testNotRegisterWhenFlagIsDisable() {
    StudioFlags.NELE_VISUALIZATION.override(false)
    val manager = VisualizationManager(project)
    manager.initToolWindow()
    val window = ToolWindowManager.getInstance(myProject).getToolWindow(manager.toolWindowId)
    assertNull(window)
    StudioFlags.NELE_VISUALIZATION.clearOverride()
  }

  fun testRegisterWhenFlagIsEnable() {
    StudioFlags.NELE_VISUALIZATION.override(true)
    val manager = VisualizationManager(project)
    manager.initToolWindow()
    val window = ToolWindowManager.getInstance(myProject).getToolWindow(manager.toolWindowId)
    assertNotNull(window)
    StudioFlags.NELE_VISUALIZATION.clearOverride()
  }
}
