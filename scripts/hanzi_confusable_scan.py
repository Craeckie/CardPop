#!/usr/bin/env python3
"""Find visually-confusable Han characters in a CardPop/FloFla deck backup.

Chinese learners frequently lapse on characters that *look* alike — a newer
character resembles an older one and the two interfere. FSRS can't model this
(it scores every card independently), so such cards become leeches. This script
surfaces those look-alike clusters and cross-references them against the cards
you are actually struggling with, so you know which ones to disambiguate first.

How it works
------------
Each Han character in the deck is decomposed via IDS (Ideographic Description
Sequences) from the CHISE/cjkvi-ids project. Two characters are flagged as
confusable when they share the same overall structure and the same *main body*,
differing only in a small radical — the 请/清/情/晴/睛 (⿰X青) pattern. A small
curated list covers classic atom look-alikes (己/已/巳, 未/末/本/木, …) that IDS
treats as indivisible. Clusters are then ranked by how much you're struggling
with the member cards (FSRS difficulty + lapses + low stability).

The IDS data (~2.6 MB) is downloaded once and cached under
~/.cache/cardpop-hanzi/ ; override with --ids-file.

Usage
-----
    python3 scripts/hanzi_confusable_scan.py BACKUP.json
    python3 scripts/hanzi_confusable_scan.py BACKUP.json --compact
    python3 scripts/hanzi_confusable_scan.py BACKUP.json --min-struggle 1.3
"""
import argparse, collections, itertools, json, os, sys, urllib.request

IDC = set("⿰⿱⿲⿳⿴⿵⿶⿷⿸⿹⿺⿻")
IDS_URLS = [
    "https://raw.githubusercontent.com/cjkvi/cjkvi-ids/master/ids.txt",
    "https://raw.githubusercontent.com/cjkvi/cjkvi-ids/master/ids-ext-cdef.txt",
]
CACHE_DIR = os.path.join(os.path.expanduser("~"), ".cache", "cardpop-hanzi")

# Classic atom look-alikes IDS treats as indivisible (intersected with the deck).
CURATED = ["未末本木术", "己已巳", "千干于", "牛午", "我找", "鸟乌", "买卖",
           "日曰旦目自白", "田由甲申电", "大太天犬夭头", "人入八", "土士",
           "刀力", "贝见", "半羊", "在再", "候后", "很跟根", "题提", "住往主",
           "天夫无", "今令", "白百自", "回向", "话活", "找我钱线"]


def load_ids(ids_file=None):
    """Return {char: primary-IDS}. Downloads + caches cjkvi-ids on first use."""
    paths = []
    if ids_file:
        paths = [ids_file]
    else:
        os.makedirs(CACHE_DIR, exist_ok=True)
        for url in IDS_URLS:
            dest = os.path.join(CACHE_DIR, os.path.basename(url))
            if not os.path.exists(dest):
                sys.stderr.write(f"downloading {url} ...\n")
                urllib.request.urlretrieve(url, dest)
            paths.append(dest)
    prim = {}
    for path in paths:
        try:
            fh = open(path, encoding="utf-8")
        except FileNotFoundError:
            continue
        for line in fh:
            if line.startswith("#"):
                continue
            p = line.rstrip("\n").split("\t")
            if len(p) < 3 or p[1] in prim:
                continue
            prim[p[1]] = p[2].split("[")[0].strip()  # drop [GTKJ] source tags
    if not prim:
        sys.exit("error: no IDS data loaded (check --ids-file or network).")
    return prim


def is_han(c):
    return "一" <= c <= "鿿" or "㐀" <= c <= "䶿"


