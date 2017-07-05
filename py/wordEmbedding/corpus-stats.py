from __future__ import division,print_function
__author__ = 'Jason'
import sys
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import numpy as np
import math






if len(sys.argv) < 2:
    print("Usage: [stat-file] ",file=sys.stderr)
    exit(1)
infile = sys.argv[1]

with open(infile) as f:
    freq2word = {}
    freqList = []
    err_cnt = 0
    for line in f.readlines():
        tokens = line.split()
        if len(tokens) != 2:
            err_cnt += 1
            continue
        word = tokens[0].strip()
        freq = int(tokens[1])
        if freq > 0:
            freqList.append(freq)
        else:
            print(line)
        if freq in freq2word:
            freq2word[freq].add(word)
        else:
            freq2word[freq] = set()

freq_cnt = sorted([(k,len(v)) for k, v in freq2word.items()],reverse=True)
# freq_cnt_log = [(math.log10(x[0]+1), math.log10(x[1]+1)) for x in freq_cnt]
print(sum([v if k < 5 else 0 for k,v in freq_cnt]))

# unigram
freqList.sort(reverse=True)
fsum = sum(freqList)
freqList = [(x/fsum)**0.75 for x in freqList]
f,ax1=plt.subplots()
ax = [math.log(x+1) for x in range(0,len(freqList))]
ay = [math.log(y) for y in freqList]
# plt.plot(ax,ay)
plt.plot(freqList,'-')
plt.xlabel("Ranking index of words")
plt.ylabel("Probability")
# plt.xlim([0,100000])
plt.ylim([0,0.15])
ax1.set_xscale('log')
# ax1.set_yscale('log')
savename = 'ugram.jpg'
f.savefig(savename, bbox_inches='tight', dpi=200)
plt.show()
plt.close()
print("save image %s" % savename)