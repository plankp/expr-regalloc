module IntSet = Set.Make (Int)
module IntMap = Map.Make (Int)
module StrSet = Set.Make (String)
module StrMap = Map.Make (String)

type prgm =
  | Prgm of topv StrMap.t

and topv =
  | Fun of block IntMap.t
  | Init of const

and const =
  | Int of int
  | Pack of const list
  | Label of string

and block =
  | Block of string list * kexpr

and kexpr =
  | Ret of vexpr
  | Cond of vexpr * int * int
  | Goto of int * vexpr list
  | Call of int * vexpr * vexpr list

and vexpr =
  | Const of const
  | Arg of string
  | Add of vexpr * vexpr
  | Proj of int * vexpr

let rec dump_prgm (Prgm prgm : prgm) : unit =
  StrMap.iter dump_topv prgm

and dump_topv (name : string) (v : topv) : unit = match v with
  | Fun f ->
      Printf.printf "def %s {\n" name;
      IntMap.iter dump_block f;
      Printf.printf "}\n"
  | Init v ->
      Printf.printf "def %s = " name;
      dump_const v;
      Printf.printf "\n"

and dump_const (v : const) : unit = match v with
  | Int i ->
      Printf.printf "%d" i
  | Pack [] ->
      Printf.printf "[]"
  | Pack (x :: xs) ->
      Printf.printf "[";
      dump_const x;
      List.iter (fun x ->
        Printf.printf ", ";
        dump_const x) xs;
      Printf.printf "]"
  | Label s ->
      Printf.printf "%s" s

and dump_block (id : int) (Block (args, tail) : block) : unit =
  Printf.printf "%d(" id;
  let () = match args with
    | [] -> ()
    | x :: xs ->
        Printf.printf "%s" x;
        List.iter (Printf.printf ", %s") xs in
  Printf.printf "):\n";
  dump_kexpr tail;
  Printf.printf "\n";

and dump_kexpr (t : kexpr) : unit = match t with
  | Ret v ->
      Printf.printf "  return ";
      dump_vexpr v
  | Cond (v, k1, k2) ->
      Printf.printf "  if ";
      dump_vexpr v;
      Printf.printf " then %d() else %d()" k1 k2
  | Goto (k, []) ->
      Printf.printf "  %d()" k;
  | Goto (k, x :: xs) ->
      Printf.printf "  %d(" k;
      dump_vexpr x;
      List.iter (fun x ->
        Printf.printf ", ";
        dump_vexpr x) xs;
      Printf.printf ")"
  | Call (k, f, []) ->
      Printf.printf "  %d(" k;
      dump_vexpr f;
      Printf.printf "  ())"
  | Call (k, f, x :: xs) ->
      Printf.printf "  %d(" k;
      dump_vexpr f;
      Printf.printf "  (";
      dump_vexpr x;
      List.iter (fun x ->
        Printf.printf ", ";
        dump_vexpr x) xs;
      Printf.printf "))"

and dump_vexpr (t : vexpr) : unit = match t with
  | Const k ->
      Printf.printf "(const ";
      dump_const k;
      Printf.printf ")"
  | Arg n ->
      Printf.printf "(arg %s)" n
  | Add (a, b) ->
      Printf.printf "(+ ";
      dump_vexpr a;
      Printf.printf " ";
      dump_vexpr b;
      Printf.printf ")"
  | Proj (i, v) ->
      Printf.printf "(proj %d " i;
      dump_vexpr v;
      Printf.printf ")"

let collect_out_edges (Block (_, tail) : block) : IntSet.t = match tail with
  | Ret _ -> IntSet.empty
  | Goto (k, _) | Call (k, _, _) -> IntSet.singleton k
  | Cond (_, k1, k2) -> IntSet.singleton k1 |> IntSet.add k2

let rename_out_edges (ren : int IntMap.t) (b : block) : block =
  let ren k =
    ren |> IntMap.find_opt k |> Option.value ~default:k
  in
  match b with
  | Block (_, Ret _) -> b
  | Block (args, Goto (k, xs)) ->
      Block (args, Goto (ren k, xs))
  | Block (args, Call (k, f, xs)) ->
      Block (args, Call (ren k, f, xs))
  | Block (args, Cond (v, k1, k2)) ->
      Block (args, Cond (v, ren k1, ren k2))

