package com.github.agawronteam.changedtestsrunner;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.github.agawronteam.changedtestsrunner.Services.RunnerServiceImpl;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class RunnerServiceImplTest {

    @Mock RunManager runManager;
    @Mock ChangeListManager changeListManager;
    @Mock PsiManager psiManager;
    @Mock VirtualFile uncommitedFile;
    @Mock FileType fileType;
    @Mock PsiJavaFile psiFile;
    @Mock PsiClass psiClass;
    @Mock PsiMethod psiMethod;
    @Mock JUnitConfigurationType jUnitConfigurationType;
    @Mock ConfigurationFactory configurationFactory;
    @Mock RunnerAndConfigurationSettings configurationSettings;
    @Mock JUnitConfiguration jUnitConfiguration;
    @Mock Module module;
    @Mock MessageBus messageBus;
    @Mock MessageBusConnection messageBusConnection;
    @Mock Project project;
    @Mock ResultsWindowFactory.TestResultsWindow testResultsWindow;

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

    RunnerServiceImpl runnerService = new RunnerServiceImplTestable();

    // -------------------------------------------------------------------------
    // Basic flow tests
    // -------------------------------------------------------------------------

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

        assertFalse(runnerService.isRunningTests());
        verify(changeListManager, times(1)).getAffectedFiles();
        verify(testResultsWindow, times(1)).reset("No tests to run");
    }

    @Test
    public void shouldSkipNonJavaKotlinFiles() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("XML");

        runnerService.runRecentlyChangedTests(project);

        verify(testResultsWindow, times(1)).reset("No tests to run");
        verify(psiManager, never()).findFile(any());
    }

    // -------------------------------------------------------------------------
    // JUnit 4 detection tests
    // -------------------------------------------------------------------------

    @Test
    public void shouldRunChangedTests_junit4() {
        setupJUnitInfrastructure();
        // JUnit 4: method has @org.junit.Test annotation
        when(psiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
        verify(testResultsWindow, times(1)).reset("Tests results");
    }

    @Test
    public void shouldNotRunClassWithNoTestAnnotations() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("JAVA");
        when(psiManager.findFile(uncommitedFile)).thenReturn(psiFile);
        when(psiFile.getClasses()).thenReturn(new PsiClass[]{psiClass});
        when(psiClass.getAllMethods()).thenReturn(new PsiMethod[]{psiMethod});
        // No annotations on method or class
        when(psiMethod.hasAnnotation(any())).thenReturn(false);
        when(psiClass.hasAnnotation(any())).thenReturn(false);

        runnerService.runRecentlyChangedTests(project);

        verify(testResultsWindow, times(1)).reset("No tests to run");
        verify(jUnitConfiguration, never()).setMainClass(any());
    }

    // -------------------------------------------------------------------------
    // JUnit 5 detection tests
    // -------------------------------------------------------------------------

    @Test
    public void shouldRunChangedTests_junit5Test() {
        setupJUnitInfrastructure();
        // JUnit 5: method has @org.junit.jupiter.api.Test
        when(psiMethod.hasAnnotation("org.junit.jupiter.api.Test")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
    }

    @Test
    public void shouldRunChangedTests_junit5ParameterizedTest() {
        setupJUnitInfrastructure();
        when(psiMethod.hasAnnotation("org.junit.jupiter.params.ParameterizedTest")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
    }

    @Test
    public void shouldRunChangedTests_junit5RepeatedTest() {
        setupJUnitInfrastructure();
        when(psiMethod.hasAnnotation("org.junit.jupiter.api.RepeatedTest")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
    }

    // -------------------------------------------------------------------------
    // Class-level annotation tests (@RunWith / @ExtendWith)
    // -------------------------------------------------------------------------

    @Test
    public void shouldRunClassAnnotatedWithRunWith() {
        setupJUnitInfrastructure();
        // No method annotations, but class has @RunWith
        when(psiMethod.hasAnnotation(any())).thenReturn(false);
        when(psiClass.hasAnnotation("org.junit.runner.RunWith")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
    }

    @Test
    public void shouldRunClassAnnotatedWithExtendWith() {
        setupJUnitInfrastructure();
        when(psiMethod.hasAnnotation(any())).thenReturn(false);
        when(psiClass.hasAnnotation("org.junit.jupiter.api.extension.ExtendWith")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
    }

    // -------------------------------------------------------------------------
    // Kotlin file support
    // -------------------------------------------------------------------------

    @Test
    public void shouldProcessKotlinFiles() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("kotlin");
        when(psiManager.findFile(uncommitedFile)).thenReturn(null); // skip class resolution, just verify file is processed

        runnerService.runRecentlyChangedTests(project);

        // File was passed through the filter and findFile was called (Kotlin file was not filtered out)
        verify(psiManager, times(1)).findFile(uncommitedFile);
    }

    @Test
    public void shouldProcessKotlinFilesWithMixedCase() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("KoTlIn");
        when(psiManager.findFile(uncommitedFile)).thenReturn(null);

        runnerService.runRecentlyChangedTests(project);

        verify(psiManager, times(1)).findFile(uncommitedFile);
    }

    // -------------------------------------------------------------------------
    // Safe module access (no ArrayIndexOutOfBoundsException)
    // -------------------------------------------------------------------------

    @Test
    public void shouldNotCrashWhenModulesArrayIsEmpty() {
        setupJUnitInfrastructure();
        when(psiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);
        // Return empty modules array instead of one with a module
        when(jUnitConfiguration.getModules()).thenReturn(new Module[0]);

        // Should not throw ArrayIndexOutOfBoundsException
        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
    }

    @Test
    public void shouldNotCrashWhenModulesIsNull() {
        setupJUnitInfrastructure();
        when(psiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);
        when(jUnitConfiguration.getModules()).thenReturn(null);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Sets up the common mock chain for a Java file with one class and one method. */
    private void setupJUnitInfrastructure() {
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("JAVA");
        when(psiManager.findFile(uncommitedFile)).thenReturn(psiFile);
        when(psiFile.getClasses()).thenReturn(new PsiClass[]{psiClass});
        when(psiClass.getAllMethods()).thenReturn(new PsiMethod[]{psiMethod});
        when(psiClass.getName()).thenReturn("ClassName");
        when(jUnitConfigurationType.getConfigurationFactories()).thenReturn(new ConfigurationFactory[]{configurationFactory});
        when(runManager.createConfiguration(anyString(), any(ConfigurationFactory.class))).thenReturn(configurationSettings);
        when(configurationSettings.getConfiguration()).thenReturn(jUnitConfiguration);
        when(configurationSettings.getUniqueID()).thenReturn("uniqueId");
        when(jUnitConfiguration.getModules()).thenReturn(new Module[]{module});
        when(project.getMessageBus()).thenReturn(messageBus);
        when(messageBus.connect()).thenReturn(messageBusConnection);
    }
}
