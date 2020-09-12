package com.cliffc.aa.node;

import com.cliffc.aa.AA;
import com.cliffc.aa.Env;
import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.Parse;
import com.cliffc.aa.type.*;
import com.cliffc.aa.util.Ary;

// Primitives can be used as an internal operator (their apply() call does the
// primitive operation).  Primitives are wrapped as functions when returned
// from Env lookup, although the immediate lookup+apply is optimized to just
// make a new primitive.  See FunNode for function Node structure.
//
// Fun/Parm-per-arg/Prim/Ret
//
public abstract class PrimNode extends Node {
  public final String _name;    // Unique name (and program bits)
  final TypeFunSig _sig;        // Argument types; 0 is display, 1 is 1st real arg
  Parse[] _badargs;             // Filled in when inlined in CallNode
  byte _op_prec;                // Operator precedence, computed from table.  Generally 1-9.
  public boolean _thunk_rhs;    // Thunk (delay) right-hand-argument.
  PrimNode( String name, TypeStruct formals, Type ret ) {
    super(OP_PRIM);
    _name=name;
    assert formals.at(0)==TypeFunPtr.NO_DISP; // Room for no closure
    _sig=TypeFunSig.make(formals,ret);
    _badargs=null;
    _op_prec = -1;              // Not set yet
    _thunk_rhs=false;
  }
  private static PrimNode[] PRIMS = null; // All primitives
  static PrimNode[][] PRECEDENCE = null;  // Just the binary operators, grouped by precedence
  public static void reset() { PRIMS=null; }

  public static PrimNode[] PRIMS() {
    if( PRIMS!=null ) return PRIMS;

    // Binary-operator primitives, sorted by precedence
    PRECEDENCE = new PrimNode[][]{

      {new MemPrimNode.ReadPrimNode.LValueLength(), }, // The other array ops are "balanced ops" and use term() for precedence

      {new MinusF64(), new MinusI64(), new Not(), },

      {new MulF64(), new DivF64(), new MulI64(), new DivI64(), new ModI64(), },

      {new AddF64(), new SubF64(), new AddI64(), new SubI64() },

      {new LT_F64(), new LE_F64(), new GT_F64(), new GE_F64(),
       new LT_I64(), new LE_I64(), new GT_I64(), new GE_I64(),},

      {new EQ_F64(), new NE_F64(), new EQ_I64(), new NE_I64(), new EQ_OOP(), new NE_OOP(), },

      {new AndI64(), },

      {new OrI64(), },

      {new AndThen(), },
      //
      //{new OrElse(), },

    };

    // Other primitives, not binary operators
    PrimNode[] others = new PrimNode[] {
      // These are called like a function
      new RandI64(),
      new Id(TypeMemPtr.ISUSED0), // Pre-split OOP from non-OOP
      new Id(TypeFunPtr.GENERIC_FUNPTR),
      new Id(Type.REAL),

      new ConvertInt64F64(),
      new ConvertStrStr(),

      // These are balanced-ops, called by Parse.term()
      new MemPrimNode.ReadPrimNode.LValueRead  (), // Read  an L-Value: (ary,idx) ==> elem
      new MemPrimNode.ReadPrimNode.LValueWrite (), // Write an L-Value: (ary,idx,elem) ==> elem
      new MemPrimNode.ReadPrimNode.LValueWriteFinal(), // Final Write an L-Value: (ary,idx,elem) ==> elem
    };

    Ary<PrimNode> allprims = new Ary<>(others);
    for( PrimNode[] prims : PRECEDENCE )
      for( PrimNode prim : prims )
        allprims.push(prim);
    PRIMS = allprims.asAry();

    // Compute precedence from table
    int max_prec = PRECEDENCE.length;
    for( int p=0; p<PRECEDENCE.length; p++ )
      for( PrimNode n : PRECEDENCE[p] )
        n._op_prec = (byte)(max_prec-p);

    return PRIMS;
  }

  public static PrimNode convertTypeName( Type from, Type to, Parse badargs ) {
    return new ConvertTypeName(from,to,badargs);
  }

