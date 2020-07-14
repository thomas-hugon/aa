package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.Type;
import com.cliffc.aa.type.TypeMem;

// Proj control
public class CProjNode extends ProjNode {
  public CProjNode( Node ifn, int idx ) { this(OP_CPROJ,ifn,idx); }
  public CProjNode( byte op, Node ifn, int idx ) { super(op,ifn,idx); }
  @Override String xstr() {
    if( !is_dead() && in(0) instanceof IfNode )
      return _idx==0 ? "False" : "True";
    return "CProj"+_idx;
  }
  @Override public Node ideal(GVNGCM gvn, int level) { return in(0).is_copy(gvn,_idx); }
  @Override public Type value(GVNGCM gvn) {
    Type x = super.value(gvn);
    if( x==Type.ANY ) return Type.XCTRL;
    if( x==Type.ALL ) return Type. CTRL;
    return x;
  }
  @Override public TypeMem live_use( GVNGCM gvn, Node def ) { return def.basic_liveness() ? TypeMem.ALIVE : TypeMem.ANYMEM; }
  // Return the op_prec of the returned value.  Not sensible except
  // when call on primitives.
  @Override public byte op_prec() { return _defs.at(0).op_prec(); }

}