let renumber_blocks (blocks : block IntMap.t) : block IntMap.t =
  let rec dfs n (acc, pending) =
    match IntMap.find_opt n pending with
      | None -> acc, pending
      | Some block ->
          let pending = IntMap.remove n pending in
          let succ = collect_out_edges block in
          let acc, pending = IntSet.fold dfs succ (acc, pending) in
          (n, block) :: acc, pending
  in
  match IntMap.min_binding_opt blocks with
    | None -> blocks (* there were no blocks to begin with *)
    | Some (n, _) ->
        (* ignore pending blocks (since they are never used) *)
        let (order, _) = dfs n ([], blocks) in
        let (blocks, ren, _) = List.fold_left (fun (blocks, ren, n') (n, body) ->
          let ren = if n' = n then ren else IntMap.add n n' ren in
          IntMap.add n' body blocks, ren, n' + 1) (IntMap.empty, IntMap.empty, n) order in
        if IntMap.is_empty ren then blocks (* no renaming needed *)
        else IntMap.map (rename_out_edges ren) blocks

let gensym (live : StrSet.t) (hint : string) : string =
  if not @@ StrSet.mem hint live then hint
  else
    let rec go base =
      let newch = Char.chr (97 + Random.int 26) in
      let base = Printf.sprintf "%s%c" base newch in
      if StrSet.mem base live then go base else base in
    match String.rindex_opt hint '.' with
    | None -> go (hint ^ ".")
    | Some s -> go (String.sub hint 0 (s + 1))

let compute_in_edges (blocks : block IntMap.t) : IntSet.t IntMap.t =
  (* make sure every block exists in the result *)
  let m = IntMap.map (fun _ -> IntSet.empty) blocks in
  IntMap.fold (fun src block ->
    let out_edges = collect_out_edges block in
    IntSet.fold (fun dst ->
      IntMap.update dst (Option.map (IntSet.add src))) out_edges) blocks m

let dom_info (blocks : block IntMap.t) : int IntMap.t =
  (* Derived from "A Simple, Fast Dominance Algorithm" *)
  let rec intersect doms b1 b2 =
    if b1 = b2 then b1
    else if b1 > b2 then
      intersect doms (IntMap.find b1 doms) b2
    else
      intersect doms b1 (IntMap.find b2 doms)
  in
  let preds = compute_in_edges blocks in
  let rec go dom =
    let dom, changed = IntMap.fold (fun id preds (dom, changed) ->
      let new_idom = IntSet.fold (fun p acc ->
        match acc with
        | None -> Some p
        | Some idom ->
            if IntMap.mem p dom then
              Some (intersect dom p idom)
            else
              Some idom) preds None in

      (* the entry block will have None here.
         in those cases, we know all blocks dominate themselves.
       *)
      let new_idom = Option.value ~default:id new_idom in

      (* carefully update the dom mapping *)
      let dom' = IntMap.update id (function
        | None -> Some new_idom
        | Some old_idom when old_idom <> new_idom -> Some new_idom
        | u -> u (* DO NOT ETA-EXPAND HERE! MUST BE (==) *)
        ) dom in
      dom', (changed || dom' != dom)) preds (dom, false) in
    if changed then go dom else dom
  in
  go IntMap.empty

let collect_all_defs (blocks : block IntMap.t) : StrSet.t =
  IntMap.fold (fun _ (Block (args, _)) acc ->
    List.fold_left (fun acc arg ->
      StrSet.add arg acc) acc args) blocks StrSet.empty

let collect_all_uses (blocks : block IntMap.t) : StrSet.t IntMap.t =
  let rec collect_uses s = function
    | Const _ -> s
    | Arg a -> StrSet.add a s
    | Add (a, b) -> collect_uses (collect_uses s a) b
    | Proj (_, a) -> collect_uses s a
  in
  IntMap.map (fun (Block (_, tail)) -> match tail with
    | Ret v | Cond (v, _, _) -> collect_uses StrSet.empty v
    | Goto (_, xs) ->
        List.fold_left collect_uses StrSet.empty xs
    | Call (_, f, xs) ->
        List.fold_left collect_uses StrSet.empty (f :: xs)) blocks

