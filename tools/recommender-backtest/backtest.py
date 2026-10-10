#!/usr/bin/env python3
"""
Back-test of score-pick policies for the betting recommender on historical EPL data.

Data: football-data.co.uk E0.csv files (results + closing odds), e.g.
  https://www.football-data.co.uk/mmz4281/2425/E0.csv   (2425 = season 2024/25)
Usage:
  python3 tools/recommender-backtest/backtest.py E0_2223.csv E0_2324.csv E0_2425.csv

For every match the script
  1. de-vigs Pinnacle closing 1X2 odds (power method) and the 2.5 over/under line,
  2. fits independent-Poisson lambdas (home, away) to those probabilities,
  3. picks a scoreline by several policies and scores it with the app's rules
     (EPL 4/2/1/-1, personal week bonus 5/3/2/0, cup 2/1/0).
Only market data is used (no FootyStats stats exist for past seasons), so this tests the
*pick rule* and the value of market information, not the app's statistics layer.
Needs: pandas, numpy.
"""
import math
import sys
import warnings

import numpy as np
import pandas as pd

warnings.filterwarnings("ignore")
G, K = 11, 6  # goals 0..10 in matrices, candidate picks 0..5


def load(paths):
    frames = []
    for p in paths:
        df = pd.read_csv(p)
        df["season"] = p
        frames.append(df)
    d = pd.concat(frames, ignore_index=True)
    return d.dropna(subset=["FTHG", "FTAG", "PSCH", "PSCD", "PSCA"]).reset_index(drop=True)


def devig_power(oh, od, oa):
    inv = np.stack([1 / oh, 1 / od, 1 / oa], 1)
    out = np.zeros_like(inv)
    for i, row in enumerate(inv):
        lo, hi = 0.5, 2.0
        for _ in range(60):
            k = (lo + hi) / 2
            lo, hi = (k, hi) if (row ** k).sum() > 1 else (lo, k)
        out[i] = row ** k
    return out


FACT = np.array([math.factorial(i) for i in range(G)], dtype=float)


def pois_row(l):
    p = np.exp(-l) * l ** np.arange(G) / FACT
    return p / p.sum()


def pois_mat(a, b):
    m = np.outer(pois_row(a), pois_row(b))
    return m / m.sum()


def fit_lambdas(P3, po, ou_ok):
    lam = np.arange(0.15, 5.0, 0.025)
    pmf = np.stack([pois_row(l) for l in lam])
    lt = np.cumsum(pmf, 1) - pmf
    ph, pdr = pmf @ lt.T, pmf @ pmf.T
    pa = 1 - ph - pdr
    s = lam[:, None] + lam[None, :]
    pover = 1 - np.exp(-s) * (1 + s + s ** 2 / 2)
    lh, la = np.zeros(len(P3)), np.zeros(len(P3))
    for i in range(len(P3)):
        err = (ph - P3[i, 0]) ** 2 + (pdr - P3[i, 1]) ** 2 + (pa - P3[i, 2]) ** 2
        if ou_ok[i]:
            err = err + (pover - po[i]) ** 2
        a, b = np.unravel_index(np.argmin(err), err.shape)
        lh[i], la[i] = lam[a], lam[b]
    return lh, la


def payoffs(mode):
    pay = np.zeros((K, K, G, G))
    for h in range(K):
        for a in range(K):
            for i in range(G):
                for j in range(G):
                    exact, sd = (i == h and j == a), (i - j == h - a)
                    so = (i > j and h > a) or (i < j and h < a)
                    if mode == "epl":
                        v = 4 if exact else 2 if sd else 1 if so else -1
                    elif mode == "bonus":
                        v = 5 if exact else 3 if sd else 2 if so else 0
                    else:
                        v = 2 if exact else 1 if (sd or so) else 0
                    pay[h, a, i, j] = v
    return pay


def main(paths):
    d = load(paths)
    n = len(d)
    fh = np.minimum(d.FTHG.astype(int).values, 10)
    fa = np.minimum(d.FTAG.astype(int).values, 10)
    season = d.season.values
    P3 = devig_power(d.PSCH.values, d.PSCD.values, d.PSCA.values)
    over, under = d["PC>2.5"], d["PC<2.5"]
    ou_ok = over.notna().values
    po = np.where(ou_ok, (1 / over.fillna(2)) / ((1 / over.fillna(2)) + (1 / under.fillna(2))), np.nan)
    y = np.where(fh > fa, 0, np.where(fh == fa, 1, 2))
    print(f"matches: {n}; market 1X2 log-loss {-np.mean(np.log(P3[np.arange(n), y])):.4f} "
          f"(uniform {math.log(3):.4f}); mean P home/draw/away {P3.mean(0).round(3)} "
          f"vs actual {[round(float((y == k).mean()), 3) for k in range(3)]}")
    lh, la = fit_lambdas(P3, po, ou_ok)
    M0 = np.stack([pois_mat(a, b) for a, b in zip(lh, la)])
    PAY = {m: payoffs(m) for m in ("epl", "bonus", "cup")}

    def ev(M, mode):
        return np.tensordot(PAY[mode], M, axes=([2, 3], [0, 1]))

    def pick_ev(M):
        e = ev(M, "epl")
        return np.unravel_index(np.argmax(e), e.shape)

    def pick_modal(M):
        return np.unravel_index(np.argmax(M[:K, :K]), (K, K))

    def shrunk(s):
        mh, ma = lh.mean(), la.mean()
        return np.stack([pois_mat(mh + s * (a - mh), ma + s * (b - ma)) for a, b in zip(lh, la)])

    pols = {
        "modal pick": [pick_modal(M) for M in M0],
        "EV pick (max expected EPL points)": [pick_ev(M) for M in M0],
        "modal, market shrunk x0.5": [pick_modal(M) for M in shrunk(0.5)],
        "EV pick, market shrunk x0.5": [pick_ev(M) for M in shrunk(0.5)],
        "EV pick, no information (x0)": [pick_ev(M) for M in shrunk(0.0)],
        "always 1:1": [(1, 1)] * n,
        "always 1:0": [(1, 0)] * n,
    }

    def realized(picks, mode):
        return np.array([PAY[mode][h, a, fh[j], fa[j]] for j, (h, a) in enumerate(picks)])

    rows = {k: {m: realized(v, m) for m in PAY} for k, v in pols.items()}
    seasons = sorted(set(season))
    print(f"\n{'policy':38s} {'EPL':>7s} {'bonus':>7s} {'cup':>6s}   per season (EPL)")
    for k, r in rows.items():
        per = " ".join(f"{r['epl'][season == s].mean():+.2f}" for s in seasons)
        print(f"{k:38s} {r['epl'].mean():+7.3f} {r['bonus'].mean():+7.3f} {r['cup'].mean():+6.3f}   {per}")
    rng = np.random.default_rng(7)
    a, b = rows["EV pick (max expected EPL points)"]["epl"], rows["modal pick"]["epl"]
    diff = a - b
    boots = [diff[rng.integers(0, n, n)].mean() for _ in range(4000)]
    print(f"\nEV pick minus modal pick: {diff.mean():+.3f} pts/match, 95% CI "
          f"[{np.percentile(boots, 2.5):+.3f}, {np.percentile(boots, 97.5):+.3f}]")
    exp_ev = np.mean([ev(M, "epl")[tuple(pols["EV pick (max expected EPL points)"][j])] for j, M in enumerate(M0)])
    print(f"model-expected EV-pick points/match {exp_ev:.3f} vs realised {a.mean():.3f} (calibration check)")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
