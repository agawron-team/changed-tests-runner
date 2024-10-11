package com.github.agawronteam.changedtestsrunner;

import com.github.agawronteam.changedtestsrunner.ResultsWindow.ResultsWindowFactory;
import com.github.agawronteam.changedtestsrunner.Services.RunnerServiceImpl;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RunnerServiceImplTest {

    @Mock
    RunManager runManager;
    @Mock
    ChangeListManager changeListManager;

    class RunnerServiceImplTestable extends RunnerServiceImpl {
        @Override
        protected RunManager getRunManagerInstance(Project project) {
            return runManager;
        }

        @Override
        protected ChangeListManager getChangeListManagerInstance(Project project) {
            return changeListManager;
        }
    }

    @Mock
    Project project;
    @Mock
    ResultsWindowFactory.TestResultsWindow testResultsWindow;

    RunnerServiceImpl runnerService = new RunnerServiceImplTestable();

    /*@Test
    public void shouldUpdateResultWindowWithNoTestsIfNoChangedFiles() {
        runnerService.registerResultsWindow(testResultsWindow);
        runnerService.runRecentlyChangedTests(project);

        verify(testResultsWindow, times(1)).reset("No tests to run");
    }*/
}
