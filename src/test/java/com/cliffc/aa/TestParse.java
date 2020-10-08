package com.cliffc.aa;

import com.cliffc.aa.type.*;
import com.cliffc.aa.util.SB;
import com.cliffc.aa.util.VBitSet;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.*;

public class TestParse {
  private static final String[] FLDS = new String[]{"^","n","v"};
  private static final BitsFun TEST_FUNBITS = BitsFun.make0(46);

  // temp/junk holder for "instant" junits, when debugged moved into other tests
  @Test public void testParse() {
    TypeStruct dummy = TypeStruct.DISPLAY;
    TypeMemPtr tdisp = TypeMemPtr.make(BitsAlias.make0(2),TypeObj.ISUSED);
    test_obj("\"Hello, world\"", TypeStr.con("Hello, world"));
    // User-defined linked list.
    String ll_def = "List=:@{next;val};";
    String ll_con = "tmp=List(List(0,1.2),2.3);";
    String ll_map = "map = {fun list -> list ? List(map(fun,list.next),fun(list.val)) : 0};";
    String ll_fun = "sq = {x -> x*x};";
    String ll_apl = "map(sq,tmp);";

    // TODO: Needs a way to easily test simple recursive types
    TypeEnv te4 = Exec.go(Env.file_scope(Env.top_scope()),"args",ll_def+ll_con+ll_map+ll_fun+ll_apl);

    // fails, oldval not defined on false arm of trinary
    //test("_tab = [7];\n" +
    //     "put = { key val ->\n" +
    //     "  idx = key.hash() % #_tab;\n" +
    //     "  entry = _tab[idx];\n" +
    //     "  entry && key.eq(entry.key) ? (oldval=entry.val; entry.val:=val; ^oldval);\n" +
    //     "  0\n" +
    //     "};\n" +
    //     "put(\"Monday\",1);\n",
    //     Type.XNIL);

    // A collection of tests which like to fail easily
    test("-1",  TypeInt.con( -1));
    test("{5}()", TypeInt.con(5)); // No args nor -> required; this is simply a function returning 5, being executed
    testerr("x=1+y","Unknown ref 'y'",4);
    test_obj("str(3.14)", TypeStr.con("3.14"));
    test("math_rand(1)?x=4:x=3;x", TypeInt.NINT8); // x defined on both arms, so available after
    test_ptr("x=@{n:=1;v:=2}; x.n := 3; x", "@{n:=3; v:=2}");
    test("x=3; mul2={x -> x*2}; mul2(2.1)", TypeFlt.con(2.1*2.0)); // must inline to resolve overload {*}:Flt with I->F conversion
    testerr("sq={x -> x*x}; sq(\"abc\")", "*\"abc\" is none of (flt64,int64)",9);
    test("fun:{int str -> int}={x y -> x+2}; fun(2,3)", TypeInt.con(4));
    testerr("math_rand(1)?x=2: 3 ;y=x+2;y", "'x' not defined on false arm of trinary",20);
    testerr("{+}(1,2,3)", "Passing 3 arguments to {+} which takes 2 arguments",3);
    test_isa("{x y -> x+y}", TypeFunPtr.make(TEST_FUNBITS,3,tdisp)); // {Scalar Scalar -> Scalar}
    testerr("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1})", "Unknown field '.y'",19);
    testerr("Point=:@{x;y}; Point((0,1))", "*(0; 1) is not a *Point:@{x:=; y:=}",21);
    test("x=@{a:=1;b= {a=a+1;b=0}}; x.b(); x.a",TypeInt.con(2));
    test("x=@{a:=1;noinline_b= {a=a+1;b=0}}; x.noinline_b(); x.a",TypeInt.NINT8);

    test("f0 = { f x -> x ? f(f0(f,x-1),1) : 0 }; f0({&},2)", Type.XNIL);
    test("fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact(1)",TypeInt.con(1));
    test("fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact(3)",TypeInt.con(6));
    test("is_even = { n -> n ? is_odd(n-1) : 1}; is_odd = {n -> n ? is_even(n-1) : 0}; is_even(4)", TypeInt.BOOL );
    test("fib = { x -> x <= 1 ? 1 : fib(x-1)+fib(x-2) }; fib(1)",TypeInt.con(1));
    test("fib = { x -> x <= 1 ? 1 : fib(x-1)+fib(x-2) }; fib(4)",TypeInt.con(5));
    test("A= :@{n=A?; v=flt}; f={x:A? -> x ? A(f(x.n),x.v*x.v) : 0}; f(A(0,1.2)).v;", TypeFlt.con(1.2*1.2));
  }

  @Test public void testParse00() {
    // Simple int
    test("1",   TypeInt.TRUE);
    // Unary operator
    test("-1",  TypeInt.con( -1));
    test("!1",  Type.XNIL);
    // Binary operators
    test("1+2", TypeInt.con(  3));
    test("1-2", TypeInt.con( -1));
    test("1+2*3", TypeInt.con(  7));
    test("1  < 2", TypeInt.TRUE );
    test("1  <=2", TypeInt.TRUE );
    test("1  > 2", TypeInt.FALSE);
    test("1  >=2", TypeInt.FALSE);
    test("1  ==2", TypeInt.FALSE);
    test("1  !=2", TypeInt.TRUE );
    test("1.2< 2", TypeInt.TRUE );
    test("1.2<=2", TypeInt.TRUE );
    test("1.2> 2", TypeInt.FALSE);
    test("1.2>=2", TypeInt.FALSE);
    test("1.2==2", TypeInt.FALSE);
    test("1.2!=2", TypeInt.TRUE );

    // Binary with precedence check
    test(" 1+2 * 3+4 *5", TypeInt.con( 27));
    test("(1+2)*(3+4)*5", TypeInt.con(105));
    test("1// some comment\n+2", TypeInt.con(3)); // With bad comment
    test("-1-2*3-4*5", TypeInt.con(-1-(2*3)-(4*5)));
    test("1&3|1&2", TypeInt.con(1));

    // Float
    test("1.2+3.4", TypeFlt.make(0,64,4.6));
    // Mixed int/float with conversion
    test("1+2.3",   TypeFlt.make(0,64,3.3));

    // Simple strings
    test_obj("\"Hello, world\"", TypeStr.con("Hello, world"));
    test_obj("str(3.14)"       , TypeStr.con("3.14"));
    test_obj("str(3)"          , TypeStr.con("3"   ));
    test_obj("str(\"abc\")"    , TypeStr.ABC);

    // Variable lookup
    test("math_pi", TypeFlt.PI);
    // bare function lookup; returns a union of '+' functions
    testerr("+", "Syntax error; trailing junk",0);
    testerr("!", "Syntax error; trailing junk",0);
    test_prim("{+}", "+");
    test_prim("{!}", "!"); // uniops are just like normal functions
    // Function application, traditional paren/comma args
    test("{+}(1,2)", TypeInt.con( 3));
    test("{-}(1,2)", TypeInt.con(-1)); // binary version
    test(" - (1  )", TypeInt.con(-1)); // unary version
    // error; mismatch arg count
    testerr("{!}()     ", "Passing 0 arguments to {!} which takes 1 arguments",3);
    testerr("math_pi(1)", "A function is being called, but 3.141592653589793 is not a function",10);
    testerr("{+}(1,2,3)", "Passing 3 arguments to {+} which takes 2 arguments",3);

    // Parsed as +(1,(2*3))
    test("{+}(1, 2 * 3) ", TypeInt.con(7));
    // Parsed as +( (1+2*3) , (4*5+6) )
    test("{+}(1 + 2 * 3, 4 * 5 + 6) ", TypeInt.con(33));
    // Statements
    test("(1;2 )", TypeInt.con(2));
    test("(1;2;)", TypeInt.con(2)); // final semicolon is optional
    test("{+}(1;2 ,3)", TypeInt.con(5)); // statements in arguments
    test("{+}(1;2;,3)", TypeInt.con(5)); // statements in arguments
    // Operators squished together
    test("-1== -1",  TypeInt.TRUE);
    test("0== !!1",  TypeInt.FALSE);
    test("2==-1",    TypeInt.FALSE);
    test("-1== --1", TypeInt.FALSE);
    test("-1== ---1",TypeInt.TRUE);
    testerr("-1== --", "Missing term after '=='",5);
  }

