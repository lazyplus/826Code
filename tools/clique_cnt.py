#!/usr/bin/python
import sys
from collections import defaultdict

root = dict()

def find(a):
    if(root[a] == a):
        return a
    root[a] = find(root[a])
    return root[a]

def union(a, b):
    root[find(a)] = find(b)

for line in sys.stdin.readlines():
    parts = line.strip().split()
    a = int(parts[0])
    b = int(parts[1])
    if(a not in root):
        root[a] = a
    if(b not in root):
        root[b] = b
    union(a, b)

cnt = defaultdict(int)
for key in root.iterkeys():
    cnt[find(root[key])] += 1

print len(root)
print cnt
print len(cnt)
