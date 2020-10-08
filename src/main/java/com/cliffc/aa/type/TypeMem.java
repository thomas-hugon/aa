package com.cliffc.aa.type;

import com.cliffc.aa.util.*;

import java.util.Arrays;
import java.util.HashMap;

/**
   Memory type; the state of all of memory; memory edges order memory ops.
   Produced at the program start, consumed by all function calls, consumed be
   Loads, consumed and produced by Stores.  Can be broken out in the "equiv-
   alence class" (Alias#) model of memory over a bulk memory to allow more fine
   grained knowledge.  Memory is accessed via Alias#s, where all TypeObjs in an
   Alias class are Meet together as an approximation.

   Conceptually, each alias# represents an infinite set of pointers - broken
   into equivalence classes.  We can split such a class in half - some pointers
   will go left and some go right, and where we can't tell we'll use both sets.
   Any alias set is a tree-like nested set of sets bottoming out in individual
   pointers.  The types are conceptually unchanged if we start using e.g. 2
   alias#s instead of 1 everywhere - we've just explicitly named the next layer
   in the tree-of-sets.

   Splitting happens during code-cloning (inlining) where we make a copy of an
   alias generator (NewNode).  Both copies are alias renumbered to child alias
   numbers from the parent.  The IR will be holding on to some copies of the
   original alias#, which is now confused with both children.  After a full
   round of gcp() this confusion will be removed.  While the confusion is not
   (yet) removed, we will have to deal with this mixture of the left child,
   right child, and parent.

   We use an "all-memory" notion to handle the worse-case from e.g. all unknown
   calls.  Really the worse a Call can be is to "leak" all aliases that come in
   to the the call (and are reachable from those) - but we need a convenient
   Bottom type.  Missing aliases default to TypeObj.

   The representation is a collection of TypeObjs indexed by alias#.  Missing
   aliases are always equal to their nearest present parent.  The root at
   alias#1 is only either TypeObj.BOT or TOP.  Alias#0 is nil and is always
   missing.  The structure is canonicalized; if a child is a dup of a parent it
   is removed (since an ask will yield the correct value from the parent).

   There is no meet/join relationship between parent and child; a child can be
   precisely updated independently from the parent and other siblings.

   CNC - Observe that the alias Trees on Fields applies to Indices on arrays as
   well - if we can group indices in a tree-like access pattern (obvious one
   being All vs some Constants).
*/
public class TypeMem extends Type<TypeMem> {
  // Mapping from alias#s to the current known alias state.  Slot 0 is
  // reserved; TypeMem is never a nil.  Slot#1 is the Parent-Of-All aliases and
  // is the default value.  Default values are replaced with null during
  // canonicalization.
  private TypeObj[] _pubs;

  // A cache of sharpened pointers.  Pointers get sharpened by looking up their
  // aliases in this memory (perhaps merging several aliases).  The process is
  // recursive and "deeply" sharpens pointers, and is somewhat expensive.
  // Maintain a cache of prior results.  Not related to the objects Type, so
  // not part of the hash/equals checks.  Optional.  Lazily filled in.
  private HashMap<TypeMemPtr,TypeMemPtr> _sharp_cache;