  @Test public void testParse01() {
    // Syntax for variable assignment
    test("x=1", TypeInt.TRUE);
    test("x=y=1", TypeInt.TRUE);
    testerr("x=y=", "Missing ifex after assignment of 'y'",4);
    testerr("x=z" , "Unknown ref 'z'",2);
    testerr("x=1+y","Unknown ref 'y'",4);
    testerr("x=y; x=y","Unknown ref 'y'",2);
    test("x=2; y=x+1; x*y", TypeInt.con(6));
    // Re-use ref immediately after def; parses as: x=(2*3); 1+x+x*x
    test("1+(x=2*3)+x*x", TypeInt.con(1+6+6*6));
    testerr("x=(1+(x=2)+x); x", "Cannot re-assign final val 'x'",0);
    test("x:=1;x++"  ,TypeInt.con(1));
    test("x:=1;x++;x",TypeInt.con(2));
    test("x:=1;x++ + x--",TypeInt.con(3));
    test("x++",Type.XNIL);
    test("x++;x",TypeInt.con(1));

    // Conditional:
    test   ("0 ?    2  : 3", TypeInt.con(3)); // false
    test   ("2 ?    2  : 3", TypeInt.con(2)); // true
    test   ("math_rand(1)?x=4:x=3;x", TypeInt.NINT8); // x defined on both arms, so available after
    test   ("math_rand(1)?x=2:  3;4", TypeInt.con(4)); // x-defined on 1 side only, but not used thereafter
    test   ("math_rand(1)?(y=2;x=y*y):x=3;x", TypeInt.NINT8); // x defined on both arms, so available after, while y is not
    testerr("math_rand(1)?x=2: 3 ;x", "'x' not defined on false arm of trinary",20);
    testerr("math_rand(1)?x=2: 3 ;y=x+2;y", "'x' not defined on false arm of trinary",20);
    testerr("0 ? x=2 : 3;x", "'x' not defined on false arm of trinary",11);
    test   ("2 ? x=2 : 3;x", TypeInt.con(2)); // off-side is constant-dead, so missing x-assign is ignored
    test   ("2 ? x=2 : y  ", TypeInt.con(2)); // off-side is constant-dead, so missing 'y'      is ignored
    testerr("x=1;2?(x=2):(x=3);x", "Cannot re-assign final val 'x'",7);
    test   ("x=1;2?   2 :(x=3);x",TypeInt.con(1)); // Re-assigned allowed & ignored in dead branch
    test   ("math_rand(1)?1:int:2:int",TypeInt.NINT8); // no ambiguity between conditionals and type annotations
    testerr("math_rand(1)?1: :2:int","missing expr after ':'",16); // missing type
    testerr("math_rand(1)?1::2:int","missing expr after ':'",15); // missing type
    testerr("math_rand(1)?1:\"a\"", "Cannot mix GC and non-GC types",18);
    test   ("math_rand(1)?1",TypeInt.BOOL); // Missing optional else defaults to nil
    test_ptr0("math_rand(1)?\"abc\"", (alias)->TypeMemPtr.make_nil(alias,TypeStr.ABC));
    test   ("x:=0;math_rand(1)?(x:=1);x",TypeInt.BOOL);
    testerr("a.b.c();","Unknown ref 'a'",0);
  }

  // Short-circuit tests
  @Test public void testParse01a() {

    test("0 && 0", Type.XNIL);
    test("1 && 2", TypeInt.con(2));
    test("0 && 2", Type.XNIL);
    test("0 || 0", Type.XNIL);
    test("0 || 2", TypeInt.con(2));
    test("1 || 2", TypeInt.con(1));
    test("0 && 1 || 2 && 3", TypeInt.con(3));    // Precedence

    test_obj("x:=y:=0; z=x++ && y++;(x,y,z)", // increments x, but it starts zero, so y never increments
             TypeStruct.make_tuple(Type.XNIL,TypeInt.con(1),Type.XNIL,Type.XNIL));
    test_obj("x:=y:=0; x++ && y++; z=x++ && y++; (x,y,z)", // x++; x++; y++; (2,1,0)
      TypeStruct.make_tuple(Type.XNIL,TypeInt.con(2),TypeInt.con(1),Type.XNIL));
    test("(x=1) && x+2", TypeInt.con(3)); // Def x in 1st position

    testerr("1 && (x=2;0) || x+3 && x+4", "'x' not defined prior to the short-circuit",5); // x maybe alive
    testerr("0 && (x=2;0) || x+3 && x+4", "'x' not defined prior to the short-circuit",5); // x definitely not alive
    test("math_rand(1) && (x=2;x*x) || 3 && 4", TypeInt.INT8); // local use of x in short-circuit; requires unzip to find 4

  }

  @Test public void testParse02() {
    TypeStruct dummy = TypeStruct.DISPLAY;
    test("{5}()", TypeInt.con(5)); // No args nor -> required; this is simply a function returning 5, being executed
    // Since call not-taken, post GCP Parms not loaded from _tf, limited to ~Scalar.  The
    // hidden internal call from {&} to the primitive is never inlined (has ~Scalar args)
    // so 'x&1' never sees the TypeInt return from primitive AND.
    TypeMemPtr tdisp = TypeMemPtr.make(BitsAlias.make0(12),TypeObj.ISUSED);
    test_isa("{x -> x&1}", TypeFunPtr.make(TEST_FUNBITS,2,tdisp)); // {Int -> Int}

    // Anonymous function definition
    test_isa("{x y -> x+y}", TypeFunPtr.make(TEST_FUNBITS,3,tdisp)); // {Scalar Scalar -> Scalar}

    // ID in different contexts; in general requires a new TypeVar per use; for
    // such a small function it is always inlined completely, has the same effect.
    test_prim("id", "id");
    test("id(1)",TypeInt.con(1));
    test("id(3.14)",TypeFlt.con(3.14));
    test_prim("id({+})","+");
    test("id({+})(id(1),id(math_pi))",TypeFlt.make(0,64,Math.PI+1));

    // Function execution and result typing
    test("x=3; andx={y -> x & y}; andx(2)", TypeInt.con(2)); // trivially inlined; capture external variable
    test("x=3; and2={x -> x & 2}; and2(x)", TypeInt.con(2)); // trivially inlined; shadow  external variable
    testerr("plus2={x -> x+2}; x", "Unknown ref 'x'",18); // Scope exit ends lifetime
    testerr("fun={x -> }; fun(0)", "Missing function body",10);
    testerr("fun(2)", "Unknown ref 'fun'", 0);
    test("mul3={x -> y=3; x*y}; mul3(2)", TypeInt.con(6)); // multiple statements in func body
    // Needs overload cloning/inlining to resolve {+}
    test("x=3; addx={y -> x+y}; addx(2)", TypeInt.con(5)); // must inline to resolve overload {+}:Int
    test("x=3; mul2={x -> x*2}; mul2(2.1)", TypeFlt.con(2.1*2.0)); // must inline to resolve overload {*}:Flt with I->F conversion
    test("x=3; mul2={x -> x*2}; mul2(2.1)+mul2(x)", TypeFlt.con(2.1*2.0+3*2)); // Mix of types to mul2(), mix of {*} operators
    test("sq={x -> x*x}; sq 2.1", TypeFlt.con(4.41)); // No () required for single args
    testerr("sq={x -> x&x}; sq(\"abc\")", "*\"abc\" is not a int64",9);
    testerr("sq={x -> x*x}; sq(\"abc\")", "*\"abc\" is none of (flt64,int64)",9);
    testerr("f0 = { f x -> f0(x-1) }; f0({+},2)", "Passing 1 arguments to f0 which takes 2 arguments",16);
    // Recursive:
    test("fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact(3)",TypeInt.con(6));
    test("fib = { x -> x <= 1 ? 1 : fib(x-1)+fib(x-2) }; fib(4)",TypeInt.con(5));
    test("f0 = { x -> x ? {+}(f0(x-1),1) : 0 }; f0(2)", TypeInt.con(2));
    testerr("fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact()","Passing 0 arguments to fact which takes 1 arguments",46);
    test_obj("fact = { x -> x <= 1 ? x : x*fact(x-1) }; (fact(0),fact(1),fact(2))",
             TypeStruct.make_tuple(Type.XNIL,Type.XNIL,TypeInt.con(1),TypeInt.con(2)));

    // Co-recursion requires parallel assignment & type inference across a lexical scope
    test("is_even = { n -> n ? is_odd(n-1) : 1}; is_odd = {n -> n ? is_even(n-1) : 0}; is_even(4)", TypeInt.BOOL );
    test("is_even = { n -> n ? is_odd(n-1) : 1}; is_odd = {n -> n ? is_even(n-1) : 0}; is_even(5)", TypeInt.BOOL );

    // This test merges 2 TypeFunPtrs in a Phi, and then fails to resolve.
    testerr("(math_rand(1) ? {+} : {*})(2,3)","Unable to resolve call",26); // either 2+3 or 2*3, or {5,6} which is INT8.
  }

