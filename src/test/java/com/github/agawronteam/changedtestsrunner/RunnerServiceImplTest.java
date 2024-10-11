package com.github.agawronteam.changedtestsrunner;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.github.agawronteam.changedtestsrunner.Services.RunnerServiceImpl;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.gradle.api.tasks.Exec;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RunnerServiceImplTest {

    @Mock
    RunManager runManager;
    @Mock
    ChangeListManager changeListManager;
    @Mock
    PsiManager psiManager;
    @Mock
    VirtualFile uncommitedFile;
    @Mock
    FileType fileType;
    @Mock
    PsiJavaFile psiFile;
    @Mock
    PsiClass psiClass;
    @Mock
    PsiMethod psiMethod;
    @Mock
    PsiModifierList modifierList;
    @Mock
    JUnitConfigurationType jUnitConfigurationType;
    @Mock
    ConfigurationFactory configurationFactory;
    @Mock
    RunnerAndConfigurationSettings configurationSettings;
    @Mock
    JUnitConfiguration jUnitConfiguration;
    @Mock
    ExecutionEnvironmentBuilder executionEnvironmentBuilder;
    @Mock
    Module module;
    @Mock
    MessageBus messageBus;
    @Mock
    MessageBusConnection messageBusConnection;

    class RunnerServiceImplTestable extends RunnerServiceImpl {
        @Override
        protected RunManager getRunManagerInstance(Project project) {
            return runManager;
        }

        @Override
        protected ChangeListManager getChangeListManagerInstance(Project project) {
            return changeListManager;
        }

        @Override
        public PsiManager getPsiManagerInstance(Project project) {
            return psiManager;
        }

        @Override
        public JUnitConfigurationType getJUnitConfigurationTypeInstance() {
            return jUnitConfigurationType;
        }

        @Override
        public ExecutionEnvironmentBuilder getExecutionEnvironmentBuilder(RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
            return null;
        }
    }

    @Mock
    Project project;
    @Mock
    ResultsWindowFactory.TestResultsWindow testResultsWindow;

    RunnerServiceImpl runnerService = new RunnerServiceImplTestable();

    @Test
    public void shouldUpdateResultWindowWithNoTestsIfNoChangedFiles() {
        runnerService.registerResultsWindow(testResultsWindow);

        runnerService.runRecentlyChangedTests(project);

        verify(testResultsWindow, times(1)).reset("No tests to run");
    }

    @Test
    public void shouldSkipFileIfCantFindPsiFile() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("jAvA");
        when(psiManager.findFile(uncommitedFile)).thenReturn(null);

        runnerService.runRecentlyChangedTests(project);

        assertThat(runnerService.isRunningTests()).isFalse();
        verify(changeListManager, times(1)).getAffectedFiles();
    }

    @Test
    public void shouldRunChangedTests() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("jAvA");
        when(psiManager.findFile(uncommitedFile)).thenReturn(psiFile);
        when(psiFile.getClasses()).thenReturn(List.of(psiClass).toArray(new PsiClass[1]));
        when(psiClass.getAllMethods()).thenReturn(List.of(psiMethod).toArray(new PsiMethod[1]));
        when(psiMethod.getModifierList()).thenReturn(modifierList);
        when(modifierList.toString()).thenReturn("@Test public");
        when(psiClass.getName()).thenReturn("ClassName");
        when(jUnitConfigurationType.getConfigurationFactories()).thenReturn(List.of(configurationFactory).toArray(new ConfigurationFactory[1]));
        when(runManager.createConfiguration(anyString(), any(com.intellij.execution.configurations.ConfigurationFactory.class))).thenReturn(configurationSettings);
        when(configurationSettings.getConfiguration()).thenReturn(jUnitConfiguration);
        when(configurationSettings.getUniqueID()).thenReturn("uniqueId");
        when(jUnitConfiguration.getModules()).thenReturn(List.of(module).toArray(new Module[1]));
        when(project.getMessageBus()).thenReturn(messageBus);
        when(messageBus.connect()).thenReturn(messageBusConnection);

        runnerService.runRecentlyChangedTests(project);

        assertThat(runnerService.isRunningTests()).isTrue();
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
        verify(changeListManager, times(1)).getAffectedFiles();
        verify(testResultsWindow, times(1)).reset("Tests results");
    }
}