  private TypeMem  (TypeObj[] pubs) { super(TMEM); init(pubs); }
  private void init(TypeObj[] pubs) {
    super.init(TMEM);
    assert check(pubs);    // Caller has canonicalized arrays already
    _pubs = pubs;
  }
  // False if not 'tight' (no trailing null pairs) or any matching pairs (should
  // collapse to their parent) or any mixed parent/child.
  private static boolean check(TypeObj[] as) {
    if( !(as[0] instanceof TypeLive) ) return false; // Slot 0 reserved for live-ness
    if( as.length == 1 ) return true;
    if( as[1]!=TypeObj.OBJ    && as[1]!=TypeObj.XOBJ   &&
        as[1]!=TypeObj.ISUSED && as[1]!=TypeObj.UNUSED &&
        !(as[1] instanceof TypeLive) &&
        as[1] != null )
      return false;             // Only 2 choices
    if( as[0].above_center()!=as[1].above_center() ) return false;
    if( as.length==2 ) return true; // Trivial all of memory
    // "tight" - something in the last slot
    if( as[as.length-1] == null ) return false;
    // No dups of any parent
    for( int i=2; i<as.length; i++ )
      if( as[i] != null )
        for( int par = BitsAlias.TREE.parent(i); par!=0; par = BitsAlias.TREE.parent(par) )
          if( as[par] != null ) {
            if( as[par] == as[i] ) return false; // Dup of a parent
            break;
          }
    return true;
  }
  @Override int compute_hash() {
    int sum=TMEM;
    for( TypeObj obj : _pubs ) sum += obj==null ? 0 : obj._hash;
    return sum;
  }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof TypeMem) ) return false;
    TypeMem tf = (TypeMem)o;
    if( _pubs .length != tf._pubs .length ) return false;
    for( int i = 0; i< _pubs.length; i++ )
      if( _pubs[i] != tf._pubs[i] ) // note '==' and NOT '.equals()'
        return false;
    return true;
  }
  // Never part of a cycle, so the normal check works
  @Override public boolean cycle_equals( Type o ) { return equals(o); }
  private static final char[] LIVEC = new char[]{' ','#','R','3'};
  @Override public SB str( SB sb, VBitSet dups, TypeMem mem, boolean debug ) {
    if( this==FULL ) return sb.p(" [ all ]");
    if( this==EMPTY) return sb.p(" [_____]");
    if( this== MEM ) return sb.p(" [ mem ]");
    if( this==XMEM ) return sb.p(" [~mem ]");
    if( this==DEAD ) return sb.p("![dead ]");
    if( this==ALIVE) return sb.p(" [live ]");
    if( this==ESCAPE)return sb.p("#[escap]");
    if( this==LIVE_BOT) return sb.p("3[!repl]");
    if( _pubs[0]==TypeLive.DEAD ) sb.p('!');
    else sb.p(LIVEC[((TypeLive)_pubs[0])._flags]);
    if( _pubs.length==1 ) return sb.p("[]");
    sb.p('[');
    for( int i = 1; i< _pubs.length; i++ )
      if( _pubs[i] != null )
        _pubs[i].str(sb.p(i).p(':'),dups,mem,debug).p(",");
    return sb.unchar().p(']');
  }

  // Alias-at.  Out of bounds or null uses the parent value.
  public TypeObj at   (int alias) { return at(_pubs ,alias); }
  static TypeObj at(TypeObj[] tos, int alias) { return tos.length==1 ? tos[0].oob(TypeObj.ISUSED): tos[at_idx(tos,alias)]; }
  // Alias-at index
  static int at_idx(TypeObj[]tos, int alias) {
    if( alias==0 ) return 1;    // Either base memory, or assert
    while( true ) {
      if( alias < tos.length && tos[alias] != null )
        return alias;
      alias = BitsAlias.TREE.parent(alias);
      assert alias!=0;
    }
  }
  //
  public TypeObj[] alias2objs() { return _pubs; }
  public int len() { return _pubs.length; }

  // Return set of aliases.  Not even sure if this is well-defined.
  public BitsAlias aliases() {
    if( this== FULL ) return BitsAlias.NZERO;
    if( this==EMPTY ) return BitsAlias.EMPTY;
    BitsAlias bas = BitsAlias.EMPTY;
    for( int i = 0; i< _pubs.length; i++ )
      if( _pubs[i]!=null && !_pubs[i].above_center() )
        bas = bas.set(i);
    return bas;
  }

  private static TypeMem FREE=null;
  @Override protected TypeMem free( TypeMem ret ) { _pubs =null; _sharp_cache=null; FREE=this; return ret; }
  private static TypeMem make(TypeObj[] pubs) {
    TypeMem t1 = FREE;
    if( t1 == null ) t1 = new TypeMem(pubs);
    else { FREE = null;       t1.init(pubs); }
    TypeMem t2 = (TypeMem)t1.hashcons();
    return t1==t2 ? t1 : t1.free(t2);
  }

  // Canonicalize memory before making.  Unless specified, the default memory is "do not care"
  public static TypeMem make0( TypeObj[] as ) {
    assert as.length==1 || as[0]==null;
    if( as.length> 1 ) as[0] = as[1].oob(TypeLive.LIVE);
    TypeObj[] tos = _make1(as);
    if( tos==null ) return DEAD; // All things are dead, so dead
    return make(tos);
  }

  // Canonicalize memory before making.  Unless specified, the default memory is "do not care"
  private static TypeObj[] _make1( TypeObj[] as ) {
    int len = as.length;
    if( len > 1 && as[1]==null ) {
      int i; for( i=2; i<len; i++ )
        if( as[i]!=null && as[i] != TypeObj.XOBJ )
          break;
      if( i==len ) return null; // All things are dead, so dead
      as[1] = TypeObj.XOBJ;     // Default memory is "do not care"
    }
    if( len <= 2 ) return as;
    // No dups of a parent
    for( int i=1; i<as.length; i++ )
      if( as[i] != null )
        for( int par = BitsAlias.TREE.parent(i); par!=0; par = BitsAlias.TREE.parent(par) )
          if( as[par] != null ) {
            if( as[par] == as[i] ) as[i] = null;
            break;
          }
    // Remove trailing nulls; make the array "tight"
    while( as[len-1] == null ) len--;
    if( as.length!=len ) as = Arrays.copyOf(as,len);
    return as;
  }

  // Precise single alias.  Other aliases are "do not care".  Nil not allowed.
  // Both "do not care" and this alias are exact.
  public static TypeMem make(int alias, TypeObj oop ) {
    TypeObj[] as = new TypeObj[alias+1];
    as[1] = TypeObj.UNUSED;
    as[alias] = oop;
    return make0(as);
  }
  public static TypeMem make(BitsAlias aliases, TypeObj oop ) {
    TypeObj[] as = new TypeObj[aliases.max()+1];
    as[1] = TypeObj.UNUSED;
    for( int alias : aliases )
      if( alias != 0 )
        as[alias] = oop;
    return make0(as);
  }

  public static TypeMem make_live(TypeLive live) { return make0(new TypeObj[]{live}); }

  public static final TypeMem FULL; // Every alias filled with something
  public static final TypeMem EMPTY;// Every alias filled with anything
  public static final TypeMem  MEM; // FULL, except lifts REC, arrays, STR
  public static final TypeMem XMEM; //
  public static final TypeMem DEAD, ALIVE, LIVE_BOT; // Sentinel for liveness flow; not part of lattice
  public static final TypeMem ESCAPE; // Sentinel for liveness, where the value "escapes" the local scope
  public static final TypeMem ANYMEM,ALLMEM; // Every alias is unused (so above XOBJ or below OBJ)
  public static final TypeMem MEM_ABC, MEM_STR;
  static {
    // Every alias is unused
    ANYMEM = make0(new TypeObj[]{null,TypeObj.UNUSED});
    ALLMEM = ANYMEM.dual();
    // All memory, all aliases, holding anything.
    FULL = make0(new TypeObj[]{null,TypeObj.OBJ});
    EMPTY= FULL.dual();

    // All memory.  Includes breakouts for all structs and all strings.
    // Triggers BitsAlias.<clinit> which makes all the initial alias splits.
    // Not currently including closures
    TypeObj[] tos = new TypeObj[Math.max(BitsAlias.RECORD,BitsAlias.ARY)+1];
    tos[BitsAlias.ALL] = TypeObj.ISUSED;
    tos[BitsAlias.RECORD]=TypeStruct.ALLSTRUCT;
    tos[BitsAlias.STR] = TypeStr.STR; //
    tos[BitsAlias.ABC] = TypeStr.ABC; //
    tos[BitsAlias.ARY] = TypeAry.ARY; //
    MEM  = make0(tos);
    XMEM = MEM.dual();

    MEM_STR = make(BitsAlias.STR,TypeStr.STR);
    MEM_ABC = make(BitsAlias.ABC,TypeStr.ABC.dual());

    // Sentinel for liveness flow; not part of lattice
    DEAD   = make_live(TypeLive.DEAD    );
    ALIVE  = make_live(TypeLive.LIVE    ); // Basic alive for all time
    ESCAPE = make_live(TypeLive.ESCAPE  ); // Alive, plus escapes some call/memory
    LIVE_BOT=make_live(TypeLive.LIVE_BOT);
  }
  static final TypeMem[] TYPES = new TypeMem[]{FULL,MEM,MEM_ABC,ANYMEM,ESCAPE};

  // All mapped memories remain, but each memory flips internally.
  @Override protected TypeMem xdual() {
    TypeObj[] pubs = new TypeObj[_pubs.length];
    for( int i = 0; i< _pubs.length; i++ )
      if( _pubs[i] != null )
        pubs[i] = (TypeObj) _pubs[i].dual();
    return new TypeMem(pubs);
  }
  @Override protected Type xmeet( Type t ) {
    if( t._type != TMEM ) return ALL;
    TypeMem tf = (TypeMem)t;
    // Meet of default values, meet of element-by-element.
    TypeObj[] as = _meet(_pubs,tf._pubs);
    TypeObj[] tos = _make1(as);
    return tos==null ? DEAD : make(tos); // All things are dead, so dead
  }

  private static TypeObj[] _meet(TypeObj[] as, TypeObj[] bs) {
    TypeObj mt_live = (TypeObj)as[0].meet(bs[0]);
    int  len = Math.max(as.length,bs.length);
    int mlen = Math.min(as.length,bs.length);
    if( mlen==1 ) {             // At least 1 is short
      // Short & low "wins": result is short.
      if( (!as[0].above_center() && as.length==1) ||
          (!bs[0].above_center() && bs.length==1) )
        return new TypeObj[]{mt_live};
    }
    TypeObj[] objs = new TypeObj[len];
    objs[0] = mt_live;
    for( int i=1; i<len; i++ )
      objs[i] = i<mlen && as[i]==null && bs[i]==null // Shortcut null-vs-null
        ? null : (TypeObj)at(as,i).meet(at(bs,i)); // meet element-by-element
    return objs;
  }

  // Shallow meet of all possible loadable values.  Used in Node.value calls, so must be monotonic.
  public TypeObj ld( TypeMemPtr ptr ) {
    if( ptr._aliases == BitsAlias.NIL.dual() || ptr._aliases == BitsAlias.NIL )
      return TypeObj.XOBJ;
    if( ptr._aliases == BitsAlias.EMPTY )
      return oob(TypeObj.OBJ);
    if( this== FULL ) return TypeObj. OBJ;
    if( this==EMPTY ) return TypeObj.XOBJ;
    return ld(_pubs,ptr._aliases);
  }
  private static TypeObj ld( TypeObj[] tos, BitsAlias aliases ) {
    boolean any = aliases.above_center();
    // Any alias, plus all of its children, are meet/joined.  This does a
    // tree-based scan on the inner loop.
    TypeObj obj1 = any ? TypeObj.ISUSED : TypeObj.UNUSED;
    for( int alias : aliases )
      for( int kid=alias; kid!=0; kid=BitsAlias.next_kid(alias,kid) ) {
        TypeObj x = at(tos,kid);
        obj1 = (TypeObj)(any ? obj1.join(x) : obj1.meet(x));
      }
    return obj1;
  }


  // Theory: filter values by live will stop the problem in test8, where
  // stale closed-cycles exist after GCP 1 (plus one round of inlining).
  // NewNode dies pre-closed-cycle, but remains live in cycle (and unused).

  // Theory: back to all-reaching and slice-reaching as 1 call, for live-mem
  // only.  All fields alive.  Assumption is this is used by ScopeNode only,
  // and Scope might read any part of reachable memory.

  // Theory: if alias arrives here, it is ISUSED in the live, even if the live
  // mem is UNUSED.
  
  // Transitively walk all reachable aliases from this set of aliases, and
  // return the complete set.
  public BitsAlias all_reaching_aliases(BitsAlias aliases) {
    if( aliases==BitsAlias.NIL || aliases==BitsAlias.EMPTY ) return BitsAlias.EMPTY;
    if( aliases==BitsAlias.FULL ) return aliases;
    AryInt work = new AryInt();
    VBitSet visit = new VBitSet();
    for( int alias : aliases )
      for( int kid=alias; kid!=0; kid = BitsAlias.next_kid(alias,kid) )
        { work.push(kid); visit.set(kid); }

    while( !work.isEmpty() ) {
      int alias=work.pop();
      if( alias==0 ) continue;
      TypeObj to = at(alias);
      if( to==TypeObj.OBJ || to==TypeObj.ISUSED )
        return BitsAlias.FULL;  // All structs with all possible pointers
      if( !(to instanceof TypeStruct) ) continue;
      TypeStruct ts = (TypeStruct)to;
      // Incomplete struct?  This is an early escapee from Parse times; more
      // fields may be added which we assume is a pointer to all.
      if( ts._open )
        return BitsAlias.FULL;  // Generic open struct points to all
      for( int i=0; i<ts._ts.length; i++ ) {
        Type fld = ts._ts[i];
        if( TypeMemPtr.OOP.isa(fld) )
          fld = TypeMemPtr.OOP;                      // All possible pointers
        if( fld instanceof TypeFunPtr ) fld = ((TypeFunPtr)fld)._disp;
        if( !(fld instanceof TypeMemPtr) ) continue; // Not a pointer, no more aliases
        if( ((TypeMemPtr)fld)._aliases.test(1) )
          return BitsAlias.FULL; // All possible pointers
        // Walk the possible pointers, and include them in the slice
        for( int ptralias : ((TypeMemPtr)fld)._aliases )
          for( int kid=ptralias; kid!=0; kid = BitsAlias.next_kid(ptralias,kid) )
            if( !visit.tset(kid) ) {
              work.push(kid);
              aliases = aliases.set(kid);
            }
      }
    }
    assert !aliases.may_nil();
    return aliases;
  }

  // Slice memory by aliases; unnamed aliases are replaced with ~use.
  public TypeMem slice_reaching_aliases(BitsAlias aliases) {
    if( aliases==BitsAlias.FULL ) return this;
    TypeObj[] tos = new TypeObj[Math.max(_pubs.length,aliases.max()+1)];
    tos[1] = at(1);
    for( int i=2; i<tos.length; i++ )
      tos[i] = aliases.test_recur(i) ? TypeObj.ISUSED : TypeObj.UNUSED;
    return make0(tos);
  }

  // Sharpen a dull pointer against this memory.
  public TypeMemPtr sharpen( TypeMemPtr dull ) {
    assert dull==dull.simple_ptr();
    if( _sharp_cache != null ) { // Check the cache first
      TypeMemPtr sharp = _sharp_cache.get(dull);
      if( sharp != null ) return sharp;
    }
    // Switch to TypeStruct for building recursive structures.
    return TypeStruct.sharpen(this,dull);
  }
  TypeMemPtr sharp_get( TypeMemPtr tmp ) { return _sharp_cache==null ? null : _sharp_cache.get(tmp); }
  TypeMemPtr sharput( TypeMemPtr dull, TypeMemPtr sharp ) {
    assert dull.interned() && sharp.interned();
    if( _sharp_cache==null ) _sharp_cache = new HashMap<>();
    _sharp_cache.put(dull,sharp);
    return sharp;               // return new not old
  }
  // Sharpen if a maybe-pointer
  @Override public Type sharptr( Type ptr ) {
    return ptr instanceof TypeMemPtr ? sharpen((TypeMemPtr)ptr) :
      (ptr instanceof TypeTuple ? ((TypeTuple)ptr).sharptr(this) : ptr);
  }

  // Widen (lose info), to make it suitable as the default memory.
  public TypeMem crush() {
    TypeObj[] oops = _pubs.clone();
    oops[0] = null;
    for( int i=1; i<oops.length; i++ )
      if( oops[i]!=null ) oops[i] = oops[i].crush();
    return TypeMem.make0(oops);
  }

  // Whole object Set at an alias.
  public TypeMem set( int alias, TypeObj obj ) {
    if( at(alias)==obj ) return this; // Shortcut
    int max = Math.max(_pubs.length,alias+1);
    TypeObj[] tos = Arrays.copyOf(_pubs,max);
    tos[0] = null;
    tos[alias] = obj;
    return make0(tos);
  }

  // Whole object Store of a New at an alias.
  // Sets the private type.
  // Lifts/sets the public type, and meets fields.
  public TypeMem st_new( int alias, TypeObj obj ) {
    TypeObj[] pubs  = _pubs ;
    TypeObj pub  = at(pubs ,alias); // Current value for alias
    if( pub==obj ) return this;     // Shortcut
    (pubs = _st_new(_pubs,pubs,alias))[alias] = (TypeObj)pub.meet(obj);
    pubs[0] = null;
    return make0(pubs);
  }
  private static TypeObj[] _st_new( TypeObj[] base, TypeObj[] as, int alias ) {
    return base==as ? Arrays.copyOf(base,Math.max(base.length,alias+1)) : as;
  }

  // Field store into a conservative set of aliases.
  public TypeMem update( BitsAlias aliases, byte fin, String fld, Type val ) {
    Ary<TypeObj> pubs  = new Ary<>(_pubs .clone());
    for( int alias : aliases )
      if( alias != 0 )
        for( int kid=alias; kid != 0; kid=BitsAlias.next_kid(alias,kid) )
          pubs.setX(kid,at(_pubs,kid).update(fin,fld,val)); // imprecise
    return make(_make1(pubs.asAry()));
  }

  // Array store into a conservative set of aliases.
  public TypeMem update( BitsAlias aliases, TypeInt idx, Type val ) {
    Ary<TypeObj> pubs  = new Ary<>(_pubs .clone());
    for( int alias : aliases )
      if( alias != 0 )
        for( int kid=alias; kid != 0; kid=BitsAlias.next_kid(alias,kid) )
          pubs.setX(kid,at(_pubs,kid).update(idx,val)); // imprecise
    return make(_make1(pubs.asAry()));
  }

  // Everything NOT in the 'escs' is flattened to UNUSED.
  public TypeMem remove_no_escapes( BitsAlias escs ) {
    int i; for( i=1; i<_pubs.length; i++ )
      if( at(i) != TypeObj.UNUSED && !escs.test_recur(i) )
        break;                         // Found a no-escape to remove
    if( i==_pubs.length ) return this; // Already flattened
    TypeObj[] tos = new TypeObj[Math.max(_pubs.length,escs.max()+1)];
    for( i=1; i<tos.length; i++ )
      tos[i] = escs.test_recur(i) ? at(i) : TypeObj.UNUSED;
    return make0(tos);
  }

  // Everything in the 'escs' set is flattened to UNUSED.
  public TypeMem remove(BitsAlias escs) {
    if( escs==BitsAlias.EMPTY ) return this;
    if( escs==BitsAlias.FULL  ) throw com.cliffc.aa.AA.unimpl(); // Shortcut
    TypeObj[] tos = _pubs.clone();
    for( int i = 1; i< _pubs.length; i++ )
      if( escs.test(i) )
        tos[i] = TypeObj.UNUSED;
    return make0(tos);
  }

  // Report back only those aliases that are also UNUSED
  public BitsAlias and_unused(BitsAlias escs) {
    int len = Math.max(_pubs.length,escs.max()+1);
    BitsAlias bs = BitsAlias.EMPTY;
    for( int i=1; i<len; i++ )
      if( at(i)==TypeObj.UNUSED && escs.test_recur(i) )
        bs = bs.set(i);
    return bs;
  }

  // True if field is final across all aliases
  public boolean fld_is_final(BitsAlias aliases, String fld) {
    for( int alias : aliases ) {
      if( alias != 0 ) {
        TypeObj to = at(alias);
        if( !(to instanceof TypeStruct) ) return false;
        TypeStruct ts = (TypeStruct)to;
        int idx = ts.find(fld);
        if( idx == -1 || ts.fmod(idx) != TypeStruct.FFNL )
          return false;
      }
    }
    return true;
  }

  public TypeMem flatten_fields() {
    TypeObj to, tof=null;
    int i; for( i=1; i< _pubs.length; i++ ) {
      if( (to = _pubs[i]) != null && (tof = to.flatten_fields())!=to )
        break;
    }
    if( i== _pubs.length ) return this;

    TypeObj[] tos = _pubs.clone();
    tos[0] = null;
    tos[i++] = tof;
    for( ; i< _pubs.length; i++ )
      if( tos[i] != null )
        tos[i] = tos[i].flatten_fields();
    return make0(tos);
  }


  @Override public boolean above_center() {
    for( TypeObj alias : _pubs )
      if( alias != null && !alias.above_center() && !alias.is_con() )
        return false;
    return true;
  }
  @Override public boolean may_be_con()   { return false;}
  @Override public boolean is_con()       { return false;}
  @Override public boolean must_nil() { return false; } // never a nil
  @Override Type not_nil() { return this; }

  public TypeLive live() { return (TypeLive)_pubs[0]; }
  public boolean is_live() { return live()!=TypeLive.DEAD; }
  public boolean basic_live() { return _pubs.length==1; }

  // Lift memory types as high as what is live
  @Override public Type lift_live(TypeMem live) { return live==DEAD ? ANYMEM : join(live); }

}