  @Test public void testParse03() {
    // Type annotations
    test("-1:int", TypeInt.con( -1));
    test("(1+2.3):flt", TypeFlt.make(0,64,3.3));
    test("x:int = 1", TypeInt.TRUE);
    test("x:flt = 1", TypeInt.TRUE); // casts for free to a float
    testerr("x:flt32 = 123456789", "123456789 is not a flt32",1);
    testerr("1:","Syntax error; trailing junk",1); // missing type
    testerr("2:x", "Syntax error; trailing junk", 1);
    testerr("(2:)", "Syntax error; trailing junk", 2);

    test   (" -1 :int1", TypeInt.con(-1));
    testerr("(-1):int1", "-1 is not a int1",4);
    testerr("\"abc\":int", "*\"abc\" is not a int64",5);
    testerr("1:str", "1 is not a *str",1);

    test   ("{x:int -> x*2}(1)", TypeInt.con(2)); // Types on parms
    testerr("{x:str -> x}(1)", "1 is not a *str", 13);

    // Type annotations on dead args are ignored
    test   ("fun:{int str -> int}={x y -> x+2}; fun(2,3)", TypeInt.con(4));
    testerr("fun:{int str -> int}={x y -> x+y}; fun(2,3)", "3 is not a *str",41);
    // Test that the type-check is on the variable and not the function.
    test_obj("fun={x y -> x*2}; bar:{int str -> int} = fun; baz:{int @{x;y} -> int} = fun; (fun(2,3),bar(2,\"abc\"))",
             TypeStruct.make_tuple(Type.XNIL,TypeInt.con(4),TypeInt.con(4)));
    testerr("fun={x y -> x+y}; baz:{int @{x;y} -> int} = fun; (fun(2,3), baz(2,3))",
            "3 is not a *@{x:=; y:=; ...}", 66);
    testerr("fun={x y -> x+y}; baz={x:int y:@{x;y} -> foo(x,y)}; (fun(2,3), baz(2,3))",
            "Unknown ref 'foo'", 41);
    // This test failed because the inner fun does not inline until GCP,
    // and then it resolves and lifts the DISPLAY (which after resolution
    // is no longer needed).  Means: cannot resolve during GCP and preserve
    // monotonicity.  Would like '.fun' to load BEFORE GCP.
    testerr("fun={x y -> x+y}; baz={x:int y:@{x;y} -> fun(x,y)}; (fun(2,3), baz(2,3))",
            "3 is not a *@{x:=; y:=; ...}", 69);

    testerr("x=3; fun:{int->int}={x -> x*2}; fun(2.1)+fun(x)", "2.1 is not a int64",36);
    test("x=3; fun:{real->real}={x -> x*2}; fun(2.1)+fun(x)", TypeFlt.con(2.1*2+3*2)); // Mix of types to fun()
    test("fun:{real->flt32}={x -> x}; fun(123 )", TypeInt.con(123 ));
    test("fun:{real->flt32}={x -> x}; fun(0.125)", TypeFlt.con(0.125));
    testerr("fun:{real->flt32}={x -> x}; fun(123456789)", "123456789 is not a flt32",3);

    // Named types
    test_name("A= :(       )" ); // Zero-length tuple
    test_name("A= :(   ,   )", Type.SCALAR); // One-length tuple
    test_name("A= :(   ,  ,)", Type.SCALAR  ,Type.SCALAR  );
    test_name("A= :(flt,   )", TypeFlt.FLT64 );
    test_name("A= :(flt,int)", TypeFlt.FLT64,TypeInt.INT64);
    test_name("A= :(   ,int)", Type.SCALAR  ,TypeInt.INT64);

    test_ptr("A= :(str?, int); A( \"abc\",2 )","A:(*\"abc\"; 2)");
    test_ptr("A= :(str?, int); A( (\"abc\",2) )","A:(*\"abc\"; 2)");
    testerr("A= :(str?, int)?","Named types are never nil",16);
  }