let liveness (blocks : block IntMap.t) : (StrSet.t * StrSet.t) IntMap.t =
  let preds = compute_in_edges blocks in
  let rec prop_liveness id v live =
    (* if it is a block argument,
       then it does not get propgated any further
     *)
    let Block (args, _) = IntMap.find id blocks in
    if List.mem v args then live
    else begin
      (* if we already marked it as livein,
         then it's already propagated, so we're done
       *)
      let (livein, liveout) = IntMap.find id live in
      let livein' = StrSet.add v livein in
      if livein' == livein then live
      else begin
        (* otherwise update the liveness information *)
        let live = IntMap.add id (livein', liveout) live in

        (* and propagate it to all the predecessors *)
        let preds = IntMap.find id preds in
        IntSet.fold (fun pred live ->
          let live = IntMap.update pred (function
            | None -> Some (StrSet.empty, StrSet.singleton v)
            | Some (livein, liveout) ->
                Some (livein, StrSet.add v liveout)) live in
          prop_liveness pred v live) preds live
      end
    end
  in
  blocks
    |> IntMap.map (fun _ -> (StrSet.empty, StrSet.empty))
    |> IntMap.fold (fun id ->
        StrSet.fold (prop_liveness id)) (collect_all_uses blocks)

let schedule_vexprs (blocks : block IntMap.t) : block IntMap.t =
  match IntMap.max_binding_opt blocks with
  | None -> blocks
  | Some (id, _) ->
    (* in short: 0: return (+ (+ a...) (+ b...)) ~~>
       0: 1(+ a...)
       1(x): 2(+ b...)
       2(y): 3(+ x y)
       3(s): return s

       which is a valid schedule (probably not the best one though).
    *)
    let rec to_cps live next acc t k = match t with
      | Arg _ as t -> k live next acc t
      | Const _ as t ->
          let n = gensym live "t" in
          let body, live, next, acc = k (StrSet.add n live) next acc @@ Arg n in
          Goto (next, [t]), live, next + 1, IntMap.add next (Block ([n], body)) acc
      | Add (a, b) ->
          to_cps live next acc a @@ fun live next acc a ->
            to_cps live next acc b @@ fun live next acc b ->
              let n = gensym live "t" in
              let body, live, next, acc = k (StrSet.add n live) next acc @@ Arg n in
              Goto (next, [Add (a, b)]), live, next + 1, IntMap.add next (Block ([n], body)) acc
      | Proj (i, v) ->
          to_cps live next acc v @@ fun live next acc v ->
            let n = gensym live "t" in
            let body, live, next, acc = k (StrSet.add n live) next acc @@ Arg n in
            Goto (next, [Proj (i, v)]), live, next + 1, IntMap.add next (Block ([n], body)) acc
    and to_cps_list live next acc xs k =
      let rec go xs' live next acc = function
        | [] -> k live next acc (List.rev xs')
        | x :: xs ->
           to_cps live next acc x @@ fun live next acc x ->
             go (x :: xs') live next acc xs in
      go [] live next acc xs
    in
    let live = collect_all_defs blocks in
    let (res, _, _) = IntMap.fold (fun id (Block (args, body)) (acc, live, next) ->
      let body, live, next, acc = match body with
        | Ret v ->
            to_cps live next acc v @@ fun live next acc v ->
              Ret v, live, next, acc
        | Cond (v, k1, k2) ->
            to_cps live next acc v @@ fun live next acc v ->
              Cond (v, k1, k2), live, next, acc
        | Goto (k, xs) ->
            to_cps_list live next acc xs @@ fun live next acc xs ->
              Goto (k, xs), live, next, acc
        | Call (k, f, xs) ->
            to_cps live next acc f @@ fun live next acc f ->
              to_cps_list live next acc xs @@ fun live next acc xs ->
                Call (k, f, xs), live, next, acc
      in
      IntMap.add id (Block (args, body)) acc, live, next
      ) blocks (IntMap.empty, live, id + 1) in
    res

let rewrite_two_addr (blocks : block IntMap.t) : block IntMap.t =
  match IntMap.max_binding_opt blocks with
  | None -> blocks
  | Some (id, _) ->
      let live = collect_all_defs blocks in
      let (res, _, _) = IntMap.fold (fun id (Block (args, body)) (acc, live, next) ->
        (* Assumes it has been converted into "normal form",
           meaning all vexprs are consumed by a Goto node.

           In our proof of concept, the only problematic node is Add.
         *)
        match body with
        | Goto (k, [Add (s1, s2)]) ->
            let n = gensym live "t" in
            let acc = IntMap.add id (Block (args, Goto (next, [s1]))) acc in
            let acc = IntMap.add next (Block ([n], Goto (k, [Add (Arg n, s2)]))) acc in
            acc, StrSet.add n live, next + 1
        | _ ->
            IntMap.add id (Block (args, body)) acc, live, next
        ) blocks (IntMap.empty, live, id + 1) in
      res

let color (dom : int IntMap.t) (live : (StrSet.t * StrSet.t) IntMap.t) (blocks : block IntMap.t) : int StrMap.t =
  let rec go acc max_color free dom_tree b =
    let (livein, _) = IntMap.find b live in
    let allowed = StrSet.fold (fun livein free ->
      match StrMap.find_opt livein acc with
      | None -> free
      | Some color -> IntSet.remove color free) livein free in
    let Block (args, _) = IntMap.find b blocks in
    let acc, max_color, free, _ = List.fold_left (fun (acc, max_color, free, allowed) arg ->
      match IntSet.min_elt_opt allowed with
      | Some color ->
          StrMap.add arg color acc, max_color, free, IntSet.remove color allowed
      | None ->
          StrMap.add arg max_color acc, max_color + 1, IntSet.add max_color free, allowed
      ) (acc, max_color, free, allowed) args in
    match IntMap.find_opt b dom_tree with
    | None -> acc, max_color, free
    | Some xs ->
        List.fold_left (fun (acc, max_color, free) ->
          go acc max_color free dom_tree) (acc, max_color, free) xs
  in
  let rec build_tree tree dom =
    match IntMap.choose_opt dom with
    | None -> tree
    | Some (n, idom) when n = idom ->
        build_tree tree (IntMap.remove n dom)
    | Some (n, idom) ->
        let tree = IntMap.update idom (function
          | None -> Some [n]
          | Some xs -> Some (n :: xs)) tree in
        build_tree tree (IntMap.remove n dom)
  in
  let dom_tree = build_tree IntMap.empty dom in
  match IntMap.min_binding_opt dom_tree with
  | None -> StrMap.empty
  | Some (start, _) ->
      let (acc, _, _) = go StrMap.empty 0 IntSet.empty dom_tree start in
      acc

let solve_phi_copy (edges : (int * int) list) : (int * int) list list =
  (* this is the same thing as "Fixing Letrec (reloaded)",
     but instead of resolving letrec's, we're resolving phi copies!
   *)
  let rec strong_connect acc m index stack v =
    let m = IntMap.add v (index, index, true) m in
    let stack = v :: stack in
    let index = index + 1 in

    let acc, m, index, stack = List.fold_left (fun (acc, m, index, stack) (d, s) ->
      if d <> v then acc, m, index, stack
      else begin
        match IntMap.find_opt s m with
        | None ->
            let acc, m, index, stack = strong_connect acc m index stack s in
            let (_, lo', _) = IntMap.find s m in
            acc, IntMap.update v (Option.map (fun (i, lo, s) ->
              i, min lo lo', s)) m, index, stack
        | Some (i', _, true) ->
            acc, IntMap.update v (Option.map (fun (i, lo, s) ->
              i, min lo i', s)) m, index, stack
        | _ -> acc, m, index, stack
      end) (acc, m, index, stack) edges in

    let i', lo', _ = IntMap.find v m in
    if i' <> lo' then acc, m, index, stack
    else begin
      let rec go scc (w :: stack) m =
        let m = IntMap.update w (Option.map (fun (i, lo, _) ->
          i, lo, false)) m in
        let scc = match List.assoc_opt w edges with
          | Some i -> (w, i) :: scc
          | None -> scc in
        if w = v then
          (if scc = [] then acc else scc :: acc), m, index, stack
        else
          go scc stack m
      in
      go [] stack m
    end
  in
  let rec go acc m index stack = function
    | [] -> acc
    | (v, _) :: xs ->
        let acc, m, index, stack = if IntMap.mem v m then
          acc, m, index, stack
        else
          strong_connect acc m index stack v in
        go acc m index stack xs
  in
  go [] IntMap.empty 0 [] edges

let hlemit (coloring : int StrMap.t) (blocks : block IntMap.t) : unit =
  IntMap.iter (fun id (Block (args, tail)) ->
    Printf.printf "%d:\n" id;
    List.iter (fun arg ->
      Printf.printf "  // %s = %%%d\n" arg (StrMap.find arg coloring)) args;
    match tail with
    | Ret (Arg s) ->
        Printf.printf "  ret %%%d\n" (StrMap.find s coloring)

    | Goto (k, [Const (Int v)]) ->
        let Block ([d], _) = IntMap.find k blocks in
        Printf.printf "  %%%d = $%d\n" (StrMap.find d coloring) v;
        Printf.printf "  goto %d\n" k

    | Goto (k, [Add (Arg a, Arg b)]) ->
        let Block ([d], _) = IntMap.find k blocks in
        Printf.printf "  %%%d = %%%d + %%%d\n"
          (StrMap.find d coloring) (StrMap.find a coloring) (StrMap.find b coloring);
        Printf.printf "  goto %d\n" k

    | Goto (k, xs) ->
        let Block (dst, _) = IntMap.find k blocks in

        let edges = List.map2 (fun d (Arg s) ->
          StrMap.find d coloring, StrMap.find s coloring) dst xs in
        let edges = solve_phi_copy edges in
        List.iter (function
        | [] -> ()  (* impossible by construction *)
        | [(a, b)] ->
            if a <> b then Printf.printf "  %%%d = %%%d\n" a b
        | (a, b) :: xs ->
            (* assume we have a free register to use, could swap instead *)
            Printf.printf "  %%t = %%%d\n" a;
            Printf.printf "  %%%d = %%%d\n" a b;
            List.iter (fun (d, s) ->
              if s = a then
                Printf.printf "  %%%d = %%t\n" d
              else
                Printf.printf "  %%%d = %%%d\n" d s) xs) edges;
        Printf.printf "  goto %d\n" k) blocks

let () =
  let prgm =
    let b0 = Goto (1, [Const (Int 1); Const (Int 2); Const (Int 3); Const (Int 4)]) in
    let f = IntMap.singleton 0 (Block ([], b0)) in
    let b1 = Goto (2, [Add (Arg "y", Arg "z")]) in
    let f = IntMap.add 1 (Block (["w"; "x"; "y"; "z"], b1)) f in
    let b2 = Goto (3, [Add (Arg "w", Arg "x")]) in
    let f = IntMap.add 2 (Block (["b"], b2)) f in
    let b3 = Ret (Add (Arg "a", Arg "b")) in
    let f = IntMap.add 3 (Block (["a"], b3)) f in
(*
    let b0 = Goto (1, [Add (Const (Int 1), Const (Int 2))]) in
    let f = IntMap.singleton 0 (Block ([], b0)) in
    let b1 = Ret (Add (Arg "x", Arg "x")) in
    let f = IntMap.add 1 (Block (["x"], b1)) f in
*)
(*
    let b0 = Goto (1, [Arg "a"; Arg "b"]) in
    let f = IntMap.singleton 0 (Block (["a"; "b"], b0)) in
    let b1 = Goto (1, [Arg "y"; Arg "x"]) in
    let f = IntMap.add 1 (Block (["x"; "y"], b1)) f in
*)
    let m = StrMap.singleton "_start" (Fun f) in
    Prgm m in
  let prgm = match prgm with Prgm p ->
    Prgm (StrMap.map (function
      | Fun u ->
          let u = u |> schedule_vexprs |> rewrite_two_addr |> renumber_blocks in
          let dom = dom_info u in
          let live = liveness u in
          let coloring = color dom live u in

          StrMap.iter (fun arg color ->
            Printf.printf "color(%s) = %d\n" arg color) coloring;

          hlemit coloring u;

          Fun u
      | i -> i) p) in
  dump_prgm prgm

(*
let spill (max_alive : int) (xs : instr list) : instr list =
  let rec go acc spill_slot alive xs =
    let du = compute_liveness xs in
    let find_spill_candidate i alive =
      (* XXX: cannot use find_first to search because non monotonic *)
      let candidate = StrSet.fold (fun u acc ->
        let next_use_at =
          du |> StrMap.find u |> IntSet.find_first (fun i' -> i' >= i)
        in
        match acc with
        | None -> Some (u, next_use_at)
        | Some (_, at') when at' < next_use_at -> Some (u, next_use_at)
        | _ -> acc) alive None in
      candidate |> Option.get
    in
    let reload u reload_at i xs =
      let rec go acc i = function
        | [] -> List.rev acc
        | x :: xs when i < reload_at ->
            go (x :: acc) (i + 1) xs
        | xs ->
            List.rev_append acc (Reload (spill_slot, u) :: xs)
      in
      go [] i xs
    in
    let remove_occ n i alive =
      let last_use = du |> StrMap.find n |> IntSet.max_elt in
      if i = last_use then StrSet.remove n alive else alive
    in
    let rec go' acc i alive = function
      | [] -> List.rev acc
      | Ret s :: xs ->
          let alive = remove_occ s i alive in
          go' (Ret s :: acc) (i + 1) alive xs
      | Spill _ :: _ ->
          failwith "IMPOSSIBLE UNLESS SPILLS WERE ALREADY PRESENT BEFOREHAND"
      | Reload (slot, d) :: xs ->
          if StrSet.cardinal alive <> max_alive then
            go' (Reload (slot, d) :: acc) (i + 1) (StrSet.add d alive) xs
          else begin
            let (u, reload_at) = find_spill_candidate i alive in
            let xs = Reload (slot, d) :: reload u reload_at (i + 1) xs in
            go (Spill (spill_slot, u) :: acc) (spill_slot + 1) (StrSet.remove u alive) xs
          end
      | Imm (v, d) :: xs ->
          if StrSet.cardinal alive <> max_alive then
            go' (Imm (v, d) :: acc) (i + 1) (StrSet.add d alive) xs
          else begin
            let (u, reload_at) = find_spill_candidate i alive in
            let xs = Imm (v, d) :: reload u reload_at (i + 1) xs in
            go (Spill (spill_slot, u) :: acc) (spill_slot + 1) (StrSet.remove u alive) xs
          end
      | Add (s1, s2, d) :: xs ->
          (* s1 + s2 : d ~~> s1 : d; s2 + d : d
             That means d's range overlaps with s2.
           *)
          let alive' = remove_occ s1 i alive in
          if StrSet.cardinal alive' <> max_alive then begin
            let alive = remove_occ s2 i alive' in
            go' (Add (s1, s2, d) :: acc) (i + 1) (StrSet.add d alive) xs
          end
          else begin
            let (u, reload_at) = find_spill_candidate i alive' in
            let xs = Add (s1, s2, d) :: reload u reload_at (i + 1) xs in
            go (Spill (spill_slot, u) :: acc) (spill_slot + 1) (StrSet.remove u alive) xs
          end
    in
    go' acc 0 alive xs
  in
  go [] 0 StrSet.empty xs

let regs = ["%rax"; "%rdx"]

let asm (coloring : int StrMap.t) (xs : instr list) : unit =
  let rec go buf maxslot = function
    | [] -> maxslot
    | Ret s :: xs ->
        let s = coloring |> StrMap.find s |> List.nth regs in
        Printf.bprintf buf "  movq %s, %%rax\n" s;
        go buf maxslot xs
    | Spill (slot, s) :: xs ->
        let s = coloring |> StrMap.find s |> List.nth regs in
        Printf.bprintf buf "  movq %s, %d(%%rbp)\n" s (-8 * (slot + 1));
        go buf (max maxslot (slot + 1)) xs
    | Reload (slot, d) :: xs ->
        let d = coloring |> StrMap.find d |> List.nth regs in
        Printf.bprintf buf "  movq %d(%%rbp), %s\n" (-8 * (slot + 1)) d;
        go buf (max maxslot (slot + 1)) xs
    | Imm (v, d) :: xs ->
        let d = coloring |> StrMap.find d |> List.nth regs in
        Printf.bprintf buf "  movq $%d, %s\n" v d;
        go buf maxslot xs
    | Add (s1, s2, d) :: xs ->
        let s1 = coloring |> StrMap.find s1 |> List.nth regs in
        let s2 = coloring |> StrMap.find s2 |> List.nth regs in
        let d = coloring |> StrMap.find d |> List.nth regs in
        Printf.bprintf buf "  movq %s, %s\n" s1 d;
        Printf.bprintf buf "  addq %s, %s\n" s2 d;
        go buf maxslot xs
  in
  let buf = Buffer.create 16 in
  let maxslot = go buf 0 xs in
  Printf.printf "_start:\n";
  if maxslot <> 0 then begin
    Printf.printf "  push %%rbp\n";
    Printf.printf "  movq %%rsp, %%rbp\n";
    Printf.printf "  subq $%d, %%rsp\n" (8 * maxslot);
  end;
  Buffer.output_buffer stdout buf;
  if maxslot <> 0 then Printf.printf "  leaveq\n";
  Printf.printf "  retq\n"
*)
