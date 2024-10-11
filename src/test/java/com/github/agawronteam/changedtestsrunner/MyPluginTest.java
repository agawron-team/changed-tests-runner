package com.github.agawronteam.changedtestsrunner;

import com.github.agawronteam.changedtestsrunner.Services.RunnerService;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.PsiErrorElementUtil;

@TestDataPath("$CONTENT_ROOT/src/test/testData")
public class MyPluginTest extends BasePlatformTestCase {

    public void testXMLFile() {
        var psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>");
        var xmlFile = assertInstanceOf(psiFile, XmlFile.class);

        assertFalse(PsiErrorElementUtil.hasErrors(getProject(), xmlFile.getVirtualFile()));

        assertNotNull(xmlFile.getRootTag());

        if (xmlFile.getRootTag() != null) {
            assertEquals("foo", xmlFile.getRootTag().getName());
            assertEquals("bar", xmlFile.getRootTag().getValue().getText());
        }
    }

    /*public void testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2");
    }*/

    public void testProjectService() {
        var projectService = getProject().getService(RunnerService.class);

        projectService.runRecentlyChangedTests(getProject());
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/rename";
    }
}