  @Test public void testParse04() {
    TypeStruct dummy = TypeStruct.DISPLAY;
    // simple anon struct tests
    testerr("a=@{x=1.2;y}; x", "Unknown ref 'x'",14);
    testerr("a=@{x=1;x=2}.x", "Cannot re-assign final field '.x'",8);
    test   ("a=@{x=1.2;y;}; a.x", TypeFlt.con(1.2)); // standard "." field naming; trailing semicolon optional
    test_ptr("x=@{n:=1;v:=2}; x.n := 3; x", "@{n:=3; v:=2}");
    testerr("(a=@{x=0;y=0}; a.)", "Missing field name after '.'",17);
    testerr("a=@{x=0;y=0}; a.x=1; a","Cannot re-assign final field '.x'",16);
    test   ("a=@{x=0;y=1}; b=@{x=2}  ; c=math_rand(1)?a:b; c.x", TypeInt.INT8); // either 0 or 2; structs can be partially merged
    testerr("a=@{x=0;y=1}; b=@{x=2}; c=math_rand(1)?a:b; c.y",  "Unknown field '.y'",46);
    testerr("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1})", "Unknown field '.y'",19);
    test   ("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1;y=2})", TypeInt.con(5));     // passed in to func
    test   ("dist={p->p.x*p.x+p.y*p.y}; dist(@{x=1;y=2;z=3})", TypeInt.con(5)); // extra fields OK
    test   ("dist={p:@{x;y} -> p.x*p.x+p.y*p.y}; dist(@{x:=1;y:=2})", TypeInt.con(5)); // Typed func arg
    test   ("a=@{x=(b=1.2)*b;y=b}; a.y", TypeFlt.con(1.2 )); // ok to use temp defs
    test   ("a=@{x=(b=1.2)*b;y=x}; a.y", TypeFlt.con(1.44)); // ok to use early fields in later defs
    testerr("a=@{x=(b=1.2)*b;y=b}; b", "Unknown ref 'b'",22);
    test   ("t=@{n=0;val=1.2}; u=math_rand(1) ? t : @{n=t;val=2.3}; u.val", TypeFlt.NFLT64); // structs merge field-by-field
    // Comments in the middle of a struct decl
    test   ("dist={p->p//qqq\n.//qqq\nx*p.x+p.y*p.y}; dist(//qqq\n@{x//qqq\n=1;y=2})", TypeInt.con(5));

    // Lexical scoping.  Struct assignments make new fields, shadowing external variables.
    test("x=@{a:=1;b=@{a=  2;b=@{a=3;b=0}}}; x.b.b.a",TypeInt.con(3));
    // Lexical scoping.  Before a new field is created, the external variable is used.
    // After the new field, the new field is used.
    test("x=@{a:=1;b=@{a=a+1;c=a}}; x.a*10+x.b.c",TypeInt.con(1*10+2));
    // Functions only make new fields if no prior one exists.
    test("x=@{a:=1;b= {a=a+1;b=0}}; x.b(); x.a",TypeInt.con(2));

    // Tuple
    test_obj_isa("(0,\"abc\")", TypeStruct.make_tuple(Type.NIL,Type.NIL,TypeMemPtr.OOP));
    test("(1,\"abc\").0", TypeInt.TRUE);
    test_obj("(1,\"abc\").1", TypeStr.ABC);

    // Named type variables
    test("gal=:flt; gal", TypeFunPtr.make(TEST_FUNBITS,2,TypeFunPtr.NO_DISP));
    test("gal=:flt; 3==gal(2)+1", TypeInt.TRUE);
    test("gal=:flt; tank:gal = gal(2)", TypeInt.con(2).set_name("gal:"));
    // test    ("gal=:flt; tank:gal = 2.0", TypeName.make("gal",TypeFlt.con(2))); // TODO: figure out if free cast for bare constants?
    testerr ("gal=:flt; tank:gal = gal(2)+1", "3 is not a gal:flt64",14);

    test    ("Point=:@{x;y}; dist={p:Point -> p.x*p.x+p.y*p.y}; dist(Point(1,2))", TypeInt.con(5));
    test    ("Point=:@{x;y}; dist={p       -> p.x*p.x+p.y*p.y}; dist(Point(1,2))", TypeInt.con(5));
    testerr ("Point=:@{x;y}; dist={p:Point -> p.x*p.x+p.y*p.y}; dist((@{x=1;y=2}))", "*@{x=1; y=2} is not a *Point:@{x:=; y:=}",55);
    testerr ("Point=:@{x;y}; Point((0,1))", "*(0; 1) is not a *Point:@{x:=; y:=}",21);
    testerr("x=@{n: =1;}","Missing type after ':'",7);
    testerr("x=@{n=;}","Missing ifex after assignment of 'n'",6);
    test_obj_isa("x=@{n}",TypeStruct.make(new String[]{"^","n"},TypeStruct.ts(TypeMemPtr.OOP,Type.XNIL),new byte[]{TypeStruct.FFNL,TypeStruct.FRW}));
  }

  @Test public void testParse05() {
    // nilable and not-nil pointers
    test   ("x:str? = 0", Type.XNIL); // question-type allows nil or not; zero digit is nil
    test_obj("x:str? = \"abc\"", TypeStr.ABC); // question-type allows nil or not
    testerr("x:str  = 0", "0 is not a *str", 1);
    test_ptr0("math_rand(1)?0:\"abc\"", (alias)->TypeMemPtr.make_nil(alias,TypeStr.ABC));
    testerr("(math_rand(1)?0 : @{x=1}).x", "Struct might be nil when reading field '.x'", 26);
    test   ("p=math_rand(1)?0:@{x=1}; p ? p.x : 0", TypeInt.BOOL); // not-nil-ness after a nil-check
    test   ("x:int = y:str? = z:flt = 0", Type.XNIL); // nil/0 freely recasts
    test   ("\"abc\"==0", TypeInt.FALSE ); // No type error, just not nil
    test   ("\"abc\"!=0", TypeInt.TRUE  ); // No type error, just not nil
    test   ("nil=0; \"abc\"!=nil", TypeInt.TRUE); // Another way to name nil
    test   ("a = math_rand(1) ? 0 : @{x=1}; // a is nil or a struct\n"+
            "b = math_rand(1) ? 0 : @{c=a}; // b is nil or a struct\n"+
            "b ? (b.c ? b.c.x : 0) : 0      // Nil-safe field load", TypeInt.BOOL); // Nested nil-safe field load
  }

  @Test public void testParse06() {
    Object dummy = Env.GVN; // Force class loading cycle

    // Building recursive types
    test("A= :int; A(1)", TypeInt.TRUE.set_name("A:"));
    test_ptr("A= :(str?, int); A(0,2)","A:(0; 2)");
    // Named recursive types
    test_ptr("A= :(A?, int); A(0,2)",(alias) -> TypeMemPtr.make(alias,TypeStruct.make_tuple(TypeStruct.ts(TypeMemPtr.NO_DISP,Type.XNIL,TypeInt.con(2))).set_name("A:")));
    test_ptr("A= :(A?, int); A(0,2)","A:(0; 2)");
    test    ("A= :@{n=A?; v=flt}; A(@{n=0;v=1.2}).v;", TypeFlt.con(1.2));
    test_ptr("A= :(A?, int); A(A(0,2),3)","A:(*A:(0; 2); 3)");

    // TODO: Needs a way to easily test simple recursive types
    TypeEnv te3 = Exec.go(Env.file_scope(Env.top_scope()),"args","A= :@{n=A?; v=int}; A(@{n=0;v=3})");
    if( te3._errs != null ) System.err.println(te3._errs.toString());
    assertNull(te3._errs);
    TypeStruct tt3 = (TypeStruct)te3._tmem.ld((TypeMemPtr)te3._t);
    assertEquals("A:", tt3._name);
    assertTrue  (tt3.at(0).is_display_ptr());
    assertEquals(Type.XNIL     ,tt3.at(1));
    assertEquals(TypeInt.con(3),tt3.at(2));
    assertEquals("n",tt3._flds[1]);
    assertEquals("v",tt3._flds[2]);

    // Missing type B is also never worked on.
    test_isa("A= :@{n=B?; v=int}", TypeFunPtr.GENERIC_FUNPTR);
    test_isa("A= :@{n=B?; v=int}; a = A(0,2)", TypeMemPtr.ISUSED);
    test_isa("A= :@{n=B?; v=int}; a = A(0,2); a.n", Type.NIL);
    // Mutually recursive type
    test_isa("A= :@{n=B; v=int}; B= :@{n=A; v=flt}", TypeFunPtr.GENERIC_FUNPTR);
  }

