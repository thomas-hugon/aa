package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.Type;
import com.cliffc.aa.type.TypeMem;
import com.cliffc.aa.type.TypeTuple;

// Thall: call of a thunk
// See Thret (Return), Thunk (Fun).

// This calls a single thunk only, determined after Parse.expr().
// Takes in Control, Memory & Thunk.
// Produces Control, Memory & Value.
public class ThallNode extends Node {
  public ThallNode( Node ctrl, Node mem, Node thunk ) { super(OP_THALL,ctrl,mem,thunk); }
  @Override public Node ideal(GVNGCM gvn, int level) {
    if( !(in(2) instanceof ThunkNode) ) return null;
    throw com.cliffc.aa.AA.unimpl();
  }
  @Override public Type value(GVNGCM.Mode opt_mode) {
    return TypeTuple.RET;
  }
  @Override public TypeMem all_live() { return TypeMem.ALLMEM; }
  @Override public TypeMem live_use(GVNGCM.Mode opt_mode, Node def ) {
    return def==in(1) ? _live : TypeMem.ALIVE; // Basic aliveness, except for memory
  }
}
