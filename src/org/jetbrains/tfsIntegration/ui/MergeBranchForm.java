/*
 * Copyright 2000-2008 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.tfsIntegration.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.EventDispatcher;
import com.microsoft.schemas.teamfoundation._2005._06.versioncontrol.clientservices._03.BranchRelative;
import com.microsoft.schemas.teamfoundation._2005._06.versioncontrol.clientservices._03.Changeset;
import com.microsoft.schemas.teamfoundation._2005._06.versioncontrol.clientservices._03.Item;
import com.microsoft.schemas.teamfoundation._2005._06.versioncontrol.clientservices._03.MergeCandidate;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.tfsIntegration.core.TFSBundle;
import org.jetbrains.tfsIntegration.core.tfs.WorkspaceInfo;
import org.jetbrains.tfsIntegration.core.tfs.version.ChangesetVersionSpec;
import org.jetbrains.tfsIntegration.core.tfs.version.LatestVersionSpec;
import org.jetbrains.tfsIntegration.core.tfs.version.VersionSpecBase;
import org.jetbrains.tfsIntegration.exceptions.TfsException;
import org.jetbrains.tfsIntegration.exceptions.UserCancelledException;
import org.jetbrains.tfsIntegration.ui.servertree.ServerBrowserDialog;
import org.jetbrains.tfsIntegration.ui.servertree.TfsTreeForm;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.List;

public class MergeBranchForm {

  public interface Listener extends EventListener {
    void stateChanged(boolean canFinish);
  }

  private enum ChangesType {
    ALL {
      public String toString() {
        return "All changes up to a specific version";
      }},

    SELECTED {
      public String toString() {
        return "Selected changesets";
      }
    }
  }

  private TextFieldWithBrowseButton.NoPathCompletion mySourceField;
  private JComboBox myTargetCombo;
  private JComboBox myChangesTypeCombo;
  private final SelectRevisionForm mySelectRevisionForm;
  private JPanel myContentPanel;
  private JPanel myChangesetsPanel;
  private JLabel mySourceBranchLabel;
  private final Project myProject;
  private final WorkspaceInfo myWorkspace;
  private final JTable myChangesetsTable;
  private final ChangesetsTableModel myChangesetsTableModel;
  private final String myDialogTitle;
  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);
  private boolean mySourceIsDirectory;
  private final FocusListener mySourceFieldFocusListener;

  public MergeBranchForm(final Project project,
                         final WorkspaceInfo workspace,
                         String initialSourcePath,
                         boolean initialSourcePathIsDirectory,
                         final String dialogTitle) {
    myProject = project;
    myWorkspace = workspace;
    myDialogTitle = dialogTitle;

    mySourceBranchLabel.setLabelFor(mySourceField.getChildComponent());

    myChangesetsTableModel = new ChangesetsTableModel();
    myChangesetsTable = new JBTable(myChangesetsTableModel);
    myChangesetsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    for (int i = 0; i < ChangesetsTableModel.Column.values().length; i++) {
      myChangesetsTable.getColumnModel().getColumn(i).setPreferredWidth(ChangesetsTableModel.Column.values()[i].getWidth());
    }

    mySelectRevisionForm = new SelectRevisionForm();

    myChangesetsPanel.add(mySelectRevisionForm.getPanel(), ChangesType.ALL.toString());
    myChangesetsPanel.add(ScrollPaneFactory.createScrollPane(myChangesetsTable), ChangesType.SELECTED.toString());

    mySourceField.setText(initialSourcePath);
    mySourceIsDirectory = initialSourcePathIsDirectory;

    mySourceField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        ServerBrowserDialog d =
          new ServerBrowserDialog(TFSBundle.message("choose.source.item.dialog.title"), project, workspace.getServer(),
                                  mySourceField.getText(), false, false);
        if (d.showAndGet()) {
          final TfsTreeForm.SelectedItem selectedItem = d.getSelectedItem();
          mySourceField.setText(selectedItem != null ? selectedItem.path : null);
          mySourceIsDirectory = selectedItem == null || selectedItem.isDirectory;
        }
        updateOnSourceChange();
      }
    });

    mySourceFieldFocusListener = new FocusAdapter() {
      @Override
      public void focusLost(final FocusEvent e) {
        mySourceIsDirectory = true;

        // TODO don't do it on focus out, rather provide a 'Refresh' button
        ApplicationManager.getApplication().invokeLater(() -> updateOnSourceChange(), ModalityState.current());
      }
    };
    mySourceField.getTextField().addFocusListener(mySourceFieldFocusListener);

    myTargetCombo.setModel(new DefaultComboBoxModel());
    myTargetCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value != null) {
          Item item = (Item)value;
          setText(item.getItem());
        }
        return c;
      }
    });

    myTargetCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (myChangesTypeCombo.getSelectedItem() == ChangesType.SELECTED) {
          updateChangesetsTable();
        }
      }
    });

    myChangesTypeCombo.setModel(new DefaultComboBoxModel(ChangesType.values()));

    myChangesTypeCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (myChangesTypeCombo.getSelectedItem() == ChangesType.SELECTED) {
          updateChangesetsTable();
        }
        ((CardLayout)myChangesetsPanel.getLayout()).show(myChangesetsPanel, myChangesTypeCombo.getSelectedItem().toString());
        fireStateChanged();
      }
    });

    myChangesetsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        fireStateChanged();
      }
    });

    myChangesTypeCombo.setSelectedIndex(0);
    mySelectRevisionForm.init(project, workspace, initialSourcePath, initialSourcePathIsDirectory);
  }

  public JComponent getContentPanel() {
    return myContentPanel;
  }

  private void updateChangesetsTable() {
    List<Changeset> changesets = new ArrayList<>();
    if (myTargetCombo.getSelectedIndex() != -1) {
      try {
        final Collection<MergeCandidate> mergeCandidates = myWorkspace.getServer().getVCS()
          .queryMergeCandidates(myWorkspace.getName(), myWorkspace.getOwnerName(), mySourceField.getText(), getTargetPath(), myProject,
                                TFSBundle.message("loading.branches"));
        for (MergeCandidate candidate : mergeCandidates) {
          changesets.add(candidate.getChangeset());
        }
      }
      catch (TfsException e) {
        Messages.showErrorDialog(myProject, e.getMessage(), myDialogTitle);
      }
    }
    myChangesetsTableModel.setChangesets(changesets);
  }

  public String getSourcePath() {
    return mySourceField.getText();
  }

  public String getTargetPath() {
    Item targetBranch = (Item)myTargetCombo.getSelectedItem();
    return targetBranch.getItem();
  }

  @Nullable
  public VersionSpecBase getFromVersion() {
    ChangesType changesType = (ChangesType)myChangesTypeCombo.getSelectedItem();
    if (changesType == ChangesType.SELECTED) {
      final Changeset fromChangeset =
        myChangesetsTableModel.getChangesets().get(myChangesetsTable.getSelectionModel().getMinSelectionIndex());
      return new ChangesetVersionSpec(fromChangeset.getCset());
    }
    else {
      return null;
    }
  }

  @Nullable
  public VersionSpecBase getToVersion() {
    ChangesType changesType = (ChangesType)myChangesTypeCombo.getSelectedItem();
    if (changesType == ChangesType.SELECTED) {
      final Changeset toChangeset =
        myChangesetsTableModel.getChangesets().get(myChangesetsTable.getSelectionModel().getMaxSelectionIndex());
      return new ChangesetVersionSpec(toChangeset.getCset());
    }
    else {
      return mySelectRevisionForm.getVersionSpec();
    }
  }

  public void addListener(Listener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    myEventDispatcher.removeListener(listener);
  }

  private void fireStateChanged() {
    myEventDispatcher.getMulticaster().stateChanged(canFinish());
  }

  private boolean canFinish() {
    ChangesType changesType = (ChangesType)myChangesTypeCombo.getSelectedItem();
    if (changesType == ChangesType.SELECTED) {
      if (myChangesetsTable.getSelectedRowCount() == 0) {
        return false;
      }
    }

    if (myTargetCombo.getSelectedIndex() == -1) {
      return false;
    }

    return true;
  }

  private void updateOnSourceChange() {
    final Collection<Item> targetBranches = new ArrayList<>();
    try {
      final Collection<BranchRelative> allBranches =
        myWorkspace.getServer().getVCS()
          .queryBranches(mySourceField.getText(), LatestVersionSpec.INSTANCE, myProject, TFSBundle.message("loading.branches"));

      BranchRelative subject = null;
      for (BranchRelative branch : allBranches) {
        if (branch.getReqstd()) {
          subject = branch;
          break;
        }
      }

      for (BranchRelative branch : allBranches) {
        if ((branch.getRelfromid() == subject.getReltoid() || branch.getReltoid() == subject.getRelfromid()) &&
            branch.getBranchToItem().getDid() == Integer.MIN_VALUE) {
          targetBranches.add(branch.getBranchToItem());
        }
      }
    }
    catch (UserCancelledException e) {
      return;
    }
    catch (TfsException e) {
      Messages.showErrorDialog(myProject, e.getMessage(), myDialogTitle);
    }

    ((DefaultComboBoxModel)myTargetCombo.getModel()).removeAllElements();
    for (Item targetBranch : targetBranches) {
      ((DefaultComboBoxModel)myTargetCombo.getModel()).addElement(targetBranch);
    }
    mySelectRevisionForm.init(myProject, myWorkspace, mySourceField.getText(), mySourceIsDirectory);
    fireStateChanged();
  }

  public void close() {
    mySourceField.getTextField().removeFocusListener(mySourceFieldFocusListener);
  }

  public JComponent getPreferredFocusedComponent() {
    return mySourceField.getChildComponent();
  }

}
