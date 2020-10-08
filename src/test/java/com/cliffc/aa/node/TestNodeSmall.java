package com.cliffc.aa.node;

import com.cliffc.aa.Env;
import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestNodeSmall {


  @Test public void testUnresolvedAdd() {
    Env top = Env.top_scope();
    GVNGCM gvn = Env.GVN;

    // Current theory on Unresolved:  Call.resolve moves closer to the centerline:
    //   LOW  fidxs are all must-calls, and removing one LIFTS .  If args are MEET, removing LIFTS.
    //   HIGH fidxs are all may -calls, and removing one LOWERS.  If args are JOIN, removing LOWERS.

    // Phi: always MEET, so highs meet to empty; and lows MEET.
    // During GCP fidxs are high, then move to low.
    // Call.resolve: choices get removed which lifts or lowers

    // WANT: During GCP, high choices fed to Call.resolve.  Choices get removed, which LOWERS resolved.
    //       Means Unresolved during GCP produces only HIGH choices.
    // WANT: During Iter, low choices fed to Call.resolve.  Choices get removed, which LIFTS  resolved.
    //       Means Unresolved during ITER produces only LOW choices.
    // WANT: Same behavior during GCP and ITER, but doesn't appear I can have that.

    // Unresolved: ITER: all is MEET and no FIDX goes HIGH (except for dead/dying).

    // Unresolved: GCP : if FunPtr above center optional to ignore or JOIN.
    //                   if FunPtr below center, flip to high and JOIN.  Also high/ignore args kept high, and low args moved high for JOIN.
    // Kinda sorta looks like: use startype on incoming, and JOIN.

    gvn._opt_mode=GVNGCM.Mode.Parse;
    UnresolvedNode uadd = (UnresolvedNode)top.lookup("+"); // {int int -> int} and {flt flt -> flt} and {str str -> str}
    FunPtrNode aflt = (FunPtrNode)uadd.in(0);
    FunPtrNode aint = (FunPtrNode)uadd.in(1);
    FunPtrNode astr = (FunPtrNode)uadd.in(2);
    // Make a flt/int combo, drops off string.
    UnresolvedNode anum = (UnresolvedNode)gvn.xform(new UnresolvedNode(null,aflt,aint));

    // All nodes have this property: START >= {ALL.dual(),value(START)} >= value(ALL.dual()) >= value(ALL) >= ALL
    // Holds for both ITER and GCP.


    // Compute Node.all_type() and all_type.startype()
    Type uaddALL = uadd._val, uaddSTART = Type.ANY;
    Type anumALL = anum._val, anumSTART = Type.ANY;
    Type afltALL = aflt._val, afltSTART = Type.ANY;
    Type aintALL = aint._val, aintSTART = Type.ANY;
    Type astrALL = astr._val, astrSTART = Type.ANY;

    // Compute Node.value() where initial GVN is startype()
    uadd._val = uaddSTART;
    anum._val = anumSTART;
    aflt._val = afltSTART;
    aint._val = aintSTART;
    astr._val = astrSTART;
    gvn._opt_mode=GVNGCM.Mode.PesiNoCG;
    Type uaddVAL1START = uadd.value(gvn._opt_mode);
    Type anumVAL1START = anum.value(gvn._opt_mode);
    Type afltVAL1START = aflt.value(gvn._opt_mode);
    Type aintVAL1START = aint.value(gvn._opt_mode);
    Type astrVAL1START = astr.value(gvn._opt_mode);
    gvn._opt_mode=GVNGCM.Mode.Opto;
    Type uaddVAL2START = uadd.value(gvn._opt_mode);
    Type anumVAL2START = anum.value(gvn._opt_mode);
    Type afltVAL2START = aflt.value(gvn._opt_mode);
    Type aintVAL2START = aint.value(gvn._opt_mode);
    Type astrVAL2START = astr.value(gvn._opt_mode);

    // Compute Node.value() where initial GVN is all_type.dual()
    uadd._val = uaddALL.dual();
    anum._val = anumALL.dual();
    aflt._val = afltALL.dual();
    aint._val = aintALL.dual();
    astr._val = astrALL.dual();
    gvn._opt_mode=GVNGCM.Mode.PesiNoCG;
    Type uaddVAL1XALL = uadd.value(gvn._opt_mode);
    Type anumVAL1XALL = anum.value(gvn._opt_mode);
    Type afltVAL1XALL = aflt.value(gvn._opt_mode);
    Type aintVAL1XALL = aint.value(gvn._opt_mode);
    Type astrVAL1XALL = astr.value(gvn._opt_mode);
    gvn._opt_mode=GVNGCM.Mode.Opto;
    Type uaddVAL2XALL = uadd.value(gvn._opt_mode);
    Type anumVAL2XALL = anum.value(gvn._opt_mode);
    Type afltVAL2XALL = aflt.value(gvn._opt_mode);
    Type aintVAL2XALL = aint.value(gvn._opt_mode);
    Type astrVAL2XALL = astr.value(gvn._opt_mode);

    // Compute Node.value() where initial GVN is all_type()
    uadd._val = uaddALL;
    anum._val = uaddALL;
    aflt._val = afltALL;
    aint._val = aintALL;
    astr._val = astrALL;
    gvn._opt_mode=GVNGCM.Mode.PesiNoCG;
    Type uaddVAL1ALL = uadd.value(gvn._opt_mode);
    Type anumVAL1ALL = anum.value(gvn._opt_mode);
    Type afltVAL1ALL = aflt.value(gvn._opt_mode);
    Type aintVAL1ALL = aint.value(gvn._opt_mode);
    Type astrVAL1ALL = astr.value(gvn._opt_mode);
    gvn._opt_mode=GVNGCM.Mode.Opto;
    Type uaddVAL2ALL = uadd.value(gvn._opt_mode);
    Type anumVAL2ALL = anum.value(gvn._opt_mode);
    Type afltVAL2ALL = aflt.value(gvn._opt_mode);
    Type aintVAL2ALL = aint.value(gvn._opt_mode);
    Type astrVAL2ALL = astr.value(gvn._opt_mode);

    Type[] uadds = new Type[]{uaddSTART,uaddALL.dual(),uaddVAL1START,uaddVAL2START,uaddVAL1XALL,uaddVAL2XALL,uaddVAL1ALL,uaddVAL2ALL,uaddALL};
    Type[] anums = new Type[]{anumSTART,anumALL.dual(),anumVAL1START,anumVAL2START,anumVAL1XALL,anumVAL2XALL,anumVAL1ALL,anumVAL2ALL,anumALL};
    Type[] aflts = new Type[]{afltSTART,afltALL.dual(),afltVAL1START,afltVAL2START,afltVAL1XALL,afltVAL2XALL,afltVAL1ALL,afltVAL2ALL,afltALL};
    Type[] aints = new Type[]{aintSTART,aintALL.dual(),aintVAL1START,aintVAL2START,aintVAL1XALL,aintVAL2XALL,aintVAL1ALL,aintVAL2ALL,aintALL};
    Type[] astrs = new Type[]{astrSTART,astrALL.dual(),astrVAL1START,astrVAL2START,astrVAL1XALL,astrVAL2XALL,astrVAL1ALL,astrVAL2ALL,astrALL};
    Type[][] tfpss = new Type[][]{aflts,aints,astrs,anums,uadds};

    // All nodes have these properties:
    //    START >= {ALL.dual(),value1(START)} >= value1(ALL.dual()) >= value1(ALL) >= ALL
    //    START >= {ALL.dual(),value2(START)} >= value2(ALL.dual()) >= value2(ALL) >= ALL
    for( Type[] tfps : tfpss ) {
      Type start = tfps[0], xall  = tfps[1], val1s = tfps[2], val2s = tfps[3];
      Type val1x = tfps[4], val2x = tfps[5], val1a = tfps[6], val2a = tfps[7];
      Type all   = tfps[8];

      assertTrue(start.isa(xall ));
      assertTrue(start.isa(val1s));   assertTrue(start.isa(val2s));
      assertTrue(xall .isa(val1x));   assertTrue(xall .isa(val2x));
      assertTrue(val1s.isa(val1x));   assertTrue(val2s.isa(val2x));
      assertTrue(val1x.isa(val1a));   assertTrue(val2x.isa(val2a));
      assertTrue(val1a.isa(all  ));   assertTrue(val2a.isa(all  ));
    }

    // Also, for CallNode.resolve we want the properties:
    //    UnresolvedAdd.XAll >= AnumAdd.Xall} >= {Flt,Int,Str}XAdd.All-- Removing a choice during GCP  lowers
    //    {Flt,Int,Str}Add.ALL >= AnumAdd.ALL >= UnresolvedAdd.All  -- Removing a choice during ITER lifts
    assertTrue(uaddALL.dual().isa(anumALL.dual()));
    assertTrue(anumALL.dual().isa(afltALL.dual()));
    assertTrue(anumALL.dual().isa(aintALL.dual()));
    assertTrue(uaddALL.dual().isa(astrALL.dual()));

    assertTrue(astrALL.isa(uaddALL));
    assertTrue(aintALL.isa(anumALL));
    assertTrue(afltALL.isa(anumALL));
    assertTrue(anumALL.isa(uaddALL));

  }

  private Type[] _testMonotonicChain(Node[] ins, Node n, TypeTuple[] argss) {
    GVNGCM gvn = Env.GVN;

    // First validate the test itself.  If two tuples are 'isa' then the result
    // is also 'isa'.
    int len = argss.length;
    int num = argss[0]._ts.length;
    for( int i=0; i<len; i++ ) {
      TypeTuple tti = argss[i];
      midloop:
      for( int j=i+1; j<len; j++ ) { // Triangulate
        TypeTuple ttj = argss[j];
        for( int k=0; k<num-1; k++ )
          if( !tti.at(k).isa(ttj.at(k)) ) // All elements except last are 'isa'
            continue midloop;
        Type ttiN = tti.at(num-1); // Then check last element is 'isa'
        Type ttjN = ttj.at(num-1);
        assertTrue("Test is broken: "+tti+" isa "+ttj+", but "+ttiN+" !isa "+ttjN,ttiN.isa(ttjN));
      }
    }


    // Now call Node.value() call, and compare to expected
    TypeTuple[] tns= new TypeTuple[argss.length];
    for( int i=0; i<argss.length; i++ ) {
      for( int j=0; j<ins.length; j++ )
        ins[j]._val = argss[i].at(j);
      tns[i] = (TypeTuple)n.value(gvn._opt_mode);
    }
    // Equals check after computing them all
    for( int i=0; i<argss.length; i++ ) {
      TypeFunPtr expect = (TypeFunPtr)argss[i].at(ins.length);
      TypeFunPtr actual = CallNode.ttfp(tns[i]); // Looking at the TFP from the Call, ignore ctrl,memory,args
      assertEquals(expect.fidxs(),actual.fidxs());
    }
    return tns;
  }


  private static TypeFunPtr v(Node n, GVNGCM gvn) { return (TypeFunPtr)n.value(gvn._opt_mode); }

  /** Validate monotonicity of CallNode.resolve().  There are only a couple of
   *  interesting variants; this test also tests e.g. XCTRL for correctness but
   *  its implementation is a simple cutout, same for the display arg "^" being
   *  NO_DISP.
   *
   *  === High mul fptr (e.g. GCP) ===
   *  arg1  arg2    fptr*      resolve
   *   ~S    ~S   [~int+flt]  [~int+flt]   Choices all around
   *    2    ~S   [~int+flt]  [~int+flt]   Choices all around; arg2 can fall to e.g. 3 or 3.14
   *    2     3   [~int+flt]  [~int+XXX]   Valid to cutout flt or allow (least_cost will resolve)
   *    2     S   [~int+flt]  [ int,flt]   Error state, but arg2 may lift
   *    S     S   [~int+flt]  [ int,flt]   Error state, but args may lift
   *   ~S     S   [~int+flt]  [ int,flt]   Error state in GCP, args may lift in ITER
   *   ~S    str  [~int+flt]  [ int,flt]   Error state - sideways
   *    2    str  [~int+flt]  [ int,flt]   Error state - sideways
   *
   *  === High add fptr (e.g. GCP) ===
   *  arg1  arg2     fptr+           resolve
   *   ~S    ~S   [~int+flt+str]  [~int+flt+str]   Choices all around
   *   2     ~S   [~int+flt+str]  [~int+XXX    ]   Cutout str, but int,flt OK
   *   2     3    [~int+flt+str]  [~int+XXX    ]   Valid to cutout flt or allow (least_cost will resolve by lowering)
   *   2     S    [~int+flt+str]  [ int,flt    ]   Error state, but arg2 may lift
   *   S     S    [~int+flt+str]  [ int,flt,str]   Error state, but args may lift
   *   2     str  [~int+flt+str]  [ int,flt,str]   Error state, none of {int,flt,str} work
   *   ~S    str  [~int+flt+str]  [        ~str]   Choice, since may yet be error
   *   str   str  [~int+flt+str]  [        ~str]   Choice, since may yet be error
   *
   *  === Low fptr (GCP, but also ITER depending on implementation choices )  ===
   *  arg1  arg2    fptr        resolve
   *   X     X    [ int,flt]  [  SAME  ]   Same as high fptr
   *   2     ~S   [~int+flt]  [~int+flt]   Error args
   *   2     3    [ int,flt]  [ int,XXX]   Low, not high, for all good args
   */
  @SuppressWarnings("unchecked")
  @Test public void testCallNodeResolve() {
    Env top = Env.top_scope();
    GVNGCM gvn = Env.GVN;

    // Make a Unknown/CallNode/CallEpi combo.
    // Unwired.  Validate the resolve process and monotonicity.
    gvn._opt_mode=GVNGCM.Mode.Parse;
    ConNode ctrl = (ConNode) gvn.xform(new ConNode<>(Type.CTRL));
    UnresolvedNode fp_mul = (UnresolvedNode)top.lookup("*"); // {int int -> int} and {flt flt -> flt}
    UnresolvedNode fp_add = (UnresolvedNode)top.lookup("+"); // {int int -> int} and {flt flt -> flt} and {str str -> str}
    FunPtrNode aflt = (FunPtrNode)fp_add.in(0);
    FunPtrNode aint = (FunPtrNode)fp_add.in(1);
    FunPtrNode astr = (FunPtrNode)fp_add.in(2);
    // Make a flt/int combo, drops off string.
    UnresolvedNode anum = new UnresolvedNode(null,aflt,aint);
    ConNode mem  = gvn.init(new ConNode<>(TypeMem.MEM));
    ConNode arg1 = gvn.init(new ConNode<>(Type.SCALAR));
    ConNode arg2 = gvn.init(new ConNode<>(Type.SCALAR));
    CallNode call = (CallNode)gvn.xform(new CallNode(true, null, ctrl, mem, fp_mul, arg1, arg2));
    CallEpiNode cepi = (CallEpiNode)gvn.xform(new CallEpiNode(call, Env.DEFMEM)); // Unwired

    gvn.unreg(call);            // Will be hacking edges
    Node[] ins = new Node[]{ctrl,mem,fp_mul,arg1,arg2};

    // Args to calls
    Type tctl = Type.CTRL, txctl = Type.XCTRL;
    Type tscl = Type.SCALAR, txscl = Type.XSCALAR;
    Type tnil = Type.XNIL;
    TypeMem tfull = TypeMem.MEM;
    Type t2 = TypeInt.con(2);
    Type t3 = TypeInt.con(3);
    Type tabc=TypeMemPtr.ABCPTR.simple_ptr();

    // iter(), not gcp().  Types always rise.  Very low types might lift to be
    // valid, but e.g. a 2:int will never lift to a str.
    gvn._opt_mode=GVNGCM.Mode.PesiNoCG;

    // The various kinds of results we expect
    TypeFunPtr tmul1 = v(fp_mul,gvn), tmul1X = tmul1.dual();
    TypeFunPtr tadd1 = v(fp_add,gvn), tadd1X = tadd1.dual();

    TypeFunPtr tnum1 = v(anum,gvn), tnum1X = tnum1.dual();
    TypeFunPtr tflt1 = v(aflt,gvn), tflt1X = tflt1.dual();
    TypeFunPtr tint1 = v(aint,gvn), tint1X = tint1.dual();
    TypeFunPtr tstr1 = v(astr,gvn), tstr1X = tstr1.dual();

    TypeFunPtr tmul1E = TypeFunPtr.make(BitsFun.EMPTY,0,TypeFunPtr.NO_DISP); // All bad choices

    assert tadd1X.isa(tnum1X) && tnum1X.isa(tflt1X) && tflt1X.isa(tnum1) && tnum1.isa(tadd1);


    // Check the fptr {int,flt} meet
    call.set_fun(ins[2]=fp_mul,gvn);
    TypeTuple[] argss_mul1 = new TypeTuple[] {                 // arg1  arg2   resolve
      TypeTuple.make( tctl, tfull, tmul1, txscl, txscl, tmul1X), //  ~S    ~S   [+int+flt] ;          high
      TypeTuple.make( tctl, tfull, tmul1, t2   , txscl, tmul1X), //   2    ~S   [+int+flt] ;     good+high
      TypeTuple.make( tctl, tfull, tmul1, t2   , t3   , tmul1 ), //   2     3   [ int,flt] ;     good
      TypeTuple.make( tctl, tfull, tmul1, t2   , tscl , tmul1 ), //   2     S   [ int,flt] ; low+good
      TypeTuple.make( tctl, tfull, tmul1, tscl , tscl , tmul1 ), //   S     S   [ int,flt] ; low
      TypeTuple.make( tctl, tfull, tmul1, txscl, tscl , tmul1 ), //  ~S     S   [ int,flt] ; low     +high
      TypeTuple.make( tctl, tfull, tmul1, txscl, tabc , tmul1X), //  ~S    str  [ int,flt] ; bad      high
      TypeTuple.make( tctl, tfull, tmul1, tabc , tabc , tmul1 ), //  str   str  [        ] ; bad
      TypeTuple.make( tctl, tfull, tmul1, t2   , tabc , tmul1 ), //   2    str  [ int,flt] ; bad+good
    };
    _testMonotonicChain(ins,call,argss_mul1);

    // Check the {int,flt,str} meet.
    // Rules:
    // - Some args High & no Low, keep all & join (ignore Good,Bad)
    // - Some args Low & no High, keep all & meet (ignore Good,Bad)
    // - Mix High/Low & no Good , keep all & fidx?join:meet
    // - Some Good, no Low, no High, drop Bad & fidx?join:meet
    // - All Bad, like Low: keep all & meet
    call.set_fun(ins[2]=fp_add,gvn);
    TypeTuple[] argss_add1 = new TypeTuple[] {
      TypeTuple.make( tctl, tfull, tadd1, txscl, txscl, tadd1X), //  ~S    ~S   [+int+flt+str] (__H,__H,__H) ; All  high, keep all, join
      TypeTuple.make( tctl, tfull, tadd1, txscl, tabc , tadd1X), //  ~S    str  [+int+flt+str] (B_H,B_H,_GH) ; Some high, keep all, join
      TypeTuple.make( tctl, tfull, tadd1, txscl, tscl , tadd1 ), //  ~S     S   [ int,flt,str] (L_H,L_H,L_H) ; Mix H/L no Good, fidx/meet
      TypeTuple.make( tctl, tfull, tadd1, tnil , txscl, tadd1X), //   0    ~S   [+int+flt+str] (_GH,_GH,_GH) ; Some high, keep all, join
      TypeTuple.make( tctl, tfull, tadd1, tnil , t3   , tnum1 ), //   0     3   [ int,flt    ] (_G_,_G_,BG_) ; Some good, drop bad, fidx/meet
      TypeTuple.make( tctl, tfull, tadd1, tnil , tabc , tstr1 ), //   0    str  [         str] (BG_,BG_,_G_) ; Some good, drop bad, fidx/meet
      TypeTuple.make( tctl, tfull, tadd1, tnil , tscl , tadd1 ), //   0     S   [ int,flt,str] (LG_,LG_,LG_) ; Some low , keep all, meet
      TypeTuple.make( tctl, tfull, tadd1, t2   , txscl, tadd1X), //   2    ~S   [+int+flt+str] (_GH,_GH,B_H) ; Some high, keep all, join
      TypeTuple.make( tctl, tfull, tadd1, t2   , t3   , tnum1 ), //   2     3   [ int,flt    ] (_G_,_G_,B__) ; Some good, drop bad, fidx/meet
      TypeTuple.make( tctl, tfull, tadd1, t2   , tabc , tadd1 ), //   2    str  [ int,flt,str] (BG_,BG_,BG_) ; All  bad , keep all, meet
      TypeTuple.make( tctl, tfull, tadd1, t2   , tscl , tadd1 ), //   2     S   [ int,flt,str] (LG_,LG_,B__) ; Some low , keep all, meet
      TypeTuple.make( tctl, tfull, tadd1, tabc , tabc , tstr1 ), //  str   str  [         str] (B__,B__,_G_) ; Some good, drop bad, fidx/meet
      TypeTuple.make( tctl, tfull, tadd1, tscl , tscl , tadd1 ), //   S     S   [ int,flt,str] (L__,L__,L__) ; All  low , keep all, meet
    };
    _testMonotonicChain(ins,call,argss_add1);


    // gcp(), not iter().  Types always lower.  Very high types might lower to be
    // valid, but e.g. a 2:int will never lower to a str.
    gvn._opt_mode=GVNGCM.Mode.Opto;

    // The various kinds of results we expect
    TypeFunPtr tmul2X = v(fp_mul,gvn), tmul2 = tmul2X.dual();
    TypeFunPtr tadd2X = v(fp_add,gvn), tadd2 = tadd2X.dual();

    TypeFunPtr tnum2X = v(anum,gvn), tnum2 = tnum2X.dual();
    TypeFunPtr tflt2  = v(aflt,gvn), tflt2X= tflt2 .dual();
    TypeFunPtr tint2  = v(aint,gvn), tint2X= tint2 .dual();
    TypeFunPtr tstr2  = v(astr,gvn), tstr2X= tstr2 .dual();

    TypeFunPtr tmul2E = tmul1E;

    assert tadd2X.isa(tnum2X) && tnum2X.isa(tflt2X) && tflt2X.isa(tnum2) && tnum2.isa(tadd2);


    // Check the fptr {+int+flt} choices
    call.set_fun(ins[2]=fp_mul,gvn);
    TypeTuple[] argss_mul2 = new TypeTuple[] {                  // arg2  arg2   resolve
      TypeTuple.make( tctl, tfull, tmul2X, txscl, txscl, tmul2X), //  ~S    ~S   [+int+flt]
      TypeTuple.make( tctl, tfull, tmul2X, t2   , txscl, tmul2X), //   2    ~S   [+int+flt]
      TypeTuple.make( tctl, tfull, tmul2X, t2   , t3   , tmul2X), //   2     3   [ int,flt]
      TypeTuple.make( tctl, tfull, tmul2X, t2   , tscl , tmul2 ), //   2     S   [ int,flt]
      TypeTuple.make( tctl, tfull, tmul2X, tscl , tscl , tmul2 ), //   S     S   [ int,flt]
      TypeTuple.make( tctl, tfull, tmul2X, txscl, tscl , tmul2X), //  ~S     S   [ int,flt]
      TypeTuple.make( tctl, tfull, tmul2X, txscl, tabc , tmul2X), //  ~S    str  [ int,flt]
      TypeTuple.make( tctl, tfull, tmul2X, t2   , tabc , tmul2 ), //   2    str  [ int,flt]
    };
    _testMonotonicChain(ins,call,argss_mul2);

    // Check the {+int+flt+str} choices
    call.set_fun(ins[2]=fp_add,gvn);
    TypeTuple[] argss_add2 = new TypeTuple[] {
      TypeTuple.make( tctl, tfull, tadd2X, txscl, txscl, tadd2X), //  ~S    ~S   [+int+flt+str] (__H,__H,__H) ; All  high, keep all, join
      TypeTuple.make( tctl, tfull, tadd2X, txscl, tabc , tadd2X), //  ~S    str  [+int+flt+str] (B_H,B_H,_GH) ; Some high, keep all, join
      TypeTuple.make( tctl, tfull, tadd2X, txscl, tscl , tadd2X), //  ~S     S   [+int+flt+str] (L_H,L_H,L_H) ; Mix H/L, no good, keep all, fidx/join
      TypeTuple.make( tctl, tfull, tadd2X, tnil , txscl, tadd2X), //   0    ~S   [+int+flt+str] (_GH,_GH,_GH) ; Some high, keep all, join
      TypeTuple.make( tctl, tfull, tadd2X, tnil , t3   , tnum2X), //   0     3   [+int+flt    ] (_G_,_G_,BG_) ; Some good, drop bad, fidx/join
      TypeTuple.make( tctl, tfull, tadd2X, tnil , tabc , tstr2 ), //   0    str  [        ~str] (BG_,BG_,_G_) ; Some good, drop bad, fidx/join
      TypeTuple.make( tctl, tfull, tadd2X, tnil , tscl , tadd2 ), //   0     S   [ int,flt,str] (LG_,LG_,LG_) ; Some low , keep all, meet
      TypeTuple.make( tctl, tfull, tadd2X, t2   , txscl, tadd2X), //   2    ~S   [+int+flt+str] (_GH,_GH,B_H) ; Some high, keep all, join
      TypeTuple.make( tctl, tfull, tadd2X, t2   , t3   , tnum2X), //   2     3   [+int+flt    ] (_G_,_G_,B__) ; Some good, drop bad, fidx/join
      TypeTuple.make( tctl, tfull, tadd2X, t2   , tabc , tadd2 ), //   2    str  [ int,flt,str] (BG_,BG_,BG_) ; All  bad , keep all, meet
      TypeTuple.make( tctl, tfull, tadd2X, t2   , tscl , tadd2 ), //   2     S   [ int,flt,str] (LG_,LG_,B__) ; Some low , keep all, meet
      TypeTuple.make( tctl, tfull, tadd2X, tabc , tabc , tstr2 ), //  str   str  [        ~str] (B__,B__,_G_) ; Some good, drop bad, fidx/join
      TypeTuple.make( tctl, tfull, tadd2X, tscl , tscl , tadd2 ), //   S     S   [ int,flt,str] (L__,L__,L__) ; All  low , keep all, meet
    };
    _testMonotonicChain(ins,call,argss_add2);

    gvn.kill(cepi);
  }



  // When making a recursive function, we get a pointer cycle with the display
  // and function arguments.  Validate that we can re-discover this closed
  // cycle during GCP from whole cloth.

  // Code: "fact={ x -> x>1 ? fact(x-1)*x : 1 }"
  // tfp = [36]{^:[*10] x:int -> Scalar}   // Function def, with standard display.  Note the display is dead here.
  // *[10] -> {^:[*6] fact:tfp}            // File-level scope
  // *[6] -> { ^:nil PRIMS...}             // Prim-level scope
  //
  // Here's an example where the display is not dead-by-default:
  // Code: "gen_ctr={cnt;{cnt++}}; ctrA=gen_ctr(); ctrB=gen_ctr(); ctrA(); ctrB(); ctrB()"
  //
  @Test public void testRecursiveDisplay() {
    Env top = Env.top_scope();
    GVNGCM gvn = Env.GVN;

    // Build the graph for the "fact" example:
    // NewObj (display); inputs are prior display and FunPtr
    //   OProj
    //   DProj
    //   MemMerge; default mem and OProj
    // Fun (and Fun._tf) - Just default control and some other control
    //   Parm:^ - Default display and DProj
    //   Parm:mem - Default mem and the MemMerge of OProj
    //   Ret - {Fun,Mem,Parm:^} - Not really fact() nor gen_ctr() code but upwards exposed closure
    //   FunPtr - Ret
    gvn._opt_mode=GVNGCM.Mode.Parse;
    ConNode ctl = gvn.init(new ConNode<>(Type.CTRL));
    ctl._val = Type.CTRL;
    ConNode mem = (ConNode)gvn.xform(new ConNode<>(TypeMem.ANYMEM));
    ConNode rpc = (ConNode)gvn.xform(new ConNode<>(TypeRPC.ALL_CALL));
    ConNode dsp_prims = (ConNode) gvn.xform(new ConNode<>(TypeMemPtr.DISP_SIMPLE));
    // The file-scope display closing the graph-cycle.  Needs the FunPtr, not
    // yet built.
    NewObjNode dsp_file = (NewObjNode)gvn.xform(new NewObjNode(true,TypeStruct.DISPLAY,dsp_prims));
    MrgProjNode dsp_file_obj = Env.DEFMEM.make_mem_proj(gvn,dsp_file,mem);
    ProjNode  dsp_file_ptr = ( ProjNode)gvn.xform(new  ProjNode(1, dsp_file));
    Env.ALL_DISPLAYS = Env.ALL_DISPLAYS.set(dsp_file._alias);
    // The Fun and Fun._tf:
    TypeStruct formals = TypeStruct.make_args(Types.ts(dsp_file_ptr._val, // File-scope display as arg0
                                                         Type.SCALAR));          // Some scalar arg1
    TypeFunSig sig = TypeFunSig.make(formals,Type.SCALAR);
    FunNode fun = new FunNode("fact",sig,-1,false);
    gvn.init(fun.add_def(ctl).add_def(ctl));
    // Parms for the Fun.  Note that the default type is "weak" because the
    // file-level display can not yet know about "fact".
    ParmNode parm_mem = new ParmNode(-2,"mem",fun,mem,null);
    ParmNode parm_dsp = new ParmNode( 0,"^"  ,fun,Type.SCALAR,gvn.con(dsp_file_ptr._val),null);
    gvn.init(parm_mem.add_def(dsp_file_obj));
    gvn.init(parm_dsp.add_def(dsp_file_ptr));
    // Close the function up
    RetNode ret = gvn.init(new RetNode(fun,parm_mem,parm_dsp,rpc,fun));
    FunPtrNode fptr = gvn.init(new FunPtrNode(ret,dsp_file_ptr));
    // Close the cycle
    dsp_file.create("fact",fptr,TypeStruct.FFNL,gvn);
    dsp_file.no_more_fields();
    // Return the fptr to keep all alive
    ScopeNode env = new ScopeNode(null,true);
    env.set_ctrl(ctl,gvn);
    env.set_ptr (dsp_file_ptr,gvn);
    env.set_mem (dsp_file_obj,gvn);
    env.set_rez (fptr,gvn);
    gvn.init(env);

    Node[] nodes = new Node[]{ctl,mem,rpc,dsp_prims,dsp_file,dsp_file_obj,dsp_file_ptr,fun,parm_mem,parm_dsp,ret,fptr,env};

    // Validate graph initial conditions.  No optimizations, as this
    // pile-o-bits is all dead and will vaporize if the optimizer is turned
    // loose on it.  Just check the types flow correctly.
    gvn._opt_mode=GVNGCM.Mode.PesiNoCG;
    for( Node n : nodes ) {
      Type old = n._val;
      Type nnn = n.value(gvn._opt_mode);
      assert nnn.isa(old);
    }

    // Now run GCP to closure.  This is the key call being tested.
    gvn.gcp(GVNGCM.Mode.Opto,env);

    // Validate cyclic display/function type
    TypeFunPtr tfptr0 = (TypeFunPtr)fptr._val;
    Type tdptr0 = tfptr0._disp;
    Type tret = ((TypeTuple)ret._val).at(2);
    assertEquals(tdptr0,tret); // Returning the display
    // Display contains 'fact' pointing to self
    TypeMem tmem = (TypeMem)dsp_file_obj._val;
    TypeStruct tdisp0 = (TypeStruct)tmem.ld((TypeMemPtr)tdptr0);
    assertEquals(tfptr0,tdisp0.at(tdisp0.find("fact")));
  }


  // Memory checks args "just like" normal args, except it changes contents of
  // memory to match incoming args.
  //
  // Single bad ptr + memory, e.g. [13]->obj and [13:@{x==1,y==2}] but the
  // formal is [2:Point:@{x,y}].  Can change memory directly here (no sharing):
  // [13:Point:@{x,y}] and leave the ptr alone.
  //
  // Can also make a new fake alias: 14>>13, change both ptr and mem:
  // *[14]->obj, [14:Point:@{x,y}].  If [13] lifts to some other refinement
  // alias, may need new fake aliases.  If [13] lifts to a refinement with a
  // valid memory, no need to change memory.
  //
  // Must be monotonic towards correctness, if theres any chance to lift (fall)
  // and be correct.  If always an error, can go sideways but still monotonic
  // on the side path.
  //
  // Have to figure out how to handle N busted ptrs, and N busted memories.
  // Either fake aliases for all, or union the incompatible types?  Begs for a
  // custom test: Fun, Parm:mem, Parm:x, Parm:y.  Outputs always within formal
  // bounds, and always monotonic, and preserves shape if in-bounds.

  private static int ERR=0;
  @Test public void testMemoryArgs() {
    Env top = Env.top_scope();
    GVNGCM gvn = Env.GVN;

    // Check Parm.value calls are monotonic, and within Fun.sig bounds -
    // including memory args.

    // Build a bunch of aliases.
    int a1 = BitsAlias.new_alias(BitsAlias.RECORD);
    int a2 = BitsAlias.new_alias(BitsAlias.RECORD);
    int a3 = BitsAlias.new_alias(BitsAlias.RECORD);
    Type[] ts_int_flt = TypeStruct.ts(TypeMemPtr.NO_DISP,TypeInt.INT64,TypeFlt.FLT64);
    Type[] ts_int_abc = TypeStruct.ts(TypeMemPtr.NO_DISP,TypeInt.INT64,TypeMemPtr.ABCPTR);
    // @{ a:int; b:"abc" }
    TypeStruct a_int_b_abc = TypeStruct.make(new String[]{"^","a","b"},ts_int_abc);

    // Build a bunch of function type signatures
    TypeFunSig[] sigs = new TypeFunSig[] {
      TypeFunSig.make(Type.SCALAR,ts_int_flt), // {int flt   -> }
      TypeFunSig.make(Type.SCALAR,ts_int_abc), // {int "abc" -> }
      // { flt @{a:int; b:"abc"} -> }
      TypeFunSig.make(Type.SCALAR,TypeStruct.ts(TypeMemPtr.NO_DISP,TypeFlt.FLT64,TypeMemPtr.make(BitsAlias.RECORD,a_int_b_abc))),
    };

    // Build a bunch of memory parm types
    TypeMem[] mems = new TypeMem[] {
      tmem(null),
      tmem(null).dual(),
      tmem(new int[]{a1},TypeStr.STR),
      tmem(new int[]{a1},a_int_b_abc),
    };

    // Build a bunch of parameter types
    Type[] args = new Type[] {
      Type.NIL,
      Type.XNIL,
      TypeInt.INT64,
      TypeInt.INT64.dual(),
      TypeInt.NINT64,
      TypeMemPtr.ABCPTR.simple_ptr(),
      TypeMemPtr.ABCPTR.dual().simple_ptr(),
      TypeMemPtr.make(a1,TypeObj.OBJ).simple_ptr(),
      TypeMemPtr.make(a1,TypeObj.OBJ).dual().simple_ptr(),
    };

    // One-off jig for testing single combo
    Type[] rez1 = check(gvn,sigs[1],mems[0],args[0],args[8]);
    Type[] rez2 = check(gvn,sigs[1],mems[0],args[0],args[5]);
    for( int k=0; k<rez1.length; k++ )
      assertTrue(rez1[k].isa(rez2[k]));


    // Call for all combos.
    // Check results are isa-sig.
    Type[][][][][] rezs = new Type[sigs.length][mems.length][args.length][args.length][];
    for( int is = 0; is<sigs.length; is++ )
      for( int im = 0; im<mems.length; im++ )
        for( int ia0 = 0; ia0<args.length; ia0++ )
          for( int ia1 = 0; ia1<args.length; ia1++ )
            rezs[is][im][ia0][ia1] = check(gvn,sigs[is],mems[im],args[ia0],args[ia1]);

    // Check results are monotonic:
    for( int is = 0; is<sigs.length; is++ )
      for( int js = 0; js<sigs.length; js++ )
        if( sigs[is].isa(sigs[js]) )
          for( int im = 0; im<mems.length; im++ )
            for( int jm = 0; jm<mems.length; jm++ )
              if( mems[im].isa(mems[jm]) )
                for( int ia0 = 0; ia0<args.length; ia0++ )
                  for( int ja0 = 0; ja0<args.length; ja0++ )
                    if( args[ia0].isa(args[ja0]) )
                      for( int ia1 = 0; ia1<args.length; ia1++ )
                        for( int ja1 = 0; ja1<args.length; ja1++ )
                          if( args[ia1].isa(args[ja1]) ) {
                            Type[] rezi = rezs[is][im][ia0][ia1];
                            Type[] rezj = rezs[js][jm][ja0][ja1];
                            for( int k=0; k<rezi.length; k++ )
                              if( !rezi[k].isa(rezj[k]) )
                                perror("Not monotonic",rezi[k],rezj[k]);
                          }
    assertEquals(0,ERR);
  }

  // Check that the Parm.value calls for these incoming args are monotonic, and
  // within the sig bounds.
  private static Type[] check( GVNGCM gvn, TypeFunSig tsig, TypeMem tmem, Type targ1, Type targ2 ) {

    ConNode ctl = gvn.init(new ConNode<>(Type.CTRL));
    CallNode call = gvn.init(new CallNode(true, null, ctl, null, null, null, null));
    CallEpiNode cepi = gvn.init(new CallEpiNode(call, Env.DEFMEM)); // Unwired
    Node cpj = gvn.xform(new CProjNode(call,0));
    ConNode mem = (ConNode)gvn.xform(new ConNode<>(tmem ));
    ConNode arg1= (ConNode)gvn.xform(new ConNode<>(targ1.simple_ptr()));
    ConNode arg2= (ConNode)gvn.xform(new ConNode<>(targ2.simple_ptr()));

    // Make nodes
    FunNode fun = new FunNode("fun",tsig,-1,false);
    gvn.xform(fun.add_def(cpj));

    ParmNode parmem= gvn.init(new ParmNode(-2,"mem" ,fun,mem ,null));
    ParmNode parm1 = gvn.init(new ParmNode( 1,"arg1",fun,arg1,null));
    ParmNode parm2 = gvn.init(new ParmNode( 2,"arg2",fun,arg2,null));

    // Types for normal args before memory type
    Type tp1 = parm1 .xval (gvn._opt_mode);
    Type tp2 = parm2 .xval (gvn._opt_mode);
    Type tpm = parmem.value(gvn._opt_mode);

    // Check the isa(sig) on complex pointer args
    Type actual1 = tpm.sharptr(tp1);
    Type formal1 = fun.formal(1);
    //if( tp1!=Type.ALL && !tp1.isa(formal1.simple_ptr()) )
    //  perror("arg1-vs-formal1",tp1,formal1.simple_ptr());
    Type actual2 = tpm.sharptr(tp2);
    Type formal2 = fun.formal(2);
    //if( tp2!=Type.ALL && !tp2.isa(formal2.simple_ptr()) )
    //  perror("arg2-vs-formal2",tp2,formal2.simple_ptr());

    // Record for later monotonic check
    return new Type[]{tpm,tp1,tp2};
  }

  static void perror( String msg, Type t1, Type t2 ) {
    if( ERR < 10 )
      System.out.println(msg+", "+t1+" is not "+t2);
    ERR++;
  }


  // Helper to make memory
  private static TypeMem tmem(int[] as, TypeObj... ts) {
    int max = BitsAlias.ARY;
    if( as !=null && as.length> 0 ) max = Math.max(max,as[as.length-1]);
    TypeObj[] tos = new TypeObj[max+1];
    tos[BitsAlias.ALL] = TypeObj.OBJ;
    tos[BitsAlias.RECORD]=TypeStruct.ALLSTRUCT;
    tos[BitsAlias.ABC] = TypeStr.ABC; //
    tos[BitsAlias.STR] = TypeStr.STR;
    tos[BitsAlias.ARY] = TypeAry.ARY;
    if( as != null )
      for( int i=0; i<as.length; i++ )
        tos[as[i]] = ts[i];
    return TypeMem.make0(tos);
  }
}

