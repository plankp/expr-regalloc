grammar Sample;

@header {
    package com.ymcmp.eralloc.ast;
}

LCURL: '{';
RCURL: '}';
LPAREN: '(';
RPAREN: ')';
COMMA: ',';
SEMI: ';';
SET: '=';
ADD: '+';
SUB: '-';
MUL: '*';
DIV: '/';
REM: '%';
SHL: '<<';
SRA: '>>';
SRL: '>>>';

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

NUMERIC
    : '0x' ('_'? [a-fA-F0-9])+
    | '0c' ('_'? [0-7])+
    | '0b' ('_'? [01])+
    | [1-9] ('_'? [0-9])*
    | '0'
    ;

WS: [ \t\r\n] -> skip;
COMMENT: '#' ~[\r\n]* -> skip;

fdecl
    : name=IDENTIFIER '('
        (params+=IDENTIFIER (COMMA params+=IDENTIFIER)*)?
    ')' '=' e=expr ';'
    ;

/*
stmt
    : name=IDENTIFIER '=' expr
    | name=IDENTIFIER
    ;
*/

expr
    : '(' e=expr ')' # ExprParenthesis
    | v=NUMERIC # ExprNumeric
    | name=IDENTIFIER # ExprName
    | lhs=expr op=('*' | '/' | '%' | '<<' | '>>' | '>>>') rhs=expr # ExprMultiplicative
    | lhs=expr op=('+' | '-') rhs=expr # ExprAdditive
    ;
