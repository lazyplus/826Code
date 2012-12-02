#!/usr/bin/python
import sys

n = float(sys.argv[1])
for line in sys.stdin.readlines():
    x = float(line)
    print x / n
