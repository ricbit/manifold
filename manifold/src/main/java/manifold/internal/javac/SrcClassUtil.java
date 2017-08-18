package manifold.internal.javac;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Pair;
import java.lang.reflect.Modifier;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import manifold.api.gen.SrcAnnotated;
import manifold.api.gen.SrcAnnotationExpression;
import manifold.api.gen.SrcClass;
import manifold.api.gen.SrcField;
import manifold.api.gen.SrcMethod;
import manifold.api.gen.SrcParameter;
import manifold.api.gen.SrcRawExpression;
import manifold.api.gen.SrcRawStatement;
import manifold.api.gen.SrcStatementBlock;
import manifold.api.gen.SrcType;

/**
 */
public class SrcClassUtil
{
  private static final SrcClassUtil INSTANCE = new SrcClassUtil();

  private SrcClassUtil()
  {
  }

  public static SrcClassUtil instance()
  {
    return INSTANCE;
  }

  SrcClass makeStub( String fqn, Symbol.ClassSymbol classSymbol, JCTree.JCCompilationUnit compilationUnit )
  {
    return makeStub( fqn, classSymbol, compilationUnit, true );
  }

  public SrcClass makeStub( String fqn, Symbol.ClassSymbol classSymbol, JCTree.JCCompilationUnit compilationUnit, boolean withMembers )
  {
    return makeSrcClass( fqn, classSymbol, compilationUnit, withMembers );
  }

  private SrcClass makeSrcClass( String fqn, Symbol.ClassSymbol classSymbol, JCTree.JCCompilationUnit compilationUnit, boolean withMembers )
  {
    SrcClass srcClass = new SrcClass( fqn, SrcClass.Kind.from( classSymbol.getKind() ) )
      .modifiers( classSymbol.getModifiers() );
    if( classSymbol.getEnclosingElement() instanceof Symbol.PackageSymbol && compilationUnit != null )
    {
      for( JCTree.JCImport imp : compilationUnit.getImports() )
      {
        if( imp.staticImport )
        {
          srcClass.addStaticImport( imp.getQualifiedIdentifier().toString() );
        }
        else
        {
          srcClass.addImport( imp.getQualifiedIdentifier().toString() );
        }
      }
    }
    addAnnotations( srcClass, classSymbol );
    for( Symbol.TypeVariableSymbol typeVar : classSymbol.getTypeParameters() )
    {
      srcClass.addTypeVar( makeTypeVarType( typeVar ) );
    }
    Type superclass = classSymbol.getSuperclass();
    if( !(superclass instanceof NoType) )
    {
      srcClass.superClass( makeNestedType( superclass ) );
    }
    for( Type iface : classSymbol.getInterfaces() )
    {
      srcClass.addInterface( makeNestedType( iface ) );
    }
    if( withMembers )
    {
      for( Symbol sym : classSymbol.getEnclosedElements() )
      {
        long modifiers = SrcAnnotated.modifiersFrom( sym.getModifiers() );
        if( Modifier.isPrivate( (int)modifiers ) )
        {
          continue;
        }

        if( sym instanceof Symbol.ClassSymbol )
        {
          addInnerClass( srcClass, sym );
        }
        else if( sym instanceof Symbol.VarSymbol )
        {
          addField( srcClass, sym );
        }
        else if( sym instanceof Symbol.MethodSymbol )
        {
          if( !isEnumMethod( sym ) )
          {
            addMethod( srcClass, (Symbol.MethodSymbol)sym );
          }
        }
      }
    }
    return srcClass;
  }

  private boolean isEnumMethod( Symbol sym )
  {
    return sym.getEnclosingElement().isEnum() &&
           (sym.toString().equals( "values()" ) || sym.toString().equals( "valueOf(java.lang.String)" ));
  }

  private SrcType makeNestedType( Type type )
  {
    String fqn = type.toString();
    Type enclosingType = type.getEnclosingType();
    SrcType srcType;
    if( enclosingType != null && !(enclosingType instanceof NoType) )
    {
      String simpleName = fqn.substring( enclosingType.toString().length() + 1 );
      srcType = new SrcType( simpleName );
      srcType.setEnclosingType( makeNestedType( enclosingType ) );
    }
    else
    {
      srcType = new SrcType( fqn );
    }
    return srcType;
  }

