from __future__ import division,print_function
__author__ = 'Jason'
import sys
import os
import re
import csv
import random
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import numpy as np
from adjustText import adjust_text

class Term:
    """
    ['term name','term_norm', 'umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']
    """
    relName = []
    NameIndex = 0
    NormIndex = 1

    def __init__(self, term=''):
        self.name = term # name of the term, e.g. Love (N)
        self.relValue = [] # relation word of the term. e.g. synonym, antonym, ...
        self.relHit = []   # the relation word is hit in the nearest neighbors.
        self.similar = []  # similar terms retrieved from embedding vectors
    def __str__(self, simple=True):
        ret = ''
        if simple:
            ret += "%s\t" % ('\t'.join(['|'.join(t) for t in self.relValue[0:self.NormIndex+1]]))
            ret += "%s" % ('\t'.join([ '|'.join(t) for t in self.relHit[self.NormIndex+1:]]))
        else:
            ret += 'name:\t%s, norm:%s\n' % (self.name,self.relValue[self.NormIndex][0])
            ret += 'similar:\t'
            for sim in self.similar:
                ret += "(%s,%0.2f), " % sim
            ret += '\n'
            for i,title in enumerate(self.relName):
                if i < self.NormIndex: continue
                ret += "%s:\t%s\n" % (title,' '.join(self.relHit[i]))
            ret += '\n'
        return ret

'''
return { term_norm->[list(relation_words), list(relation-words)] }
'''
def load_relation(csvfile, evalVocab, testTerm,sample=1.0):
    global withRelation
    termList = {}
    testVocab = set()
    testTerm = [x.lower() for x in testTerm]
    print("loading relation")
    with open(csvfile, 'rb') as csvfile:
        csvreader = csv.reader(csvfile, delimiter='\t', quotechar='"')
        first = True
        for row in csvreader:
            if len(row) < 2: continue
            if first:
                first = False
                Term.relName += row
                continue

            if sample > 0 and random.random() > sample: continue  # sample question, for test only
            #if row[0].lower()  not in testTerm: continue
            term = Term(row[0])
            isTestTerm = True
            for i,rel in enumerate(row):
                term.relValue.append([])
                # if len(rel)==0: continue
                if i == Term.NameIndex:  # add the name of the term
                    term.relValue[i].append(rel)
                    continue
                if i == term.NormIndex and rel.lower() not in testTerm:
                    isTestTerm = False
                rel_term = rel.split(',')
                for t in rel_term:
                    if len(t.strip()) == 0: continue
                    if t.lower() not in evalVocab:
                        if i == Term.NormIndex :
                            # print("%s in term(%s) is ignored record for term caused by not-in-evalVocab " % (t, term.name), file=sys.stderr)
                            break
                        continue
                    if (withRelation == False and i > term.NormIndex) or isTestTerm == False:
                        continue
                    term.relValue[i].append(t)
                    testVocab.add(t)
            if len(term.relValue[term.NormIndex]) > 0:
                termList[term.relValue[term.NormIndex][0]] = term
            else:
                # print("term(%s) not valid." % (term.name), file=sys.stderr)
                pass
    print("loaded relation. termList %d, testvVocab %d" % (len(termList), len(testVocab)))
    return (termList,testVocab)

def mark_term(termList, vocab,testVocab):
    colors=[]
    marks=[]
    areas=[]
    labels=[]
    COLOR=['red','black','purple','gold','crimson','yellow','magenta','orange']

    '''  ['umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']'''
    MARK=[     'h',     '>',   '<',     '^',      'v',       's',      'p',        '.',       'd', '*']
    # each word in vocab

    for i,(v,tf) in enumerate(vocab):
        colors.append(-1)
        marks.append(-1)
        areas.append(-1)
        labels.append(-1)
        AREA = np.pi * 8**2
        # each term to evaluate
        if v in testVocab:
            for j,key in enumerate(termList):
                if v == key:
                    print("find target term %s" % (v))
                    colors[i] = COLOR[j % len(COLOR)]
                    marks[i] = '*'
                    areas[i] = AREA
                    labels[i] = 'target term'
                else:
                    # each relation type
                    for k,rels in enumerate(termList[key].relValue):
                        #each ralation word
                        if k <= Term.NormIndex: continue
                        idx = k - Term.NormIndex-1
                        if v in rels:
                            if colors[i] == -1: colors[i] = COLOR[j % len(COLOR)]
                            if marks[i] == -1: marks[i] = MARK[idx % len(MARK)]
                            if areas[i] == -1: areas[i] = AREA / 3.0
                            if labels[i] == -1: labels[i] = Term.relName[k]
                            print("%s 's %s: %s" % (key,Term.relName[k],v))
    return (colors,marks,areas,labels)


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
    print("Usage: [model-file] [vocab-file]  [relation-file] [top-k] ",file=sys.stderr)
    exit(1)
print('\t'.join(sys.argv))
vecFile= sys.argv[1]
vocFile=sys.argv[2]
relFile = "" if len(sys.argv) <= 3 else sys.argv[3]

print("Loading vecFile...")
points = np.loadtxt(vecFile)
print("vecFile loaded.")
# load np points
N = points.shape[0]
testTerm = None if len(sys.argv) <= 4 else re.split(r'#|\s|,',sys.argv[4].strip())
topn = N if len(sys.argv) <= 6 else min(N,int(sys.argv[6]))
withRelation = True if len(sys.argv) <= 5 else sys.argv[5].strip().lower()!='false'
points = points[:topn]
# load vocabulary
vocab = get_vocab(vocFile)
vocab = vocab[:topn]
# load relation words
testTerm= ['professor'] if testTerm == None else [x.strip() for x in testTerm]
termList = []
default_color = 'grey'
default_mark = 'o'
default_label = 'others'
default_area = np.pi * 5**2
if len(relFile) > 2:
    termList, testVocab = load_relation(relFile,set([x[0] for x in vocab]),testTerm)
    rel_colors, rel_marks, rel_areas ,rel_labels= mark_term(termList,vocab,testVocab)
    pass

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
texts=[]
for (key,p,v,a,c,m,l) in sorted(zip(rel_areas,points,vocab,areas,colors,marks,labels), key=lambda x: x[0]):
    alpha = 1 if v[0] in testVocab else 0.2
    lb = None
    if l in label_set:
        lb = l
        if lb == default_label: a = default_area
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
fname = "%s-%s-top%d-%s.jpg" % (term_name, os.path.basename(vecFile).split('.')[0], topn, withRelation)
plt.savefig(fname,dpi=400,bbox_inches='tight')
# plt.show()
plt.close()

# print("Start PCA...")
# pca = PCA(n_components=0.95)  # dim = 253. so we don't use pca
# pca_result = pca.fit_transform(wv.syn0)
# print(len(pca.explained_variance_ratio_), pca.explained_variance_ratio_)

