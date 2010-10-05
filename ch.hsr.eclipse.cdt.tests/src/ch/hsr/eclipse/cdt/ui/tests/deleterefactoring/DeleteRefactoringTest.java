package ch.hsr.eclipse.cdt.ui.tests.deleterefactoring;

import java.util.Properties;
import java.util.Vector;

import org.eclipse.cdt.ui.tests.refactoring.RefactoringTest;
import org.eclipse.cdt.ui.tests.refactoring.TestSourceFile;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import ch.hsr.eclipse.cdt.ui.deltoggle.DelToggleRefactoring;

public class DeleteRefactoringTest extends RefactoringTest {

	public DeleteRefactoringTest(String name, Vector<TestSourceFile> files) {
		super(name, files);
	}

	protected void setUp() throws Exception {
		super.setUp();
	}

	@Override
	protected void configureRefactoring(Properties refactoringProperties) {
	}

	@Override
	protected void runTest() throws Throwable {
		Refactoring refactoring = new DelToggleRefactoring(project.getFile(fileName), selection, cproject);
		RefactoringStatus preconditions = refactoring.checkInitialConditions(NULL_PROGRESS_MONITOR);
		assertFalse(preconditions.hasError());
		compareFiles(fileMap);
	}

}
