package whilelang.io;

import jasm.attributes.Code;
import jasm.lang.Bytecode;
import jasm.lang.ClassFile;
import jasm.lang.ClassFile.Method;
import jasm.lang.JvmType;
import jasm.lang.JvmType.Bool;
import jasm.lang.JvmType.Clazz;
import jasm.lang.JvmTypes;
import jasm.lang.Modifier;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import whilelang.lang.Expr;
import whilelang.lang.Expr.LVal;
import whilelang.lang.Stmt;
import whilelang.lang.Type;
import whilelang.lang.WhileFile;
import whilelang.lang.WhileFile.Decl;
import whilelang.lang.WhileFile.FunDecl;
import whilelang.lang.WhileFile.Parameter;

/**
 * Responsible for translating a While source file into a JVM Class file.
 * 
 * @author David J. Pearce
 * 
 */
public class ClassFileWriter {

	jasm.io.ClassFileWriter writer;

	public ClassFileWriter(File classFile) {
		try {
			writer = new jasm.io.ClassFileWriter(
					new FileOutputStream(classFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void write(WhileFile sourceFile) throws IOException {
		List<Modifier> modifiers = new ArrayList<Modifier>();
		modifiers.add(Modifier.ACC_PUBLIC);
		jasm.lang.ClassFile cf = new ClassFile(49, new JvmType.Clazz("",
				"Class name here"), JvmTypes.JAVA_LANG_OBJECT,
				Collections.EMPTY_LIST, modifiers);

		// TODO: implement this method!!
		for (Decl function : sourceFile.declarations) {
			if (function instanceof FunDecl) {
				addMethod(cf, (FunDecl) function);
			}
		}

		writer.write(cf);
	}

	private void addMethod(ClassFile cf, FunDecl function) {
		// TODO Auto-generated method stub
		String methodName = function.name();
		JvmType returnType = jvmType(function.ret);

		Map<String, Integer> localIndexs = new HashMap<String, Integer>();
		localIndexs.put("this", 0);

		List<JvmType> parameters = new ArrayList<JvmType>();
		for (Parameter param : function.parameters) {
			parameters.add(jvmType(param.type));
			localIndexs.put(param.name(), localIndexs.size());
		}

		List<Modifier> modifiers = new ArrayList<Modifier>();
		if (methodName.equals("main"))
			modifiers.add(Modifier.ACC_STATIC);

		JvmType.Function func = new JvmType.Function(returnType, parameters);
		ClassFile.Method method = new ClassFile.Method(methodName, func,
				modifiers);

		List<Bytecode> bytecodes = new ArrayList<Bytecode>();
		addBytecodes(localIndexs, bytecodes, function.statements);

		method.attributes().add(new Code(bytecodes, Collections.EMPTY_LIST, method));
		
		cf.methods().add(method);
	}

	private JvmType jvmType(Type ret) {
		if (ret instanceof Type.Void)
			return new JvmType.Void();
		return null;
	}

	/**
	 * 
	 * @param localIndexs
	 *            variable name -> index of variable stack
	 * @param bytecodes
	 *            current list of bytecodes to add to
	 * @param statements
	 *            statements that need to be converted to bytecodes
	 * @return
	 */
	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, ArrayList<Stmt> statements) {
		for (Stmt statement : statements) {
			addBytecodes(localIndexs, bytecodes, statement);
		}
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt statement) {
		if (statement instanceof Stmt.VariableDeclaration) {
			addBytecodes(localIndexs, bytecodes,
					(Stmt.VariableDeclaration) statement);
		} else if (statement instanceof Stmt.Assign) {
			addBytecodes(localIndexs, bytecodes, (Stmt.Assign) statement);
		} else if (statement instanceof Stmt.Print) {
			addBytecodes(localIndexs, bytecodes, (Stmt.Print) statement);
		}
		// TODO Auto-generated method stub
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.VariableDeclaration statement) {
		Expr expr = statement.getExpr();
		JvmType type = null;
		if (expr instanceof Expr.Constant) {
			bytecodes.add(new Bytecode.LoadConst(((Expr.Constant) expr)
					.getValue()));
			type = getJvmType(((Expr.Constant) expr).getValue());
		}

		localIndexs.put(statement.getName(), localIndexs.size());
		bytecodes.add(new Bytecode.Store(localIndexs.size() - 1, type));
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.Assign statement) {
		Expr rhs = statement.getRhs();
		JvmType type = null;
		if (rhs instanceof Expr.Constant) {
			bytecodes.add(new Bytecode.LoadConst(((Expr.Constant) rhs)
					.getValue()));
			type = getJvmType(((Expr.Constant) rhs).getValue());
		}

		LVal lhs = statement.getLhs();
		if (lhs instanceof Expr.Variable) {
			bytecodes.add(new Bytecode.Store(localIndexs
					.get(((Expr.Variable) lhs).getName()), type));
		}
		// TODO index of
		// TODO record access

	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.Print statement) {

		JvmType.Clazz JAVA_LANG_SYSTEM = new JvmType.Clazz("java.lang",
				"system"), JAVA_IO_PRINTSTREAM = new JvmType.Clazz("java.io",
				"PrintStream");

		bytecodes.add(new Bytecode.GetField(JAVA_LANG_SYSTEM, "out",
				JAVA_IO_PRINTSTREAM, Bytecode.FieldMode.STATIC));

		Expr expr = statement.getExpr();
		JvmType type = null;
		if (expr instanceof Expr.Constant) {
			bytecodes.add(new Bytecode.LoadConst(((Expr.Constant) expr)
					.getValue()));
			type = getJvmType(((Expr.Constant) expr).getValue());
		} else if (expr instanceof Expr.Variable) {
			type = getJvmType(((Expr.Variable) expr).attributes());
			bytecodes.add(new Bytecode.Load(localIndexs
					.get(((Expr.Variable) expr).getName()), type));
		}

		bytecodes
				.add(new Bytecode.Invoke(JAVA_IO_PRINTSTREAM, "println",
						new JvmType.Function(JvmTypes.T_VOID,
								JvmTypes.JAVA_LANG_STRING),
						Bytecode.InvokeMode.VIRTUAL));

	}

	private JvmType getJvmType(List<whilelang.util.Attribute> attributes) {
		for (int i = attributes.size() - 1; i >= 0; i--) {
			if (attributes.get(i) instanceof whilelang.util.Attribute.Type) {
				return getJvmType(((whilelang.util.Attribute.Type) attributes
						.get(i)).type);
			}
		}
		return null;
	}

	private JvmType getJvmType(Object value) {
		if (value instanceof Boolean
				|| value instanceof whilelang.lang.Type.Bool) {
			return new JvmType.Bool();
		}
		// TODO Auto-generated method stub
		return null;
	}
}