  private static final String[] FLDS2= new String[]{"^","map","nn","vv"};
  @Test public void testParse07() {
    // Passing a function recursively
    test("f0 = { f x -> x ? f(f0(f,x-1),1) : 0 }; f0({&},2)", Type.XNIL);
    test("f0 = { f x -> x ? f(f0(f,x-1),1) : 0 }; f0({+},2)", TypeInt.con(2));
    test_isa("A= :@{n=A?; v=int}; f={x:A? -> x ? A(f(x.n),x.v*x.v) : 0}", TypeFunPtr.GENERIC_FUNPTR);
    test    ("A= :@{n=A?; v=flt}; f={x:A? -> x ? A(f(x.n),x.v*x.v) : 0}; f(A(0,1.2)).v;", TypeFlt.con(1.2*1.2));
    test("tmp=((0,1.2),2.3); sq={x->x*x}; map={f t -> t ? (map(f,t.0),f t.1) : 0}; map(sq,tmp).1",TypeFlt.con(2.3*2.3));
    // Calling a function twice which returns the same alias.  Verify no pointer confusion.
    test("noinline_x={@{a}}; x0=noinline_x(); x1=noinline_x(); x0.a:=2; x1.a",  TypeInt.INT8);

    // Longer variable-length list (so no inline-to-trivial).  Pure integer
    // ops, no overload resolution.  Does final stores into new objects
    // interspersed with recursive computation calls.
    test_obj_isa("map={x -> x ? @{nn=map(x.n);vv=x.v&x.v} : 0};"+
                 "map(@{n=math_rand(1)?0:@{n=math_rand(1)?0:@{n=math_rand(1)?0:@{n=0;v=1};v=2};v=3};v=4})",
                 TypeStruct.make(FLDS2,TypeStruct.ts(TypeMemPtr.DISPLAY_PTR,Type.XSCALAR,TypeMemPtr.STRUCT0,TypeInt.INT8))); //con(20.25)
    // Test does loads after recursive call, which should be allowed to bypass.
    test("sum={x -> x ? sum(x.n) + x.v : 0};"+
         "sum(@{n=math_rand(1)?0:@{n=math_rand(1)?0:@{n=math_rand(1)?0:@{n=0;v=1};v=2};v=3};v=4})",
         TypeInt.INT64);

    // User-defined linked list.
    String ll_def = "List=:@{next;val};";
    String ll_con = "tmp=List(List(0,1.2),2.3);";
    String ll_map = "map = {fun list -> list ? List(map(fun,list.next),fun(list.val)) : 0};";
    String ll_fun = "sq = {x -> x*x};";
    String ll_apl = "map(sq,tmp);";

    test_isa(ll_def, TypeFunPtr.GENERIC_FUNPTR);
    test(ll_def+ll_con+"; tmp.next.val", TypeFlt.con(1.2));
    test_isa(ll_def+ll_con+ll_map, TypeFunPtr.GENERIC_FUNPTR);
    test_isa(ll_def+ll_con+ll_map+ll_fun, TypeFunPtr.GENERIC_FUNPTR);

    // TODO: Needs a way to easily test simple recursive types
    TypeEnv te4 = Exec.go(Env.file_scope(Env.top_scope()),"args",ll_def+ll_con+ll_map+ll_fun+ll_apl);
    if( te4._errs != null ) System.err.println(te4._errs.toString());
    assertNull(te4._errs);
    TypeStruct tt4 = (TypeStruct)te4._tmem.sharpen((TypeMemPtr)te4._t)._obj;
    assertEquals("List:", tt4._name);
    TypeMemPtr tmp5 = (TypeMemPtr)tt4.at(1);
    assertEquals(2.3*2.3,tt4.at(2).getd(),1e-6);
    assertEquals("next",tt4._flds[1]);
    assertEquals("val" ,tt4._flds[2]);

    // Test inferring a recursive struct type, with a little help
    Type[] ts0 = TypeStruct.ts(Type.XNIL, Type.XSCALAR, Type.XNIL,TypeFlt.con(1.2*1.2));
    test_struct("map={x:@{n=;v=flt}? -> x ? @{nn=map(x.n);vv=x.v*x.v} : 0}; map(@{n=0;v=1.2})",
                TypeStruct.make(FLDS2,ts0,TypeStruct.ffnls(4)));

    // Test inferring a recursive struct type, with less help.  This one
    // inlines so doesn't actually test inferring a recursive type.
    Type[] ts1 = TypeStruct.ts(Type.XNIL, Type.XSCALAR, Type.XNIL,TypeFlt.con(1.2*1.2));
    test_struct("map={x -> x ? @{nn=map(x.n);vv=x.v*x.v} : 0}; map(@{n=0;v=1.2})",
                TypeStruct.make(FLDS2,ts1,TypeStruct.ffnls(4)));

    // Test inferring a recursive struct type, with less help. Too complex to
    // inline, so actual inference happens
    test_obj_isa("map={x -> x ? @{nn=map(x.n);vv=x.v*x.v} : 0};"+
                 "map(@{n=math_rand(1)?0:@{n=math_rand(1)?0:@{n=math_rand(1)?0:@{n=0;v=1.2};v=2.3};v=3.4};v=4.5})",
                 TypeStruct.make(FLDS2,TypeStruct.ts(TypeMemPtr.STRUCT0,Type.XSCALAR,TypeMemPtr.STRUCT0,TypeFlt.FLT64))); //con(20.25)

    // Test inferring a recursive tuple type, with less help.  This one
    // inlines so doesn't actually test inferring a recursive type.
    test_ptr("map={x -> x ? (map(x.0),x.1*x.1) : 0}; map((0,1.2))",
             (alias) -> TypeMemPtr.make(alias,TypeStruct.make_tuple(Type.XNIL,Type.XNIL,TypeFlt.con(1.2*1.2))));

    test_obj_isa("map={x -> x ? (map(x.0),x.1*x.1) : 0};"+
                 "map((math_rand(1)?0: (math_rand(1)?0: (math_rand(1)?0: (0,1.2), 2.3), 3.4), 4.5))",
                 TypeStruct.make(TypeStruct.ts(Type.XNIL,TypeMemPtr.STRUCT0,TypeFlt.con(20.25))));

    // TODO: Need real TypeVars for these
    //test("id:{A->A}"    , Env.lookup_valtype("id"));
    //test("id:{A:int->A}", Env.lookup_valtype("id"));
    //test("id:{int->int}", Env.lookup_valtype("id"));
  }