  // Apply types are 1-based (same as the incoming node index), and not
  // zero-based (not same as the _formals and _args fields).
  public abstract Type apply( Type[] args ); // Execute primitive
  @Override public String xstr() { return _name+":"+_sig._formals.at(1); }
  @Override public Node ideal(GVNGCM gvn, int level) { return null; }
  @Override public Type value(GVNGCM.Mode opt_mode) {
    Type[] ts = new Type[_defs._len]; // 1-based
    // If all inputs are constants we constant fold.  If any input is high, we
    // return high otherwise we return low.
    boolean is_con = true, has_high = false;
    for( int i=1; i<_defs._len; i++ ) { // first is control
      Type tactual = val(i);
      Type tformal = _sig.arg(i);
      Type t = tformal.dual().meet(ts[i] = tactual);
      if( !t.is_con() ) {
        is_con = false;         // Some non-constant
        if( t.above_center() ) has_high=true;
      }
    }
    return is_con ? apply(ts) : (has_high ? _sig._ret.dual() : _sig._ret);
  }
  @Override public ErrMsg err( boolean fast ) {
    for( int i=1; i<_defs._len; i++ ) { // first is control
      Type tactual = val(i);
      Type tformal = _sig.arg(i).simple_ptr();
      if( !tactual.isa(tformal) )
        return _badargs==null ? ErrMsg.BADARGS : ErrMsg.typerr(_badargs[i],tactual,null,tformal);
    }
    return null;
  }
  // Prims are equal for same-name-same-signature (and same inputs).
  // E.g. float-minus of x and y is NOT the same as int-minus of x and y
  // despite both names being '-'.
  @Override public int hashCode() { return super.hashCode()+_name.hashCode()+_sig._hash; }
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    if( !super.equals(o) ) return false;
    if( !(o instanceof PrimNode) ) return false;
    PrimNode p = (PrimNode)o;
    return _name.equals(p._name) && _sig==p._sig;
  }

  // Called during basic Env creation and making of type constructors, this
  // wraps a PrimNode as a full 1st-class function to be passed about or
  // assigned to variables.
  public FunPtrNode as_fun( GVNGCM gvn ) {
    _defs.clear();  _uses.clear();
    FunNode fun = (FunNode) gvn.xform(new  FunNode(this).add_def(Env.ALL_CTRL)); // Points to ScopeNode only
    Node rpc = gvn.xform(new ParmNode(-1,"rpc",fun,gvn.con(TypeRPC.ALL_CALL),null));
    Node mem = gvn.xform(new ParmNode(-2,"mem",fun,TypeMem.MEM,Env.DEFMEM,null));
    add_def(_thunk_rhs ? fun : null);   // Control for the primitive in slot 0
    if( _thunk_rhs ) add_def(mem);      // Memory if thunking
    for( int i=1; i<_sig.nargs(); i++ ) // First is display
      add_def(gvn.xform(new ParmNode(i,_sig.fld(i),fun, gvn.con(_sig.arg(i).simple_ptr()),null)));
    gvn.init(this);
    Node ctl,rez;
    if( _thunk_rhs ) {
      ctl = gvn.xform(new CProjNode(this,0));
      mem = gvn.xform(new MProjNode(this,1));
      rez = gvn.xform(new  ProjNode(2,this));
    } else {
      ctl = fun;
      rez = this;
    }
    // Functions return the set of *modified* memory.  Most PrimNodes never
    // *modify* memory (see Intrinsic*Node for some primitives that *modify*
    // memory).  Thunking (short circuit) prims return both memory and a value.
    RetNode ret = (RetNode)gvn.xform(new RetNode(ctl,mem,rez,rpc,fun));
    // No closures are added to primitives
    return new FunPtrNode(ret,gvn.con(TypeFunPtr.NO_DISP));
  }


  // --------------------
  // Default name constructor using a single tuple type
  static class ConvertTypeName extends PrimNode {
    ConvertTypeName(Type from, Type to, Parse badargs) {
      super(to._name,TypeStruct.make_args(TypeStruct.ts(TypeStruct.NO_DISP,from)),to);
      _badargs = new Parse[]{badargs};
    }
    @Override public Type value(GVNGCM.Mode opt_mode) {
      Type[] ts = new Type[_defs._len];
      for( int i=1; i<_defs._len; i++ )
        ts[i] = _defs.at(i)._val;
      return apply(ts);     // Apply (convert) even if some args are not constant
    }
    @Override public Type apply( Type[] args ) {
      Type actual = args[1];
      if( actual==Type.ANY || actual==Type.ALL ) return actual;
      Type formal = _sig.arg(1);
      // Wrapping function will not inline if args are in-error
      assert formal.dual().isa(actual) && actual.isa(formal);
      return actual.set_name(_sig._ret._name);
    }
    @Override public ErrMsg err( boolean fast ) {
      Type actual = val(1);
      Type formal = _sig.arg(1);
      if( !actual.isa(formal) ) // Actual is not a formal
        return ErrMsg.typerr(_badargs[0],actual,null,formal);
      return null;
    }
  }

  static class ConvertInt64F64 extends PrimNode {
    ConvertInt64F64() { super("flt64",TypeStruct.INT64,TypeFlt.FLT64); }
    @Override public Type apply( Type[] args ) { return TypeFlt.con((double)args[1].getl()); }
  }

  // TODO: Type-check strptr input args
  static class ConvertStrStr extends PrimNode {
    ConvertStrStr() { super("str",TypeStruct.STRPTR,TypeMemPtr.OOP); }
    @Override public Node ideal(GVNGCM gvn, int level) { return in(1); }
    @Override public Type value(GVNGCM.Mode opt_mode) { return val(1); }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
  }

  // 1Ops have uniform input/output types, so take a shortcut on name printing
  abstract static class Prim1OpF64 extends PrimNode {
    Prim1OpF64( String name ) { super(name,TypeStruct.FLT64,TypeFlt.FLT64); }
    public Type apply( Type[] args ) { return TypeFlt.con(op(args[1].getd())); }
    abstract double op( double d );
  }

  static class MinusF64 extends Prim1OpF64 {
    MinusF64() { super("-"); }
    @Override double op( double d ) { return -d; }
  }

  // 1Ops have uniform input/output types, so take a shortcut on name printing
  abstract static class Prim1OpI64 extends PrimNode {
    Prim1OpI64( String name ) { super(name,TypeStruct.INT64,TypeInt.INT64); }
    @Override public Type apply( Type[] args ) { return TypeInt.con(op(args[1].getl())); }
    abstract long op( long d );
  }

  static class MinusI64 extends Prim1OpI64 {
    MinusI64() { super("-"); }
    @Override long op( long x ) { return -x; }
  }

  // 2Ops have uniform input/output types, so take a shortcut on name printing
  abstract static class Prim2OpF64 extends PrimNode {
    Prim2OpF64( String name ) { super(name,TypeStruct.FLT64_FLT64,TypeFlt.FLT64); }
    @Override public Type apply( Type[] args ) { return TypeFlt.con(op(args[1].getd(),args[2].getd())); }
    abstract double op( double x, double y );
  }

  static class AddF64 extends Prim2OpF64 {
    AddF64() { super("+"); }
    double op( double l, double r ) { return l+r; }
  }

  static class SubF64 extends Prim2OpF64 {
    SubF64() { super("-"); }
    double op( double l, double r ) { return l-r; }
  }

  static class MulF64 extends Prim2OpF64 {
    MulF64() { super("*"); }
    @Override double op( double l, double r ) { return l*r; }
  }

  static class DivF64 extends Prim2OpF64 {
    DivF64() { super("/"); }
    @Override double op( double l, double r ) { return l/r; }
  }

  // 2RelOps have uniform input types, and bool output
  abstract static class Prim2RelOpF64 extends PrimNode {
    Prim2RelOpF64( String name ) { super(name,TypeStruct.FLT64_FLT64,TypeInt.BOOL); }
    @Override public Type apply( Type[] args ) { return op(args[1].getd(),args[2].getd())?TypeInt.TRUE:TypeInt.FALSE; }
    abstract boolean op( double x, double y );
  }

  static class LT_F64 extends Prim2RelOpF64 { LT_F64() { super("<" ); } boolean op( double l, double r ) { return l< r; } }
  static class LE_F64 extends Prim2RelOpF64 { LE_F64() { super("<="); } boolean op( double l, double r ) { return l<=r; } }
  static class GT_F64 extends Prim2RelOpF64 { GT_F64() { super(">" ); } boolean op( double l, double r ) { return l> r; } }
  static class GE_F64 extends Prim2RelOpF64 { GE_F64() { super(">="); } boolean op( double l, double r ) { return l>=r; } }
  static class EQ_F64 extends Prim2RelOpF64 { EQ_F64() { super("=="); } boolean op( double l, double r ) { return l==r; } }
  static class NE_F64 extends Prim2RelOpF64 { NE_F64() { super("!="); } boolean op( double l, double r ) { return l!=r; } }


  // 2Ops have uniform input/output types, so take a shortcut on name printing
  abstract static class Prim2OpI64 extends PrimNode {
    Prim2OpI64( String name ) { super(name,TypeStruct.INT64_INT64,TypeInt.INT64); }
    @Override public Type apply( Type[] args ) { return TypeInt.con(op(args[1].getl(),args[2].getl())); }
    abstract long op( long x, long y );
  }

  static class AddI64 extends Prim2OpI64 {
    AddI64() { super("+"); }
    @Override long op( long l, long r ) { return l+r; }
  }

  static class SubI64 extends Prim2OpI64 {
    SubI64() { super("-"); }
    @Override long op( long l, long r ) { return l-r; }
  }

  static class MulI64 extends Prim2OpI64 {
    MulI64() { super("*"); }
    @Override long op( long l, long r ) { return l*r; }
  }

  static class DivI64 extends Prim2OpI64 {
    DivI64() { super("/"); }
    @Override long op( long l, long r ) { return l/r; } // Long division
  }

  static class ModI64 extends Prim2OpI64 {
    ModI64() { super("%"); }
    @Override long op( long l, long r ) { return l%r; }
  }

  static class AndI64 extends Prim2OpI64 {
    AndI64() { super("&"); }
    // And can preserve bit-width
    @Override public Type value(GVNGCM.Mode opt_mode) {
      Type t1 = val(1), t2 = val(2);
      // 0 AND anything is 0
      if( t1 == Type. NIL || t2 == Type. NIL ) return Type. NIL;
      if( t1 == Type.XNIL || t2 == Type.XNIL ) return Type.XNIL;
      // If either is high - results might fall to something reasonable
      if( t1.above_center() || t2.above_center() )
        return TypeInt.INT64.dual();
      // Both are low-or-constant, and one is not valid - return bottom result
      if( !t1.isa(TypeInt.INT64) || !t2.isa(TypeInt.INT64) )
        return TypeInt.INT64;
      // If both are constant ints, return the constant math.
      if( t1.is_con() && t2.is_con() )
        return TypeInt.con(t1.getl() & t2.getl());
      if( !(t1 instanceof TypeInt) || !(t2 instanceof TypeInt) )
        return TypeInt.INT64;
      // Preserve width
      return ((TypeInt)t1).minsize((TypeInt)t2);
    }
    @Override long op( long l, long r ) { return l&r; }
  }

  static class OrI64 extends Prim2OpI64 {
    OrI64() { super("|"); }
    // And can preserve bit-width
    @Override public Type value(GVNGCM.Mode opt_mode) {
      Type t1 = val(1), t2 = val(2);
      // 0 OR anything is that thing
      if( t1 == Type.NIL || t1 == Type.XNIL ) return t2;
      if( t2 == Type.NIL || t2 == Type.XNIL ) return t1;
      // If either is high - results might fall to something reasonable
      if( t1.above_center() || t2.above_center() )
        return TypeInt.INT64.dual();
      // Both are low-or-constant, and one is not valid - return bottom result
      if( !t1.isa(TypeInt.INT64) || !t2.isa(TypeInt.INT64) )
        return TypeInt.INT64;
      // If both are constant ints, return the constant math.
      if( t1.is_con() && t2.is_con() )
        return TypeInt.con(t1.getl() | t2.getl());
      if( !(t1 instanceof TypeInt) || !(t2 instanceof TypeInt) )
        return TypeInt.INT64;
      // Preserve width
      return ((TypeInt)t1).maxsize((TypeInt)t2);
    }
    @Override long op( long l, long r ) { return l&r; }
  }

  // 2RelOps have uniform input types, and bool output
  abstract static class Prim2RelOpI64 extends PrimNode {
    Prim2RelOpI64( String name ) { super(name,TypeStruct.INT64_INT64,TypeInt.BOOL); }
    @Override public Type apply( Type[] args ) { return op(args[1].getl(),args[2].getl())?TypeInt.TRUE:TypeInt.FALSE; }
    abstract boolean op( long x, long y );
  }

  static class LT_I64 extends Prim2RelOpI64 { LT_I64() { super("<" ); } boolean op( long l, long r ) { return l< r; } }
  static class LE_I64 extends Prim2RelOpI64 { LE_I64() { super("<="); } boolean op( long l, long r ) { return l<=r; } }
  static class GT_I64 extends Prim2RelOpI64 { GT_I64() { super(">" ); } boolean op( long l, long r ) { return l> r; } }
  static class GE_I64 extends Prim2RelOpI64 { GE_I64() { super(">="); } boolean op( long l, long r ) { return l>=r; } }
  static class EQ_I64 extends Prim2RelOpI64 { EQ_I64() { super("=="); } boolean op( long l, long r ) { return l==r; } }
  static class NE_I64 extends Prim2RelOpI64 { NE_I64() { super("!="); } boolean op( long l, long r ) { return l!=r; } }


  static class EQ_OOP extends PrimNode {
    EQ_OOP() { super("==",TypeStruct.OOP_OOP,TypeInt.BOOL); }
    @Override public Type value(GVNGCM.Mode opt_mode) {
      // Oop-equivalence is based on pointer-equivalence NOT on a "deep equals".
      // Probably need a java-like "===" vs "==" to mean deep-equals.  You are
      // equals if your inputs are the same node, and you are unequals if your
      // input is 2 different NewNodes (or casts of NewNodes).  Otherwise you
      // have to do the runtime test.
      if( in(1)==in(2) ) return TypeInt.TRUE;
      Node nn1 = in(1).in(0), nn2 = in(2).in(0);
      if( nn1 instanceof NewNode &&
          nn2 instanceof NewNode &&
          nn1 != nn2 ) return TypeInt.FALSE;
      // Constants can only do nil-vs-not-nil, since e.g. two strings "abc" and
      // "abc" are equal constants in the type system but can be two different
      // string pointers.
      Type t1 = val(1);
      Type t2 = val(2);
      if( t1==Type.NIL || t1==Type.XNIL ) return vs_nil(t2,TypeInt.TRUE,TypeInt.FALSE);
      if( t2==Type.NIL || t2==Type.XNIL ) return vs_nil(t1,TypeInt.TRUE,TypeInt.FALSE);
      if( t1.above_center() || t2.above_center() ) return TypeInt.BOOL.dual();
      return TypeInt.BOOL;
    }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
    static Type vs_nil( Type tx, Type t, Type f ) {
      if( tx==Type.NIL || tx==Type.XNIL ) return t;
      if( tx.above_center() ) return tx.isa(Type.NIL) ? TypeInt.BOOL.dual() : f;
      return tx.must_nil() ? TypeInt.BOOL : f;
    }
  }

  static class NE_OOP extends PrimNode {
    NE_OOP() { super("!=",TypeStruct.OOP_OOP,TypeInt.BOOL); }
    @Override public Type value(GVNGCM.Mode opt_mode) {
      // Oop-equivalence is based on pointer-equivalence NOT on a "deep equals".
      // Probably need a java-like "===" vs "==" to mean deep-equals.  You are
      // equals if your inputs are the same node, and you are unequals if your
      // input is 2 different NewNodes (or casts of NewNodes).  Otherwise you
      // have to do the runtime test.
      if( in(1)==in(2) ) return TypeInt.FALSE;
      Node nn1 = in(1).in(0), nn2 = in(2).in(0);
      if( nn1 instanceof NewNode &&
          nn2 instanceof NewNode &&
          nn1 != nn2 ) return TypeInt.TRUE;
      // Constants can only do nil-vs-not-nil, since e.g. two strings "abc" and
      // "abc" are equal constants in the type system but can be two different
      // string pointers.
      Type t1 = val(1);
      Type t2 = val(2);
      if( t1==Type.NIL || t1==Type.XNIL ) return EQ_OOP.vs_nil(t2,TypeInt.FALSE,TypeInt.TRUE);
      if( t2==Type.NIL || t2==Type.XNIL ) return EQ_OOP.vs_nil(t1,TypeInt.FALSE,TypeInt.TRUE);
      if( t1.above_center() || t2.above_center() ) return TypeInt.BOOL.dual();
      return TypeInt.BOOL;
    }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
  }


  static class Not extends PrimNode {
    // Rare function which takes a Scalar (works for both ints and ptrs)
    Not() { super("!",TypeStruct.SCALAR1,TypeInt.BOOL); }
    @Override public Type value(GVNGCM.Mode opt_mode) {
      Type t = val(1);
      if( t== Type.XNIL ||
          t== Type. NIL ||
          t== TypeInt.ZERO )
        return TypeInt.TRUE;
      if( t. may_nil() ) return TypeInt.BOOL.dual();
      if( t.must_nil() ) return TypeInt.BOOL;
      return Type.XNIL;           // Cannot be a nil, so return a nil
    }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
  }

  static class RandI64 extends PrimNode {
    RandI64() { super("math_rand",TypeStruct.INT64,TypeInt.INT64); }
    @Override public Type value(GVNGCM.Mode opt_mode) {
      Type t = val(1);
      if( t.above_center() ) return TypeInt.BOOL.dual();
      if( TypeInt.INT64.dual().isa(t) && t.isa(TypeInt.INT64) )
        return t.meet(TypeInt.FALSE);
      return t.oob(TypeInt.INT64);
    }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
    // Rands have hidden internal state; 2 Rands are never equal
    @Override public boolean equals(Object o) { return this==o; }
  }

  static class Id extends PrimNode {
    Id(Type arg) { super("id",TypeStruct.make_args(TypeStruct.ts(TypeStruct.NO_DISP,arg)),arg); }
    @Override public Node ideal(GVNGCM gvn, int level) { return in(1); }
    @Override public Type value(GVNGCM.Mode opt_mode) { return val(1); }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
  }

  static class AndThen extends PrimNode {
    // Takes a value on the LHS, and a THUNK on the RHS.
    AndThen() { super("&&",TypeStruct.ANDTHEN,Type.SCALAR); _thunk_rhs=true; }
    // Expect this to inline everytime
    @Override public Node ideal(GVNGCM gvn, int level) {
      if( _defs._len != 4 ) return null; // Already did this
      Node ctl = in(0);
      Node mem = in(1);
      Node lhs = in(2);
      Node rhs = in(3);
      // Expand to if/then/else
      Node iff = gvn.xform(new IfNode(ctl,lhs));
      Node fal = gvn.xform(new CProjNode(iff,0));
      Node tru = gvn.xform(new CProjNode(iff,1));
      // Unthunk (call) on true branch
      Node tal = gvn.xform(new ThallNode(tru,mem,rhs));
      Node ccc = gvn.xform(new CProjNode(tal,0));
      Node memc= gvn.xform(new MProjNode(tal,1));
      Node rez = gvn.xform(new  ProjNode(2,tal));
      // Region merging results
      Node reg = gvn.init (new RegionNode(null,fal,ccc));
      Node phi = gvn.xform(new PhiNode(Type.SCALAR,null,reg,lhs,rez ));
      Node phim= gvn.xform(new PhiNode(TypeMem.MEM,null,reg,mem,memc ));
      // Plug into self & trigger is_copy
      set_def(0,reg ,gvn);
      set_def(1,phim,gvn);
      set_def(2,phi ,gvn);
      pop();                    // Remove arg2, trigger is_copy
      return this;
    }
    @Override public Type value(GVNGCM.Mode opt_mode) {
      return TypeTuple.RET;
    }
    @Override public TypeMem live_use(GVNGCM.Mode opt_mode, Node def ) {
      return def==in(1) ? _live : TypeMem.ALIVE; // Basic aliveness, except for memory
    }
    @Override public TypeMem all_live() { return TypeMem.ALLMEM; }
    @Override public TypeInt apply( Type[] args ) { throw AA.unimpl(); }
    @Override public Node is_copy(GVNGCM gvn, int idx) {
      return _defs._len==4 ? null : in(idx);
    }
  }
}
