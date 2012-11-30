#!/usr/bin/python
import sys
from collections import defaultdict

nstate = int(sys.argv[1])

ps = {}
for i in range(0, nstate):
    ps[i] = defaultdict(float)
    f = open("result." + str(i)).readlines()
    for line in f:
        line = line.strip()
        part = line.split()
        ps[i][part[0]] = float(part[1])

for key in ps[0].keys():
    output = key
    for i in range(0, nstate):
        output += '\t' + str(ps[i][key])
    print output
