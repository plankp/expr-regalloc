type terms =
  | Var of string
  | Const of int
  | Add of terms * terms
  | Mul of terms * terms
  | Let of string * terms * terms

let rec asm (b : Buffer.t) (env : (string * int64) list) (maxstack : int64) (t : terms) : int64 =
  match t with
  | Var n ->
      let offs = List.assoc n env in
      Printf.bprintf b "  movq -%Ld(%%rbp), %%rax\n" offs;
      maxstack
  | Const n ->
      Printf.bprintf b "  movq $%d, %%rax\n" n;
      maxstack
  | Add (t1, t2) ->
      let maxstack = asm b env maxstack t1 in
      Printf.bprintf b "  pushq %%rax\n";
      let maxstack = asm b env maxstack t2 in
      Printf.bprintf b "  popq %%rdx\n";
      Printf.bprintf b "  addq %%rdx, %%rax\n";
      maxstack
  | Mul (t1, t2) ->
      let maxstack = asm b env maxstack t1 in
      Printf.bprintf b "  pushq %%rax\n";
      let maxstack = asm b env maxstack t2 in
      Printf.bprintf b "  popq %%rdx\n";
      Printf.bprintf b "  imul %%rdx\n";
      maxstack
  | Let (n, t1, t2) ->
      let maxstack = asm b env maxstack t1 in
      let maxstack = Int64.add maxstack 4L in
      Printf.bprintf b "  movq %%rax, -%Ld(%%rbp)\n" maxstack;
      asm b ((n, maxstack) :: env) maxstack t2

let asm (t : terms) : unit =
  let b = Buffer.create 16 in
  let maxstack = asm b [] 0L t in
  Printf.printf "_start:\n";
  Printf.printf "  push %%rbp\n";
  Printf.printf "  movq %%rsp, %%rbp\n";
  Printf.printf "  subq $%Ld, %%rsp\n" maxstack;
  Buffer.output_buffer stdout b;
  Printf.printf "  leaveq\n";
  Printf.printf "  retq\n"

let () =
  let prgm =
    Let ("x", Add (Const 1, Const 2), Mul (Var "x", Var "x")) in
  asm prgm

