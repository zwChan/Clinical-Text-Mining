from __future__ import division

__author__ = 'Jason'
import sys
import csv
import  gensim

class Evaluate:
    """
    ['term name','term_norm', 'umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']
    """
    def __init__(self,termList, topn=10):
        if len(termList) == 0:
            print("No term found.")
            return
        self.termList = termList
        self.topn = topn
        self.termNum = len(termList)
        self.relName = self.termList[0].relName
        self.NameIndex = self.termList[0].NameIndex
        self.NormIndex = self.termList[0].NormIndex
        self.cnt = self.empty_ret() # occurring count of the relation words
        self.hit_cnt = self.empty_ret() #total of hit relation words
        self.cnt_term = self.empty_ret() # number of terms that has this relation word
        self.hit_term = self.empty_ret() # number of terms that is hit this relation word
        self.hit_cnt_weighted = self.empty_ret() # weighted hit number of relation word. weight = 1 / min(number of relation word, topn)

        # self.evaluate()

    def empty_ret(self):
        ret = []
        for i in range(0,len(self.relName)):
            ret.append(0)
        return ret

    def evaluate(self):
        for term in self.termList:
            for i,rels in enumerate(term.relValue):
                if i <= term.NormIndex: continue  # skip term name and term norm
                if len(rels) > 0:
                    self.cnt_term[i] += 1
                    self.hit_cnt_weighted[i] += 1.0*len(term.relHit[i])/min([len(rels),self.topn])
                self.cnt[i] += len(rels)
                if len(term.relHit[i]):
                    self.hit_term[i] += 1
                self.hit_cnt[i] += len(term.relHit[i])

    def __str__(self):
        ret = 'Evaluation:\t%s\n' % ('\t'.join([str(i) for i in self.relName[self.NormIndex+1:]]))
        ret += "cnt:\t%s\n" % ('\t'.join([str(i) for i in self.cnt[self.NormIndex+1:]]))
        ret += "cnt_term:\t%s\n" % ('\t'.join([str(i) for i in self.cnt_term[self.NormIndex+1:]]))
        ret += "cnt_avg:\t%s\n" % ('\t'.join(['%0.2f' % (z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.cnt,self.cnt_term)[self.NormIndex+1:]]))
        ret += "hit_cnt:\t%s\n" % ('\t'.join([str(i) for i in self.hit_cnt[self.NormIndex+1:]]))
        ret += "hit_term:\t%s\n" % ('\t'.join([str(i) for i in self.hit_term[self.NormIndex+1:]]))
        ret += "hit_weight:\t%s\n" % ('\t'.join(["%0.2f" % (i) for i in self.hit_cnt_weighted[self.NormIndex+1:]]))
        ret += "hit_cnt_avg:\t%s\n" % ('\t'.join(['%0.3f' % (z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_cnt,self.hit_term)[self.NormIndex+1:]]))
        ret += "hit_cnt_pct:\t%s\n" % ('\t'.join(['%0.4f' % (100*z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_cnt,self.cnt)[self.NormIndex+1:]]))
        ret += "hit_cnt_term_pct:\t%s\n" % ('\t'.join(['%0.4f' % (100*z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_term,self.cnt_term)[self.NormIndex+1:]]))
        ret += "hit_weight_term_pct:\t%s\n" % ('\t'.join(['%0.4f' % (100*z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_cnt_weighted,self.cnt_term)[self.NormIndex+1:]]))
        ret += "\n"
        return ret

    def PrintHitList(self):
        print('\t'.join(self.relName))
        for t in self.termList:
            print(t)

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
    def similarMap(self):
        simMap = {}
        for kv in self.similar:
            simMap[kv[0]] = kv[1]
        return simMap

def term_similar(w2v, term,  most_similar_f, topn,restrict_vocab=30000):
    term_norm = term.relValue[term.NormIndex]
    sim = []
    try:
        term.similar = most_similar_f(w2v,term_norm,topn=topn,restrict_vocab=restrict_vocab)
    except KeyError as e:
        print("No such word: %s" % (term_norm))
    simMap = term.similarMap()

    for rels in term.relValue:
        hitTerms = []
        for rel in rels:
            if rel in simMap:
                hitTerms.append(rel)
        term.relHit.append(hitTerms)

def accuracy_rel(w2v, csvfile,most_similar_f, topn=10, restrict_vocab=30000,case_insensitive=True):
    termList = []
    with open(csvfile, 'rb') as csvfile:
        csvreader = csv.reader(csvfile, delimiter='\t', quotechar='"')
        first = True
        for row in csvreader:
            if len(row) < 2: continue
            if first:
                first = False
                Term.relName += row
                continue
            term = Term(row[0])
            for i,rel in enumerate(row):
                term.relValue.append([])
                # if len(rel)==0: continue
                rel_term = rel.split('|')
                for t in rel_term:
                    if len(t) == 0: continue
                    term.relValue[i].append(t)
            term_similar(w2v,term,most_similar_f,topn,restrict_vocab)
            termList.append(term)
    return termList


if len(sys.argv) < 4:
    print("Usage: [model-file] [vocab-file] [input-file]")
    exit(1)
model = sys.argv[1]
# model = r'C:\fsu\class\thesis\token.txt.bin'
vocFile=sys.argv[2]
# vocFile = r'C:\fsu\class\thesis\token.txt.voc'
qfile = sys.argv[3]
# qfile = r'C:\fsu\ra\data\201706\synonym_ret.csv'
wv = gensim.models.KeyedVectors.load_word2vec_format(model,fvocab=vocFile,binary=True,encoding='ascii', unicode_errors='ignore')
# wv.most_similar_cosmul(['king','women'],['man'])
# wv.accuracy(qfile)
topn = 10
termList = accuracy_rel(wv,qfile,gensim.models.KeyedVectors.most_similar,topn=topn)
evaluation = Evaluate(termList,topn=topn)
print("### result start: ###")
evaluation.PrintHitList()
print("#### evaluation result ###")
evaluation.evaluate()
print(evaluation)



