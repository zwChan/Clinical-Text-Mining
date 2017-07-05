from __future__ import division,print_function
__author__ = 'Jason'
import sys
import os
import re
import matplotlib.pyplot as plt
import numpy as np
import gensim
from adjustText import adjust_text

def mark_termNeighbor(termList, vocab, testVocab):
    colors=[]
    marks=[]
    areas=[]
    labels=[]
    COLOR=['red','black','purple','gold','crimson','yellow','magenta','orange']

    '''  ['umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']'''
    MARK=[     'o',     '>',   '<',     '^',      'v',       's',      'p',        '.',       'd', '*']
    # each word in vocab
    for i,(v,tf) in enumerate(vocab):
        # print(i,v,tf)
        colors.append(-1)
        marks.append(-1)
        areas.append(-1)
        labels.append(-1)
        AREA = np.pi * 8**2
        # each term to evaluate
        if v in testVocab:
            for j,key in enumerate(termList):
                if v == key:
                    print("find term %s" % (v))
                    colors[i] = COLOR[j % len(COLOR)]
                    marks[i] = '*'
                    areas[i] = AREA
                    labels[i] = 'target term'
                else:
                    # each relation type
                    for k,nb in enumerate(termList[key]):
                        idx = k
                        word=nb[0]
                        dist=nb[1]
                        if v == word:
                            if colors[i] == -1: colors[i] = COLOR[j % len(COLOR)]
                            if marks[i] == -1: marks[i] = MARK[0]
                            if areas[i] == -1: areas[i] = AREA * dist * 0.3
                            if labels[i] == -1: labels[i] = 'neighbors'
                            print("find neight %s, %.2f" % (word,dist))
    return (colors,marks,areas,labels)

def get_neighbors(wv,testTerm,vocab,topn=10):
    termList = {}
    testVocab = set()
    for term in testTerm:
        nbs = wv.most_similar(term,topn=topn, restrict_vocab=len(vocab))
        termList[term] = nbs
        testVocab.add(term)
        for word,dist in nbs:
            testVocab.add(word)
    return (termList,testVocab)

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


################################################
if len(sys.argv) <= 4:
    print("Usage: [model-file] [vector-file] [vocab-file] [top-k] ",file=sys.stderr)
    exit(1)
print('\t'.join(sys.argv))
modelFile= sys.argv[1]
vecFile= sys.argv[2]
vocFile=sys.argv[3]

print("Loading vecFile...")
points = np.loadtxt(vecFile)
wv = gensim.models.KeyedVectors.load_word2vec_format(modelFile,fvocab=vocFile,binary=True)
print("vecFile loaded.")
# load np points
N = points.shape[0]
testTerm = None if len(sys.argv) <= 5 else re.split(r'#|\s|,',sys.argv[5].strip())
topn = 10 if len(sys.argv) <= 4 else min(N,int(sys.argv[4]))
# points = points[:N]
# load vocabulary
vocab = get_vocab(vocFile)
vocab = vocab[:N]
# load relation words
testTerm= ['professor'] if testTerm == None else [x.strip() for x in testTerm]
default_color = 'grey'
default_mark = 'o'
default_label = 'others'
default_area = np.pi * 5**2

termList,testVocab = get_neighbors(wv,testTerm,vocab,topn)
print("neighbors: %s" % (','.join(testVocab)))
rel_colors, rel_marks, rel_areas ,rel_labels= mark_termNeighbor(termList,vocab,testVocab)
print("len of corlor %d" % len(rel_colors))
freq = np.array([x[1] for x in vocab])
areas = np.pi * (np.log(freq) - np.log(np.min(freq)) + 1)**2

plt.figure(dpi=500,figsize=(10,6))
plt.ylim([np.min(points[:,1])-0.3,np.max(points[:,1])+0.5])
plt.xlim([np.min(points[:,0])-0.3,np.max(points[:,0])+1])

areas = [max(x) for  x in zip(areas,rel_areas)]
colors = [default_color if c == -1 else c for c in rel_colors]
marks = [default_mark if m == -1 else m for m in rel_marks]
labels = [default_label if l == -1 else l for l in rel_labels]
label_set = set(labels)
texts = []
for (key,p,v,a,c,m,l) in sorted(zip(rel_areas,points,vocab,areas,colors,marks,labels), key=lambda x: x[0]):
    alpha = 1 if v[0] in testVocab else 0.2
    lb = None
    if l in label_set:
        lb = l
        if lb ==default_label: a = default_area
        label_set.remove(l)
    if lb is None:
        plt.scatter(p[0], p[1], s=a, c=c,marker=m, alpha=alpha, edgecolors='r',linewidths=0.3)
    else:
        plt.scatter(p[0], p[1], s=a, c=c,marker=m, alpha=alpha, edgecolors='r',linewidths=0.3, label=lb)

    if v[0] in testVocab:
        texts.append(plt.text(p[0],p[1],v[0],size=8))
plt.legend(loc='lower right', scatterpoints=1, ncol=1, fontsize=8)
adjust_text(texts, arrowprops=dict(arrowstyle="->", color='b', lw=0.5))
ax = plt.gca()
leg = ax.get_legend()
for h in leg.legendHandles[1:]:
    h.set_color('red')

term_name = '-'.join(testTerm)
fname = "%s-%s-top%d-nb.jpg" % (term_name, os.path.basename(vecFile).split('.')[0], topn)
plt.savefig(fname,dpi=400,bbox_inches='tight')
# plt.show()
plt.close()

# print("Start PCA...")
# pca = PCA(n_components=0.95)  # dim = 253. so we don't use pca
# pca_result = pca.fit_transform(wv.syn0)
# print(len(pca.explained_variance_ratio_), pca.explained_variance_ratio_)

