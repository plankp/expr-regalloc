type instr =
  | Ret of string
  | Spill of int * string
  | Reload of int * string
  | Imm of int * string
  | Add of string * string * string

let dump = function
  | Ret n -> Printf.printf "return %s\n" n
  | Spill (slot, s) -> Printf.printf "spill #%d %s\n" slot s
  | Reload (slot, d) -> Printf.printf "reload #%d : %s\n" slot d
  | Imm (i, d) -> Printf.printf "imm $%d : %s\n" i d
  | Add (s1, s2, d) -> Printf.printf "%s + %s : %s\n" s1 s2 d

module IntSet = Set.Make (Int)
module StrSet = Set.Make (String)
module StrMap = Map.Make (String)

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

let uniq_names (live : StrSet.t) (xs : instr list) : instr list =
  let map_value subst v =
    subst |> StrMap.find_opt v |> Option.value ~default:v
  in
  let rec go acc live subst = function
    | [] -> List.rev acc
    | Ret v :: xs ->
        go (Ret (map_value subst v) :: acc) live subst xs
    | Spill (slot, s) :: xs ->
        go (Spill (slot, map_value subst s) :: acc) live subst xs
    | Reload (slot, d) :: xs ->
        let d' = gensym live d in
        go (Reload (slot, d') :: acc) (StrSet.add d' live) (StrMap.add d d' subst) xs
    | Imm (v, d) :: xs ->
        let d' = gensym live d in
        go (Imm (v, d') :: acc) (StrSet.add d' live) (StrMap.add d d' subst) xs
    | Add (s1, s2, d) :: xs ->
        let d' = gensym live d in
        go (Add (map_value subst s1, map_value subst s2, d') :: acc) (StrSet.add d' live) (StrMap.add d d' subst) xs
  in
  go [] live StrMap.empty xs

let compute_liveness (xs : instr list) : IntSet.t StrMap.t =
  let use_value i v =
    StrMap.update v @@ function
      | None -> Some (IntSet.singleton i)
      | Some s -> Some (IntSet.add i s)
  in
  let rec go i = function
    | [] -> StrMap.empty
    | Ret v :: xs ->
        xs |> go (i + 1) |> use_value i v
    | Spill (_, v) :: xs ->
        xs |> go (i + 1) |> use_value i v
    | Reload (_, _) :: xs ->
        xs |> go (i + 1)
    | Imm (_, _) :: xs ->
        xs |> go (i + 1)
    | Add (a, b, _) :: xs ->
        xs |> go (i + 1) |> use_value i a |> use_value i b
  in
  go 0 xs

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

let compute_igraph (xs : instr list) : (string * StrSet.t) list =
  let rec go acc = function
    | [] -> acc, StrSet.empty
    | Ret v :: xs ->
        let acc, fv = go acc xs in
        acc, StrSet.add v fv
    | Spill (_, s) :: xs ->
        let acc, fv = go acc xs in
        acc, StrSet.add s fv
    | Reload (_, d) :: xs ->
        let acc, fv = go acc xs in
        let fv = StrSet.remove d fv in
        (d, fv) :: acc, fv
    | Imm (_, d) :: xs ->
        let acc, fv = go acc xs in
        let fv = StrSet.remove d fv in
        (d, fv) :: acc, fv
    | Add (s1, s2, d) :: xs ->
        (* as above, s2 is simultaneously live with d *)
        let acc, fv = go acc xs in
        let fv = StrSet.remove d fv in
        let fv = StrSet.add s2 fv in
        (d, fv) :: acc, StrSet.add s1 fv
  in
  xs |> go [] |> fst

let color (order : (string * StrSet.t) list) : int * int StrMap.t =
  let rec go acc max max_colors = function
    | [] -> max, acc
    | (n, neighbours) :: xs ->
        let s' = StrSet.fold (fun n ->
          IntSet.remove (StrMap.find n acc)) neighbours max_colors in
        let max, max_colors, color = match IntSet.min_elt_opt s' with
          | None -> max + 1, IntSet.add max max_colors, max
          | Some color -> max, max_colors, color in
        go (StrMap.add n color acc) max max_colors xs
  in
  go StrMap.empty 0 IntSet.empty order

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

let () =
  let prgm =
    [
     Imm (1, "a");
     Imm (2, "b");
     Add ("a", "b", "x");
     Add ("x", "x", "c");
     Ret "c"
(*
     Imm (0, "a");
     Imm (1, "b");
     Imm (2, "c");
     Imm (3, "d");
     Add ("c", "d", "e");
     Add ("a", "b", "f");
     Add ("e", "f", "g");
     Ret "g"
*)
(*
     Imm (0, "a");
     Imm (1, "b");
     Add ("a", "b", "c");
     Add ("a", "b", "d");
     Add ("c", "d", "e");
     Ret "e"
*)
    ] |> uniq_names StrSet.empty in
  let prgm = prgm |> spill 2 |> uniq_names StrSet.empty in
  let max, coloring = prgm |> compute_igraph |> color in
  if max > 2 then failwith "SPILLING WAS INCORRECT!";
  asm coloring prgm

