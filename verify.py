#!/usr/bin/python
import sys
from collections import defaultdict

def find_tag(v):
    tag = 0
    for i in range(1, len(v)):
        if v[i] > v[tag] :
            tag = i
    return tag

tag = defaultdict(int)

cnt = defaultdict(int)
prior = open(sys.argv[1]).readlines()
for line in prior:
    line = line.strip()
    # print line
    sum = 0.0
    tokens = line.split('\t')
    # print tokens
    v = []
    for i in range(1, len(tokens)):
        v.append(float(tokens[i][1:]))
        # print tokens[i][1:]
        sum += v[-1]
    v.append(1-sum)
    # print v
    tag[tokens[0]] = find_tag(v)
    cnt[tag[tokens[0]]] += 1
    # print find_tag(v)
print cnt

Wrong = 0
Correct = 0

res_cnt = defaultdict(int)
result = open(sys.argv[2]).readlines()
for line in result:
    line = line.strip()
    v = []
    tokens = line.split('\t')
    for i in range(1, len(tokens)):
        v.append(float(tokens[i]))
    rtag = find_tag(v)
    if rtag != tag[tokens[0]] :
        Wrong += 1
        # print rtag, tag[tokens[0]], tokens[0]
    else:
        Correct += 1
    res_cnt[rtag] += 1

print Wrong
print Correct
print res_cnt