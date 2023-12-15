type terms =
  | Var of string
  | Const of int
  | Add of terms * terms
  | Mul of terms * terms
  | Spill of int * terms * terms
  | Reload of int
  | Let of string * terms * terms

let rec dump (t : terms) : unit = match t with
  | Var n -> Printf.printf "%s" n
  | Const i -> Printf.printf "%d" i
  | Add (a, b) ->
      Printf.printf "(+ ";
      dump a;
      Printf.printf " ";
      dump b;
      Printf.printf ")"
  | Mul (a, b) ->
      Printf.printf "(* ";
      dump a;
      Printf.printf " ";
      dump b;
      Printf.printf ")"
  | Spill (s, t1, t2) ->
      Printf.printf "(spill #%d " s;
      dump t1;
      Printf.printf " ";
      dump t2;
      Printf.printf ")"
  | Reload s ->
      Printf.printf "(reload #%d)" s
  | Let (n, t1, t2) ->
      Printf.printf "(let ((%s " n;
      dump t1;
      Printf.printf ")) ";
      dump t2;
      Printf.printf ")"

let gensym (live : string list) (hint : string) : string =
  if not @@ List.mem hint live then hint
  else
    let rec go base =
      let newch = Char.chr (97 + Random.int 26) in
      let base = Printf.sprintf "%s%c" base newch in
      if List.mem base live then go base else base in
    match String.rindex_opt hint '.' with
    | None -> go (hint ^ ".")
    | Some s -> go (String.sub hint 0 (s + 1))

