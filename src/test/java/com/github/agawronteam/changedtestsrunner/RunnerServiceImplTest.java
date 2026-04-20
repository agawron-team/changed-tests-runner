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
import static org.mockito.Mockito.mock;
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
            // Return a non-null mock so launchJob() is called (not the null/failed branch)
            return mock(ExecutionEnvironmentBuilder.class);
        }

        @Override
        protected void launchJob(Project project, ExecutionEnvironmentBuilder builder) {
            // Do nothing — simulate a job that has been launched but not yet terminated.
            // The job remains active (testJobsActive = true) until a processTerminated
            // event arrives, which in unit tests never fires. This mirrors the real
            // behaviour where isRunningTests() returns true while the process runs.
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
    // Stop behaviour
    // -------------------------------------------------------------------------

    @Test
    public void stopTests_whileRunning_cancelsRemainingQueuedTests() {
        // Arrange: set up two changed test files so two jobs are discovered
        VirtualFile secondFile = mock(VirtualFile.class);
        FileType secondFileType = mock(FileType.class);
        PsiJavaFile secondPsiFile = mock(PsiJavaFile.class);
        PsiClass secondPsiClass = mock(PsiClass.class);
        PsiMethod secondPsiMethod = mock(PsiMethod.class);
        RunnerAndConfigurationSettings secondConfigSettings = mock(RunnerAndConfigurationSettings.class);
        JUnitConfiguration secondJUnitConfiguration = mock(JUnitConfiguration.class);

        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile, secondFile));

        // First file
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("JAVA");
        when(psiManager.findFile(uncommitedFile)).thenReturn(psiFile);
        when(psiFile.getClasses()).thenReturn(new PsiClass[]{psiClass});
        when(psiClass.getAllMethods()).thenReturn(new PsiMethod[]{psiMethod});
        when(psiClass.getName()).thenReturn("FirstTest");
        when(psiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);

        // Second file
        when(secondFile.getFileType()).thenReturn(secondFileType);
        when(secondFileType.getName()).thenReturn("JAVA");
        when(psiManager.findFile(secondFile)).thenReturn(secondPsiFile);
        when(secondPsiFile.getClasses()).thenReturn(new PsiClass[]{secondPsiClass});
        when(secondPsiClass.getAllMethods()).thenReturn(new PsiMethod[]{secondPsiMethod});
        when(secondPsiClass.getName()).thenReturn("SecondTest");
        when(secondPsiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);

        // Shared infrastructure
        when(jUnitConfigurationType.getConfigurationFactories()).thenReturn(new ConfigurationFactory[]{configurationFactory});
        when(runManager.createConfiguration("FirstTest", configurationFactory)).thenReturn(configurationSettings);
        when(configurationSettings.getConfiguration()).thenReturn(jUnitConfiguration);
        when(configurationSettings.getUniqueID()).thenReturn("uniqueId1");
        when(jUnitConfiguration.getModules()).thenReturn(new Module[]{module});
        when(runManager.createConfiguration("SecondTest", configurationFactory)).thenReturn(secondConfigSettings);
        when(secondConfigSettings.getConfiguration()).thenReturn(secondJUnitConfiguration);
        when(secondConfigSettings.getUniqueID()).thenReturn("uniqueId2");
        when(secondJUnitConfiguration.getModules()).thenReturn(new Module[]{module});
        when(project.getMessageBus()).thenReturn(messageBus);
        when(messageBus.connect()).thenReturn(messageBusConnection);

        // Act: start running (first job launches, second stays pending)
        runnerService.runRecentlyChangedTests(project);

        // Both jobs registered, first is active, second is still pending
        assertTrue(runnerService.isRunningTests());

        // Stop — drains pending queue and signals stop; the active first job stays "running"
        // until its processTerminated fires (which doesn't happen in unit tests).
        runnerService.stopTests();

        // The pending (second) job must have been immediately marked CANCELLED in the UI
        verify(testResultsWindow, times(1)).updateTest(
                RunnerServiceImpl.getUUID("uniqueId2"), ResultsWindowFactory.TestStatus.CANCELLED);

        // The pending queue is now empty — no more tests will be launched after stop
        // (isStopping flag is set, launchNextPending will bail out)
        assertTrue(runnerService.isStopping());
    }

    @Test
    public void stopTests_beforeAnyTestLaunched_resetsState() {
        // Arrange: no changed files → nothing to run
        runnerService.registerResultsWindow(testResultsWindow);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of());

        // Not running yet
        assertFalse(runnerService.isRunningTests());

        // Calling stopTests() when nothing is running should be a no-op (no crash)
        runnerService.stopTests();

        assertFalse(runnerService.isRunningTests());
    }

    @Test
    public void stopTests_preventsNewTestsFromStarting() {
        // Arrange: one test found and launched (but not yet terminated in tests)
        setupJUnitInfrastructure();
        when(psiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);
        assertTrue(runnerService.isRunningTests());

        // Stop — sets isStopping=true; the single active job stays "running" in the map
        // until processTerminated fires (doesn't happen in unit tests), but no new jobs
        // can be launched because launchNextPending() checks isStopping first.
        runnerService.stopTests();

        // isStopping flag is set, so launchNextPending will refuse to start anything new
        assertTrue(runnerService.isStopping());
    }

    // -------------------------------------------------------------------------
    // Detect affected tests - flag behaviour
    // -------------------------------------------------------------------------

    @Test
    public void detectAffectedTests_defaultIsOff() {
        assertFalse(runnerService.isDetectAffectedTests());
    }

    @Test
    public void detectAffectedTests_canBeEnabled() {
        runnerService.setDetectAffectedTests(true);
        assertTrue(runnerService.isDetectAffectedTests());
    }

    @Test
    public void detectAffectedTests_canBeDisabledAgain() {
        runnerService.setDetectAffectedTests(true);
        runnerService.setDetectAffectedTests(false);
        assertFalse(runnerService.isDetectAffectedTests());
    }

    @Test
    public void detectAffectedTests_whenOffAndChangedFileIsProductionCode_doesNotSearchForReferences() {
        // Arrange: changed file is NOT a test class (no @Test annotations on methods or class)
        runnerService.registerResultsWindow(testResultsWindow);
        runnerService.setDetectAffectedTests(false);
        when(changeListManager.getAffectedFiles()).thenReturn(List.of(uncommitedFile));
        when(uncommitedFile.getFileType()).thenReturn(fileType);
        when(fileType.getName()).thenReturn("JAVA");
        when(psiManager.findFile(uncommitedFile)).thenReturn(psiFile);
        when(psiFile.getClasses()).thenReturn(new PsiClass[]{psiClass});
        when(psiClass.getAllMethods()).thenReturn(new PsiMethod[]{psiMethod});
        when(psiMethod.hasAnnotation(any())).thenReturn(false);
        when(psiClass.hasAnnotation(any())).thenReturn(false);

        runnerService.runRecentlyChangedTests(project);

        // No tests found, no run configured
        verify(testResultsWindow, times(1)).reset("No tests to run");
        verify(jUnitConfiguration, never()).setMainClass(any());
    }

    @Test
    public void detectAffectedTests_whenOffChangedTestClassIsStillRun() {
        // Even when detectAffectedTests is off, directly changed test files are still run
        setupJUnitInfrastructure();
        runnerService.setDetectAffectedTests(false);
        when(psiMethod.hasAnnotation("org.junit.Test")).thenReturn(true);

        runnerService.runRecentlyChangedTests(project);

        assertTrue(runnerService.isRunningTests());
        verify(jUnitConfiguration, times(1)).setMainClass(psiClass);
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
