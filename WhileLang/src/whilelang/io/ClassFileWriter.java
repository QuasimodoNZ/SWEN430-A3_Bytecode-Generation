package whilelang.io;

import static whilelang.util.SyntaxError.internalFailure;
import jasm.attributes.Code;
import jasm.lang.Bytecode;
import jasm.lang.Bytecode.BinOp;
import jasm.lang.Bytecode.LoadConst;
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

		for (Decl function : sourceFile.declarations) {
			if (function instanceof FunDecl) {
				addMethod(cf, (FunDecl) function);
			}
		}

		writer.write(cf);
	}

	private void addMethod(ClassFile cf, FunDecl function) {
		// TODO need to figure out return types, parameters and also modifiers
		String methodName = function.name();
		JvmType returnType = getJvmType(function.ret);

		Map<String, Integer> localIndexs = new HashMap<String, Integer>();
		if (!methodName.equalsIgnoreCase("main"))
			localIndexs.put("this", 0);

		List<JvmType> parameters = new ArrayList<JvmType>();
		for (Parameter param : function.parameters) {
			parameters.add(getJvmType(param.type));
			localIndexs.put(param.name(), localIndexs.size());
		}

		List<Modifier> modifiers = new ArrayList<Modifier>();
		if (methodName.equals("main")) {
			modifiers.add(Modifier.ACC_PUBLIC);
			modifiers.add(Modifier.ACC_STATIC);
			parameters.add(new JvmType.Array(JvmTypes.JAVA_LANG_STRING));
		}

		JvmType.Function func = new JvmType.Function(returnType, parameters);
		ClassFile.Method method = new ClassFile.Method(methodName, func,
				modifiers);

		List<Bytecode> bytecodes = new ArrayList<Bytecode>();
		addBytecodes(localIndexs, bytecodes, function.statements);
		bytecodes.add(new Bytecode.Return(null));

		method.attributes().add(
				new Code(bytecodes, Collections.EMPTY_LIST, method));

		cf.methods().add(method);
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
			List<Bytecode> bytecodes, Stmt stmt) {
		if (stmt instanceof Stmt.Assign) {
			addBytecodes(localIndexs, bytecodes, (Stmt.Assign) stmt);
		} else if (stmt instanceof Stmt.For) {
			addBytecodes(localIndexs, bytecodes, (Stmt.For) stmt);
			// TODO
		} else if (stmt instanceof Stmt.While) {
			addBytecodes(localIndexs, bytecodes, (Stmt.While) stmt);
			// TODO
		} else if (stmt instanceof Stmt.IfElse) {
			addBytecodes(localIndexs, bytecodes, (Stmt.IfElse) stmt);
			// TODO
		} else if (stmt instanceof Stmt.Return) {
			addBytecodes(localIndexs, bytecodes, (Stmt.Return) stmt);
			// TODO
		} else if (stmt instanceof Stmt.VariableDeclaration) {
			addBytecodes(localIndexs, bytecodes,
					(Stmt.VariableDeclaration) stmt);
		} else if (stmt instanceof Stmt.Print) {
			addBytecodes(localIndexs, bytecodes, (Stmt.Print) stmt);
		} else if (stmt instanceof Expr.Invoke) {
			JvmType type = addBytecodes(localIndexs, bytecodes,
					(Expr.Invoke) stmt);
			// TODO may need to through away returned value
			bytecodes.add(new Bytecode.Pop(type));
		} else {
			// TODO unknown statement type, we need to fail here
		}
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.Assign statement) {
		Expr rhs = statement.getRhs();
		JvmType type = addBytecodes(localIndexs, bytecodes, rhs);

		LVal lhs = statement.getLhs();
		if (lhs instanceof Expr.Variable) {
			bytecodes.add(new Bytecode.Store(localIndexs
					.get(((Expr.Variable) lhs).getName()), type));
		}
		// TODO index of
		// TODO record access

	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.For statement) {
		addBytecodes(localIndexs, bytecodes, statement.getDeclaration());
		addBytecodes(localIndexs, bytecodes, statement.getCondition());

		// TODO fill out this method
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.While statement) {
		// TODO fill out this method
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.IfElse statement) {
		// TODO fill out this method
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.Return statement) {
		// TODO fill out this method
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.VariableDeclaration statement) {
		JvmType type = addBytecodes(localIndexs, bytecodes, statement.getExpr());

		localIndexs.put(statement.getName(), localIndexs.size());
		bytecodes.add(new Bytecode.Store(localIndexs.size() - 1, type));
	}

	private void addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Stmt.Print stmt) {

		JvmType.Clazz JAVA_LANG_SYSTEM = new JvmType.Clazz("java.lang",
				"System"), JAVA_IO_PRINTSTREAM = new JvmType.Clazz("java.io",
				"PrintStream");

		bytecodes.add(new Bytecode.GetField(JAVA_LANG_SYSTEM, "out",
				JAVA_IO_PRINTSTREAM, Bytecode.FieldMode.STATIC));

		// String str = toString(execute(stmt.getExpr(),frame));
		// execute expression leaving value on top returning the JvmType of the
		// value
		JvmType type = addBytecodes(localIndexs, bytecodes, stmt.getExpr());

		// System.out.println(str);

		bytecodes.add(new Bytecode.Invoke(JAVA_IO_PRINTSTREAM, "println",
				new JvmType.Function(JvmTypes.T_VOID, type),
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
		if (expr instanceof Expr.Binary) {
			return addBytecodes(localIndexs, bytecodes, (Expr.Binary) expr);
		} else if (expr instanceof Expr.Cast) {
			return addBytecodes(localIndexs, bytecodes, (Expr.Cast) expr);
		} else if (expr instanceof Expr.Constant) {
			return addBytecodes(localIndexs, bytecodes, (Expr.Constant) expr);
		} else if (expr instanceof Expr.Invoke) {
			return addBytecodes(localIndexs, bytecodes, (Expr.Invoke) expr);
		} else if (expr instanceof Expr.IndexOf) {
			return addBytecodes(localIndexs, bytecodes, (Expr.IndexOf) expr);
		} else if (expr instanceof Expr.ListConstructor) {
			return addBytecodes(localIndexs, bytecodes,
					(Expr.ListConstructor) expr);
		} else if (expr instanceof Expr.RecordAccess) {
			return addBytecodes(localIndexs, bytecodes,
					(Expr.RecordAccess) expr);
		} else if (expr instanceof Expr.RecordConstructor) {
			return addBytecodes(localIndexs, bytecodes,
					(Expr.RecordConstructor) expr);
		} else if (expr instanceof Expr.Unary) {
			return addBytecodes(localIndexs, bytecodes, (Expr.Unary) expr);
		} else if (expr instanceof Expr.Variable) {
			return addBytecodes(localIndexs, bytecodes, (Expr.Variable) expr);
		} else {
			// TODO unknown expression type, should through a compile error
			return null;
		}
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.Binary expr) {
		// TODO
		JvmType lhs = addBytecodes(localIndexs, bytecodes, expr.getLhs()), rhs = addBytecodes(localIndexs, bytecodes, expr.getRhs());
		JvmType.Bool type;
		switch(expr.getOp()){
		case AND:
			type = new JvmType.Bool();
			bytecodes.add(new Bytecode.BinOp(Bytecode.BinOp.AND, type));
			return type;
		case OR:
			type = new JvmType.Bool();
			bytecodes.add(new Bytecode.BinOp(Bytecode.BinOp.OR, type));
			return type;

		}
		
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.Cast expr) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.Constant expr) {
		bytecodes
				.add(new Bytecode.LoadConst(((Expr.Constant) expr).getValue()));
		return getJvmType(((Expr.Constant) expr).getValue());
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.Invoke expr) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.IndexOf expr) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.ListConstructor expr) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.RecordAccess expr) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.RecordConstructor expr) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.Unary expr) {
		JvmType type = addBytecodes(localIndexs, bytecodes, expr.getExpr());
		switch (expr.getOp()){
		case NOT:
			bytecodes.add(new Bytecode.LoadConst(true));
			bytecodes.add(new Bytecode.BinOp(Bytecode.BinOp.XOR, type));
		}
		// TODO
		return type;
	}

	private JvmType addBytecodes(Map<String, Integer> localIndexs,
			List<Bytecode> bytecodes, Expr.Variable expr) {
		JvmType type = getJvmType(((Expr.Variable) expr).attributes());
		bytecodes.add(new Bytecode.Load(localIndexs.get(((Expr.Variable) expr)
				.getName()), type));
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
		} else if (value instanceof String
				|| value instanceof whilelang.lang.Type.Strung) {
			return JvmTypes.JAVA_LANG_STRING;
		}

		else if (value instanceof whilelang.lang.Type.Void) {
			return new JvmType.Void();
		}
		// TODO Auto-generated method stub
		return null;
	}
}
