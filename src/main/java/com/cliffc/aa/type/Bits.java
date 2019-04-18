package com.cliffc.aa.type;

import com.cliffc.aa.AA;
import com.cliffc.aa.util.Ary;
import com.cliffc.aa.util.SB;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

// Bits supporting a lattice; immutable; hash-cons'd.
public abstract class Bits implements Iterable<Integer> {
  // Holds a set of bits meet'd together, or join'd together, along
  // with an infinite extent or a single bit choice as a constant.
  //
  // If _bits is NULL, then _con holds the single set bit (including 0).
  // If _bits is not-null, then _con is -2 for meet, and -1 for join.
  // The last bit of _bits is the "sign" bit, and extends infinitely.
  long[] _bits;   // Bits set or null for a single bit
  int _con;       // value of single bit, or -2 for meet or -1 for join
  int _hash;      // Pre-computed hashcode
  void init(int con, long[] bits ) {
    if( bits==null ) assert con >= 0;
    else             assert con==-2 || con==-1;
    _con = con;
    _bits=bits;
    _hash=compute_hash();
  }
  int compute_hash() {
    int sum=_con;
    if( _bits != null ) for( long bit : _bits ) sum += bit;
    return sum;
  }
  @Override public int hashCode( ) { return _hash; }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof Bits) ) return false;
    Bits bs = (Bits)o;
    if( _con != bs._con || _hash != bs._hash ) return false;
    if( _bits == bs._bits ) return true;
    if( _bits ==null || bs._bits==null ) return false;
    if( _bits.length != bs._bits.length ) return false;
    for( int i=0; i<_bits.length; i++ )
      if( _bits[i]!=bs._bits[i] ) return false;
    return true;
  }
  @Override public String toString() { return toString(new SB()).toString(); }
  public SB toString(SB sb) {
    if( this==FULL() ) return sb.p("[ALL]");
    else if( _con==-1 && _bits.length==1 && _bits[0]==-1 ) return sb.p("[~ALL]");
    sb.p('[');
    if( _bits==null ) sb.p(_con);
    else {
      for( Integer idx : this ) sb.p(idx).p(above_center()?'+':',');
    }
    if( inf() ) sb.p("...");
    return sb.p(']');
  }

  // Intern: lookup and return an existing Bits or install in hashmap and
  // return a new Bits.  Overridden in subclasses to make type-specific Bits.
  private static HashMap<Bits,Bits> INTERN = new HashMap<>();
  private static Bits FREE=null;
  abstract Bits make_impl(int con, long[] bits );
  
  // Constructor taking an array of bits, and allowing join/meet selection.
  // The 'this' pointer is only used to clone the class.
  final Bits make( int con, long[] bits ) {
    assert con==-2 || con==-1;
    int len = bits.length;
    while( len > 0 && (bits[len-1]==0 || bits[len-1]== -1) ) len--;
    if( len != bits.length ) throw AA.unimpl(); // TODO: remove trailing sign-extend words
    long b = bits[len-1];
    if( (b & (b-1))==0 ) {      // Last word has only a single bit
      for( int i=0; i<len-1; i++ )
        if( bits[i] != 0 )
          return make_impl(con,bits);
      con = 63-Long.numberOfLeadingZeros(b);
      return make_impl(con,null);    // Single bit in last word only, switch to con version
    }
    return make_impl(con,bits);
  }
  // Constructor taking a list of bits; bits are 'meet'.
  final Bits make( int... bits ) {
    long[] ls = new long[1];
    for( int bit : bits ) {
      if( bit < 0 ) throw new IllegalArgumentException("bit must be positive");
      if( bit >= 63 ) throw AA.unimpl();
      ls[0] |= 1L<<bit;
    }
    return make(-2,ls);
  }
  // Constructor taking a single bit
  final Bits make( int bit ) {
    if( bit < 0 ) throw new IllegalArgumentException("bit must be positive");
    if( bit >= 63 ) throw AA.unimpl();
    return make_impl(bit,null);
  }
  
  public abstract Bits FULL();
  public abstract Bits ANY ();
  
  private static int  idx (int i) { return i>>6; }
  private static long mask(int i) { return 1L<<(i&63); }
  public  boolean inf() { return _bits !=null && (_bits[_bits.length-1]>>63)==-1; }
  
  int getbit() { assert _bits==null; return _con; }
  public int   abit() { return _bits==null ? _con : -1; }
  public boolean is_con() { return _bits==null; }
  public boolean above_center() { return _con==-1; }
  boolean may_nil() { return _con==0 || (_con==-1 && ((_bits[0]&1) == 1)); }

  // Test a specific bit is set or clear
  public boolean test(int i) {
    if( _bits==null ) return i==_con;
    int idx = idx(i);
    return idx < _bits.length ? (_bits[idx]&mask(i))!=0 : inf();
  }
  public Bits clear(int i) {
    if( !test(i) ) return this;  // Already clear
    if( _con==i ) return make(); // No bits set???
    assert _con<0;
    int idx = idx(i);
    long[] bits = _bits.clone();
    bits[idx] &= ~mask(i);
    return make(_con,bits);
  }
  
  private int max() { return (_bits.length<<6)-1; }
  private static void or( long[] bits, int con ) { bits[idx(con)] |= mask(con); }
  private static long[] bits( int a, int b ) { return new long[idx(Math.max(a,b))+1]; }
  
  public Bits meet( Bits bs ) {
    if( this==bs ) return this;
    Bits full = FULL();         // Subclass-specific version of full
    if( this==full || bs==full ) return full;
    Bits any  = ANY ();         // Subclass-specific version of any
    if( this==any ) return bs;
    if( bs  ==any ) return this;
    if( _bits==null || bs._bits==null ) { // One or both are constants
      Bits conbs = this, bigbs = bs;
      if( bs._bits==null ) { conbs = bs;  bigbs = this; }
      if( bigbs._bits==null ) { // both constants
        // two constants; make a big enough bits array for both
        long[] bits = bits(conbs._con,bigbs._con);
        or( bits,conbs._con);
        or( bits,bigbs._con);
        Bits bs0 = make(-2,bits);
        assert !bs0.inf(); // didn't set sign bit by accident (need bigger array if so)
        return bs0;
      }
      
      if( bigbs._con==-2 ) {     // Meet of constant and set
        if( bigbs.test(conbs._con) ) return bigbs; // already a member
        // Grow set to hold constant and OR it in
        long[] bits = bits(bigbs.max(),conbs._con);
        System.arraycopy(bigbs._bits,0,bits,0,bigbs._bits.length);
        or( bits,conbs._con);
        Bits bs0 = make(-2,bits);
        assert bs0.inf()==bigbs.inf();
        return bs0;
      }
      // Meet of constant and joined set
      if( bigbs.test(conbs._con) ) return conbs; // Already a member
      // Pick first non-zero bit, and meet with constant
      if( conbs._con >= 64 ) throw AA.unimpl();
      for( int e : bigbs )
        if( e != 0 ) {
          if( e >= 64 ) throw AA.unimpl();
          return make(-2,new long[]{(1L<<e) | (1L<<conbs._con)});
        }
      throw AA.unimpl(); // Should not reach here
    }

    if( _con==-2 ) {            // Meet
      if( bs._con==-2 ) {
        Bits smlbs = this, bigbs = bs;
        if( smlbs._bits.length > bigbs._bits.length ) { smlbs=bs; bigbs=this; }
        long[] bits = bigbs._bits.clone();
        for( int i=0; i<smlbs._bits.length; i++ ) bits[i]|=smlbs._bits[i];
        return make(-2,bits);

      } else {                  // Meet of a high set and low set
        // Probably require 1 bit from high set in the low set.
        // For now, just return low set
        return this;
      }
    }
    if( bs._con==-2 ) { // Meet of a low set and high set
      // Probably require 1 bit from high set in the low set.
      // For now, just return low set
      return bs;
    }

    // join of 2 sets; return intersection
    if(    subset(bs  ) ) return this;
    if( bs.subset(this) ) return bs  ;

    // join of 2 joined sets.  OR all bits together.
    Bits smlbs = this, bigbs = bs;
    if( smlbs._bits.length > bigbs._bits.length ) { smlbs=bs; bigbs=this; }
    long[] bits =  bigbs._bits.clone();
    for( int i=0; i<smlbs._bits.length; i++ )
      bits[i] |= smlbs._bits[i];
    return make(-1,bits);
  }
  
  private boolean subset( Bits bs ) {
    if( _bits.length > bs._bits.length ) return false;
    for( int i=0; i<_bits.length; i++ )
      if( (_bits[i]&bs._bits[i]) != _bits[i] )
        return false;
    return true;
  }
  
  public Bits dual() {
    if( _bits==null ) return this; // Dual of a constant is itself
    // Otherwise just flip _con
    assert _con==-2 || _con==-1;
    return make_impl(-3-_con,_bits);
  }
  // join is defined in terms of meet and dual
  public Bits join(Bits bs) { return dual().meet(bs.dual()).dual(); }

  /** @return an iterator */
  @NotNull @Override public Iterator<Integer> iterator() { return new Iter(); }
  private class Iter implements Iterator<Integer> {
    int _i=-1;
    @Override public boolean hasNext() {
      if( _bits==null )
        if( _i==-1 ) { _i=0; return true; } else return false;
      int idx;
      while( (idx=idx(++_i)) < _bits.length )
        if( (_bits[idx]&mask(_i)) != 0 )
          return true;
      return false;
    }
    @Override public Integer next() {
      if( _bits==null ) return _con;
      if( idx(_i) < _bits.length ) return _i;
      throw new NoSuchElementException();
    }
  }

  // Smalltalk-style "becomes"!!!!

  // Convert everywhere a type X to a type Y!!!
  
  // Conceptually, each alias# represents an infinite set of pointers - broken
  // into equivalence classes.  We can split such a class in half - some
  // pointers will go left and some go right, and where we can't tell we'll use
  // both sets.  Any alias set is a tree-like nested set of sets bottoming out
  // in individual pointers.  The types are conceptually unchanged if we start
  // using e.g. 2 alias#s instead of 1 everywhere - we've just explicitly named
  // the next layer in the tree-of-sets.
  
  // Split an existing alias# in half, such that some ptrs point to one half or
  // the other, and most point to either (or both).  Basically find all
  // references to alias#X and add a new alias#Y paired with X - making all
  // alias types use both equally.  Leave the base constructor of an X alias
  // (some NewNode) alone - it still produces just an X.  The Node calling
  // split_alias gets Y alone, and the system as a whole makes a conservative
  // approximation that {XY} are always confused.  Afterwards we can lift the
  // types to refine as needed.
  
  // Do this "cheaply"!  I can think of 2 approaches: (1) visit all Types in
  // the GVN type array replacing TypeMem[Ptr]{alias#X} with {alias#XY}, or (2)
  // update the Types themselves.  Due to the interning, it suffices to swap
  // all the Alias Bits for Bits with Y# set.  Bits are used for both Alias and
  // RPC and FIDXs so we'd need separate intern sets for these.

  static <B extends Bits> int split( int a1, HashMap<B, B> intern ) {
    for( Type t : Type.INTERN.keySet() )
      assert t.compute_hash()==t._hash && Type.INTERN.get(t)==t;
    // I think its important to log these changes over time, so I can track/debug.
    int a2 = Type.new_alias();
    System.out.println("Alias split "+a1+" into {"+a1+","+a2+"}");

    // For now voting for the BitsAlias hack.

    // Walk the given intern table, and add a2 to whereever a1 appears.
    B[] bits = (B[])intern.keySet().toArray(new Bits[0]); // Copy to array
    for( B b : bits ) {
      if( b.test(a1) == b.test(a2) ) continue;
      long[] bs = b._bits;
      if( bs==null ) {          // Only a single constant bit
        b._con = -2;            // Become a MEET of 2 bits
        b._bits = bits(a1,a2);  // Big enuf for both bits
        b._bits[idx(a1)] |= mask(a1);
        b._bits[idx(a2)] |= mask(a2);
      } else {                  // An array of bits already
        int i1 = idx(a1);
        if( i1 >= bits.length ) // Need to grow
          throw AA.unimpl();
        int i2 = idx(a2);
        if( (bs[i1]&mask(a1))==0 ) bs[i2] &= mask(a2);
        else                       bs[i2] |= mask(a2);
      }
      b._hash = b.compute_hash(); // Set new hashcode
    }
    // Re-intern
    intern.clear();
    for( B b : bits ) intern.put(b,b);
    return a2;
  }
}
