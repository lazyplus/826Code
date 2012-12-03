import sys

def get_ecoc(nstate):
        if nstate == 4:
                return [[1,1,1,1,1,1,1],
                        [0,0,0,0,1,1,1],
                        [0,0,1,1,0,0,1],
                        [0,1,0,1,0,1,0]]
        if nstate == 3:
                return [[1,1,1],
                        [0,0,1],
                        [0,1,0]]



from collections import defaultdict

def find_tag(v):
    tag = 0
    for i in range(1, len(v)):
        if v[i] > v[tag] :
            tag = i
    return tag

def hamming(v1,v2):
	diff = 0
	for i in range(len(v1)):
		if v1[i] != v2[i]:
			diff += 1
	return diff

def get_bits(v1):
	bits = list()
	for i in v1:
		if i > 0.5:
			bits.append(1)
		else:
			bits.append(0)
	return bits

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


result = open(sys.argv[2]).readlines()
nstate = int(sys.argv[3])
ecoc = get_ecoc(nstate)
right = 0
wrong = 0


for l in result:
	l = l.strip().split()
	n = l[0]
	if n not in tag:
		continue
	la = tag[n]
	vs = [ float(v) for v in l[1:] ]
	bits = get_bits(vs)
	hs = [ hamming(bits,e)  for e in ecoc ]
	minh = min(hs)
	mla = hs.index(minh)
	if mla == tag[n]:
		right += 1
	else:
		wrong += 1


print wrong
print right
