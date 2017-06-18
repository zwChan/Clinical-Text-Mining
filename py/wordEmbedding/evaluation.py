from __future__ import division,print_function
__author__ = 'Jason'
import sys
import random
import csv
import logging
import  gensim
from gensim import utils, matutils
# logger = logging.getLogger(__name__)

class EvaluateAnalogy:
    def __init__(self,sections,topn=10):
        if len(sections) == 0:
            print("No term found.")
            return
        self.sections = sections

    def __str__(self):
        ret = "section\taccuracy\tcorrect\tincorrect\n"
        for section in self.sections:
            if len(section['correct'])+len(section['incorrect']) > 0:
                ret += "%s\t%0.2f\t%d\t%d\n" \
                       % (section['section'],100.0*len(section['correct'])/(len(section['correct'])+len(section['incorrect'])), len(section['correct']), len(section['incorrect']))
        correct = sum(len(s['correct']) for s in analogyList)
        incorrect = sum(len(s['incorrect']) for s in analogyList)
        ret += "%s\t%0.2f\t%d\t%d\n" \
            % ('total',100.0*correct/(correct+incorrect), correct, incorrect)
        return ret

class EvaluateRelation:
    """
    ['term name','term_norm', 'umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']
    """
    def __init__(self,termList, topn=10):
        if len(termList) == 0:
            print("No term found.")
            self.termList = []
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
        if len(self.termList) == 0: return
        for term in self.termList:
            for i,rels in enumerate(term.relValue):
                if i <= term.NormIndex: continue  # skip term name and term norm
                if len(rels) > 0:
                    self.cnt_term[i] += 1
                    self.hit_cnt_weighted[i] += 1.0*len(term.relHit[i])/min([len(rels),self.topn])
                self.cnt[i] += len(rels)
                if len(term.relHit[i]) > 0:
                    self.hit_term[i] += 1
                self.hit_cnt[i] += len(term.relHit[i])

    def __str__(self):
        if len(self.termList) == 0: return ""
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
        if len(self.termList) == 0: return
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
        print("No such word: %s" % (term_norm), file=sys.stderr)
    simMap = term.similarMap()

    for rels in term.relValue:
        hitTerms = []
        for rel in rels:
            if rel in simMap:
                hitTerms.append(rel)
        term.relHit.append(hitTerms)

'''
    sample: use part of the evaluation data, for test only
'''
def accuracy_rel(w2v, csvfile,most_similar_f, topn=10, restrict_vocab=30000,case_insensitive=True, sample=0):
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
            if sample > 0 and random.random() > sample: continue  # sample question, for test only
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

