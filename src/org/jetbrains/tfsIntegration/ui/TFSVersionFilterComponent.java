package org.jetbrains.tfsIntegration.ui;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TFSVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {
  private JPanel myPanel;
  private JCheckBox myUseAuthorFilter;
  private JTextField myAuthorField;
  private JPanel myStandardPanel;

  public TFSVersionFilterComponent(final boolean showDateFilter) {
    super(showDateFilter);
    myStandardPanel.setLayout(new BorderLayout());
    myStandardPanel.add(getStandardPanel(), BorderLayout.CENTER);
    init(new ChangeBrowserSettings());
  }

  protected void updateAllEnabled(final ActionEvent e) {
    super.updateAllEnabled(e);
    updatePair(myUseAuthorFilter, myAuthorField, e);
  }

  protected void initValues(ChangeBrowserSettings settings) {
    super.initValues(settings);
    myUseAuthorFilter.setSelected(settings.USE_USER_FILTER);
    myAuthorField.setText(settings.USER);
  }

  public void saveValues(ChangeBrowserSettings settings) {
    super.saveValues(settings);
    settings.USER = myAuthorField.getText();
    settings.USE_USER_FILTER = myUseAuthorFilter.isSelected();
  }

  protected void installCheckBoxListener(final ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myUseAuthorFilter.addActionListener(filterListener);
    myAuthorField.addActionListener(filterListener);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @Nullable
  public String getAuthorFilter() {
    if (myUseAuthorFilter.isSelected() && myAuthorField.getText().length() > 0) {
      return myAuthorField.getText();
    }
    else {
      return null;
    }
  }

  @Override
  protected String getChangeNumberTitle() {
    return "Revision";
  }

  public JComponent getComponent() {
    return getPanel();
  }
}
