package ch.hsr.eclipse.cdt.ui.toggle;

import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTDeclarator;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTQualifiedName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateDeclaration;
import org.eclipse.cdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.internal.core.dom.parser.ASTNode;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTFunctionDefinition;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTSimpleDeclaration;
import org.eclipse.cdt.internal.ui.refactoring.CRefactoring;
import org.eclipse.cdt.internal.ui.refactoring.ModificationCollector;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.text.edits.TextEditGroup;

@SuppressWarnings("restriction")
public class ToggleRefactoring extends CRefactoring {

	private IASTFunctionDefinition selectedDefinition;
	private CPPASTFunctionDeclarator selectedDeclaration;
	private final TextSelection selection;
	private IASTNode parentInsertionPoint;

	public ToggleRefactoring(IFile file, ISelection selection, ICProject proj) {
		super(file, selection, null, proj);
		this.selection = (TextSelection) selection;
	}

	@Override
	protected RefactoringDescriptor getRefactoringDescriptor() {
		return new EmptyRefactoringDescription();
	}

	@Override
	protected void collectModifications(IProgressMonitor pm,
			ModificationCollector collector) throws CoreException {
		try {
			lockIndex();
			try {
				collectMoveChanges(collector);
			} finally {
				unlockIndex();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void collectMoveChanges(ModificationCollector collector) {
		selectedDeclaration = ToggleSelectionHelper.getSelectedDeclaration(unit, selection);
		selectedDefinition  = ToggleSelectionHelper.getSelectedDefinition(unit, selection);
		determinePosition();
		if (selectedDefinition.getDeclarator() == selectedDeclaration) {
			System.out.println("We're in the in-class situation.");
			IASTNode newfunc = getInHeaderDefinition();
			addDefinitionAppendModification(collector, newfunc);
		} else {
			System.out.println("We're in the in-header situation.");
			IASTFunctionDefinition newfunc = getInClassDefinition();
			addDeclarationReplaceModification(collector, newfunc);
		}

	}

	private void addDeclarationReplaceModification(
			ModificationCollector collector, IASTFunctionDefinition definition) {
		ASTRewrite rewrite = collector.rewriterForTranslationUnit(unit);
		TextEditGroup infoText = new TextEditGroup("Toggle");
		
		IASTNode toremove = (ASTNode) selectedDefinition; 
		if (toremove.getParent() != null && toremove.getParent() instanceof ICPPASTTemplateDeclaration)
			toremove = selectedDefinition.getParent();
		
		rewrite.remove(toremove, infoText);
		rewrite.replace(selectedDeclaration.getParent(), definition, infoText);
	}

	private IASTFunctionDefinition getInClassDefinition() {
		IASTDeclSpecifier newdeclspec = selectedDefinition.getDeclSpecifier().copy();
		newdeclspec.setInline(false);
		IASTFunctionDeclarator funcdecl = selectedDeclaration.copy();

		IASTStatement newbody = selectedDefinition.getBody().copy();
		IASTFunctionDefinition newfunc = new CPPASTFunctionDefinition(newdeclspec, funcdecl, newbody);
		// falsch, muss entsprechende klasse sein
		parentInsertionPoint = getParentInsertionPoint();
		newfunc.setParent(parentInsertionPoint);
		return newfunc;
	}

	private IASTNode getParentInsertionPoint() {
		IASTNode node = selectedDeclaration; 
		while (node.getParent() != null) {
			node = node.getParent();
			if (node instanceof ICPPASTCompositeTypeSpecifier) {
				ICPPASTCompositeTypeSpecifier type = (ICPPASTCompositeTypeSpecifier) node;
				System.out.println("Found class declaration: " + type.getName().getSimpleID().toString());
				return type;
			}
		}
		return unit;
	}

	private void determinePosition() {
		if (selectedDeclaration == null || selectedDefinition == null) {
			System.out.println("declaration AND definition needed. Cannot toggle. Stopping.");
			return;
		}
	}

	private IASTNode getInHeaderDefinition() {
		IASTDeclSpecifier newdeclspec = selectedDefinition.getDeclSpecifier().copy();
		newdeclspec.setInline(true);
		IASTFunctionDeclarator funcdecl = selectedDeclaration.copy();
		ICPPASTQualifiedName quali = ToggleSelectionHelper.getQualifiedName(selectedDefinition);
		funcdecl.setName(quali);
		for (IASTNode node : funcdecl.getChildren()) {
			System.out.println("nodeType: " + node.getClass());
			if (node instanceof ICPPASTParameterDeclaration) {
				ICPPASTParameterDeclaration param = (ICPPASTParameterDeclaration) node;
				ICPPASTDeclarator d = param.getDeclarator();
				System.out.println("hatswas? " + d.getInitializer());
				d.setInitializer(null);
			}
		}
		
		IASTStatement newbody = selectedDefinition.getBody().copy();
		IASTFunctionDefinition newfunc = new CPPASTFunctionDefinition(newdeclspec, funcdecl, newbody);
		
		IASTNode node = getTemplateDeclaration();
		if (node != null && node instanceof ICPPASTTemplateDeclaration) {
			ICPPASTTemplateDeclaration templdecl = (ICPPASTTemplateDeclaration) node; 
			templdecl.setParent(unit);
			templdecl.setDeclaration(newfunc);
			return templdecl;
		} else {
			newfunc.setParent(unit);
			return newfunc;
		}
	}

	private IASTNode getTemplateDeclaration() {
		IASTNode node = selectedDeclaration;
		while(node.getParent() != null) {
			node = node.getParent();
			if (node instanceof ICPPASTTemplateDeclaration)
				break;
		}
		return node.copy();
	}

	private void addDefinitionAppendModification(
			ModificationCollector collector, IASTNode newfunc) {
		ASTRewrite rewrite = collector.rewriterForTranslationUnit(unit);
		TextEditGroup infoText = new TextEditGroup("Toggle");
		IASTSimpleDeclaration declaration = createDeclarationFromDefinition();
		rewrite.replace(selectedDefinition, declaration, infoText);
		rewrite.insertBefore(unit, null, newfunc, infoText);
	}

	private IASTSimpleDeclaration createDeclarationFromDefinition() {
		IASTDeclarator declarator = selectedDefinition.getDeclarator().copy();
		IASTDeclSpecifier specifier = selectedDefinition.getDeclSpecifier().copy();
		IASTSimpleDeclaration result = new CPPASTSimpleDeclaration(specifier);
		result.addDeclarator(declarator);
		return result;
	}

}
