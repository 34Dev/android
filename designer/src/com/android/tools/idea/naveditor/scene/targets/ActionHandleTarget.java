/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.idea.naveditor.scene.draw.DrawActionHandle;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandleDrag;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawCommand;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * {@linkplain ActionHandleTarget} is a target for handling drag-creation of actions.
 * It appears as a circular grab handle on the right side of the navigation screen.
 */
public class ActionHandleTarget extends BaseTarget {
  private int myCurrentRadius = 0;
  private boolean myIsDragging = false;

  public ActionHandleTarget(@NotNull SceneComponent component) {
    setComponent(component);
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    myLeft = r - DrawActionHandle.LARGE_RADIUS;
    myTop = t + (b - t) / 2 - DrawActionHandle.LARGE_RADIUS;
    myRight = myLeft + 2 * DrawActionHandle.LARGE_RADIUS;
    myBottom = myTop + 2 * DrawActionHandle.LARGE_RADIUS;

    return false;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myIsDragging = true;
    myComponent.getScene().needsRebuildList();
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    myIsDragging = false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    DrawCommand drawCommand = myIsDragging
                              ? createDrawActionHandleDrag(sceneContext)
                              : createDrawActionHandle(sceneContext);

    list.add(drawCommand);
  }

  private DrawCommand createDrawActionHandleDrag(@NotNull SceneContext sceneContext) {
    return new DrawActionHandleDrag(sceneContext.getSwingX(getCenterX()), sceneContext.getSwingY(getCenterY()),
                                    sceneContext.getColorSet().getSelectedFrames());
  }

  private DrawCommand createDrawActionHandle(@NotNull SceneContext sceneContext) {
    int newRadius = 0;

    if (mIsOver) {
      newRadius = DrawActionHandle.LARGE_RADIUS;
    }
    else if (getComponent().getDrawState() == SceneComponent.DrawState.HOVER || getComponent().isSelected()) {
      newRadius = DrawActionHandle.SMALL_RADIUS;
    }

    ColorSet colorSet = sceneContext.getColorSet();
    Color borderColor = colorSet.getFrames();

    if (getComponent().isSelected()) {
      borderColor = colorSet.getSelectedFrames();
    }
    else if (getComponent().getDrawState() == SceneComponent.DrawState.HOVER) {
      borderColor = colorSet.getHighlightedFrames();
    }

    Color fillColor = colorSet.getBackground();

    DrawActionHandle drawCommand =
      new DrawActionHandle(sceneContext.getSwingX(getCenterX()), sceneContext.getSwingY(getCenterY()), myCurrentRadius, newRadius,
                           borderColor, fillColor);
    myCurrentRadius = newRadius;
    return drawCommand;
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    picker.addCircle(this, 0, transform.getSwingX(getCenterX()), transform.getSwingY(getCenterY()), DrawActionHandle.LARGE_RADIUS);
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }
}
