#!/usr/bin/python
import sys
from collections import defaultdict

nstate = int(sys.argv[1])

def load_psi():
    psi = defaultdict(dict)
    f = open("bp.ep.all").readlines()
    i = 0
    for line in f:
        sum = 1.0
        parts = line.strip().split()
        j = 0
        for x in parts:
            psi[i][j] = float(x)
            sum -= float(x)
            j = j + 1
        psi[i][j] = sum
        i = i + 1
    return psi

psi = load_psi()

def gen_vs_data(index):
    f = open("bp.ep." + str(index), "w+")
    f.write(str(psi[index][index]) + "\n")
    f.write(str(1.0 - psi[index][index]))
    f.close()
    f = open("bp.prior." + str(index), "w+")
    prior = open("bp.prior.all")
    for line in prior.readlines():
        parts = line.strip().split()
        p = []
        sum = 0.0
        for i in range(1, len(parts)):
            p.append(float(parts[i][1:]))
            sum += float(parts[i][1:])
        p.append(1.0 - sum)
        f.write(parts[0] + "\tp" + str(p[index]) + "\n")

for i in range(0, nstate):
    gen_vs_data(i)
