#!/usr/bin/python
import random
import sys

f = sys.stdin.readlines()

random.shuffle(f)

for l in f:
    print l.strip()
