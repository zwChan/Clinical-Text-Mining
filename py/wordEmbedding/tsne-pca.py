from __future__ import division,print_function
__author__ = 'Jason'
import sys
import  gensim
from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
import numpy as np
import matplotlib.pyplot as plt
import math

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
    print("Usage: [model-file] [vocab-file] [vector-out-file] [tf-mini] [pca]",file=sys.stderr)
    exit(1)
print('\t'.join(sys.argv))
model = sys.argv[1]
vocFile=sys.argv[2]
outFile=sys.argv[3]
tfTh = 100 if len(sys.argv) <= 4 else int(sys.argv[4])
ispca = False if len(sys.argv) <= 5 else sys.argv[5].strip() == 'pca'
print("Loading model...")
evalVocab = get_evaluation_vocab(vocFile,tfTh)
wv = gensim.models.KeyedVectors.load_word2vec_format(model,fvocab=vocFile,binary=True)
print("model loaded.")
topn = len(evalVocab)

if ispca:
    print("Start PCA...")
    pca = PCA(n_components=0.95)  # dim = 253. so we don't use pca
    pca_result = pca.fit_transform(wv.syn0[:topn])
    print(len(pca.explained_variance_ratio_), pca.explained_variance_ratio_)

    varList = pca.explained_variance_ratio_
    # varList.sort(reverse=True)
    # fsum = sum(varList)
    # varList = [(x/fsum)**0.75 for x in varList]
    f,ax1=plt.subplots()
    plt.plot(varList,marker='o', linestyle='--', color='r')
    plt.xlabel("Ranking index of eigen value")
    plt.ylabel("Variance ratio")
    # plt.xlim([0,100000])
    # plt.ylim([0,0.15])
    savename = 'pca-variance95-tf%d.jpg'% tfTh
    f.savefig(savename, bbox_inches='tight', dpi=200)
    # plt.show()
    plt.close()

    np.savetxt(outFile,pca_result[:,0:2])
else:
    print("TSNE...")
    tsne = TSNE(n_components=2, verbose=1, perplexity=40, n_iter=5000)
    tsne_ret = tsne.fit_transform(wv.syn0[:topn])
    print("TSNE done.")
    np.savetxt(outFile,tsne_ret)