def accuracy_analogy(wv, questions, most_similar, topn=10, case_insensitive=True, sample=0):
    """
    Compute accuracy of the model. `questions` is a filename where lines are
    4-tuples of words, split into sections by ": SECTION NAME" lines.
    See questions-words.txt in https://storage.googleapis.com/google-code-archive-source/v2/code.google.com/word2vec/source-archive.zip for an example.

    The accuracy is reported (=printed to log and returned as a list) for each
    section separately, plus there's one aggregate summary at the end.

    Use `restrict_vocab` to ignore all questions containing a word not in the first `restrict_vocab`
    words (default 30,000). This may be meaningful if you've sorted the vocabulary by descending frequency.
    In case `case_insensitive` is True, the first `restrict_vocab` words are taken first, and then
    case normalization is performed.

    Use `case_insensitive` to convert all words in questions and vocab to their uppercase form before
    evaluating the accuracy (default True). Useful in case of case-mismatch between training tokens
    and question words. In case of multiple case variants of a single word, the vector for the first
    occurrence (also the most frequent if vocabulary is sorted) is taken.

    sample: use part of the evaluation data, for test only
    This method corresponds to the `compute-accuracy` script of the original C word2vec.

    """
    self=wv
    ok_vocab = self.vocab
    ok_vocab = dict((w.upper(), v) for w, v in ok_vocab.items()) if case_insensitive else ok_vocab
    sections, section = [], None
    for line_no, line in enumerate(utils.smart_open(questions)):
        # TODO: use level3 BLAS (=evaluate multiple questions at once), for speed
        line = utils.to_unicode(line).lower()
        if line.startswith(': '):
            # a new section starts => store the old section
            if section:
                sections.append(section)
                self.log_accuracy(section)
                print("", file=sys.stderr)
            section = {'section': line.lstrip(': ').strip(), 'correct': [], 'incorrect': []}
        else:
            if sample > 0 and random.random() > sample: continue  # sample question, for test only
            if not section:
                raise ValueError("missing section header before line #%i in %s" % (line_no, questions))
            try:
                if case_insensitive:
                    a, b, c, expected = [word.upper() for word in line.split()]
                else:
                    a, b, c, expected = [word for word in line.split()]
            except:
                print("skipping invalid line in %s, %s" % (section['section'], line.strip()), file=sys.stderr)
                continue
            if a not in ok_vocab or b not in ok_vocab or c not in ok_vocab or expected not in ok_vocab:
                print("skipping line in %s with OOV words: %s" % (section['section'], line.strip()), file=sys.stderr)
                continue

            print("%s found words: %s\n" % (section['section'], line.strip()), file=sys.stderr)

            original_vocab = self.vocab
            self.vocab = ok_vocab
            ignore = set([a, b, c])  # input words to be ignored
            predicted = None
            # find the most likely prediction, ignoring OOV words and input words
            sims = most_similar(self, positive=[b, c], negative=[a], topn=topn, restrict_vocab=None)
            self.vocab = original_vocab
            for word, dist in sims:
                predicted = word.upper() if case_insensitive else word
                if predicted in ok_vocab and predicted not in ignore:
                    if predicted == expected:
                        break  # found.
            if predicted == expected:
                section['correct'].append((a, b, c, expected))
            else:
                section['incorrect'].append((a, b, c, expected))
            print("\r%s: %0.2f%% correct %d, incorrect %d" %\
                  (section['section'],100.0*len(section['correct'])/(len(section['correct'])+len(section['incorrect'])), len(section['correct']), len(section['incorrect'])),\
                  end='', file=sys.stderr)

    if section:
        # store the last section, too
        sections.append(section)
        self.log_accuracy(section)

    # total = {
    #     'section': 'total',
    #     'correct': sum((s['correct'] for s in sections), []),
    #     'incorrect': sum((s['incorrect'] for s in sections), []),
    # }
    # self.log_accuracy(total)
    # sections.append(total)
    return sections

# --------------------------------------------------------------------------------
if len(sys.argv) < 5:
    print("Usage: [model-file] [vocab-file] [analogy-file] [relation-file] [top-n] [sample(test)]",file=sys.stderr)
    exit(1)
model = sys.argv[1]
# model = r'C:\fsu\class\thesis\token.txt.bin'
vocFile=sys.argv[2]
# vocFile = r'C:\fsu\class\thesis\token.txt.voc'
analogyfile = sys.argv[3]
# qfile = r'C:\fsu\ra\data\201706\synonym_ret.csv'
relfile = sys.argv[4]
topn = 10 if len(sys.argv) < 6 else int(sys.argv[5])
sample = 0 if len(sys.argv) < 7 else float(sys.argv[6])

wv = gensim.models.KeyedVectors.load_word2vec_format(model,fvocab=vocFile,binary=True)
# termList = accuracy_rel(wv,relfile,gensim.models.KeyedVectors.most_similar,topn=topn,sample=sample)
# evaluation_rel = EvaluateRelation(termList,topn=topn)
# # print("### result start: ###")
# # evaluation.PrintHitList()
# # print("#### evaluation result ###")
# evaluation_rel.evaluate()
# print(evaluation_rel)

analogyList = accuracy_analogy(wv,analogyfile,gensim.models.KeyedVectors.most_similar,topn=topn, sample=sample)
evaluation_analogy = EvaluateAnalogy(analogyList,topn=topn)
print(evaluation_analogy)




