/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomNodeData;
import com.android.tools.idea.editors.gfxtrace.renderers.SchemaTreeRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;

public class AtomController implements PathListener {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final SimpleTree myTree;
  private DefaultMutableTreeNode myAtomTreeRoot;
  private AtomGroup myAtomGroup;
  private AtomList myAtomList;
  private final PathStore<AtomsPath> myAtomsPath = new PathStore<AtomsPath>();
  private boolean mDisableActivation = false;

  public AtomController(@NotNull GfxTraceEditor editor, @NotNull Project project, @NotNull JBScrollPane scrollPane) {
    myEditor = editor;
    myEditor.addPathListener(this);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    myTree = new SimpleTree();
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setLineStyleAngled();
    myTree.getEmptyText().setText(GfxTraceEditor.SELECT_CAPTURE);
    myTree.setCellRenderer(new SchemaTreeRenderer());
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), project);
    myLoadingPanel.add(myTree);
    scrollPane.setViewportView(myLoadingPanel);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        if (mDisableActivation || !myAtomsPath.isValid()) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node == null || node.getUserObject() == null) return;
        Object object = node.getUserObject();
        if (object instanceof AtomGroup) {
          myEditor.activatePath(myAtomsPath.getPath().index(((AtomGroup)object).getRange().getLast()));
        } else if (object instanceof AtomNodeData) {
          myEditor.activatePath(myAtomsPath.getPath().index(((AtomNodeData)object).index));
        }
      }
    });

  }

  @NotNull
  public static DefaultMutableTreeNode prepareData(@NotNull AtomGroup root, @NotNull AtomList atoms) {
    assert (!ApplicationManager.getApplication().isDispatchThread());
    return generateAtomTree(root, atoms);
  }

  @NotNull
  private static DefaultMutableTreeNode generateAtomTree(@NotNull AtomGroup atomGroup, @NotNull AtomList atoms) {
    assert (atomGroup.isValid());

    DefaultMutableTreeNode currentNode = new DefaultMutableTreeNode();
    currentNode.setUserObject(atomGroup);

    long lastGroupIndex = atomGroup.getRange().getStart();
    for (AtomGroup subGroup : atomGroup.getSubGroups()) {
      long subGroupFirst = subGroup.getRange().getStart();
      assert (subGroupFirst >= lastGroupIndex);
      if (subGroupFirst > lastGroupIndex) {
        addLeafNodes(currentNode, subGroupFirst, subGroupFirst - lastGroupIndex, atoms);
      }
      currentNode.add(generateAtomTree(subGroup, atoms));
      lastGroupIndex = subGroup.getRange().getEnd();
    }

    long nextSiblingStartIndex = atomGroup.getRange().getEnd();
    if (nextSiblingStartIndex > lastGroupIndex) {
      addLeafNodes(currentNode, lastGroupIndex, nextSiblingStartIndex - lastGroupIndex, atoms);
    }

    return currentNode;
  }

  private static void addLeafNodes(@NotNull DefaultMutableTreeNode parentNode, long start, long count, @NotNull AtomList atoms) {
    for (long i = 0, index = start; i < count; ++i, ++index) {
      parentNode.add(new DefaultMutableTreeNode(new AtomNodeData(index, atoms.get(i)), false));
    }
  }

  @NotNull
  public SimpleTree getTree() {
    return myTree;
  }

  public void populateUi(@NotNull DefaultMutableTreeNode root, @NotNull AtomList atoms) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myAtomTreeRoot = root;

    myTree.setModel(new DefaultTreeModel(myAtomTreeRoot));
    myTree.setLargeModel(true); // Set some performance optimizations for large models.
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT); // Make sure our rows are constant height.

    if (myAtomTreeRoot.getChildCount() == 0) {
      myTree.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    myLoadingPanel.stopLoading();
    myLoadingPanel.revalidate();
  }

  public void selectDeepestVisibleNode(long atomIndex) {
    selectDeepestVisibleNode(myAtomTreeRoot, new TreePath(myAtomTreeRoot), atomIndex);
  }

  public void selectDeepestVisibleNode(DefaultMutableTreeNode node, TreePath path, long atomIndex) {
    if (node.isLeaf() || !myTree.isExpanded(path)) {
      try {
        mDisableActivation = true;
        myTree.setSelectionPath(path);
        myTree.scrollPathToVisible(path);
        return;
      } finally {
        mDisableActivation = false;
      }
    }
    // Search through the list for now.
    for (Enumeration it = node.children(); it.hasMoreElements(); ) {
      Object obj = it.nextElement();
      assert (obj instanceof DefaultMutableTreeNode);
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)obj;
      Object object = child.getUserObject();
      boolean matches = false;
      if((object instanceof AtomGroup) &&
         (((AtomGroup)object).getRange().contains(atomIndex))) {
          matches = true;
      } else if((object instanceof AtomNodeData) &&
                ((((AtomNodeData)object).index == atomIndex))) {
        matches = true;
      }
      if (matches) {
        selectDeepestVisibleNode(child, path.pathByAddingChild(child), atomIndex);
      }
    }
  }

  public void clear() {
    myTree.setModel(null);
    myAtomTreeRoot = null;
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateAtoms = false;
    if (path instanceof CapturePath) {
      updateAtoms |= myAtomsPath.update(((CapturePath)path).atoms());
    }
    if (path instanceof AtomPath) {
      selectDeepestVisibleNode(((AtomPath)path).getIndex());
    }
    if (updateAtoms && myAtomsPath.isValid()) {
      myTree.getEmptyText().setText("");
      myLoadingPanel.startLoading();
      final ListenableFuture<AtomList> atomF = myEditor.getClient().get(myAtomsPath.getPath());
      final ListenableFuture<AtomGroup> hierarchyF = myEditor.getClient().get(myAtomsPath.getPath().getCapture().hierarchy());
      Futures.addCallback(Futures.allAsList(atomF, hierarchyF), new LoadingCallback<java.util.List<BinaryObject>>(LOG, myLoadingPanel) {
        @Override
        public void onSuccess(@Nullable final java.util.List<BinaryObject> all) {
          myLoadingPanel.stopLoading();
          final AtomList atoms = (AtomList)all.get(0);
          final AtomGroup group = (AtomGroup)all.get(1);
          final DefaultMutableTreeNode root = prepareData(group, atoms);
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              // Back in the UI thread here
              populateUi(root, atoms);
            }
          });
        }
      });
    }
  }
}