let normalize (live : string list) (t : terms) : terms =
  let rec go live subst t k = match t with
    | Var n as t ->
        subst |> List.assoc_opt n |> Option.value ~default:t |> k live subst
    | Const _ as t ->
        (* here we assume constants also need to be named,
           which isn't too incorrect due to potential ISA restrictions.
         *)
        let n = gensym live "t" in
        Let (n, t, k (n :: live) subst @@ Var n)
    | Add (a, b) ->
        go live subst a @@ fun live subst a ->
          go live subst b @@ fun live subst b ->
            let n = gensym live "t" in
            Let (n, Add (a, b), k (n :: live) subst @@ Var n)
    | Mul (a, b) ->
        go live subst a @@ fun live subst a ->
          go live subst b @@ fun live subst b ->
            let n = gensym live "t" in
            Let (n, Mul (a, b), k (n :: live) subst @@ Var n)
    | Spill (s, t1, t2) ->
        go live subst t1 @@ fun live subst t1 ->
          Spill (s, t1, go live subst t2 k)
    | Reload _ as t ->
        let n = gensym live "t" in
        Let (n, t, k (n :: live) subst @@ Var n)
    | Let (n, t1, t2) ->
        go live subst t1 @@ fun live subst t1 -> begin
          match t1 with
            Var _ as v ->
              go live ((n, v) :: subst) t2 k
          | _ ->
              let n' = gensym live n in
              Let (n', t1, go (n' :: live) ((n, Var n') :: subst) t2 k)
        end
  in
  go live [] t @@ fun _ _ x -> x

let uniq_names (live : string list) (t : terms) =
  let rec go live subst = function
    | Var n ->
        Var (subst |> List.assoc_opt n |> Option.value ~default:n), live
    | Const _ | Reload _ as t ->
        t, live
    | Add (a, b) ->
        let a, live = go live subst a in
        let b, live = go live subst b in
        Add (a, b), live
    | Mul (a, b) ->
        let a, live = go live subst a in
        let b, live = go live subst b in
        Mul (a, b), live
    | Spill (s, t1, t2) ->
        let t1, live = go live subst t1 in
        let t2, live = go live subst t2 in
        Spill (s, t1, t2), live
    | Let (n, t1, t2) ->
        let t1, live = go live subst t1 in
        let n' = gensym live n in
        let t2, live = go (n' :: live) ((n, n') :: subst) t2 in
        Let (n', t1, t2), live in
  go live [] t |> fst

module StrSet = Set.Make (String)

let rec seq_append (a : 'a Seq.t) (b : 'a Seq.t) : 'a Seq.t = fun () ->
  match a () with
  | Seq.Nil -> b ()
  | Seq.Cons (x, xs) -> Seq.Cons (x, seq_append xs b)

let compute_igraph (t : terms) : (string * StrSet.t) list =
  let rec go acc = function
    | Var n -> acc, StrSet.singleton n
    | Spill (_, t1, t2) ->
        let acc, fv = go acc t2 in
        let acc, fv' = go acc t1 in
        acc, StrSet.union fv fv'
    | Let (n, (Const _ | Reload _), t) ->
        let acc, fv = go acc t in
        let fv = StrSet.remove n fv in
        (n, fv) :: acc, fv
    | Let (n, Add (Var a, Var b), t)
    | Let (n, Mul (Var a, Var b), t) ->
        (* let n = a + b in ... ~~> n <- a; n <- n + b; ...
           which means n and b are simultaneously live!
         *)
        let acc, fv = go acc t in
        let fv = StrSet.remove n fv in
        let fv = StrSet.add b fv in
        (n, fv) :: acc, StrSet.add a fv
    | _ -> failwith "UNSUPPORTED TERM FORM" in
  go [] t |> fst

module IntSet = Set.Make (Int)

let color (order : (string * StrSet.t) list) : int * (string, int) Hashtbl.t =
  let coloring = Hashtbl.create 16 in
  let rec go max s = function
    | [] -> max, coloring
    | (n, lives) :: xs ->
        let s' = StrSet.fold (fun n s' ->
          IntSet.remove (Hashtbl.find coloring n) s') lives s in
        let color, max, s = match IntSet.min_elt_opt s' with
          | None -> max, max + 1, IntSet.add max s
          | Some color -> color, max, s in
        Hashtbl.replace coloring n color;
        go max s xs in
  go 0 IntSet.empty order

let find_spill_candidate (t : terms) : StrSet.t =
  let gap = Hashtbl.create 16 in
  let rec go acc = function
    | Var n ->
        Hashtbl.replace gap n 0;
        acc
    | Let (n, t1, t2) -> begin
        let acc = go acc t2 in
        let acc = match acc, Hashtbl.find_opt gap n with
          | None, Some u -> Some (n, u)
          | Some (_, u'), Some u when u' < u -> Some (n, u)
          | _ -> acc in
        Hashtbl.filter_map_inplace (fun _ v -> Some (v + 1)) gap;
        go acc t1
      end
    | Spill (_, t1, t2) ->
        go (go acc t2) t1
    | Add (a, b)
    | Mul (a, b) ->
        go (go acc a) b
    | _ -> acc in
  match go None t with
    | None -> StrSet.empty (* ??? *)
    | Some (n, _) -> StrSet.singleton n

let rec spill (id : int) (names : StrSet.t) (t : terms) : terms =
  match t with
  | Var n as t ->
      if StrSet.mem n names then Reload id else t
  | Let (n, t1, t2) when StrSet.mem n names ->
      Let (n, spill id names t1, Spill (id, Var n, spill id names t2))
  | Let (n, t1, t2) ->
      Let (n, spill id names t1, spill id names t2)
  | Spill (n, t1, t2) ->
      Spill (n, spill id names t1, spill id names t2)
  | Add (a, b) ->
      Add (spill id names a, spill id names b)
  | Mul (a, b) ->
      Mul (spill id names a, spill id names b)
  | t -> t

let regs = ["%rax"; "%rdx"]

let asm (coloring : (string, int) Hashtbl.t) (t : terms) : unit =
  let rec go buf maxslot = function
    | Var _ -> maxslot
    | Spill (slot, Var n, t) ->
        let n = n |> Hashtbl.find coloring |> List.nth regs in
        Printf.bprintf buf "  movq %s, %d(%%rbp)\n" n (-8 * (slot + 1));
        go buf (max maxslot (slot + 1)) t
    | Let (n, Reload slot, t) ->
        let n = n |> Hashtbl.find coloring |> List.nth regs in
        Printf.bprintf buf "  movq %d(%%rbp), %s\n" (-8 * (slot + 1)) n;
        go buf (max maxslot (slot + 1)) t
    | Let (n, Const i, t) ->
        let dst = n |> Hashtbl.find coloring |> List.nth regs in
        Printf.bprintf buf "  movq $%d, %s\n" i dst;
        go buf maxslot t
    | Let (n, Add (Var a, Var b), t) ->
        let dst = n |> Hashtbl.find coloring |> List.nth regs in
        let a = a |> Hashtbl.find coloring |> List.nth regs in
        let b = b |> Hashtbl.find coloring |> List.nth regs in
        Printf.bprintf buf "  movq %s, %s\n" a dst;
        Printf.bprintf buf "  addq %s, %s\n" b dst;
        go buf maxslot t
    | Let (n, Mul (Var a, Var b), t) ->
        let dst = n |> Hashtbl.find coloring |> List.nth regs in
        let a = a |> Hashtbl.find coloring |> List.nth regs in
        let b = b |> Hashtbl.find coloring |> List.nth regs in
        Printf.bprintf buf "  movq %s, %s\n" a dst;
        Printf.bprintf buf "  imulq %s, %s\n" b dst;
        go buf maxslot t
    | _ -> failwith "UNSUPPORTED TERM FORM" in
  let buf = Buffer.create 16 in
  let maxslot = go buf 0 t in
  Printf.printf "_start:\n";
  if maxslot <> 0 then begin
    Printf.printf "  push %%rbp\n";
    Printf.printf "  movq %%rsp, %%rbp\n";
    Printf.printf "  subq $%d, %%rsp\n" (8 * maxslot)
  end;
  Buffer.output_buffer stdout buf;
  if maxslot <> 0 then Printf.printf "  leaveq\n";
  Printf.printf "  retq\n"

let () =
  let prgm =
    Let ("x", Add (Const 1, Const 2), Mul (Var "x", Var "x")) in
  (* (* this one needs spilling! *)
  let prgm =
    Let ("a", Const 1, Let ("b", Const 2, Let ("c", Const 3, Let ("d", Const 4,
      Let ("e", Add (Var "c", Var "d"), Let ("f", Add (Var "a", Var "b"),
        Let ("g", Add (Var "e", Var "f"), Var "g"))))))) in
  *)
  let rec compile i prgm =
    let prgm = prgm |> normalize [] |> uniq_names [] in
    let max, coloring = prgm |> compute_igraph |> color in
    let regcnt = List.length regs in
    if max > regcnt then begin
      let candidates = find_spill_candidate prgm in
      prgm |> spill i candidates |> compile (i + 1)
    end
    else
      asm coloring prgm in
  prgm |> compile 0

