from __future__ import division,print_function
__author__ = 'Jason'
import sys
import  gensim
from sklearn.manifold import TSNE
import numpy as np


def get_evaluation_vocab(vocFile,tfTh=0):
    # construct the vocabulary for evaluation
    evalVocab = {}
    with open(vocFile) as f:
        for line in f.readlines():
            tokens = line.split()
            tf = 0 if len(tokens) < 2 else int(tokens[1])
            if len(tokens)>1 and tf > tfTh:
                evalVocab[tokens[0].lower()] = int(tokens[1])
    print("size of vocabulary is %d" % len(evalVocab))
    return evalVocab


if len(sys.argv) <= 3:
    print("Usage: [model-file] [vocab-file] [top-k] ",file=sys.stderr)
    exit(1)
print('\t'.join(sys.argv))
model = sys.argv[1]
vocFile=sys.argv[2]
outFile=sys.argv[3]
tfTh = 100 if len(sys.argv) <= 4 else int(sys.argv[4])
print("Loading model...")
evalVocab = get_evaluation_vocab(vocFile,1000)
wv = gensim.models.KeyedVectors.load_word2vec_format(model,fvocab=vocFile,binary=True)
print("model loaded.")
topn = len(evalVocab)
print("TSNE...")
tsne = TSNE(n_components=2, verbose=1, perplexity=40, n_iter=1000)
tsne_ret = tsne.fit_transform(wv.syn0[:topn])
print("TSNE done.")
np.savetxt(outFile,tsne_ret)


# print("Start PCA...")
# pca = PCA(n_components=0.95)  # dim = 253. so we don't use pca
# pca_result = pca.fit_transform(wv.syn0)
# print(len(pca.explained_variance_ratio_), pca.explained_variance_ratio_)

