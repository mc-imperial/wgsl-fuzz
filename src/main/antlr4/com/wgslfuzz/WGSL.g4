// Based on a combination of:
// - https://github.com/gpuweb/gpuweb/blob/a8e20cf4b10982b5d505d9a3a7f30995523c0297/wgsl/index.bs
// - https://www.w3.org/TR/WGSL/#grammar-recursive-descent

grammar WGSL;

fragment EOL: '\r\n' | '\n';

WHITESPACE
    : [ \n\r\t\u000B\u000C\u0000]+ -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' (BLOCK_COMMENT | .)*? '*/' -> channel(HIDDEN)
    ; // WGSL allows nested comments

LINE_COMMENT
    : '//' .*? ('\n' | EOF) -> channel(HIDDEN)
    ;

// Literals

FLOAT_LITERAL: '0' [fh]
    | [1-9][0-9]*[fh]
    | [0-9]* '.' [0-9]+([eE][+-]?[0-9]+)?[fh]?
    | [0-9]+ '.' [0-9]*([eE][+-]?[0-9]+)?[fh]?
    | [0-9]+[eE][+-]?[0-9]+[fh]?
    | '0' [xX][0-9a-fA-F]* '.' [0-9a-fA-F]+([pP][+-]?[0-9]+[fh]?)?
    | '0' [xX][0-9a-fA-F]+ '.' [0-9a-fA-F]*([pP][+-]?[0-9]+[fh]?)?
    | '0' [xX][0-9a-fA-F]+[pP][+-]?[0-9]+[fh]?;

BOOL_LITERAL: 'false' | 'true';
INT_LITERAL: '0' [xX][0-9a-fA-F]+[iu]? | '0'[iu]? | [1-9][0-9]*[iu]?;

// Type-defining keywords

ARRAY: 'array';
BOOL: 'bool';
FLOAT32: 'f32';
INT32: 'i32';
MAT2X2: 'mat2x2';
MAT2X3: 'mat2x3';
MAT2X4: 'mat2x4';
MAT3X2: 'mat3x2';
MAT3X3: 'mat3x3';
MAT3X4: 'mat3x4';
MAT4X2: 'mat4x2';
MAT4X3: 'mat4x3';
MAT4X4: 'mat4x4';
STRUCT: 'struct';
UINT32: 'u32';
VEC2: 'vec2';
VEC3: 'vec3';
VEC4: 'vec4';

// Other keywords

ALIAS: 'alias';
BREAK: 'break';
CASE: 'case';
CONST: 'const';
CONST_ASSERT: 'const_assert';
CONTINUE: 'continue';
CONTINUING: 'continuing';
DEFAULT: 'default';
DIAGNOSTIC: 'diagnostic';
DISCARD: 'discard';
ELSE: 'else';
FALLTHROUGH: 'fallthrough';
FN: 'fn';
FOR: 'for';
FUNCTION: 'function';
IF: 'if';
LET: 'let';
LOOP: 'loop';
OVERRIDE: 'override';
PRIVATE: 'private';
PTR: 'ptr';
RETURN: 'return';
STORAGE: 'storage';
SWITCH: 'switch';
TYPE: 'type';
UNIFORM: 'uniform';
VAR: 'var';
WHILE: 'while';
WORKGROUP: 'workgroup';

// Syntactic tokens

AND: '&';
AND_AND: '&&';
ARROW: '->';
ATTR: '@';
FORWARD_SLASH: '/';
BANG: '!';
BRACKET_LEFT: '[';
BRACKET_RIGHT: ']';
BRACE_LEFT: '{';
BRACE_RIGHT: '}';
COLON: ':';
COMMA: ',';
EQUAL: '=';
EQUAL_EQUAL: '==';
NOT_EQUAL: '!=';
GREATER_THAN: '>';
GREATER_THAN_EQUAL: '>=';
LESS_THAN: '<';
LESS_THAN_EQUAL: '<=';
MODULO: '%';
MINUS: '-';
MINUS_MINUS: '--';
PERIOD: '.';
PLUS: '+';
PLUS_PLUS: '++';
OR: '|';
OR_OR: '||';
PAREN_LEFT: '(';
PAREN_RIGHT: ')';
SEMICOLON: ';';
STAR: '*';
TILDE: '~';
UNDERSCORE: '_';
XOR: '^';
PLUS_EQUAL: '+=';
MINUS_EQUAL: '-=';
TIMES_EQUAL: '*=';
DIVISION_EQUAL: '/=';
MODULO_EQUAL: '%=';
AND_EQUAL: '&=';
OR_EQUAL: '|=';
XOR_EQUAL: '^=';
SHIFT_LEFT_EQUAL: '<<=';
SHIFT_RIGHT_EQUAL: '>>=';

IDENT: [_\p{XID_Start}] [\p{XID_Continue}]+ | [\p{XID_Start}];

