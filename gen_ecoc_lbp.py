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


def get_labels(code):
	labels = list()
	for i in range(len(code)):
		if code[i] == 1:
			labels.append(i)
	return labels

def gen_vs_data(index,labels):
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
        mp = max(p)
        mi = p.index(mp)
        if mi in labels:
                f.write(parts[0] + "\tp" + str(p[mi]) + "\n")
        else:
                f.write(parts[0] + "\tp" + str(p[labels[0]]) + "\n")

import sys

nstate = int(sys.argv[1])

ecoc = get_ecoc(nstate)
for i in range(len(ecoc[0])):
	code = list()
	for j in range(nstate):
		code.append(ecoc[j][i])
	labels = get_labels(code)
	gen_vs_data(i,labels)    
