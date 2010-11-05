package ch.hsr.eclipse.cdt.ui.toggle;

import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateDeclaration;
import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class ToggleStrategyFactory {
	
	private ToggleRefactoringContext context;

	public ToggleStrategyFactory(ToggleRefactoringContext context) {
		this.context = context;
	}
	
	public ToggleRefactoringAbstractStrategy getAppropriatedStategy(RefactoringStatus initStatus) {
		if (isInClassSituation())
			return new ToggleFromClassToInHeaderStrategy(context);
		else if (isTemplateSituation())
			return new ToggleFromInHeaderToClassStrategy(context);
		else if (isinHeaderSituation()) {
			try {
				return new ToggleFromInHeaderToImplementationStragegy(context);
			} catch (CModelException e) {
				initStatus.addFatalError(e.getMessage());
			} catch (CoreException e) {
				initStatus.addFatalError(e.getMessage());
			}
		} else if (isInImplementationSituation()) 	
			return new ToggleFromImplementationToClassStragegy(context);
		return null;
	}
	
	private boolean isinHeaderSituation() {
		return context.getDeclaration().getFileLocation().getFileName().equals(context.getDefinition().getFileLocation().getFileName());
	}
	
	private boolean isInClassSituation() {
		return context.getDefinition().getDeclarator() == context.getDeclaration() && 
		context.getDeclaration().getFileLocation().getFileName().equals(context.getDefinition().getFileLocation().getFileName());
	}

	private boolean isTemplateSituation() {
		IASTNode node = context.getDefinition();
		while(node.getParent() != null) {
			node = node.getParent();
			if (node instanceof ICPPASTTemplateDeclaration)
				return true;
		}
		return false;
	}

	private boolean isInImplementationSituation() {
		String extension1 = getFileExtension(context.getDeclaration().getFileLocation().getFileName());
		String extension2 = getFileExtension(context.getDefinition().getFileLocation().getFileName());
		if (extension1.equals("h") && extension2.equals("cpp"))
			return true;
		return false;
	}
	
	private String getFileExtension(String fileName) {
		return fileName.replaceAll("(.)*\\.", "");
	}
}