// Literals

float_literal: FLOAT_LITERAL;
int_literal: INT_LITERAL;
bool_literal: BOOL_LITERAL;
const_literal: int_literal | float_literal | bool_literal;

// Attributes

attr_keyword: CONST
    | DIAGNOSTIC;

attr_name: attr_keyword | IDENT;

attribute: ATTR attr_name PAREN_LEFT (expression COMMA)* expression COMMA? PAREN_RIGHT
         | ATTR IDENT;

literal_or_ident: INT_LITERAL | IDENT;

// Types

array_type_decl: ARRAY (LESS_THAN type_decl (COMMA element_count_expression)? GREATER_THAN)?;
// The element count is a general expression because may be e.g. the product of two constant expressions.
element_count_expression: expression;

struct_decl: STRUCT IDENT struct_body_decl SEMICOLON?;
struct_body_decl: BRACE_LEFT (struct_member COMMA)* struct_member COMMA? BRACE_RIGHT;
struct_member: attribute* IDENT COLON type_decl;

// The options for access mode are 'read', 'write' and 'read_write', but these are not keywords
access_mode: IDENT;
address_space: FUNCTION | PRIVATE | WORKGROUP | UNIFORM | STORAGE;

type_alias_decl: ALIAS IDENT EQUAL type_decl;

type_decl: IDENT (LESS_THAN type_decl (COMMA type_decl)* COMMA? GREATER_THAN)? | type_decl_without_ident;

type_decl_without_ident: BOOL
                       | FLOAT32
                       | INT32
                       | UINT32
                       | vec_prefix LESS_THAN type_decl GREATER_THAN
                       | mat_prefix LESS_THAN type_decl GREATER_THAN
                       | PTR LESS_THAN address_space COMMA type_decl (COMMA access_mode)? GREATER_THAN
                       | array_type_decl;

vec_prefix: VEC2 | VEC3 | VEC4;
mat_prefix: MAT2X2
          | MAT2X3
          | MAT2X4
          | MAT3X2
          | MAT3X3
          | MAT3X4
          | MAT4X2
          | MAT4X3
          | MAT4X4;

// Variables

ident_with_optional_type: IDENT (COLON type_decl)?;

variable_or_value_statement: variable_decl
                  | variable_decl EQUAL expression
                  | CONST ident_with_optional_type EQUAL expression
                  | LET ident_with_optional_type EQUAL expression;

variable_decl: VAR variable_qualifier? ident_with_optional_type;
variable_qualifier: LESS_THAN address_space (COMMA access_mode)? GREATER_THAN;
global_variable_decl: attribute* variable_decl (EQUAL expression)?;
global_value_decl: CONST ident_with_optional_type EQUAL expression
                    | attribute* OVERRIDE ident_with_optional_type (EQUAL expression)?;

// Expressions

primary_expression: IDENT
          | callable_val argument_expression_list
          | const_literal
          | PAREN_LEFT expression PAREN_RIGHT;

callable_val: IDENT (LESS_THAN type_decl GREATER_THAN)?
            | type_decl_without_ident
            | vec_prefix
            | mat_prefix;

argument_expression_list: PAREN_LEFT ((expression COMMA)* expression COMMA?)? PAREN_RIGHT;

postfix_expression: BRACKET_LEFT expression BRACKET_RIGHT postfix_expression?
                  | PERIOD IDENT postfix_expression?;

unary_expression: singular_expression
                | MINUS unary_expression
                | BANG unary_expression
                | TILDE unary_expression
                | STAR unary_expression
                | AND unary_expression;

singular_expression: primary_expression postfix_expression?;

lhs_expression: (STAR | AND)* core_lhs_expression postfix_expression?;

core_lhs_expression: IDENT | PAREN_LEFT lhs_expression PAREN_RIGHT;

multiplicative_expression: unary_expression
                         | multiplicative_expression STAR unary_expression
                         | multiplicative_expression FORWARD_SLASH unary_expression
                         | multiplicative_expression MODULO unary_expression;

additive_expression: multiplicative_expression
                   | additive_expression PLUS multiplicative_expression
                   | additive_expression MINUS multiplicative_expression;

shift_expression: additive_expression
                | shift_expression GREATER_THAN GREATER_THAN additive_expression
                | shift_expression LESS_THAN LESS_THAN additive_expression;

relational_expression: shift_expression
                     | shift_expression LESS_THAN shift_expression
                     | shift_expression GREATER_THAN shift_expression
                     | shift_expression LESS_THAN_EQUAL shift_expression
                     | shift_expression GREATER_THAN_EQUAL shift_expression
                     | shift_expression EQUAL_EQUAL shift_expression
                     | shift_expression NOT_EQUAL shift_expression;

short_circuit_and_expression: relational_expression
                            | short_circuit_and_expression AND_AND relational_expression;