  @Test public void testParse08() {
    Object dummy = Env.GVN; // Force class loading cycle
    Type dummy2 = TypeStruct.DISPLAY;
    // Main issue with the map() test is final assignments crossing recursive
    // not-inlined calls.  Smaller test case:
    test_ptr("tmp=@{val=2;nxt=@{val=1;nxt=0}}; noinline_map={tree -> tree ? @{vv=tree.val&tree.val;nn=noinline_map(tree.nxt)} : 0}; noinline_map(tmp)",
             "@{vv=int8; noinline_map=~Scalar; nn=*$?}");

    // Too big to inline, multi-recursive
    test_ptr("tmp=@{"+
                    "  l=@{"+
                    "    l=@{ l=0; r=0; v=3 };"+
                    "    r=@{ l=0; r=0; v=7 };"+
                    "    v=5"+
                    "  };"+
                    "  r=@{"+
                    "    l=@{ l=0; r=0; v=15 };"+
                    "    r=@{ l=0; r=0; v=22 };"+
                    "    v=20"+
                    "  };"+
                    "  v=12 "+
                    "};"+
                    "map={tree -> tree"+
                    "     ? @{ll=map(tree.l);rr=map(tree.r);vv=tree.v&tree.v}"+
                    "     : 0};"+
                    "map(tmp)",
             "@{map=~Scalar; ll=*@{map=~Scalar; ll=*$?; rr=$; vv=int8}?; rr=$; vv=int8}");


    // Failed attempt at a Tree-structure inference test.  Triggered all sorts
    // of bugs and error reporting issues, so keeping it as a regression test.
    testerr("tmp=@{"+
         "  l=@{"+
         "    l=@{ l=0; v=3 };"+
         "    l=0;"+
         "    v=5"+
         "  };"+
         "  v=12 "+
         "};"+
         "map={tree -> tree"+
         "     ? @{ll=map(tree.l);vv=tree.v}"+
         "     : 0};"+
         "map(tmp)",
            "Cannot re-assign final field '.l'",36);

    // Good tree-structure inference test
    test_ptr("tmp=@{"+
         "  l=@{"+
         "    l=@{ l=0; r=0; v=3 };"+
         "    r=@{ l=0; r=0; v=7 };"+
         "    v=5"+
         "  };"+
         "  r=@{"+
         "    l=@{ l=0; r=0; v=15 };"+
         "    r=@{ l=0; r=0; v=22 };"+
         "    v=20"+
         "  };"+
         "  v=12 "+
         "};"+
         "map={tree fun -> tree"+
         "     ? @{l=map(tree.l,fun);r=map(tree.r,fun);v=fun(tree.v)}"+
         "     : 0};"+
         "map(tmp,{x->x+x})",
         "@{map=~Scalar; l=*@{map=~Scalar; l=*$?; r=$; v=int64}?; r=$; v=int64}");

    // A linked-list mixing ints and strings, always in pairs
    String ll_cona = "a=0; ";
    String ll_conb = "b=math_rand(1) ? ((a,1),\"abc\") : a; ";
    String ll_conc = "c=math_rand(1) ? ((b,2),\"def\") : b; ";
    String ll_cond = "d=math_rand(1) ? ((c,3),\"ghi\") : c; ";
    String ll_cone = "e=math_rand(1) ? ((d,4),\"jkl\") : d; ";
    String ll_cont = "tmp=e; ";
    // Standard pair-UN-aware map call
    String ll_map2 = "map = {fun list -> list ? (map(fun,list.0),fun(list.1)) : 0};";
    String ll_fun2 = "plus = {x -> x+x};";
    String ll_apl2 = "map(plus,tmp);";
    // End type: ((((*?,scalar)?,str)?,int64),str)?

    // After inlining once, we become pair-aware.

    TypeStruct xts_int = TypeStruct.make_tuple(Type.XNIL,TypeMemPtr.OOP0,TypeInt.INT64);
    TypeMemPtr xpt_int = TypeMemPtr.make(BitsAlias.RECORD_BITS0,xts_int);
    TypeStruct xts_str = TypeStruct.make_tuple(Type.XNIL,xpt_int,TypeMemPtr.STRPTR);
    TypeMemPtr xtmp = TypeMemPtr.make(BitsAlias.RECORD_BITS0,xts_str);

    test_isa(ll_cona+ll_conb+ll_conc+ll_cond+ll_cone+ll_cont+ll_map2+ll_fun2+ll_apl2,xtmp);

  }

  @Test public void testParse09() {
    // Test re-assignment
    test("x=1", TypeInt.TRUE);
    test("x=y=1", TypeInt.TRUE);
    testerr("x=y=", "Missing ifex after assignment of 'y'",4);
    testerr("x=z" , "Unknown ref 'z'",2);
    testerr("x=1+y","Unknown ref 'y'",4);

    test("x:=1", TypeInt.TRUE);
    test_obj("x:=0; a=x; x:=1; b=x; x:=2; (a,b,x)", TypeStruct.make_tuple(Type.XNIL,Type.XNIL,TypeInt.con(1),TypeInt.con(2)));

    testerr("x=1; x:=2; x", "Cannot re-assign final val 'x'", 5);
    testerr("x=1; x =2; x", "Cannot re-assign final val 'x'", 5);

    test("math_rand(1)?(x=4):(x=3);x", TypeInt.NINT8); // x defined on both arms, so available after
    test("math_rand(1)?(x:=4):(x:=3);x", TypeInt.NINT8); // x defined on both arms, so available after
    test("math_rand(1)?(x:=4):(x:=3);x:=x+1", TypeInt.INT64); // x mutable on both arms, so mutable after
    test   ("x:=0; 1 ? (x:=4):; x:=x+1", TypeInt.con(5)); // x mutable ahead; ok to mutate on 1 arm and later
    test   ("x:=0; 1 ? (x =4):; x", TypeInt.con(4)); // x final on 1 arm, dead on other arm
    testerr("x:=0; math_rand(1) ? (x =4):3; x=2; x", "Cannot re-assign read-only val 'x'",31);
  }

  // Ffnls are declared with an assignment.  This is to avoid the C++/Java
  // problem of making final-field cycles.  Java requires final fields to be
  // only assigned in constructors before the value escapes, which prevents any
  // final-cyclic structures.  Final assignments have to be unambiguous - they
  // fold into a 'New' at some point, same as casting to a 'Name', but they can
  // interleave with other operations (such as other News) as long as the store
  // is unambiguous.
  //                                                   unknown
  // Field mod status makes a small lattice:      final       read/write
  //                                                  read-only