  private void addInnerClass( SrcClass srcClass, Symbol sym )
  {
    SrcClass innerClass = makeSrcClass( sym.getQualifiedName().toString(), (Symbol.ClassSymbol)sym, null, true );
    srcClass.addInnerClass( innerClass );
  }

  private void addField( SrcClass srcClass, Symbol sym )
  {
    Symbol.VarSymbol field = (Symbol.VarSymbol)sym;
    SrcField srcField = new SrcField( field.name.toString(), new SrcType( field.type.toString() ) );
    if( sym.isEnum() )
    {
      srcField.enumConst();
    }
    else
    {
      srcField.modifiers( field.getModifiers() );
      if( Modifier.isFinal( (int)srcField.getModifiers() ) )
      {
        srcField.initializer( new SrcRawExpression( getValueForType( sym.type ) ) );
      }
    }
    srcClass.addField( srcField );
  }

  private void addMethod( SrcClass srcClass, Symbol.MethodSymbol method )
  {
    SrcMethod srcMethod = new SrcMethod( srcClass );
    addAnnotations( srcMethod, method );
    srcMethod.modifiers( method.getModifiers() );
    if( (method.flags() & Flags.VARARGS) != 0 )
    {
      srcMethod.modifiers( srcMethod.getModifiers() | 0x00000080 ); // Modifier.VARARGS
    }
    String name = method.flatName().toString();
    if( name.equals( "<clinit>" ) )
    {
      return;
    }
    boolean isConstructor = name.equals( "<init>" );
    if( isConstructor )
    {
      srcMethod.name( srcClass.getSimpleName() );
      srcMethod.setConstructor( true );
    }
    else
    {
      srcMethod.name( name );
      srcMethod.returns( new SrcType( method.getReturnType().toString() ) );
    }
    for( Symbol.TypeVariableSymbol typeVar : method.getTypeParameters() )
    {
      srcMethod.addTypeVar( makeTypeVarType( typeVar ) );
    }
    for( Symbol.VarSymbol param : method.getParameters() )
    {
      SrcParameter srcParam = new SrcParameter( param.flatName().toString(), new SrcType( param.type.toString() ) );
      srcMethod.addParam( srcParam );
      addAnnotations( srcParam, param );
    }
    for( Type throwType : method.getThrownTypes() )
    {
      srcMethod.addThrowType( new SrcType( throwType.toString() ) );
    }
    srcMethod.body( new SrcStatementBlock()
                      .addStatement(
                        new SrcRawStatement()
                          .rawText( "throw new RuntimeException();" ) ) );
    srcClass.addMethod( srcMethod );
  }

  private void addAnnotations( SrcAnnotated<?> srcAnnotated, Symbol symbol )
  {
    for( Attribute.Compound annotationMirror : symbol.getAnnotationMirrors() )
    {
      SrcAnnotationExpression annoExpr = new SrcAnnotationExpression( annotationMirror.getAnnotationType().toString() );
      for( Pair<Symbol.MethodSymbol, Attribute> value : annotationMirror.values )
      {
        annoExpr.addArgument( value.fst.flatName().toString(), new SrcType( value.snd.type.toString() ), value.snd.getValue() );
      }
      srcAnnotated.addAnnotation( annoExpr );
    }
  }

  private SrcType makeTypeVarType( Symbol.TypeVariableSymbol typeVar )
  {
    StringBuilder sb = new StringBuilder( typeVar.type.toString() );
    Type lowerBound = typeVar.type.getLowerBound();
    if( lowerBound != null && !(lowerBound instanceof NullType) )
    {
      sb.append( " super " ).append( lowerBound.toString() );
    }
    else
    {
      Type upperBound = typeVar.type.getUpperBound();
      if( upperBound != null && !(upperBound instanceof NoType) && !upperBound.toString().equals( Object.class.getName() ) )
      {
        sb.append( " extends " ).append( upperBound.toString() );
      }
    }
    return new SrcType( sb.toString() );
  }

  private String getValueForType( Type type )
  {
    if( type.toString().equals( "boolean" ) )
    {
      return "false";
    }
    else if( type.isPrimitive() )
    {
      return "0";
    }
    else
    {
      return "null";
    }
  }

}