package com.wgslfuzz

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ResolverTests {
    private fun gatherExpressions(
        node: AstNode,
        expressions: MutableSet<Expression>,
    ) {
        traverse(::gatherExpressions, node, expressions)
        when (node) {
            is Expression -> {
                expressions.add(node)
            }
            else -> { }
        }
    }

    @Test
    fun miscTest() {
        val input =
            """
            fn f() -> i32
            {
              var i : i32;
              while (i < 4)
              {
                i = i + 1;
              }
              return i;
            }

            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val expressions = mutableSetOf<Expression>()
        gatherExpressions(tu, expressions)

        // Confirm that a type was found for every expression.
        expressions.forEach {
            environment.typeOf(it)
        }

        val functionDecl = tu.globalDecls[0] as GlobalDecl.Function
        val whileStmt = functionDecl.body.statements[1] as Statement.While
        val whileCondition = whileStmt.expression as Expression.Paren
        assertEquals(Type.Bool, environment.typeOf(whileCondition))
        val whileConditionInner = whileCondition.target as Expression.Binary
        assertEquals(Type.Bool, environment.typeOf(whileConditionInner))
        assertEquals(Type.I32, environment.typeOf(whileConditionInner.lhs))
        assertEquals(Type.AbstractInteger, environment.typeOf(whileConditionInner.rhs))
    }

    @Test
    fun pointerTest() {
        val input =
            """
            @group(0, )
            @binding(0, )
            var<storage, read_write> s : i32;

            var<workgroup> g1 : atomic<i32, >;

            struct S {
              a : i32,
              b : i32,
            }

            fn accept_ptr_deref_pass_through(
              val : ptr<function, i32>,
            ) -> i32
            {
              return (*(val) + accept_ptr_deref_call_func(val, ));
            }

            fn accept_ptr_to_struct_and_access(
              val : ptr<function, S>,
            ) -> i32
            {
              return ((*(val)).a + (*(val)).b);
            }

            fn accept_ptr_to_struct_access_pass_ptr(
              val : ptr<function, S>,
            ) -> i32
            {
              let b = &((*(val)).a);
              *(b) = 2;
              return *(b);
            }

            fn accept_ptr_deref_call_func(
              val : ptr<function, i32>,
            ) -> i32
            {
              return (*(val) + accept_value(*(val), ));
            }

            fn accept_value(
              val : i32,
            ) -> i32
            {
              return val;
            }

            fn accept_ptr_vec_access_elements(
              v1 : ptr<function, vec3f>,
            ) -> i32
            {
              (*(v1)).x = cross(*(v1), *(v1), ).x;
              return i32((*(v1)).x, );
            }

            fn call_builtin_with_mod_scope_ptr() -> i32
            {
              return atomicLoad(&(g1), );
            }

            @compute
            @workgroup_size(1, )
            fn main()
            {
              var v1 = 0;
              var v2 = S();
              let v3 = &(v2);
              var v4 = vec3f();
              let t1 = atomicLoad(&(g1), );
              s = ((((((accept_ptr_deref_pass_through(&(v1), ) + accept_ptr_to_struct_and_access(&(v2), )) + accept_ptr_to_struct_and_access(v3, )) + accept_ptr_vec_access_elements(&(v4), )) + accept_ptr_to_struct_access_pass_ptr(&(v2), )) + call_builtin_with_mod_scope_ptr()) + t1);
            }
            
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val expressions = mutableSetOf<Expression>()
        gatherExpressions(tu, expressions)

        // Confirm that a type was found for every expression.
        expressions.forEach {
            environment.typeOf(it)
        }
    }
}
