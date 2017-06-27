from __future__ import division,print_function
__author__ = 'Jason'
import sys
import math
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import numpy as np


def get_vocab(vocFile):
    # construct the vocabulary for evaluation
    evalVocab = []
    with open(vocFile) as f:
        for line in f.readlines():
            tokens = line.split()
            tf = 0 if len(tokens) < 2 else int(tokens[1])
            if len(tokens)>1:
                evalVocab.append((tokens[0], int(tokens[1])))
    print("size of vocabulary is %d" % len(evalVocab))
    return evalVocab

if len(sys.argv) <= 3:
    print("Usage: [model-file] [vocab-file] [top-k] [relation-file] ",file=sys.stderr)
    exit(1)
print('\t'.join(sys.argv))
vecFile= sys.argv[1]
vocFile=sys.argv[2]
print("Loading vecFile...")
points = np.loadtxt(vecFile)
print("vecFile loaded.")

N = points.shape[0]
topn = N if len(sys.argv) <= 3 else int(sys.argv[3])
points = points[:topn]

vocab = get_vocab(vocFile)
vocab = vocab[:topn]
freq = np.array([x[1] for x in vocab])
area = np.pi * (np.log(freq) - np.log(np.min(freq)) + 1)**2
plt.figure()
plt.scatter(points[:,0], points[:,1], s=area)
plt.show()
fname = "tsne-top%d.png" % (topn)
# plt.savefig(fname,dpi=200, bbox_inches='tight')
plt.close()

# print("Start PCA...")
# pca = PCA(n_components=0.95)  # dim = 253. so we don't use pca
# pca_result = pca.fit_transform(wv.syn0)
# print(len(pca.explained_variance_ratio_), pca.explained_variance_ratio_)

