package whilelang.io;

import jasm.attributes.Code;
import jasm.lang.Bytecode;
import jasm.lang.Bytecode.FieldMode;
import jasm.lang.Bytecode.Label;
import jasm.lang.ClassFile;
import jasm.lang.JvmType;
import jasm.lang.JvmType.Clazz;
import jasm.lang.JvmType.Primitive;
import jasm.lang.JvmType.Reference;
import jasm.lang.JvmTypes;
import jasm.lang.Modifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import whilelang.lang.WhileFile.ConstDecl;
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
	private static Map<String, JvmType> methodReturnTypes = new HashMap<String, JvmType>();// Method
																							// name
																							// to
																							// return
																							// type

	JvmType.Clazz JAVA_LANG_SYSTEM = new JvmType.Clazz("java.lang", "System"),
			JAVA_IO_PRINTSTREAM = new JvmType.Clazz("java.io", "PrintStream"),
			JAVA_UTIL_ARRAYLIST = new JvmType.Clazz("java.util", "ArrayList"),
			JAVA_UTIL_HASHMAP = new JvmType.Clazz("java.io", "HashMap");

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

		JvmType.Clazz thIs = new JvmType.Clazz("", className);
		jasm.lang.ClassFile cf = new ClassFile(50, thIs,
				JvmTypes.JAVA_LANG_OBJECT, Collections.EMPTY_LIST, modifiers);

		methodReturnTypes = new HashMap<String, JvmType>();
		for (Decl function : sourceFile.declarations)
			if (function instanceof FunDecl)
				methodReturnTypes.put(function.name(),
						getJvmType(((FunDecl) function).ret));

		List<ConstDecl> cd = new ArrayList<ConstDecl>();
		for (Decl d : sourceFile.declarations)
			if (d instanceof ConstDecl) {
				cd.add((ConstDecl) d);
				List<Modifier> l = new ArrayList<Modifier>(modifiers);
				l.add(Modifier.ACC_STATIC);
				cf.fields().add(
						new ClassFile.Field(d.name(),
								getJvmType(((ConstDecl) d).attributes()), l));
			}
		
		if(cf.fields().size()!=0){
			addFields(cf, cd);
		}

		for (Decl function : sourceFile.declarations) {
			if (function instanceof FunDecl) {
				addMethod(cf, (FunDecl) function, thIs, sourceFile);
			}
		}

		writer.write(cf);
	}

	private void addFields(ClassFile cf, List<ConstDecl> cd) {
		// TODO Auto-generated method stub
		for(ConstDecl c : cd){
			String name = c.name;
			Object value = ((Expr.Constant)c.constant).getValue();
			
		}
		
	}

	private void addMethod(ClassFile cf, FunDecl function, JvmType.Clazz thIs,
			WhileFile sourceFile) {
		MethodPage mp = new MethodPage(thIs, sourceFile);

		String methodName = function.name();
		JvmType returnType = getJvmType(function.ret);

		List<JvmType> parameters = new ArrayList<JvmType>();
		for (Parameter param : function.parameters) {
			parameters.add(getJvmType(param.type));
			mp.put(param.name(), jvmTypeSize(getJvmType(param.type)));
		}

		List<Modifier> modifiers = new ArrayList<Modifier>();
		modifiers.add(Modifier.ACC_PUBLIC);
		modifiers.add(Modifier.ACC_STATIC);
		if (methodName.equals("main")) {
			parameters.add(new JvmType.Array(JvmTypes.JAVA_LANG_STRING));
		}

		JvmType.Function func = new JvmType.Function(returnType, parameters);
		ClassFile.Method method = new ClassFile.Method(methodName, func,
				modifiers);

		addBytecodes(function.statements, mp);
		mp.bc.add(new Bytecode.Return(null));

		method.attributes()
				.add(new Code(mp.bc, Collections.EMPTY_LIST, method));

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
	private void addBytecodes(List<Stmt> statements, MethodPage mp) {
		for (Stmt statement : statements) {
			addBytecodes(statement, mp);
		}
	}

	private void addBytecodes(Stmt stmt, MethodPage mp) {
		if (stmt instanceof Stmt.Assign) {
			addBytecodes((Stmt.Assign) stmt, mp);
		} else if (stmt instanceof Stmt.For) {
			addBytecodes((Stmt.For) stmt, mp);
			// TODO
		} else if (stmt instanceof Stmt.While) {
			addBytecodes((Stmt.While) stmt, mp);
			// TODO
		} else if (stmt instanceof Stmt.IfElse) {
			addBytecodes((Stmt.IfElse) stmt, mp);
		} else if (stmt instanceof Stmt.Return) {
			addBytecodes((Stmt.Return) stmt, mp);
		} else if (stmt instanceof Stmt.VariableDeclaration) {
			addBytecodes((Stmt.VariableDeclaration) stmt, mp);
		} else if (stmt instanceof Stmt.Print) {
			addBytecodes((Stmt.Print) stmt, mp);
		} else if (stmt instanceof Expr.Invoke) {
			JvmType type = addBytecodes((Expr.Invoke) stmt, mp);
			mp.bc.add(new Bytecode.Pop(type));
		} else {
			// TODO unknown statement type, we need to fail here
		}
	}

	private void addBytecodes(Stmt.Assign statement, MethodPage mp) {
		Expr rhs = statement.getRhs();
		JvmType type = addBytecodes(rhs, mp);

		LVal lhs = statement.getLhs();
		if (lhs instanceof Expr.Variable) {
			mp.bc.add(new Bytecode.Store(mp.localIndexs
					.get(((Expr.Variable) lhs).getName()), type));
		}
		// TODO index of
		// TODO record access

	}

	private void addBytecodes(Stmt.For statement, MethodPage mp) {
		addBytecodes(statement.getDeclaration(),mp);
		
		String startLabel = mp.next(), endLabel = mp.next();
		mp.bc.add(new Bytecode.Label(startLabel));
		addBytecodes(statement.getCondition(), mp);		
		mp.bc.add(new Bytecode.If(Bytecode.IfMode.EQ, endLabel));
		addBytecodes(statement.getBody(), mp);
		addBytecodes(statement.getIncrement(),mp);
		mp.bc.add(new Bytecode.Goto(startLabel));
		mp.bc.add(new Bytecode.Label(endLabel));
	}

	private void addBytecodes(Stmt.While statement, MethodPage mp) {
		String startLabel = mp.next(), endLabel = mp.next();
		mp.bc.add(new Bytecode.Label(startLabel));
		addBytecodes(statement.getCondition(), mp);		
		mp.bc.add(new Bytecode.If(Bytecode.IfMode.EQ, endLabel));
		addBytecodes(statement.getBody(), mp);
		mp.bc.add(new Bytecode.Goto(startLabel));
		mp.bc.add(new Bytecode.Label(endLabel));
	}

	private void addBytecodes(Stmt.IfElse stmt, MethodPage mp) {
		addBytecodes(stmt.getCondition(), mp);

		String falseLabel = mp.next(), endLabel = mp.next();
		mp.bc.add(new Bytecode.If(Bytecode.IfMode.EQ, falseLabel));

		// execute true branch
		addBytecodes(stmt.getTrueBranch(), mp);
		// skip false branch and go to end
		mp.bc.add(new Bytecode.Goto(endLabel));
		// false branchlabel
		mp.bc.add(new Bytecode.Label(falseLabel));
		// false branch
		if (stmt.getFalseBranch().size() != 0) {
			addBytecodes(stmt.getFalseBranch(), mp);
		}
		mp.bc.add(new Bytecode.Label(endLabel));
	}

	private void addBytecodes(Stmt.Return statement, MethodPage mp) {
		JvmType type = addBytecodes(statement.getExpr(), mp);
		mp.bc.add(new Bytecode.Return(type));
	}

	private void addBytecodes(Stmt.VariableDeclaration statement, MethodPage mp) {
		JvmType type = addBytecodes(statement.getExpr(), mp);

		mp.put(statement.getName(), jvmTypeSize(type));
		mp.bc.add(new Bytecode.Store(mp.localIndexs.get(statement.getName()),
				type));
	}

	private void addBytecodes(Stmt.Print stmt, MethodPage mp) {
		mp.bc.add(new Bytecode.GetField(JAVA_LANG_SYSTEM, "out",
				JAVA_IO_PRINTSTREAM, Bytecode.FieldMode.STATIC));

		// String str = toString(execute(stmt.getExpr(),frame));
		// execute expression leaving value on top returning the JvmType of the
		// value
		JvmType type = addBytecodes(stmt.getExpr(), mp);

		// System.out.println(str);

		mp.bc.add(new Bytecode.Invoke(JAVA_IO_PRINTSTREAM, "println",
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
	private JvmType addBytecodes(Expr expr, MethodPage mp) {
		if (expr instanceof Expr.Binary) {
			return addBytecodes((Expr.Binary) expr, mp);
		} else if (expr instanceof Expr.Cast) {
			return addBytecodes((Expr.Cast) expr, mp);
		} else if (expr instanceof Expr.Constant) {
			return addBytecodes((Expr.Constant) expr, mp);
		} else if (expr instanceof Expr.Invoke) {
			return addBytecodes((Expr.Invoke) expr, mp);
		} else if (expr instanceof Expr.IndexOf) {
			return addBytecodes((Expr.IndexOf) expr, mp);
		} else if (expr instanceof Expr.ListConstructor) {
			return addBytecodes((Expr.ListConstructor) expr, mp);
		} else if (expr instanceof Expr.RecordAccess) {
			return addBytecodes((Expr.RecordAccess) expr, mp);
		} else if (expr instanceof Expr.RecordConstructor) {
			return addBytecodes((Expr.RecordConstructor) expr, mp);
		} else if (expr instanceof Expr.Unary) {
			return addBytecodes((Expr.Unary) expr, mp);
		} else if (expr instanceof Expr.Variable) {
			return addBytecodes((Expr.Variable) expr, mp);
		} else {
			// TODO unknown expression type, should through a compile error
			return null;
		}
	}

	private JvmType addBytecodes(Expr.Binary expr, MethodPage mp) {
		// TODO
		JvmType lhs = addBytecodes(expr.getLhs(), mp);
		String label = mp.next();
		mp.bc.add(new Bytecode.Label(label));
		JvmType rhs = addBytecodes(expr.getRhs(), mp);

		if (lhs.getClass().equals(rhs.getClass())) {
		} else if (lhs instanceof JvmType.Int && rhs instanceof JvmType.Double) {
			for (int i = mp.bc.size() - 1; 0 <= i; i--) {
				Bytecode b = mp.bc.get(i);
				if (b instanceof Bytecode.Label
						&& ((Bytecode.Label) b).name.equals(label)) {
					mp.bc.set(i, new Bytecode.Conversion(new JvmType.Int(),
							new JvmType.Double()));
					break;
				}
			}
			lhs = new JvmType.Double();
		} else if (lhs instanceof JvmType.Double && rhs instanceof JvmType.Int) {
			rhs = new JvmType.Double();
			mp.bc.add(new Bytecode.Conversion(new JvmType.Int(),
					new JvmType.Double()));
		}

		// TODO test the types, if the are not the same like integer and double
		// or float then we need to cast them and then try again

		JvmType type = lhs;
		String trueLabel, endLabel;

		switch (expr.getOp()) {
		case AND:
			type = new JvmType.Bool();
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.AND, type));
			return type;
		case OR:
			type = new JvmType.Bool();
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.OR, type));
			return type;
		case ADD:
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.ADD, type));
			return type;
		case SUB:
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.SUB, type));
			return type;
		case MUL:
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.MUL, type));
			return type;
		case DIV:
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.DIV, type));
			return type;
		case REM:
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.REM, type));
			return type;
		case EQ:
			trueLabel = mp.next();
			endLabel = mp.next();
			mp.bc.add(new Bytecode.IfCmp(Bytecode.IfCmp.EQ, type, trueLabel));
			mp.bc.add(new Bytecode.LoadConst(false));
			mp.bc.add(new Bytecode.Goto(endLabel));
			mp.bc.add(new Bytecode.Label(trueLabel));
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.Label(endLabel));
			return new JvmType.Bool();
		case NEQ:// TODO
			trueLabel = mp.next();
			endLabel = mp.next();
			mp.bc.add(new Bytecode.IfCmp(Bytecode.IfCmp.NE, type, trueLabel));
			mp.bc.add(new Bytecode.LoadConst(false));
			mp.bc.add(new Bytecode.Goto(endLabel));
			mp.bc.add(new Bytecode.Label(trueLabel));
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.Label(endLabel));
			return new JvmType.Bool();
		case LT:// TODO
			trueLabel = mp.next();
			endLabel = mp.next();
			mp.bc.add(new Bytecode.IfCmp(Bytecode.IfCmp.LT, type, trueLabel));
			mp.bc.add(new Bytecode.LoadConst(false));
			mp.bc.add(new Bytecode.Goto(endLabel));
			mp.bc.add(new Bytecode.Label(trueLabel));
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.Label(endLabel));
			return new JvmType.Bool();
		case LTEQ:// TODO
			trueLabel = mp.next();
			endLabel = mp.next();
			mp.bc.add(new Bytecode.IfCmp(Bytecode.IfCmp.LE, type, trueLabel));
			mp.bc.add(new Bytecode.LoadConst(false));
			mp.bc.add(new Bytecode.Goto(endLabel));
			mp.bc.add(new Bytecode.Label(trueLabel));
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.Label(endLabel));
			return new JvmType.Bool();
		case GT:// TODO
			trueLabel = mp.next();
			endLabel = mp.next();
			mp.bc.add(new Bytecode.IfCmp(Bytecode.IfCmp.GT, type, trueLabel));
			mp.bc.add(new Bytecode.LoadConst(false));
			mp.bc.add(new Bytecode.Goto(endLabel));
			mp.bc.add(new Bytecode.Label(trueLabel));
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.Label(endLabel));
			return new JvmType.Bool();
		case GTEQ:// TODO
			trueLabel = mp.next();
			endLabel = mp.next();
			mp.bc.add(new Bytecode.IfCmp(Bytecode.IfCmp.GE, type, trueLabel));
			mp.bc.add(new Bytecode.LoadConst(false));
			mp.bc.add(new Bytecode.Goto(endLabel));
			mp.bc.add(new Bytecode.Label(trueLabel));
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.Label(endLabel));
			return new JvmType.Bool();
		case APPEND:// TODO
		}

		return null;
	}

	private JvmType addBytecodes(Expr.Cast expr, MethodPage mp) {
		JvmType type = addBytecodes(expr.getSource(), mp);
		if (type instanceof Primitive
				&& getJvmType(expr.getType()) instanceof Primitive)
			mp.bc.add(new Bytecode.Conversion((Primitive) type,
					(Primitive) getJvmType(expr.getType())));
		return getJvmType(expr.getType());
	}

	private JvmType addBytecodes(Expr.Constant expr, MethodPage mp) {
		mp.bc.add(new Bytecode.LoadConst(((Expr.Constant) expr).getValue()));
		return getJvmType(((Expr.Constant) expr).getValue());
	}

	private JvmType addBytecodes(Expr.Invoke expr, MethodPage mp) {
		List<JvmType> argumentTypes = new ArrayList<JvmType>();
		for (Expr arg : expr.getArguments())
			argumentTypes.add(addBytecodes(arg, mp));

		String methodName = expr.getName();
		JvmType.Function func = new JvmType.Function(
				methodReturnTypes.get(methodName), argumentTypes);

		mp.bc.add(new Bytecode.Invoke(mp.thIs, methodName, func,
				Bytecode.InvokeMode.STATIC));
		return methodReturnTypes.get(methodName);
	}

	private JvmType addBytecodes(Expr.IndexOf expr, MethodPage mp) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Expr.ListConstructor expr, MethodPage mp) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Expr.RecordAccess expr, MethodPage mp) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Expr.RecordConstructor expr, MethodPage mp) {
		// TODO
		return null;
	}

	private JvmType addBytecodes(Expr.Unary expr, MethodPage mp) {
		JvmType type = addBytecodes(expr.getExpr(), mp);
		switch (expr.getOp()) {
		case NOT:
			mp.bc.add(new Bytecode.LoadConst(true));
			mp.bc.add(new Bytecode.BinOp(Bytecode.BinOp.XOR, type));
			return type;
		case NEG:
			mp.bc.add(new Bytecode.Neg(type));
			return type;
		case LENGTHOF:
			Reference ref;
			String methodName;
			if (JvmTypes.isJavaLangString(type)) {
				ref = JvmTypes.JAVA_LANG_STRING;
				methodName = "length";
			} else if (JvmTypes.isJavaLangObject(type)) {
				ref = null; // TODO figure out if list or map
				methodName = "size";
			} else {
				// TODO throw error
				ref = null;
				methodName = null;
			}
			mp.bc.add(new Bytecode.Invoke(ref, methodName,
					new JvmType.Function(new JvmType.Int()),
					Bytecode.InvokeMode.VIRTUAL));
			type = new JvmType.Int();
		}
		// TODO
		return type;
	}

	private JvmType addBytecodes(Expr.Variable expr, MethodPage mp) {
		JvmType type = getJvmType(((Expr.Variable) expr).attributes());

		if (!mp.localIndexs.containsKey(((Expr.Variable) expr).getName()))
			mp.bc.add(new Bytecode.GetField(mp.thIs, expr.getName(), type,
					FieldMode.STATIC));
		else
			mp.bc.add(new Bytecode.Load(mp.localIndexs
					.get(((Expr.Variable) expr).getName()), type));
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
		if (value instanceof Type.Null) {
			return new JvmType.Null();
		} else if (value instanceof Boolean || value instanceof Type.Bool) {
			return new JvmType.Bool();
		} else if (value instanceof Character || value instanceof Type.Char) {
			return new JvmType.Char();
		} else if (value instanceof Integer || value instanceof Type.Int) {
			return new JvmType.Int();
		} else if (value instanceof java.lang.Double
				|| value instanceof Type.Real) {
			return new JvmType.Double();
		} else if (value instanceof String || value instanceof Type.Strung) {
			return JvmTypes.JAVA_LANG_STRING;
		} else if (value instanceof Type.Void) {
			return new JvmType.Void();
		}
		return null;
	}

	private int jvmTypeSize(JvmType type) {
		if (type instanceof JvmType.Long || type instanceof JvmType.Double)
			return 2;
		return 1;
	}

	private class MethodPage {

		public Clazz thIs;
		public WhileFile sourceFile;
		public List<Bytecode> bc;

		public Map<String, Integer> localIndexs;
		public int nextIndex;
		private int labelCounter;

		private MethodPage(Clazz thIs, WhileFile sourceFile) {
			this.thIs = thIs;
			this.sourceFile = sourceFile;
			localIndexs = new HashMap<String, Integer>();
			nextIndex = 0;
			bc = new ArrayList<Bytecode>();
			labelCounter = 0;
		}

		private void put(String name, int typeSize) {
			localIndexs.put(name, nextIndex);
			nextIndex += typeSize;
		}

		private String next() {
			return "label-" + labelCounter++;
		}

		public String toString() {
			return bc.toString();
		}

	}
}