  // Type-error if a final assignment does not fold into a New.  Cannot cast
  // final to r/w, nor r/w to final.  Can cast both to r/o.  The reason you
  // cannot cast to a final, is that some other caller/thread with some other
  // r/w copy of the same pointer you have, can modify the supposedly final
  // object.  Hence you cannot cast to "final", but you can cast to "read-only"
  // which only applies to you, and not to other r/w pointers.
  @Test public void testParse10() {
    Object dummy = TypeStruct.DISPLAY;
    // Test re-assignment in struct
    Type[] ts = TypeStruct.ts(TypeMemPtr.DISP_SIMPLE, TypeInt.con(1), TypeInt.con(2));
    test_obj_isa("x=@{n:=1;v:=2}", TypeStruct.make(FLDS, ts,new byte[]{TypeStruct.FFNL,TypeStruct.FRW,TypeStruct.FRW}));
    testerr ("x=@{n =1;v:=2}; x.n  = 3; x.n", "Cannot re-assign final field '.n'",18);
    test    ("x=@{n:=1;v:=2}; x.n  = 3", TypeInt.con(3));
    test_ptr("x=@{n:=1;v:=2}; x.n := 3; x", "@{n:=3; v:=2}");
    testerr ("x=@{n:=1;v:=2}; x.n  = 3; x.v = 1; x.n = 4; x.n", "Cannot re-assign final field '.n'",37);
    test    ("x=@{n:=1;v:=2}; y=@{n=3;v:=4}; tmp = math_rand(1) ? x : y; tmp.n", TypeInt.NINT8);
    testerr ("x=@{n:=1;v:=2}; y=@{n=3;v:=4}; tmp = math_rand(1) ? x : y; tmp.n = 5; tmp.n", "Cannot re-assign read-only field '.n'",63);
    test    ("x=@{n:=1;v:=2}; foo={q -> q.n=3}; foo(x); x.n",TypeInt.con(3)); // Side effects persist out of functions
    // Tuple assignment
    testerr ("x=(1,2); x.0=3; x", "Cannot re-assign final field '.0'",11);
    // Final-only and read-only type syntax.
    testerr ("ptr2rw = @{f:=1}; ptr2final:@{f=} = ptr2rw; ptr2final", "*@{f:=1} is not a *@{f=; ...}",27); // Cannot cast-to-final

    test_obj_isa("ptr2   = @{f =1}; ptr2final:@{f=} = ptr2  ; ptr2final", // Good cast
                 TypeStruct.make(new String[]{"^","f"},new Type[]{TypeMemPtr.DISPLAY_PTR,TypeInt.con(1)},TypeStruct.ffnls(2)));
    testerr ("ptr=@{f=1}; ptr2rw:@{f:=} = ptr; ptr2rw", "*@{f=1} is not a *@{f:=; ...}", 18); // Cannot cast-away final
    test    ("ptr=@{f=1}; ptr2rw:@{f:=} = ptr; 2", TypeInt.con(2)); // Dead cast-away of final
    test    ("@{x:=1;y =2}:@{x;y=}.y", TypeInt.con(2)); // Allowed reading final field
    testerr ("f={ptr2final:@{x;y=} -> ptr2final.y  }; f(@{x:=1;y:=2})", "*@{x:=1; y:=2} is not a *@{x:=; y=; ...}",42); // Another version of casting-to-final
    testerr ("f={ptr2final:@{x;y=} -> ptr2final.y=3; ptr2final}; f(@{x:=1;y =2})", "Cannot re-assign final field '.y'",34);
    test    ("f={ptr:@{x==;y:=} -> ptr.y=3; ptr}; f(@{x:=1;y:=2}).y", TypeInt.con(3)); // On field x, cast-away r/w for r/o
    test    ("f={ptr:@{x=;y:=} -> ptr.y=3; ptr}; f(@{x =1;y:=2}).y", TypeInt.con(3)); // On field x, cast-up r/o for final but did not read
    testerr ("f={ptr:@{x=;y:=} -> ptr.y=3; ptr}; f(@{x:=1;y:=2}).x", "*@{x:=1; y:=2} is not a *@{x=; y:=; ...}",37); // On field x, cast-up r/w for final and read
    test    ("f={ptr:@{x;y} -> ptr.y }; f(@{x:=1;y:=2}:@{x;y==})", TypeInt.con(2)); // cast r/w to r/o, and read
    test    ("f={ptr:@{x==;y==} -> ptr }; f(@{x=1;y=2}).y", TypeInt.con(2)); // cast final to r/o and read
    test    ("ptr=@{f:=1}; ptr:@{f=}.f=2",TypeInt.con(2)); // Checking that it is-a final does not make it final
    // In general for these next two, want a 'MEET' style type assertion where
    // locally at the function parm we "finalize" ptr.y, so the function body
    // cannot modify it.  However, no final store occurs so after the function,
    // ptr.y remains writable.
    //testerr ("f={ptr:@{x;y=} -> ptr.y=3}; f(@{x:=1;y:=2});", "Cannot re-assign read-only field '.y'",24);
    //testerr ("f={ptr:@{x;y} -> ptr.y=3}; f(@{x:=1;y:=2}:@{x;y=})", "Cannot re-assign read-only field '.y'",24);
    test    ("ptr=@{a:=1}; val=ptr.a; ptr.a=2; val",TypeInt.con(1));
    // Allowed to build final pointer cycles
    test    ("ptr0=@{p:=0;v:=1}; ptr1=@{p=ptr0;v:=2}; ptr0.p=ptr1; ptr0.p.v+ptr1.p.v+(ptr0.p==ptr1)", TypeInt.con(4)); // final pointer-cycle is ok
  }

  // Early function exit
  @Test public void testParse11() {
    test("x:=0; {1 ? ^2; x=3}(); x",Type.XNIL);  // Following statement is ignored
    test("{ ^3; 5}()",TypeInt.con(3)); // early exit
    test("x:=0; {^3; x++}(); x",Type.XNIL);  // Following statement is ignored
    test("x:=0; {^1 ? (x=1); x=3}(); x",TypeInt.con(1));  // Return of an ifex
    test("x:=0; {^1 ?  x=1 ; x=3}(); x",TypeInt.con(1));  // Return of an ifex
    test("f={0 ? ^0; 7}; f()", TypeInt.con(7));
    // Find: returns 0 if not found, or first element which passes predicate.
    test("find={list pred -> !list ? ^0; pred(list.1) ? ^list.1; find(list.0,pred)}; find(((0,3),2),{e -> e&1})", TypeInt.INT8);
    test("x:=0; {1 ? ^2; x=3}(); x",Type.XNIL);  // Following statement is ignored
    // Curried functions
    test("for={A->    A+3 }; for 2  ", TypeInt.con(5));
    test("for={A->{B->A+B}}; for 2 3", TypeInt.con(5));
    test("for={pred->{body->!pred()?^;tmp=body(); tmp?^tmp;7}}; for {1}{0}", TypeInt.con(7));
  }

  // Upwards exposed closure tests
  @Test public void testParse12() {
    test("incA= {cnt:=0; {cnt++}       }(); incA();incA()",TypeInt.con(1));
    test("cnt:=0; incA={cnt++}; incA();incA()+cnt",TypeInt.con(1+2));
    test("incA= {cnt:=0; {cnt++}       }();                      incA()       ",Type.XNIL);
    test("incA= {cnt:=0; {cnt++}       }();                      incA();incA()",TypeInt.con(1));
    test("tmp = {cnt:=0;({cnt++},{cnt})}();incA=tmp.0;getA=tmp.1;incA();incA()+getA()",TypeInt.con(1+2));
    test("gen = {cnt:=0;({cnt++},{cnt})};" +
         "tmp:=gen(); incA=tmp.0;getA=tmp.1;"+
         "tmp:=gen(); incB=tmp.0;getB=tmp.1;"+
         "incA();incB();incA(); getA()*10+getB()",
         TypeInt.con(2*10+1));
  }

  // Serial loops; using variable 'for': "for pred body".
  // If 'pred' is false , the loop exits with false.
  // If 'body' is truthy, the loop exits with this value.
  // To 'continue', use '^0'.  To 'break' with non-zero 'val' use '^val'.
  // Break cannot exit with '0'.
  private final String FORELSE="for={pred->{body->!pred()?^;(tmp=body())?^tmp; for pred body}};";
  // If 'pred' is false, the loop exits with false, else loop continues.  'body' value is ignored.
  // To 'continue', use '^'.
  // There is no 'break'.
  private final String DO="do={pred->{body->!pred()?^;body(); do pred body}};";

  @Test public void testParse13() {
    test(DO+"i:=0; do {i++ < 2} {i== 9} ? ",Type.XNIL);    // Late exit, body never returns true.
    test(FORELSE+"i:=0; for {i++ < 100} {i== 5} ",TypeInt.BOOL); // Not sure of exit value, except bool
    test(FORELSE+"i:=0; for {i++ < 100} {i==50?i}",TypeInt.INT64); // Early exit on condition i==50
    test(DO+"sum:=0; i:=0; do {i++ < 100} {sum:=sum+i}; sum",TypeInt.INT64);
  }

  // Array syntax examples
  @Test public void testParse14() {
    test_ptr("[3]", "[$]0/obj");
    test    ("ary = [3]; ary[0]", Type.XNIL);
    test    ("[3][0]", Type.XNIL);
    test    ("ary = [3]; ary[0]:=2", TypeInt.con(2));
    test_obj("ary = [3]; ary[0]:=0; ary[1]:=1; ary[2]:=2; (ary[0],ary[1],ary[2])", // array create, array storing
      TypeStruct.make_tuple(Type.XNIL,TypeInt.INT8,TypeInt.INT8,TypeInt.INT8));
    testary("0[0]","0 is not a *[]Scalar/obj",1);
    testary("[3] [4]","Index must be out of bounds",5);
    testary("[3] [-1]","Index must be out of bounds",5);
    test_obj("[3]:[int]", TypeAry.make(TypeInt.con(3),Type.XNIL,TypeObj.OBJ)); // Array of 3 XNILs in INTs.
    //test("[1,2,3]", TypeAry.make(TypeInt.con(1),TypeInt.con(3),TypeInt.INT8)); // Array of 3 elements
    test("ary=[3];#ary",TypeInt.con(3)); // Array length
    test_ptr(DO+"ary=[99]; i:=0; do {i++ < #ary} {ary[i]:=i*i};ary", "[$]int64/obj"); // sequential iteration over array
    // ary.{e -> f(e)} // map over array elements
    // ary.{e -> f(e)}.{e0 e1 -> f(e0,e1) } // map/reduce over array elements
  }

