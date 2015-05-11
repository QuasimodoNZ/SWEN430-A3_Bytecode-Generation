package whilelang.io;

import static whilelang.util.SyntaxError.internalFailure;
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
		String className = sourceFile.filename;
		className = className.substring(className.lastIndexOf('/') + 1,
				className.lastIndexOf('.'));
		jasm.lang.ClassFile cf = new ClassFile(49, new JvmType.Clazz("",
				className), JvmTypes.JAVA_LANG_OBJECT, Collections.EMPTY_LIST,
				modifiers);

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

		method.attributes().add(
				new Code(bytecodes, Collections.EMPTY_LIST, method));

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
			addStmtBytecodes(localIndexs, bytecodes, statement);
		}
	}

	private void addStmtBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt stmt) {
		if (stmt instanceof Stmt.VariableDeclaration) {
		} else if (stmt instanceof Stmt.Assign) {
		} else if (stmt instanceof Stmt.Print) {
		}

		if (stmt instanceof Stmt.Assign) {
			addStmtBytecodes(localIndexs, bytecodes, (Stmt.Assign) stmt);
		} else if (stmt instanceof Stmt.For) {
			addStmtBytecodes(localIndexs, bytecodes, (Stmt.For) stmt);
			// TODO
		} else if (stmt instanceof Stmt.While) {
			addStmtBytecodes(localIndexs, bytecodes, (Stmt.While) stmt);
			// TODO
		} else if (stmt instanceof Stmt.IfElse) {
			addStmtBytecodes(localIndexs, bytecodes, (Stmt.IfElse) stmt);
			// TODO
		} else if (stmt instanceof Stmt.Return) {
			addStmtBytecodes(localIndexs, bytecodes, (Stmt.Return) stmt);
			// TODO
		} else if (stmt instanceof Stmt.VariableDeclaration) {
			addStmtBytecodes(localIndexs, bytecodes,
					(Stmt.VariableDeclaration) stmt);
		} else if (stmt instanceof Stmt.Print) {
			addStmtBytecodes(localIndexs, bytecodes, (Stmt.Print) stmt);
		} else if (stmt instanceof Expr.Invoke) {
			JvmType type = addBytecodes(localIndexs, bytecodes, (Expr.Invoke) stmt);
			// TODO may need to through away returned value
			bytecodes.add(new Bytecode.Pop(type));
		} else {
			// TODO unknown statment type, we need to fail here
		}
	}

	private void addStmtBytecodes(Map<String, Integer> localIndexs,
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

	private void addStmtBytecodes(Map<String, Integer> localIndexs,
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

	private void addStmtBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.Print stmt) {

		JvmType.Clazz JAVA_LANG_SYSTEM = new JvmType.Clazz("java.lang",
				"system"), JAVA_IO_PRINTSTREAM = new JvmType.Clazz("java.io",
				"PrintStream");

		bytecodes.add(new Bytecode.GetField(JAVA_LANG_SYSTEM, "out",
				JAVA_IO_PRINTSTREAM, Bytecode.FieldMode.STATIC));

		// String str = toString(execute(stmt.getExpr(),frame));
		// execute expression leaving value on top returning the JvmType of the
		// value
		JvmType type = addBytecodes(localIndexs, bytecodes, stmt.getExpr());

		// System.out.println(str);

		bytecodes
				.add(new Bytecode.Invoke(JAVA_IO_PRINTSTREAM, "println",
						new JvmType.Function(JvmTypes.T_VOID,
								JvmTypes.JAVA_LANG_STRING),
						Bytecode.InvokeMode.VIRTUAL));

	}

	/**
	 * Adds bytecodes for executing the expression so that the top result on the
	 * stack is the resulting value
	 * 
	 * @param localIndexs
	 * @param bytecodes
	 * @param expr
	 */
	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr expr) {
		JvmType type;
		// TODO Auto-generated method stub
		if (expr instanceof Expr.Constant) {
			bytecodes.add(new Bytecode.LoadConst(((Expr.Constant) expr)
					.getValue()));
			type = getJvmType(((Expr.Constant) expr).getValue());
		} else if (expr instanceof Expr.Variable) {
		}

		if (expr instanceof Expr.Binary) {
			// return execute((Expr.Binary) expr,frame);
		} else if (expr instanceof Expr.Cast) {
			// return execute((Expr.Cast) expr,frame);
		} else if (expr instanceof Expr.Constant) {
			bytecodes.add(new Bytecode.LoadConst(((Expr.Constant) expr)
					.getValue()));
			type = getJvmType(((Expr.Constant) expr).getValue());
		} else if (expr instanceof Expr.Invoke) {
			// return execute((Expr.Invoke) expr,frame);
		} else if (expr instanceof Expr.IndexOf) {
			// return execute((Expr.IndexOf) expr,frame);
		} else if (expr instanceof Expr.ListConstructor) {
			// return execute((Expr.ListConstructor) expr,frame);
		} else if (expr instanceof Expr.RecordAccess) {
			// return execute((Expr.RecordAccess) expr,frame);
		} else if (expr instanceof Expr.RecordConstructor) {
			// return execute((Expr.RecordConstructor) expr,frame);
		} else if (expr instanceof Expr.Unary) {
			// return execute((Expr.Unary) expr,frame);
		} else if (expr instanceof Expr.Variable) {
			type = getJvmType(((Expr.Variable) expr).attributes());
			bytecodes.add(new Bytecode.Load(localIndexs
					.get(((Expr.Variable) expr).getName()), type));
			// return execute((Expr.Variable) expr,frame);
		} else {
			// TODO unknown expression type, should through a compile error
		}
		type = null;
		return type;

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