class Confuser:
    def __init__(self, prim):
        self.prim = prim

    def nleaves(self, ch, _seen=None, depth=0):
        _seen = _seen or set()
        if ch in _seen or depth > 8:
            return 1
        comps = [c for c in (self.prim.get(ch) or "") if c not in IDC and c != ch]
        if not comps:
            return 1
        return sum(self.nleaves(c, _seen | {ch}, depth + 1) for c in comps)

    def comps1(self, ch):
        ids = self.prim.get(ch, ch)
        return [c for c in ids if c not in IDC and c != ch] or [ch]

    def topop(self, ch):
        ids = self.prim.get(ch, ch)
        return ids[0] if ids and ids[0] in IDC else ""

    def confusable(self, a, b):
        """Same structure, shared bulk, differ in exactly one small radical."""
        if self.topop(a) != self.topop(b):
            return False
        ca, cb = self.comps1(a), self.comps1(b)
        if len(ca) != len(cb) or len(ca) < 2:
            return False
        diffs = [(x, y) for x, y in zip(ca, cb) if x != y]
        if len(diffs) != 1:
            return False
        shared = [x for x, y in zip(ca, cb) if x == y]
        shared_leaves = sum(self.nleaves(x) for x in shared)
        diff_leaves = max(self.nleaves(diffs[0][0]), self.nleaves(diffs[0][1]))
        return shared_leaves >= 2 and shared_leaves >= diff_leaves


