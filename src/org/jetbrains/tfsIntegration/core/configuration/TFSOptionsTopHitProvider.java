package org.jetbrains.tfsIntegration.core.configuration;

import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * @author Sergey.Malenkov
 */
final class TFSOptionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @NotNull
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
      if ("TFS".equals(descriptor.getName())) {
        return Collections.unmodifiableCollection(Arrays.<BooleanOptionDescription>asList(
          new Option("TFS: Use HTTP Proxy settings", null, "useIdeaHttpProxy", "setUseIdeaHttpProxy"),
          new Option("TFS: Evaluate Team Explorer policies", "teamExplorer", null, "setSupportTfsCheckinPolicies"),
          new Option("TFS: Evaluate Teamprise policies", "teamprise", null, "setSupportStatefulCheckinPolicies"),
          new Option("TFS: Warn about not installed policies", "nonInstalled", null, "setReportNotInstalledCheckinPolicies")));
      }
    }
    return Collections.emptyList();
  }

  private static final class Option extends PublicMethodBasedOptionDescription {
    private final String myField;

    Option(String option, String field, String getterName, String setterName) {
      super(option, "vcs.TFS", getterName, setterName, () -> "");
      myField = field;
    }

    @Override
    public @NotNull TFSConfigurationManager getInstance() {
      return TFSConfigurationManager.getInstance();
    }

    @Override
    public boolean isOptionEnabled() {
      if (myField == null) {
        return super.isOptionEnabled();
      }
      try {
        Object instance = getInstance().getCheckinPoliciesCompatibility();
        final Field field = instance.getClass().getField(myField);
        return field.getBoolean(instance);
      }
      catch (NoSuchFieldException | IllegalAccessException ignore) {
      }
      return false;
    }
  }
}