  /*
// type variables are free in : type expressions

// Define a pair as 2 fields "a" and "b" both with the same type T.  Note that
// 'a' and 'b' and 'T' are all free, but the @ parses this as a struct, so 'a'
// and 'b' become field names and 'T' becomes a free type-var.
Pair = :@{ a:T, b:T }

// Since 'A' and 'B' are free and not field names, they become type-vars.
MapType = :{ {A->B} List[A] -> List[B] }

// map: no leading ':' so a function definition, not a type def
map:MapType  = { f list -> ... }

// A List type.  Named types are not 'null', so not valid to use "List = :...?".
// Type List takes a type-variable 'A' (which is free in the type expr).
// List is a self-recursive type.
// Field 'next' can be null or List(A).
// Field 'val' is type A.
List = :@{ next:List?, val:A }
   */

  /*** Fanciful attempt at a HashTable class.  No resize, size, clear, etc.
HashTable = {@{
  _tab = [7];

  get = { key ->
    entry = _tab[key.hash() % #_tab];
    entry && key.eq(entry.key) ? entry.val;
  }
  put = { key val ->
    idx = key.hash() % #_tab;
    entry = _tab[idx];
    entry && key.eq(entry.key) ? (oldval=entry.val; entry.val:=val; ^oldval);
    _tab[idx]= @{key=key; val=val; next=entry};
    entry ? entry.val;
  }
}}
   */


  // Caller must close TypeEnv
  static private TypeEnv run( String program ) {
    TypeEnv te = Exec.open(Env.file_scope(Env.top_scope()),"args",program);
    if( te._errs != null ) System.err.println(te._errs.toString());
    assertNull(te._errs);
    return te;
  }

  static private void test( String program, Type expected ) {
    try( TypeEnv te = run(program) ) {
      assertEquals(expected,te._t);
    }
  }
  static private void test_prim( String program, String prim ) {
    Env top = Env.top_scope();
    Type expected = top.lookup_valtype(prim);
    try( TypeEnv te = Exec.open(Env.file_scope(top),"args",program) ) {
      if( te._errs != null ) System.err.println(te._errs.toString());
      assertNull(te._errs);
      assertEquals(expected,te._t);
    }
  }
  static private void test_name( String program, Type... args ) {
    try( TypeEnv te = run(program) ) {
      assertTrue(te._t instanceof TypeFunPtr);
      TypeFunPtr actual = (TypeFunPtr)te._t;
      TypeFunPtr expected = TypeFunPtr.make(actual.fidxs(),2,TypeFunPtr.NO_DISP);
      assertEquals(expected,actual);
    }
  }
  static private void test_ptr( String program, Function<Integer,Type> expected ) {
    try( TypeEnv te = run(program) ) {
      TypeMemPtr actual = te._tmem.sharpen((TypeMemPtr)te._t);
      int alias = actual.getbit(); // internally asserts only 1 bit set
      Type t_expected = expected.apply(alias);
      assertEquals(t_expected,actual);
    }
  }
  static private void test_ptr0( String program, Function<Integer,Type> expected ) {
    try( TypeEnv te = run(program) ) {
      TypeMemPtr tmp = te._tmem.sharpen((TypeMemPtr)te._t);
      BitsAlias bits = tmp._aliases;
      assertTrue(bits.test(0));
      int alias = bits.strip_nil().getbit(); // internally asserts only 1 bit set
      Type t_expected = expected.apply(alias);
      assertEquals(t_expected,tmp);
    }
  }
  static private void test_obj( String program, TypeObj expected) {
    try( TypeEnv te = run(program) ) {
      assertTrue(te._t instanceof TypeMemPtr);
      int alias = ((TypeMemPtr)te._t).getbit(); // internally asserts only 1 bit set
      TypeObj actual = te._tmem.ld((TypeMemPtr)te._t);
      assertEquals(expected,actual);
    }
  }
  static private void test_struct( String program, TypeStruct expected) {
    try( TypeEnv te = run(program) ) {
      TypeStruct actual = (TypeStruct)te._tmem.ld((TypeMemPtr)te._t);
      actual = actual.set_fld(0,Type.XNIL,TypeStruct.FFNL);
      assertEquals(expected,actual);
    }
  }
  static private void test_obj_isa( String program, TypeObj expected) {
    try( TypeEnv te = run(program) ) {
      int alias = ((TypeMemPtr)te._t)._aliases.strip_nil().getbit(); // internally asserts only 1 bit set
      TypeObj actual = te._tmem.sharpen((TypeMemPtr)te._t)._obj;
      assertTrue(actual.isa(expected));
    }
  }
  static private void test_ptr( String program, String expected ) {
    try( TypeEnv te = run(program) ) {
      assertTrue(te._t instanceof TypeMemPtr);
      TypeObj to = te._tmem.ld((TypeMemPtr)te._t); // Peek thru pointer
      SB sb = to.str(new SB(),new VBitSet(),te._tmem,false);      // Print what we see, with memory
      assertEquals(expected,strip_alias_numbers(sb.toString()));
    }
  }
  static private void test( String program, Function<Integer,Type> expected ) {
    try( TypeEnv te = run(program) ) {
      Type t_expected = expected.apply(-99); // unimpl
      assertEquals(t_expected,te._t);
    }
  }
  static private void test_isa( String program, Type expected ) {
    try( TypeEnv te = run(program) ) {
      Type actual = te._tmem.sharptr(te._t);
      assertTrue(actual.isa(expected));
    }
  }
  static private void testerr( String program, String err, String cursor ) {
    System.out.println("fix test, cur_off="+cursor.length());
    fail();
  }
  static void testerr( String program, String err, int cur_off ) {
    TypeEnv te = Exec.go(Env.file_scope(Env.top_scope()),"args",program);
    assertTrue(te._errs != null && te._errs.size()>=1);
    String cursor = new String(new char[cur_off]).replace('\0', ' ');
    String err2 = new SB().p("args:1:").p(err).nl().p(program).nl().p(cursor).p('^').nl().toString();
    assertEquals(err2,strip_alias_numbers(te._errs.get(0).toString()));
  }
  private static String strip_alias_numbers( String err ) {
    // Remove alias#s from the result string: *[123]@{x=1,y=2} ==> *[$]@{x=1,y=2}
    //     \\      Must use two \\ because of String escaping for every 1 in the regex.
    // Thus replacing: \[[,0-9]*  with:  \[\$
    // Regex breakdown:
    //     \\[     prevents using '[' as the start of a regex character class
    //     [,0-9]  matches digits and commas
    //     *       matches all the digits and commas
    //     \\[     Replacement [ because the first one got matched and replaced.
    //     \\$     Prevent $ being interpreted as a regex group start
    return err.replaceAll("\\[[,0-9]*", "\\[\\$");
  }
  static private void testary( String program, String err, int cur_off ) {
    TypeEnv te = Exec.go(Env.file_scope(Env.top_scope()),"args",program);
    assertTrue(te._errs != null && te._errs.size()>=1);
    String cursor = new String(new char[cur_off]).replace('\0', ' ');
    String err2 = new SB().p("args:1:").p(err).nl().p(program).nl().p(cursor).p('^').nl().toString();
    assertEquals(err2,te._errs.get(0).toString());
  }

}