def render_markdown(ranked, struggle, common_comp, distinguishing,
                    worstcard, words_of, gloss, g, n_chars, n_links):
    def esc(s):
        return s.replace("|", "/")

    def strug(ch):
        return struggle(ch) >= 1.3

    def nstrug(m):
        return sum(strug(c) for c in m)

    pri = [m for m in ranked if nstrug(m) >= 2]
    watch = [m for m in ranked if nstrug(m) == 1]
    clean = [m for m in ranked if nstrug(m) == 0]
    o = []
    o += ["# CardPop — Confusable Characters", "",
          f"_{n_chars} Han characters · {n_links} confusable links · {len(ranked)} clusters_", "",
          "`*` marks a card you are currently struggling with. Each cluster shares a main "
          "component and differs only in a small part — the **Differs** column is what to lean "
          "on when telling them apart.", "",
          f"- 🔴 **Priority** (2+ struggling): {len(pri)}",
          f"- 🟡 **Watch** (1 struggling): {len(watch)}",
          f"- ⚪ **Clean** (look-alikes, no lapses yet): {len(clean)}", ""]

    o += ["## 🔴 Priority — fix these first", ""]
    for m in pri:
        members = sorted(m, key=struggle, reverse=True)
        common = common_comp(members)
        title = " · ".join(f"{c}{'*' if strug(c) else ''}" for c in members)
        o += [f"### {title}" + (f" — shared **{''.join(common)}**" if common else ""), "",
              "| Char | Differs | Words | Diff | Lapses | Stab | Meaning |",
              "|---|---|---|--:|--:|--:|---|"]
        for ch in members:
            w = worstcard(ch)
            o.append(f"| {ch} | {''.join(distinguishing(ch, common)) or '—'} | {esc(' '.join(words_of(ch)))} "
                     f"| {g(w,'difficulty'):.0f} | {g(w,'lapses')} | {g(w,'stability'):.0f}d | {esc(gloss(w))} |")
        o.append("")

    o += ["## 🟡 Watch — one side already lapsing", "",
          "| Cluster | Shared | Struggling card | Diff | Lapses | Stab |",
          "|---|:--:|---|--:|--:|--:|"]
    for m in watch:
        members = sorted(m, key=struggle, reverse=True)
        common = common_comp(members)
        s = members[0]
        w = worstcard(s)
        cl = " ".join(f"{c}{'*' if strug(c) else ''}" for c in members)
        o.append(f"| {cl} | {''.join(common) or '—'} | {s} {esc('/'.join(words_of(s)))} "
                 f"| {g(w,'difficulty'):.0f} | {g(w,'lapses')} | {g(w,'stability'):.0f}d |")
    o.append("")

    o += ["## ⚪ Clean look-alikes — no lapses yet", ""]
    for m in clean:
        members = sorted(m)
        common = common_comp(members)
        words = " · ".join("/".join(words_of(ch)) for ch in members)
        o.append(f"- **{' '.join(members)}**" + (f" _(shared {''.join(common)})_" if common else "")
                 + f" — {esc(words)}")
    return "\n".join(o)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("backup", help="CardPop backup JSON")
    ap.add_argument("--ids-file", help="path to a local cjkvi ids.txt (skips download)")
    ap.add_argument("--compact", action="store_true", help="one line per cluster")
    ap.add_argument("--markdown", action="store_true", help="render a formatted Markdown report")
    ap.add_argument("--min-struggle", type=float, default=0.0,
                    help="only show clusters with a member at/above this struggle score")
    args = ap.parse_args()

    cf = Confuser(load_ids(args.ids_file))
    backup = json.load(open(args.backup, encoding="utf-8"))
    cards = [c for c in backup["flashcards"] if c.get("isEnabled", True)]

    def g(c, k):
        return c.get(k, 0)

    char_cards = collections.defaultdict(list)
    for c in cards:
        for ch in set(c.get("question", "")):
            if is_han(ch):
                char_cards[ch].append(c)
    deck = set(char_cards)

    def struggle(ch):
        best = 0.0
        for c in char_cards[ch]:
            s = g(c, "difficulty") / 10 + min(g(c, "lapses"), 5) / 5 * 1.5 \
                + (0.5 if (g(c, "state") == 2 and g(c, "stability") < 7) else 0)
            best = max(best, s)
        return best

    edges = []
    for a, b in itertools.combinations(sorted(deck), 2):
        if cf.confusable(a, b):
            edges.append((a, b))
    eset = {frozenset(e) for e in edges}
    for grp in CURATED:
        present = [c for c in grp if c in deck]
        for a, b in itertools.combinations(present, 2):
            if frozenset((a, b)) not in eset:
                edges.append((a, b)); eset.add(frozenset((a, b)))

    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]; x = parent[x]
        return x

    for a, b in edges:
        parent[find(a)] = find(b)
    clusters = collections.defaultdict(set)
    for a, b in edges:
        clusters[find(a)].add(a); clusters[find(a)].add(b)

    def worstcard(ch):
        return max(char_cards[ch], key=lambda x: g(x, "difficulty") / 10 + min(g(x, "lapses"), 5))

    def gloss(c, n=48):
        return " ".join(c.get("answer", "").split())[:n]

    def words_of(ch):
        return sorted({x.get("question", "") for x in char_cards[ch]})

    def common_comp(members):
        sets = [set(cf.comps1(ch)) for ch in members]
        return [c for c in cf.comps1(members[0]) if all(c in s for s in sets)]

    def distinguishing(ch, common):
        return [c for c in cf.comps1(ch) if c not in common]

    ranked = sorted(clusters.values(), key=lambda m: max(struggle(c) for c in m), reverse=True)
    ranked = [m for m in ranked if max(struggle(c) for c in m) >= args.min_struggle]

    if args.markdown:
        print(render_markdown(ranked, struggle, common_comp, distinguishing,
                              worstcard, words_of, gloss, g, len(deck), len(edges)))
        return

    print(f"# {len(deck)} Han chars | {len(edges)} confusable links | {len(ranked)} clusters shown\n")
    for m in ranked:
        members = sorted(m, key=struggle, reverse=True)
        nstrug = sum(struggle(c) >= 1.3 for c in m)
        flag = " <-- 2+ STRUGGLING" if nstrug >= 2 else (" <- 1 struggling" if nstrug == 1 else "")
        head = "  ".join(f"{c}{'*' if struggle(c) >= 1.3 else ''}" for c in members)
        if args.compact:
            words = " | ".join(
                f"{ch}:" + "/".join(sorted({x.get('question', '') for x in char_cards[ch]}))
                for ch in members)
            print(f"[ {head} ]{flag}\n    {words}")
        else:
            print(f"[ {head} ]{flag}")
            for ch in members:
                w = worstcard(ch)
                fronts = ", ".join(sorted({x.get('question', '') for x in char_cards[ch]}))[:40]
                print(f"    {ch}  in: {fronts:<40}  worst[diff{g(w,'difficulty'):.0f} "
                      f"lap{g(w,'lapses')} {g(w,'stability'):.0f}d]  {gloss(w)}")
            print()


if __name__ == "__main__":
    main()
