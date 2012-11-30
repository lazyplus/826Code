#!/usr/bin/python
import sys
from collections import defaultdict

nstate = int(sys.argv[1])

def gen_vs_data(index):
    f = open("lbp.prior." + str(index), "w+")
    prior = open("lbp.prior.all")
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
