# CalcLens Math Engine

## 1. Engine Philosophy & Architectural Isolation

The Math Engine is a pure, zero-dependency mathematical parser and evaluator. It has zero knowledge of cameras, user interfaces, or computer vision. Given a string of normalized characters, it deterministically validates syntax, generates an Abstract Syntax Tree (AST), and evaluates the exact mathematical result.

```text
Normalized String (e.g. "2 + 3 * 4")
              │
              ▼
    ┌───────────────────┐
    │       Lexer       │ ──▶ Token Stream: [NUM(2), PLUS, NUM(3), MUL, NUM(4), EOF]
    └─────────┬─────────┘
              │
              ▼
    ┌───────────────────┐
    │     AST Parser    │ ──▶ BinaryOp(PLUS, Num(2), BinaryOp(MUL, Num(3), Num(4)))
    └─────────┬─────────┘
              │
              ▼
    ┌───────────────────┐
    │   AST Evaluator   │ ──▶ Result: 14
    └───────────────────┘
```

---

## 2. Formal Grammar Specification (EBNF)

The engine implements a standard arithmetic expression grammar enforcing operator precedence (BODMAS/PEMDAS):

```ebnf
Expression     := AdditiveExpr ;

AdditiveExpr   := MultiplicativeExpr ( ( "+" | "-" ) MultiplicativeExpr )* ;

Multiplicative := PrimaryExpr ( ( "*" | "/" ) PrimaryExpr )* ;

PrimaryExpr    := [ "-" ] ( Number | "(" Expression ")" ) ;

Number         := Digit+ [ "." Digit+ ] ;

Digit          := "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
```

* **Precedence Tier 1 (Lowest)**: Addition (`+`) and Subtraction (`-`)
* **Precedence Tier 2 (Highest)**: Multiplication (`*`) and Division (`/`)
* **Associativity**: Left-associative across all binary operations:
  $$a - b - c \equiv (a - b) - c$$
  $$a / b / c \equiv (a / b) / c$$

---

## 3. Abstract Syntax Tree (AST) Data Structures

```typescript
type TokenType = 
  | 'NUMBER'
  | 'PLUS'
  | 'MINUS'
  | 'MULTIPLY'
  | 'DIVIDE'
  | 'LPAREN'
  | 'RPAREN'
  | 'EOF';

interface Token {
  type: TokenType;
  value: string;
  position: number;
}

type ASTNode = 
  | NumberLiteralNode
  | BinaryOperationNode
  | UnaryOperationNode;

interface NumberLiteralNode {
  type: 'NumberLiteral';
  value: number;
}

interface BinaryOperationNode {
  type: 'BinaryOperation';
  operator: '+' | '-' | '*' | '/';
  left: ASTNode;
  right: ASTNode;
}

interface UnaryOperationNode {
  type: 'UnaryOperation';
  operator: '-';
  operand: ASTNode;
}
```

---

## 4. Evaluation Algorithm & Error Handling

### 4.1 Recursive Evaluation
```typescript
function evaluate(node: ASTNode): number {
  switch (node.type) {
    case 'NumberLiteral':
      return node.value;

    case 'UnaryOperation':
      if (node.operator === '-') {
        return -evaluate(node.operand);
      }
      throw new Error(`Unsupported unary operator: ${node.operator}`);

    case 'BinaryOperation': {
      const leftVal = evaluate(node.left);
      const rightVal = evaluate(node.right);

      switch (node.operator) {
        case '+':
          return leftVal + rightVal;
        case '-':
          return leftVal - rightVal;
        case '*':
          return leftVal * rightVal;
        case '/':
          if (rightVal === 0) {
            throw new MathEvaluationError('DIVISION_BY_ZERO', 'Division by zero is undefined');
          }
          return leftVal / rightVal;
      }
    }
  }
}
```

### 4.2 Error Taxonomy
* **`SYNTAX_ERROR`**: Unbalanced parentheses, trailing operators (e.g. `27 + `), consecutive invalid operators (`5 ++ 2`).
* **`DIVISION_BY_ZERO`**: Zero divisor encountered during tree evaluation (`100 / 0`).
* **`OVERFLOW`**: Numeric results exceeding standard safe integer or float bounds ($|x| > 2^{53} - 1$ or $\pm 1.79 \times 10^{308}$).

### 4.3 Number Formatting
* Integer outputs are formatted without decimal fractions (`378.0` `->` `"378"`).
* Floating-point numbers are rounded to at most 6 significant decimal places, stripping trailing zeros (`10 / 4` `->` `"2.5"`).
* Avoid scientific notation for standard numbers ($< 1,000,000,000$).

---

## 5. Benchmarks & Testing Strategy

Because the math engine has zero platform dependencies:
* 100% of arithmetic rules are validated through headless automated unit tests.
* Evaluation latency is verified to execute in $< 0.1\text{ ms}$ per expression.
* Fuzz testing generates millions of randomized valid and malformed expressions to guarantee zero unhandled runtime crashes.
