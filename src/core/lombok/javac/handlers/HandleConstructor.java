/*
 * Copyright © 2010 Reinier Zwitserloot, Roel Spilker and Robbert Jan Grootjans.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac.handlers;

import static lombok.javac.handlers.JavacHandlerUtil.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.core.AnnotationValues;
import lombok.core.AST.Kind;
import lombok.core.handlers.TransformationsUtil;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil.MemberExistsResult;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

public class HandleConstructor {
	@ProviderFor(JavacAnnotationHandler.class)
	public static class HandleNoArgsConstructor implements JavacAnnotationHandler<NoArgsConstructor> {
		@Override public boolean handle(AnnotationValues<NoArgsConstructor> annotation, JCAnnotation ast, JavacNode annotationNode) {
			markAnnotationAsProcessed(annotationNode, NoArgsConstructor.class);
			deleteImportFromCompilationUnit(annotationNode, "lombok.AccessLevel");
			JavacNode typeNode = annotationNode.up();
			NoArgsConstructor ann = annotation.getInstance();
			AccessLevel level = ann.access();
			String staticName = ann.staticName();
			if (level == AccessLevel.NONE) return true;
			List<JavacNode> fields = List.nil();
			new HandleConstructor().generateConstructor(typeNode, level, fields, staticName, false, false);
			return true;
		}
		
		@Override public boolean isResolutionBased() {
			return false;
		}
	}
	
	@ProviderFor(JavacAnnotationHandler.class)
	public static class HandleRequiredArgsConstructor implements JavacAnnotationHandler<RequiredArgsConstructor> {
		@Override public boolean handle(AnnotationValues<RequiredArgsConstructor> annotation, JCAnnotation ast, JavacNode annotationNode) {
			markAnnotationAsProcessed(annotationNode, RequiredArgsConstructor.class);
			deleteImportFromCompilationUnit(annotationNode, "lombok.AccessLevel");
			JavacNode typeNode = annotationNode.up();
			RequiredArgsConstructor ann = annotation.getInstance();
			AccessLevel level = ann.access();
			String staticName = ann.staticName();
			@SuppressWarnings("deprecation")
			boolean suppressConstructorProperties = ann.suppressConstructorProperties();
			if (level == AccessLevel.NONE) return true;
			new HandleConstructor().generateConstructor(typeNode, level, findRequiredFields(typeNode), staticName, false, suppressConstructorProperties);
			return true;
		}
		
		@Override public boolean isResolutionBased() {
			return false;
		}
	}
	
	private static List<JavacNode> findRequiredFields(JavacNode typeNode) {
		ListBuffer<JavacNode> fields = ListBuffer.lb();
		for (JavacNode child : typeNode.down()) {
			if (child.getKind() != Kind.FIELD) continue;
			JCVariableDecl fieldDecl = (JCVariableDecl) child.get();
			//Skip fields that start with $
			if (fieldDecl.name.toString().startsWith("$")) continue;
			long fieldFlags = fieldDecl.mods.flags;
			//Skip static fields.
			if ((fieldFlags & Flags.STATIC) != 0) continue;
			boolean isFinal = (fieldFlags & Flags.FINAL) != 0;
			boolean isNonNull = !findAnnotations(child, TransformationsUtil.NON_NULL_PATTERN).isEmpty();
			if ((isFinal || isNonNull) && fieldDecl.init == null) fields.append(child);
		}
		return fields.toList();
	}
	
	@ProviderFor(JavacAnnotationHandler.class)
	public static class HandleAllArgsConstructor implements JavacAnnotationHandler<AllArgsConstructor> {
		@Override public boolean handle(AnnotationValues<AllArgsConstructor> annotation, JCAnnotation ast, JavacNode annotationNode) {
			markAnnotationAsProcessed(annotationNode, AllArgsConstructor.class);
			deleteImportFromCompilationUnit(annotationNode, "lombok.AccessLevel");
			JavacNode typeNode = annotationNode.up();
			AllArgsConstructor ann = annotation.getInstance();
			AccessLevel level = ann.access();
			String staticName = ann.staticName();
			@SuppressWarnings("deprecation")
			boolean suppressConstructorProperties = ann.suppressConstructorProperties();
			if (level == AccessLevel.NONE) return true;
			ListBuffer<JavacNode> fields = ListBuffer.lb();
			for (JavacNode child : typeNode.down()) {
				if (child.getKind() != Kind.FIELD) continue;
				JCVariableDecl fieldDecl = (JCVariableDecl) child.get();
				// Skip fields that start with $
				if (fieldDecl.name.toString().startsWith("$")) continue;
				long fieldFlags = fieldDecl.mods.flags;
				// Skip static fields.
				if ((fieldFlags & Flags.STATIC) != 0) continue;
				// Skip initialized final fields.
				if (((fieldFlags & Flags.FINAL) != 0) && fieldDecl.init != null) continue;
				fields.append(child);
			}
			new HandleConstructor().generateConstructor(typeNode, level, fields.toList(), staticName, false, suppressConstructorProperties);
			return true;
		}
		
		@Override public boolean isResolutionBased() {
			return false;
		}
	}
	
	public void generateRequiredArgsConstructor(JavacNode typeNode, AccessLevel level, String staticName, boolean skipIfConstructorExists) {
		generateConstructor(typeNode, level, findRequiredFields(typeNode), staticName, skipIfConstructorExists, false);
	}
	
	public void generateConstructor(JavacNode typeNode, AccessLevel level, List<JavacNode> fields, String staticName, boolean skipIfConstructorExists, boolean suppressConstructorProperties) {
		if (skipIfConstructorExists && constructorExists(typeNode) != MemberExistsResult.NOT_EXISTS) return;
		if (skipIfConstructorExists) {
			for (JavacNode child : typeNode.down()) {
				if (child.getKind() == Kind.ANNOTATION) {
					if (Javac.annotationTypeMatches(NoArgsConstructor.class, child) ||
							Javac.annotationTypeMatches(AllArgsConstructor.class, child) ||
							Javac.annotationTypeMatches(RequiredArgsConstructor.class, child))
						return;
				}
			}
		}
		
		boolean staticConstrRequired = staticName != null && !staticName.equals("");
		
		JCMethodDecl constr = createConstructor(staticConstrRequired ? AccessLevel.PRIVATE : level, typeNode, fields, suppressConstructorProperties);
		injectMethod(typeNode, constr);
		if (staticConstrRequired) {
			JCMethodDecl staticConstr = createStaticConstructor(staticName, level, typeNode, fields);
			injectMethod(typeNode, staticConstr);
		}
	}
	
	private static void addConstructorProperties(JCModifiers mods, JavacNode node, List<JavacNode> fields) {
		if (fields.isEmpty()) return;
		TreeMaker maker = node.getTreeMaker();
		JCExpression constructorPropertiesType = chainDots(maker, node, "java", "beans", "ConstructorProperties");
		ListBuffer<JCExpression> fieldNames = ListBuffer.lb();
		for (JavacNode field : fields) {
			fieldNames.append(maker.Literal(field.getName()));
		}
		JCExpression fieldNamesArray = maker.NewArray(null, List.<JCExpression>nil(), fieldNames.toList());
		JCAnnotation annotation = maker.Annotation(constructorPropertiesType, List.of(fieldNamesArray));
		mods.annotations = mods.annotations.append(annotation);
	}
	
	private JCMethodDecl createConstructor(AccessLevel level, JavacNode typeNode, List<JavacNode> fields, boolean suppressConstructorProperties) {
		TreeMaker maker = typeNode.getTreeMaker();
		
		ListBuffer<JCStatement> nullChecks = ListBuffer.lb();
		ListBuffer<JCStatement> assigns = ListBuffer.lb();
		ListBuffer<JCVariableDecl> params = ListBuffer.lb();
		
		for (JavacNode fieldNode : fields) {
			JCVariableDecl field = (JCVariableDecl) fieldNode.get();
			List<JCAnnotation> nonNulls = findAnnotations(fieldNode, TransformationsUtil.NON_NULL_PATTERN);
			List<JCAnnotation> nullables = findAnnotations(fieldNode, TransformationsUtil.NULLABLE_PATTERN);
			JCVariableDecl param = maker.VarDef(maker.Modifiers(Flags.FINAL, nonNulls.appendList(nullables)), field.name, field.vartype, null);
			params.append(param);
			JCFieldAccess thisX = maker.Select(maker.Ident(fieldNode.toName("this")), field.name);
			JCAssign assign = maker.Assign(thisX, maker.Ident(field.name));
			assigns.append(maker.Exec(assign));
			
			if (!nonNulls.isEmpty()) {
				JCStatement nullCheck = generateNullCheck(maker, fieldNode);
				if (nullCheck != null) nullChecks.append(nullCheck);
			}
		}
		
		JCModifiers mods = maker.Modifiers(toJavacModifier(level));
		if (!suppressConstructorProperties && level != AccessLevel.PRIVATE && !isLocalType(typeNode)) {
			addConstructorProperties(mods, typeNode, fields);
		}
		
		return maker.MethodDef(mods, typeNode.toName("<init>"),
				null, List.<JCTypeParameter>nil(), params.toList(), List.<JCExpression>nil(), maker.Block(0L, nullChecks.appendList(assigns).toList()), null);
	}
	
	private boolean isLocalType(JavacNode type) {
		Kind kind = type.up().getKind();
		if (kind == Kind.COMPILATION_UNIT) return false;
		if (kind == Kind.TYPE) return isLocalType(type.up());
		return true;
	}
	
	private JCMethodDecl createStaticConstructor(String name, AccessLevel level, JavacNode typeNode, List<JavacNode> fields) {
		TreeMaker maker = typeNode.getTreeMaker();
		JCClassDecl type = (JCClassDecl) typeNode.get();
		
		JCModifiers mods = maker.Modifiers(Flags.STATIC | toJavacModifier(level));
		
		JCExpression returnType, constructorType;
		
		ListBuffer<JCTypeParameter> typeParams = ListBuffer.lb();
		ListBuffer<JCVariableDecl> params = ListBuffer.lb();
		ListBuffer<JCExpression> typeArgs1 = ListBuffer.lb();
		ListBuffer<JCExpression> typeArgs2 = ListBuffer.lb();
		ListBuffer<JCExpression> args = ListBuffer.lb();
		
		if (!type.typarams.isEmpty()) {
			for (JCTypeParameter param : type.typarams) {
				typeArgs1.append(maker.Ident(param.name));
				typeArgs2.append(maker.Ident(param.name));
				typeParams.append(maker.TypeParameter(param.name, param.bounds));
			}
			returnType = maker.TypeApply(maker.Ident(type.name), typeArgs1.toList());
			constructorType = maker.TypeApply(maker.Ident(type.name), typeArgs2.toList());
		} else {
			returnType = maker.Ident(type.name);
			constructorType = maker.Ident(type.name);
		}
		
		for (JavacNode fieldNode : fields) {
			JCVariableDecl field = (JCVariableDecl) fieldNode.get();
			JCExpression pType;
			if (field.vartype instanceof JCIdent) pType = maker.Ident(((JCIdent)field.vartype).name);
			else if (field.vartype instanceof JCTypeApply) {
				JCTypeApply typeApply = (JCTypeApply) field.vartype;
				ListBuffer<JCExpression> tArgs = ListBuffer.lb();
				for (JCExpression arg : typeApply.arguments) tArgs.append(arg);
				pType = maker.TypeApply(typeApply.clazz, tArgs.toList());
			} else {
				pType = field.vartype;
			}
			List<JCAnnotation> nonNulls = findAnnotations(fieldNode, TransformationsUtil.NON_NULL_PATTERN);
			List<JCAnnotation> nullables = findAnnotations(fieldNode, TransformationsUtil.NULLABLE_PATTERN);
			JCVariableDecl param = maker.VarDef(maker.Modifiers(Flags.FINAL, nonNulls.appendList(nullables)), field.name, pType, null);
			params.append(param);
			args.append(maker.Ident(field.name));
		}
		JCReturn returnStatement = maker.Return(maker.NewClass(null, List.<JCExpression>nil(), constructorType, args.toList(), null));
		JCBlock body = maker.Block(0, List.<JCStatement>of(returnStatement));
		
		return maker.MethodDef(mods, typeNode.toName(name), returnType, typeParams.toList(), params.toList(), List.<JCExpression>nil(), body, null);
	}
}