short_circuit_or_expression: relational_expression
                           | short_circuit_or_expression OR_OR relational_expression;

binary_or_expression: unary_expression
                    | binary_or_expression OR unary_expression;

binary_and_expression: unary_expression
                     | binary_and_expression AND unary_expression;

binary_xor_expression: unary_expression
                     | binary_xor_expression XOR unary_expression;

expression: relational_expression
          | short_circuit_or_expression OR_OR relational_expression
          | short_circuit_and_expression AND_AND relational_expression
          | binary_and_expression AND unary_expression
          | binary_or_expression OR unary_expression
          | binary_xor_expression XOR unary_expression;

// Statements

compound_statement: attribute? BRACE_LEFT statement* BRACE_RIGHT;

assignment_statement: lhs_expression (EQUAL | compound_assignment_operator) expression
                    | UNDERSCORE EQUAL expression;

compound_assignment_operator: PLUS_EQUAL
                            | MINUS_EQUAL
                            | TIMES_EQUAL
                            | DIVISION_EQUAL
                            | MODULO_EQUAL
                            | AND_EQUAL
                            | OR_EQUAL
                            | XOR_EQUAL
                            | SHIFT_LEFT_EQUAL
                            | SHIFT_RIGHT_EQUAL;

increment_statement: lhs_expression PLUS_PLUS;
decrement_statement: lhs_expression MINUS_MINUS;

if_statement: attribute? IF expression compound_statement (ELSE else_statement)?;
else_statement: compound_statement | if_statement;

switch_statement: attribute? SWITCH expression attribute? BRACE_LEFT switch_body+ BRACE_RIGHT;
switch_body: CASE case_selectors COLON? case_compound_statement
           | DEFAULT COLON? case_compound_statement;
expression_or_default: expression | DEFAULT;
case_selectors: expression_or_default (COMMA expression_or_default)* COMMA?;
case_compound_statement: attribute? BRACE_LEFT statement* fallthrough_statement? BRACE_RIGHT;
fallthrough_statement: FALLTHROUGH SEMICOLON;

loop_statement: attribute? LOOP attribute? BRACE_LEFT statement* continuing_statement? BRACE_RIGHT;

for_statement: attribute? FOR PAREN_LEFT for_header PAREN_RIGHT compound_statement;
for_header: for_init? SEMICOLON expression? SEMICOLON for_update?;
for_init: variable_or_value_statement
        | increment_statement
        | decrement_statement
        | assignment_statement
        | func_call_statement;
for_update: increment_statement
          | decrement_statement
          | assignment_statement
          | func_call_statement;

while_statement: attribute? WHILE expression compound_statement;

break_statement: BREAK;
break_if_statement: BREAK IF expression SEMICOLON;
continue_statement: CONTINUE;
continuing_statement: CONTINUING continuing_compound_statement;
continuing_compound_statement: attribute? BRACE_LEFT statement* break_if_statement? BRACE_RIGHT;
discard_statement: DISCARD;
return_statement: RETURN expression?;
func_call_statement: IDENT argument_expression_list;
const_assert_statement: CONST_ASSERT expression;
empty_statement: SEMICOLON;

statement: empty_statement
         | return_statement SEMICOLON
         | if_statement
         | switch_statement
         | loop_statement
         | for_statement
         | while_statement
         | func_call_statement SEMICOLON
         | variable_or_value_statement SEMICOLON
         | break_statement SEMICOLON
         | continue_statement SEMICOLON
         | assignment_statement SEMICOLON
         | compound_statement
         | increment_statement SEMICOLON
         | decrement_statement SEMICOLON
         | discard_statement SEMICOLON
         | const_assert_statement SEMICOLON;

// Functions

function_decl: attribute* function_header compound_statement;
function_header: FN IDENT PAREN_LEFT param_list? PAREN_RIGHT (ARROW attribute* type_decl)?;
param_list: (param COMMA)* param COMMA?;
param: attribute* IDENT COLON type_decl;

severity_control_name: IDENT;
diagnostic_rule_name: IDENT | IDENT PERIOD IDENT;
diagnostic_directive: DIAGNOSTIC PAREN_LEFT severity_control_name COMMA diagnostic_rule_name COMMA? PAREN_RIGHT;

enable_directive: 'enable' IDENT (COMMA IDENT)* COMMA?;

requires_directive: 'requires' IDENT (COMMA IDENT)* COMMA?;

// Program

translation_unit: global_directive* global_decl* EOF;

global_directive: diagnostic_directive SEMICOLON
           | enable_directive SEMICOLON
           | requires_directive SEMICOLON;

empty_global_decl: SEMICOLON;

const_assert_decl: const_assert_statement;

global_decl: empty_global_decl
           | global_variable_decl SEMICOLON
           | global_value_decl SEMICOLON
           | type_alias_decl SEMICOLON
           | struct_decl
           | function_decl
           | const_assert_decl SEMICOLON;
